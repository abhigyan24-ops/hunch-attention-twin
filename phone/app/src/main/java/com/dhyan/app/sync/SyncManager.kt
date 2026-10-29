package com.dhyan.app.sync

import com.dhyan.app.actuator.ActuationEventBus
import com.dhyan.app.twin.decision.ActuationStatus
import com.dhyan.app.actuator.HeldItemsView
import com.dhyan.app.actuator.PhoneControl
import com.dhyan.app.actuator.RoomHeldItemsView
import com.dhyan.app.actuator.SharedActuationEventBus
import com.dhyan.app.data.CmdLogDao
import com.dhyan.app.data.CmdLogEntity
import com.dhyan.app.data.DhyanDatabase
import com.dhyan.app.data.EventDao
import com.dhyan.app.data.SettingsDao
import com.dhyan.app.data.SettingsEntity
import com.dhyan.app.fusion.ContextFuser
import com.dhyan.app.personas.json.JsonReader
import com.dhyan.app.sync.command.SyncCommandDispatcher
import com.dhyan.app.sync.crypto.KeystoreKeyWrapper
import com.dhyan.app.sync.data.InboundEventListener
import com.dhyan.app.sync.data.SyncDataEngine
import com.dhyan.app.sync.pairing.OkHttpRendezvousTransport
import com.dhyan.app.sync.pairing.PairingController
import com.dhyan.app.sync.pairing.PairingState
import com.dhyan.app.sync.pairing.RendezvousTransport
import com.dhyan.app.sync.session.OkHttpSessionTransport
import com.dhyan.app.sync.session.RelayClient
import com.dhyan.app.sync.session.SessionState
import com.dhyan.app.sync.session.SessionTransport
import com.dhyan.app.twin.episodes.EventStreamAdapter
import com.dhyan.app.twin.model.DeclaredSession
import com.dhyan.app.twin.model.LaptopState
import com.dhyan.app.twin.model.TaskType
import com.dhyan.app.ui.model.LiveContextHolder
import com.dhyan.app.ui.settings.SyncUiStateMapper
import com.dhyan.app.ui.settings.SyncUiStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Top-level coordinator for cross-device synchronization (Milestones M1-M4).
 * Bridges Pairing, Session, Data Engine, Command Dispatcher, and ContextFuser.
 */
class SyncManager(
    private val db: DhyanDatabase,
    private val phoneControl: PhoneControl,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO),
    val contextFuser: ContextFuser = ContextFuser(),
    private val rendezvousTransportProvider: () -> RendezvousTransport = { OkHttpRendezvousTransport() },
    private val sessionTransportProvider: () -> SessionTransport = { OkHttpSessionTransport() },
    private val keyWrapper: KeystoreKeyWrapper = KeystoreKeyWrapper(),
    private val actuationEventBus: ActuationEventBus = SharedActuationEventBus,
    private val heldItemsView: HeldItemsView = RoomHeldItemsView(db.heldNotifDao())
) {
    private val settingsDao: SettingsDao = db.settingsDao()
    private val cmdLogDao: CmdLogDao = db.cmdLogDao()
    private val eventDao: EventDao = db.eventDao()

    val pairingController = PairingController(
        settingsDao = settingsDao,
        keyWrapper = keyWrapper,
        transport = rendezvousTransportProvider(),
        scope = scope
    )

    private var relayClient: RelayClient? = null
    private var dataEngine: SyncDataEngine? = null
    private var commandDispatcher: SyncCommandDispatcher? = null
    private var eventStreamAdapter: EventStreamAdapter? = null

    fun attachEventStreamAdapter(adapter: EventStreamAdapter) {
        this.eventStreamAdapter = adapter
        dataEngine?.inboundEventListener = InboundEventListener { type, ts, payload ->
            adapter.processEvent(type, ts, payload)
        }
    }

    fun detachEventStreamAdapter() {
        this.eventStreamAdapter = null
        dataEngine?.inboundEventListener = null
    }

    companion object {
        val DEFAULT_RELAY_URL: String = run {
            val fp = runCatching { android.os.Build.FINGERPRINT }.getOrNull()
            val hw = runCatching { android.os.Build.HARDWARE }.getOrNull()
            if (fp != null && (fp.contains("generic") || hw?.contains("goldfish") == true || hw?.contains("ranchu") == true)) {
                "ws://10.0.2.2:8080/ws"
            } else if (fp != null) {
                "ws://127.0.0.1:8080/ws"
            } else {
                "ws://10.0.2.2:8080/ws"
            }
        }
    }

    private val _relayUrl = MutableStateFlow(DEFAULT_RELAY_URL)
    val relayUrl: StateFlow<String> = _relayUrl.asStateFlow()

    private val _hasStoredCredentials = MutableStateFlow(false)
    val hasStoredCredentials: StateFlow<Boolean> = _hasStoredCredentials.asStateFlow()

    private val _activeDeclaredSession = MutableStateFlow<DeclaredSession?>(null)
    val activeDeclaredSession: StateFlow<DeclaredSession?> = _activeDeclaredSession.asStateFlow()

    private val sessionStateFlow = MutableStateFlow<SessionState>(SessionState.Disconnected)

    val syncStatus: StateFlow<SyncUiStatus> = combine(
        pairingController.state,
        sessionStateFlow,
        _hasStoredCredentials
    ) { pState, sState, hasCreds ->
        SyncUiStateMapper.map(
            pairingState = pState,
            sessionState = sState,
            isLaptopStale = contextFuser.isLaptopStale(),
            hasStoredCredentials = hasCreds
        )
    }.stateIn(scope, SharingStarted.Eagerly, SyncUiStatus.NotPaired)

    val recentCommands: StateFlow<List<CmdLogEntity>> = cmdLogDao.observeAll()
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    private val mirrorJobs = mutableListOf<Job>()

    init {
        // Wire LiveContextHolder to delegate directly to this ContextFuser
        LiveContextHolder.attachFusedSource(contextFuser)

        // Observe stored credentials and auto-connect
        scope.launch {
            loadStoredCredentialsAndConnect()
        }

        // Observe pairing transitions to auto-initialize session upon pairing completion
        observePairingOutcomes()
    }

    private suspend fun loadStoredCredentialsAndConnect() {
        val savedUrl = settingsDao.get("sync_relay_url")
        if (!savedUrl.isNullOrBlank()) {
            _relayUrl.value = savedUrl
        }

        val wrappedPk = settingsDao.get(PairingController.KEY_PAIRING_PK)
        val sid = settingsDao.get(PairingController.KEY_SESSION_ROOM_ID)
        if (!wrappedPk.isNullOrBlank() && !sid.isNullOrBlank()) {
            try {
                val pk = keyWrapper.unwrap(wrappedPk)
                _hasStoredCredentials.value = true
                initializeSession(pk, sid, _relayUrl.value)
            } catch (_: Exception) {
                _hasStoredCredentials.value = false
            }
        }
    }

    private fun observePairingOutcomes() {
        scope.launch {
            pairingController.state.collect { state ->
                if (state is PairingState.Paired) {
                    val sid = state.sidHex
                    val wrappedPk = settingsDao.get(PairingController.KEY_PAIRING_PK)
                    if (!wrappedPk.isNullOrBlank()) {
                        try {
                            val pk = keyWrapper.unwrap(wrappedPk)
                            _hasStoredCredentials.value = true
                            initializeSession(pk, sid, _relayUrl.value)
                        } catch (_: Exception) {
                            _hasStoredCredentials.value = false
                        }
                    }
                }
            }
        }
    }

    private fun initializeSession(pk: ByteArray, sidHex: String, url: String) {
        relayClient?.disconnect()
        mirrorJobs.forEach { it.cancel() }
        mirrorJobs.clear()

        val client = RelayClient(
            pk = pk,
            sidHex = sidHex,
            relayUrl = url,
            transport = sessionTransportProvider(),
            scope = scope,
            getLastSeqAcked = { dataEngine?.highestSettledSeq ?: 0L }
        )
        relayClient = client

        val engine = SyncDataEngine(
            eventDao = eventDao,
            relayClient = client,
            inboundEventListener = InboundEventListener { type, ts, payload ->
                eventStreamAdapter?.processEvent(type, ts, payload)
            }
        )
        dataEngine = engine

        val dispatcher = SyncCommandDispatcher(
            phoneControl = phoneControl,
            cmdLogDao = cmdLogDao,
            relayClient = client,
            heldItemsView = heldItemsView
        )
        commandDispatcher = dispatcher

        // Track session state
        scope.launch {
            client.state.collect { sState ->
                sessionStateFlow.value = sState
                val isConnected = sState is SessionState.Connected
                contextFuser.updateRelayConnectivity(isConnected)

                if (isConnected) {
                    // Outbound mirror on connect
                    dispatcher.sendHeldList()
                }
            }
        }

        // Handle incoming encrypted messages
        client.onMessageReceived = { type, bodyJson, _, _ ->
            scope.launch {
                handleIncomingSessionMessage(type, bodyJson)
            }
        }

        // Start actuation stream mirroring
        startActuationMirroring(dispatcher)

        client.connect()
    }

    private suspend fun handleIncomingSessionMessage(type: String, bodyJson: String) {
        when (type) {
            "state" -> {
                val parsed = JsonReader.parse(bodyJson) as? Map<*, *> ?: return
                val lStateStr = parsed["laptopState"] as? String ?: "offline"
                val lState = LaptopState.entries.find { it.wire.equals(lStateStr, ignoreCase = true) }
                    ?: LaptopState.OFFLINE

                val declaredMap = parsed["declared"] as? Map<*, *>
                val declaredSession = if (declaredMap != null) {
                    val taskStr = declaredMap["task"] as? String ?: "coding"
                    val task = TaskType.entries.find { it.wire.equals(taskStr, ignoreCase = true) }
                        ?: TaskType.UNKNOWN
                    val plannedMin = (declaredMap["plannedMin"] as? Number)?.toDouble() ?: 25.0
                    val strict = (declaredMap["strict"] as? Boolean) ?: false
                    val startedTs = (declaredMap["startedTs"] as? Number)?.toLong() ?: System.currentTimeMillis()
                    DeclaredSession(task, plannedMin, strict, startedTs)
                } else null

                _activeDeclaredSession.value = declaredSession
                contextFuser.updateLaptopState(lState, declaredSession)
            }

            "events" -> {
                val parsed = JsonReader.parse(bodyJson) as? Map<*, *> ?: return
                val fromSeq = (parsed["fromSeq"] as? Number)?.toLong() ?: 1L
                val eventsList = parsed["events"] as? List<Map<*, *>> ?: emptyList()
                dataEngine?.processInboundBatch(fromSeq, eventsList)
            }

            "command" -> {
                commandDispatcher?.handleInboundCommand(bodyJson)
            }
        }
    }

    private fun startActuationMirroring(dispatcher: SyncCommandDispatcher) {
        // Mirror Pet Events (held, released, celebrate)
        val petJob = scope.launch {
            actuationEventBus.events.collect { event ->
                val kind = when (event.status) {
                    ActuationStatus.HELD -> "held"
                    ActuationStatus.RELEASED, ActuationStatus.FAILED_OPEN -> "released"
                    else -> "nudge"
                }
                dispatcher.sendPetEvent(kind)
            }
        }
        mirrorJobs.add(petJob)

        // Mirror Held List
        val heldListJob = scope.launch {
            heldItemsView.observeHeldItems().collect {
                dispatcher.sendHeldList()
            }
        }
        mirrorJobs.add(heldListJob)
    }

    fun setRelayUrl(url: String) {
        _relayUrl.value = url
        scope.launch {
            settingsDao.set(SettingsEntity("sync_relay_url", url, System.currentTimeMillis()))
        }
    }

    fun startPairing(code: String, url: String = _relayUrl.value) {
        setRelayUrl(url)
        pairingController.startPairing(code, url)
    }

    fun confirmSas() {
        pairingController.confirmSas(accepted = true)
    }

    fun rejectSas() {
        pairingController.confirmSas(accepted = false)
    }

    fun unpair() {
        scope.launch {
            pairingController.unpair()
            relayClient?.disconnect()
            relayClient = null
            dataEngine = null
            commandDispatcher = null
            mirrorJobs.forEach { it.cancel() }
            mirrorJobs.clear()
            settingsDao.delete(PairingController.KEY_PAIRING_PK)
            settingsDao.delete(PairingController.KEY_SESSION_ROOM_ID)
            _hasStoredCredentials.value = false
            sessionStateFlow.value = SessionState.Disconnected
            contextFuser.reset()
            _activeDeclaredSession.value = null
        }
    }
}

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
import com.dhyan.app.sync.crypto.KeyWrapper
import com.dhyan.app.sync.crypto.KeystoreKeyWrapper
import com.dhyan.app.sync.data.InboundEventListener
import com.dhyan.app.sync.data.SyncDataEngine
import com.dhyan.app.sync.data.PhoneEventStore
import com.dhyan.app.sync.data.WireSchemas
import com.dhyan.app.ingest.ClockOffset
import androidx.room.withTransaction
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
    private val keyWrapper: KeyWrapper = KeystoreKeyWrapper(),
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
    private val eventStore = PhoneEventStore(db)
    private val initializationMutex = Mutex()
    private var outgoingAck = 0L
    private var highestSent = 0L
    private var initializedSid: String? = null
    val storagePaused = MutableStateFlow(false)

    fun recordLocalEvent(type: String, source: String, payload: Map<String, Any?>, ts: Long) {
        scope.launch {
            runCatching { eventStore.append(type, source, payload, ts) }
                .onFailure { storagePaused.value = true }
        }
    }

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
            } else {
                // Target hardware in debug mode: loopback via adb reverse over USB (127.0.0.1:8080), no LAN IP committed
                "ws://127.0.0.1:8080/ws"
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
        LiveContextHolder.attachFusedSource(contextFuser) { snapshot ->
            contextFuser.updatePhoneState(snapshot.phoneState, taskType = snapshot.taskType,
                declaredSession = snapshot.declaredSession, hourBand = snapshot.hourBand,
                fatigueBand = snapshot.fatigueBand, recentInterrupts30m = snapshot.recentInterrupts30m,
                baseConfidence = snapshot.confidence)
        }

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

    private suspend fun initializeSession(pk: ByteArray, sidHex: String, url: String) = initializationMutex.withLock {
        if (initializedSid == sidHex && relayClient != null) return@withLock
        val isLoopback = url.startsWith("ws://127.0.0.1") || url.startsWith("ws://localhost") || url.startsWith("ws://10.0.2.2")
        if (url.startsWith("ws://", ignoreCase = true) && !isLoopback) {
            throw IllegalArgumentException("Cleartext WebSocket is prohibited for non-loopback endpoints. Use wss://")
        }
        relayClient?.disconnect()
        initializedSid = sidHex
        val deviceId = eventStore.deviceId()
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
            localDeviceId = deviceId,
            settingsDao = settingsDao,
            transaction = { block -> db.withTransaction { block() } },
            inboundEventListener = InboundEventListener { type, ts, payload ->
                eventStreamAdapter?.processEvent(type, ts, payload)
            }
        )
        dataEngine = engine
        engine.restore()

        val dispatcher = SyncCommandDispatcher(
            phoneControl = phoneControl,
            cmdLogDao = cmdLogDao,
            relayClient = client,
            heldItemsView = heldItemsView
            , transaction = { block -> db.withTransaction { block() } }
        )
        commandDispatcher = dispatcher

        // Track session state
        mirrorJobs.add(scope.launch {
            client.state.collect { sState ->
                sessionStateFlow.value = sState
                val isConnected = sState is SessionState.Connected
                contextFuser.updateRelayConnectivity(isConnected)

                if (isConnected) {
                    outgoingAck = (sState as SessionState.Connected).peerLastSeqAcked
                    highestSent = outgoingAck
                    // Outbound mirror on connect
                    dispatcher.sendHeldList()
                }
            }
        })
        client.onPongReceived = { t0, t1, t2, t3 -> ClockOffset.recordSample(t0, t1, t2, t3) }
        mirrorJobs.add(scope.launch {
            var ticks = 0L
            var peerSilent = false
            while (isActive) {
                delay(1_000L)
                contextFuser.recompute()
                if (client.state.value !is SessionState.Connected) continue
                ticks++
                try {
                    if (ticks % 2L == 0L) {
                        if (engine.exportOutboundBatch(outgoingAck)) highestSent = maxOf(highestSent, engine.lastExportedSeq)
                    }
                    if (ticks % 5L == 0L) contextFuser.currentSnapshot()?.let {
                        engine.sendStateSnapshot(it, heldItemsView.currentHeldItems().size)
                    }
                    if (ticks % 30L == 0L) client.sendPing()
                    val silent = System.currentTimeMillis() - client.lastPeerFrameMs > 30_000L
                    if (silent && !peerSilent) eventStore.append("gap", "system",
                        mapOf("deviceFrom" to "laptop", "deviceTo" to "phone", "reason" to "peer_silent"), System.currentTimeMillis())
                    peerSilent = silent
                    storagePaused.value = false
                } catch (_: Exception) { storagePaused.value = true }
            }
        })

        // Handle incoming encrypted messages
        client.onMessageReceived = { type, bodyJson, _, _ ->
            scope.launch {
                try { handleIncomingSessionMessage(type, bodyJson) }
                catch (_: IllegalArgumentException) { client.sendEncrypted("error", """{"code":"schema_invalid"}""") }
                catch (_: Exception) { storagePaused.value = true }
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
                contextFuser.adapterHealthy = parsed["adapterHealthy"] as? Boolean
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
            "ack" -> {
                val parsed = JsonReader.parse(bodyJson) as? Map<*, *> ?: return
                val ack = SyncDataEngine.integer(parsed["upToSeq"]) ?: return
                require(ack in outgoingAck..highestSent) { "invalid_ack" }
                outgoingAck = ack
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
            initializedSid = null
            dataEngine = null
            commandDispatcher = null
            mirrorJobs.forEach { it.cancel() }
            mirrorJobs.clear()
            settingsDao.delete(PairingController.KEY_PAIRING_PK)
            settingsDao.delete(PairingController.KEY_SESSION_ROOM_ID)
            settingsDao.delete("sync_peer_device_id")
            _hasStoredCredentials.value = false
            sessionStateFlow.value = SessionState.Disconnected
            contextFuser.reset()
            _activeDeclaredSession.value = null
        }
    }
}

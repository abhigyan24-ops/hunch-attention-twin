package com.dhyan.app.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dhyan.app.actuator.ActuatorConfig
import com.dhyan.app.actuator.KillSwitchController
import com.dhyan.app.actuator.digest.DigestBuilder
import com.dhyan.app.actuator.digest.DigestPoster
import com.dhyan.app.data.CmdLogEntity
import com.dhyan.app.data.DhyanDatabase
import com.dhyan.app.data.SettingsDao
import com.dhyan.app.data.SettingsEntity
import com.dhyan.app.data.VipDao
import com.dhyan.app.data.VipEntity
import com.dhyan.app.ingest.AppCategorizer
import com.dhyan.app.sync.SyncManager
import com.dhyan.app.sync.data.DeviceIdProvider
import com.dhyan.app.twin.decision.PolicyMode
import com.dhyan.app.twin.decision.PolicySettings
import com.dhyan.app.twin.model.DeclaredSession
import com.dhyan.app.ui.model.PolicySettingsHolder
import com.dhyan.app.ui.settings.SyncUiStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SettingsUiState(
    val settings: PolicySettings = PolicySettings(),
    val vipHashes: List<String> = emptyList(),
    val showLiveConfirmDialog: Boolean = false,
    val pendingMode: PolicyMode? = null,
    val debugHoldOverrideMinutes: Double? = null,
    val salt: String = "dhyan_salt_default"
)

class SettingsViewModel(
    private val vipDao: VipDao,
    private val settingsDao: SettingsDao? = null,
    private val killSwitchController: KillSwitchController? = null,
    private val digestPoster: DigestPoster? = null,
    private val syncManager: SyncManager? = null,
    private val db: DhyanDatabase? = null,
    val salt: String = "dhyan_salt_default",
    coroutineScope: kotlinx.coroutines.CoroutineScope? = null
) : ViewModel() {

    private val scope = coroutineScope ?: viewModelScope
    private val _uiState = MutableStateFlow(SettingsUiState(settings = PolicySettingsHolder.get(), salt = salt))
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    val syncStatus: StateFlow<SyncUiStatus> = syncManager?.syncStatus
        ?: MutableStateFlow(SyncUiStatus.NotPaired)

    val relayUrl: StateFlow<String> = syncManager?.relayUrl
        ?: MutableStateFlow(SyncManager.DEFAULT_RELAY_URL)

    val hasStoredCredentials: StateFlow<Boolean> = syncManager?.hasStoredCredentials
        ?: MutableStateFlow(false)

    val activeDeclaredSession: StateFlow<DeclaredSession?> = syncManager?.activeDeclaredSession
        ?: MutableStateFlow(null)

    val recentCommands: StateFlow<List<CmdLogEntity>> = syncManager?.recentCommands
        ?: MutableStateFlow(emptyList())

    init {
        loadVips()
        scope.launch {
            PolicySettingsHolder.settingsFlow.collect { settings ->
                _uiState.value = _uiState.value.copy(settings = settings)
            }
        }
    }

    private fun loadVips() {
        scope.launch {
            val hashes = vipDao.allHashes()
            _uiState.value = _uiState.value.copy(vipHashes = hashes)
        }
    }

    fun startPairing(code: String, url: String) {
        syncManager?.startPairing(code, url)
    }

    fun confirmSas() {
        syncManager?.confirmSas()
    }

    fun rejectSas() {
        syncManager?.rejectSas()
    }

    fun unpair() {
        syncManager?.unpair()
    }

    fun requestModeChange(mode: PolicyMode) {
        if (mode == PolicyMode.LIVE) {
            _uiState.value = _uiState.value.copy(showLiveConfirmDialog = true, pendingMode = mode)
        } else {
            PolicySettingsHolder.setMode(mode)
        }
    }

    fun confirmLiveMode() {
        PolicySettingsHolder.setMode(PolicyMode.LIVE)
        _uiState.value = _uiState.value.copy(showLiveConfirmDialog = false, pendingMode = null)
    }

    fun dismissLiveDialog() {
        _uiState.value = _uiState.value.copy(showLiveConfirmDialog = false, pendingMode = null)
    }

    fun setCautionMultiplier(kappaDPerHour: Double) {
        PolicySettingsHolder.update { current ->
            PolicySettings(
                mode = current.mode,
                killSwitch = current.killSwitch,
                kappaDPerHour = kappaDPerHour,
                explorationEnabled = current.explorationEnabled,
                epsilon = current.epsilon,
                strictSession = current.strictSession,
                allowListPackages = current.allowListPackages,
                seed = current.seed,
                digestIntervalMin = current.digestIntervalMin
            )
        }
    }

    fun setExplorationEnabled(enabled: Boolean) {
        PolicySettingsHolder.update { current ->
            PolicySettings(
                mode = current.mode,
                killSwitch = current.killSwitch,
                kappaDPerHour = current.kappaDPerHour,
                explorationEnabled = enabled,
                epsilon = current.epsilon,
                strictSession = current.strictSession,
                allowListPackages = current.allowListPackages,
                seed = current.seed,
                digestIntervalMin = current.digestIntervalMin
            )
        }
    }

    fun setDigestIntervalMin(intervalMin: Int) {
        PolicySettingsHolder.update { current ->
            PolicySettings(
                mode = current.mode,
                killSwitch = current.killSwitch,
                kappaDPerHour = current.kappaDPerHour,
                explorationEnabled = current.explorationEnabled,
                epsilon = current.epsilon,
                strictSession = current.strictSession,
                allowListPackages = current.allowListPackages,
                seed = current.seed,
                digestIntervalMin = intervalMin
            )
        }
    }

    fun toggleAllowListPackage(pkg: String) {
        PolicySettingsHolder.update { current ->
            val updated = current.allowListPackages.toMutableSet()
            if (updated.contains(pkg)) updated.remove(pkg) else updated.add(pkg)
            PolicySettings(
                mode = current.mode,
                killSwitch = current.killSwitch,
                kappaDPerHour = current.kappaDPerHour,
                explorationEnabled = current.explorationEnabled,
                epsilon = current.epsilon,
                strictSession = current.strictSession,
                allowListPackages = updated,
                seed = current.seed,
                digestIntervalMin = current.digestIntervalMin
            )
        }
    }

    fun toggleKillSwitch() {
        scope.launch {
            val current = PolicySettingsHolder.get().killSwitch
            val next = !current
            PolicySettingsHolder.setKillSwitch(next)
            if (next && killSwitchController != null) {
                killSwitchController.activate()
            }
        }
    }

    /**
     * Hashes contact name with salt and stores in VIP DAO.
     * PRIVACY CONTRACT: Never persists the raw contact name!
     */
    fun addVipSender(contactName: String) {
        if (contactName.isBlank()) return
        val hash = AppCategorizer.senderHash(contactName.trim(), salt)
        scope.launch {
            vipDao.insert(VipEntity(senderHash = hash, addedMs = System.currentTimeMillis()))
            loadVips()
        }
    }

    fun removeVipSender(hash: String) {
        scope.launch {
            vipDao.delete(hash)
            loadVips()
        }
    }

    fun setDebugHoldOverride(minutes: Double?) {
        _uiState.value = _uiState.value.copy(debugHoldOverrideMinutes = minutes)
    }

    fun postTestNotification() {
        digestPoster?.post(
            DigestBuilder.DigestContent(
                title = "1 test notification held",
                summaryLine = "test · Dhyan debug tool",
                items = listOf(
                    DigestBuilder.DigestItem(
                        keyHash = "test_key_hash",
                        appCat = "test",
                        notifCat = "promo",
                        heldAgo = "0s ago",
                        decisionAction = "DIGEST"
                    )
                )
            )
        )
    }

    /**
     * Clears phone Room tables (held_notifs, decisions, feedback, events, etc.),
     * resets persistent UUID device ID, and triggers laptop reset (POST /reset).
     */
    fun wipeAndResetMyData(context: Context) {
        scope.launch(Dispatchers.IO) {
            db?.let { database ->
                runCatching { database.clearAllTables() }
            }

            SimulateViewModel.personaStoreCache.clear()
            DeviceIdProvider.resetDeviceId(context)

            syncManager?.triggerLaptopReset()
            loadVips()
        }
    }
}

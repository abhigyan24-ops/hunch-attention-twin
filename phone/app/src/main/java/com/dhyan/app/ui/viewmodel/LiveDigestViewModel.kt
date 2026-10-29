package com.dhyan.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dhyan.app.actuator.Clock
import com.dhyan.app.actuator.KillSwitchController
import com.dhyan.app.actuator.SystemClock
import com.dhyan.app.data.DecisionDao
import com.dhyan.app.data.DecisionEntity
import com.dhyan.app.data.FeedbackDao
import com.dhyan.app.data.FeedbackEntity
import com.dhyan.app.data.HeldNotifDao
import com.dhyan.app.data.HeldNotifEntity
import com.dhyan.app.twin.decision.ActuationStatus
import com.dhyan.app.twin.decision.PolicyMode
import com.dhyan.app.twin.decision.PolicySettings
import com.dhyan.app.twin.model.ContextSnapshot
import com.dhyan.app.ui.model.LiveContextHolder
import com.dhyan.app.ui.model.PolicySettingsHolder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class LiveHeldItemUi(
    val keyHash: String,
    val appCat: String,
    val notifCat: String,
    val mechanism: String,
    val state: String,
    val heldAgo: String,
    val dueIn: String,
    val propensity: Double,
    val decisionAction: String
)

data class LiveDecisionItemUi(
    val id: Long,
    val keyHash: String,
    val action: String,
    val reasonCode: String,
    val safetyReason: String?,
    val deltaLMean: Double?,
    val propensity: Double,
    val timestampAgo: String,
    val actuationStatus: String?,
    val exploration: Boolean,
    val failedOpen: Boolean,
    val mode: String
)

data class LiveDigestUiState(
    val mode: PolicyMode = PolicyMode.OFF,
    val killSwitchActive: Boolean = false,
    val contextSnapshot: ContextSnapshot? = null,
    val heldQueue: List<LiveHeldItemUi> = emptyList(),
    val decisionFeed: List<LiveDecisionItemUi> = emptyList(),
    val historyStrip: List<LiveHeldItemUi> = emptyList(),
    val isFakeProvider: Boolean = true,
    val provenance: String = "observed",
    val laptopAvailability: String = "No laptop data"
)

class LiveDigestViewModel(
    private val heldNotifDao: HeldNotifDao,
    private val decisionDao: DecisionDao,
    private val feedbackDao: FeedbackDao,
    private val killSwitchController: KillSwitchController? = null,
    private val clock: Clock = SystemClock,
    val isFakeProvider: Boolean = true,
    val provenance: String = "observed",
    coroutineScope: kotlinx.coroutines.CoroutineScope? = null
) : ViewModel() {

    private val scope = coroutineScope ?: viewModelScope

    val uiState: StateFlow<LiveDigestUiState> = combine(
        PolicySettingsHolder.settingsFlow,
        LiveContextHolder.snapshot,
        heldNotifDao.observeActive(),
        decisionDao.observeRecent(50),
        heldNotifDao.observeHistory(10)
    ) { settings, snapshot, activeHeld, recentDecisions, history ->
        val now = clock.nowMs()
        val prov = try {
            com.dhyan.app.DhyanApplication.syncManager.contextFuser.currentProvenance()
        } catch (_: Throwable) {
            if (snapshot?.laptopState == com.dhyan.app.twin.model.LaptopState.OFFLINE) "estimated" else "observed"
        }
        LiveDigestUiState(
            mode = settings.mode,
            killSwitchActive = settings.killSwitch,
            contextSnapshot = snapshot,
            heldQueue = activeHeld.map { it.toUi(now) },
            decisionFeed = recentDecisions.map { it.toUi(now) },
            historyStrip = history.map { it.toUi(now) },
            isFakeProvider = isFakeProvider,
            provenance = prov,
            laptopAvailability = runCatching {
                com.dhyan.app.DhyanApplication.syncManager.contextFuser.laptopAvailability()
            }.getOrDefault("No laptop data")
        )
    }.stateIn(
        scope = scope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = LiveDigestUiState(isFakeProvider = isFakeProvider)
    )

    fun showNow(keyHash: String) {
        scope.launch {
            heldNotifDao.setStateWithOverride(keyHash, ActuationStatus.RELEASED.name)
        }
    }

    fun markImportant(keyHash: String, decisionId: Long) {
        scope.launch {
            feedbackDao.insert(
                FeedbackEntity(
                    ts = clock.nowMs(),
                    decisionId = decisionId,
                    type = "override_allow",
                    comment = null
                )
            )
            // User indicated important -> release immediately
            heldNotifDao.setStateWithOverride(keyHash, ActuationStatus.RELEASED.name)
        }
    }

    fun markNoise(keyHash: String, decisionId: Long) {
        scope.launch {
            feedbackDao.insert(
                FeedbackEntity(
                    ts = clock.nowMs(),
                    decisionId = decisionId,
                    type = "thumbs_down",
                    comment = null
                )
            )
        }
    }

    fun releaseAll() {
        scope.launch {
            val active = heldNotifDao.allActive()
            active.forEach { entity ->
                heldNotifDao.setStateWithOverride(entity.keyHash, ActuationStatus.RELEASED.name)
            }
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

    fun setMode(mode: PolicyMode) {
        PolicySettingsHolder.setMode(mode)
    }

    private fun HeldNotifEntity.toUi(nowMs: Long): LiveHeldItemUi {
        val elapsedSec = ((nowMs - heldSinceMs) / 1000).coerceAtLeast(0)
        val heldAgo = when {
            elapsedSec < 60 -> "${elapsedSec}s ago"
            elapsedSec < 3600 -> "${elapsedSec / 60}m ago"
            else -> "${elapsedSec / 3600}h ago"
        }

        val dueIn = if (holdUntilMs > nowMs) {
            val remainSec = (holdUntilMs - nowMs) / 1000
            when {
                remainSec < 60 -> "Due in <1m"
                remainSec < 3600 -> "Due in ${remainSec / 60}m"
                else -> "Due in ${remainSec / 3600}h"
            }
        } else if (holdUntilMs > 0) {
            "Due now"
        } else {
            "Next digest"
        }

        return LiveHeldItemUi(
            keyHash = keyHash,
            appCat = appCat,
            notifCat = notifCat,
            mechanism = mechanism,
            state = state,
            heldAgo = heldAgo,
            dueIn = dueIn,
            propensity = propensity,
            decisionAction = decisionAction
        )
    }

    private fun DecisionEntity.toUi(nowMs: Long): LiveDecisionItemUi {
        val elapsedSec = ((nowMs - ts) / 1000).coerceAtLeast(0)
        val ago = when {
            elapsedSec < 60 -> "${elapsedSec}s ago"
            elapsedSec < 3600 -> "${elapsedSec / 60}m ago"
            else -> "${elapsedSec / 3600}h ago"
        }
        return LiveDecisionItemUi(
            id = id,
            keyHash = keyHash,
            action = action,
            reasonCode = reasonCode,
            safetyReason = safetyReason,
            deltaLMean = deltaLMean,
            propensity = propensity,
            timestampAgo = ago,
            actuationStatus = actuationStatus,
            exploration = exploration,
            failedOpen = failedOpen,
            mode = mode
        )
    }
}

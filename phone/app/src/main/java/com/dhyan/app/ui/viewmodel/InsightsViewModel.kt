package com.dhyan.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dhyan.app.data.DecisionDao
import com.dhyan.app.data.HeldNotifDao
import com.dhyan.app.twin.decision.Action
import com.dhyan.app.twin.decision.ActuationStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class InsightsUiState(
    val allowCount: Int = 0,
    val delayCount: Int = 0,
    val digestCount: Int = 0,
    val silentCount: Int = 0,
    val heldCount: Int = 0,
    val snoozedCount: Int = 0,
    val releasedCount: Int = 0,
    val failedOpenCount: Int = 0,
    val unknownCount: Int = 0,
    val totalDecisions: Int = 0
)

class InsightsViewModel(
    private val decisionDao: DecisionDao,
    private val heldNotifDao: HeldNotifDao,
    coroutineScope: kotlinx.coroutines.CoroutineScope? = null
) : ViewModel() {

    private val scope = coroutineScope ?: viewModelScope
    private val _uiState = MutableStateFlow(InsightsUiState())
    val uiState: StateFlow<InsightsUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        scope.launch {
            val allows = decisionDao.countByAction(Action.ALLOW.name)
            val delays = decisionDao.countByAction(Action.DELAY.name)
            val digests = decisionDao.countByAction(Action.DIGEST.name)
            val silents = decisionDao.countByAction(Action.SILENT.name)

            val held = heldNotifDao.countByState(ActuationStatus.HELD.name)
            val snoozed = heldNotifDao.countByState(ActuationStatus.SNOOZED.name)
            val released = heldNotifDao.countByState(ActuationStatus.RELEASED.name)
            val failedOpen = heldNotifDao.countByState(ActuationStatus.FAILED_OPEN.name)
            val unknown = heldNotifDao.countByState(ActuationStatus.ACTUATION_UNKNOWN.name)

            val total = allows + delays + digests + silents

            _uiState.value = InsightsUiState(
                allowCount = allows,
                delayCount = delays,
                digestCount = digests,
                silentCount = silents,
                heldCount = held,
                snoozedCount = snoozed,
                releasedCount = released,
                failedOpenCount = failedOpen,
                unknownCount = unknown,
                totalDecisions = total
            )
        }
    }
}

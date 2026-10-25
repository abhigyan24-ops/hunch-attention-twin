package com.dhyan.app.actuator

import com.dhyan.app.twin.decision.ActuationStatus
import com.dhyan.app.twin.model.ContextSnapshot
import com.dhyan.app.twin.model.DeclaredSession
import com.dhyan.app.twin.model.FocusState
import com.dhyan.app.twin.model.LaptopState
import com.dhyan.app.twin.model.PhoneState
import com.dhyan.app.twin.model.TaskSource
import com.dhyan.app.twin.model.TaskType
import com.dhyan.app.ui.model.LiveContextHolder
import com.dhyan.app.ui.model.PolicySettingsHolder

/** Result returned by [PhoneControl] commands. */
sealed interface ControlResult {
    val applied: Boolean
    val reason: String

    data class Applied(override val reason: String = "applied") : ControlResult {
        override val applied: Boolean = true
    }

    data class Rejected(override val reason: String) : ControlResult {
        override val applied: Boolean = false
    }
}

/**
 * Single facade entry point for remote commands (e.g. from laptop companion via Stage 8 relay).
 *
 * Enforces safety boundaries: never-hold rules, bounds validation, and fail-open kill switch semantics.
 */
interface PhoneControl {
    suspend fun showNow(heldId: String? = null, all: Boolean = false): ControlResult
    suspend fun setKillSwitch(on: Boolean): ControlResult
    suspend fun declareSession(task: String, plannedMin: Double, strict: Boolean): ControlResult
    suspend fun endSession(): ControlResult
}

class PhoneControlImpl(
    private val heldStore: HeldStore,
    private val killSwitchController: KillSwitchController? = null,
    private val clock: Clock = SystemClock,
    private val eventBus: ActuationEventBus = SharedActuationEventBus
) : PhoneControl {

    override suspend fun showNow(heldId: String?, all: Boolean): ControlResult {
        val now = clock.nowMs()
        if (all) {
            val active = heldStore.allActive()
            for (item in active) {
                heldStore.setStateWithOverride(item.keyHash, ActuationStatus.RELEASED)
                eventBus.emit(ActuationEvent(item.keyHash, ActuationStatus.RELEASED, item.appCat, now))
            }
            return ControlResult.Applied("all_released")
        }

        if (heldId == null) {
            return ControlResult.Rejected("missing_held_id")
        }

        val item = heldStore.get(heldId) ?: return ControlResult.Rejected("not_found")
        heldStore.setStateWithOverride(heldId, ActuationStatus.RELEASED)
        eventBus.emit(ActuationEvent(heldId, ActuationStatus.RELEASED, item.appCat, now))
        return ControlResult.Applied("released")
    }

    override suspend fun setKillSwitch(on: Boolean): ControlResult {
        PolicySettingsHolder.setKillSwitch(on)
        if (on && killSwitchController != null) {
            killSwitchController.activate()
        }
        return ControlResult.Applied(if (on) "kill_switch_activated" else "kill_switch_deactivated")
    }

    override suspend fun declareSession(task: String, plannedMin: Double, strict: Boolean): ControlResult {
        if (plannedMin !in 5.0..480.0) {
            return ControlResult.Rejected("invalid_planned_min")
        }

        val taskType = try {
            TaskType.valueOf(task.uppercase())
        } catch (_: Exception) {
            TaskType.entries.find { it.wire.equals(task, ignoreCase = true) } ?: TaskType.UNKNOWN
        }

        val now = clock.nowMs()
        val current = LiveContextHolder.currentSnapshot()
        val updated = (current ?: fallbackSnapshot(now)).copy(
            taskType = taskType,
            taskSource = TaskSource.DECLARED,
            declaredSession = DeclaredSession(
                task = taskType,
                plannedMin = plannedMin,
                strict = strict,
                startedTs = now
            )
        )
        LiveContextHolder.update(updated)
        return ControlResult.Applied("session_declared")
    }

    override suspend fun endSession(): ControlResult {
        val current = LiveContextHolder.currentSnapshot() ?: return ControlResult.Applied("no_active_session")
        val updated = current.copy(
            taskSource = TaskSource.INFERRED,
            declaredSession = null
        )
        LiveContextHolder.update(updated)
        return ControlResult.Applied("session_ended")
    }

    private fun fallbackSnapshot(nowMs: Long) = ContextSnapshot(
        ts = nowMs,
        focusState = FocusState.WORKING,
        taskType = TaskType.UNKNOWN,
        taskSource = TaskSource.DECLARED,
        confidence = 0.5,
        hourBand = "afternoon",
        minutesInState = 0.0,
        fatigueBand = "low",
        recentInterrupts30m = 0,
        laptopState = LaptopState.OFFLINE,
        phoneState = PhoneState.IN_APP,
        secondScreenOverlap = false,
        declaredSession = null
    )
}

package com.dhyan.app.actuator.release

import com.dhyan.app.actuator.HeldStore
import com.dhyan.app.twin.decision.ActuationStatus

/**
 * Pure idempotent release logic — shared by [ReleaseWorker] and [SweepWorker].
 * Never touches the platform; just updates DB state.
 */
object ReleaseService {

    data class ReleaseResult(
        val released: Int,
        val failedOpen: Int,
        val expired: Int
    )

    /**
     * Release all items whose [holdUntilMs] has passed,
     * fail-open any stuck PENDING rows,
     * and delete expired rows.
     */
    suspend fun releaseDue(store: HeldStore, nowMs: Long): ReleaseResult {
        // Release due items
        val due = store.dueForRelease(nowMs)
        due.forEach { store.setState(it.keyHash, ActuationStatus.RELEASED) }

        // Fail-open stuck PENDING rows (recovery)
        val stuckCutoff = nowMs - com.dhyan.app.actuator.ActuatorConfig.PENDING_RECOVERY_MS
        val stuck = store.stuckPending(stuckCutoff)
        stuck.forEach { store.setState(it.keyHash, ActuationStatus.FAILED_OPEN) }

        // Expire old finished rows
        store.deleteExpired(nowMs)

        return ReleaseResult(
            released = due.size,
            failedOpen = stuck.size,
            expired = 0 // deleteExpired doesn't report count in our interface
        )
    }
}

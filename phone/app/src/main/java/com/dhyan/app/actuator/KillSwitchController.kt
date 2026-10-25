package com.dhyan.app.actuator

import com.dhyan.app.actuator.release.ReleaseJobScheduler
import com.dhyan.app.twin.decision.ActuationStatus

/**
 * Kill switch: immediately fails open all active held notifications
 * and cancels all pending release jobs.
 *
 * Effects are enqueued as a coroutine — never blocks the calling thread.
 */
class KillSwitchController(
    private val heldStore: HeldStore,
    private val releaseJobScheduler: ReleaseJobScheduler
) {
    /** Returns number of items released (for UI feedback). */
    suspend fun activate(): Int {
        releaseJobScheduler.cancelAll()
        val active = heldStore.allActive()
        active.forEach { entity ->
            heldStore.setState(entity.keyHash, ActuationStatus.FAILED_OPEN)
        }
        return active.size
    }
}

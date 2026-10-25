package com.dhyan.app.actuator.release

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.dhyan.app.actuator.ActuatorConfig
import com.dhyan.app.actuator.HeldStore
import com.dhyan.app.actuator.RoomHeldStore
import com.dhyan.app.actuator.SystemClock
import com.dhyan.app.data.DhyanDatabase
import com.dhyan.app.twin.decision.ActuationStatus

/**
 * WorkManager worker: releases all due held notifications.
 * Called by one-time per-key jobs and the periodic sweep.
 */
class ReleaseWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val db = DhyanDatabase.getInstance(applicationContext)
        val store: HeldStore = RoomHeldStore(db.heldNotifDao())
        val nowMs = SystemClock.nowMs()

        // Release all due items
        val due = store.dueForRelease(nowMs)
        due.forEach { entity ->
            store.setState(entity.keyHash, ActuationStatus.RELEASED)
        }

        // Recovery: fail-open any stuck PENDING rows
        val stuckCutoff = nowMs - ActuatorConfig.PENDING_RECOVERY_MS
        val stuck = store.stuckPending(stuckCutoff)
        stuck.forEach { entity ->
            store.setState(entity.keyHash, ActuationStatus.FAILED_OPEN)
        }

        // Expire old rows
        store.deleteExpired(nowMs)

        return Result.success()
    }
}

/**
 * Periodic sweep worker — identical logic to ReleaseWorker.
 * Kept as a separate class so tags can distinguish one-time vs periodic jobs.
 */
class SweepWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        // Delegate to shared release service logic
        val db = DhyanDatabase.getInstance(applicationContext)
        val store: HeldStore = RoomHeldStore(db.heldNotifDao())
        ReleaseService.releaseDue(store, SystemClock.nowMs())
        return Result.success()
    }
}

package com.dhyan.app.actuator.release

import android.content.Context
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.dhyan.app.actuator.ActuatorConfig
import java.util.concurrent.TimeUnit

/** Schedules WorkManager jobs that trigger notification release. */
interface ReleaseJobScheduler {
    /** Schedule a one-time job to release a specific keyHash at [releaseAtMs]. */
    fun scheduleRelease(keyHash: String, releaseAtMs: Long)
    /** Schedule the periodic sweep (idempotent). */
    fun schedulePeriodicSweep()
    /** Cancel all release jobs. Used by kill-switch. */
    fun cancelAll()
}

class WorkManagerReleaseJobScheduler(private val context: Context) : ReleaseJobScheduler {

    private val wm get() = WorkManager.getInstance(context)

    override fun scheduleRelease(keyHash: String, releaseAtMs: Long) {
        val delayMs = (releaseAtMs - System.currentTimeMillis()).coerceAtLeast(0L)
        val data = Data.Builder().putString("key_hash", keyHash).build()
        val request = OneTimeWorkRequestBuilder<ReleaseWorker>()
            .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
            .setInputData(data)
            .addTag(ActuatorConfig.WORK_TAG_RELEASE)
            .build()
        wm.enqueueUniqueWork(
            "${ActuatorConfig.WORK_TAG_RELEASE}_$keyHash",
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    override fun schedulePeriodicSweep() {
        val request = PeriodicWorkRequestBuilder<SweepWorker>(
            ActuatorConfig.SWEEP_INTERVAL_MIN, TimeUnit.MINUTES
        )
            .addTag(ActuatorConfig.WORK_TAG_SWEEP)
            .build()
        wm.enqueueUniquePeriodicWork(
            ActuatorConfig.WORK_NAME_SWEEP,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    override fun cancelAll() {
        wm.cancelAllWorkByTag(ActuatorConfig.WORK_TAG_RELEASE)
        wm.cancelAllWorkByTag(ActuatorConfig.WORK_TAG_SWEEP)
    }
}

/** No-op scheduler for unit tests. */
class NoOpReleaseJobScheduler : ReleaseJobScheduler {
    val scheduledKeys = mutableListOf<Pair<String, Long>>()
    var cancelled = false

    override fun scheduleRelease(keyHash: String, releaseAtMs: Long) {
        scheduledKeys.add(keyHash to releaseAtMs)
    }
    override fun schedulePeriodicSweep() {}
    override fun cancelAll() { cancelled = true }
}

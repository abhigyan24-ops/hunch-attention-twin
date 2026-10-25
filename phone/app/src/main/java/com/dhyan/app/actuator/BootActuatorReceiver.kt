package com.dhyan.app.actuator

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.dhyan.app.actuator.release.ReleaseJobScheduler
import com.dhyan.app.actuator.release.WorkManagerReleaseJobScheduler

/**
 * Restarts the periodic sweep on device reboot.
 * Requires RECEIVE_BOOT_COMPLETED permission in AndroidManifest.xml.
 */
class BootActuatorReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return
        context ?: return
        val scheduler: ReleaseJobScheduler = WorkManagerReleaseJobScheduler(context)
        scheduler.schedulePeriodicSweep()
    }
}

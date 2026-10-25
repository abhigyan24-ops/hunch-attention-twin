package com.dhyan.app.ingest

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

/**
 * Boot receiver — restarts [DhyanForegroundService] after device reboot.
 *
 * Required for persistent monitoring on Oppo Reno 14 / ColorOS.
 * Requires RECEIVE_BOOT_COMPLETED permission and android:exported="true" in manifest.
 *
 * Privacy: no data collected here — only restarts the service.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val serviceIntent = Intent(context, DhyanForegroundService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent)
        } else {
            context.startService(serviceIntent)
        }
    }
}

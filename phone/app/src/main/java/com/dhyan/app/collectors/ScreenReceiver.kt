package com.dhyan.app.collectors

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import com.dhyan.app.ingest.ClockOffset

/**
 * Screen state broadcast receiver.
 *
 * Listens for:
 * - [Intent.ACTION_SCREEN_OFF]    → phone locked/screen off
 * - [Intent.ACTION_SCREEN_ON]     → screen turned on (before unlock)
 * - [Intent.ACTION_USER_PRESENT]  → user unlocked the device (true pickup)
 *
 * Must be registered dynamically (not in manifest) because these broadcasts are not
 * deliverable to statically-registered receivers on Android 8+.
 *
 * Privacy: no content involved — pure screen state booleans.
 */
class ScreenReceiver : BroadcastReceiver() {

    /** Emits (eventKind, ts) where kind is "screen_off" | "screen_on" | "pickup". */
    var onScreenEvent: ((kind: String, ts: Long) -> Unit)? = null

    override fun onReceive(context: Context?, intent: Intent?) {
        val ts = ClockOffset.adjustedNow()
        val kind = when (intent?.action) {
            Intent.ACTION_SCREEN_OFF   -> "screen_off"
            Intent.ACTION_SCREEN_ON    -> "screen_on"
            Intent.ACTION_USER_PRESENT -> "pickup"
            else                       -> return
        }
        onScreenEvent?.invoke(kind, ts)
    }

    companion object {
        fun intentFilter(): IntentFilter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
        }
    }
}

package com.dhyan.app.qs

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.dhyan.app.MainActivity
import com.dhyan.app.widget.HunchFlowWidgetProvider

object LockScreenActionsHelper {
    const val ACTION_EXTEND_FLOW_10M = "com.hunch.action.EXTEND_FLOW_10M"
    const val ACTION_PARK_QUICK_NOTE = "com.hunch.action.PARK_QUICK_NOTE"
    const val ACTION_OPEN_BREAK_DIGEST = "com.hunch.action.OPEN_BREAK_DIGEST"

    fun extendRemainingMinutes(remainingMins: Int): Int =
        remainingMins.coerceIn(0, Int.MAX_VALUE - 10) + 10

    fun applyExtension(context: Context) {
        val prefs = CompanionPreferences.get(context)
        if (!prefs.getBoolean(CompanionPreferences.KEY_SHIELD_ACTIVE, false)) return
        val remaining = prefs.getInt(CompanionPreferences.KEY_SHIELD_REMAINING_MINS, 25)
        prefs.edit().putInt(CompanionPreferences.KEY_SHIELD_REMAINING_MINS, extendRemainingMinutes(remaining)).apply()
        HunchFlowWidgetProvider.refresh(context)
    }

    fun pendingIntent(context: Context, action: String): PendingIntent {
        val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        return when (action) {
            ACTION_EXTEND_FLOW_10M -> PendingIntent.getBroadcast(
                context, 0, Intent(context, HunchFlowWidgetProvider::class.java).setAction(action), flags,
            )
            ACTION_PARK_QUICK_NOTE, ACTION_OPEN_BREAK_DIGEST -> PendingIntent.getActivity(
                context, 0, Intent(context, MainActivity::class.java).setAction(action), flags,
            )
            else -> error("Unknown Hunch action")
        }
    }
}

package com.dhyan.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.dhyan.app.R
import com.dhyan.app.qs.CompanionPreferences
import com.dhyan.app.qs.LockScreenActionsHelper

data class WidgetDisplayModel(val flow: String, val shield: String, val peak: String)

class HunchFlowWidgetProvider : AppWidgetProvider() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            TOGGLE -> CompanionPreferences.toggle(context)
            LockScreenActionsHelper.ACTION_EXTEND_FLOW_10M -> LockScreenActionsHelper.applyExtension(context)
            else -> super.onReceive(context, intent)
        }
    }

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        if (ids.isEmpty()) return
        val prefs = CompanionPreferences.get(context)
        val model = formatWidgetState(
            prefs.getBoolean(CompanionPreferences.KEY_SHIELD_ACTIVE, false),
            prefs.getInt(CompanionPreferences.KEY_DEEP_WORK_MINUTES_TODAY, 0),
            prefs.getInt(CompanionPreferences.KEY_HELD_NOTIFICATIONS_COUNT, 0),
            prefs.getString(CompanionPreferences.KEY_PEAK_WINDOW_LABEL, "").orEmpty(),
        )
        val views = RemoteViews(context.packageName, R.layout.widget_hunch_flow).apply {
            setTextViewText(R.id.hunch_widget_flow, model.flow)
            setTextViewText(R.id.hunch_widget_shield, model.shield)
            setTextViewText(R.id.hunch_widget_peak, model.peak)
            setOnClickPendingIntent(R.id.hunch_widget_toggle, PendingIntent.getBroadcast(
                context, 0, Intent(context, HunchFlowWidgetProvider::class.java).setAction(TOGGLE),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            ))
            setOnClickPendingIntent(R.id.hunch_widget_park, LockScreenActionsHelper.pendingIntent(
                context, LockScreenActionsHelper.ACTION_PARK_QUICK_NOTE,
            ))
        }
        manager.updateAppWidget(ids, views)
    }

    companion object {
        private const val TOGGLE = "com.hunch.action.TOGGLE_WIDGET_SHIELD"

        fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val component = ComponentName(context, HunchFlowWidgetProvider::class.java)
            val ids = manager.getAppWidgetIds(component)
            if (ids.isNotEmpty()) context.sendBroadcast(Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE)
                .setComponent(component).putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids))
        }

        fun formatWidgetState(isActive: Boolean, deepWorkMins: Int, heldCount: Int, peakWindow: String): WidgetDisplayModel {
            val shield = if (isActive) "Shield ON" else "Shield off"
            val held = heldCount.coerceAtLeast(0)
            return WidgetDisplayModel(
                "${deepWorkMins.coerceAtLeast(0)}m Flow Today",
                if (held > 0) "$shield • $held held" else shield,
                if (peakWindow.isBlank()) "Peak window unavailable" else "Peak Window: ${peakWindow.trim()}",
            )
        }
    }
}

package com.dhyan.app.qs

import android.content.Context
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.dhyan.app.widget.HunchFlowWidgetProvider

object CompanionPreferences {
    const val NAME = "hunch_companion_prefs"
    const val KEY_SHIELD_ACTIVE = "hunch_shield_active"
    const val KEY_SHIELD_REMAINING_MINS = "hunch_shield_remaining_mins"
    const val KEY_HELD_NOTIFICATIONS_COUNT = "hunch_held_count"
    const val KEY_DEEP_WORK_MINUTES_TODAY = "hunch_deep_work_mins_today"
    const val KEY_PEAK_WINDOW_LABEL = "hunch_peak_window_label"

    fun get(context: Context) = context.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    fun toggle(context: Context) {
        val prefs = get(context)
        prefs.edit().putBoolean(KEY_SHIELD_ACTIVE, !prefs.getBoolean(KEY_SHIELD_ACTIVE, false)).apply()
        HunchFlowWidgetProvider.refresh(context)
    }
}

data class TilePresentation(val label: String, val subtitle: String, val state: Int)

class HunchQuickSettingsTileService : TileService() {
    override fun onStartListening() {
        super.onStartListening()
        render()
    }

    override fun onClick() {
        super.onClick()
        if (isLocked) unlockAndRun { toggle() } else toggle()
    }

    private fun toggle() {
        CompanionPreferences.toggle(this)
        render()
    }

    private fun render() {
        val prefs = CompanionPreferences.get(this)
        val presentation = computeTilePresentation(
            prefs.getBoolean(CompanionPreferences.KEY_SHIELD_ACTIVE, false),
            prefs.getInt(CompanionPreferences.KEY_SHIELD_REMAINING_MINS, 25),
            prefs.getInt(CompanionPreferences.KEY_HELD_NOTIFICATIONS_COUNT, 0),
        )
        qsTile?.apply {
            label = presentation.label
            if (Build.VERSION.SDK_INT >= 29) subtitle = presentation.subtitle
            state = presentation.state
            updateTile()
        }
    }

    companion object {
        fun computeTilePresentation(isActive: Boolean, remainingMins: Int, heldCount: Int): TilePresentation =
            if (isActive) TilePresentation(
                "Hunch Shield: ON", "${remainingMins.coerceAtLeast(0)}m left • ${heldCount.coerceAtLeast(0)} held",
                Tile.STATE_ACTIVE,
            ) else TilePresentation("Hunch Shield", "Tap to start focus", Tile.STATE_INACTIVE)
    }
}

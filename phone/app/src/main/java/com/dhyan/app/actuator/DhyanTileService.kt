package com.dhyan.app.actuator

import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.dhyan.app.R
import androidx.annotation.RequiresApi

/**
 * Quick Settings tile for Dhyan kill switch.
 * State: ACTIVE = monitoring, INACTIVE = kill switch ON (stopped).
 *
 * Requires BIND_QUICK_SETTINGS_TILE permission and tile declaration in AndroidManifest.
 */
@RequiresApi(Build.VERSION_CODES.N)
class DhyanTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        updateTile()
    }

    override fun onClick() {
        super.onClick()
        // Toggle kill switch state — in M6 this wires to real PolicySettings
        val tile = qsTile ?: return
        val newState = if (tile.state == Tile.STATE_ACTIVE) Tile.STATE_INACTIVE else Tile.STATE_ACTIVE
        tile.state = newState
        tile.updateTile()
    }

    private fun updateTile() {
        val tile = qsTile ?: return
        tile.label = getString(R.string.qs_tile_label)
        tile.state = Tile.STATE_ACTIVE // default active (monitoring)
        tile.updateTile()
    }
}

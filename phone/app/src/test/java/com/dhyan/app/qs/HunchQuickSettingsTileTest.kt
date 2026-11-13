package com.dhyan.app.qs

import android.service.quicksettings.Tile
import org.junit.Assert.assertEquals
import org.junit.Test

class HunchQuickSettingsTileTest {
    @Test fun rendersActiveAndInactiveStates() {
        val active = HunchQuickSettingsTileService.computeTilePresentation(true, 25, 3)
        assertEquals(TilePresentation("Hunch Shield: ON", "25m left • 3 held", Tile.STATE_ACTIVE), active)
        val idle = HunchQuickSettingsTileService.computeTilePresentation(false, 25, 3)
        assertEquals(TilePresentation("Hunch Shield", "Tap to start focus", Tile.STATE_INACTIVE), idle)
    }

    @Test fun extendsWithoutOverflow() {
        assertEquals(35, LockScreenActionsHelper.extendRemainingMinutes(25))
        assertEquals(10, LockScreenActionsHelper.extendRemainingMinutes(-1))
        assertEquals(Int.MAX_VALUE, LockScreenActionsHelper.extendRemainingMinutes(Int.MAX_VALUE))
    }
}

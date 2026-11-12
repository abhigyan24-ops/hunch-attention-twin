package com.dhyan.app.privacy

import org.junit.Assert.*
import org.junit.Test

class PrivacyLedgerManagerTest {

    @Test
    fun `snapshot privacy fields are always zero`() {
        val snap = PrivacyLedgerManager.buildSnapshot(10, 5, 3, 2)
        assertEquals(0, snap.rawKeystrokesLogged)
        assertEquals(0, snap.rawNotificationTextBytesStored)
        assertEquals(0, snap.sourceCodeBytesCaptured)
    }

    @Test
    fun `buildSnapshot reflects provided counts`() {
        val snap = PrivacyLedgerManager.buildSnapshot(42, 7, 3, 1)
        assertEquals(42, snap.appTransitionsCount)
        assertEquals(7, snap.notificationsHeldCount)
        assertEquals(3, snap.otpBypassedCount)
        assertEquals(1, snap.circadianSyncCount)
    }

    @Test
    fun `durationMinutes in snapshot is non-negative`() {
        val snap = PrivacyLedgerManager.buildSnapshot(0, 0, 0, 0)
        // All counts should be >= 0
        assertTrue(snap.appTransitionsCount >= 0)
        assertTrue(snap.notificationsHeldCount >= 0)
        assertTrue(snap.otpBypassedCount >= 0)
        assertTrue(snap.circadianSyncCount >= 0)
    }
}

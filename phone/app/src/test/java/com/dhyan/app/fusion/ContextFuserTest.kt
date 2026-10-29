package com.dhyan.app.fusion

import com.dhyan.app.twin.model.DeclaredSession
import com.dhyan.app.twin.model.FocusState
import com.dhyan.app.twin.model.LaptopState
import com.dhyan.app.twin.model.PhoneState
import com.dhyan.app.twin.model.TaskType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ContextFuserTest {

    private var currentTimeMs: Long = 100_000L

    private val fuser = ContextFuser(
        config = FusionConfig(
            laptopStaleMs = 15_000L,
            relayDownConfidenceDrop = 0.2,
            laptopStaleConfidenceDrop = 0.3
        ),
        timeProvider = { currentTimeMs }
    )

    @Test
    fun testInitialStateHasNoSnapshot() {
        assertNull(fuser.currentSnapshot())
        assertTrue(fuser.isLaptopStale())
        assertFalse(fuser.isRelayConnected())
    }

    @Test
    fun testPhoneUpdateProducesSnapshot() {
        fuser.updatePhoneState(
            phoneState = PhoneState.IN_APP,
            phoneAppCat = "comms",
            taskType = TaskType.WRITING,
            hourBand = "afternoon",
            fatigueBand = "low",
            baseConfidence = 0.9
        )

        val snap = fuser.currentSnapshot()
        assertNotNull(snap)
        assertEquals(PhoneState.IN_APP, snap!!.phoneState)
        assertEquals(LaptopState.OFFLINE, snap.laptopState)
        assertFalse(snap.secondScreenOverlap)
        // relay disconnected (-0.2) and laptop stale (-0.3) -> 0.9 - 0.5 = 0.4
        assertEquals(0.4, snap.confidence, 0.001)
    }

    @Test
    fun testRelayConnectionAndFreshLaptopRestoresConfidence() {
        fuser.updatePhoneState(phoneState = PhoneState.IN_APP, baseConfidence = 0.9)
        fuser.updateRelayConnectivity(true)

        // Relay connected (+0.2 restored), but laptop still stale (-0.3) -> 0.6
        var snap = fuser.currentSnapshot()
        assertNotNull(snap)
        assertEquals(0.6, snap!!.confidence, 0.001)

        // Laptop fresh (+0.3 restored) -> 0.9
        fuser.updateLaptopState(LaptopState.FLOWING, receivedAtMs = currentTimeMs)
        snap = fuser.currentSnapshot()
        assertEquals(0.9, snap!!.confidence, 0.001)
    }

    @Test
    fun testLaptopStateIngestionAndStalenessTimeout() {
        fuser.updateRelayConnectivity(true)
        fuser.updatePhoneState(phoneState = PhoneState.SCREEN_OFF)

        // Ingest laptop state at t = 100_000
        fuser.updateLaptopState(LaptopState.FLOWING, receivedAtMs = currentTimeMs)

        var snap = fuser.currentSnapshot()
        assertNotNull(snap)
        assertEquals(LaptopState.FLOWING, snap!!.laptopState)
        assertFalse(fuser.isLaptopStale())

        // Advance 10s (within 15s staleness window)
        currentTimeMs += 10_000L
        fuser.recompute()
        snap = fuser.currentSnapshot()
        assertEquals(LaptopState.FLOWING, snap!!.laptopState)
        assertFalse(fuser.isLaptopStale())

        // Advance another 6s (total 16s > 15s staleness timeout)
        currentTimeMs += 6_000L
        fuser.recompute()
        snap = fuser.currentSnapshot()
        assertEquals(LaptopState.OFFLINE, snap!!.laptopState)
        assertTrue(fuser.isLaptopStale())
    }

    @Test
    fun testDeclaredSessionPreference() {
        val phoneSession = DeclaredSession(TaskType.WRITING, 30.0, false, 90_000L)
        val laptopSession = DeclaredSession(TaskType.CODING, 45.0, true, 95_000L)

        fuser.updatePhoneState(phoneState = PhoneState.IN_APP, declaredSession = phoneSession)
        assertEquals(phoneSession, fuser.currentSnapshot()?.declaredSession)

        // Remote laptop declared session overrides phone declared session
        fuser.updateLaptopState(LaptopState.FLOWING, declaredSession = laptopSession, receivedAtMs = currentTimeMs)
        assertEquals(laptopSession, fuser.currentSnapshot()?.declaredSession)

        // When remote declared session becomes null, falls back to phone session
        fuser.updateLaptopState(LaptopState.FLOWING, declaredSession = null, receivedAtMs = currentTimeMs)
        assertEquals(phoneSession, fuser.currentSnapshot()?.declaredSession)
    }

    @Test
    fun testResetClearsRemoteState() {
        fuser.updateRelayConnectivity(true)
        fuser.updatePhoneState(phoneState = PhoneState.IN_APP)
        fuser.updateLaptopState(LaptopState.FLOWING, receivedAtMs = currentTimeMs)
        assertNotNull(fuser.currentSnapshot())

        fuser.reset()
        assertNull(fuser.currentSnapshot())
        assertTrue(fuser.isLaptopStale())
        assertFalse(fuser.isRelayConnected())
    }
}

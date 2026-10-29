package com.dhyan.app.fusion

import com.dhyan.app.twin.model.LaptopState
import com.dhyan.app.twin.model.PhoneState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SecondScreenDetectorTest {

    private val detector = SecondScreenDetector(
        config = FusionConfig(
            secondScreenDebounceMs = 10_000L,
            secondScreenHysteresisMs = 5_000L
        )
    )

    @Test
    fun testDebounce10sRequiredBeforeOverlapTrue() {
        var t = 1_000L

        // t = 1s: both active
        assertFalse(detector.update(PhoneState.IN_APP, LaptopState.FLOWING, isLaptopStale = false, nowMs = t))

        // t = 6s (5s elapsed): still false
        t += 5_000L
        assertFalse(detector.update(PhoneState.IN_APP, LaptopState.FLOWING, isLaptopStale = false, nowMs = t))

        // t = 10.9s (9.9s elapsed): still false
        t += 4_900L
        assertFalse(detector.update(PhoneState.IN_APP, LaptopState.FLOWING, isLaptopStale = false, nowMs = t))

        // t = 11.1s (10.1s elapsed): now true!
        t += 200L
        assertTrue(detector.update(PhoneState.IN_APP, LaptopState.FLOWING, isLaptopStale = false, nowMs = t))
    }

    @Test
    fun testHysteresis5sOnExit() {
        var t = 1_000L

        // Achieve overlap
        detector.update(PhoneState.IN_APP, LaptopState.FLOWING, isLaptopStale = false, nowMs = t)
        t += 10_000L
        assertTrue(detector.update(PhoneState.IN_APP, LaptopState.FLOWING, isLaptopStale = false, nowMs = t))

        // Phone screen turns off at t + 10s
        t += 1_000L
        // During 5s hysteresis, overlap remains true
        assertTrue(detector.update(PhoneState.SCREEN_OFF, LaptopState.FLOWING, isLaptopStale = false, nowMs = t))

        t += 3_000L // 4s elapsed since screen off
        assertTrue(detector.update(PhoneState.SCREEN_OFF, LaptopState.FLOWING, isLaptopStale = false, nowMs = t))

        t += 2_100L // 6.1s elapsed (> 5s)
        assertFalse(detector.update(PhoneState.SCREEN_OFF, LaptopState.FLOWING, isLaptopStale = false, nowMs = t))
    }

    @Test
    fun testStaleOrOfflineLaptopNeverTriggersOverlap() {
        var t = 1_000L

        // Offline laptop
        detector.update(PhoneState.IN_APP, LaptopState.OFFLINE, isLaptopStale = false, nowMs = t)
        t += 12_000L
        assertFalse(detector.update(PhoneState.IN_APP, LaptopState.OFFLINE, isLaptopStale = false, nowMs = t))

        // Stale laptop
        detector.reset()
        t = 1_000L
        detector.update(PhoneState.IN_APP, LaptopState.FLOWING, isLaptopStale = true, nowMs = t)
        t += 12_000L
        assertFalse(detector.update(PhoneState.IN_APP, LaptopState.FLOWING, isLaptopStale = true, nowMs = t))
    }

    @Test
    fun testEligibleLaptopStates() {
        listOf(LaptopState.FLOWING, LaptopState.STUCK, LaptopState.IDLE_OPEN).forEach { state ->
            val d = SecondScreenDetector()
            d.update(PhoneState.SCROLLING, state, isLaptopStale = false, nowMs = 1_000L)
            assertTrue(d.update(PhoneState.SCROLLING, state, isLaptopStale = false, nowMs = 11_100L))
        }

        listOf(LaptopState.AWAY, LaptopState.GAMING, LaptopState.OFFLINE).forEach { state ->
            val d = SecondScreenDetector()
            d.update(PhoneState.SCROLLING, state, isLaptopStale = false, nowMs = 1_000L)
            assertFalse(d.update(PhoneState.SCROLLING, state, isLaptopStale = false, nowMs = 11_100L))
        }
    }
}

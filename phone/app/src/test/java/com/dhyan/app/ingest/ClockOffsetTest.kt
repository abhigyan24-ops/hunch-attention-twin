package com.dhyan.app.ingest

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ClockOffsetTest {

    @Before
    fun setup() {
        ClockOffset.reset()
    }

    @Test
    fun testClockOffsetEstimatorWithSmallestRtt() {
        assertTrue(ClockOffset.isEstimated())
        assertEquals(0L, ClockOffset.currentOffsetMs())

        // Sample 1: RTT = (100 - 0) - (60 - 40) = 100 - 20 = 80ms
        // Offset = ((40 - 0) + (60 - 100)) / 2 = (40 - 40) / 2 = 0ms
        ClockOffset.recordSample(t0 = 0L, t1 = 40L, t2 = 60L, t3 = 100L)
        assertFalse(ClockOffset.isEstimated())
        assertEquals(0L, ClockOffset.currentOffsetMs())

        // Sample 2 (smaller RTT): RTT = (80 - 0) - (50 - 45) = 80 - 5 = 75ms
        // Peer clock is 100ms ahead:
        // t1 = 145, t2 = 150, t0 = 0, t3 = 80
        // RTT = (80 - 0) - (150 - 145) = 75ms
        // Offset = ((145 - 0) + (150 - 80)) / 2 = (145 + 70) / 2 = 107ms
        ClockOffset.recordSample(t0 = 0L, t1 = 145L, t2 = 150L, t3 = 80L)
        assertEquals(107L, ClockOffset.currentOffsetMs())
        assertEquals(-107L, ClockOffset.offsetToPhone())

        // Sample 3 (larger RTT): should not overwrite smaller RTT sample
        ClockOffset.recordSample(t0 = 0L, t1 = 300L, t2 = 350L, t3 = 200L) // RTT = 150ms
        assertEquals(107L, ClockOffset.currentOffsetMs())

        // Sample 4 (RTT > 2000ms): should be completely ignored
        ClockOffset.recordSample(t0 = 0L, t1 = 1000L, t2 = 1000L, t3 = 3000L)
        assertEquals(107L, ClockOffset.currentOffsetMs())
    }

    @Test
    fun testTimestampAdjustmentToPhoneTime() {
        // Peer clock is 5000ms ahead of phone
        // t0 = 0, t1 = 5020, t2 = 5030, t3 = 50
        // RTT = (50 - 0) - (5030 - 5020) = 40ms
        // Offset = ((5020 - 0) + (5030 - 50)) / 2 = (5020 + 4980) / 2 = 5000ms
        ClockOffset.recordSample(t0 = 0L, t1 = 5020L, t2 = 5030L, t3 = 50L)

        val laptopEventTs = 15_000L
        val adjustedInPhoneTime = ClockOffset.adjustIncomingTs(laptopEventTs)
        assertEquals(10_000L, adjustedInPhoneTime)
    }
}

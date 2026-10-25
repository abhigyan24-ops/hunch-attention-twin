package com.dhyan.app.twin.rng

import org.junit.Assert.*
import org.junit.Test

class SeededRngTest {

    @Test
    fun sameSeedProducesIdenticalSequence() {
        val rng1 = SeededRng(123456789L)
        val rng2 = SeededRng(123456789L)

        for (i in 0 until 1000) {
            assertEquals("Long output differed at step $i", rng1.nextLong(), rng2.nextLong())
            assertEquals("Double output differed at step $i", rng1.nextDouble(), rng2.nextDouble(), 0.0)
        }
    }

    @Test
    fun differentSeedsProduceDifferentSequences() {
        val rng1 = SeededRng(100L)
        val rng2 = SeededRng(200L)

        var matches = 0
        for (i in 0 until 100) {
            if (rng1.nextLong() == rng2.nextLong()) matches++
        }
        assertTrue("Too many matches between different seeds: $matches", matches < 5)
    }

    @Test
    fun nextOpenDoubleNeverReturnsZero() {
        val rng = SeededRng(999L)
        for (i in 0 until 10000) {
            val v = rng.nextOpenDouble()
            assertTrue("nextOpenDouble() must be strictly positive", v > 0.0)
            assertTrue("nextOpenDouble() must be strictly less than 1.0", v < 1.0)
        }
    }

    @Test
    fun rngStreamsAreIndependentAcrossTagsAndRuns() {
        val masterSeed = 42L
        val epistemic0 = RngStreams.stream(masterSeed, 0, StreamTag.EPISTEMIC)
        val self0 = RngStreams.stream(masterSeed, 0, StreamTag.SELF)
        val notif0 = RngStreams.stream(masterSeed, 0, StreamTag.NOTIF)
        val epistemic1 = RngStreams.stream(masterSeed, 1, StreamTag.EPISTEMIC)

        assertNotEquals(epistemic0.nextLong(), self0.nextLong())
        assertNotEquals(epistemic0.nextLong(), notif0.nextLong())
        assertNotEquals(epistemic0.nextLong(), epistemic1.nextLong())
    }
}

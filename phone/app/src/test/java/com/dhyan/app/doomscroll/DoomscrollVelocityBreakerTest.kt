package com.dhyan.app.doomscroll

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DoomscrollVelocityBreakerTest {

    private lateinit var breaker: DoomscrollVelocityBreaker

    @Before
    fun setUp() {
        breaker = DoomscrollVelocityBreaker()
    }

    @Test
    fun `high-risk context triggers gentle nudge at 4 mins and firm breaker at 8 mins`() {
        val pkg = "com.instagram.android"
        var t = 1_000_000L

        // Initial sample
        val s0 = breaker.evaluateForegroundSample(pkg, t, isHighRiskContext = true)
        assertEquals(DoomscrollStage.NONE, s0.stage)

        // Advance 240 seconds (4 minutes) in 10-second intervals
        for (i in 1..24) {
            t += 10_000L
            breaker.evaluateForegroundSample(pkg, t, isHighRiskContext = true)
        }

        // At 240s -> GENTLE_NUDGE
        val s240 = breaker.evaluateForegroundSample(pkg, t, isHighRiskContext = true)
        assertEquals(DoomscrollStage.GENTLE_NUDGE, s240.stage)
        assertNotNull(s240.humanMessage)
        assertTrue(s240.humanMessage!!.contains("Instagram"))
        assertTrue(s240.humanMessage!!.contains("Save the rest for break"))

        // Advance to 480 seconds (8 minutes)
        for (i in 1..24) {
            t += 10_000L
            breaker.evaluateForegroundSample(pkg, t, isHighRiskContext = true)
        }

        // At 480s -> FIRM_BREAKER
        val s480 = breaker.evaluateForegroundSample(pkg, t, isHighRiskContext = true)
        assertEquals(DoomscrollStage.FIRM_BREAKER, s480.stage)
        assertNotNull(s480.humanMessage)
        assertTrue(s480.humanMessage!!.contains("Tap to close and reclaim your flow"))
    }

    @Test
    fun `standard context triggers gentle nudge at 8 mins and firm breaker at 15 mins`() {
        val pkg = "com.zhiliaoapp.musically" // TikTok
        var t = 1_000_000L

        breaker.evaluateForegroundSample(pkg, t, isHighRiskContext = false)

        // Advance 4 minutes (240s) -> Should still be NONE in standard context
        for (i in 1..24) {
            t += 10_000L
            breaker.evaluateForegroundSample(pkg, t, isHighRiskContext = false)
        }
        val s240 = breaker.evaluateForegroundSample(pkg, t, isHighRiskContext = false)
        assertEquals(DoomscrollStage.NONE, s240.stage)

        // Advance to 8 minutes (480s) -> GENTLE_NUDGE
        for (i in 1..24) {
            t += 10_000L
            breaker.evaluateForegroundSample(pkg, t, isHighRiskContext = false)
        }
        val s480 = breaker.evaluateForegroundSample(pkg, t, isHighRiskContext = false)
        assertEquals(DoomscrollStage.GENTLE_NUDGE, s480.stage)
        assertTrue(s480.humanMessage!!.contains("quick breath"))

        // Advance to 15 minutes (900s) -> FIRM_BREAKER
        for (i in 1..42) {
            t += 10_000L
            breaker.evaluateForegroundSample(pkg, t, isHighRiskContext = false)
        }
        val s900 = breaker.evaluateForegroundSample(pkg, t, isHighRiskContext = false)
        assertEquals(DoomscrollStage.FIRM_BREAKER, s900.stage)
    }

    @Test
    fun `brief switch away under 15s preserves accumulated dwell time`() {
        val pkg = "com.reddit.frontpage"
        var t = 1_000_000L

        breaker.evaluateForegroundSample(pkg, t, isHighRiskContext = true)

        // 200 seconds of scrolling
        for (i in 1..20) {
            t += 10_000L
            breaker.evaluateForegroundSample(pkg, t, isHighRiskContext = true)
        }
        val dwellBefore = breaker.getAccumulatedDwellSeconds()
        assertEquals(200L, dwellBefore)

        // Switch to Settings for 10 seconds (<= 15s tolerance)
        t += 10_000L
        breaker.evaluateForegroundSample("com.android.settings", t, isHighRiskContext = true)

        // Switch back to Reddit
        t += 2_000L
        breaker.evaluateForegroundSample(pkg, t, isHighRiskContext = true)

        // Dwell time preserved
        val dwellAfter = breaker.getAccumulatedDwellSeconds()
        assertEquals(200L, dwellAfter)

        // Another 40 seconds of scrolling -> hits 240s threshold!
        for (i in 1..4) {
            t += 10_000L
            breaker.evaluateForegroundSample(pkg, t, isHighRiskContext = true)
        }
        val status = breaker.evaluateForegroundSample(pkg, t, isHighRiskContext = true)
        assertEquals(DoomscrollStage.GENTLE_NUDGE, status.stage)
    }

    @Test
    fun `switching away for 60s or more resets accumulated dwell time`() {
        val pkg = "com.google.android.youtube"
        var t = 1_000_000L

        breaker.evaluateForegroundSample(pkg, t, isHighRiskContext = true)

        // 200 seconds of scrolling
        for (i in 1..20) {
            t += 10_000L
            breaker.evaluateForegroundSample(pkg, t, isHighRiskContext = true)
        }
        assertEquals(200L, breaker.getAccumulatedDwellSeconds())

        // Switch away to Slack for 65 seconds (>= 60s reset)
        t += 65_000L
        breaker.evaluateForegroundSample("com.slack", t, isHighRiskContext = true)

        // Return to YouTube
        t += 5_000L
        val returnStatus = breaker.evaluateForegroundSample(pkg, t, isHighRiskContext = true)
        assertEquals(0L, returnStatus.accumulatedDwellSeconds)
        assertEquals(DoomscrollStage.NONE, returnStatus.stage)
    }
}

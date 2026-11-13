package com.dhyan.app.walkaway

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WalkAwayDetectorTest {

    private fun generateRhythmicSteps(count: Int, intervalMs: Long = 550L, startMs: Long = 1000L): List<Long> {
        val list = mutableListOf<Long>()
        var current = startMs
        for (i in 0 until count) {
            list.add(current)
            // Add subtle natural jitter (within +/- 20ms)
            val jitter = if (i % 2 == 0) 15L else -15L
            current += (intervalMs + jitter)
        }
        return list
    }

    @Test
    fun `confirmed walk-away with rhythmic steps, laptop idle, and RSSI drop`() {
        val steps = generateRhythmicSteps(14, intervalMs = 550L)
        val window = WalkAwaySensorWindow(
            stepTimestampsMillis = steps,
            accelRms = 2.4f,
            isScreenInteractive = false,
            hasRecentForegroundTouch = false,
            baselineRssiDbm = -50,
            currentRssiDbm = -58, // 8 dBm drop >= 6
            justUnpluggedCharger = false,
            liftedFromFlipToFocus = false,
            laptopIdleSeconds = 50,
            inCall = false
        )

        val assessment = WalkAwayEvaluator.evaluateWalkAway(window)

        assertEquals(WalkAwayState.WALK_AWAY_CONFIRMED, assessment.state)
        assertTrue(assessment.confidence >= 0.75f)
        assertTrue(assessment.gate1CadenceMet)
        assertFalse(assessment.gate2ScreenPocketVeto)
        assertTrue(assessment.gate3DepartureMet)
        assertTrue(assessment.gate4IdleMet)
    }

    @Test
    fun `erratic desk foot-tapping or leg bouncing is rejected`() {
        // High variance intervals (e.g. 150ms, 900ms, 200ms)
        val erraticSteps = listOf(
            1000L, 1150L, 2050L, 2250L, 3500L, 3700L, 4800L, 5100L, 6200L, 6400L, 7500L, 7700L
        )
        val window = WalkAwaySensorWindow(
            stepTimestampsMillis = erraticSteps,
            accelRms = 0.8f, // Below human walking threshold
            isScreenInteractive = false,
            hasRecentForegroundTouch = false,
            baselineRssiDbm = -50,
            currentRssiDbm = -50,
            laptopIdleSeconds = 60,
            inCall = false
        )

        val assessment = WalkAwayEvaluator.evaluateWalkAway(window)

        assertEquals(WalkAwayState.UNDETERMINED, assessment.state)
        assertFalse(assessment.gate1CadenceMet)
        assertTrue(assessment.confidence < 0.75f)
    }

    @Test
    fun `at-desk phone scrolling triggers AT_DESK_PHONE_DRIFT hard veto`() {
        // Only 5 steps (e.g. shifting in chair), screen interactive with active touch
        val window = WalkAwaySensorWindow(
            stepTimestampsMillis = listOf(1000L, 1600L, 2200L, 2800L, 3400L),
            accelRms = 1.0f,
            isScreenInteractive = true,
            hasRecentForegroundTouch = true,
            baselineRssiDbm = -50,
            currentRssiDbm = -51,
            laptopIdleSeconds = 25,
            inCall = false
        )

        val assessment = WalkAwayEvaluator.evaluateWalkAway(window)

        assertEquals(WalkAwayState.AT_DESK_PHONE_DRIFT, assessment.state)
        assertEquals(0.0f, assessment.confidence, 0.001f)
        assertTrue(assessment.gate2ScreenPocketVeto)
    }

    @Test
    fun `pacing during call triggers PACING_ON_CALL hard veto`() {
        val steps = generateRhythmicSteps(16, intervalMs = 520L)
        val window = WalkAwaySensorWindow(
            stepTimestampsMillis = steps,
            accelRms = 2.2f,
            isScreenInteractive = false,
            hasRecentForegroundTouch = false,
            baselineRssiDbm = -50,
            currentRssiDbm = -60,
            laptopIdleSeconds = 120,
            inCall = true // User is in a meeting/call
        )

        val assessment = WalkAwayEvaluator.evaluateWalkAway(window)

        assertEquals(WalkAwayState.PACING_ON_CALL, assessment.state)
        assertEquals(0.0f, assessment.confidence, 0.001f)
    }

    @Test
    fun `laptop active less than 15s triggers AT_DESK_ACTIVE hard veto`() {
        val steps = generateRhythmicSteps(14, intervalMs = 540L)
        val window = WalkAwaySensorWindow(
            stepTimestampsMillis = steps,
            accelRms = 2.0f,
            isScreenInteractive = false,
            hasRecentForegroundTouch = false,
            laptopIdleSeconds = 10, // User just typed 10 seconds ago
            inCall = false
        )

        val assessment = WalkAwayEvaluator.evaluateWalkAway(window)

        assertEquals(WalkAwayState.AT_DESK_ACTIVE, assessment.state)
        assertEquals(0.0f, assessment.confidence, 0.001f)
    }

    @Test
    fun `departure boost via unplugged charger or flip-to-focus lift`() {
        val steps = generateRhythmicSteps(12, intervalMs = 600L)
        val window = WalkAwaySensorWindow(
            stepTimestampsMillis = steps,
            accelRms = 2.1f,
            isScreenInteractive = false,
            hasRecentForegroundTouch = false,
            justUnpluggedCharger = true,
            laptopIdleSeconds = 45,
            inCall = false
        )

        val assessment = WalkAwayEvaluator.evaluateWalkAway(window)

        assertEquals(WalkAwayState.WALK_AWAY_CONFIRMED, assessment.state)
        assertTrue(assessment.gate3DepartureMet)
        assertEquals(1.0f, assessment.confidence, 0.001f)
    }

    @Test
    fun `return to desk evaluation on keyboard activity or stationary flip-to-focus`() {
        // Laptop keystroke / mouse move -> idle 2s
        assertTrue(WalkAwayEvaluator.evaluateReturnToDesk(laptopIdleSeconds = 2, isFlipToFocusActive = false, secondsSinceLastStep = 0))

        // Phone placed face-down and stationary for 12s
        assertTrue(WalkAwayEvaluator.evaluateReturnToDesk(laptopIdleSeconds = 60, isFlipToFocusActive = true, secondsSinceLastStep = 12))

        // Still walking away
        assertFalse(WalkAwayEvaluator.evaluateReturnToDesk(laptopIdleSeconds = 15, isFlipToFocusActive = false, secondsSinceLastStep = 2))
    }
}

package com.dhyan.app.twin.inference

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CompulsiveScoreTest {

    private lateinit var model: CompulsiveScore

    @Before
    fun setUp() {
        model = CompulsiveScore(eta = 0.05, lambda = 0.01)
    }

    @Test
    fun calmWorkingSessionClassifiesAsSilent() {
        val calmSignals = CompulsiveSignals(
            switchRate = 0.2,
            scrollRate = 0.0,
            isShortsOrFeed = false,
            engagement = 2.0,
            declaredStudy = true,
            otherDeviceWorkActivity = true,
            secondScreenOverlap = false,
            lateHour = false
        )
        val result = model.evaluate(calmSignals)
        assertEquals(CompulsiveAction.SILENT, result.action)
        assertTrue("Probability should be < 0.35, got ${result.probability}", result.probability < 0.35)
    }

    @Test
    fun highFeedAndLateHourClassifiesAsGentleNudge() {
        val compulsiveSignals = CompulsiveSignals(
            switchRate = 4.5,
            scrollRate = 6.0,
            isShortsOrFeed = true,
            engagement = 0.0,
            declaredStudy = false,
            otherDeviceWorkActivity = false,
            secondScreenOverlap = true,
            lateHour = true
        )
        val result = model.evaluate(compulsiveSignals)
        assertEquals(CompulsiveAction.GENTLE_NUDGE, result.action)
        assertTrue("Probability should be > 0.65, got ${result.probability}", result.probability > 0.65)
    }

    @Test
    fun moderateDriftTriggersPromptOnce() {
        val moderateSignals = CompulsiveSignals(
            switchRate = 0.8,
            scrollRate = 1.5,
            isShortsOrFeed = false,
            engagement = 0.2,
            declaredStudy = false,
            otherDeviceWorkActivity = false,
            secondScreenOverlap = false,
            lateHour = false
        )
        val result = model.evaluate(moderateSignals)
        assertTrue(
            "Expected probability in [0.35, 0.65], got ${result.probability}",
            result.probability in 0.35..0.65
        )
        assertEquals(CompulsiveAction.PROMPT_ONCE, result.action)
    }

    @Test
    fun onlineSgdUpdatesWeightsTowardsFeedback() {
        val ambiguousSignals = CompulsiveSignals(
            switchRate = 1.5,
            scrollRate = 1.2,
            isShortsOrFeed = false,
            engagement = 0.2
        )
        val initialProb = model.evaluate(ambiguousSignals).probability

        // User repeatedly flags drift / compulsive (y = 1.0)
        repeat(5) {
            model.update(ambiguousSignals, y = 1.0)
        }
        val probAfterCompulsive = model.evaluate(ambiguousSignals).probability
        assertTrue("Probability should increase after compulsive feedback", probAfterCompulsive > initialProb)

        // User repeatedly flags intentional (y = 0.0)
        repeat(15) {
            model.update(ambiguousSignals, y = 0.0)
        }
        val probAfterIntentional = model.evaluate(ambiguousSignals).probability
        assertTrue("Probability should decrease after intentional feedback", probAfterIntentional < probAfterCompulsive)
    }
}

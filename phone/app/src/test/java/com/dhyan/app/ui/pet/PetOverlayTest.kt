package com.dhyan.app.ui.pet

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.*
import org.junit.Test

class PetOverlayTest {
    @Test fun inactivitySleepsAtEighteenSecondsAndTouchWakes() {
        val behavior = PetOverlayBehavior()
        behavior.advance(17_999, false, false)
        assertEquals(PetEmotion.CURIOUS, behavior.emotion)
        behavior.advance(18_000, false, false)
        assertEquals(PetEmotion.SLEEPY, behavior.emotion)
        behavior.touch(Offset(20f, 40f), false)
        behavior.advance(18_001, false, false)
        assertEquals(PetEmotion.CURIOUS, behavior.emotion)
        assertEquals(Offset(20f, 40f), behavior.lookTarget)
    }

    @Test fun activeTaskPreventsSleepWithoutInventingMeditation() {
        val behavior = PetOverlayBehavior()
        behavior.advance(90_000, true, false)
        assertEquals(PetEmotion.FOCUSED, behavior.emotion)
        behavior.advance(90_001, false, false)
        assertEquals(PetEmotion.SLEEPY, behavior.emotion)
    }

    @Test fun globalMovementFocusesOnlyBriefly() {
        val behavior = PetOverlayBehavior()
        behavior.advance(500, false, false)
        behavior.touch(Offset(30f, 40f), true)
        behavior.advance(501, false, false)
        assertEquals(PetEmotion.FOCUSED, behavior.emotion)
        behavior.advance(1700, false, false)
        assertEquals(PetEmotion.CURIOUS, behavior.emotion)
    }

    @Test fun directReactionHasPriorityOverTaskThenExpires() {
        val behavior = PetOverlayBehavior()
        behavior.react(PetEmotion.HAPPY, 1500, null)
        behavior.advance(1499, true, false)
        assertEquals(PetEmotion.HAPPY, behavior.emotion)
        behavior.advance(1500, true, false)
        assertEquals(PetEmotion.FOCUSED, behavior.emotion)
    }

    @Test fun dragPreventsSleepAndDizzyOverridesDragForTwoPointFiveSeconds() {
        val behavior = PetOverlayBehavior()
        behavior.advance(20_000, false, true)
        assertEquals(PetEmotion.SURPRISED, behavior.emotion)
        behavior.react(PetEmotion.DIZZY, 2500, "Whoa...")
        behavior.advance(22_499, true, true)
        assertEquals(PetEmotion.DIZZY, behavior.emotion)
        behavior.advance(22_500, true, true)
        assertEquals(PetEmotion.SURPRISED, behavior.emotion)
        behavior.advance(22_501, true, false)
        assertEquals(PetEmotion.FOCUSED, behavior.emotion)
    }

    @Test fun continuedPettingSustainsLoveWithoutRequiringAnotherBubble() {
        val behavior = PetOverlayBehavior()
        behavior.react(PetEmotion.LOVE, 2000, "Yay! ✨")
        behavior.advance(1900, false, true)
        behavior.react(PetEmotion.LOVE, 2000, null)
        behavior.advance(3000, false, true)
        assertEquals(PetEmotion.LOVE, behavior.emotion)
        assertNull(behavior.speechBubble)
    }

    @Test fun speechExpiresAndInvalidTouchDoesNotWake() {
        val behavior = PetOverlayBehavior()
        behavior.react(PetEmotion.LOVE, 2000, "Yay! ✨")
        behavior.advance(2499, false, false)
        assertNotNull(behavior.speechBubble)
        behavior.advance(2500, false, false)
        assertNull(behavior.speechBubble)
        behavior.advance(18_000, false, false)
        behavior.touch(Offset(Float.NaN, 10f), false)
        behavior.advance(18_001, false, false)
        assertEquals(PetEmotion.SLEEPY, behavior.emotion)
    }

    @Test fun frameClockNeverRunsBackwards() {
        val behavior = PetOverlayBehavior()
        behavior.advance(10_000, false, false)
        behavior.react(PetEmotion.HAPPY, 1500, null)
        behavior.advance(5000, false, false)
        assertEquals(PetEmotion.HAPPY, behavior.emotion)
        behavior.advance(11_500, false, false)
        assertEquals(PetEmotion.CURIOUS, behavior.emotion)
    }

    @Test fun gentleThreeReversalsRecognizePetting() {
        val detector = PetGestureClassifier(Offset(100f, 100f), 0, 96f, 2.5f)
        val results = listOf(112f, 88f, 112f, 88f).mapIndexed { index, x ->
            detector.observe(Offset(x, 100f), (index + 1) * 100L)
        }
        assertTrue(results.take(3).all { it == PetGestureSignal.NONE })
        assertEquals(PetGestureSignal.LOVE, results.last())
    }

    @Test fun vigorousRepeatedShakingHasPriorityOverGentlePetting() {
        val detector = PetGestureClassifier(Offset(100f, 100f), 0, 96f, 2.5f)
        var result = PetGestureSignal.NONE
        repeat(6) { index ->
            result = detector.observe(
                Offset(if (index % 2 == 0) 180f else 20f, if (index % 2 == 0) 80f else 120f),
                (index + 1) * 100L)
        }
        assertEquals(PetGestureSignal.DIZZY, result)
    }

    @Test fun oldReversalsCannotAccumulatePastNineHundredMilliseconds() {
        val detector = PetGestureClassifier(Offset(100f, 100f), 0, 96f, 2.5f)
        detector.observe(Offset(112f, 100f), 100)
        detector.observe(Offset(88f, 100f), 200)
        detector.observe(Offset(112f, 100f), 1200)
        detector.observe(Offset(88f, 100f), 1300)
        assertEquals(PetGestureSignal.NONE, detector.observe(Offset(112f, 100f), 1400))
    }

    @Test fun verticalDraggingIsNotMistakenForHeadRub() {
        val detector = PetGestureClassifier(Offset(100f, 100f), 0, 96f, 2.5f)
        var result = PetGestureSignal.NONE
        repeat(4) { index ->
            result = detector.observe(
                Offset(if (index % 2 == 0) 112f else 88f, 100f + (index + 1) * 40f),
                (index + 1) * 100L)
        }
        assertEquals(PetGestureSignal.NONE, result)
    }

    @Test fun invalidPositionsAndClockRollbackDoNotCreateGestureEvidence() {
        val detector = PetGestureClassifier(Offset(100f, 100f), 100, 96f, 2.5f)
        assertEquals(PetGestureSignal.NONE, detector.observe(Offset(Float.NaN, 0f), 101))
        detector.observe(Offset(112f, 100f), 200)
        detector.observe(Offset(88f, 100f), 300)
        assertEquals(PetGestureSignal.NONE, detector.observe(Offset(112f, 100f), 50))
        assertEquals(PetGestureSignal.NONE, detector.observe(Offset(88f, 100f), 60))
    }
}

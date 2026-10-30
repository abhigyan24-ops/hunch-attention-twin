package com.dhyan.app.ui.pet

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.*
import org.junit.Test

class PetEdgeCaseTest {
    @Test fun rubWindowIncludesNineHundredMillisecondsButNotNineHundredAndOne() {
        fun classify(lastMs: Long): PetGestureSignal {
            val detector = PetGestureClassifier(Offset(100f, 100f), 0L, 96f, 2.5f)
            detector.observe(Offset(112f, 100f), 100L)
            detector.observe(Offset(88f, 100f), 300L)
            detector.observe(Offset(112f, 100f), 600L)
            return detector.observe(Offset(88f, 100f), lastMs)
        }
        assertEquals(PetGestureSignal.LOVE, classify(900L))
        assertEquals(PetGestureSignal.NONE, classify(901L))
    }

    @Test fun sameDpRubWorksAcrossPixelDensities() {
        listOf(1f, 2f, 2.625f, 3f).forEach { density ->
            val detector = PetGestureClassifier(Offset(100f, 100f) * density,
                0L, 96f * density, 2.5f * density)
            var result = PetGestureSignal.NONE
            listOf(112f, 88f, 112f, 88f).forEachIndexed { index, x ->
                result = detector.observe(Offset(x, 100f) * density, (index + 1) * 100L)
            }
            assertEquals("density=$density", PetGestureSignal.LOVE, result)
        }
    }

    @Test fun fineGrainedMotionStillRecognizesThreeReversals() {
        val detector = PetGestureClassifier(Offset(100f, 100f), 0L, 96f, 2.5f)
        var x = 100
        var time = 0L
        var result = PetGestureSignal.NONE
        listOf(112, 88, 112, 88).forEach { target ->
            while (x != target) {
                x += if (x < target) 1 else -1
                time += 4L
                result = detector.observe(Offset(x.toFloat(), 100f), time)
            }
        }
        assertEquals(PetGestureSignal.LOVE, result)
    }

    @Test fun subThresholdJitterDoesNotBecomePetting() {
        val detector = PetGestureClassifier(Offset(100f, 100f), 0L, 96f, 2.5f)
        repeat(100) { index ->
            assertEquals(PetGestureSignal.NONE,
                detector.observe(Offset(if (index % 2 == 0) 101f else 100f, 100f), index * 5L))
        }
    }

    @Test fun oneDirectionDragNeverBecomesHeadRub() {
        val detector = PetGestureClassifier(Offset(100f, 100f), 0L, 96f, 2.5f)
        repeat(20) { index ->
            assertEquals(PetGestureSignal.NONE,
                detector.observe(Offset(100f + index * 12f, 100f), index * 30L))
        }
    }

    @Test fun invalidPetDimensionsCannotProduceGestureEvidence() {
        listOf(0f, -1f, Float.NaN, Float.POSITIVE_INFINITY).forEach { size ->
            val detector = PetGestureClassifier(Offset.Zero, 0L, size, 2.5f)
            repeat(8) { index ->
                assertEquals(PetGestureSignal.NONE, detector.observe(
                    Offset(if (index % 2 == 0) 100f else -100f, 0f), index * 50L))
            }
        }
    }

    @Test fun everyNewTouchRestartsTheFullInactivityWindow() {
        val behavior = PetOverlayBehavior()
        repeat(3) { index ->
            behavior.advance((index + 1) * 17_000L, false, false)
            behavior.touch(Offset(10f, 20f), false)
        }
        behavior.advance(68_999L, false, false)
        assertEquals(PetEmotion.CURIOUS, behavior.emotion)
        behavior.advance(69_000L, false, false)
        assertEquals(PetEmotion.SLEEPY, behavior.emotion)
    }

    @Test fun touchAfterTaskEndStartsAFreshIdleWindow() {
        val behavior = PetOverlayBehavior()
        behavior.advance(100_000L, true, false)
        behavior.touch(Offset(10f, 20f), false)
        behavior.advance(117_999L, false, false)
        assertEquals(PetEmotion.CURIOUS, behavior.emotion)
        behavior.advance(118_000L, false, false)
        assertEquals(PetEmotion.SLEEPY, behavior.emotion)
    }

    @Test fun reactionExpiryDoesNotExtendInactivityBeyondLastInteraction() {
        val behavior = PetOverlayBehavior()
        behavior.advance(1000L, false, false)
        behavior.react(PetEmotion.HAPPY, 1500L, null)
        behavior.advance(18_999L, false, false)
        assertEquals(PetEmotion.CURIOUS, behavior.emotion)
        behavior.advance(19_000L, false, false)
        assertEquals(PetEmotion.SLEEPY, behavior.emotion)
    }

    @Test fun zeroAndNegativeReactionDurationsExpireOnTheNextTick() {
        listOf(0L, -1L).forEach { duration ->
            val behavior = PetOverlayBehavior()
            behavior.react(PetEmotion.HAPPY, duration, null)
            behavior.advance(0L, false, false)
            assertEquals(PetEmotion.CURIOUS, behavior.emotion)
        }
    }
}

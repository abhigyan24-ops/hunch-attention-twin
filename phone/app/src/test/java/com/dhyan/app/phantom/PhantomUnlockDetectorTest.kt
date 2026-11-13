package com.dhyan.app.phantom

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PhantomUnlockDetectorTest {

    @Test
    fun `triggers SHORT_UNLOCK_BURST on 3 short non-utility unlocks within 15 minutes`() {
        val t0 = 1_000_000L
        val prior = listOf(
            UnlockSession(unlockTimestampMillis = t0, durationMillis = 12_000L, firstForegroundPackage = "com.launcher"),
            UnlockSession(unlockTimestampMillis = t0 + 120_000L, durationMillis = 8_000L, firstForegroundPackage = "com.launcher")
        )

        // 3rd short unlock at t0 + 240_000L (4 mins later) lasting 15 seconds
        val decision = PhantomUnlockEvaluator.evaluateUnlockEvent(
            priorSessions = prior,
            currentUnlockTimestampMillis = t0 + 240_000L,
            currentDurationMillis = 15_000L,
            currentFirstForegroundPackage = "com.launcher",
            lastNudgeTimestampMillis = 0L
        )

        assertEquals(PhantomTrigger.SHORT_UNLOCK_BURST, decision.trigger)
        assertNotNull(decision.humanPrompt)
        assertTrue(decision.humanPrompt!!.contains("3rd quick pickup"))
        assertTrue(decision.humanPrompt!!.contains("muscle memory"))
    }

    @Test
    fun `utility app unlocks are completely excluded and do not trigger phantom nudge`() {
        val t0 = 1_000_000L
        val prior = listOf(
            UnlockSession(unlockTimestampMillis = t0, durationMillis = 5_000L, firstForegroundPackage = "com.google.android.dialer"),
            UnlockSession(unlockTimestampMillis = t0 + 60_000L, durationMillis = 8_000L, firstForegroundPackage = "com.android.camera2")
        )

        // 3rd short unlock for Google Maps
        val decision = PhantomUnlockEvaluator.evaluateUnlockEvent(
            priorSessions = prior,
            currentUnlockTimestampMillis = t0 + 120_000L,
            currentDurationMillis = 10_000L,
            currentFirstForegroundPackage = "com.google.android.apps.maps",
            lastNudgeTimestampMillis = 0L
        )

        assertEquals(PhantomTrigger.NONE, decision.trigger)
        assertNull(decision.humanPrompt)
    }

    @Test
    fun `triggers REFLEX_SOCIAL_LAUNCH on rapid social open after recent unlock`() {
        val t0 = 1_000_000L
        // Prior unlock session ended 2 minutes ago
        val prior = listOf(
            UnlockSession(unlockTimestampMillis = t0, durationMillis = 60_000L, firstForegroundPackage = "com.slack")
        )

        // Unlocked 2 mins after prior session ended, opened Instagram within 1.5 seconds
        val currentUnlock = t0 + 60_000L + 120_000L
        val decision = PhantomUnlockEvaluator.evaluateUnlockEvent(
            priorSessions = prior,
            currentUnlockTimestampMillis = currentUnlock,
            currentDurationMillis = 1_500L,
            currentFirstForegroundPackage = "com.instagram.android",
            lastNudgeTimestampMillis = 0L
        )

        assertEquals(PhantomTrigger.REFLEX_SOCIAL_LAUNCH, decision.trigger)
        assertNotNull(decision.humanPrompt)
        assertTrue(decision.humanPrompt!!.contains("Quick reflex pickup"))
    }

    @Test
    fun `reflex social launch is NOT triggered if previous unlock was more than 10 minutes ago`() {
        val t0 = 1_000_000L
        // Prior unlock session ended 25 minutes ago
        val prior = listOf(
            UnlockSession(unlockTimestampMillis = t0, durationMillis = 30_000L, firstForegroundPackage = "com.slack")
        )

        val currentUnlock = t0 + 30_000L + (25 * 60_000L)
        val decision = PhantomUnlockEvaluator.evaluateUnlockEvent(
            priorSessions = prior,
            currentUnlockTimestampMillis = currentUnlock,
            currentDurationMillis = 2_000L,
            currentFirstForegroundPackage = "com.google.android.youtube",
            lastNudgeTimestampMillis = 0L
        )

        assertEquals(PhantomTrigger.NONE, decision.trigger)
    }

    @Test
    fun `20-minute cooldown suppresses duplicate phantom prompts`() {
        val t0 = 1_000_000L
        val prior = listOf(
            UnlockSession(unlockTimestampMillis = t0, durationMillis = 10_000L, firstForegroundPackage = "com.launcher"),
            UnlockSession(unlockTimestampMillis = t0 + 60_000L, durationMillis = 10_000L, firstForegroundPackage = "com.launcher")
        )

        // Nudge was shown 5 minutes ago (cooldown is 20 minutes)
        val lastNudge = t0 + 100_000L
        val currentUnlock = t0 + 120_000L

        val decision = PhantomUnlockEvaluator.evaluateUnlockEvent(
            priorSessions = prior,
            currentUnlockTimestampMillis = currentUnlock,
            currentDurationMillis = 10_000L,
            currentFirstForegroundPackage = "com.launcher",
            lastNudgeTimestampMillis = lastNudge
        )

        assertEquals(PhantomTrigger.NONE, decision.trigger)
    }
}

package com.dhyan.app.twin.decision

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ImportanceModelTest {

    @Test
    fun `vip override returns one point zero`() {
        val model = ImportanceModel()
        val score = model.score(
            senderHash = "hash123",
            appCat = "social",
            notifCat = "promo",
            isVip = true
        )
        assertEquals(1.0, score, 0.0001)
    }

    @Test
    fun `otp like flag returns high priority score`() {
        val model = ImportanceModel()
        val score = model.score(
            senderHash = null,
            appCat = "comms",
            notifCat = "msg",
            isVip = false,
            isOtpLike = true
        )
        assertEquals(0.95, score, 0.0001)
    }

    @Test
    fun `emergency keyword flag boosts urgency score to at least 0 point 80`() {
        val model = ImportanceModel()
        val score = model.score(
            senderHash = "sender_a",
            appCat = "comms",
            notifCat = "msg",
            isVip = false,
            hasEmergencyKeyword = true
        )
        assertTrue("Expected score >= 0.80 for emergency keyword, was $score", score >= 0.80)
    }

    @Test
    fun `high urgency notification categories return at least 0 point 85`() {
        val model = ImportanceModel()
        val alarmScore = model.score(
            senderHash = null,
            appCat = "system",
            notifCat = "alarm",
            isVip = false
        )
        assertTrue("Alarm category must be >= 0.85, was $alarmScore", alarmScore >= 0.85)

        val callScore = model.score(
            senderHash = null,
            appCat = "comms",
            notifCat = "call",
            isVip = false
        )
        assertTrue("Call category must be >= 0.85, was $callScore", callScore >= 0.85)
    }

    @Test
    fun `repeat count within 5 minutes scales importance`() {
        val model = ImportanceModel()
        val singleScore = model.score(
            senderHash = "sender_b",
            appCat = "social",
            notifCat = "social",
            isVip = false,
            repeatCount5m = 0
        )
        val repeatScore = model.score(
            senderHash = "sender_b",
            appCat = "social",
            notifCat = "social",
            isVip = false,
            repeatCount5m = 3
        )
        assertTrue("Repeated notification must have higher score ($repeatScore > $singleScore)", repeatScore > singleScore)
    }

    @Test
    fun `online feedback updates sender specific posterior mean`() {
        val model = ImportanceModel()
        val hash = "sender_alice"
        val initialScore = model.score(
            senderHash = hash,
            appCat = "work",
            notifCat = "msg",
            isVip = false
        )

        // User marks 5 notifications from Alice as important
        repeat(5) {
            model.observeFeedback(senderHash = hash, appCat = "work", isImportant = true)
        }

        val updatedScore = model.score(
            senderHash = hash,
            appCat = "work",
            notifCat = "msg",
            isVip = false
        )
        assertTrue("Score must increase after positive feedback: initial=$initialScore, updated=$updatedScore", updatedScore > initialScore)

        // Another sender marked as noise decreases
        val noiseHash = "sender_spammer"
        val initialNoise = model.score(
            senderHash = noiseHash,
            appCat = "social",
            notifCat = "social",
            isVip = false
        )
        repeat(5) {
            model.observeFeedback(senderHash = noiseHash, appCat = "social", isImportant = false)
        }
        val updatedNoise = model.score(
            senderHash = noiseHash,
            appCat = "social",
            notifCat = "social",
            isVip = false
        )
        assertTrue("Score must decrease after noise feedback: initial=$initialNoise, updated=$updatedNoise", updatedNoise < initialNoise)
    }

    @Test
    fun `noisy-or invariants are preserved within 0 and 1`() {
        val model = ImportanceModel()
        val score = model.score(
            senderHash = "random_sender",
            appCat = "promo",
            notifCat = "promo",
            isVip = false,
            hasReplyAction = false,
            repeatCount5m = 0,
            hasEmergencyKeyword = false,
            isOtpLike = false,
            urgencyScore = 0.1
        )
        assertTrue("Score must be in [0, 1], was $score", score in 0.0..1.0)
    }
}

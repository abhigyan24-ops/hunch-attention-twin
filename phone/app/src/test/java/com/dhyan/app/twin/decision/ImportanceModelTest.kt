package com.dhyan.app.twin.decision

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ImportanceModelTest {

    private lateinit var model: ImportanceModel

    @Before
    fun setUp() {
        model = ImportanceModel(defaultPriorAlpha = 1.0, defaultPriorBeta = 4.0)
    }

    @Test
    fun vipSenderAlwaysProducesScoreOne() {
        val score = model.score(
            senderHash = "hash_vip",
            appCat = "social",
            notifCat = "promo",
            isVip = true,
            urgencyScore = 0.0
        )
        assertEquals(1.0, score, 1e-6)
    }

    @Test
    fun defaultPriorImportanceIsTwentyPercent() {
        // With alpha=1, beta=4, prior mean = 1 / (1 + 4) = 0.20
        // With no urgency and no repeats: I = 1 - (1 - 0.20)(1 - 0)(1 - 0) = 0.20
        val score = model.score(
            senderHash = "unknown_sender",
            appCat = "unknown_cat",
            notifCat = null,
            isVip = false,
            urgencyScore = 0.0
        )
        assertEquals(0.20, score, 1e-4)
    }

    @Test
    fun highUrgencyCategoryBoostsImportance() {
        val scoreOtp = model.score(
            senderHash = "auth_service",
            appCat = "system",
            notifCat = "otp",
            isVip = false
        )
        assertTrue("OTP should produce score >= 0.85, got $scoreOtp", scoreOtp >= 0.85)

        val scoreCall = model.score(
            senderHash = "caller",
            appCat = "comms",
            notifCat = "call",
            isVip = false
        )
        assertTrue("Call should produce score >= 0.85, got $scoreCall", scoreCall >= 0.85)
    }

    @Test
    fun urgentKeywordsBoostScoreLocallyWithoutPersisting() {
        val scoreUrgent = model.score(
            senderHash = "friend_1",
            appCat = "social",
            notifCat = "msg",
            isVip = false,
            rawTextHint = "Please reply ASAP, this is urgent!"
        )
        assertTrue("ASAP keyword should yield score >= 0.80, got $scoreUrgent", scoreUrgent >= 0.80)

        val scoreHindi = model.score(
            senderHash = "friend_2",
            appCat = "social",
            notifCat = "msg",
            isVip = false,
            rawTextHint = "Bhai jaldi call me"
        )
        assertTrue("Hindi urgency keyword should yield score >= 0.80, got $scoreHindi", scoreHindi >= 0.80)
    }

    @Test
    fun repeatCountIncreasesScoreViaNoisyOr() {
        val baseScore = model.score(
            senderHash = "chat_group",
            appCat = "comms",
            notifCat = "msg",
            isVip = false,
            repeatCount5m = 0
        )
        val repeat2Score = model.score(
            senderHash = "chat_group",
            appCat = "comms",
            notifCat = "msg",
            isVip = false,
            repeatCount5m = 2
        )
        val repeat3Score = model.score(
            senderHash = "chat_group",
            appCat = "comms",
            notifCat = "msg",
            isVip = false,
            repeatCount5m = 3
        )

        assertTrue(repeat2Score > baseScore)
        assertTrue(repeat3Score > repeat2Score)
    }

    @Test
    fun directReplyActionBoostsScore() {
        val withoutReply = model.score(
            senderHash = "colleague",
            appCat = "comms",
            notifCat = "msg",
            isVip = false,
            hasReplyAction = false,
            urgencyScore = 0.2
        )
        val withReply = model.score(
            senderHash = "colleague",
            appCat = "comms",
            notifCat = "msg",
            isVip = false,
            hasReplyAction = true,
            urgencyScore = 0.2
        )

        assertTrue("hasReplyAction should increase score", withReply > withoutReply)
    }

    @Test
    fun userFeedbackUpdatesPosteriorAndAdaptsScores() {
        val sender = "newsletter_promo"
        val cat = "marketing"

        val initialScore = model.score(sender, cat, "promo", isVip = false)

        // User repeatedly opens/marks important
        repeat(5) {
            model.observeFeedback(sender, cat, isImportant = true)
        }
        val scoreAfterImportant = model.score(sender, cat, "promo", isVip = false)
        assertTrue("Score should increase after positive feedback", scoreAfterImportant > initialScore)

        // Another sender repeatedly marked as noise/ignored
        val noiseSender = "spammer"
        repeat(5) {
            model.observeFeedback(noiseSender, cat, isImportant = false)
        }
        val scoreAfterNoise = model.score(noiseSender, cat, "promo", isVip = false)
        assertTrue("Score should decrease after noise feedback", scoreAfterNoise < initialScore)
    }
}

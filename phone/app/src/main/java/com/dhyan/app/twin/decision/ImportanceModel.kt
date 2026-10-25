package com.dhyan.app.twin.decision

import com.dhyan.app.twin.params.BetaStat
import java.util.concurrent.ConcurrentHashMap

/**
 * Noisy-OR importance model (spec 8.5).
 *
 * Computes importance score in [0, 1]:
 *   I = 1 - (1 - I_sender) * (1 - I_urgency) * (1 - I_repeat)
 *
 * Pure, synchronous, in-memory class.
 * Never reads or persists raw notification text. All content classification
 * (e.g. isOtpLike, hasEmergencyKeyword) is evaluated upstream by NotifExtractor
 * in transient memory, passing boolean flags only.
 */
class ImportanceModel(
    private val defaultPriorAlpha: Double = 1.0,
    private val defaultPriorBeta: Double = 4.0 // Prior mean = 1/(1+4) = 0.20
) {
    // In-memory sender-specific importance stats (senderHash -> BetaStat)
    private val senderStats = ConcurrentHashMap<String, BetaStat>()
    // In-memory app-category importance stats (appCat -> BetaStat)
    private val appCatStats = ConcurrentHashMap<String, BetaStat>()

    companion object {
        private val HIGH_URGENCY_CATEGORIES = setOf(
            "call", "alarm", "otp", "transport", "delivery", "security", "alert"
        )
    }

    /**
     * Computes the importance score I in [0, 1] for a notification.
     *
     * @param senderHash           Salted SHA-256 hash of the sender (never raw identifier).
     * @param appCat               Category of the app (social, comms, etc.).
     * @param notifCat             Category of the notification (msg, promo, alert, etc.).
     * @param isVip                True if sender is in the VIP list (forces I = 1.0).
     * @param hasReplyAction       True if the notification has a direct reply action.
     * @param repeatCount5m        Number of notifications from this sender in prior 5 minutes.
     * @param hasEmergencyKeyword  True if transient memory detected an emergency keyword (D56-1).
     * @param isOtpLike            True if transient memory detected an OTP-like token (D56-1).
     * @param urgencyScore         Base urgency score in [0, 1] from system classification.
     */
    fun score(
        senderHash: String?,
        appCat: String,
        notifCat: String?,
        isVip: Boolean,
        hasReplyAction: Boolean = false,
        repeatCount5m: Int = 0,
        hasEmergencyKeyword: Boolean = false,
        isOtpLike: Boolean = false,
        urgencyScore: Double = 0.0,
        rawTextHint: String? = null // Retained for backwards compatibility; ignored if flags set
    ): Double {
        if (isVip) return 1.0
        if (isOtpLike) return 0.95

        val iSender = computeSenderImportance(senderHash, appCat)
        val iUrgency = computeUrgencyImportance(
            notifCat = notifCat,
            hasReplyAction = hasReplyAction,
            hasEmergencyKeyword = hasEmergencyKeyword || (rawTextHint?.let { containsEmergencyKeyword(it) } ?: false),
            baseUrgencyScore = urgencyScore
        )
        val iRepeat = computeRepeatImportance(repeatCount5m)

        val product = (1.0 - iSender) * (1.0 - iUrgency) * (1.0 - iRepeat)
        return (1.0 - product).coerceIn(0.0, 1.0)
    }

    /**
     * Online update from user interaction or explicit feedback (spec 8.5):
     * - Opened within 2 min or user tapped "important": success (isImportant = true).
     * - Ignored in digest or marked "noise": failure (isImportant = false).
     */
    fun observeFeedback(
        senderHash: String?,
        appCat: String,
        isImportant: Boolean,
        gamma: Double = 0.995
    ) {
        senderHash?.let { hash ->
            senderStats.compute(hash) { _, current ->
                (current ?: BetaStat()).updated(picked = isImportant, gamma = gamma)
            }
        }
        appCatStats.compute(appCat) { _, current ->
            (current ?: BetaStat()).updated(picked = isImportant, gamma = gamma)
        }
    }

    private fun computeSenderImportance(senderHash: String?, appCat: String): Double {
        val priorMean = defaultPriorAlpha / (defaultPriorAlpha + defaultPriorBeta)
        val appCatStat = appCatStats[appCat]
        val appCatMean = appCatStat?.pointEstimate(
            alpha0 = defaultPriorAlpha,
            beta0 = defaultPriorBeta,
            kappa = 0.0,
            parentMean = priorMean
        ) ?: priorMean

        if (senderHash != null) {
            val senderStat = senderStats[senderHash]
            if (senderStat != null) {
                // Shrink sender towards appCatMean with kappa = 2.0
                return senderStat.pointEstimate(
                    alpha0 = defaultPriorAlpha,
                    beta0 = defaultPriorBeta,
                    kappa = 2.0,
                    parentMean = appCatMean
                )
            }
        }
        return appCatMean
    }

    private fun computeUrgencyImportance(
        notifCat: String?,
        hasReplyAction: Boolean,
        hasEmergencyKeyword: Boolean,
        baseUrgencyScore: Double
    ): Double {
        var score = baseUrgencyScore.coerceIn(0.0, 1.0)

        // Category-based urgency
        if (notifCat != null && HIGH_URGENCY_CATEGORIES.contains(notifCat.lowercase())) {
            score = maxOf(score, 0.85)
        }

        // Reply action boost (+0.15)
        if (hasReplyAction) {
            score = maxOf(score, score + 0.15).coerceAtMost(1.0)
        }

        // Emergency keyword flag (ephemeral signal from NotifExtractor)
        if (hasEmergencyKeyword) {
            score = maxOf(score, 0.80)
        }

        return score.coerceIn(0.0, 1.0)
    }

    private fun computeRepeatImportance(repeatCount5m: Int): Double = when {
        repeatCount5m >= 3 -> 0.60
        repeatCount5m >= 2 -> 0.30
        else -> 0.0
    }

    private fun containsEmergencyKeyword(text: String): Boolean {
        val lower = text.lowercase()
        val keywords = listOf(
            "urgent", "asap", "emergency", "immediately", "critical",
            "jaldi", "zaroori", "turant", "call me", "otp", "verification code"
        )
        return keywords.any { lower.contains(it) }
    }
}

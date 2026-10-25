package com.dhyan.app.twin.inference

import kotlin.math.exp

/**
 * Output action determined by the intentional-vs-compulsive score (spec 8.6).
 */
enum class CompulsiveAction {
    /** P(compulsive) < 0.35: session is considered intentional or benign. */
    SILENT,
    /** 0.35 <= P(compulsive) <= 0.65: prompt once ("Study or break?") and remember. */
    PROMPT_ONCE,
    /** P(compulsive) > 0.65: gentle nudge or Pip posture change. */
    GENTLE_NUDGE
}

/**
 * Eight observable signals for intentionality inference (spec 8.6).
 *
 * @param switchRate Content or surface switches per minute.
 * @param scrollRate Scroll burst frequency (bursts per minute).
 * @param isShortsOrFeed True if current surface is shorts, reels, or endless feed.
 * @param engagement Active engagement: pauses, seeks, notes, typing.
 * @param declaredStudy True if user is inside a declared study session.
 * @param otherDeviceWorkActivity True if laptop is actively in coding, learning, or work.
 * @param secondScreenOverlap True if both phone and laptop screens are active simultaneously.
 * @param lateHour True if local time is between 23:00 and 05:00.
 */
data class CompulsiveSignals(
    val switchRate: Double = 0.0,
    val scrollRate: Double = 0.0,
    val isShortsOrFeed: Boolean = false,
    val engagement: Double = 0.0,
    val declaredStudy: Boolean = false,
    val otherDeviceWorkActivity: Boolean = false,
    val secondScreenOverlap: Boolean = false,
    val lateHour: Boolean = false
)

/**
 * Result of intentionality inference.
 */
data class CompulsiveResult(
    val linearScore: Double,
    val probability: Double,
    val action: CompulsiveAction
)

/**
 * Logistic regression model over 8 signals to detect compulsive distraction drift (spec 8.6).
 *
 * s = b0 + w1*switchRate + w2*scrollRate + w3*isShortsOrFeed
 *        - w4*engagement - w5*declaredStudy - w6*otherDeviceWorkActivity
 *        + w7*secondScreenOverlap + w8*lateHour
 * P(compulsive) = sigma(s)
 *
 * Weights update online via SGD with L2 regularization towards priors:
 *   w <- w + eta*(y - p)*x - eta*lambda*(w - w_prior)
 * with eta = 0.05, lambda = 0.01.
 */
class CompulsiveScore(
    private val eta: Double = 0.05,
    private val lambda: Double = 0.01
) {
    // Feature dimensions: 0=bias, 1=switchRate, 2=scrollRate, 3=isShortsOrFeed,
    // 4=engagement, 5=declaredStudy, 6=otherDeviceWork, 7=secondScreenOverlap, 8=lateHour
    private val priorWeights = doubleArrayOf(
        -1.2, // b0: baseline bias favoring intentionality when signals are calm
        0.8,  // w1: switchRate
        0.5,  // w2: scrollRate
        1.5,  // w3: isShortsOrFeed
        1.0,  // w4: engagement (subtracted)
        1.8,  // w5: declaredStudy (subtracted)
        1.2,  // w6: otherDeviceWorkActivity (subtracted)
        0.8,  // w7: secondScreenOverlap (added)
        0.7   // w8: lateHour (added)
    )

    private val currentWeights = priorWeights.clone()

    /** Returns a defensive copy of current weights. */
    fun weights(): DoubleArray = currentWeights.clone()

    /**
     * Scores the given signals and returns linear score, probability, and action.
     */
    fun evaluate(signals: CompulsiveSignals): CompulsiveResult {
        val x = extractFeatures(signals)
        var s = 0.0
        for (i in currentWeights.indices) {
            s += currentWeights[i] * x[i]
        }

        val prob = sigmoid(s)
        val action = when {
            prob < 0.35 -> CompulsiveAction.SILENT
            prob <= 0.65 -> CompulsiveAction.PROMPT_ONCE
            else -> CompulsiveAction.GENTLE_NUDGE
        }

        return CompulsiveResult(
            linearScore = s,
            probability = prob,
            action = action
        )
    }

    /**
     * Online SGD update from explicit or implicit user feedback (spec 8.6).
     *
     * @param signals The signals present during the evaluated session.
     * @param y Ground truth label (1.0 for compulsive/drift, 0.0 for intentional/study).
     */
    @Synchronized
    fun update(signals: CompulsiveSignals, y: Double) {
        val x = extractFeatures(signals)
        var s = 0.0
        for (i in currentWeights.indices) {
            s += currentWeights[i] * x[i]
        }
        val p = sigmoid(s)
        val error = y.coerceIn(0.0, 1.0) - p

        for (i in currentWeights.indices) {
            val gradLoss = error * x[i]
            val reg = lambda * (currentWeights[i] - priorWeights[i])
            currentWeights[i] += eta * (gradLoss - reg)
        }
    }

    /**
     * Extracts feature vector x aligned with weight signs.
     * Negative signs for dampening signals (engagement, declaredStudy, otherDeviceWork).
     */
    private fun extractFeatures(signals: CompulsiveSignals): DoubleArray = doubleArrayOf(
        1.0,
        signals.switchRate,
        signals.scrollRate,
        if (signals.isShortsOrFeed) 1.0 else 0.0,
        -signals.engagement,
        if (signals.declaredStudy) -1.0 else 0.0,
        if (signals.otherDeviceWorkActivity) -1.0 else 0.0,
        if (signals.secondScreenOverlap) 1.0 else 0.0,
        if (signals.lateHour) 1.0 else 0.0
    )

    private fun sigmoid(z: Double): Double {
        return when {
            z < -45.0 -> 0.0
            z > 45.0 -> 1.0
            else -> 1.0 / (1.0 + exp(-z))
        }
    }
}

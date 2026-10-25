package com.dhyan.app.twin.params

import kotlin.math.*

/**
 * Immutable value holding a lognormal distribution's learned evidence via Welford's algorithm.
 *
 * Observations must be positive durations in **minutes**. Non-positive durations are rejected.
 *
 * Posterior:
 *   m  = (n0 * m0 + count * welfordMean) / (n0 + count)
 *   s² = (n0 * s0² + welfordM2)           / (n0 + count)    [biased; adequate for our purposes]
 * where n0 and (m0, s0) are the prior pseudo-observation strength and prior from [PriorTable].
 *
 * The sigma floor [sigmaFloor] = 0.25 is applied when sampling, not to the stored value.
 *
 * @param count       Number of observed durations.
 * @param welfordMean Running mean of ln(d).
 * @param welfordM2   Running sum of squared deviations of ln(d) from the mean (Welford M2).
 */
data class LogNormalStat(
    val count: Double = 0.0,
    val welfordMean: Double = 0.0,
    val welfordM2: Double = 0.0
) {
    /**
     * Returns a new [LogNormalStat] after incorporating one positive duration [dMin].
     * Silently ignores non-positive durations (logs an in-memory count).
     */
    fun updated(dMin: Double): LogNormalStat {
        if (dMin <= 0.0) return this  // reject non-positive durations silently
        val newCount = count + 1.0
        val lnD = ln(dMin)
        val delta = lnD - welfordMean
        val newMean = welfordMean + delta / newCount
        val delta2 = lnD - newMean
        val newM2 = welfordM2 + delta * delta2
        return LogNormalStat(newCount, newMean, newM2)
    }

    /**
     * Posterior mean of ln(d) blended with prior pseudo-observations.
     * @param priorN  Prior pseudo-observation strength (n0, default 3).
     * @param priorM  Prior mean of ln(d) (m0, from PriorTable).
     */
    fun posteriorMean(priorN: Double, priorM: Double): Double =
        (priorN * priorM + count * welfordMean) / (priorN + count)

    /**
     * Posterior variance of ln(d) blended with prior.
     * @param priorN  Prior pseudo-observation strength (n0).
     * @param priorS  Prior sigma of ln(d) (s0, from PriorTable).
     */
    fun posteriorVariance(priorN: Double, priorS: Double): Double =
        (priorN * priorS * priorS + welfordM2) / (priorN + count)

    /**
     * Posterior sigma of ln(d), floored at [sigmaFloor].
     */
    fun posteriorSigma(priorN: Double, priorS: Double, sigmaFloor: Double = 0.25): Double =
        maxOf(sqrt(posteriorVariance(priorN, priorS)), sigmaFloor)
}

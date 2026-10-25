package com.dhyan.app.twin.params

/**
 * Immutable value holding a Gamma-distributed baseline hazard rate posterior.
 * The hazard λ is in **pickups per hour**.
 *
 * Posterior: shape = shape0 + events, rate = rate0 + exposureHours
 * where shape0 and rate0 come from [PriorTable].
 *
 * Shrinkage toward the parent bucket uses κ as pseudo-exposure hours:
 *   effectiveRate = rate + κ   (not rate0 + events + κ separately)
 *   — this is the conventional form: "pretend we observed κ hours of the parent's rate."
 *
 * @param events         Accumulated self-initiated pickup events.
 * @param exposureHours  Accumulated hours of exposure (time spent in the context).
 * @param n              Observation count (number of episodes contributing data).
 */
data class GammaHazardStat(
    val events: Double = 0.0,
    val exposureHours: Double = 0.0,
    val n: Double = 0.0
) {
    /**
     * Returns a new [GammaHazardStat] after adding [newEvents] over [newExposureHours].
     * No decay: hazard estimates accumulate without recency decay (unlike Beta pickup odds).
     */
    fun updated(newEvents: Double, newExposureHoursValue: Double): GammaHazardStat = GammaHazardStat(
        events = events + newEvents,
        exposureHours = exposureHours + newExposureHoursValue,
        n = n + 1.0
    )

    /**
     * Posterior shape for Gamma sampling, given prior [shape0] from PriorTable.
     */
    fun posteriorShape(shape0: Double): Double = shape0 + events

    /**
     * Posterior rate for Gamma sampling, given prior [rate0] from PriorTable.
     * [kappaPseudoHours] is the shrinkage pseudo-exposure (S3-8, default 5h).
     * Not used directly for sampling; included for hierarchy blending.
     */
    fun posteriorRate(rate0: Double): Double = rate0 + exposureHours
}

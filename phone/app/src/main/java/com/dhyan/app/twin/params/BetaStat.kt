package com.dhyan.app.twin.params

import kotlin.math.*

/**
 * Immutable value holding a Beta distribution's learned evidence.
 *
 * Evidence counts [successes] and [failures] are decayed (multiplied by γ=0.995) BEFORE
 * adding a new observation, so they are fractional. The prior parameters [alpha0] and
 * [beta0] are not stored here; they come from [PriorTable] at resolve time.
 *
 * Shrinkage with parent mean [parentMean] and strength κ:
 *   posterior alpha = alpha0 + successes + κ * parentMean
 *   posterior beta  = beta0  + failures  + κ * (1 - parentMean)
 *   point estimate  = (alpha0 + successes + κ * parentMean) / (alpha0 + beta0 + successes + failures + κ)
 *
 * @param successes  Decayed success count (picks that happened).
 * @param failures   Decayed failure count (picks that did not happen).
 * @param n          Decayed total observation count (successes + failures, tracked separately for nEff).
 */
data class BetaStat(
    val successes: Double = 0.0,
    val failures: Double = 0.0,
    val n: Double = 0.0
) {
    /** Returns a new [BetaStat] after decaying existing evidence and adding one new observation. */
    fun updated(picked: Boolean, gamma: Double): BetaStat {
        val newS = successes * gamma + if (picked) 1.0 else 0.0
        val newF = failures  * gamma + if (!picked) 1.0 else 0.0
        val newN = n * gamma + 1.0
        return BetaStat(newS, newF, newN)
    }

    /**
     * Returns the shrunk posterior parameters for sampling from Beta(α_post, β_post).
     * [alpha0] and [beta0] are from the prior; [kappa] is the shrinkage strength;
     * [parentMean] is the parent bucket's posterior mean (0.5 if no parent).
     */
    fun posteriorParams(alpha0: Double, beta0: Double, kappa: Double, parentMean: Double): Pair<Double, Double> {
        val alpha = alpha0 + successes + kappa * parentMean
        val beta  = beta0  + failures  + kappa * (1.0 - parentMean)
        return Pair(alpha, beta)
    }

    /**
     * Point estimate with shrinkage.
     */
    fun pointEstimate(alpha0: Double, beta0: Double, kappa: Double, parentMean: Double): Double {
        val (alpha, beta) = posteriorParams(alpha0, beta0, kappa, parentMean)
        return alpha / (alpha + beta)
    }
}

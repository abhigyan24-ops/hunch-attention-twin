package com.dhyan.app.twin.params

/**
 * Immutable value holding Dirichlet distribution evidence for one destination.
 * One [DirichletStat] row is stored per (bucket, destinationId) pair in the param store.
 *
 * Evidence count [decayedCount] is decayed (multiplied by γ) before adding a new observation.
 * Shrinkage toward the parent's normalised mean is applied at resolve time with strength κ.
 *
 * @param decayedCount  Decayed observation count for this destination.
 */
data class DirichletStat(val decayedCount: Double = 0.0) {
    /** Returns a new [DirichletStat] after decaying existing evidence and adding one observation. */
    fun updated(gamma: Double): DirichletStat =
        DirichletStat(decayedCount * gamma + 1.0)

    /**
     * Returns the effective Dirichlet alpha for this destination.
     *
     * @param kappa        Shrinkage strength (default 5).
     * @param parentMean   Parent bucket's normalised mean for this destination.
     */
    fun effectiveAlpha(kappa: Double, parentMean: Double): Double =
        decayedCount + kappa * parentMean
}

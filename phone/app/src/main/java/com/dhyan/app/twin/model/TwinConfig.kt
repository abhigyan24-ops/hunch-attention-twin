package com.dhyan.app.twin.model

/**
 * Immutable configuration for the twin model.
 * Values come from Appendix A of the spec and remain stable across runs.
 * [configVersion] is bumped whenever any default changes.
 */
data class TwinConfig(
    /** Number of Monte Carlo simulation runs (N). */
    val monteCarloN: Int = 500,
    /** Outcome window in minutes (W). */
    val windowMin: Double = 30.0,
    /** Hierarchy shrinkage strength (κ). */
    val kappa: Double = 5.0,
    /** Beta recency decay per episode (γ). */
    val gamma: Double = 0.995,
    /** Lognormal prior pseudo-observation count (n0). */
    val lognormPriorN: Double = 3.0,
    /** Minimum sigma for lognormal sampling (σ_min). */
    val sigmaMin: Double = 0.25,
    /** Glance cost in focus-minutes (c_glance). */
    val cGlanceMin: Double = 0.4,
    /** Version string incremented when any default changes. */
    val configVersion: String = "1.0.0"
) {
    companion object {
        fun default() = TwinConfig()
    }
}

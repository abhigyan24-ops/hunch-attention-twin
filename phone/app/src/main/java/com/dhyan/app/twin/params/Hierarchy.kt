package com.dhyan.app.twin.params

/**
 * Hierarchy levels for parameter backoff (spec 8.3).
 *
 * When a full context bucket has insufficient data, the resolver walks up the hierarchy:
 *   FULL → PARENT → GRANDPARENT → PRIOR
 *
 * "Baseline" parameters (no notification class) have their own simpler chain:
 *   BASELINE_FULL → BASELINE_PARENT → PRIOR
 */
enum class HierarchyLevel {
    FULL,
    PARENT,
    GRANDPARENT,
    PRIOR,
    BASELINE_FULL,
    BASELINE_PARENT
}

/**
 * The result of resolving a statistic at a particular hierarchy level.
 *
 * @param level       Which level in the hierarchy provided the data.
 * @param nEff        Effective sample size (per S3-9 formula).
 * @param nAtFull     Observed count at the FULL/BASELINE_FULL level (0 if not reached).
 * @param nAtParent   Observed count at the PARENT/BASELINE_PARENT level.
 */
data class HierarchyResolution(
    val level: HierarchyLevel,
    val nEff: Double,
    val nAtFull: Double,
    val nAtParent: Double
) {
    companion object {
        /**
         * Computes nEff from full-level and parent-level decayed observation counts.
         *
         * Formula (S3-9): nEff = n_full + 0.5 * max(0, n_parent - n_full)
         */
        fun computeNEff(nFull: Double, nParent: Double): Double =
            nFull + 0.5 * maxOf(0.0, nParent - nFull)
    }
}

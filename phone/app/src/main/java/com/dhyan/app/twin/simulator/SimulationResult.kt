package com.dhyan.app.twin.simulator

import com.dhyan.app.twin.params.HierarchyLevel

/**
 * Result of a paired Monte Carlo simulation (SHOW vs SUPPRESS).
 *
 * All time loss values are in focus-minutes.
 *
 * Uncertainty and labeling rules (spec 25.2, 25.3):
 * - [deltaL80SimIntervalLow] and [deltaL80SimIntervalHigh] are the 10th and 90th percentiles
 *   of ΔL across the simulation runs. They are labeled "80% simulation interval", NOT a Bayesian credible interval.
 * - [pickupPosterior80Low] and [pickupPosterior80High] are the 10th and 90th percentiles of the
 *   sampled p_pickup parameters (an empirical 80% posterior interval).
 * - [evidenceStrength] is nEff / (nEff + 10), a heuristic evidence-strength score, NOT a probability of correctness.
 * - Monte Carlo error is reported separately via [deltaLStdError].
 */
data class SimulationResult(
    val show: ArmStats,
    val suppress: ArmStats,
    val deltaLMean: Double,
    val deltaL80SimIntervalLow: Double,
    val deltaL80SimIntervalHigh: Double,
    val deltaLStdError: Double,
    val pickupPosterior80Low: Double,
    val pickupPosterior80High: Double,
    val nEff: Double,
    val evidenceStrength: Double,
    val modelVersion: Long,
    val seed: Long,
    val configVersion: String,
    val n: Int,
    val pickupHierarchyLevel: HierarchyLevel,
    val hazardHierarchyLevel: HierarchyLevel,
    val ownerFractions: Map<String, Double>,
    // Per-run diagnostic arrays (0: NONE, 1: NOTIFICATION, 2: BASELINE)
    val lShow: DoubleArray,
    val lSuppress: DoubleArray,
    val deltaL: DoubleArray,
    val owner: ByteArray,
    val pPickupSample: DoubleArray
) {
    companion object {
        const val OWNER_NONE: Byte = 0
        const val OWNER_NOTIFICATION: Byte = 1
        const val OWNER_BASELINE: Byte = 2
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SimulationResult) return false
        return show == other.show &&
            suppress == other.suppress &&
            deltaLMean == other.deltaLMean &&
            deltaL80SimIntervalLow == other.deltaL80SimIntervalLow &&
            deltaL80SimIntervalHigh == other.deltaL80SimIntervalHigh &&
            deltaLStdError == other.deltaLStdError &&
            pickupPosterior80Low == other.pickupPosterior80Low &&
            pickupPosterior80High == other.pickupPosterior80High &&
            nEff == other.nEff &&
            evidenceStrength == other.evidenceStrength &&
            modelVersion == other.modelVersion &&
            seed == other.seed &&
            configVersion == other.configVersion &&
            n == other.n &&
            pickupHierarchyLevel == other.pickupHierarchyLevel &&
            hazardHierarchyLevel == other.hazardHierarchyLevel &&
            ownerFractions == other.ownerFractions &&
            lShow.contentEquals(other.lShow) &&
            lSuppress.contentEquals(other.lSuppress) &&
            deltaL.contentEquals(other.deltaL) &&
            owner.contentEquals(other.owner) &&
            pPickupSample.contentEquals(other.pPickupSample)
    }

    override fun hashCode(): Int {
        var result = show.hashCode()
        result = 31 * result + suppress.hashCode()
        result = 31 * result + deltaLMean.hashCode()
        result = 31 * result + modelVersion.hashCode()
        result = 31 * result + seed.hashCode()
        return result
    }
}

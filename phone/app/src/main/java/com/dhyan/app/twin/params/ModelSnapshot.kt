package com.dhyan.app.twin.params

/**
 * An immutable snapshot of all parameter values needed for a single simulation call.
 * The simulator must only read from this object (not from the live store) during a run,
 * guaranteeing thread-safety and a consistent view of the model.
 *
 * @param pickupAlpha           Posterior alpha for Beta pickup probability.
 * @param pickupBeta            Posterior beta for Beta pickup probability.
 * @param hazardShape           Posterior shape for Gamma baseline hazard (per hour).
 * @param hazardRate            Posterior rate for Gamma baseline hazard (per hour).
 * @param selfDirichletAlphas   Effective Dirichlet alphas for self-initiated destination.
 * @param notifDirichletAlphas  Effective Dirichlet alphas for notification-triggered destination.
 * @param destinations          Destination IDs parallel to the Dirichlet alpha arrays.
 * @param dwellMeanLog          Map from destination to posterior lognormal mean of ln(dwell).
 * @param dwellSigma            Map from destination to posterior lognormal sigma of dwell.
 * @param resumeMeanLog         Posterior lognormal mean of ln(resume delay) for this taskType.
 * @param resumeSigma           Posterior lognormal sigma of resume delay.
 * @param pickupLatencyMeanLog  Lognormal mean of ln(pickup latency).
 * @param pickupLatencySigma    Lognormal sigma of pickup latency.
 * @param nEff                  Effective sample size for the pickup stat.
 * @param evidenceStrength      Heuristic evidence-strength score = nEff / (nEff + 10). NOT a probability.
 * @param pickupHierarchyLevel  Which hierarchy level provided the pickup stat.
 * @param hazardHierarchyLevel  Which hierarchy level provided the hazard stat.
 * @param modelVersion          Monotonic counter bumped on every parameter update.
 */
data class ModelSnapshot(
    val pickupAlpha: Double,
    val pickupBeta: Double,
    val hazardShape: Double,
    val hazardRate: Double,
    val selfDirichletAlphas: DoubleArray,
    val notifDirichletAlphas: DoubleArray,
    val destinations: List<String>,
    val dwellMeanLog: Map<String, Double>,
    val dwellSigma: Map<String, Double>,
    val resumeMeanLog: Double,
    val resumeSigma: Double,
    val pickupLatencyMeanLog: Double,
    val pickupLatencySigma: Double,
    val nEff: Double,
    val evidenceStrength: Double,
    val pickupHierarchyLevel: HierarchyLevel,
    val hazardHierarchyLevel: HierarchyLevel,
    val modelVersion: Long
) {
    // DoubleArrays are not structurally compared by default; override for correctness in tests.
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ModelSnapshot) return false
        return pickupAlpha == other.pickupAlpha &&
            pickupBeta == other.pickupBeta &&
            hazardShape == other.hazardShape &&
            hazardRate == other.hazardRate &&
            selfDirichletAlphas.contentEquals(other.selfDirichletAlphas) &&
            notifDirichletAlphas.contentEquals(other.notifDirichletAlphas) &&
            destinations == other.destinations &&
            dwellMeanLog == other.dwellMeanLog &&
            dwellSigma == other.dwellSigma &&
            resumeMeanLog == other.resumeMeanLog &&
            resumeSigma == other.resumeSigma &&
            pickupLatencyMeanLog == other.pickupLatencyMeanLog &&
            pickupLatencySigma == other.pickupLatencySigma &&
            nEff == other.nEff &&
            evidenceStrength == other.evidenceStrength &&
            pickupHierarchyLevel == other.pickupHierarchyLevel &&
            hazardHierarchyLevel == other.hazardHierarchyLevel &&
            modelVersion == other.modelVersion
    }

    override fun hashCode(): Int {
        var result = pickupAlpha.hashCode()
        result = 31 * result + pickupBeta.hashCode()
        result = 31 * result + hazardShape.hashCode()
        result = 31 * result + hazardRate.hashCode()
        result = 31 * result + selfDirichletAlphas.contentHashCode()
        result = 31 * result + notifDirichletAlphas.contentHashCode()
        result = 31 * result + destinations.hashCode()
        result = 31 * result + modelVersion.hashCode()
        return result
    }
}

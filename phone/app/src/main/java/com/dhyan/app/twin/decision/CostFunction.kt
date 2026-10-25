package com.dhyan.app.twin.decision

/** Expected focus-minute costs; percentile/tail quantities deliberately have no input here. */
data class CostBreakdown(
    val eShowMin: Double, val eSuppMin: Double, val effectiveKappaDPerHour: Double,
    val releaseDelayHarmMin: Double, val digestDelayHarmMin: Double,
    val releaseInterruptionMin: Double, val digestOverheadMin: Double, val silentMissMin: Double,
    val jAllow: Double, val jDelay: Double, val jDigest: Double, val jSilent: Double
) {
    fun cost(action: Action): Double = when(action) {
        Action.ALLOW -> jAllow; Action.DELAY -> jDelay; Action.DIGEST -> jDigest; Action.SILENT -> jSilent
    }
}

object CostFunction {
    fun compute(eShowMin: Double, eSuppMin: Double, importance: Double,
        kappaDPerHour: Double, strictSession: Boolean, tauReleaseMin: Double, tauDigestMin: Double): CostBreakdown {
        require(listOf(eShowMin,eSuppMin,importance,kappaDPerHour,tauReleaseMin,tauDigestMin).all { it.isFinite() }) { "nonfinite_cost_input" }
        require(eShowMin >= 0 && eSuppMin >= 0 && importance in 0.0..1.0 && kappaDPerHour > 0 && tauReleaseMin >= 0 && tauDigestMin >= 0) { "invalid_cost_input" }
        val kappa = kappaDPerHour * if(strictSession) 0.5 else 1.0
        val releaseHarm = kappa * importance * importance * (tauReleaseMin / 60.0)
        val digestHarm = kappa * importance * importance * (tauDigestMin / 60.0)
        val release = PolicyDefaults.C_REL * eShowMin
        val miss = PolicyDefaults.LAMBDA_MISS_MIN * importance
        val delay = eSuppMin + releaseHarm + release
        val digest = eSuppMin + digestHarm + PolicyDefaults.C_DIGEST_MIN
        val silent = eSuppMin + digestHarm + miss
        require(listOf(releaseHarm,digestHarm,delay,digest,silent).all { it.isFinite() }) { "nonfinite_cost_result" }
        return CostBreakdown(eShowMin,eSuppMin,kappa,releaseHarm,digestHarm,release,PolicyDefaults.C_DIGEST_MIN,miss,eShowMin,delay,digest,silent)
    }
}

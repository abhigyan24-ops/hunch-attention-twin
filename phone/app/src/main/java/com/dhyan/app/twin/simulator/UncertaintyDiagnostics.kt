package com.dhyan.app.twin.simulator

import com.dhyan.app.twin.model.ModelConfig
import com.dhyan.app.twin.model.TwinConfig
import com.dhyan.app.twin.params.HierarchyLevel
import com.dhyan.app.twin.params.ModelSnapshot
import com.dhyan.app.twin.rng.RngStreams
import com.dhyan.app.twin.rng.Samplers
import com.dhyan.app.twin.rng.StreamTag
import kotlin.math.*

/**
 * Nested Monte Carlo diagnostics to decompose uncertainty (spec 25.2).
 *
 * Runs off the hot path (diagnostic/inspection only).
 * Uses the Law of Total Variance:
 *   Var(ΔL) = Var_E[E_P[ΔL | E]] + E_E[Var_P[ΔL | E]]
 *
 * - Epistemic variance = Var_E[E_P[ΔL | E]]
 * - Process variance   = E_E[Var_P[ΔL | E]]
 * - Monte Carlo error  = sqrt(TotalVar / (outer * inner))
 */
object UncertaintyDiagnostics {

    fun diagnoseUncertainty(
        snapshot: ModelSnapshot,
        config: TwinConfig = TwinConfig.default(),
        modelConfig: ModelConfig = ModelConfig(),
        seed: Long = 42L,
        outerRuns: Int = 200,
        innerRuns: Int = 50
    ): UncertaintyReport {
        val innerMeans = DoubleArray(outerRuns)
        val innerVars = DoubleArray(outerRuns)

        val windowMin = config.windowMin
        val cGlance = config.cGlanceMin
        val truncate = modelConfig.truncateCostsAtWindow
        val sqrtN0 = sqrt(config.lognormPriorN)
        val sigmaMin = config.sigmaMin

        val destList = snapshot.destinations
        val destCount = destList.size
        val dwellMeans = DoubleArray(destCount) { i -> snapshot.dwellMeanLog[destList[i]] ?: ln(8.0) }
        val dwellSigmas = DoubleArray(destCount) { i -> maxOf(snapshot.dwellSigma[destList[i]] ?: 0.8, sigmaMin) }
        val resumeMean = snapshot.resumeMeanLog
        val resumeSigma = maxOf(snapshot.resumeSigma, sigmaMin)
        val plMean = snapshot.pickupLatencyMeanLog
        val plSigma = maxOf(snapshot.pickupLatencySigma, sigmaMin)

        val innerDeltas = DoubleArray(innerRuns)

        for (o in 0 until outerRuns) {
            // Draw one epistemic parameter state
            val rngEpistemic = RngStreams.stream(seed, o, StreamTag.EPISTEMIC)
            val p_i = Samplers.nextBeta(rngEpistemic, snapshot.pickupAlpha, snapshot.pickupBeta)
            val lambda_i = Samplers.nextGamma(rngEpistemic, snapshot.hazardShape, snapshot.hazardRate)
            val theta_self = Samplers.nextDirichlet(rngEpistemic, snapshot.selfDirichletAlphas)
            val theta_notif = Samplers.nextDirichlet(rngEpistemic, snapshot.notifDirichletAlphas)
            val eps_Dself = Samplers.nextNormal(rngEpistemic)
            val eps_Dnotif = Samplers.nextNormal(rngEpistemic)
            val eps_R = Samplers.nextNormal(rngEpistemic)

            for (i in 0 until innerRuns) {
                val runIdx = o * innerRuns + i
                val rngSelf = RngStreams.stream(seed + 1000L, runIdx, StreamTag.SELF)
                val u_time_self = rngSelf.nextOpenDouble()
                val u_dest_self = rngSelf.nextDouble()
                val z_D_self = Samplers.nextNormal(rngSelf)
                val z_R_self = Samplers.nextNormal(rngSelf)

                val ratePerMin = lambda_i / 60.0
                val t_self = if (ratePerMin == 0.0) Double.POSITIVE_INFINITY else -ln(1.0 - u_time_self) / ratePerMin
                val hasSelf = t_self < windowMin

                val destIdxSelf = Samplers.categoricalIndex(theta_self, u_dest_self)
                val sDSelf = dwellSigmas[destIdxSelf]
                val mDSelf = dwellMeans[destIdxSelf] + eps_Dself * sDSelf / sqrtN0
                val dSelf = exp(mDSelf + sDSelf * z_D_self)
                val mRSelf = resumeMean + eps_R * resumeSigma / sqrtN0
                val rSelf = exp(mRSelf + resumeSigma * z_R_self)
                val rawCostSelf = dSelf + rSelf
                val costSelf = if (hasSelf) {
                    if (truncate) minOf(rawCostSelf, maxOf(0.0, windowMin - t_self)) else rawCostSelf
                } else 0.0

                val lSuppress = costSelf

                val rngNotif = RngStreams.stream(seed + 1000L, runIdx, StreamTag.NOTIF)
                val u_pick = rngNotif.nextDouble()
                val z_pl = Samplers.nextNormal(rngNotif)
                val u_dest_notif = rngNotif.nextDouble()
                val z_D_notif = Samplers.nextNormal(rngNotif)
                val z_R_notif = Samplers.nextNormal(rngNotif)

                val picked = u_pick < p_i
                val t_notif = exp(plMean + plSigma * z_pl)
                val hasNotif = picked && (t_notif < windowMin)

                val destIdxNotif = Samplers.categoricalIndex(theta_notif, u_dest_notif)
                val sDNotif = dwellSigmas[destIdxNotif]
                val mDNotif = dwellMeans[destIdxNotif] + eps_Dnotif * sDNotif / sqrtN0
                val dNotif = exp(mDNotif + sDNotif * z_D_notif)
                val mRNotif = resumeMean + eps_R * resumeSigma / sqrtN0
                val rNotif = exp(mRNotif + resumeSigma * z_R_notif)
                val rawCostNotif = dNotif + rNotif
                val costNotif = if (hasNotif) {
                    if (truncate) minOf(rawCostNotif, maxOf(0.0, windowMin - t_notif)) else rawCostNotif
                } else 0.0

                val lShow: Double = if (hasNotif && hasSelf) {
                    if (t_notif <= t_self) cGlance + costNotif else cGlance + costSelf
                } else if (hasNotif) {
                    cGlance + costNotif
                } else if (hasSelf) {
                    cGlance + costSelf
                } else {
                    cGlance
                }

                innerDeltas[i] = lShow - lSuppress
            }

            val m = innerDeltas.average()
            var vSum = 0.0
            for (v in innerDeltas) {
                val diff = v - m
                vSum += diff * diff
            }
            innerMeans[o] = m
            innerVars[o] = if (innerRuns > 1) vSum / (innerRuns - 1) else 0.0
        }

        // Law of total variance
        val grandMean = innerMeans.average()
        var epistemicSum = 0.0
        for (m in innerMeans) {
            val d = m - grandMean
            epistemicSum += d * d
        }
        val epistemicVar = if (outerRuns > 1) epistemicSum / (outerRuns - 1) else 0.0
        val processVar = innerVars.average()
        val totalVar = epistemicVar + processVar
        val mcStdError = sqrt(totalVar / (outerRuns * innerRuns))

        // Model uncertainty flags
        val flags = mutableListOf<String>()
        if (snapshot.pickupHierarchyLevel != HierarchyLevel.FULL) {
            flags.add("Pickup probability uses hierarchy backoff: ${snapshot.pickupHierarchyLevel}")
        }
        if (snapshot.hazardHierarchyLevel != HierarchyLevel.BASELINE_FULL) {
            flags.add("Baseline hazard uses hierarchy backoff: ${snapshot.hazardHierarchyLevel}")
        }
        if (snapshot.nEff < modelConfig.minNEffForData) {
            flags.add("Low evidence strength: nEff=${snapshot.nEff} < ${modelConfig.minNEffForData}")
        }
        flags.add("Assumption S3-1: Notification pickup latency prior LogNormal(0, 0.9)")
        flags.add("Assumption S3-16: Single distraction owner per arm per window")
        if (modelConfig.truncateCostsAtWindow) {
            flags.add("Cost truncation at window boundary: ON")
        } else {
            flags.add("Cost truncation at window boundary: OFF")
        }

        return UncertaintyReport(
            epistemicVariance = epistemicVar,
            processVariance = processVar,
            monteCarloStdError = mcStdError,
            modelUncertaintyFlags = flags
        )
    }
}

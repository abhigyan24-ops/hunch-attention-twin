package com.dhyan.app.twin.simulator

import com.dhyan.app.twin.model.ModelConfig
import com.dhyan.app.twin.model.TwinConfig
import com.dhyan.app.twin.params.ModelSnapshot
import com.dhyan.app.twin.rng.RngStreams
import com.dhyan.app.twin.rng.Samplers
import com.dhyan.app.twin.rng.StreamTag
import kotlin.math.*

/**
 * Paired Common Random Numbers (CRN) Monte Carlo Simulator for SHOW vs SUPPRESS.
 *
 * Implements guide section 6:
 * - Competing risks model: first qualifying event (notification vs baseline self-distraction)
 *   owns the distraction window.
 * - Single-owner property: at most one distraction episode per arm per window.
 * - Paired CRN: shared random streams for epistemic parameters and baseline self-initiated hazard,
 *   so baseline variance cancels out when computing ΔL = L_show - L_suppress.
 * - Zero allocations in the hot simulation loop.
 */
object PairedMonteCarlo {

    fun simulate(
        snapshot: ModelSnapshot,
        config: TwinConfig = TwinConfig.default(),
        modelConfig: ModelConfig = ModelConfig(),
        seed: Long = 42L,
        // Test hook to allow uncoupled streams for CRN variance comparison
        uncoupledBaselineStreams: Boolean = false
    ): SimulationResult {
        val n = config.monteCarloN
        val windowMin = config.windowMin
        val cGlance = config.cGlanceMin
        val truncate = modelConfig.truncateCostsAtWindow
        val sqrtN0 = sqrt(config.lognormPriorN)
        val sigmaMin = config.sigmaMin

        val lShow = DoubleArray(n)
        val lSuppress = DoubleArray(n)
        val deltaL = DoubleArray(n)
        val owner = ByteArray(n)
        val pPickupSample = DoubleArray(n)

        var ownerNoneCount = 0
        var ownerNotifCount = 0
        var ownerBaseCount = 0

        // Pre-resolve destination array to avoid allocation
        val destList = snapshot.destinations
        val destCount = destList.size
        val dwellMeans = DoubleArray(destCount) { i -> snapshot.dwellMeanLog[destList[i]] ?: ln(8.0) }
        val dwellSigmas = DoubleArray(destCount) { i -> maxOf(snapshot.dwellSigma[destList[i]] ?: 0.8, sigmaMin) }

        val resumeMean = snapshot.resumeMeanLog
        val resumeSigma = maxOf(snapshot.resumeSigma, sigmaMin)

        val plMean = snapshot.pickupLatencyMeanLog
        val plSigma = maxOf(snapshot.pickupLatencySigma, sigmaMin)

        for (i in 0 until n) {
            // ── 1. Epistemic stream (shared between both arms) ─────────────────
            val rngEpistemic = RngStreams.stream(seed, i, StreamTag.EPISTEMIC)
            val p_i = Samplers.nextBeta(rngEpistemic, snapshot.pickupAlpha, snapshot.pickupBeta)
            val lambda_i = Samplers.nextGamma(rngEpistemic, snapshot.hazardShape, snapshot.hazardRate)
            val theta_self = Samplers.nextDirichlet(rngEpistemic, snapshot.selfDirichletAlphas)
            val theta_notif = Samplers.nextDirichlet(rngEpistemic, snapshot.notifDirichletAlphas)
            val eps_Dself = Samplers.nextNormal(rngEpistemic)
            val eps_Dnotif = Samplers.nextNormal(rngEpistemic)
            val eps_R = Samplers.nextNormal(rngEpistemic)

            pPickupSample[i] = p_i

            // ── 2. Baseline self-distraction (SELF stream) ───────────────────────
            // When uncoupledBaselineStreams=true, use a different stream tag/seed to measure variance reduction
            val selfSeedTag = if (uncoupledBaselineStreams) StreamTag.SELF_UNCOUPLED else StreamTag.SELF
            val rngSelf = RngStreams.stream(seed, i, selfSeedTag)

            // Always draw unconditional values to preserve stream alignment
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

            // ── 3. SUPPRESS arm ────────────────────────────────────────────────
            lSuppress[i] = costSelf

            // ── 4. Notification arm (NOTIF stream) ─────────────────────────────
            val rngNotif = RngStreams.stream(seed, i, StreamTag.NOTIF)

            // Always draw unconditional values to preserve stream alignment
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

            // ── 5. Competing Risks in SHOW arm ──────────────────────────────────
            // Earliest event owns the episode; ties broken in favor of notification
            if (hasNotif && hasSelf) {
                if (t_notif <= t_self) {
                    lShow[i] = cGlance + costNotif
                    owner[i] = SimulationResult.OWNER_NOTIFICATION
                    ownerNotifCount++
                } else {
                    lShow[i] = cGlance + costSelf
                    owner[i] = SimulationResult.OWNER_BASELINE
                    ownerBaseCount++
                }
            } else if (hasNotif) {
                lShow[i] = cGlance + costNotif
                owner[i] = SimulationResult.OWNER_NOTIFICATION
                ownerNotifCount++
            } else if (hasSelf) {
                lShow[i] = cGlance + costSelf
                owner[i] = SimulationResult.OWNER_BASELINE
                ownerBaseCount++
            } else {
                lShow[i] = cGlance
                owner[i] = SimulationResult.OWNER_NONE
                ownerNoneCount++
            }

            // ── 6. Paired difference ───────────────────────────────────────────
            deltaL[i] = lShow[i] - lSuppress[i]
        }

        // Summary statistics
        val showStats = computeArmStats(lShow)
        val suppressStats = computeArmStats(lSuppress)

        val deltaMean = deltaL.average()
        val deltaSorted = deltaL.clone().apply { sort() }
        val deltaL80Low = quantile(deltaSorted, 0.10)
        val deltaL80High = quantile(deltaSorted, 0.90)

        // Monte Carlo standard error of the mean: sqrt(Var / N)
        var varSum = 0.0
        for (i in 0 until n) {
            val d = deltaL[i] - deltaMean
            varSum += d * d
        }
        val deltaLStdError = if (n > 1) sqrt((varSum / (n - 1)) / n) else 0.0

        val pPickupSorted = pPickupSample.clone().apply { sort() }
        val pPickup80Low = quantile(pPickupSorted, 0.10)
        val pPickup80High = quantile(pPickupSorted, 0.90)

        val ownerFractions = mapOf(
            "none" to (ownerNoneCount.toDouble() / n),
            "notification" to (ownerNotifCount.toDouble() / n),
            "baseline" to (ownerBaseCount.toDouble() / n)
        )

        return SimulationResult(
            show = showStats,
            suppress = suppressStats,
            deltaLMean = deltaMean,
            deltaL80SimIntervalLow = deltaL80Low,
            deltaL80SimIntervalHigh = deltaL80High,
            deltaLStdError = deltaLStdError,
            pickupPosterior80Low = pPickup80Low,
            pickupPosterior80High = pPickup80High,
            nEff = snapshot.nEff,
            evidenceStrength = snapshot.evidenceStrength,
            modelVersion = snapshot.modelVersion,
            seed = seed,
            configVersion = config.configVersion,
            n = n,
            pickupHierarchyLevel = snapshot.pickupHierarchyLevel,
            hazardHierarchyLevel = snapshot.hazardHierarchyLevel,
            ownerFractions = ownerFractions,
            lShow = lShow,
            lSuppress = lSuppress,
            deltaL = deltaL,
            owner = owner,
            pPickupSample = pPickupSample
        )
    }

    private fun computeArmStats(samples: DoubleArray): ArmStats {
        val n = samples.size
        val mean = samples.average()
        val sorted = samples.clone().apply { sort() }

        val p10 = quantile(sorted, 0.10)
        val p50 = quantile(sorted, 0.50)
        val p90 = quantile(sorted, 0.90)

        var gt1Count = 0
        for (v in samples) {
            if (v > 1.0) gt1Count++
        }
        val pLossGt1 = gt1Count.toDouble() / n

        return ArmStats(
            mean = mean,
            p10 = p10,
            p50 = p50,
            p90 = p90,
            pLossGt1Min = pLossGt1
        )
    }

    /**
     * Linear interpolation quantile at index q * (N - 1), 0 <= q <= 1.
     * Expects [sorted] to be sorted ascending.
     */
    fun quantile(sorted: DoubleArray, q: Double): Double {
        if (sorted.isEmpty()) return 0.0
        if (sorted.size == 1) return sorted[0]
        val idx = q * (sorted.size - 1)
        val low = idx.toInt()
        val high = minOf(low + 1, sorted.size - 1)
        val frac = idx - low
        return sorted[low] + frac * (sorted[high] - sorted[low])
    }
}

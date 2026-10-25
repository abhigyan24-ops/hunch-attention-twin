package com.dhyan.app.twin.simulator

import com.dhyan.app.twin.model.ModelConfig
import com.dhyan.app.twin.model.TwinConfig
import com.dhyan.app.twin.params.HierarchyLevel
import com.dhyan.app.twin.params.ModelSnapshot
import com.dhyan.app.twin.params.PriorTable
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.*

class PairedMonteCarloTest {

    private fun sampleSnapshot(
        pickupAlpha: Double = 3.0,
        pickupBeta: Double = 4.0,
        hazardShape: Double = 1.5,
        hazardRate: Double = 1.0,
        cGlance: Double = 0.4
    ): ModelSnapshot {
        val dest = PriorTable.destinations
        val dwellMeans = dest.associateWith { ln(8.0) }
        val dwellSigmas = dest.associateWith { 0.8 }

        return ModelSnapshot(
            pickupAlpha = pickupAlpha,
            pickupBeta = pickupBeta,
            hazardShape = hazardShape,
            hazardRate = hazardRate,
            selfDirichletAlphas = DoubleArray(dest.size) { 1.0 },
            notifDirichletAlphas = DoubleArray(dest.size) { 1.0 },
            destinations = dest,
            dwellMeanLog = dwellMeans,
            dwellSigma = dwellSigmas,
            resumeMeanLog = ln(5.0),
            resumeSigma = 0.6,
            pickupLatencyMeanLog = ln(1.0),
            pickupLatencySigma = 0.9,
            nEff = 5.0,
            evidenceStrength = 5.0 / 15.0,
            pickupHierarchyLevel = HierarchyLevel.FULL,
            hazardHierarchyLevel = HierarchyLevel.BASELINE_FULL,
            modelVersion = 1L
        )
    }

    @Test
    fun fixedSeedProducesDeterministicResults() {
        val snapshot = sampleSnapshot()
        val res1 = PairedMonteCarlo.simulate(snapshot, seed = 12345L)
        val res2 = PairedMonteCarlo.simulate(snapshot, seed = 12345L)
        val res3 = PairedMonteCarlo.simulate(snapshot, seed = 99999L)

        assertEquals("Same seed must yield identical show mean", res1.show.mean, res2.show.mean, 0.0)
        assertEquals("Same seed must yield identical deltaL mean", res1.deltaLMean, res2.deltaLMean, 0.0)
        assertArrayEquals("Same seed must yield identical per-run deltaL", res1.deltaL, res2.deltaL, 0.0)

        assertNotEquals("Different seeds must yield different deltaL mean", res1.deltaLMean, res3.deltaLMean, 1e-6)
    }

    @Test
    fun zeroPickupProbabilityYieldsGlanceCostExactly() {
        // Force pickup probability to 0 using a degenerate Beta or very small alpha with large beta
        // Or alpha = 1e-12, beta = 1000.0 so p_i is effectively 0.0
        val snapshot = sampleSnapshot(pickupAlpha = 1e-12, pickupBeta = 10000.0)
        val cGlance = 0.4
        val config = TwinConfig(cGlanceMin = cGlance)

        val result = PairedMonteCarlo.simulate(snapshot, config = config, seed = 42L)

        // When p_pickup = 0, no notification event ever fires.
        // Therefore, in every run: L_show = cGlance + costSelf = cGlance + L_suppress
        // deltaL = L_show - L_suppress = cGlance!
        for (i in 0 until config.monteCarloN) {
            assertEquals("Run $i must equal cGlance exactly", cGlance, result.deltaL[i], 1e-5)
        }
        assertEquals(cGlance, result.deltaLMean, 1e-5)

        // With cGlance = 0.0, deltaL must be exactly 0.0
        val zeroGlanceConfig = TwinConfig(cGlanceMin = 0.0)
        val zeroGlanceResult = PairedMonteCarlo.simulate(snapshot, config = zeroGlanceConfig, seed = 42L)
        for (i in 0 until zeroGlanceConfig.monteCarloN) {
            assertEquals(0.0, zeroGlanceResult.deltaL[i], 1e-5)
        }
    }

    @Test
    fun highBaselineHazardDrivesDeltaLTowardsGlanceCost() {
        // High hazard rate (50/hr) + near-zero notification pickup (pickupAlpha=1e-12) isolates
        // the baseline competing risk. Baseline owns the SHOW arm; costSelf cancels via CRN so
        // deltaL = L_show - L_suppress = cGlance + costSelf - costSelf = cGlance.
        val snapshot = sampleSnapshot(
            hazardShape = 50.0, hazardRate = 1.0,
            pickupAlpha = 1e-12, pickupBeta = 10000.0
        )
        val config = TwinConfig(cGlanceMin = 0.4)

        val result = PairedMonteCarlo.simulate(snapshot, config = config, seed = 42L)

        // deltaLMean must be close to cGlance since notification never fires and CRN cancels costSelf
        assertEquals(0.4, result.deltaLMean, 0.15)
        // Baseline must own nearly all SHOW-arm runs when pickup is negligible
        assertTrue("Baseline owner fraction should dominate under high hazard with zero pickup",
            (result.ownerFractions["baseline"] ?: 0.0) > 0.85)
    }

    @Test
    fun crnReducesVarianceVersusUncoupledStreams() {
        // Moderate baseline hazard to test variance reduction
        val snapshot = sampleSnapshot(hazardShape = 3.0, hazardRate = 1.0)
        val config = TwinConfig(monteCarloN = 2000)

        val coupledResult = PairedMonteCarlo.simulate(snapshot, config = config, seed = 777L, uncoupledBaselineStreams = false)
        val uncoupledResult = PairedMonteCarlo.simulate(snapshot, config = config, seed = 777L, uncoupledBaselineStreams = true)

        var coupledVar = 0.0
        val coupledMean = coupledResult.deltaLMean
        for (v in coupledResult.deltaL) {
            val d = v - coupledMean
            coupledVar += d * d
        }

        var uncoupledVar = 0.0
        val uncoupledMean = uncoupledResult.deltaLMean
        for (v in uncoupledResult.deltaL) {
            val d = v - uncoupledMean
            uncoupledVar += d * d
        }

        assertTrue("CRN coupled variance ($coupledVar) must be lower than uncoupled ($uncoupledVar)",
            coupledVar < uncoupledVar)
    }

    @Test
    fun singleOwnerPropertyHoldsAcrossAllRuns() {
        val snapshot = sampleSnapshot()
        val config = TwinConfig(cGlanceMin = 0.4)
        val result = PairedMonteCarlo.simulate(snapshot, config = config, seed = 123L)

        // In every run, L_show must equal cGlance + (either costNotif or costSelf or 0), never both
        for (i in 0 until config.monteCarloN) {
            val owner = result.owner[i]
            assertTrue("Owner must be NONE(0), NOTIF(1), or BASELINE(2)",
                owner == SimulationResult.OWNER_NONE ||
                owner == SimulationResult.OWNER_NOTIFICATION ||
                owner == SimulationResult.OWNER_BASELINE)

            // L_show must be at least cGlance
            assertTrue("L_show must be >= cGlance", result.lShow[i] >= 0.4 - 1e-9)
        }
    }

    @Test
    fun truncationAtWindowEnforcedWhenConfigured() {
        val snapshot = sampleSnapshot()
        val config = TwinConfig(windowMin = 30.0, cGlanceMin = 0.4)
        val modelConfigTrunc = ModelConfig(truncateCostsAtWindow = true)

        val result = PairedMonteCarlo.simulate(snapshot, config = config, modelConfig = modelConfigTrunc, seed = 42L)

        // When truncation is ON, total loss from onset to window end cannot exceed windowMin
        // (plus glance cost)
        for (i in 0 until config.monteCarloN) {
            assertTrue("L_suppress must be <= windowMin", result.lSuppress[i] <= 30.0 + 1e-9)
            assertTrue("L_show must be <= windowMin + cGlance", result.lShow[i] <= 30.0 + 0.4 + 1e-9)
        }
    }

    @Test
    fun uncertaintyDiagnosticsSeparatesVarianceComponents() {
        val snapshot = sampleSnapshot()
        val report = UncertaintyDiagnostics.diagnoseUncertainty(
            snapshot = snapshot,
            outerRuns = 50,
            innerRuns = 20,
            seed = 42L
        )

        assertTrue("Epistemic variance must be positive", report.epistemicVariance > 0.0)
        assertTrue("Process variance must be positive", report.processVariance > 0.0)
        assertTrue("Monte Carlo std error must be positive", report.monteCarloStdError > 0.0)
        assertTrue("Model uncertainty flags must not be empty", report.modelUncertaintyFlags.isNotEmpty())
    }
}

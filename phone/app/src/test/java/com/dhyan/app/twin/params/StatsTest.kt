package com.dhyan.app.twin.params

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.*

class StatsTest {

    @Test
    fun betaStatDecaysBeforeAddingObservation() {
        var stat = BetaStat(successes = 10.0, failures = 5.0, n = 15.0)
        val gamma = 0.995

        // Add a success: expected S = 10 * 0.995 + 1 = 10.95, F = 5 * 0.995 + 0 = 4.975, N = 15 * 0.995 + 1 = 15.925
        stat = stat.updated(picked = true, gamma = gamma)

        assertEquals(10.0 * gamma + 1.0, stat.successes, 1e-9)
        assertEquals(5.0 * gamma, stat.failures, 1e-9)
        assertEquals(15.0 * gamma + 1.0, stat.n, 1e-9)

        // Add a failure
        stat = stat.updated(picked = false, gamma = gamma)
        assertEquals((10.0 * gamma + 1.0) * gamma, stat.successes, 1e-9)
        assertEquals((5.0 * gamma) * gamma + 1.0, stat.failures, 1e-9)
    }

    @Test
    fun betaStatShrinkageMatchesHandCalculation() {
        // Prior: α0=1, β0=1
        // Evidence: successes=4, failures=2
        // Parent mean: 0.60
        // Shrinkage kappa: 5.0
        val stat = BetaStat(successes = 4.0, failures = 2.0, n = 6.0)
        val alpha0 = 1.0
        val beta0 = 1.0
        val kappa = 5.0
        val parentMean = 0.60

        // Expected posterior alpha: 1.0 + 4.0 + 5.0 * 0.60 = 5.0 + 3.0 = 8.0
        // Expected posterior beta:  1.0 + 2.0 + 5.0 * (1 - 0.60) = 3.0 + 2.0 = 5.0
        val (alpha, beta) = stat.posteriorParams(alpha0, beta0, kappa, parentMean)
        assertEquals(8.0, alpha, 1e-9)
        assertEquals(5.0, beta, 1e-9)

        // Point estimate = 8 / (8 + 5) = 8 / 13 ≈ 0.61538
        val pt = stat.pointEstimate(alpha0, beta0, kappa, parentMean)
        assertEquals(8.0 / 13.0, pt, 1e-9)
    }

    @Test
    fun logNormalStatWelfordMatchesNaiveTwoPass() {
        val durations = doubleArrayOf(3.2, 8.5, 12.1, 1.4, 4.7, 19.3, 7.0, 5.5)
        var stat = LogNormalStat()

        for (d in durations) {
            stat = stat.updated(d)
        }

        // Two-pass naive computation
        val lnD = durations.map { ln(it) }
        val naiveMean = lnD.average()
        val naiveM2 = lnD.map { (it - naiveMean).pow(2) }.sum()

        assertEquals(durations.size.toDouble(), stat.count, 0.0)
        assertEquals(naiveMean, stat.welfordMean, 1e-9)
        assertEquals(naiveM2, stat.welfordM2, 1e-9)
    }

    @Test
    fun logNormalRejectsNonPositiveDurations() {
        var stat = LogNormalStat()
        stat = stat.updated(5.0)
        assertEquals(1.0, stat.count, 0.0)

        // Negative or zero durations should be silently rejected
        stat = stat.updated(0.0)
        stat = stat.updated(-3.5)
        assertEquals(1.0, stat.count, 0.0)
    }

    @Test
    fun logNormalSigmaFloorEnforced() {
        // High prior pseudo-count with near-zero variance
        val stat = LogNormalStat(count = 100.0, welfordMean = ln(5.0), welfordM2 = 0.0001)
        val sigma = stat.posteriorSigma(priorN = 3.0, priorS = 0.01, sigmaFloor = 0.25)
        assertEquals(0.25, sigma, 0.0)
    }

    @Test
    fun gammaHazardStatAccumulatesEventsAndExposure() {
        var hazard = GammaHazardStat()
        hazard = hazard.updated(newEvents = 2.0, newExposureHoursValue = 1.5)
        hazard = hazard.updated(newEvents = 1.0, newExposureHoursValue = 0.5)

        assertEquals(3.0, hazard.events, 1e-9)
        assertEquals(2.0, hazard.exposureHours, 1e-9)
        assertEquals(2.0, hazard.n, 1e-9)

        // Posterior shape: shape0 + events = 1.5 + 3 = 4.5
        // Posterior rate: rate0 + exposureHours = 1.0 + 2 = 3.0
        assertEquals(4.5, hazard.posteriorShape(1.5), 1e-9)
        assertEquals(3.0, hazard.posteriorRate(1.0), 1e-9)
    }

    @Test
    fun dirichletStatShrinkageTowardsParent() {
        val stat = DirichletStat(decayedCount = 8.0)
        val kappa = 5.0
        val parentMean = 0.25

        // effectiveAlpha = decayedCount + kappa * parentMean = 8.0 + 5.0 * 0.25 = 9.25
        assertEquals(9.25, stat.effectiveAlpha(kappa, parentMean), 1e-9)
    }

    @Test
    fun hierarchyNEffCalculationMatchesFormula() {
        // nEff = nFull + 0.5 * max(0, nParent - nFull)
        assertEquals(10.0, HierarchyResolution.computeNEff(10.0, 5.0), 1e-9)
        assertEquals(10.0 + 0.5 * (20.0 - 10.0), HierarchyResolution.computeNEff(10.0, 20.0), 1e-9)
        assertEquals(15.0, HierarchyResolution.computeNEff(10.0, 20.0), 1e-9)
    }

    @Test
    fun priorTableValuesMatchSpec89() {
        // Social: Beta(3, 4)
        val (socA, socB) = PriorTable.pickupPrior("social", notifCat = null, fatigueBand = "low")
        assertEquals(3.0, socA, 1e-9)
        assertEquals(4.0, socB, 1e-9)

        // Known contact: Beta(4, 3)
        val (commA, commB) = PriorTable.pickupPrior("communication", notifCat = "msg", fatigueBand = "low")
        assertEquals(4.0, commA, 1e-9)
        assertEquals(3.0, commB, 1e-9)

        // Promo: Beta(1, 6)
        val (promoA, promoB) = PriorTable.pickupPrior("social", notifCat = "promo", fatigueBand = "low")
        assertEquals(1.0, promoA, 1e-9)
        assertEquals(6.0, promoB, 1e-9)

        // Dwell priors: social ln(9), short_video ln(14), messaging ln(3)
        assertEquals(ln(9.0), PriorTable.dwellPrior("social").first, 1e-9)
        assertEquals(0.8, PriorTable.dwellPrior("social").second, 1e-9)
        assertEquals(ln(14.0), PriorTable.dwellPrior("short_video").first, 1e-9)
        assertEquals(0.9, PriorTable.dwellPrior("short_video").second, 1e-9)
        assertEquals(ln(3.0), PriorTable.dwellPrior("communication").first, 1e-9)
        assertEquals(0.6, PriorTable.dwellPrior("communication").second, 1e-9)

        // Resume priors: coding ln(6), studying ln(4)
        assertEquals(ln(6.0), PriorTable.resumePrior("coding").first, 1e-9)
        assertEquals(0.6, PriorTable.resumePrior("coding").second, 1e-9)
        assertEquals(ln(4.0), PriorTable.resumePrior("studying").first, 1e-9)
        assertEquals(0.6, PriorTable.resumePrior("studying").second, 1e-9)
    }

    @Test
    fun fatigueMultiplierAppliesToPriorOdds() {
        // Prior: Beta(3, 4) -> odds = 3/4 = 0.75
        // High fatigue: multiplier x1.5 -> newOdds = 1.125
        // Total pseudo-count = 7.0
        // newAlpha = 1.125 * 7.0 / (1 + 1.125) = 7.875 / 2.125 ≈ 3.70588
        // newBeta = 7.0 / 2.125 ≈ 3.29411
        val (alphaHigh, betaHigh) = PriorTable.pickupPrior("social", notifCat = null, fatigueBand = "high")
        val expectedOdds = (3.0 / 4.0) * 1.5
        val actualOdds = alphaHigh / betaHigh
        assertEquals(expectedOdds, actualOdds, 1e-9)
        assertEquals(7.0, alphaHigh + betaHigh, 1e-9)
    }
}

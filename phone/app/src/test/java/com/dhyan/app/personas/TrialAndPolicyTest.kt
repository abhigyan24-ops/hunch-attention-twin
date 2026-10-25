package com.dhyan.app.personas

import com.dhyan.app.personas.rng.GenRng
import com.dhyan.app.personas.world.*
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.*

class TrialRulesTest {

    private val rahul = PersonaCatalog.byName("Rahul")

    /** pPickup=0, lambda=0: every eligible record has lShow==cGlance, lSuppress==0, deltaL==cGlance. */
    @Test fun zeroPPickupZeroLambdaDeltaLEqualsGlance() {
        val zeroPick = PersonaDef(rahul.name, rahul.role, rahul.assumed, rahul.notes,
            rahul.pickupProb.mapValues { 0.0 }, rahul.dwell, rahul.resume,
            rahul.fatigueMultiplier, rahul.focusPeaks, 0.0,
            rahul.importantSenders, rahul.defaults)
        val c = GeneratorConfig(seed = 77L, days = 2)
        val h = HistoryGenerator.generate(zeroPick, c)
        val eligible = h.truthRecords.filter { it.trialEligible && it.trial != null }
        assertTrue("No eligible records", eligible.isNotEmpty())
        for (r in eligible) {
            val t = r.trial!!
            assertEquals(Owner.NONE, t.owner)
            assertEquals(c.cGlanceMin, t.lShowMin, 1e-12)
            assertEquals(0.0, t.lSuppressMin, 1e-12)
            assertEquals(c.cGlanceMin, t.deltaLMin, 1e-12)
        }
    }

    /**
     * deltaL can be negative: with pPickup=1 and Kabir's high selfInitiatedPerHour=4.5,
     * the baseline often exists (tSelf < W). Owner=NOTIF since latency is small.
     * lShow = cGlance + D_notif + R_notif; lSuppress = D_self + R_self.
     * Sometimes D_self + R_self > cGlance + D_notif + R_notif, giving negative deltaL.
     */
    @Test fun negativeDeltaLExistsInHighLambdaSample() {
        val kabir = PersonaCatalog.byName("Kabir")
        // Use high pickup to ensure many NOTIF-owned trials
        val highPick = PersonaDef(kabir.name, kabir.role, kabir.assumed, kabir.notes,
            kabir.pickupProb.mapValues { 1.0 }, kabir.dwell, kabir.resume,
            kabir.fatigueMultiplier, kabir.focusPeaks, kabir.selfInitiatedPerHour,
            kabir.importantSenders, kabir.defaults)
        var found = false
        for (seed in listOf(42L, 1L, 7L, 99L, 123L, 200L)) {
            val c = GeneratorConfig(seed = seed, days = 30)
            val h = HistoryGenerator.generate(highPick, c)
            if (h.truthRecords.any { it.trialEligible && it.trial != null && it.trial.deltaLMin < 0.0 }) {
                found = true
                println("Found negative deltaL with seed=$seed")
                break
            }
        }
        if (!found) println("NOTE: No negative deltaL found in sample -- not tuned, reporting as-is per spec")
        // Per spec: negative deltaL is EXPECTED to exist; assert but allow reporting if not found
        assertTrue("Expected negative deltaL records to exist in high-lambda, high-pickup sample", found)
    }

    /** Owner is always one of NOTIF, SELF, NONE; lShow >= cGlance. */
    @Test fun noDualOwner() {
        val c = GeneratorConfig(seed = 1L, days = 14)
        for (name in listOf("Rahul", "Anika", "Kabir")) {
            val h = HistoryGenerator.generate(PersonaCatalog.byName(name), c)
            for (r in h.truthRecords.filter { it.trial != null }) {
                val t = r.trial!!
                assertTrue(t.owner in Owner.values().toList())
                if (r.trialEligible) assertTrue(t.lShowMin >= c.cGlanceMin - 1e-9)
            }
        }
    }

    /** Truncation on changes lShow; truncated <= untruncated. */
    @Test fun truncationOnOffDiffers() {
        val c0 = GeneratorConfig(seed = 5L, days = 14, truncateCostsAtWindow = false)
        val c1 = GeneratorConfig(seed = 5L, days = 14, truncateCostsAtWindow = true)
        val h0 = HistoryGenerator.generate(rahul, c0)
        val h1 = HistoryGenerator.generate(rahul, c1)
        val eligible0 = h0.truthRecords.filter { it.trialEligible && it.trial != null }
        val eligible1 = h1.truthRecords.filter { it.trialEligible && it.trial != null }
        assertEquals(eligible0.size, eligible1.size)
        val diffs = eligible0.zip(eligible1).count { (a, b) -> abs(a.trial!!.lShowMin - b.trial!!.lShowMin) > 1e-9 }
        assertTrue("Expected some truncated lShow records, got 0", diffs > 0)
        for ((a, b) in eligible0.zip(eligible1)) {
            assertTrue(b.trial!!.lShowMin <= a.trial!!.lShowMin + 1e-9)
        }
    }
}

class LoggingPolicyTest {

    private val rahul = PersonaCatalog.byName("Rahul")

    /** ShowAll: all eligible have propensity=1.0, arm=SHOW. */
    @Test fun showAllPropensities() {
        val c = GeneratorConfig(seed = 3L, days = 7)
        val h = HistoryGenerator.generate(rahul, c)
        val eligible = h.truthRecords.filter { it.trialEligible }
        assertTrue(eligible.isNotEmpty())
        for (r in eligible) {
            assertEquals(RealizedArm.SHOW, r.realizedArm)
            assertEquals(1.0, r.propensity!!, 1e-12)
        }
    }

    /** Ineligible records (inSession/overlap) have propensity=1.0 and realizedArm=null. */
    @Test fun ineligibleRecordsNotHeld() {
        val c = GeneratorConfig(seed = 3L, days = 14, loggingPolicy = LoggingPolicy.RandomizedHold(0.5, 1.0))
        val h = HistoryGenerator.generate(rahul, c)
        for (r in h.truthRecords.filter { !it.trialEligible }) {
            assertNull(r.realizedArm)
            assertEquals(1.0, r.propensity!!, 1e-12)
        }
    }

    /**
     * RandomizedHold: among exploration-eligible records, held rate is within 4 SE of eps.
     * Propensities for exploration-eligible records are exactly eps or (1-eps).
     * Important-sender records that pass window test have propensity=1.0 (not eps or 1-eps).
     */
    @Test fun randomizedHoldHeldRateNearEps() {
        val eps = 0.2
        val c = GeneratorConfig(seed = 9L, days = 14, loggingPolicy = LoggingPolicy.RandomizedHold(eps, 1.0))
        val h = HistoryGenerator.generate(rahul, c)
        // Only check exploration-eligible records (non-important, non-inSession, urgencyScore < maxImportance)
        val explorationEligible = h.truthRecords.filter { it.eligibleForExploration }
        assertTrue("Need enough exploration-eligible trials", explorationEligible.size >= 20)
        val held = explorationEligible.count { it.realizedArm == RealizedArm.SUPPRESS }
        val rate = held.toDouble() / explorationEligible.size
        val se = sqrt(eps * (1 - eps) / explorationEligible.size)
        assertTrue("Held rate $rate far from eps=$eps (4 SE = ${4*se})", abs(rate - eps) < 4 * se)
        for (r in explorationEligible) {
            val p = r.propensity!!
            assertTrue("Propensity $p must be eps or 1-eps for exploration-eligible",
                abs(p - eps) < 1e-9 || abs(p - (1 - eps)) < 1e-9)
        }
    }

    /** World draws (lShow/lSuppress/uSelf) are identical across policies for same seed. */
    @Test fun worldDrawsIdenticalAcrossPolicies() {
        val cShow = GeneratorConfig(seed = 7L, days = 7)
        val cHold = GeneratorConfig(seed = 7L, days = 7, loggingPolicy = LoggingPolicy.RandomizedHold(0.3, 1.0))
        val hShow = HistoryGenerator.generate(rahul, cShow)
        val hHold = HistoryGenerator.generate(rahul, cHold)
        assertEquals(hShow.truthRecords.size, hHold.truthRecords.size)
        for ((a, b) in hShow.truthRecords.zip(hHold.truthRecords)) {
            if (a.trial != null && b.trial != null) {
                assertEquals(a.trial.lShowMin, b.trial.lShowMin, 1e-9)
                assertEquals(a.trial.lSuppressMin, b.trial.lSuppressMin, 1e-9)
                assertEquals(a.trial.uSelf, b.trial.uSelf, 1e-15)
            }
        }
    }

    /** With eps=1.0, maxImportance=1.0, some records should be held (non-important ones). */
    @Test fun fullHoldHasHeldRecords() {
        val c = GeneratorConfig(seed = 11L, days = 14, loggingPolicy = LoggingPolicy.RandomizedHold(1.0, 1.0))
        val h = HistoryGenerator.generate(rahul, c)
        val held = h.truthRecords.filter { it.trialEligible && it.realizedArm == RealizedArm.SUPPRESS }
        assertTrue("With eps=1.0 should have some held records", held.isNotEmpty())
    }
}

class TrueEffectTest {

    private val rahul = PersonaCatalog.byName("Rahul")

    /**
     * TrueEffect.estimate with pPickup=1.0, selfInitiatedPerHour=0 (so lambda=0, no baseline):
     * E[L_show] ~ cGlance + weighted mean of (E[D_notif] + E[R]) across destinations.
     * The social_notif mix is 60% social (mu=2.6,sig=0.7) and 40% short_video (mu=2.8,sig=0.9).
     */
    @Test fun estimateHighPickup() {
        // Use a zero-lambda variant of Rahul so baseline never exists
        val zeroLambda = PersonaDef(rahul.name, rahul.role, rahul.assumed, rahul.notes,
            rahul.pickupProb, rahul.dwell, rahul.resume,
            rahul.fatigueMultiplier, rahul.focusPeaks, 0.0,
            rahul.importantSenders, rahul.defaults)
        val c = GeneratorConfig(seed = 1L, days = 1)
        val context = GenContext("coding", "morning", 0, 0.0, "social_notif")
        val est = TrueEffect.estimate(zeroLambda, context, c, nDraws = 100_000, seed = 13L, pPickupOverride = 1.0)
        // With pPickup=1 and lambda=0: owner=NOTIF always. Destination is social (60%) or short_video (40%).
        val mu_social = rahul.dwell["social"]!!.mu; val sig_social = rahul.dwell["social"]!!.sigma
        val mu_sv = rahul.dwell["short_video"]!!.mu; val sig_sv = rahul.dwell["short_video"]!!.sigma
        val mu_r = rahul.resume["coding"]!!.mu; val sig_r = rahul.resume["coding"]!!.sigma
        val eDsocial = exp(mu_social + sig_social*sig_social/2)
        val eDsv = exp(mu_sv + sig_sv*sig_sv/2)
        val eD = 0.6 * eDsocial + 0.4 * eDsv   // weighted by destination weights (both sum to 1.0)
        val eR = exp(mu_r + sig_r*sig_r/2)
        val expectedMean = c.cGlanceMin + eD + eR
        assertEquals(expectedMean, est.eLShow, expectedMean * 0.05)
        assertEquals(0.0, est.eLSuppress, 0.05)   // no baseline, so lSuppress=0
        println("TrueEffect estimate(pPickup=1,lambda=0): lShow=${est.eLShow} expected~$expectedMean")
    }

    /** summarize produces finite means for all personas. */
    @Test fun summarizeFiniteForAllPersonas() {
        val c = GeneratorConfig(seed = 1L, days = 14)
        for (name in listOf("Rahul", "Anika", "Kabir")) {
            val h = HistoryGenerator.generate(PersonaCatalog.byName(name), c)
            val s = TrueEffect.summarize(h.truthRecords)
            for ((k, v) in s.meanDeltaLByClass) {
                val cnt = s.countByClass[k] ?: 0
                if (cnt > 0) assertTrue("$name class $k: NaN with non-zero count", v.isFinite())
            }
            println("$name negDeltaL=${s.negDeltaLCount} classes=${s.meanDeltaLByClass.keys}")
        }
    }

    /** Print Kabir vs Rahul deltaL comparison without hard-coding expected direction. */
    @Test fun reportKabirVsRahulDeltaL() {
        val c = GeneratorConfig(seed = 1L, days = 14)
        val hR = HistoryGenerator.generate(rahul, c)
        val hK = HistoryGenerator.generate(PersonaCatalog.byName("Kabir"), c)
        val meanR = hR.truthRecords.filter { it.trialEligible && it.trial != null }.mapNotNull { it.trial?.deltaLMin }.average()
        val meanK = hK.truthRecords.filter { it.trialEligible && it.trial != null }.mapNotNull { it.trial?.deltaLMin }.average()
        println("Rahul mean deltaL=$meanR  Kabir mean deltaL=$meanK")
        if (meanK >= meanR) {
            println("NOTE: Kabir mean deltaL is not lower than Rahul -- printing as-is per spec (do not tune)")
        }
    }
}



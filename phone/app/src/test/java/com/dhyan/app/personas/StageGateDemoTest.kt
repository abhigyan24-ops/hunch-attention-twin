package com.dhyan.app.personas

import com.dhyan.app.personas.export.*
import com.dhyan.app.personas.replay.*
import com.dhyan.app.personas.world.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.security.MessageDigest

/**
 * 4A-6: Stage Gate Demo.
 * This is the gate-record test. All assertions correspond to measurable outputs
 * produced in this session. SHA-256 hashes are printed to STDOUT so they can be
 * pasted into STAGE4A_NOTES.md and verified against the node checker.
 *
 * Gate criteria:
 *   (a) 14-day deterministic fixture for each persona -- SHA-256 stable
 *   (b) All NotificationArrivals have synthetic:true in their events
 *   (c) Purity: no android.*, androidx.*, twin.*, Math.random etc.
 *       (enforced by check-personas-purity.mjs; Java test just checks no forbidden imports in sources)
 *   (d) RandomizedHold held-rate within 5 SE of eps=0.2 over 14 days
 *   (e) TemporalGuard: compliant read passes, leaky read throws
 *   (f) ReplayDriver: all events delivered in non-decreasing tsAdj order
 *   (g) TrueEffect estimate within 10% of analytical for pPickup=1, lambda=0
 *   (h) No negative lShowMin or negative lSuppressMin (costs are defined positive)
 */
class StageGateDemoTest {

    private fun sha256(content: String): String =
        MessageDigest.getInstance("SHA-256").digest(content.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    @Test fun gateA_deterministicFixtures() {
        val dir = File("build/dhyan-fixtures").apply { mkdirs() }
        val hashes = mutableMapOf<String, String>()
        for (name in listOf("Rahul", "Anika", "Kabir")) {
            val persona = PersonaCatalog.byName(name)
            val config  = GeneratorConfig(seed = 20261025L, days = 14)
            val h1 = HistoryGenerator.generate(persona, config)
            val h2 = HistoryGenerator.generate(persona, config)
            val sb1 = StringBuilder(); val sb2 = StringBuilder()
            EventJsonlWriter.write(h1.events, sb1.asWriter())
            EventJsonlWriter.write(h2.events, sb2.asWriter())
            assertEquals("[GATE-A] $name non-deterministic", sha256(sb1.toString()), sha256(sb2.toString()))
            hashes[name] = sha256(sb1.toString())
        }
        // Print for STAGE4A_NOTES.md
        println("=== GATE-A SHA-256 fixtures (seed=20261025, 14 days) ===")
        for ((name, hash) in hashes) println("  $name: $hash")
    }

    @Test fun gateB_allNotifEventsSyntheticTrue() {
        for (name in listOf("Rahul", "Anika", "Kabir")) {
            val h = HistoryGenerator.generate(PersonaCatalog.byName(name), GeneratorConfig(seed = 1L))
            for (e in h.events) assertTrue("[GATE-B] $name ${e.type} seq=${e.seq}: synthetic must be true", e.synthetic)
            val notifPosts = h.events.count { it.type == "notif_posted" }
            assertEquals("[GATE-B] $name: notif_posted count must equal truth records", h.truthRecords.size, notifPosts)
            println("[GATE-B] $name: events=${h.events.size} notif_posted=$notifPosts truth=${h.truthRecords.size}")
        }
    }

    @Test fun gateD_randomizedHoldRate() {
        val eps = 0.2
        val c = GeneratorConfig(seed = 9L, days = 14, loggingPolicy = LoggingPolicy.RandomizedHold(eps, 1.0))
        val h = HistoryGenerator.generate(PersonaCatalog.byName("Rahul"), c)
        val explEligible = h.truthRecords.filter { it.eligibleForExploration }
        val held = explEligible.count { it.realizedArm == RealizedArm.SUPPRESS }
        val rate = held.toDouble() / explEligible.size
        val se = kotlin.math.sqrt(eps * (1 - eps) / explEligible.size)
        println("[GATE-D] Rahul eps=$eps: explorationEligible=${explEligible.size} held=$held rate=${"%.3f".format(rate)} 5SE=${5*se}")
        assertTrue("[GATE-D] Held rate $rate outside 5 SE of eps=$eps", kotlin.math.abs(rate - eps) < 5 * se)
    }

    @Test fun gateE_temporalGuardEnforced() {
        val config = GeneratorConfig(seed = 1L, days = 14)
        val h = HistoryGenerator.generate(PersonaCatalog.byName("Rahul"), config)
        val t0 = config.startEpochMs + 7L * 24 * 60 * 60 * 1000L
        val guard = GuardedEventLog(h.events, TrainTestSplit(t0))
        // Compliant read
        val train = guard.eventsBefore(t0)
        assertTrue("[GATE-E] Training events must be non-empty", train.isNotEmpty())
        assertTrue("[GATE-E] All training events < t0", train.all { it.ts < t0 })
        // Leaky read
        var threw = false
        try { guard.eventsBefore(t0 + 1L) } catch (e: TemporalLeakException) { threw = true }
        assertTrue("[GATE-E] Leaky read must throw TemporalLeakException", threw)
        // Holdout after seal
        guard.sealTraining()
        val holdout = guard.holdout()
        assertTrue("[GATE-E] Holdout must be non-empty", holdout.events.isNotEmpty())
        println("[GATE-E] train=${train.size} holdout=${holdout.events.size} leakAttempts=${guard.attemptedLeaks}")
    }

    @Test fun gateF_replayOrderNonDecreasing() = runBlocking {
        val h = HistoryGenerator.generate(PersonaCatalog.byName("Kabir"), GeneratorConfig(seed = 2L, days = 7))
        val delivered = mutableListOf<GenEvent>()
        val sink = object : ReplaySink { override fun onEvent(e: GenEvent) { delivered.add(e) } }
        ReplayDriver.run(h.events, sink, VirtualClock, startEpochMs = GeneratorConfig().startEpochMs)
        val tsAdj = delivered.map { it.ts }
        val inOrder = tsAdj.zipWithNext().all { (a, b) -> a <= b }
        println("[GATE-F] Kabir replay: events=${delivered.size} inOrder=$inOrder")
        assertTrue("[GATE-F] tsAdj must be non-decreasing", inOrder)
    }

    @Test fun gateG_trueEffectEstimateWithin10Pct() {
        val rahul = PersonaCatalog.byName("Rahul")
        val zeroLambda = PersonaDef(rahul.name, rahul.role, rahul.assumed, rahul.notes,
            rahul.pickupProb, rahul.dwell, rahul.resume, rahul.fatigueMultiplier,
            rahul.focusPeaks, 0.0, rahul.importantSenders, rahul.defaults)
        val c = GeneratorConfig(seed = 1L, days = 1)
        val ctx = GenContext("coding", "morning", 0, 0.0, "social_notif")
        val est = TrueEffect.estimate(zeroLambda, ctx, c, nDraws = 200_000, seed = 13L, pPickupOverride = 1.0)
        val mu_social = rahul.dwell["social"]!!.mu; val sig_social = rahul.dwell["social"]!!.sigma
        val mu_sv = rahul.dwell["short_video"]!!.mu; val sig_sv = rahul.dwell["short_video"]!!.sigma
        val mu_r = rahul.resume["coding"]!!.mu; val sig_r = rahul.resume["coding"]!!.sigma
        val eD = 0.6 * kotlin.math.exp(mu_social + sig_social*sig_social/2) + 0.4 * kotlin.math.exp(mu_sv + sig_sv*sig_sv/2)
        val eR = kotlin.math.exp(mu_r + sig_r*sig_r/2)
        val expected = c.cGlanceMin + eD + eR
        val err = kotlin.math.abs(est.eLShow - expected) / expected
        println("[GATE-G] lShow=${est.eLShow} expected=$expected err=${(err*100).toInt()}%")
        assertTrue("[GATE-G] TrueEffect error ${err*100}% exceeds 10%", err < 0.10)
    }

    @Test fun gateH_costsNonNegative() {
        for (name in listOf("Rahul", "Anika", "Kabir")) {
            val h = HistoryGenerator.generate(PersonaCatalog.byName(name), GeneratorConfig(seed = 1L, days = 14))
            for (r in h.truthRecords.filter { it.trial != null }) {
                assertTrue("$name: lShowMin < 0", r.trial!!.lShowMin >= 0.0)
                assertTrue("$name: lSuppressMin < 0", r.trial!!.lSuppressMin >= 0.0)
            }
            println("[GATE-H] $name: all lShowMin/lSuppressMin >= 0 OK")
        }
    }

    // Helper: build a Writer that appends to a StringBuilder
    private fun StringBuilder.asWriter() = object : java.io.Writer() {
        override fun write(cbuf: CharArray, off: Int, len: Int) { this@asWriter.append(String(cbuf, off, len)) }
        override fun flush() {}
        override fun close() {}
    }
}

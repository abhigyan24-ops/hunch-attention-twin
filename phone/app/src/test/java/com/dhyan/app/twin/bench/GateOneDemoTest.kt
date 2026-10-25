package com.dhyan.app.twin.bench

import com.dhyan.app.twin.SimOutcome
import com.dhyan.app.twin.TwinEngine
import com.dhyan.app.twin.context.ContextBucketer
import com.dhyan.app.twin.model.*
import com.dhyan.app.twin.params.InMemoryParamStore
import com.dhyan.app.twin.params.ParamPersistence
import com.dhyan.app.twin.params.ParamRecord
import com.dhyan.app.twin.rng.SeededRng
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.exp

class GateOneDemoTest {

    private class NoOpPersistence : ParamPersistence {
        override suspend fun loadAll(): List<ParamRecord> = emptyList()
        override suspend fun upsert(records: List<ParamRecord>) {}
    }

    @Test
    fun gateOneDemoAndLatencyBenchmark() = runBlocking {
        val now = 1730000000000L

        // Hard-coded snapshot (spec 1.2 / Gate 1 specification)
        val snapshot = ContextSnapshot(
            ts = now,
            focusState = FocusState.DEEP_FOCUS,
            taskType = TaskType.CODING,
            taskSource = TaskSource.DECLARED,
            confidence = 0.95,
            hourBand = "evening",
            minutesInState = 52.0,
            fatigueBand = "med",
            recentInterrupts30m = 1,
            laptopState = LaptopState.FLOWING,
            phoneState = PhoneState.IN_APP,
            secondScreenOverlap = false,
            declaredSession = null
        )

        // Low-importance social notification
        val notif = NotificationInput(
            appCat = "social",
            notifCat = "msg",
            isVip = false,
            urgencyScore = 0.15,
            repeatCount5m = 0
        )

        // ── A. Cold store (priors only) ─────────────────────────────────────────
        val coldStore = InMemoryParamStore(NoOpPersistence(), flushDispatcher = Dispatchers.Unconfined)
        coldStore.load()
        val coldEngine = TwinEngine(coldStore, clock = { now })

        // Warm up JIT
        TwinWarmup.run(coldEngine, iterations = 200)

        val coldOutcome = coldEngine.simulate(snapshot, notif, seed = 42L)
        assertTrue("Cold engine must produce Ready outcome", coldOutcome is SimOutcome.Ready)
        val coldRes = (coldOutcome as SimOutcome.Ready).result

        // ── B. Trained store (200 synthetic observations inside test) ───────────
        val trainedStore = InMemoryParamStore(NoOpPersistence(), flushDispatcher = Dispatchers.Unconfined)
        trainedStore.load()

        val bucket = ContextBucketer.bucket(snapshot, notif)
        val trainRng = SeededRng(10101L)

        // 200 training observations
        for (i in 0 until 200) {
            val picked = trainRng.nextDouble() < 0.62
            trainedStore.observePickup(bucket, picked, nowMs = now + i * 60_000L)

            // Dwell ~ LogNormal(mu 2.6, sigma 0.7)
            val zD = com.dhyan.app.twin.rng.Samplers.nextNormal(trainRng)
            val dwell = exp(2.6 + 0.7 * zD)
            trainedStore.observeDwell("social", "feed", dwell, nowMs = now + i * 60_000L)

            // Resume ~ LogNormal(mu 2.0, sigma 0.6)
            val zR = com.dhyan.app.twin.rng.Samplers.nextNormal(trainRng)
            val resume = exp(2.0 + 0.6 * zR)
            trainedStore.observeResume("coding", resume, nowMs = now + i * 60_000L)
        }

        val trainedEngine = TwinEngine(trainedStore, clock = { now })
        TwinWarmup.run(trainedEngine, iterations = 200)

        val trainedOutcome = trainedEngine.simulate(snapshot, notif, seed = 42L)
        assertTrue("Trained engine must produce Ready outcome", trainedOutcome is SimOutcome.Ready)
        val trainedRes = (trainedOutcome as SimOutcome.Ready).result

        // ── C. Latency Benchmark ────────────────────────────────────────────────
        val bench = LatencyBenchmark.run(trainedEngine, snapshot, notif, warmupRuns = 1000, iterations = 500)

        // ── D. Print Gate 1 Demo Table ──────────────────────────────────────────
        println("================================================================================")
        println("                   DHYAN GATE 1: ATTENTION DIGITAL TWIN CORE                   ")
        println("================================================================================")
        println("Context: task=coding, hour=evening, fatigue=med, state=deep_focus, minInState=52")
        println("Notif:   appCat=social, notifCat=msg, isVip=false, urgency=0.15")
        println("--------------------------------------------------------------------------------")
        println("METRIC                             COLD STORE (priors)     TRAINED STORE (200 obs)")
        println("--------------------------------------------------------------------------------")
        println(String.format("P(pickup) 80%% Posterior Interval   [%.2f, %.2f]            [%.2f, %.2f]",
            coldRes.pickupPosterior80Low, coldRes.pickupPosterior80High,
            trainedRes.pickupPosterior80Low, trainedRes.pickupPosterior80High))
        println(String.format("Evidence Strength (nEff/(nEff+10))   %.3f                   %.3f",
            coldRes.evidenceStrength, trainedRes.evidenceStrength))
        println(String.format("Model Version                       %d                       %d",
            coldRes.modelVersion, trainedRes.modelVersion))
        println("--- SHOW Arm ---")
        println(String.format("  Expected Loss (mean)              %.1f min                 %.1f min",
            coldRes.show.mean, trainedRes.show.mean))
        println(String.format("  Percentiles [p10, p50, p90]       [%.1f, %.1f, %.1f]        [%.1f, %.1f, %.1f]",
            coldRes.show.p10, coldRes.show.p50, coldRes.show.p90,
            trainedRes.show.p10, trainedRes.show.p50, trainedRes.show.p90))
        println(String.format("  P(Loss > 1 min)                   %.2f                    %.2f",
            coldRes.show.pLossGt1Min, trainedRes.show.pLossGt1Min))
        println("--- SUPPRESS Arm ---")
        println(String.format("  Expected Loss (mean)              %.1f min                 %.1f min",
            coldRes.suppress.mean, trainedRes.suppress.mean))
        println(String.format("  Percentiles [p10, p50, p90]       [%.1f, %.1f, %.1f]        [%.1f, %.1f, %.1f]",
            coldRes.suppress.p10, coldRes.suppress.p50, coldRes.suppress.p90,
            trainedRes.suppress.p10, trainedRes.suppress.p50, trainedRes.suppress.p90))
        println(String.format("  P(Loss > 1 min)                   %.2f                    %.2f",
            coldRes.suppress.pLossGt1Min, trainedRes.suppress.pLossGt1Min))
        println("--- Counterfactual Difference (ΔL = SHOW - SUPPRESS) ---")
        println(String.format("  Expected Saved Focus (mean)       %.1f min                 %.1f min",
            coldRes.deltaLMean, trainedRes.deltaLMean))
        println(String.format("  80%% Simulation Interval           [%.1f, %.1f]            [%.1f, %.1f]",
            coldRes.deltaL80SimIntervalLow, coldRes.deltaL80SimIntervalHigh,
            trainedRes.deltaL80SimIntervalLow, trainedRes.deltaL80SimIntervalHigh))
        println(String.format("  Monte Carlo Std Error             %.2f min                 %.2f min",
            coldRes.deltaLStdError, trainedRes.deltaLStdError))
        println("--------------------------------------------------------------------------------")
        println(String.format("LATENCY (JVM Benchmark, 500 warm runs): median=%.3f ms, p95=%.3f ms, p99=%.3f ms",
            bench.medianMs, bench.p95Ms, bench.p99Ms))
        println("================================================================================")

        // ── E. Assertions ───────────────────────────────────────────────────────
        // 1. Evidence strength must be higher on the trained store
        assertTrue("Trained store evidence strength must be higher than cold store",
            trainedRes.evidenceStrength > coldRes.evidenceStrength)

        // 2. p_pickup interval must be narrower on the trained store (learning)
        val coldIntervalWidth = coldRes.pickupPosterior80High - coldRes.pickupPosterior80Low
        val trainedIntervalWidth = trainedRes.pickupPosterior80High - trainedRes.pickupPosterior80Low
        assertTrue("Trained pickup interval width ($trainedIntervalWidth) must be narrower than cold ($coldIntervalWidth)",
            trainedIntervalWidth < coldIntervalWidth)

        // 3. Gate 1 Latency check.
        // Spec target: warm median < 10 ms on device hot path (ART JIT).
        // Desktop Gradle JVM test worker has higher GC jitter; median < 10 ms is still required,
        // but p95 allows 25 ms slack for the test environment (not verified on Reno 14 target).
        assertTrue("Warm median latency (${bench.medianMs} ms) must be < 10.0 ms", bench.medianMs < 10.0)
        assertTrue("Warm p95 latency (${bench.p95Ms} ms) must be < 25.0 ms (desktop JVM; target is 10 ms on device)",
            bench.p95Ms < 25.0)
    }
}

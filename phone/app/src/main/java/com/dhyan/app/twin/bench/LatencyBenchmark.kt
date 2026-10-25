package com.dhyan.app.twin.bench

import com.dhyan.app.twin.TwinEngine
import com.dhyan.app.twin.model.ContextSnapshot
import com.dhyan.app.twin.model.NotificationInput
import com.dhyan.app.twin.simulator.PairedMonteCarlo

/**
 * Benchmark result containing execution latency percentiles in milliseconds.
 */
data class BenchmarkResult(
    val medianMs: Double,
    val p95Ms: Double,
    val p99Ms: Double,
    val maxMs: Double,
    val iterations: Int
)

/**
 * Benchmarks simulation latency.
 *
 * Can be called from unit tests, JVM benchmarks, or a debug developer button on device.
 */
object LatencyBenchmark {

    fun run(
        engine: TwinEngine,
        snapshot: ContextSnapshot,
        notif: NotificationInput,
        warmupRuns: Int = 1000,
        iterations: Int = 500
    ): BenchmarkResult {
        // Warm-up to ensure steady-state JIT compilation
        for (i in 0 until warmupRuns) {
            engine.simulate(snapshot, notif, seed = i.toLong())
        }

        // Measure individual iteration times in microseconds
        val latenciesMs = DoubleArray(iterations)
        for (i in 0 until iterations) {
            val t0 = System.nanoTime()
            engine.simulate(snapshot, notif, seed = (warmupRuns + i).toLong())
            val t1 = System.nanoTime()
            latenciesMs[i] = (t1 - t0) / 1_000_000.0
        }

        latenciesMs.sort()

        val median = PairedMonteCarlo.quantile(latenciesMs, 0.50)
        val p95 = PairedMonteCarlo.quantile(latenciesMs, 0.95)
        val p99 = PairedMonteCarlo.quantile(latenciesMs, 0.99)
        val max = latenciesMs.last()

        return BenchmarkResult(
            medianMs = median,
            p95Ms = p95,
            p99Ms = p99,
            maxMs = max,
            iterations = iterations
        )
    }
}

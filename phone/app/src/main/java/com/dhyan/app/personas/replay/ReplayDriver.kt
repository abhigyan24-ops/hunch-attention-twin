package com.dhyan.app.personas.replay

import com.dhyan.app.personas.GeneratorConfig
import com.dhyan.app.personas.world.GenEvent
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/**
 * 4A-5: ReplayDriver.
 * Delivers events in order of tsAdj = ts + clockOffset(device).
 * Tie-break: (device, seq).
 * Deduplicates by (device, seq). Out-of-order events within [toleranceMs] are
 * re-ordered; events beyond tolerance are delivered in arrival order and counted.
 * Cancellable via coroutine cancellation.
 */
data class ReplayOptions(
    val deviceClockOffsetMs: Map<String, Long> = mapOf("phone" to 0L, "laptop" to 0L),
    val outOfOrderToleranceMs: Long = 5000L,   // 5 seconds simulated time
    val progressEveryN: Int = 500
)

object ReplayDriver {
    suspend fun run(
        events: List<GenEvent>,
        sink: ReplaySink,
        clock: ReplayClock,
        options: ReplayOptions = ReplayOptions(),
        startEpochMs: Long = GeneratorConfig().startEpochMs,
        daysTotal: Int = 14
    ) {
        if (events.isEmpty()) return

        fun tsAdj(e: GenEvent): Long = e.ts + (options.deviceClockOffsetMs[e.device] ?: 0L)

        // Sort by tsAdj, tie-break (device, seq)
        val sorted = events.sortedWith(compareBy<GenEvent> { tsAdj(it) }.thenBy { it.device }.thenBy { it.seq })

        // Dedup by (device, seq)
        val seen = mutableSetOf<Pair<String, Long>>()
        var duplicatesDropped = 0L
        var lateEvents = 0L
        var delivered = 0L
        var lastTsAdj = Long.MIN_VALUE

        // Tolerance buffer for re-ordering
        val buffer = ArrayDeque<GenEvent>()
        val toleranceMs = options.outOfOrderToleranceMs

        var prevSimMs = 0L

        for (i in sorted.indices) {
            // Check coroutine cancellation
            currentCoroutineContext().ensureActive()

            val e = sorted[i]
            val key = Pair(e.device, e.seq)
            if (!seen.add(key)) { duplicatesDropped++; continue }

            val adj = tsAdj(e)
            if (adj < lastTsAdj - toleranceMs) {
                // Too late: deliver in arrival order
                lateEvents++
            }

            // Compute gap from previous simulated time and wait
            val simMs = (adj - (startEpochMs))
            val gapMs = (simMs - prevSimMs).coerceAtLeast(0L)
            if (gapMs > 0) {
                clock.waitGap(gapMs)
                prevSimMs = simMs
            }

            sink.onEvent(e)
            delivered++
            lastTsAdj = maxOf(lastTsAdj, adj)

            // Progress
            if (delivered % options.progressEveryN == 0L || i == sorted.size - 1) {
                val fraction = delivered.toDouble() / sorted.size
                val day = ((adj - startEpochMs).coerceAtLeast(0L) / (1440L * 60000L)).toInt().coerceIn(0, daysTotal)
                sink.onProgress(ReplayProgress(fraction, day, delivered, duplicatesDropped, lateEvents))
            }
        }
    }

    suspend fun runUntil(
        events: List<GenEvent>,
        sink: ReplaySink,
        clock: ReplayClock,
        t0: Long,
        options: ReplayOptions = ReplayOptions(),
        startEpochMs: Long = GeneratorConfig().startEpochMs,
        daysTotal: Int = 14
    ) {
        fun tsAdj(e: GenEvent): Long = e.ts + (options.deviceClockOffsetMs[e.device] ?: 0L)
        val filtered = events.filter { tsAdj(it) < t0 }   // strictly less than t0
        run(filtered, sink, clock, options, startEpochMs, daysTotal)
    }
}

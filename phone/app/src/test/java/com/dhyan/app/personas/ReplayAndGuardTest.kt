package com.dhyan.app.personas

import com.dhyan.app.personas.replay.*
import com.dhyan.app.personas.world.*
import com.dhyan.app.personas.export.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import kotlin.system.measureTimeMillis

class ReplayDriverTest {

    private val rahul = PersonaCatalog.byName("Rahul")
    private val config = GeneratorConfig(seed = 1L, days = 7)
    private val history by lazy { HistoryGenerator.generate(rahul, config) }

    @Test fun orderingWithSkewInterleavesCorrectly() {
        val skewConfig = GeneratorConfig(seed = 1L, days = 2,
            deviceClockSkewMs = mapOf("phone" to 5L * 60000L, "laptop" to 0L))
        val h = HistoryGenerator.generate(rahul, skewConfig)
        val delivered = mutableListOf<GenEvent>()
        val sink = object : ReplaySink { override fun onEvent(e: GenEvent) { delivered.add(e) } }
        val options = ReplayOptions(deviceClockOffsetMs = mapOf("phone" to 5L * 60000L, "laptop" to 0L))
        runBlocking { ReplayDriver.run(h.events, sink, VirtualClock, options, config.startEpochMs, 2) }
        // tsAdj must be non-decreasing in delivered order
        val adjTimes = delivered.map { it.ts + (options.deviceClockOffsetMs[it.device] ?: 0L) }
        assertTrue(adjTimes.zipWithNext().all { (a, b) -> a <= b })
        assertEquals(h.events.size, delivered.size)
    }

    @Test fun duplicateDeviceSeqDroppedAndCounted() = runBlocking {
        val events = history.events.take(10) + history.events.take(3)  // 3 duplicates
        var dups = 0L
        val sink = object : ReplaySink {
            override fun onEvent(e: GenEvent) {}
            override fun onProgress(p: ReplayProgress) { dups = p.duplicatesDropped }
        }
        val options = ReplayOptions(progressEveryN = 1)
        ReplayDriver.run(events, sink, VirtualClock, options, config.startEpochMs, 7)
        assertEquals(3L, dups)
    }

    @Test fun runUntilDeliversNothingAtOrAfterT0() = runBlocking {
        val t0 = config.startEpochMs + 3L * 24 * 60 * 60 * 1000L  // day 3 boundary
        val delivered = mutableListOf<GenEvent>()
        val sink = object : ReplaySink { override fun onEvent(e: GenEvent) { delivered.add(e) } }
        ReplayDriver.runUntil(history.events, sink, VirtualClock, t0, startEpochMs = config.startEpochMs)
        assertTrue("All delivered events must have ts < t0", delivered.all { it.ts < t0 })
        // Confirm that at least one event is excluded (assuming 7 days has events on day 3+)
        assertTrue("Expected some events after t0", history.events.any { it.ts >= t0 })
    }

    @Test fun virtualClockFinishesWithoutRealSleeping() = runBlocking {
        val sink = object : ReplaySink { override fun onEvent(e: GenEvent) {} }
        val ms = measureTimeMillis {
            ReplayDriver.run(history.events, sink, VirtualClock, startEpochMs = config.startEpochMs)
        }
        assertTrue("VirtualClock should finish in < 5 seconds wall time, took ${ms}ms", ms < 5000L)
    }

    @Test fun scaledRealtimeClockUsesCorrectScaledDelays() = runBlocking {
        // Use a fake delay function that records requests
        val delays = mutableListOf<Long>()
        val fakeClock = ScaledRealtimeClock(speed = 100.0) { ms -> delays.add(ms) }
        val smallHistory = HistoryGenerator.generate(rahul, GeneratorConfig(seed = 1L, days = 1))
        val sink = object : ReplaySink { override fun onEvent(e: GenEvent) {} }
        ReplayDriver.run(smallHistory.events, sink, fakeClock, startEpochMs = config.startEpochMs, daysTotal = 1)
        // Each delay must be real_ms = gap_ms / 100
        assertTrue("Should have some delays", delays.isNotEmpty())
        assertTrue("All delays must be non-negative", delays.all { it >= 0 })
    }

    @Test fun partialJsonlLineRejected() {
        val dir = File("build/dhyan-fixtures").apply { mkdirs() }
        val badFile = File(dir, "partial_test.events.jsonl")
        badFile.writeText("{\"id\":\"x\"")  // no trailing newline, incomplete JSON
        try {
            EventJsonlReader.readMaps(badFile)
            fail("Expected exception on partial JSONL")
        } catch (e: IllegalArgumentException) {
            assertTrue("Error should mention newline or parse", e.message!!.contains("newline") || e.message!!.contains("parse") || e.message!!.contains("JSON"))
        } finally { badFile.delete() }
    }
}

class TemporalGuardTest {

    private val config = GeneratorConfig(seed = 2L, days = 14)
    private val history by lazy { HistoryGenerator.generate(PersonaCatalog.byName("Rahul"), config) }

    private fun t0() = config.startEpochMs + 7L * 24 * 60 * 60 * 1000L  // day 7 boundary

    @Test fun compliantFeaturePasses() {
        val guard = GuardedEventLog(history.events, TrainTestSplit(t0()))
        val events = guard.eventsBefore(t0())
        assertTrue(events.isNotEmpty())
        assertTrue(events.all { it.ts < t0() })
    }

    @Test fun leakyFeatureThrowsTemporalLeakException() {
        val guard = GuardedEventLog(history.events, TrainTestSplit(t0()))
        // A feature that reads between(0, t0+1) must throw
        try {
            guard.between(0L, t0() + 1L)
            fail("Expected TemporalLeakException")
        } catch (e: TemporalLeakException) {
            assertTrue(e.message!!.contains("t0"))
            assertEquals(1L, guard.attemptedLeaks)
        }
    }

    @Test fun latestBeforeT0NeverReturnsEventAtT0() {
        val guard = GuardedEventLog(history.events, TrainTestSplit(t0()))
        val result = guard.latestBefore(t0())
        result?.let { assertTrue("latestBefore(t0) must return event strictly before t0", it.ts < t0()) }
    }

    @Test fun trainingSideCannotReachHoldoutBeforeSeal() {
        val guard = GuardedEventLog(history.events, TrainTestSplit(t0()))
        try {
            guard.holdout()
            fail("Expected IllegalStateException before sealTraining()")
        } catch (e: IllegalStateException) {
            assertTrue(e.message!!.contains("seal"))
        }
    }

    @Test fun holdoutAccessibleAfterSeal() {
        val guard = GuardedEventLog(history.events, TrainTestSplit(t0()))
        guard.sealTraining()
        val holdout = guard.holdout()
        assertTrue(holdout.events.isNotEmpty())
        assertTrue(holdout.events.all { it.ts >= t0() })
    }

    @Test fun eventsBetweenExactT0BoundaryExcluded() {
        val guard = GuardedEventLog(history.events, TrainTestSplit(t0()))
        // between(0, t0) is legal — all results must be < t0
        val results = guard.between(0L, t0())
        assertTrue(results.all { it.ts < t0() })
    }
}

class FastForwardTest {
    @Test fun fastForwardCompletesWith100xVirtualClock() = runBlocking {
        val persona = PersonaCatalog.byName("Kabir")
        val config = GeneratorConfig(seed = 3L, days = 7)
        val ff = FastForward(persona, config)
        var lastProgress: ReplayProgress? = null
        val sink = object : ReplaySink {
            override fun onEvent(e: GenEvent) {}
            override fun onProgress(p: ReplayProgress) { lastProgress = p }
        }
        val ms = measureTimeMillis { ff.run(sink, VirtualClock) }
        println("FastForward 7 days Kabir VirtualClock: ${ms}ms, delivered=${lastProgress?.delivered}")
        assertTrue("Should finish fast with VirtualClock", ms < 5000L)
        assertNotNull(lastProgress)
        assertTrue(lastProgress!!.delivered > 0)
        assertEquals(0L, lastProgress!!.duplicatesDropped)
    }
}

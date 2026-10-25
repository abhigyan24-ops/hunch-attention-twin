package com.dhyan.app.personas.replay

import com.dhyan.app.personas.world.GenEvent

/**
 * 4A-5: TemporalGuard. Enforces train-test split.
 *
 * GuardedEventLog: the ONLY way training-side code may read history before sealTraining().
 * Any request whose upper bound exceeds t0, or any returned event with tsAdj >= t0,
 * throws TemporalLeakException. Never leaks event content in the exception.
 *
 * HoldoutAccessor: available only after sealTraining() is called.
 * Accessing it before sealing throws IllegalStateException.
 */
class TemporalLeakException(message: String) : RuntimeException(message)

class TrainTestSplit(val t0: Long)

class GuardedEventLog(
    private val events: List<GenEvent>,
    private val split: TrainTestSplit,
    private val clockOffsetMs: Map<String, Long> = mapOf("phone" to 0L, "laptop" to 0L)
) {
    private var sealed = false
    var attemptedLeaks = 0L; private set

    private fun tsAdj(e: GenEvent): Long = e.ts + (clockOffsetMs[e.device] ?: 0L)
    private fun requireBound(upperBound: Long) {
        if (upperBound > split.t0) {
            attemptedLeaks++
            throw TemporalLeakException("Training-side read attempted with upper bound $upperBound >= t0=${split.t0}")
        }
    }

    /** All events with tsAdj strictly in [0, upperBound). */
    fun eventsBefore(upperBound: Long): List<GenEvent> {
        requireBound(upperBound)
        return events.filter { tsAdj(it) < upperBound }.also { results ->
            results.forEach { e -> if (tsAdj(e) >= split.t0) { attemptedLeaks++; throw TemporalLeakException("Returned event tsAdj=${tsAdj(e)} >= t0=${split.t0}") } }
        }
    }

    /** Events in [from, to). */
    fun between(from: Long, to: Long): List<GenEvent> {
        requireBound(to)
        return events.filter { tsAdj(it) in from until to }.also { results ->
            results.forEach { e -> if (tsAdj(e) >= split.t0) { attemptedLeaks++; throw TemporalLeakException("Returned event tsAdj=${tsAdj(e)} >= t0=${split.t0}") } }
        }
    }

    /** Latest event with tsAdj < upperBound. Never returns an event at or after t0. */
    fun latestBefore(upperBound: Long): GenEvent? {
        requireBound(upperBound)
        val result = events.filter { tsAdj(it) < upperBound }.maxByOrNull { tsAdj(it) }
        result?.let { if (tsAdj(it) >= split.t0) { attemptedLeaks++; throw TemporalLeakException("latestBefore returned event at tsAdj=${tsAdj(it)} >= t0=${split.t0}") } }
        return result
    }

    /** Count of events with tsAdj < upperBound. */
    fun countBefore(upperBound: Long): Int {
        requireBound(upperBound)
        return events.count { tsAdj(it) < upperBound }
    }

    /** Seal the training side. After this, HoldoutAccessor becomes available. */
    fun sealTraining() { sealed = true }

    /** Access holdout events (tsAdj >= t0). Only available after sealTraining(). */
    fun holdout(): HoldoutAccessor {
        check(sealed) { "Cannot access holdout before sealTraining() is called" }
        return HoldoutAccessor(events.filter { tsAdj(it) >= split.t0 }, split)
    }
}

class HoldoutAccessor internal constructor(
    val events: List<GenEvent>,
    val split: TrainTestSplit
)

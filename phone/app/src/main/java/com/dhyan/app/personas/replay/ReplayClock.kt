package com.dhyan.app.personas.replay

import kotlinx.coroutines.delay

/**
 * 4A-5: Clocks for replay.
 * VirtualClock: advances instantly (no real delay). Used in tests.
 * ScaledRealtimeClock: suspends for (gap / speed) real milliseconds.
 * The delay function is injected so tests can substitute a fake.
 */
interface ReplayClock {
    /** Suspend for the equivalent of [gapMs] simulated milliseconds. */
    suspend fun waitGap(gapMs: Long)
}

/** No real delay. All events delivered instantly. */
object VirtualClock : ReplayClock {
    override suspend fun waitGap(gapMs: Long) { /* instant */ }
}

/** Scales simulated time gaps to real time gaps. Default 100x playback speed. */
class ScaledRealtimeClock(
    val speed: Double = 100.0,
    private val delayFn: suspend (Long) -> Unit = { ms -> delay(ms) }
) : ReplayClock {
    init { require(speed > 0) }
    override suspend fun waitGap(gapMs: Long) {
        val realMs = (gapMs / speed).toLong()
        if (realMs > 0) delayFn(realMs)
    }
}

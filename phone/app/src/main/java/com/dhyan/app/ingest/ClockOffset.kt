package com.dhyan.app.ingest

import java.util.concurrent.atomic.AtomicLong

/**
 * Stores and applies the clock offset between phone and relay server.
 *
 * Computed from a WebSocket ping/pong round-trip:
 *   offset = (t_pong - t_ping) / 2 - (server_ts - t_ping)
 *
 * All events emitted by the phone use [adjustedNow] to synchronise with laptop events.
 *
 * Privacy: no event content involved — pure timing.
 */
object ClockOffset {

    private val offsetMs = AtomicLong(0L)

    /** Update the offset from a ping/pong exchange. */
    fun update(phonePingMs: Long, serverTs: Long, phonePongMs: Long) {
        val rtt = phonePongMs - phonePingMs
        val oneWay = rtt / 2
        val computed = serverTs - (phonePingMs + oneWay)
        offsetMs.set(computed)
    }

    /** Current estimated offset (phone_ts + offset ≈ server_ts). */
    fun currentOffsetMs(): Long = offsetMs.get()

    /** Returns the current phone time adjusted for relay clock skew. */
    fun adjustedNow(): Long = System.currentTimeMillis() + offsetMs.get()
}

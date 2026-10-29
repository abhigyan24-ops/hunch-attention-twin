package com.dhyan.app.ingest

/**
 * Stores and applies the clock offset between phone and laptop peer
 * per Contract Section 7.1.
 *
 * Algorithm:
 * Every 30 s each side sends ping {t0}. Peer answers pong {t0, t1, t2}.
 * The requester records t3 on receipt and computes:
 *   rtt    = (t3 - t0) - (t2 - t1)
 *   offset = ((t1 - t0) + (t2 - t3)) / 2  # peer clock minus my clock
 *
 * Keep the last 8 samples, ignore samples with rtt > 2000 ms,
 * use the sample with the smallest rtt.
 * If there is no valid sample, offset = 0 and cross-device timing is marked estimated.
 *
 * For the phone receiving laptop events:
 *   tsAdj = ts - offset (or ts + offsetToPhone)
 */
object ClockOffset {

    data class Sample(
        val rtt: Long,
        val offset: Long,
        val ts: Long
    )

    private val samples = mutableListOf<Sample>()
    private val lock = Any()

    /**
     * Records a pong measurement from a ping/pong exchange.
     * @param t0 Phone's send timestamp
     * @param t1 Peer's receive timestamp
     * @param t2 Peer's reply timestamp
     * @param t3 Phone's receive timestamp
     */
    fun recordSample(t0: Long, t1: Long, t2: Long, t3: Long) {
        val rtt = (t3 - t0) - (t2 - t1)
        if (rtt < 0 || rtt > 2000) return

        val offset = ((t1 - t0) + (t2 - t3)) / 2

        synchronized(lock) {
            samples.add(Sample(rtt = rtt, offset = offset, ts = t3))
            if (samples.size > 8) {
                samples.removeAt(0)
            }
        }
    }

    /** Legacy single update method for backward compatibility. */
    fun update(phonePingMs: Long, serverTs: Long, phonePongMs: Long) {
        recordSample(phonePingMs, serverTs, serverTs, phonePongMs)
    }

    /**
     * Returns the peer-minus-phone clock offset (ms) using the sample with the smallest RTT.
     * Returns 0 if no valid sample exists within the window.
     */
    fun currentOffsetMs(): Long = synchronized(lock) {
        samples.minByOrNull { it.rtt }?.offset ?: 0L
    }

    /**
     * Returns true if no valid clock synchronization samples exist.
     */
    fun isEstimated(): Boolean = synchronized(lock) {
        samples.isEmpty()
    }

    /**
     * Returns the offset to add to incoming laptop timestamps to map to phone reference time:
     * tsAdj = laptopTs - peerMinusPhoneOffset
     */
    fun offsetToPhone(): Long = -currentOffsetMs()

    /**
     * Adjusts an incoming peer timestamp into phone local time.
     */
    fun adjustIncomingTs(peerTs: Long): Long = peerTs + offsetToPhone()

    /** Returns the current phone time adjusted for clock skew. */
    fun adjustedNow(): Long = System.currentTimeMillis()

    /** Clears all recorded samples (useful for testing or unpairing). */
    fun reset() = synchronized(lock) {
        samples.clear()
    }
}

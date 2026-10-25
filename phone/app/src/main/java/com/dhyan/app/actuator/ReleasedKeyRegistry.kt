package com.dhyan.app.actuator

/**
 * 24-hour loop guard: tracks recently released keyHashes to prevent
 * re-actuation of the same notification within the guard window.
 */
class ReleasedKeyRegistry(private val clock: Clock = SystemClock) {

    private data class Entry(val keyHash: String, val releasedAtMs: Long)
    private val entries = ArrayDeque<Entry>()

    @Synchronized
    fun register(keyHash: String) {
        entries.addLast(Entry(keyHash, clock.nowMs()))
        purgeExpired()
    }

    @Synchronized
    fun isRecent(keyHash: String): Boolean {
        purgeExpired()
        return entries.any { it.keyHash == keyHash }
    }

    @Synchronized
    fun clear() = entries.clear()

    private fun purgeExpired() {
        val cutoff = clock.nowMs() - ActuatorConfig.LOOP_GUARD_WINDOW_MS
        while (entries.isNotEmpty() && entries.first().releasedAtMs < cutoff) {
            entries.removeFirst()
        }
    }
}

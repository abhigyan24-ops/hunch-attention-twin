package com.dhyan.app.personas.replay

import com.dhyan.app.personas.world.GenEvent

/**
 * 4A-5: Replay sink interface.
 * Receives events and optional progress callbacks.
 */
data class ReplayProgress(
    val fraction: Double,          // 0.0 .. 1.0
    val simulatedDay: Int,
    val delivered: Long,
    val duplicatesDropped: Long,
    val lateEvents: Long
)

interface ReplaySink {
    fun onEvent(e: GenEvent)
    fun onProgress(p: ReplayProgress) {}   // default no-op
}

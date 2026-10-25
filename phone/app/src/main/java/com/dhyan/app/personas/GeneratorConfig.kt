package com.dhyan.app.personas

enum class World { MATCHED, MILD_MISSPEC }
sealed interface LoggingPolicy {
    data object ShowAll : LoggingPolicy
    data class RandomizedHold(val eps: Double, val maxImportance: Double) : LoggingPolicy {
        init { require(eps.isFinite() && eps in 0.0..1.0); require(maxImportance.isFinite() && maxImportance in 0.0..1.0) }
    }
}
class GeneratorConfig(
    val seed: Long = 1L,
    // 2026-01-05 00:00 Asia/Kolkata; invariant across host timezones.
    val startEpochMs: Long = 1767551400000L,
    val days: Int = 14,
    val windowMin: Double = 30.0,
    val cGlanceMin: Double = 0.4,
    val truncateCostsAtWindow: Boolean = false,
    val world: World = World.MATCHED,
    val loggingPolicy: LoggingPolicy = LoggingPolicy.ShowAll,
    deviceClockSkewMs: Map<String, Long> = mapOf("phone" to 0L, "laptop" to 0L)
) {
    val deviceClockSkewMs = frozen(deviceClockSkewMs)
    init {
        require(startEpochMs >= 0 && days in 1..3660)
        require(windowMin.isFinite() && windowMin > 0)
        require(cGlanceMin.isFinite() && cGlanceMin >= 0)
        require(deviceClockSkewMs.keys == setOf("phone", "laptop"))
    }
}

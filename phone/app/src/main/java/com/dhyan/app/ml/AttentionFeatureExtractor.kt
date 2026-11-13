package com.dhyan.app.ml

import kotlin.math.cos
import kotlin.math.sin

/**
 * Raw context telemetry fed into the feature extractor.
 */
data class RawAttentionContext(
    val hourOfDay: Int = 12,
    val minuteOfHour: Int = 0,
    val sessionElapsedMins: Double = 0.0,
    val lastNightSleepMins: Double = 480.0,
    val unlocksLast15m: Int = 0,
    val appSwitchesLast15m: Int = 0,
    val notificationsLast15m: Int = 0,
    val isThrashing: Boolean = false,
    val minsSinceLastBreak: Double = 0.0,
    val socialScrollSecsLast30m: Double = 0.0
)

/**
 * Deterministic 12-dimensional feature extractor mapping telemetry into [0.0, 1.0]^12.
 */
object AttentionFeatureExtractor {
    const val NUM_FEATURES = 12

    fun extractFeatures(ctx: RawAttentionContext): DoubleArray {
        val features = DoubleArray(NUM_FEATURES)

        // Continuous hour in [0.0, 24.0)
        val contHour = (ctx.hourOfDay % 24) + (ctx.minuteOfHour.coerceIn(0, 59) / 60.0)
        val hourRad = 2.0 * Math.PI * contHour / 24.0

        // x[0]: Bias
        features[0] = 1.0

        // x[1]: Cyclical hour sine [0.0, 1.0]
        features[1] = ((sin(hourRad) + 1.0) / 2.0).coerceIn(0.0, 1.0)

        // x[2]: Cyclical hour cosine [0.0, 1.0]
        features[2] = ((cos(hourRad) + 1.0) / 2.0).coerceIn(0.0, 1.0)

        // x[3]: Session elapsed duration [0.0, 1.0]
        features[3] = (ctx.sessionElapsedMins.coerceAtLeast(0.0) / 90.0).coerceIn(0.0, 1.0)

        // x[4]: Circadian sleep debt [0.0, 1.0]
        val sleepDebt = (480.0 - ctx.lastNightSleepMins.coerceAtLeast(0.0)).coerceAtLeast(0.0)
        features[4] = (sleepDebt / 240.0).coerceIn(0.0, 1.0)

        // x[5]: Rolling 15m phone unlock velocity [0.0, 1.0]
        features[5] = (ctx.unlocksLast15m.coerceAtLeast(0).toDouble() / 8.0).coerceIn(0.0, 1.0)

        // x[6]: Rolling 15m app/window switch velocity [0.0, 1.0]
        features[6] = (ctx.appSwitchesLast15m.coerceAtLeast(0).toDouble() / 20.0).coerceIn(0.0, 1.0)

        // x[7]: Rolling 15m notification pressure [0.0, 1.0]
        features[7] = (ctx.notificationsLast15m.coerceAtLeast(0).toDouble() / 15.0).coerceIn(0.0, 1.0)

        // x[8]: Cognitive thrash indicator [0.0, 1.0]
        features[8] = if (ctx.isThrashing) 1.0 else 0.0

        // x[9]: Cumulative work fatigue [0.0, 1.0]
        features[9] = (ctx.minsSinceLastBreak.coerceAtLeast(0.0) / 180.0).coerceIn(0.0, 1.0)

        // x[10]: Recent high-dopamine feed exposure [0.0, 1.0]
        features[10] = (ctx.socialScrollSecsLast30m.coerceAtLeast(0.0) / 600.0).coerceIn(0.0, 1.0)

        // x[11]: Fatigue x Sleep interaction [0.0, 1.0]
        features[11] = (features[3] * features[4]).coerceIn(0.0, 1.0)

        return features
    }
}

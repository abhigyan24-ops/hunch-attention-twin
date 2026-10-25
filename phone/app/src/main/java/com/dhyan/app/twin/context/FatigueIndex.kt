package com.dhyan.app.twin.context

import kotlin.math.max
import kotlin.math.min

/**
 * Fatigue index computation and band classification (spec 8.3).
 *
 * f = clip(0.5 * (hoursWorked / 6) + 0.3 * switchRateZ + 0.2 * stallRatio, 0, 1)
 *
 * Bands:
 *   low  → f < 0.33
 *   med  → f in [0.33, 0.66)
 *   high → f >= 0.66
 *
 * [switchRateZ] is the z-score of the current switch rate relative to the user's baseline;
 * if unavailable, use 0.0 (neutral).
 * [stallRatio] is the fraction of recent time in a stalled/idle work state; if unavailable, use 0.0.
 */
object FatigueIndex {

    /** Computes the raw fatigue index value in [0, 1]. */
    fun compute(hoursWorked: Double, switchRateZ: Double = 0.0, stallRatio: Double = 0.0): Double {
        val raw = 0.5 * (hoursWorked / 6.0) + 0.3 * switchRateZ + 0.2 * stallRatio
        return max(0.0, min(1.0, raw))
    }

    /** Returns the fatigue band string for a given index value. */
    fun band(f: Double): String = when {
        f < 0.33 -> "low"
        f < 0.66 -> "med"
        else     -> "high"
    }

    /** Computes and classifies in one call. */
    fun bandFor(hoursWorked: Double, switchRateZ: Double = 0.0, stallRatio: Double = 0.0): String =
        band(compute(hoursWorked, switchRateZ, stallRatio))
}

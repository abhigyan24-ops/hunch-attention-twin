package com.dhyan.app.util

import java.util.Calendar

/**
 * 4:00 AM logical circadian day boundary utility.
 * Guarantees that late-night coding/focus sessions before 4:00 AM count toward the current logical day,
 * and ensures non-negative durations despite clock skew.
 */
object LogicalDayUtil {

    const val DEFAULT_ROLLOVER_HOUR = 4

    fun getLogicalDayKey(
        timestampMs: Long = System.currentTimeMillis(),
        rolloverHour: Int = DEFAULT_ROLLOVER_HOUR
    ): String {
        val cal = Calendar.getInstance().apply { timeInMillis = timestampMs }
        if (cal.get(Calendar.HOUR_OF_DAY) < rolloverHour) {
            cal.add(Calendar.DAY_OF_YEAR, -1)
        }
        val year = cal.get(Calendar.YEAR)
        val month = cal.get(Calendar.MONTH) + 1
        val day = cal.get(Calendar.DAY_OF_MONTH)
        return String.format("%04d-%02d-%02d", year, month, day)
    }

    fun clampNonNegativeDuration(durationMs: Long): Long {
        return maxOf(0L, durationMs)
    }
}

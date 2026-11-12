package com.dhyan.app.wellbeing

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.os.Process
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import kotlin.math.roundToInt

/**
 * Immutable representation of the user's bedtime & wake-time window.
 */
data class BedtimeSchedule(
    val bedtimeHour: Int,
    val bedtimeMinute: Int,
    val wakeHour: Int,
    val wakeMinute: Int,
    val isAutoDetected: Boolean,
    val isFallbackDefault: Boolean
) {
    init {
        require(bedtimeHour in 0..23) { "bedtimeHour must be 0..23, was $bedtimeHour" }
        require(bedtimeMinute in 0..59) { "bedtimeMinute must be 0..59, was $bedtimeMinute" }
        require(wakeHour in 0..23) { "wakeHour must be 0..23, was $wakeHour" }
        require(wakeMinute in 0..59) { "wakeMinute must be 0..59, was $wakeMinute" }
    }

    /** Formats bedtime in 12-hour clock, e.g. "11:45 PM". */
    fun formattedBedtime(): String = format12Hour(bedtimeHour, bedtimeMinute)

    /** Formats wake time in 12-hour clock, e.g. "7:15 AM". */
    fun formattedWakeTime(): String = format12Hour(wakeHour, wakeMinute)

    /** Duration of the sleep window in minutes, handling overnight wrap-around. */
    fun durationMinutes(): Int {
        val bedTotal = bedtimeHour * 60 + bedtimeMinute
        val wakeTotal = wakeHour * 60 + wakeMinute
        return ((wakeTotal - bedTotal) + MINUTES_PER_DAY) % MINUTES_PER_DAY
    }

    /**
     * Returns true when [nowMillis] falls inside `[bedtime, wakeTime)` in the system default timezone,
     * properly handling schedules that cross midnight.
     */
    fun isCurrentlyBedtime(nowMillis: Long): Boolean =
        isCurrentlyBedtime(nowMillis, TimeZone.getDefault())

    internal fun isCurrentlyBedtime(nowMillis: Long, timeZone: TimeZone): Boolean {
        val bedTotal = bedtimeHour * 60 + bedtimeMinute
        val wakeTotal = wakeHour * 60 + wakeMinute
        if (bedTotal == wakeTotal) return false

        val cal = Calendar.getInstance(timeZone).apply { timeInMillis = nowMillis }
        val nowTotal = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)

        return if (bedTotal < wakeTotal) {
            nowTotal in bedTotal until wakeTotal
        } else {
            nowTotal >= bedTotal || nowTotal < wakeTotal
        }
    }

    companion object {
        private const val MINUTES_PER_DAY = 24 * 60

        fun defaultFallback(): BedtimeSchedule = BedtimeSchedule(
            bedtimeHour = 23,
            bedtimeMinute = 30,
            wakeHour = 7,
            wakeMinute = 0,
            isAutoDetected = true,
            isFallbackDefault = true
        )

        private fun format12Hour(hour24: Int, minute: Int): String {
            val isPm = hour24 >= 12
            val hour12 = when (val h = hour24 % 12) {
                0 -> 12
                else -> h
            }
            val amPm = if (isPm) "PM" else "AM"
            return String.format(Locale.US, "%d:%02d %s", hour12, minute, amPm)
        }
    }
}

/**
 * Infers default bedtime & wake-time from Digital Wellbeing ([UsageStatsManager]) interactive
 * history and manages user manual overrides in [SharedPreferences].
 */
class BedtimeManager(private val context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Returns true if the user has granted Usage Access ([AppOpsManager.OPSTR_GET_USAGE_STATS]).
     */
    fun hasUsageStatsPermission(): Boolean {
        return try {
            val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager
                ?: return false
            val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                appOps.unsafeCheckOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    Process.myUid(),
                    context.packageName
                )
            } else {
                @Suppress("DEPRECATION")
                appOps.checkOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    Process.myUid(),
                    context.packageName
                )
            }
            mode == AppOpsManager.MODE_ALLOWED
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Queries the last 7 days of [UsageStatsManager] events and infers median sleep & wake times.
     * Returns `null` cleanly if Usage Access is not granted or fewer than 1 night of history exists.
     */
    fun inferBedtimeFromUsageStats(): BedtimeSchedule? {
        if (!hasUsageStatsPermission()) return null

        return try {
            val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
                ?: return null
            val now = System.currentTimeMillis()
            val sevenDaysAgo = now - LOOKBACK_WINDOW_MS
            val usageEvents = usm.queryEvents(sevenDaysAgo, now) ?: return null

            val timestamps = ArrayList<Long>()
            val event = UsageEvents.Event()
            while (usageEvents.hasNextEvent()) {
                usageEvents.getNextEvent(event)
                if (isInteractiveEvent(event.eventType)) {
                    timestamps.add(event.timeStamp)
                }
            }
            calculateScheduleFromTimestamps(timestamps)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Returns the active [BedtimeSchedule].
     * - When `auto_bedtime_enabled` is true (default), runs [inferBedtimeFromUsageStats], caches any
     *   inferred schedule in [SharedPreferences], and falls back to `23:30 – 07:00` if unavailable.
     * - When manually overridden, returns the stored custom schedule (`isAutoDetected = false`).
     */
    fun getBedtimeSchedule(): BedtimeSchedule {
        val autoEnabled = prefs.getBoolean(KEY_AUTO_ENABLED, true)
        if (!autoEnabled) {
            val bedH = prefs.getInt(KEY_BEDTIME_HOUR, DEFAULT_BED_HOUR).coerceIn(0, 23)
            val bedM = prefs.getInt(KEY_BEDTIME_MINUTE, DEFAULT_BED_MINUTE).coerceIn(0, 59)
            val wakeH = prefs.getInt(KEY_WAKE_HOUR, DEFAULT_WAKE_HOUR).coerceIn(0, 23)
            val wakeM = prefs.getInt(KEY_WAKE_MINUTE, DEFAULT_WAKE_MINUTE).coerceIn(0, 59)
            return BedtimeSchedule(
                bedtimeHour = bedH,
                bedtimeMinute = bedM,
                wakeHour = wakeH,
                wakeMinute = wakeM,
                isAutoDetected = false,
                isFallbackDefault = false
            )
        }

        val inferred = inferBedtimeFromUsageStats()
        if (inferred != null) {
            prefs.edit()
                .putBoolean(KEY_AUTO_ENABLED, true)
                .putInt(KEY_BEDTIME_HOUR, inferred.bedtimeHour)
                .putInt(KEY_BEDTIME_MINUTE, inferred.bedtimeMinute)
                .putInt(KEY_WAKE_HOUR, inferred.wakeHour)
                .putInt(KEY_WAKE_MINUTE, inferred.wakeMinute)
                .apply()
            return inferred
        }

        return BedtimeSchedule.defaultFallback()
    }

    /**
     * Saves a manual user schedule and disables automatic Digital Wellbeing override.
     */
    fun setManualBedtime(bedHour: Int, bedMinute: Int, wakeHour: Int, wakeMinute: Int): BedtimeSchedule {
        val bH = bedHour.coerceIn(0, 23)
        val bM = bedMinute.coerceIn(0, 59)
        val wH = wakeHour.coerceIn(0, 23)
        val wM = wakeMinute.coerceIn(0, 59)

        prefs.edit()
            .putBoolean(KEY_AUTO_ENABLED, false)
            .putInt(KEY_BEDTIME_HOUR, bH)
            .putInt(KEY_BEDTIME_MINUTE, bM)
            .putInt(KEY_WAKE_HOUR, wH)
            .putInt(KEY_WAKE_MINUTE, wM)
            .apply()

        return BedtimeSchedule(
            bedtimeHour = bH,
            bedtimeMinute = bM,
            wakeHour = wH,
            wakeMinute = wM,
            isAutoDetected = false,
            isFallbackDefault = false
        )
    }

    /**
     * Re-enables Digital Wellbeing sync and immediately recomputes the schedule.
     */
    fun syncWithDigitalWellbeing(): BedtimeSchedule {
        prefs.edit()
            .putBoolean(KEY_AUTO_ENABLED, true)
            .apply()
        return getBedtimeSchedule()
    }

    private fun isInteractiveEvent(eventType: Int): Boolean {
        if (eventType == UsageEvents.Event.USER_INTERACTION) return true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
            eventType == UsageEvents.Event.SCREEN_INTERACTIVE
        ) {
            return true
        }
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            eventType == UsageEvents.Event.ACTIVITY_RESUMED
        } else {
            @Suppress("DEPRECATION")
            eventType == UsageEvents.Event.MOVE_TO_FOREGROUND
        }
    }

    companion object {
        private const val PREFS_NAME = "dhyan_wellbeing_prefs"
        private const val KEY_AUTO_ENABLED = "auto_bedtime_enabled"
        private const val KEY_BEDTIME_HOUR = "bedtime_hour"
        private const val KEY_BEDTIME_MINUTE = "bedtime_minute"
        private const val KEY_WAKE_HOUR = "wake_hour"
        private const val KEY_WAKE_MINUTE = "wake_minute"

        private const val DEFAULT_BED_HOUR = 23
        private const val DEFAULT_BED_MINUTE = 30
        private const val DEFAULT_WAKE_HOUR = 7
        private const val DEFAULT_WAKE_MINUTE = 0

        private const val LOOKBACK_WINDOW_MS = 7L * 24L * 60L * 60L * 1000L
        private const val MIN_SLEEP_GAP_MINUTES = 240L
        private const val MAX_SLEEP_GAP_MINUTES = 720L
        private const val ANCHOR_EVENING_MINUTES = 18 * 60
        private const val MINUTES_PER_DAY = 24 * 60

        /**
         * Pure deterministic schedule inference from interactive event timestamps.
         * Returns `null` when fewer than 1 qualifying overnight inactivity gap is found.
         */
        internal fun calculateScheduleFromTimestamps(
            timestamps: List<Long>,
            timeZone: TimeZone = TimeZone.getDefault()
        ): BedtimeSchedule? {
            if (timestamps.size < 2) return null

            val sorted = timestamps.sorted()
            val sleepOffsetsSince18 = ArrayList<Int>()
            val wakeMinutesOfDay = ArrayList<Int>()

            val calLast = Calendar.getInstance(timeZone)
            val calNext = Calendar.getInstance(timeZone)

            for (i in 0 until sorted.lastIndex) {
                val lastActive = sorted[i]
                val nextActive = sorted[i + 1]
                val gapMinutes = (nextActive - lastActive) / 60_000L

                if (gapMinutes !in MIN_SLEEP_GAP_MINUTES..MAX_SLEEP_GAP_MINUTES) continue

                calLast.timeInMillis = lastActive
                calNext.timeInMillis = nextActive

                val lastMinOfDay =
                    calLast.get(Calendar.HOUR_OF_DAY) * 60 + calLast.get(Calendar.MINUTE)
                val nextMinOfDay =
                    calNext.get(Calendar.HOUR_OF_DAY) * 60 + calNext.get(Calendar.MINUTE)

                // lastActive local hour between 20:00 and 04:00 (inclusive)
                val isNightStart = lastMinOfDay >= 20 * 60 || lastMinOfDay <= 4 * 60
                // nextActive local hour between 04:00 and 11:00 (inclusive)
                val isMorningWake = nextMinOfDay in (4 * 60)..(11 * 60)

                if (!isNightStart || !isMorningWake) continue

                // Normalize sleep start as minutes elapsed since 18:00 (6:00 PM)
                val elapsedSince18 =
                    ((lastMinOfDay - ANCHOR_EVENING_MINUTES) + MINUTES_PER_DAY) % MINUTES_PER_DAY
                sleepOffsetsSince18.add(elapsedSince18)
                wakeMinutesOfDay.add(nextMinOfDay)
            }

            if (sleepOffsetsSince18.isEmpty()) return null

            val medianSleepOffset = median(sleepOffsetsSince18)
            val medianWakeMin = median(wakeMinutesOfDay)

            val roundedSleepOffset = ((medianSleepOffset / 15.0).roundToInt() * 15) % MINUTES_PER_DAY
            val roundedWakeMin = ((medianWakeMin / 15.0).roundToInt() * 15) % MINUTES_PER_DAY

            val bedtimeTotalMin = (ANCHOR_EVENING_MINUTES + roundedSleepOffset) % MINUTES_PER_DAY

            return BedtimeSchedule(
                bedtimeHour = bedtimeTotalMin / 60,
                bedtimeMinute = bedtimeTotalMin % 60,
                wakeHour = roundedWakeMin / 60,
                wakeMinute = roundedWakeMin % 60,
                isAutoDetected = true,
                isFallbackDefault = false
            )
        }

        private fun median(values: List<Int>): Double {
            val sorted = values.sorted()
            val mid = sorted.size / 2
            return if (sorted.size % 2 == 1) {
                sorted[mid].toDouble()
            } else {
                (sorted[mid - 1] + sorted[mid]) / 2.0
            }
        }
    }
}

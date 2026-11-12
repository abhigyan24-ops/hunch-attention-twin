package com.dhyan.app.wellbeing

import java.util.Calendar
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BedtimeManagerTest {

    private val utc: TimeZone = TimeZone.getTimeZone("UTC")

    private fun ts(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long {
        return Calendar.getInstance(utc).apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month)
            set(Calendar.DAY_OF_MONTH, day)
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    @Test
    fun `calculateScheduleFromTimestamps handles mixed pre and post midnight sleep times`() {
        // 3 nights of synthetic telemetry:
        // Night 1: 23:30 -> 07:00 (450m)  [sleep offset since 18:00 = 330m]
        // Night 2: 00:30 -> 07:30 (420m)  [sleep offset since 18:00 = 390m]
        // Night 3: 23:45 -> 07:15 (450m)  [sleep offset since 18:00 = 345m]
        // Daytime activity included between wake & next sleep (< 240m gaps or daytime hours)
        val timestamps = listOf(
            // Night 1
            ts(2026, Calendar.OCTOBER, 1, 21, 0),
            ts(2026, Calendar.OCTOBER, 1, 23, 30),
            ts(2026, Calendar.OCTOBER, 2, 7, 0),
            ts(2026, Calendar.OCTOBER, 2, 10, 0),
            ts(2026, Calendar.OCTOBER, 2, 14, 0),
            ts(2026, Calendar.OCTOBER, 2, 17, 30),
            ts(2026, Calendar.OCTOBER, 2, 21, 15),
            // Night 2 (post-midnight 00:30 sleep on Oct 3)
            ts(2026, Calendar.OCTOBER, 3, 0, 30),
            ts(2026, Calendar.OCTOBER, 3, 7, 30),
            ts(2026, Calendar.OCTOBER, 3, 12, 0),
            ts(2026, Calendar.OCTOBER, 3, 22, 0),
            // Night 3
            ts(2026, Calendar.OCTOBER, 3, 23, 45),
            ts(2026, Calendar.OCTOBER, 4, 7, 15)
        ).shuffled() // Verify sorting inside algorithm

        val schedule = BedtimeManager.calculateScheduleFromTimestamps(timestamps, utc)

        assertNotNull(schedule)
        schedule!!
        assertEquals(23, schedule.bedtimeHour)
        assertEquals(45, schedule.bedtimeMinute)
        assertEquals(7, schedule.wakeHour)
        assertEquals(15, schedule.wakeMinute)
        assertTrue(schedule.isAutoDetected)
        assertFalse(schedule.isFallbackDefault)
    }

    @Test
    fun `calculateScheduleFromTimestamps averages even number of pre and post midnight nights across midnight`() {
        // Night 1: 23:30 (330m since 18:00) -> 07:00 (420m)
        // Night 2: 00:30 (390m since 18:00) -> 07:30 (450m)
        // Median sleep offset = (330 + 390) / 2 = 360m since 18:00 -> 00:00
        // Median wake = (420 + 450) / 2 = 435m -> 07:15
        val timestamps = listOf(
            ts(2026, Calendar.OCTOBER, 5, 23, 30),
            ts(2026, Calendar.OCTOBER, 6, 7, 0),
            ts(2026, Calendar.OCTOBER, 6, 12, 30),
            ts(2026, Calendar.OCTOBER, 6, 17, 0),
            ts(2026, Calendar.OCTOBER, 7, 0, 30),
            ts(2026, Calendar.OCTOBER, 7, 7, 30)
        )

        val schedule = BedtimeManager.calculateScheduleFromTimestamps(timestamps, utc)

        assertNotNull(schedule)
        schedule!!
        assertEquals(0, schedule.bedtimeHour)
        assertEquals(0, schedule.bedtimeMinute)
        assertEquals(7, schedule.wakeHour)
        assertEquals(15, schedule.wakeMinute)
    }

    @Test
    fun `calculateScheduleFromTimestamps rounds median bedtime and wake time to nearest 15 minutes`() {
        // Single night: 23:53 (rounds to 00:00) -> 07:08 (rounds to 07:15)
        val night1 = listOf(
            ts(2026, Calendar.OCTOBER, 10, 23, 53),
            ts(2026, Calendar.OCTOBER, 11, 7, 8)
        )
        val roundedAcrossMidnight = BedtimeManager.calculateScheduleFromTimestamps(night1, utc)!!
        assertEquals(0, roundedAcrossMidnight.bedtimeHour)
        assertEquals(0, roundedAcrossMidnight.bedtimeMinute)
        assertEquals(7, roundedAcrossMidnight.wakeHour)
        assertEquals(15, roundedAcrossMidnight.wakeMinute)

        // Single night: 23:22 (rounds to 23:15) -> 06:52 (rounds to 06:45)
        val night2 = listOf(
            ts(2026, Calendar.OCTOBER, 12, 23, 22),
            ts(2026, Calendar.OCTOBER, 13, 6, 52)
        )
        val roundedDown = BedtimeManager.calculateScheduleFromTimestamps(night2, utc)!!
        assertEquals(23, roundedDown.bedtimeHour)
        assertEquals(15, roundedDown.bedtimeMinute)
        assertEquals(6, roundedDown.wakeHour)
        assertEquals(45, roundedDown.wakeMinute)
    }

    @Test
    fun `calculateScheduleFromTimestamps ignores daytime gaps, short naps, and marathon gaps`() {
        val nonQualifying = listOf(
            // Afternoon 5h gap (13:00 -> 18:00): valid duration, wrong local hours
            ts(2026, Calendar.OCTOBER, 1, 13, 0),
            ts(2026, Calendar.OCTOBER, 1, 18, 0),
            // Too short overnight gap (< 240m): 02:00 -> 05:30 (210m)
            ts(2026, Calendar.OCTOBER, 2, 2, 0),
            ts(2026, Calendar.OCTOBER, 2, 5, 30),
            // Too long overnight gap (> 720m): 21:00 -> 09:30 (750m)
            ts(2026, Calendar.OCTOBER, 2, 21, 0),
            ts(2026, Calendar.OCTOBER, 3, 9, 30)
        )

        assertNull(BedtimeManager.calculateScheduleFromTimestamps(nonQualifying, utc))
        assertNull(BedtimeManager.calculateScheduleFromTimestamps(emptyList(), utc))
        assertNull(BedtimeManager.calculateScheduleFromTimestamps(listOf(ts(2026, Calendar.OCTOBER, 1, 23, 0)), utc))
    }

    @Test
    fun `isCurrentlyBedtime handles overnight wrap-around pre and post midnight`() {
        val schedule = BedtimeSchedule(
            bedtimeHour = 23,
            bedtimeMinute = 30,
            wakeHour = 7,
            wakeMinute = 15,
            isAutoDetected = true,
            isFallbackDefault = false
        )

        // Pre-midnight checks
        assertFalse(schedule.isCurrentlyBedtime(ts(2026, Calendar.OCTOBER, 10, 23, 29), utc))
        assertTrue(schedule.isCurrentlyBedtime(ts(2026, Calendar.OCTOBER, 10, 23, 30), utc))
        assertTrue(schedule.isCurrentlyBedtime(ts(2026, Calendar.OCTOBER, 10, 23, 59), utc))

        // Post-midnight checks
        assertTrue(schedule.isCurrentlyBedtime(ts(2026, Calendar.OCTOBER, 11, 0, 0), utc))
        assertTrue(schedule.isCurrentlyBedtime(ts(2026, Calendar.OCTOBER, 11, 3, 45), utc))
        assertTrue(schedule.isCurrentlyBedtime(ts(2026, Calendar.OCTOBER, 11, 7, 14), utc))
        assertFalse(schedule.isCurrentlyBedtime(ts(2026, Calendar.OCTOBER, 11, 7, 15), utc))
        assertFalse(schedule.isCurrentlyBedtime(ts(2026, Calendar.OCTOBER, 11, 15, 0), utc))
    }

    @Test
    fun `isCurrentlyBedtime handles post-midnight same-day schedule`() {
        val postMidnightSchedule = BedtimeSchedule(
            bedtimeHour = 0,
            bedtimeMinute = 30,
            wakeHour = 8,
            wakeMinute = 0,
            isAutoDetected = false,
            isFallbackDefault = false
        )

        assertFalse(postMidnightSchedule.isCurrentlyBedtime(ts(2026, Calendar.OCTOBER, 10, 23, 45), utc))
        assertFalse(postMidnightSchedule.isCurrentlyBedtime(ts(2026, Calendar.OCTOBER, 11, 0, 29), utc))
        assertTrue(postMidnightSchedule.isCurrentlyBedtime(ts(2026, Calendar.OCTOBER, 11, 0, 30), utc))
        assertTrue(postMidnightSchedule.isCurrentlyBedtime(ts(2026, Calendar.OCTOBER, 11, 7, 59), utc))
        assertFalse(postMidnightSchedule.isCurrentlyBedtime(ts(2026, Calendar.OCTOBER, 11, 8, 0), utc))
    }

    @Test
    fun `formatting and duration helpers produce accurate values`() {
        val s1 = BedtimeSchedule(23, 45, 7, 15, isAutoDetected = true, isFallbackDefault = false)
        assertEquals("11:45 PM", s1.formattedBedtime())
        assertEquals("7:15 AM", s1.formattedWakeTime())
        assertEquals(450, s1.durationMinutes())

        val s2 = BedtimeSchedule(0, 0, 8, 5, isAutoDetected = false, isFallbackDefault = false)
        assertEquals("12:00 AM", s2.formattedBedtime())
        assertEquals("8:05 AM", s2.formattedWakeTime())
        assertEquals(485, s2.durationMinutes())

        val fallback = BedtimeSchedule.defaultFallback()
        assertEquals("11:30 PM", fallback.formattedBedtime())
        assertEquals("7:00 AM", fallback.formattedWakeTime())
        assertEquals(450, fallback.durationMinutes())
        assertTrue(fallback.isAutoDetected)
        assertTrue(fallback.isFallbackDefault)
    }
}

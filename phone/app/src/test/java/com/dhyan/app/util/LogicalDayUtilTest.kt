package com.dhyan.app.util

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar

class LogicalDayUtilTest {

    @Test
    fun `late night time before 4am maps to previous calendar day`() {
        val cal = Calendar.getInstance().apply {
            set(Calendar.YEAR, 2026)
            set(Calendar.MONTH, Calendar.NOVEMBER)
            set(Calendar.DAY_OF_MONTH, 13)
            set(Calendar.HOUR_OF_DAY, 1)
            set(Calendar.MINUTE, 30)
            set(Calendar.SECOND, 0)
        }

        val dayKey = LogicalDayUtil.getLogicalDayKey(cal.timeInMillis, rolloverHour = 4)
        assertEquals("2026-11-12", dayKey)
    }

    @Test
    fun `morning time after 4am maps to current calendar day`() {
        val cal = Calendar.getInstance().apply {
            set(Calendar.YEAR, 2026)
            set(Calendar.MONTH, Calendar.NOVEMBER)
            set(Calendar.DAY_OF_MONTH, 13)
            set(Calendar.HOUR_OF_DAY, 5)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
        }

        val dayKey = LogicalDayUtil.getLogicalDayKey(cal.timeInMillis, rolloverHour = 4)
        assertEquals("2026-11-13", dayKey)
    }

    @Test
    fun `duration clamp prevents negative durations from clock skew`() {
        assertEquals(0L, LogicalDayUtil.clampNonNegativeDuration(-5000L))
        assertEquals(12000L, LogicalDayUtil.clampNonNegativeDuration(12000L))
    }
}

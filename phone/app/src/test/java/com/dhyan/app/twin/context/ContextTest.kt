package com.dhyan.app.twin.context

import com.dhyan.app.twin.model.*
import org.junit.Assert.*
import org.junit.Test

class ContextTest {

    @Test
    fun hourBandBoundariesClassifiedCorrectly() {
        // Night: [0, 6)
        assertEquals(HourBand.NIGHT, HourBand.from(0))
        assertEquals(HourBand.NIGHT, HourBand.from(5))

        // Morning: [6, 12)
        assertEquals(HourBand.MORNING, HourBand.from(6))
        assertEquals(HourBand.MORNING, HourBand.from(11))

        // Afternoon: [12, 17)
        assertEquals(HourBand.AFTERNOON, HourBand.from(12))
        assertEquals(HourBand.AFTERNOON, HourBand.from(16))

        // Evening: [17, 22)
        assertEquals(HourBand.EVENING, HourBand.from(17))
        assertEquals(HourBand.EVENING, HourBand.from(21))

        // Late: [22, 24)
        assertEquals(HourBand.LATE, HourBand.from(22))
        assertEquals(HourBand.LATE, HourBand.from(23))
    }

    @Test
    fun fatigueIndexFormulaAndBands() {
        // f = clip(0.5*(hoursWorked/6) + 0.3*switchRateZ + 0.2*stallRatio, 0, 1)
        // 0 hours worked, 0 switch, 0 stall -> f = 0.0 -> low
        assertEquals(0.0, FatigueIndex.compute(0.0), 1e-9)
        assertEquals("low", FatigueIndex.bandFor(0.0))

        // 3 hours worked (0.5 * 0.5 = 0.25) -> low (< 0.33)
        assertEquals("low", FatigueIndex.bandFor(3.0))

        // 4.5 hours worked (0.5 * 4.5/6 = 0.375) -> med
        assertEquals("med", FatigueIndex.bandFor(4.5))

        // 8 hours worked (0.5 * 8/6 ≈ 0.667) -> high
        assertEquals("high", FatigueIndex.bandFor(8.0))

        // Clip to [0, 1]
        assertEquals(1.0, FatigueIndex.compute(20.0, 5.0, 5.0), 1e-9)
        assertEquals(0.0, FatigueIndex.compute(-5.0, -10.0, -5.0), 1e-9)
    }

    @Test
    fun notifClassBanding() {
        assertEquals("low", NotifClass.urgencyBand(0.1))
        assertEquals("low", NotifClass.urgencyBand(0.329))
        assertEquals("med", NotifClass.urgencyBand(0.33))
        assertEquals("med", NotifClass.urgencyBand(0.659))
        assertEquals("high", NotifClass.urgencyBand(0.66))
        assertEquals("high", NotifClass.urgencyBand(0.99))

        assertEquals("R0", NotifClass.repeatBand(0))
        assertEquals("R0", NotifClass.repeatBand(1))
        assertEquals("R2", NotifClass.repeatBand(2))
        assertEquals("R3", NotifClass.repeatBand(3))
        assertEquals("R3", NotifClass.repeatBand(7))
    }

    @Test
    fun contextBucketerGeneratesCorrectBuckets() {
        val snapshot = ContextSnapshot(
            ts = 1730000000000L,
            focusState = FocusState.DEEP_FOCUS,
            taskType = TaskType.CODING,
            taskSource = TaskSource.DECLARED,
            confidence = 0.95,
            hourBand = "evening",
            minutesInState = 52.0,
            fatigueBand = "med",
            recentInterrupts30m = 2,
            laptopState = LaptopState.FLOWING,
            phoneState = PhoneState.SCREEN_OFF,
            secondScreenOverlap = false,
            declaredSession = null
        )

        val notif = NotificationInput(
            appCat = "social",
            notifCat = "msg",
            isVip = false,
            urgencyScore = 0.15,
            repeatCount5m = 1
        )

        val bucket = ContextBucketer.bucket(snapshot, notif)
        assertEquals("coding", bucket.taskType)
        assertEquals("evening", bucket.hourBand)
        assertEquals("med", bucket.fatigueBand)
        assertEquals("social|urg=low|rep=R0|vip=0", bucket.notifClass)

        val baseline = ContextBucketer.baselineBucket(snapshot)
        assertEquals("coding", baseline.taskType)
        assertEquals("evening", baseline.hourBand)
        assertEquals("med", baseline.fatigueBand)
    }

    @Test
    fun snapshotHolderUpdatesAndEmits() {
        val holder = SnapshotHolder()
        assertNull(holder.current)

        val snapshot = ContextSnapshot(
            ts = 1000L,
            focusState = FocusState.WORKING,
            taskType = TaskType.STUDYING,
            taskSource = TaskSource.INFERRED,
            confidence = 0.7,
            hourBand = "morning",
            minutesInState = 15.0,
            fatigueBand = "low",
            recentInterrupts30m = 0,
            laptopState = LaptopState.IDLE_OPEN,
            phoneState = PhoneState.IN_APP,
            secondScreenOverlap = true,
            declaredSession = null
        )

        holder.update(snapshot)
        assertEquals(snapshot, holder.current)
        assertEquals(snapshot, holder.snapshotFlow.value)
    }
}

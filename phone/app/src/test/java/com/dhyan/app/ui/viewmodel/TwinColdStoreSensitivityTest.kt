package com.dhyan.app.ui.viewmodel

import com.dhyan.app.twin.SimOutcome
import com.dhyan.app.twin.TwinEngine
import com.dhyan.app.twin.model.ContextSnapshot
import com.dhyan.app.twin.model.FocusState
import com.dhyan.app.twin.model.LaptopState
import com.dhyan.app.twin.model.NotificationInput
import com.dhyan.app.twin.model.PhoneState
import com.dhyan.app.twin.model.TaskSource
import com.dhyan.app.twin.model.TaskType
import com.dhyan.app.twin.params.InMemoryParamStore
import com.dhyan.app.twin.params.ParamPersistence
import com.dhyan.app.twin.params.ParamRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test

class TwinColdStoreSensitivityTest {

    private class EmptyPersistence : ParamPersistence {
        override suspend fun loadAll(): List<ParamRecord> = emptyList()
        override suspend fun upsert(records: List<ParamRecord>) {}
    }

    private fun createColdEngine(): TwinEngine {
        val store = InMemoryParamStore(EmptyPersistence(), flushDispatcher = Dispatchers.Unconfined)
        runBlocking { store.load() }
        return TwinEngine(store = store, clock = { 10_000L })
    }

    private fun snapshot(
        taskType: TaskType = TaskType.CODING,
        hourBand: String = "afternoon",
        fatigueBand: String = "low",
        focusState: FocusState = FocusState.DEEP_FOCUS,
        minutesInState: Double = 45.0
    ): ContextSnapshot = ContextSnapshot(
        ts = 10_000L,
        focusState = focusState,
        taskType = taskType,
        taskSource = TaskSource.DECLARED,
        confidence = 0.85,
        hourBand = hourBand,
        minutesInState = minutesInState,
        fatigueBand = fatigueBand,
        recentInterrupts30m = 0,
        laptopState = LaptopState.FLOWING,
        phoneState = PhoneState.SCREEN_OFF,
        secondScreenOverlap = true,
        declaredSession = null
    )

    private fun notif(
        appCat: String = "social",
        notifCat: String = "post",
        isVip: Boolean = false,
        urgencyScore: Double = 0.2,
        repeatCount5m: Int = 0
    ): NotificationInput = NotificationInput(
        appCat = appCat,
        notifCat = notifCat,
        isVip = isVip,
        urgencyScore = urgencyScore,
        repeatCount5m = repeatCount5m
    )

    private fun simulateDeltaL(engine: TwinEngine, snapshot: ContextSnapshot, notif: NotificationInput): Double {
        val outcome = engine.simulate(snapshot, notif, seed = 42L)
        assertTrue("Outcome must be Ready", outcome is SimOutcome.Ready)
        return (outcome as SimOutcome.Ready).result.deltaLMean
    }

    @Test
    fun testColdStoreParameterSensitivity() {
        val engine = createColdEngine()
        val baseSnapshot = snapshot()
        val baseNotif = notif()
        val baseDeltaL = simulateDeltaL(engine, baseSnapshot, baseNotif)

        println("=== COLD STORE SENSITIVITY AUDIT ===")
        println(String.format("Baseline (social/post, fatigue=low, hour=afternoon, task=coding): deltaL = %.4f min", baseDeltaL))

        // 1. App Category and Notification Category
        val deltaLSocial = simulateDeltaL(engine, baseSnapshot, notif(appCat = "social", notifCat = "post"))
        val deltaLComm = simulateDeltaL(engine, baseSnapshot, notif(appCat = "communication", notifCat = "msg"))
        val deltaLPromo = simulateDeltaL(engine, baseSnapshot, notif(appCat = "social", notifCat = "promo"))
        val deltaLUnknown = simulateDeltaL(engine, baseSnapshot, notif(appCat = "tools", notifCat = "alert"))
        println(String.format("  appCat=social, notifCat=post:       deltaL = %.4f min", deltaLSocial))
        println(String.format("  appCat=communication, notifCat=msg: deltaL = %.4f min", deltaLComm))
        println(String.format("  notifCat=promo:                     deltaL = %.4f min", deltaLPromo))
        println(String.format("  appCat=tools (unknown):             deltaL = %.4f min", deltaLUnknown))

        // 2. Fatigue Band (low, med, high)
        val deltaLFatigueLow = simulateDeltaL(engine, snapshot(fatigueBand = "low"), baseNotif)
        val deltaLFatigueMed = simulateDeltaL(engine, snapshot(fatigueBand = "med"), baseNotif)
        val deltaLFatigueHigh = simulateDeltaL(engine, snapshot(fatigueBand = "high"), baseNotif)
        println(String.format("  fatigueBand=low:                    deltaL = %.4f min", deltaLFatigueLow))
        println(String.format("  fatigueBand=med:                    deltaL = %.4f min", deltaLFatigueMed))
        println(String.format("  fatigueBand=high:                   deltaL = %.4f min", deltaLFatigueHigh))

        // 3. VIP Flag
        val deltaLNonVip = simulateDeltaL(engine, baseSnapshot, notif(isVip = false))
        val deltaLVip = simulateDeltaL(engine, baseSnapshot, notif(isVip = true))
        println(String.format("  isVip=false:                        deltaL = %.4f min", deltaLNonVip))
        println(String.format("  isVip=true:                         deltaL = %.4f min", deltaLVip))

        // 4. Urgency Score Band (low < 0.33, med < 0.66, high >= 0.66)
        val deltaLUrgLow = simulateDeltaL(engine, baseSnapshot, notif(urgencyScore = 0.1))
        val deltaLUrgMed = simulateDeltaL(engine, baseSnapshot, notif(urgencyScore = 0.5))
        val deltaLUrgHigh = simulateDeltaL(engine, baseSnapshot, notif(urgencyScore = 0.9))
        println(String.format("  urgencyScore=0.1 (low):             deltaL = %.4f min", deltaLUrgLow))
        println(String.format("  urgencyScore=0.5 (med):             deltaL = %.4f min", deltaLUrgMed))
        println(String.format("  urgencyScore=0.9 (high):            deltaL = %.4f min", deltaLUrgHigh))

        // 5. Repeat Count Band (R0 <= 1, R2 == 2, R3 >= 3)
        val deltaLRep0 = simulateDeltaL(engine, baseSnapshot, notif(repeatCount5m = 0))
        val deltaLRep2 = simulateDeltaL(engine, baseSnapshot, notif(repeatCount5m = 2))
        val deltaLRep5 = simulateDeltaL(engine, baseSnapshot, notif(repeatCount5m = 5))
        println(String.format("  repeatCount5m=0 (R0):               deltaL = %.4f min", deltaLRep0))
        println(String.format("  repeatCount5m=2 (R2):               deltaL = %.4f min", deltaLRep2))
        println(String.format("  repeatCount5m=5 (R3):               deltaL = %.4f min", deltaLRep5))

        // 6. Task Type (coding, studying, browsing)
        val deltaLCoding = simulateDeltaL(engine, snapshot(taskType = TaskType.CODING), baseNotif)
        val deltaLStudying = simulateDeltaL(engine, snapshot(taskType = TaskType.STUDYING), baseNotif)
        val deltaLBrowsing = simulateDeltaL(engine, snapshot(taskType = TaskType.BROWSING), baseNotif)
        println(String.format("  taskType=CODING:                    deltaL = %.4f min", deltaLCoding))
        println(String.format("  taskType=STUDYING:                  deltaL = %.4f min", deltaLStudying))
        println(String.format("  taskType=BROWSING:                  deltaL = %.4f min", deltaLBrowsing))

        // 7. Hour Band (morning, afternoon, evening)
        val deltaLMorning = simulateDeltaL(engine, snapshot(hourBand = "morning"), baseNotif)
        val deltaLAfternoon = simulateDeltaL(engine, snapshot(hourBand = "afternoon"), baseNotif)
        val deltaLEvening = simulateDeltaL(engine, snapshot(hourBand = "evening"), baseNotif)
        println(String.format("  hourBand=morning:                   deltaL = %.4f min", deltaLMorning))
        println(String.format("  hourBand=afternoon:                 deltaL = %.4f min", deltaLAfternoon))
        println(String.format("  hourBand=evening:                   deltaL = %.4f min", deltaLEvening))

        // 8. Focus State (DEEP_FOCUS, WORKING, DISTRACTED)
        val deltaLDeepFocus = simulateDeltaL(engine, snapshot(focusState = FocusState.DEEP_FOCUS), baseNotif)
        val deltaLWorking = simulateDeltaL(engine, snapshot(focusState = FocusState.WORKING), baseNotif)
        val deltaLDistracted = simulateDeltaL(engine, snapshot(focusState = FocusState.DISTRACTED), baseNotif)
        println(String.format("  focusState=DEEP_FOCUS:              deltaL = %.4f min", deltaLDeepFocus))
        println(String.format("  focusState=WORKING:                 deltaL = %.4f min", deltaLWorking))
        println(String.format("  focusState=DISTRACTED:              deltaL = %.4f min", deltaLDistracted))
        println("=====================================")
    }
}

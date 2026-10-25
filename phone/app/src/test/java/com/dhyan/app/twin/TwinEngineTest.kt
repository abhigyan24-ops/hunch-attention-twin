package com.dhyan.app.twin

import com.dhyan.app.twin.context.BaselineBucket
import com.dhyan.app.twin.context.ContextBucket
import com.dhyan.app.twin.model.*
import com.dhyan.app.twin.params.InMemoryParamStore
import com.dhyan.app.twin.params.ParamPersistence
import com.dhyan.app.twin.params.ParamRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class TwinEngineTest {

    private class EmptyPersistence : ParamPersistence {
        override suspend fun loadAll(): List<ParamRecord> = emptyList()
        override suspend fun upsert(records: List<ParamRecord>) {}
    }

    private fun validSnapshot(ts: Long = 1000L, confidence: Double = 0.85): ContextSnapshot = ContextSnapshot(
        ts = ts,
        focusState = FocusState.WORKING,
        taskType = TaskType.CODING,
        taskSource = TaskSource.INFERRED,
        confidence = confidence,
        hourBand = "evening",
        minutesInState = 45.0,
        fatigueBand = "med",
        recentInterrupts30m = 1,
        laptopState = LaptopState.FLOWING,
        phoneState = PhoneState.SCREEN_OFF,
        secondScreenOverlap = false,
        declaredSession = null
    )

    private val sampleNotif = NotificationInput(
        appCat = "social",
        notifCat = "msg",
        isVip = false,
        urgencyScore = 0.2,
        repeatCount5m = 0
    )

    @Test
    fun returnsModelNotLoadedWhenStoreNotLoaded() {
        val store = InMemoryParamStore(EmptyPersistence(), flushDispatcher = Dispatchers.Unconfined)
        val engine = TwinEngine(store, clock = { 1000L })

        val outcome = engine.simulate(validSnapshot(ts = 1000L), sampleNotif)
        assertTrue("Expected NotReady", outcome is SimOutcome.NotReady)
        assertEquals(NotReadyReason.MODEL_NOT_LOADED, (outcome as SimOutcome.NotReady).reason)
    }

    @Test
    fun returnsInvalidSnapshotOnMalformedData() = runBlocking {
        val store = InMemoryParamStore(EmptyPersistence(), flushDispatcher = Dispatchers.Unconfined)
        store.load()
        val engine = TwinEngine(store, clock = { 1000L })

        // Negative timestamp
        val badTs = validSnapshot(ts = -100L)
        val out1 = engine.simulate(badTs, sampleNotif)
        assertTrue(out1 is SimOutcome.NotReady)
        assertEquals(NotReadyReason.INVALID_SNAPSHOT, (out1 as SimOutcome.NotReady).reason)

        // Invalid confidence > 1.0
        val badConf = validSnapshot(confidence = 1.5)
        val out2 = engine.simulate(badConf, sampleNotif)
        assertTrue(out2 is SimOutcome.NotReady)
        assertEquals(NotReadyReason.INVALID_SNAPSHOT, (out2 as SimOutcome.NotReady).reason)
    }

    @Test
    fun returnsStaleContextWhenSnapshotIsTooOld() = runBlocking {
        val store = InMemoryParamStore(EmptyPersistence(), flushDispatcher = Dispatchers.Unconfined)
        store.load()
        // Current clock = 500_000, snapshot ts = 100_000 -> age = 400_000 ms > 120_000 ms threshold
        val engine = TwinEngine(store, clock = { 500_000L })

        val staleSnap = validSnapshot(ts = 100_000L)
        val outcome = engine.simulate(staleSnap, sampleNotif)

        assertTrue(outcome is SimOutcome.NotReady)
        assertEquals(NotReadyReason.STALE_CONTEXT, (outcome as SimOutcome.NotReady).reason)
    }

    @Test
    fun coldStoreIsReadyWithPriorsAndLowEvidenceStrength() = runBlocking {
        val store = InMemoryParamStore(EmptyPersistence(), flushDispatcher = Dispatchers.Unconfined)
        store.load()
        val engine = TwinEngine(store, clock = { 1000L })

        val outcome = engine.simulate(validSnapshot(ts = 1000L), sampleNotif, seed = 42L)
        assertTrue("Cold store must return Ready (priors apply)", outcome is SimOutcome.Ready)

        val result = (outcome as SimOutcome.Ready).result
        assertTrue("Evidence strength on cold store must be low (< 0.2)", result.evidenceStrength < 0.2)
        assertTrue("Expected SHOW loss > glance cost", result.show.mean > 0.4)
    }
}

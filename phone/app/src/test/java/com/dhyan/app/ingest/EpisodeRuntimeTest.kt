package com.dhyan.app.ingest

import com.dhyan.app.twin.episodes.EpisodeLabel
import com.dhyan.app.twin.model.ContextSnapshot
import com.dhyan.app.twin.model.FocusState
import com.dhyan.app.twin.model.LaptopState
import com.dhyan.app.twin.model.NotificationInput
import com.dhyan.app.twin.model.PhoneState
import com.dhyan.app.twin.model.TaskSource
import com.dhyan.app.twin.model.TaskType
import com.dhyan.app.twin.context.ContextBucketer
import com.dhyan.app.twin.params.InMemoryParamStore
import com.dhyan.app.twin.params.ParamPersistence
import com.dhyan.app.twin.params.ParamRecord
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.ConcurrentHashMap

@OptIn(ExperimentalCoroutinesApi::class)
class EpisodeRuntimeTest {

    private class MockPersistence : ParamPersistence {
        val records = ConcurrentHashMap<String, ParamRecord>()
        override suspend fun loadAll(): List<ParamRecord> = records.values.toList()
        override suspend fun upsert(records: List<ParamRecord>) {
            for (r in records) this.records[r.key] = r
        }
    }

    private lateinit var persistence: MockPersistence
    private lateinit var store: InMemoryParamStore

    @Before
    fun setUp() {
        ClockOffset.reset()
        persistence = MockPersistence()
        store = InMemoryParamStore(persistence = persistence)
    }

    @Test
    fun episodeRuntime_consumesPhoneAndLaptopEvents_persistsLearnedParams() = runTest {
        store.load()
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val runtimeScope = TestScope(testDispatcher)
        val runtime = EpisodeRuntime(store = store, scope = runtimeScope, enablePeriodicChecker = false)

        val t0 = 10_000_000L

        // 1. Phone notification posted
        runtime.onNotificationPosted(
            key = "notif_rt_1",
            appCat = "social",
            notifCat = "msg_known",
            senderHash = "hash123",
            isVip = false,
            urgencyScore = 0.5,
            ts = t0
        )
        runtimeScope.advanceUntilIdle()

        // 2. Phone user pickup & foreground
        runtime.onAppForeground("social", "feed", ts = t0 + 10_000L)
        runtimeScope.advanceUntilIdle()

        // 3. User leaves phone app after 2 minutes
        runtime.onAppBackground(durationMs = 120_000L, ts = t0 + 130_000L)
        runtimeScope.advanceUntilIdle()

        // 4. Laptop resumes work via edit burst
        runtime.eventStreamAdapter.processEvent("edit_burst", t0 + 190_000L, mapOf("editsPerMin" to 35.0))
        runtimeScope.advanceUntilIdle()

        // Episode is closed and parameters should be updated in store and flushed
        val notifInput = NotificationInput(
            appCat = "social",
            notifCat = "msg_known",
            isVip = false,
            urgencyScore = 0.5,
            repeatCount5m = 0
        )
        val dummySnapshot = ContextSnapshot(
            ts = t0,
            focusState = FocusState.DEEP_FOCUS,
            taskType = TaskType.CODING,
            taskSource = TaskSource.INFERRED,
            confidence = 0.9,
            hourBand = "afternoon",
            minutesInState = 30.0,
            fatigueBand = "low",
            recentInterrupts30m = 0,
            laptopState = LaptopState.FLOWING,
            phoneState = PhoneState.SCREEN_OFF,
            secondScreenOverlap = false,
            declaredSession = null
        )
        val bucket = ContextBucketer.bucket(dummySnapshot, notifInput)
        val parentStat = store.getBetaStat(bucket.parentKey())
        assertNotNull("Parent pickup stat must be updated after episode close", parentStat)
        assertTrue("Successes should be recorded for picked-up notification", parentStat!!.successes > 0.0)

        // Verify persistence received records
        assertTrue("Persistence must contain flushed parameter records", persistence.records.isNotEmpty())
    }

    @Test
    fun episodeRuntime_localGapCensors_remoteGapDoesNotCensor() = runTest {
        store.load()
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val runtimeScope = TestScope(testDispatcher)
        val runtime = EpisodeRuntime(store = store, scope = runtimeScope, enablePeriodicChecker = false)

        val t0 = 20_000_000L

        // Local gap censors
        runtime.onNotificationPosted("n_loc", "news", "headline", null, false, 0.2, ts = t0)
        runtime.onLocalCollectorGap("usage_service_crashed", ts = t0 + 5_000L)
        runtimeScope.advanceUntilIdle()

        assertEquals(EpisodeLabel.NONE, runtime.builder.activeEpisodeLabel)

        // Remote gap does not censor
        runtime.onNotificationPosted("n_rem", "comms", "chat", null, false, 0.4, ts = t0 + 10_000L)
        runtimeScope.advanceUntilIdle()
        runtime.onAppForeground("comms", "chat_window", ts = t0 + 15_000L)
        runtimeScope.advanceUntilIdle()
        runtime.onAppBackground(60_000L, ts = t0 + 75_000L)
        runtimeScope.advanceUntilIdle()

        runtime.eventStreamAdapter.processEvent("gap", t0 + 80_000L, mapOf("reason" to "relay_down"))
        runtimeScope.advanceUntilIdle()

        assertEquals(EpisodeLabel.NONE, runtime.builder.activeEpisodeLabel)
        val outcome = runtime.builder.lastOutcome
        assertNotNull("Remote gap must close episode as dwell-only", outcome)
        assertFalse("Remote gap outcome is not censored", outcome!!.isCensored)
        assertTrue("Resume is flagged as estimated", outcome.isResumeEstimated)
    }
}

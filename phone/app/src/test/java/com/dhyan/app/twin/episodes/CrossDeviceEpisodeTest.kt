package com.dhyan.app.twin.episodes

import com.dhyan.app.actuator.InMemoryParamPersistence
import com.dhyan.app.data.EventDao
import com.dhyan.app.data.EventEntity
import com.dhyan.app.ingest.ClockOffset
import com.dhyan.app.sync.data.InboundEventListener
import com.dhyan.app.sync.data.SyncDataEngine
import com.dhyan.app.sync.session.RelayClient
import com.dhyan.app.sync.session.SessionTransport
import com.dhyan.app.sync.session.SessionTransportListener
import com.dhyan.app.twin.context.ContextBucketer
import com.dhyan.app.twin.model.ContextSnapshot
import com.dhyan.app.twin.model.FocusState
import com.dhyan.app.twin.model.LaptopState
import com.dhyan.app.twin.model.NotificationInput
import com.dhyan.app.twin.model.PhoneState
import com.dhyan.app.twin.model.TaskSource
import com.dhyan.app.twin.model.TaskType
import com.dhyan.app.twin.params.InMemoryParamStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Cross-device episode attribution, outcome measurement, and learning tests
 * per Spec 7.1, 7.2, 25.4, 25.13, and 25.14.
 */
class CrossDeviceEpisodeTest {

    private val closedEpisodes = mutableListOf<Episode>()
    private lateinit var builder: EpisodeBuilder
    private lateinit var adapter: EventStreamAdapter
    private lateinit var store: InMemoryParamStore

    private fun dummySnapshot(ts: Long = 1_000_000L): ContextSnapshot = ContextSnapshot(
        ts = ts,
        focusState = FocusState.DEEP_FOCUS,
        taskType = TaskType.CODING,
        taskSource = TaskSource.INFERRED,
        confidence = 0.9,
        hourBand = "afternoon",
        minutesInState = 45.0,
        fatigueBand = "low",
        recentInterrupts30m = 0,
        laptopState = LaptopState.FLOWING,
        phoneState = PhoneState.SCREEN_OFF,
        secondScreenOverlap = false,
        declaredSession = null
    )

    @Before
    fun setUp() {
        ClockOffset.reset()
        closedEpisodes.clear()
        store = InMemoryParamStore(persistence = InMemoryParamPersistence())
        runBlocking { store.load() }
        builder = EpisodeBuilder(
            windowMin = 30.0,
            defaultEstimatedResumeDelayMin = 3.5,
            onEpisodeClosed = { closedEpisodes.add(it) }
        )
        adapter = EventStreamAdapter(builder)
    }

    @Test
    fun crossDeviceEpisode_phoneDwellAndLaptopResume() {
        val t0 = 10_000_000L

        // 1. Notification posted on phone
        adapter.processEvent(
            type = "notif_posted",
            ts = t0,
            payload = mapOf(
                "key" to "notif_msg_1",
                "appCat" to "comms",
                "notifCat" to "msg_known",
                "urgencyScore" to 0.4
            )
        )

        // 2. User picks up phone at t0 + 1 min
        val tPickup = t0 + 60_000L
        adapter.processEvent(
            type = "app_foreground",
            ts = tPickup,
            payload = mapOf("appCat" to "comms", "surface" to "chat")
        )

        // 3. Laptop window change away from work (distraction spread) at t0 + 2 min
        val tLaptopDistract = t0 + 120_000L
        adapter.processEvent(
            type = "window_change",
            ts = tLaptopDistract,
            payload = mapOf("appCat" to "social", "app" to "Twitter")
        )

        // 4. User finishes phone session at t0 + 6 min (5 min dwell)
        val tBackground = t0 + 360_000L
        adapter.processEvent(
            type = "app_background",
            ts = tBackground,
            payload = mapOf("durationMs" to 300_000L)
        )

        // 5. User resumes work on laptop via VS Code extension at t0 + 8 min (2 min resume delay)
        val tResume = t0 + 480_000L
        adapter.processEvent(
            type = "resume_first_edit",
            ts = tResume,
            payload = mapOf("delayMs" to 120_000L)
        )

        assertEquals("Episode should close upon laptop resume", 1, closedEpisodes.size)
        val ep = closedEpisodes[0]
        assertEquals(EpisodeLabel.NOTIF_TRIGGERED, ep.label)
        val outcome = ep.outcome
        assertNotNull(outcome)
        assertTrue(outcome!!.pickedUp)
        assertEquals(1.0, outcome.pickupLatencyMin ?: 0.0, 1e-4)
        assertEquals(5.0, outcome.dwellMin, 1e-4)
        assertEquals(2.0, outcome.resumeDelayMin, 1e-4)
        assertTrue("Laptop left work should be true", outcome.laptopLeftWork)
        assertFalse("Resume delay was observed, not estimated", outcome.isResumeEstimated)
        assertFalse("Episode was not censored", outcome.isCensored)
    }

    @Test
    fun crossDeviceEpisode_tabCategoryDiversion() {
        val t0 = 10_000_000L
        adapter.processEvent("notif_posted", t0, mapOf("key" to "notif_promo", "appCat" to "social"))
        adapter.processEvent("app_foreground", t0 + 30_000L, mapOf("appCat" to "social", "surface" to "feed"))

        // Browser emits tab_category with non-work domainCat "video"
        adapter.processEvent("tab_category", t0 + 60_000L, mapOf("domainCat" to "video"))
        adapter.processEvent("app_background", t0 + 180_000L, mapOf("durationMs" to 150_000L))
        adapter.processEvent("resume_first_edit", t0 + 240_000L, mapOf("delayMs" to 60_000L))

        assertEquals(1, closedEpisodes.size)
        val outcome = closedEpisodes[0].outcome!!
        assertTrue("tab_category non-work domain flags laptopLeftWork", outcome.laptopLeftWork)
        assertEquals(2.5, outcome.dwellMin, 1e-4)
        assertEquals(1.0, outcome.resumeDelayMin, 1e-4)
    }

    @Test
    fun tabCategory_missingDomainCat_isIgnored() {
        val t0 = 10_000_000L
        adapter.processEvent("window_change", t0, mapOf("appCat" to "coding"))
        assertEquals(TaskType.CODING, adapter.currentTaskType)
        assertEquals(LaptopState.FLOWING, adapter.currentLaptopState)

        // Missing domainCat should be ignored, leaving current laptop state unchanged
        adapter.processEvent("tab_category", t0 + 1000L, emptyMap())
        assertEquals(LaptopState.FLOWING, adapter.currentLaptopState)
    }

    @Test
    fun editBurst_doesNotChangeTaskType() {
        val t0 = 10_000_000L
        adapter.processEvent("window_change", t0, mapOf("appCat" to "learning"))
        assertEquals(TaskType.STUDYING, adapter.currentTaskType)

        // edit_burst must not alter TaskType
        adapter.processEvent("edit_burst", t0 + 5000L, mapOf("editsPerMin" to 40.0))
        assertEquals(TaskType.STUDYING, adapter.currentTaskType)
        assertEquals(LaptopState.FLOWING, adapter.currentLaptopState)
    }

    @Test
    fun crossDeviceEpisode_proxyResumeViaEditBurst() {
        val t0 = 10_000_000L
        adapter.processEvent("notif_posted", t0, mapOf("key" to "notif_edit", "appCat" to "comms"))
        adapter.processEvent("app_foreground", t0 + 60_000L, mapOf("appCat" to "comms", "surface" to "chat"))
        adapter.processEvent("app_background", t0 + 240_000L, mapOf("durationMs" to 180_000L))

        // No explicit resume_first_edit; laptop emits edit_burst in VS Code 2 min later
        val tBurst = t0 + 360_000L // 120_000 ms after background
        adapter.processEvent("edit_burst", tBurst, mapOf("editsPerMin" to 35.0, "saves" to 1))

        assertEquals("Proxy resume via edit_burst should close episode", 1, closedEpisodes.size)
        val outcome = closedEpisodes[0].outcome!!
        assertTrue(outcome.pickedUp)
        assertEquals(3.0, outcome.dwellMin, 1e-4)
        assertEquals(2.0, outcome.resumeDelayMin, 1e-4)
        assertFalse(outcome.isResumeEstimated)
    }

    @Test
    fun crossDeviceEpisode_proxyResumeViaWorkWindowChange() {
        val t0 = 10_000_000L
        adapter.processEvent("notif_posted", t0, mapOf("key" to "notif_win", "appCat" to "social"))
        adapter.processEvent("app_foreground", t0 + 60_000L, mapOf("appCat" to "social", "surface" to "feed"))
        adapter.processEvent("window_change", t0 + 90_000L, mapOf("appCat" to "gaming"))
        adapter.processEvent("app_background", t0 + 300_000L, mapOf("durationMs" to 240_000L))

        // Return to work app at t0 + 420_000L (120_000 ms after background)
        adapter.processEvent("window_change", t0 + 420_000L, mapOf("appCat" to "coding", "app" to "Code"))

        assertEquals("Return to work window triggers proxy resume", 1, closedEpisodes.size)
        val outcome = closedEpisodes[0].outcome!!
        assertTrue(outcome.laptopLeftWork)
        assertEquals(4.0, outcome.dwellMin, 1e-4)
        assertEquals(2.0, outcome.resumeDelayMin, 1e-4)
        assertFalse(outcome.isResumeEstimated)
    }

    @Test
    fun missingOrStaleLaptop_dwellOnlyWithEstimatedResume() {
        val t0 = 10_000_000L
        adapter.processEvent("notif_posted", t0, mapOf("key" to "notif_offline", "appCat" to "social"))
        adapter.processEvent("app_foreground", t0 + 60_000L, mapOf("appCat" to "social", "surface" to "feed"))
        adapter.processEvent("app_background", t0 + 360_000L, mapOf("durationMs" to 300_000L))

        // No laptop events arrive; window timeout fires at 30 min (t0 + 1_800_000L)
        val tTimeout = t0 + 1_800_000L
        adapter.processEvent("screen_on", tTimeout) // triggers checkTimeout

        assertEquals(1, closedEpisodes.size)
        val outcome = closedEpisodes[0].outcome!!
        assertTrue(outcome.pickedUp)
        assertEquals(5.0, outcome.dwellMin, 1e-4)
        assertEquals(3.5, outcome.resumeDelayMin, 1e-4)
        assertTrue("Missing laptop flags resume delay as estimated", outcome.isResumeEstimated)
        assertFalse(outcome.isCensored)
    }

    @Test
    fun episodeCloser_doesNotContaminateLearnedResumeWhenEstimated() {
        val closer = EpisodeCloser(store)
        val snapshot = dummySnapshot(1_000_000L)

        val trigger = EpisodeTrigger.Notification(
            key = "notif_est",
            notifClass = "social|urg=0.2|vip=false",
            appCat = "social",
            notifCat = "msg",
            senderHash = "abc",
            isVip = false,
            urgencyScore = 0.2,
            ts = 1_000_000L
        )

        val estimatedEpisode = Episode(
            id = "ep_est",
            trigger = trigger,
            snapshot = snapshot,
            openedTs = 1_000_000L,
            outcomeWindowEndTs = 2_800_000L,
            closedTs = 2_800_000L,
            outcome = EpisodeOutcome(
                pickedUp = true,
                dwellMin = 4.0,
                resumeDelayMin = 3.5,
                isResumeEstimated = true
            ),
            label = EpisodeLabel.NOTIF_TRIGGERED,
            isClosed = true
        )

        val baselineBucket = ContextBucketer.baselineBucket(snapshot)
        val notifInput = NotificationInput("social", "msg", false, 0.2, 0)
        val bucket = ContextBucketer.bucket(snapshot, notifInput)

        val prevSnap = store.resolve(bucket, baselineBucket, notifInput)!!
        closer.onEpisodeClosed(estimatedEpisode)
        val afterEstSnap = store.resolve(bucket, baselineBucket, notifInput)!!

        // Resume distribution parameters must NOT change for estimated resume
        assertEquals("Estimated resume must not change resumeMeanLog", prevSnap.resumeMeanLog, afterEstSnap.resumeMeanLog, 1e-6)
        assertEquals("Estimated resume must not change resumeSigma", prevSnap.resumeSigma, afterEstSnap.resumeSigma, 1e-6)

        // Now observe a truly measured episode
        val observedEpisode = estimatedEpisode.copy(
            id = "ep_obs",
            outcome = EpisodeOutcome(
                pickedUp = true,
                dwellMin = 4.0,
                resumeDelayMin = 0.5,
                isResumeEstimated = false
            )
        )
        closer.onEpisodeClosed(observedEpisode)
        val afterObsSnap = store.resolve(bucket, baselineBucket, notifInput)!!
        assertFalse("Observed resume must update resumeMeanLog", prevSnap.resumeMeanLog == afterObsSnap.resumeMeanLog)
    }

    @Test
    fun localCollectorGap_censorsEpisodeAndExcludesFromIgnores() {
        val closer = EpisodeCloser(store)
        val closerEpisodes = mutableListOf<Episode>()
        val closingBuilder = EpisodeBuilder(onEpisodeClosed = {
            closerEpisodes.add(it)
            closer.onEpisodeClosed(it)
        })
        val closingAdapter = EventStreamAdapter(closingBuilder)

        val notifInput = NotificationInput("social", "msg", false, 0.2, 0)
        val bucket = ContextBucketer.bucket(dummySnapshot(), notifInput)
        val baselineBucket = ContextBucketer.baselineBucket(dummySnapshot())
        val prevSnap = store.resolve(bucket, baselineBucket, notifInput)!!

        val t0 = 10_000_000L
        closingAdapter.processEvent("notif_posted", t0, mapOf("key" to "notif_local_gap", "appCat" to "social"))

        // Local collector gap (usage) damages the outcome -> must censor
        closingAdapter.processEvent("gap", t0 + 60_000L, mapOf("reason" to "usage_query_failed", "deviceFrom" to "phone", "deviceTo" to "phone"))

        assertEquals(1, closerEpisodes.size)
        val ep = closerEpisodes[0]
        assertEquals(EpisodeLabel.CENSORED, ep.label)
        assertTrue(ep.outcome!!.isCensored)

        // Per Spec 25.4: Censored outcomes stay excluded from ordinary failure updates
        val afterGapSnap = store.resolve(bucket, baselineBucket, notifInput)!!
        assertEquals("Censored episode must not increment beta failures", prevSnap.pickupBeta, afterGapSnap.pickupBeta, 1e-6)
    }

    @Test
    fun remoteLaptopGap_doesNotCensor_switchesToDwellOnlyWithEstimatedResume() {
        val t0 = 10_000_000L
        // 1. Notification posted on phone
        adapter.processEvent("notif_posted", t0, mapOf("key" to "notif_remote_gap", "appCat" to "social"))

        // 2. User picks up phone at t0 + 30s
        adapter.processEvent("app_foreground", t0 + 30_000L, mapOf("appCat" to "social", "surface" to "feed"))

        // 3. User finishes phone session at t0 + 330s (5m dwell) -> now RESUMING
        adapter.processEvent("app_background", t0 + 330_000L, mapOf("durationMs" to 300_000L))

        // 4. Laptop gap occurs (relay_down / peer_silent) -> must NOT censor!
        adapter.processEvent("gap", t0 + 360_000L, mapOf("reason" to "relay_down"))

        assertEquals("Remote gap should close episode as dwell-only", 1, closedEpisodes.size)
        val ep = closedEpisodes[0]
        assertEquals("Remote gap must not censor episode", EpisodeLabel.NOTIF_TRIGGERED, ep.label)
        val outcome = ep.outcome!!
        assertFalse("Remote gap outcome is not censored", outcome.isCensored)
        assertTrue("Resume is flagged as estimated", outcome.isResumeEstimated)
        assertEquals(5.0, outcome.dwellMin, 1e-4)
        assertEquals(3.5, outcome.resumeDelayMin, 1e-4)
    }

    @Test
    fun supersededEpisode_closesAtNewNotificationTimestamp() {
        val closer = EpisodeCloser(store)
        val closerEpisodes = mutableListOf<Episode>()
        val closingBuilder = EpisodeBuilder(onEpisodeClosed = {
            closerEpisodes.add(it)
            closer.onEpisodeClosed(it)
        })
        val closingAdapter = EventStreamAdapter(closingBuilder)

        val notifInput = NotificationInput("social", "msg", false, 0.2, 0)
        val bucket = ContextBucketer.bucket(dummySnapshot(), notifInput)
        val baselineBucket = ContextBucketer.baselineBucket(dummySnapshot())
        val prevSnap = store.resolve(bucket, baselineBucket, notifInput)!!

        val t0 = 10_000_000L
        // 1. Notification A arrives
        closingAdapter.processEvent("notif_posted", t0, mapOf("key" to "notif_A", "appCat" to "social"))

        // 2. Notification B arrives 2 min later before pickup
        closingAdapter.processEvent("notif_posted", t0 + 120_000L, mapOf("key" to "notif_B", "appCat" to "social"))

        assertEquals(1, closerEpisodes.size)
        val epA = closerEpisodes[0]
        assertEquals(EpisodeLabel.SUPERSEDED, epA.label)
        assertEquals(t0 + 120_000L, epA.closedTs)
        assertFalse("Superseded episode before pickup marked unpicked", epA.outcome!!.pickedUp)

        // Per Spec 7.1: superseded without pickup records a non-pickup
        val afterSupersededSnap = store.resolve(bucket, baselineBucket, notifInput)!!
        assertTrue("Superseded unpicked episode records failure", afterSupersededSnap.pickupBeta > prevSnap.pickupBeta)
    }

    @Test
    fun clockSkew_appliedViaTsAdj() {
        // Laptop clock is 5000 ms ahead of phone
        // Ping-pong exchange:
        val t0 = 10_000_000L
        val t1 = t0 + 5010L // peer receives
        val t2 = t0 + 5010L // peer replies
        val t3 = t0 + 20L   // phone receives (RTT = 20ms, offset = 5000ms)
        ClockOffset.recordSample(t0, t1, t2, t3)
        assertEquals(5000L, ClockOffset.currentOffsetMs())

        // Raw peer timestamp is 10_005_000; adjusted timestamp should be 10_000_000
        val rawPeerTs = 10_005_000L
        val adjusted = ClockOffset.adjustIncomingTs(rawPeerTs)
        assertEquals(10_000_000L, adjusted)

        // Event processed with tsAdj in payload
        adapter.processEvent(
            type = "window_change",
            ts = rawPeerTs,
            payload = mapOf("tsAdj" to adjusted, "appCat" to "social")
        )
        // Ensure adapter took the adjusted timestamp
        val snapshot = adapter.buildSnapshot(adjusted)
        assertEquals(adjusted, snapshot.ts)
    }

    @Test
    fun deduplication_filtersOutDuplicateEvents() = runBlocking {
        val capturedEvents = mutableListOf<String>()
        val mockDao = object : EventDao {
            private val storage = mutableMapOf<String, EventEntity>()
            override suspend fun insert(event: EventEntity): Long {
                storage["${event.deviceId}_${event.eventId}"] = event
                return 1L
            }
            override suspend fun insertAll(events: List<EventEntity>) {
                events.forEach { insert(it) }
            }
            override suspend fun findByDeviceAndId(deviceId: String, eventId: String): EventEntity? =
                storage["${deviceId}_${eventId}"]
            override suspend fun getOutboundAfterSeq(deviceId: String, afterSeq: Long, limit: Int): List<EventEntity> = emptyList()
            override suspend fun getHighestSeq(deviceId: String): Long? = null
            override suspend fun recentEvents(sinceMs: Long, limit: Int): List<EventEntity> = emptyList()
            override suspend fun recentByKind(kind: String, sinceMs: Long, limit: Int): List<EventEntity> = emptyList()
            override suspend fun purgeOlderThan(beforeMs: Long): Int = 0
        }

        val dummyTransport = object : SessionTransport {
            override fun connect(url: String, roomId: String, device: String, listener: SessionTransportListener) {}
            override fun send(text: String): Boolean = true
            override fun close(code: Int, reason: String) {}
        }

        val dummyClient = RelayClient(
            pk = ByteArray(32),
            sidHex = "sid123",
            relayUrl = "ws://127.0.0.1:8080/ws",
            transport = dummyTransport,
            scope = CoroutineScope(Dispatchers.Unconfined)
        )

        val engine = SyncDataEngine(
            eventDao = mockDao,
            relayClient = dummyClient,
            inboundEventListener = InboundEventListener { type, _, _ ->
                capturedEvents.add(type)
            }
        )

        val batch = listOf(
            com.dhyan.app.sync.data.CanonicalTestEvents.event(1, "window_change", id = com.dhyan.app.sync.data.CanonicalTestEvents.uuid(1)),
            com.dhyan.app.sync.data.CanonicalTestEvents.event(2, "edit_burst", id = com.dhyan.app.sync.data.CanonicalTestEvents.uuid(2))
        )
        engine.processInboundBatch(1L, batch)
        assertEquals("Both valid events should be emitted", listOf("window_change", "edit_burst"), capturedEvents)

        // Now replay with duplicate ev_2 and a new ev_3
        val batch2 = listOf(
            com.dhyan.app.sync.data.CanonicalTestEvents.event(3, "edit_burst", id = com.dhyan.app.sync.data.CanonicalTestEvents.uuid(2)),
            com.dhyan.app.sync.data.CanonicalTestEvents.event(4, "resume_first_edit", id = com.dhyan.app.sync.data.CanonicalTestEvents.uuid(3))
        )
        engine.processInboundBatch(3L, batch2)
        // ev_2 must be rejected as duplicate and not delivered to listener
        assertEquals("Duplicate ev_2 was filtered; only ev_3 delivered", listOf("window_change", "edit_burst", "resume_first_edit"), capturedEvents)
    }
}

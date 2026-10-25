package com.dhyan.app.twin.episodes

import com.dhyan.app.twin.model.ContextSnapshot
import com.dhyan.app.twin.model.FocusState
import com.dhyan.app.twin.model.LaptopState
import com.dhyan.app.twin.model.PhoneState
import com.dhyan.app.twin.model.TaskSource
import com.dhyan.app.twin.model.TaskType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class EpisodeBuilderTest {

    private val closedEpisodes = mutableListOf<Episode>()
    private lateinit var builder: EpisodeBuilder

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
        closedEpisodes.clear()
        builder = EpisodeBuilder(
            windowMin = 30.0,
            onEpisodeClosed = { closedEpisodes.add(it) }
        )
    }

    @Test
    fun twoNotificationsThreeMinutesApart_closesFirstAsSuperseded() {
        val t0 = 1_000_000L
        val t1 = t0 + 3 * 60 * 1000L // 3 minutes later

        // 1. Notification A arrives
        builder.onNotificationPosted(
            key = "notif_A",
            appCat = "social",
            notifCat = "social_notif",
            senderHash = "hash_a",
            isVip = false,
            urgencyScore = 0.2,
            ts = t0,
            snapshot = dummySnapshot(t0)
        )
        assertTrue("No episode should be closed yet", closedEpisodes.isEmpty())

        // 2. Notification B arrives 3 min later, before user picks up phone
        builder.onNotificationPosted(
            key = "notif_B",
            appCat = "comms",
            notifCat = "msg_known",
            senderHash = "hash_b",
            isVip = false,
            urgencyScore = 0.6,
            ts = t1,
            snapshot = dummySnapshot(t1)
        )

        // Episode A must immediately close as SUPERSEDED with pickedUp = false
        assertEquals("Episode A should have closed", 1, closedEpisodes.size)
        val epA = closedEpisodes[0]
        assertEquals(EpisodeLabel.SUPERSEDED, epA.label)
        assertFalse("Episode A pickedUp should be false", epA.outcome?.pickedUp ?: true)
        assertEquals(t1, epA.closedTs)
        val triggerA = epA.trigger as EpisodeTrigger.Notification
        assertEquals("notif_A", triggerA.key)

        // 3. User responds to Notification B: foregrounds app at t0 + 4 min
        val tForeground = t0 + 4 * 60 * 1000L
        builder.onAppForeground("comms", "chat", tForeground, dummySnapshot(tForeground))

        // 4. User finishes and backgrounds app at t0 + 10 min (6 min dwell)
        val tBackground = t0 + 10 * 60 * 1000L
        builder.onAppBackground(6 * 60 * 1000L, tBackground)

        // 5. User resumes work on laptop at t0 + 12 min (2 min resume delay)
        val tResume = t0 + 12 * 60 * 1000L
        builder.onResumeFirstEdit(2 * 60 * 1000L, tResume)

        // Episode B must be closed as NOTIF_TRIGGERED with pickedUp = true
        assertEquals("Episode B should now be closed", 2, closedEpisodes.size)
        val epB = closedEpisodes[1]
        assertEquals(EpisodeLabel.NOTIF_TRIGGERED, epB.label)
        val outcomeB = epB.outcome
        assertNotNull(outcomeB)
        assertTrue(outcomeB!!.pickedUp)
        assertEquals(1.0, outcomeB.pickupLatencyMin ?: 0.0, 1e-4) // 4 min - 3 min = 1 min
        assertEquals("comms", outcomeB.firstAppCat)
        assertEquals(6.0, outcomeB.dwellMin, 1e-4)
        assertEquals(2.0, outcomeB.resumeDelayMin, 1e-4)
    }

    @Test
    fun inSessionNotificationArrival_doesNotSupersedeOrOpenNewEpisode() {
        val t0 = 1_000_000L
        builder.onNotificationPosted(
            key = "notif_1",
            appCat = "social",
            notifCat = "social_notif",
            senderHash = "hash_1",
            isVip = false,
            urgencyScore = 0.3,
            ts = t0,
            snapshot = dummySnapshot(t0)
        )

        // User opens app at t0 + 1 min (now in-app session)
        val tOpen = t0 + 60_000L
        builder.onAppForeground("social", "feed", tOpen, dummySnapshot(tOpen))

        // Second notification arrives while user is in-app
        val tInSession = t0 + 120_000L
        builder.onNotificationPosted(
            key = "notif_2",
            appCat = "social",
            notifCat = "promo",
            senderHash = "hash_2",
            isVip = false,
            urgencyScore = 0.1,
            ts = tInSession,
            snapshot = dummySnapshot(tInSession)
        )

        assertEquals("Should not have closed any episode yet", 0, closedEpisodes.size)
        assertEquals("In-session arrival count should be 1", 1L, builder.inSessionArrivalCount)

        // Close app and resume
        builder.onAppBackground(300_000L, t0 + 420_000L)
        builder.onResumeFirstEdit(60_000L, t0 + 480_000L)

        // Only notif_1 closed
        assertEquals(1, closedEpisodes.size)
        val closed = closedEpisodes[0]
        assertEquals("notif_1", (closed.trigger as EpisodeTrigger.Notification).key)
        assertTrue(closed.outcome!!.pickedUp)
    }

    @Test
    fun selfInitiatedPickup_triggersSelfInitiatedEpisode() {
        val t0 = 2_000_000L
        // User opens app with no notifications in the prior 2 minutes
        builder.onAppForeground("games", "game_screen", t0, dummySnapshot(t0))

        // 10 minutes dwell
        val tEnd = t0 + 10 * 60_000L
        builder.onAppBackground(10 * 60_000L, tEnd)
        // 3 minutes resume delay
        val tResume = tEnd + 3 * 60_000L
        builder.onResumeFirstEdit(3 * 60_000L, tResume)

        assertEquals(1, closedEpisodes.size)
        val ep = closedEpisodes[0]
        assertEquals(EpisodeLabel.SELF_INITIATED, ep.label)
        assertTrue(ep.trigger is EpisodeTrigger.SelfInitiated)
        assertTrue(ep.outcome!!.pickedUp)
        assertEquals(10.0, ep.outcome!!.dwellMin, 1e-4)
        assertEquals(3.0, ep.outcome!!.resumeDelayMin, 1e-4)
    }

    @Test
    fun outcomeWindowTimeout_closesEpisodeWithNotPicked() {
        val t0 = 1_000_000L
        builder.onNotificationPosted(
            key = "notif_ignored",
            appCat = "promo",
            notifCat = "promo",
            senderHash = "shop",
            isVip = false,
            urgencyScore = 0.05,
            ts = t0,
            snapshot = dummySnapshot(t0)
        )

        // Time advances beyond the 30-minute window (31 minutes)
        val tTimeout = t0 + 31 * 60_000L
        builder.checkTimeout(tTimeout)

        assertEquals(1, closedEpisodes.size)
        val ep = closedEpisodes[0]
        assertEquals(EpisodeLabel.NONE, ep.label)
        assertFalse(ep.outcome!!.pickedUp)
        assertNull(ep.outcome!!.pickupLatencyMin)
        assertEquals(0.0, ep.outcome!!.dwellMin, 1e-6)
    }
}

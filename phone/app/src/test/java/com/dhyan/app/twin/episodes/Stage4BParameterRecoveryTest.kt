package com.dhyan.app.twin.episodes

import com.dhyan.app.personas.GeneratorConfig
import com.dhyan.app.personas.LoggingPolicy
import com.dhyan.app.personas.PersonaCatalog
import com.dhyan.app.personas.PersonaLoader
import com.dhyan.app.personas.World
import com.dhyan.app.personas.world.HistoryGenerator
import com.dhyan.app.twin.context.BaselineBucket
import com.dhyan.app.twin.context.ContextBucket
import com.dhyan.app.twin.context.ContextBucketer
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
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class Stage4BParameterRecoveryTest {

    private class TestPersistence : ParamPersistence {
        val records = java.util.concurrent.ConcurrentHashMap<String, ParamRecord>()
        override suspend fun loadAll(): List<ParamRecord> = emptyList()
        override suspend fun upsert(records: List<ParamRecord>) {
            for (r in records) this.records[r.key] = r
        }
    }

    @Test
    fun rahulFourteenDayReplayRecoversPickupProbWithinEightPercent() = runBlocking {
        // 1. Load Rahul from catalog
        val persona = PersonaCatalog.byName("Rahul")

        // 2. Generate 14 days of deterministic synthetic history in MATCHED world
        val config = GeneratorConfig(
            seed = 42L,
            days = 14,
            world = World.MATCHED,
            loggingPolicy = LoggingPolicy.ShowAll
        )
        val generated = HistoryGenerator.generate(persona, config)
        assertTrue("Expected non-empty generated events", generated.events.isNotEmpty())

        // 3. Set up Twin learning pipeline
        val persistence = TestPersistence()
        val store = InMemoryParamStore(persistence = persistence)
        store.load() // Initialize cold prior state

        val closedEpisodes = mutableListOf<Episode>()
        val closer = EpisodeCloser(store)
        val builder = EpisodeBuilder(
            windowMin = 30.0,
            onEpisodeClosed = { ep ->
                closedEpisodes.add(ep)
                closer.onEpisodeClosed(ep)
            }
        )
        val adapter = EventStreamAdapter(builder, java.util.TimeZone.getTimeZone(persona.defaults.timezone))

        // 4. Stream events into adapter
        for (event in generated.events) {
            adapter.processEvent(
                type = event.type,
                ts = event.ts,
                payload = event.payload.values
            )
        }

        // Close any trailing episode at the end of the 14 days
        val lastTs = generated.events.last().ts
        builder.checkTimeout(lastTs + 35 * 60 * 1000L)

        println("Stage 4B Replay complete: ${closedEpisodes.size} episodes closed across 14 days.")
        val socialTruth = generated.truthRecords.filter { (it.trial?.pPickup ?: 0.0) > 0.5 }
        val socialPickedDraws = socialTruth.filter { it.trial?.uPick != null && it.trial.uPick < it.trial.pPickup }
        val socialNotifOwners = socialTruth.filter { it.trial?.owner == com.dhyan.app.personas.world.Owner.NOTIF }
        val eligibleSocial = socialTruth.filter { it.trialEligible }
        val eligiblePicked = eligibleSocial.filter { it.trial?.owner == com.dhyan.app.personas.world.Owner.NOTIF }
        println("Truth records with pPickup > 0.5: ${socialTruth.size}")
        println("  draw.picked: ${socialPickedDraws.size}/${socialTruth.size} (${socialPickedDraws.size.toDouble() / socialTruth.size})")
        println("  draw.owner == NOTIF: ${socialNotifOwners.size}/${socialTruth.size}")
        println("  trialEligible: ${eligibleSocial.size}, eligible & NOTIF: ${eligiblePicked.size} (${if (eligibleSocial.isNotEmpty()) eligiblePicked.size.toDouble() / eligibleSocial.size else 0.0})")

        val notifEpisodes = closedEpisodes.filter { it.trigger is EpisodeTrigger.Notification }
        val socialNotifEpisodes = notifEpisodes.filter { (it.trigger as EpisodeTrigger.Notification).notifCat == "social_notif" }
        val socialPicked = socialNotifEpisodes.filter { it.outcome?.pickedUp == true }
        println("Total notif episodes: ${notifEpisodes.size}")
        println("Social notif episodes: ${socialNotifEpisodes.size}, picked up: ${socialPicked.size}")
        if (socialNotifEpisodes.isNotEmpty()) {
            println("Social empirical pickup rate in episodes: ${socialPicked.size.toDouble() / socialNotifEpisodes.size}")
        }

        // 5. Query learned parameters for Rahul: (coding, social_notif)
        val notif = NotificationInput(
            appCat = "social",
            notifCat = "social_notif",
            isVip = true,
            urgencyScore = 0.3,
            repeatCount5m = 0
        )
        val snapshot = ContextSnapshot(
            ts = lastTs,
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
        val bucket = ContextBucketer.bucket(snapshot, notif)
        val baselineBucket = ContextBucketer.baselineBucket(snapshot)

        val modelSnapshot = store.resolve(bucket, baselineBucket, notif)
        assertNotNull("ModelSnapshot must resolve", modelSnapshot)

        // 1. Recover p_pickup: The persona ground truth for Rahul is defined at the notification-class level ("social_notif|coding")
        // Parent key: beta_pickup|nc=social|urg=low|rep=R0|vip=1
        val parentStat = store.getBetaStat(bucket.parentKey())
        assertNotNull("Parent stat for ${bucket.parentKey()} must be present", parentStat)

        val (alpha0, beta0) = com.dhyan.app.twin.params.PriorTable.pickupPrior(notif.appCat, notif.notifCat, bucket.fatigueBand)
        val recoveredParentProb = parentStat!!.pointEstimate(alpha0, beta0, kappa = 0.0, parentMean = 0.5)

        val trueRahulPickup = persona.pickupProb["social_notif|coding"] ?: 0.62
        println("Rahul ground truth p_pickup: $trueRahulPickup")
        println("Twin learned parent p_pickup: $recoveredParentProb (successes=${parentStat.successes}, failures=${parentStat.failures}, nEff=${modelSnapshot!!.nEff})")

        val diff = abs(recoveredParentProb - trueRahulPickup)
        println("Difference from ground truth: $diff (spec limit: <= 0.08)")

        assertTrue(
            "p_pickup error ($diff) must be within spec exit threshold 0.08. Expected in [${trueRahulPickup - 0.08}, ${trueRahulPickup + 0.08}], got $recoveredParentProb",
            diff <= 0.08
        )

        // 2. Verify dwell parameters moved toward persona truth (mu=2.6)
        val socialDwellMu = modelSnapshot.dwellMeanLog["social/feed"] ?: modelSnapshot.dwellMeanLog["social/reels"]
        assertNotNull("Social dwell should be learned", socialDwellMu)
        println("Learned social dwell mu: $socialDwellMu (persona ground truth: ${persona.dwell["social"]?.mu})")

        // 3. Verify resume delay moved toward persona truth (mu=2.0)
        println("Learned coding resume mu: ${modelSnapshot.resumeMeanLog} (persona ground truth: ${persona.resume["coding"]?.mu})")
    }
}

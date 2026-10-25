package com.dhyan.app.twin.episodes

import com.dhyan.app.twin.context.ContextBucketer
import com.dhyan.app.twin.model.NotificationInput
import com.dhyan.app.twin.params.InMemoryParamStore

/**
 * Connects closed episodes to the parameter learning pipeline P5 (spec 7.1, 8.4).
 *
 * Updates the in-memory parameter distributions (Beta for pickup, LogNormal for dwell and resume,
 * Dirichlet for destination choices, Gamma for baseline self hazard) in O(1) time.
 *
 * @param store The in-memory parameter store to update.
 */
class EpisodeCloser(
    private val store: InMemoryParamStore
) : EpisodeCloseListener {

    override fun onEpisodeClosed(episode: Episode) {
        val closedTs = episode.closedTs ?: episode.openedTs
        val outcome = episode.outcome ?: EpisodeOutcome(pickedUp = false)

        when (val trigger = episode.trigger) {
            is EpisodeTrigger.Notification -> {
                val notifInput = NotificationInput(
                    appCat = trigger.appCat,
                    notifCat = trigger.notifCat ?: "msg",
                    isVip = trigger.isVip,
                    urgencyScore = trigger.urgencyScore,
                    repeatCount5m = trigger.repeatCount5m
                )
                val bucket = ContextBucketer.bucket(episode.snapshot, notifInput)

                when (episode.label) {
                    EpisodeLabel.NOTIF_TRIGGERED -> {
                        // 1. Observe pickup
                        store.observePickup(bucket, picked = outcome.pickedUp, nowMs = closedTs)

                        if (outcome.pickedUp) {
                            // 2. Observe pickup latency
                            outcome.pickupLatencyMin?.takeIf { it > 0.0 }?.let {
                                store.observePickupLatency(it, closedTs)
                            }

                            // 3. Observe dwell
                            val appCat = outcome.firstAppCat ?: trigger.appCat
                            val surface = outcome.firstSurface ?: "feed"
                            if (outcome.dwellMin > 0.0) {
                                store.observeDwell(appCat, surface, outcome.dwellMin, closedTs)
                            }

                            // 4. Observe resume delay
                            if (outcome.resumeDelayMin > 0.0) {
                                store.observeResume(episode.snapshot.taskType.wire, outcome.resumeDelayMin, closedTs)
                            }

                            // 5. Observe destination choice
                            store.observeDestination(
                                kind = "notif",
                                bucket = bucket,
                                destination = "$appCat/$surface",
                                nowMs = closedTs
                            )
                        }
                    }
                    EpisodeLabel.SUPERSEDED, EpisodeLabel.NONE -> {
                        // Ignored or superseded before pickup: record non-pickup failure
                        store.observePickup(bucket, picked = false, nowMs = closedTs)
                    }
                    EpisodeLabel.SELF_INITIATED -> {
                        // Cannot have a Notification trigger with SELF_INITIATED label
                    }
                }
            }
            is EpisodeTrigger.SelfInitiated -> {
                if (episode.label == EpisodeLabel.SELF_INITIATED) {
                    val baselineBucket = ContextBucketer.baselineBucket(episode.snapshot)
                    val exposureHours = maxOf(0.01, (closedTs - episode.openedTs) / 3_600_000.0)

                    // 1. Observe self-initiated hazard
                    store.observeSelfPickups(baselineBucket, events = 1.0, exposureHours = exposureHours, nowMs = closedTs)

                    // 2. Observe dwell
                    val appCat = outcome.firstAppCat ?: "social"
                    val surface = outcome.firstSurface ?: "feed"
                    if (outcome.dwellMin > 0.0) {
                        store.observeDwell(appCat, surface, outcome.dwellMin, closedTs)
                    }

                    // 3. Observe resume delay
                    if (outcome.resumeDelayMin > 0.0) {
                        store.observeResume(episode.snapshot.taskType.wire, outcome.resumeDelayMin, closedTs)
                    }

                    // 4. Observe destination
                    val dummyNotif = NotificationInput(appCat, "msg", false, 0.5, 0)
                    val bucket = ContextBucketer.bucket(episode.snapshot, dummyNotif)
                    store.observeDestination(
                        kind = "self",
                        bucket = bucket,
                        destination = "$appCat/$surface",
                        nowMs = closedTs
                    )
                }
            }
        }
    }
}

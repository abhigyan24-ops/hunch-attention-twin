package com.dhyan.app.twin.episodes

import com.dhyan.app.twin.model.ContextSnapshot
import com.dhyan.app.twin.model.NotificationInput
import java.util.concurrent.atomic.AtomicLong

/**
 * Listener interface notified when an episode closes (ready for learning in P5).
 */
fun interface EpisodeCloseListener {
    fun onEpisodeClosed(episode: Episode)
}

/**
 * Single-owner stateful episode builder implementing spec 7.1.
 *
 * Attribution rules:
 * 1. At most ONE unresolved `notif_triggered` episode is active per device.
 * 2. If notification B arrives while notification A's window is open and user has NOT yet picked up:
 *    - Episode A closes immediately at t_B with pickedUp = false, label = SUPERSEDED.
 *    - Episode B becomes the active trigger.
 * 3. Notifications arriving while user is IN_APP / active session do not start new episodes (in-session arrivals).
 * 4. Pickups with no notification in the prior 2 minutes establish a self_initiated episode.
 *
 * @param windowMin Outcome window duration in minutes (spec default: 30.0).
 * @param defaultEstimatedResumeDelayMin Fallback resume delay when laptop is missing/stale (spec 7.2).
 * @param onEpisodeClosed Callback invoked whenever an episode finishes.
 */
class EpisodeBuilder(
    val windowMin: Double = 30.0,
    val defaultEstimatedResumeDelayMin: Double = 3.0,
    private val onEpisodeClosed: EpisodeCloseListener? = null
) {
    companion object {
        /** Canonical work categories derived from core-spec/categories.json */
        val WORK_CATEGORIES: Set<String> = setOf("coding", "docs", "learning")

        /** Local collector gap keywords that damage the outcome measurement (spec 25.4) */
        private val LOCAL_GAP_REASONS: Set<String> = setOf(
            "usage", "notif_listener", "a11y", "persistence", "local_gap", "sensor_crash"
        )
    }

    private enum class AttentionState {
        FOCUSED,
        NOTIFIED,
        IN_APP,
        RESUMING
    }

    private var state = AttentionState.FOCUSED
    private var activeEpisode: Episode? = null
    private var lastNotifTs: Long = -1L

    // Metrics for the currently active episode
    private var currentPickedUp = false
    private var currentPickupLatencyMin: Double? = null
    private var currentFirstAppCat: String? = null
    private var currentFirstSurface: String? = null
    private var currentDwellMin = 0.0
    private var currentResumeDelayMin = 0.0
    private var currentLaptopLeftWork = false
    private var lastBackgroundTs = -1L
    private var laptopTelemetryUnavailable = false

    // Baseline hazard tracking
    private var lastFocusStartTs: Long = 0L
    private val idCounter = AtomicLong(1L)

    var inSessionArrivalCount: Long = 0L
        private set

    val activeEpisodeLabel: EpisodeLabel
        get() = activeEpisode?.label ?: EpisodeLabel.NONE

    var lastOutcome: EpisodeOutcome? = null
        private set

    /**
     * Handles an incoming notification.
     */
    fun onNotificationPosted(
        key: String,
        appCat: String,
        notifCat: String?,
        senderHash: String?,
        isVip: Boolean,
        urgencyScore: Double,
        ts: Long,
        snapshot: ContextSnapshot,
        repeatCount5m: Int = 0
    ) {
        checkTimeout(ts)

        // Rule 3: Notification arriving while user is actively in-app or resuming is an in-session arrival
        if (state == AttentionState.IN_APP || state == AttentionState.RESUMING) {
            inSessionArrivalCount++
            lastNotifTs = ts
            return
        }

        // Rule 2: If previous notification episode is still open and unpicked, supersede it
        activeEpisode?.let { prev ->
            if (!prev.isClosed && !currentPickedUp) {
                closeEpisode(
                    closedTs = ts,
                    label = EpisodeLabel.SUPERSEDED,
                    outcome = EpisodeOutcome(
                        pickedUp = false,
                        pickupLatencyMin = null,
                        firstAppCat = null,
                        firstSurface = null,
                        dwellMin = 0.0,
                        resumeDelayMin = 0.0,
                        laptopLeftWork = false,
                        isResumeEstimated = false,
                        isCensored = false
                    )
                )
            }
        }

        // Open new notif_triggered episode
        val trigger = EpisodeTrigger.Notification(
            key = key,
            notifClass = "$appCat|urg=${urgencyScore}|vip=$isVip",
            appCat = appCat,
            notifCat = notifCat,
            senderHash = senderHash,
            isVip = isVip,
            urgencyScore = urgencyScore,
            repeatCount5m = repeatCount5m,
            ts = ts
        )

        val windowMs = (windowMin * 60_000.0).toLong()
        activeEpisode = Episode(
            id = "ep_${idCounter.getAndIncrement()}",
            trigger = trigger,
            snapshot = snapshot,
            openedTs = ts,
            outcomeWindowEndTs = ts + windowMs,
            label = EpisodeLabel.NOTIF_TRIGGERED
        )

        state = AttentionState.NOTIFIED
        currentPickedUp = false
        currentPickupLatencyMin = null
        currentFirstAppCat = null
        currentFirstSurface = null
        currentDwellMin = 0.0
        currentResumeDelayMin = 0.0
        currentLaptopLeftWork = false
        laptopTelemetryUnavailable = false
        lastNotifTs = ts
    }

    /**
     * Handles user picking up or foregrounding an app on the phone.
     */
    fun onAppForeground(
        appCat: String,
        surface: String,
        ts: Long,
        snapshot: ContextSnapshot
    ) {
        checkTimeout(ts)

        when (state) {
            AttentionState.NOTIFIED -> {
                // User opened app in response to notification episode
                currentPickedUp = true
                val latencyMs = ts - (activeEpisode?.openedTs ?: ts)
                currentPickupLatencyMin = maxOf(0.0, latencyMs / 60_000.0)
                currentFirstAppCat = appCat
                currentFirstSurface = surface
                state = AttentionState.IN_APP
            }
            AttentionState.FOCUSED -> {
                // Rule 4: Self-initiated if no notification in prior 2 minutes (120_000 ms)
                val isRecentNotif = (lastNotifTs > 0L) && (ts - lastNotifTs < 120_000L)
                if (!isRecentNotif) {
                    val trigger = EpisodeTrigger.SelfInitiated(ts = ts)
                    val windowMs = (windowMin * 60_000.0).toLong()
                    activeEpisode = Episode(
                        id = "ep_${idCounter.getAndIncrement()}",
                        trigger = trigger,
                        snapshot = snapshot,
                        openedTs = ts,
                        outcomeWindowEndTs = ts + windowMs,
                        label = EpisodeLabel.SELF_INITIATED
                    )
                    currentPickedUp = true
                    currentPickupLatencyMin = 0.0
                    currentFirstAppCat = appCat
                    currentFirstSurface = surface
                    currentDwellMin = 0.0
                    currentResumeDelayMin = 0.0
                    currentLaptopLeftWork = false
                    laptopTelemetryUnavailable = false
                    state = AttentionState.IN_APP
                }
            }
            AttentionState.IN_APP -> {
                // Continuous in-app activity
            }
            AttentionState.RESUMING -> {
                // Re-opened app while resuming (still part of dwell)
                state = AttentionState.IN_APP
            }
        }
    }

    /**
     * Handles app moving to background on the phone.
     */
    fun onAppBackground(
        durationMs: Long,
        ts: Long
    ) {
        if (state == AttentionState.IN_APP) {
            currentDwellMin += maxOf(0.0, durationMs / 60_000.0)
            lastBackgroundTs = ts

            if (laptopTelemetryUnavailable) {
                // Laptop telemetry is missing/stale -> complete dwell-only with estimated resume (spec 7.2)
                closeEpisode(
                    closedTs = ts,
                    label = activeEpisode?.label ?: EpisodeLabel.NONE,
                    outcome = buildCurrentOutcome(isResumeEstimated = true).copy(
                        resumeDelayMin = defaultEstimatedResumeDelayMin
                    )
                )
                state = AttentionState.FOCUSED
                lastFocusStartTs = ts
            } else {
                state = AttentionState.RESUMING
            }
        }
    }

    /**
     * Handles phone screen turning off.
     */
    fun onScreenOff(ts: Long) {
        if (state == AttentionState.IN_APP) {
            lastBackgroundTs = ts
            if (laptopTelemetryUnavailable) {
                closeEpisode(
                    closedTs = ts,
                    label = activeEpisode?.label ?: EpisodeLabel.NONE,
                    outcome = buildCurrentOutcome(isResumeEstimated = true).copy(
                        resumeDelayMin = defaultEstimatedResumeDelayMin
                    )
                )
                state = AttentionState.FOCUSED
                lastFocusStartTs = ts
            } else {
                state = AttentionState.RESUMING
            }
        }
    }

    /**
     * Handles laptop switching active window.
     */
    fun onLaptopWindowChange(
        appCat: String,
        ts: Long
    ) {
        val isWork = appCat in WORK_CATEGORIES
        if (state == AttentionState.IN_APP || state == AttentionState.RESUMING) {
            if (!isWork) {
                currentLaptopLeftWork = true
            } else if (state == AttentionState.RESUMING && currentResumeDelayMin == 0.0) {
                // Spec 7.2: Return to work app acts as the proxy for first non-idle input
                val delayMs = if (lastBackgroundTs > 0L) maxOf(0L, ts - lastBackgroundTs) else 0L
                onResumeFirstEdit(delayMs, ts)
            }
        }
    }

    /**
     * Handles laptop edit burst events (Spec 7.2 proxy for first non-idle input).
     */
    fun onLaptopEditBurst(
        editsPerMin: Double,
        ts: Long
    ) {
        if (state == AttentionState.RESUMING && editsPerMin > 0.0 && currentResumeDelayMin == 0.0) {
            val delayMs = if (lastBackgroundTs > 0L) maxOf(0L, ts - lastBackgroundTs) else 0L
            onResumeFirstEdit(delayMs, ts)
        }
    }

    /**
     * Handles service gap or telemetry loss (Spec 25.4 & Fix 1).
     *
     * Local collector gaps (usage, notif listener, a11y, persistence) damage the outcome -> censor.
     * Remote laptop gaps (relay_down, peer_silent, adapter_offline) must NOT censor;
     * they switch the outcome to dwell-only with isResumeEstimated = true.
     */
    fun onServiceGap(
        reason: String,
        ts: Long
    ) {
        val ep = activeEpisode ?: return
        if (ep.isClosed) return

        val normalized = reason.lowercase()
        val isLocal = LOCAL_GAP_REASONS.any { normalized.contains(it) }

        if (isLocal) {
            // Local collector gap damages the outcome measurement -> censor
            closeEpisode(
                closedTs = ts,
                label = EpisodeLabel.CENSORED,
                outcome = buildCurrentOutcome(isCensored = true)
            )
            state = AttentionState.FOCUSED
            lastFocusStartTs = ts
        } else {
            // Remote laptop gaps must NOT censor; switch to dwell-only with isResumeEstimated = true
            if (state == AttentionState.RESUMING) {
                val outcome = buildCurrentOutcome(isResumeEstimated = true).copy(
                    resumeDelayMin = defaultEstimatedResumeDelayMin
                )
                closeEpisode(
                    closedTs = ts,
                    label = ep.label,
                    outcome = outcome
                )
                state = AttentionState.FOCUSED
                lastFocusStartTs = ts
            } else {
                laptopTelemetryUnavailable = true
            }
        }
    }

    /**
     * Handles first meaningful edit or activity resuming on laptop.
     */
    fun onResumeFirstEdit(
        delayMs: Long,
        ts: Long
    ) {
        if (state == AttentionState.RESUMING) {
            currentResumeDelayMin = maxOf(0.0, delayMs / 60_000.0)
            // Episode complete: dwell and resume observed
            closeEpisode(
                closedTs = ts,
                label = activeEpisode?.label ?: EpisodeLabel.NONE,
                outcome = buildCurrentOutcome()
            )
            state = AttentionState.FOCUSED
            lastFocusStartTs = ts
        }
    }

    /**
     * Checks if the outcome window has expired.
     */
    fun checkTimeout(nowTs: Long) {
        val ep = activeEpisode ?: return
        if (ep.isClosed) return

        if (nowTs >= ep.outcomeWindowEndTs) {
            val isEstimated = currentPickedUp && currentResumeDelayMin == 0.0 && state == AttentionState.RESUMING
            val effectiveResumeDelay = if (isEstimated) defaultEstimatedResumeDelayMin else currentResumeDelayMin
            val outcome = buildCurrentOutcome(isResumeEstimated = isEstimated).copy(resumeDelayMin = effectiveResumeDelay)
            val label = if (currentPickedUp) ep.label else EpisodeLabel.NONE
            val closeAt = minOf(nowTs, ep.outcomeWindowEndTs)
            closeEpisode(closeAt, label, outcome)
            state = AttentionState.FOCUSED
            lastFocusStartTs = closeAt
        }
    }

    /**
     * Forces immediate close of the active episode (useful for graceful shutdown or testing).
     */
    fun forceClose(nowTs: Long) {
        val ep = activeEpisode ?: return
        if (!ep.isClosed) {
            closeEpisode(nowTs, ep.label, buildCurrentOutcome())
            state = AttentionState.FOCUSED
        }
    }

    private fun buildCurrentOutcome(
        isResumeEstimated: Boolean = false,
        isCensored: Boolean = false
    ): EpisodeOutcome = EpisodeOutcome(
        pickedUp = currentPickedUp,
        pickupLatencyMin = currentPickupLatencyMin,
        firstAppCat = currentFirstAppCat,
        firstSurface = currentFirstSurface,
        dwellMin = currentDwellMin,
        resumeDelayMin = currentResumeDelayMin,
        laptopLeftWork = currentLaptopLeftWork,
        isResumeEstimated = isResumeEstimated,
        isCensored = isCensored
    )

    private fun closeEpisode(
        closedTs: Long,
        label: EpisodeLabel,
        outcome: EpisodeOutcome
    ) {
        val ep = activeEpisode ?: return
        val finalized = ep.copy(
            closedTs = closedTs,
            outcome = outcome,
            label = label,
            isClosed = true
        )
        activeEpisode = null
        lastOutcome = outcome
        onEpisodeClosed?.onEpisodeClosed(finalized)
    }
}

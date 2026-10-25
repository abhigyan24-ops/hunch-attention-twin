package com.dhyan.app.twin.episodes

import com.dhyan.app.twin.model.ContextSnapshot

/**
 * Trigger that initiated an episode (spec 7.1).
 */
sealed class EpisodeTrigger {
    abstract val ts: Long

    /**
     * An episode triggered by an incoming notification.
     */
    data class Notification(
        val key: String,
        val notifClass: String,
        val appCat: String,
        val notifCat: String?,
        val senderHash: String?,
        val isVip: Boolean,
        val urgencyScore: Double,
        val repeatCount5m: Int = 0,
        override val ts: Long
    ) : EpisodeTrigger()

    /**
     * An episode triggered by a self-initiated pickup (no notification in prior 2 min).
     */
    data class SelfInitiated(
        override val ts: Long
    ) : EpisodeTrigger()
}

/**
 * Single-owner classification label for the episode (spec 7.1).
 */
enum class EpisodeLabel {
    /** A notification arrived and caused or qualified for the distraction window. */
    NOTIF_TRIGGERED,
    /** Self-initiated pickup with no notification in the prior 2 minutes. */
    SELF_INITIATED,
    /** A subsequent notification arrived before pickup; this episode closed as unpicked. */
    SUPERSEDED,
    /** Outcome window expired with no pickup or activity. */
    NONE
}

/**
 * Measured outcomes within the episode window (spec 7.1, 7.2).
 */
data class EpisodeOutcome(
    /** True if user opened an app or picked up the phone within the window. */
    val pickedUp: Boolean,
    /** Time from trigger to first pickup in minutes (null if not picked up). */
    val pickupLatencyMin: Double? = null,
    /** Category of the first opened app. */
    val firstAppCat: String? = null,
    /** Surface of the first opened app. */
    val firstSurface: String? = null,
    /** Dwell duration in minutes across distracting apps. */
    val dwellMin: Double = 0.0,
    /** Delay in minutes from return to work to first edit/activity on laptop. */
    val resumeDelayMin: Double = 0.0,
    /** True if laptop left the work category during the episode. */
    val laptopLeftWork: Boolean = false
)

/**
 * An episode tracking attention interruption and recovery within an outcome window W (spec 7.1).
 */
data class Episode(
    val id: String,
    val trigger: EpisodeTrigger,
    val snapshot: ContextSnapshot,
    val openedTs: Long,
    val outcomeWindowEndTs: Long,
    val closedTs: Long? = null,
    val outcome: EpisodeOutcome? = null,
    val label: EpisodeLabel = EpisodeLabel.NONE,
    val isClosed: Boolean = false
)

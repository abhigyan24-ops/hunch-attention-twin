package com.dhyan.app.ui.model

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.math.roundToInt

class DhyanStateHolder {
    // Current Persona
    var selectedPersona by mutableStateOf(PersonaCatalog.RAHUL)
        private set

    // Attention & Live Context
    var currentAttentionState by mutableStateOf(AttentionState.DEEP_FOCUS)
    var laptopPresence by mutableStateOf(LaptopPresence.FLOWING)
    var sessionElapsedMin by mutableIntStateOf(48)
    var currentHour by mutableIntStateOf(14)
    var fatigueLevel by mutableFloatStateOf(0.35f)
    var isOverlapActive by mutableStateOf(true)

    // Safety & Governance Controls
    var isKillSwitchActive by mutableStateOf(false)
    var cautionMultiplier by mutableFloatStateOf(1.0f) // alpha_d
    var isExplorationEnabled by mutableStateOf(true) // epsilon = 0.05
    var relayPairingCode by mutableStateOf("DH-7829")
    var isRelayConnected by mutableStateOf(true)

    // Current Simulation Input
    var simApp by mutableStateOf("Instagram")
    var simCategory by mutableStateOf("social")
    var simSender by mutableStateOf("@deepika_k")
    var simUrgency by mutableIntStateOf(1)
    var simIsVip by mutableStateOf(false)
    var simTask by mutableStateOf("Coding")

    // Fast-Forward Simulation State
    var isSimulatingFastForward by mutableStateOf(false)
    var fastForwardProgress by mutableFloatStateOf(0f)
    var fastForwardDaysSimulated by mutableIntStateOf(0)

    // Digest Queue
    val digestQueue = mutableStateListOf(
        DigestItem(
            id = "d1",
            appName = "Instagram",
            sender = "@deepika_k",
            category = "social",
            receivedAgo = "4m ago",
            heldReason = "Coding session active (48m). High hazard of infinite scroll dwell.",
            importanceScore = 0.12
        ),
        DigestItem(
            id = "d2",
            appName = "Slack",
            sender = "#eng-announcements",
            category = "work",
            receivedAgo = "11m ago",
            heldReason = "Broadcast channel; batching until natural task boundary.",
            importanceScore = 0.38
        ),
        DigestItem(
            id = "d3",
            appName = "YouTube",
            sender = "Tech Lead Digest",
            category = "entertainment",
            receivedAgo = "28m ago",
            heldReason = "Video recommendation held during deep focus block.",
            importanceScore = 0.08
        )
    )

    // Live Decision Feed
    val decisionFeed = mutableStateListOf(
        DecisionFeedItem(
            id = "dec1",
            appName = "WhatsApp",
            action = ActionVerdict.ALLOW,
            timestamp = "14:22",
            reason = "VIP Contact (Mom). Urgent keyword match overrides focus block."
        ),
        DecisionFeedItem(
            id = "dec2",
            appName = "Instagram",
            action = ActionVerdict.DIGEST,
            timestamp = "14:18",
            reason = "Likes pull you away 6 of 10 times while coding; 24 min expected focus lost."
        ),
        DecisionFeedItem(
            id = "dec3",
            appName = "GitHub",
            action = ActionVerdict.DELAY,
            timestamp = "13:50",
            reason = "CI pass notification delayed 15 min until current edit burst stabilizes."
        ),
        DecisionFeedItem(
            id = "dec4",
            appName = "Uber Eats",
            action = ActionVerdict.SILENT,
            timestamp = "13:30",
            reason = "Promotional notification suppressed silently without interrupting."
        )
    )

    // Top Interruption Triggers
    val interruptionTriggers = listOf(
        TriggerStat("Instagram", "Social", 14, 21.4, false),
        TriggerStat("Self-Phone Check", "Internal", 11, 7.8, true),
        TriggerStat("Slack (#random)", "Work Chat", 9, 14.2, false),
        TriggerStat("YouTube Shorts", "Video", 6, 28.5, false),
        TriggerStat("Twitter/X", "Social", 5, 16.0, false)
    )

    fun selectPersona(persona: PersonaProfile) {
        selectedPersona = persona
        when (persona.id) {
            "rahul" -> {
                simTask = "Coding"
                sessionElapsedMin = 48
                simApp = "Instagram"
                simCategory = "social"
            }
            "anika" -> {
                simTask = "Spec Review"
                sessionElapsedMin = 20
                simApp = "Slack"
                simCategory = "work"
            }
            "kabir" -> {
                simTask = "Studying"
                sessionElapsedMin = 35
                simApp = "YouTube"
                simCategory = "entertainment"
            }
        }
    }

    fun dismissDigest(item: DigestItem) {
        digestQueue.remove(item)
    }

    fun showDigestNow(item: DigestItem) {
        digestQueue.remove(item)
        decisionFeed.add(
            0,
            DecisionFeedItem(
                id = "rel_${System.currentTimeMillis()}",
                appName = item.appName,
                action = ActionVerdict.ALLOW,
                timestamp = "Just now",
                reason = "User manually requested immediate release ('Show Now')."
            )
        )
    }

    fun markImportant(item: DigestItem) {
        digestQueue.remove(item)
        decisionFeed.add(
            0,
            DecisionFeedItem(
                id = "imp_${System.currentTimeMillis()}",
                appName = item.appName,
                action = ActionVerdict.ALLOW,
                timestamp = "Just now",
                reason = "Feedback logged: Marked as Important. Sender/channel priority increased."
            )
        )
    }

    fun markNoise(item: DigestItem) {
        digestQueue.remove(item)
        decisionFeed.add(
            0,
            DecisionFeedItem(
                id = "noi_${System.currentTimeMillis()}",
                appName = item.appName,
                action = ActionVerdict.SILENT,
                timestamp = "Just now",
                reason = "Feedback logged: Marked as Noise. Future suppression propensity reinforced."
            )
        )
    }
}

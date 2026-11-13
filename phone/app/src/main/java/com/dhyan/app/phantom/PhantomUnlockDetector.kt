package com.dhyan.app.phantom

import android.content.Context
import java.util.Collections

/**
 * Representation of a phone screen unlock session.
 */
data class UnlockSession(
    val unlockTimestampMillis: Long,
    val durationMillis: Long,
    val firstForegroundPackage: String? = null
)

/**
 * Trigger classifications for unconscious muscle-memory phone checks.
 */
enum class PhantomTrigger {
    NONE,
    SHORT_UNLOCK_BURST,
    REFLEX_SOCIAL_LAUNCH
}

/**
 * Behavioral decision with plain-English reality check.
 */
data class PhantomDecision(
    val trigger: PhantomTrigger,
    val humanPrompt: String? = null
)

/**
 * Pure, deterministic evaluator for unconscious muscle-memory pickups.
 */
object PhantomUnlockEvaluator {

    const val COOLDOWN_MILLIS = 20 * 60_000L // 20 minutes between nudges
    const val BURST_WINDOW_MILLIS = 15 * 60_000L // 15-minute rolling window
    const val SHORT_UNLOCK_MAX_DURATION_MILLIS = 20_000L // 20 seconds max duration
    const val REFLEX_SOCIAL_THRESHOLD_MILLIS = 3_000L // 3 seconds from unlock
    const val RECENT_UNLOCK_GAP_MILLIS = 10 * 60_000L // 10 minutes from last lock

    val UTILITY_PACKAGE_FRAGMENTS = setOf(
        "dialer", "telecom", "contacts", "camera", "maps", "clock",
        "calculator", "authenticator", "upi", "paytm", "phonepe", "gpay",
        "bhim", "whatsapp.voip", "alarm", "wallet"
    )

    val SOCIAL_DOPAMINE_PACKAGES = setOf(
        "com.instagram.android",
        "com.google.android.youtube",
        "com.reddit.frontpage",
        "com.twitter.android",
        "com.zhiliaoapp.musically",
        "com.snapchat.android",
        "com.facebook.katana"
    )

    fun isUtilityPackage(pkg: String?): Boolean {
        if (pkg.isNullOrBlank()) return false
        val lower = pkg.lowercase()
        return UTILITY_PACKAGE_FRAGMENTS.any { lower.contains(it) }
    }

    fun isSocialPackage(pkg: String?): Boolean {
        if (pkg.isNullOrBlank()) return false
        return SOCIAL_DOPAMINE_PACKAGES.contains(pkg.lowercase())
    }

    /**
     * Deterministically evaluates whether the current unlock matches unconscious muscle-memory patterns.
     */
    fun evaluateUnlockEvent(
        priorSessions: List<UnlockSession>,
        currentUnlockTimestampMillis: Long,
        currentDurationMillis: Long,
        currentFirstForegroundPackage: String?,
        lastNudgeTimestampMillis: Long
    ): PhantomDecision {
        // 1. Enforce 20-minute cooldown between reality checks
        if (lastNudgeTimestampMillis > 0 &&
            (currentUnlockTimestampMillis - lastNudgeTimestampMillis) in 0 until COOLDOWN_MILLIS
        ) {
            return PhantomDecision(PhantomTrigger.NONE)
        }

        // 2. Ignore intentional utility pickups (calls, navigation, camera, authenticator)
        if (isUtilityPackage(currentFirstForegroundPackage)) {
            return PhantomDecision(PhantomTrigger.NONE)
        }

        // 3. Pattern B: Reflex Social Launch (< 3s from unlock and previous unlock was < 10m ago)
        val isSocial = isSocialPackage(currentFirstForegroundPackage)
        if (isSocial && currentDurationMillis <= REFLEX_SOCIAL_THRESHOLD_MILLIS) {
            val mostRecentPrior = priorSessions.maxByOrNull { it.unlockTimestampMillis }
            if (mostRecentPrior != null) {
                val priorEnd = mostRecentPrior.unlockTimestampMillis + mostRecentPrior.durationMillis
                val gapSinceLastLock = currentUnlockTimestampMillis - priorEnd
                if (gapSinceLastLock in 0 until RECENT_UNLOCK_GAP_MILLIS) {
                    return PhantomDecision(
                        trigger = PhantomTrigger.REFLEX_SOCIAL_LAUNCH,
                        humanPrompt = "Quick reflex pickup — Did you mean to open social feed, or was it muscle memory?"
                    )
                }
            }
        }

        // 4. Pattern A: Short Impulse Unlock Burst (>= 3 short unlocks <= 20s within 15m)
        val isCurrentShort = currentDurationMillis <= SHORT_UNLOCK_MAX_DURATION_MILLIS
        val currentSession = if (isCurrentShort) {
            listOf(UnlockSession(currentUnlockTimestampMillis, currentDurationMillis, currentFirstForegroundPackage))
        } else {
            emptyList()
        }

        val windowCutoff = currentUnlockTimestampMillis - BURST_WINDOW_MILLIS
        val candidateShortSessions = (priorSessions + currentSession)
            .filter { s ->
                s.unlockTimestampMillis in windowCutoff..currentUnlockTimestampMillis &&
                s.durationMillis <= SHORT_UNLOCK_MAX_DURATION_MILLIS &&
                !isUtilityPackage(s.firstForegroundPackage)
            }
            .sortedBy { it.unlockTimestampMillis }

        if (candidateShortSessions.size >= 3) {
            val firstInBurst = candidateShortSessions.first()
            val spanMins = ((currentUnlockTimestampMillis - firstInBurst.unlockTimestampMillis) / 60_000L)
                .coerceAtLeast(1L)
            val ordinal = when (candidateShortSessions.size) {
                3 -> "3rd"
                else -> "${candidateShortSessions.size}th"
            }
            return PhantomDecision(
                trigger = PhantomTrigger.SHORT_UNLOCK_BURST,
                humanPrompt = "$ordinal quick pickup in $spanMins mins — Looking for something specific, or just muscle memory?"
            )
        }

        return PhantomDecision(PhantomTrigger.NONE)
    }
}

/**
 * Stateful manager tracking recent unlock sessions and evaluating muscle-memory triggers.
 */
class PhantomUnlockDetector(private val context: Context? = null) {

    private val sessionHistory = Collections.synchronizedList(mutableListOf<UnlockSession>())
    private var lastNudgeTimestampMillis: Long = 0L

    companion object {
        private const val MAX_HISTORY_CAP = 50
        const val PREFS_NAME = "hunch_phantom_prefs"
        const val KEY_LAST_NUDGE = "last_phantom_nudge_ms"
    }

    init {
        loadLastNudge()
    }

    @Synchronized
    fun onSessionCompleted(
        unlockTimestampMillis: Long,
        durationMillis: Long,
        firstForegroundPackage: String?
    ): PhantomDecision {
        val decision = PhantomUnlockEvaluator.evaluateUnlockEvent(
            priorSessions = sessionHistory.toList(),
            currentUnlockTimestampMillis = unlockTimestampMillis,
            currentDurationMillis = durationMillis,
            currentFirstForegroundPackage = firstForegroundPackage,
            lastNudgeTimestampMillis = lastNudgeTimestampMillis
        )

        sessionHistory.add(UnlockSession(unlockTimestampMillis, durationMillis, firstForegroundPackage))
        if (sessionHistory.size > MAX_HISTORY_CAP) {
            sessionHistory.removeAt(0)
        }

        if (decision.trigger != PhantomTrigger.NONE) {
            lastNudgeTimestampMillis = unlockTimestampMillis
            saveLastNudge(lastNudgeTimestampMillis)
        }

        return decision
    }

    fun getSessionCount(): Int = sessionHistory.size

    @Synchronized
    fun clearHistory() {
        sessionHistory.clear()
        lastNudgeTimestampMillis = 0L
        saveLastNudge(0L)
    }

    private fun loadLastNudge() {
        val ctx = context ?: return
        try {
            val prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            lastNudgeTimestampMillis = prefs.getLong(KEY_LAST_NUDGE, 0L)
        } catch (_: Exception) {}
    }

    private fun saveLastNudge(ts: Long) {
        val ctx = context ?: return
        try {
            val prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putLong(KEY_LAST_NUDGE, ts).apply()
        } catch (_: Exception) {}
    }
}

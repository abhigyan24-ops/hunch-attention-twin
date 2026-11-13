package com.dhyan.app.doomscroll

/**
 * Stages of doomscroll intervention.
 */
enum class DoomscrollStage {
    NONE,
    GENTLE_NUDGE,
    FIRM_BREAKER
}

/**
 * Status evaluation of doomscroll velocity.
 */
data class DoomscrollStatus(
    val stage: DoomscrollStage,
    val accumulatedDwellSeconds: Long,
    val packageName: String? = null,
    val humanMessage: String? = null
)

/**
 * Velocity breaker tracking continuous foreground dwell time on infinite-scroll feeds.
 */
class DoomscrollVelocityBreaker {

    companion object {
        val DOOMSCROLL_PACKAGES = setOf(
            "com.instagram.android",
            "com.google.android.youtube",
            "com.reddit.frontpage",
            "com.twitter.android",
            "com.zhiliaoapp.musically",
            "com.snapchat.android"
        )

        // Thresholds in seconds
        const val HIGH_RISK_GENTLE_SECONDS = 240L // 4 minutes
        const val HIGH_RISK_FIRM_SECONDS = 480L // 8 minutes

        const val STANDARD_GENTLE_SECONDS = 480L // 8 minutes
        const val STANDARD_FIRM_SECONDS = 900L // 15 minutes

        const val BRIEF_SWITCH_TOLERANCE_MILLIS = 15_000L // 15 seconds preserves timer
        const val AWAY_RESET_MILLIS = 60_000L // 60 seconds away resets timer
    }

    private var currentPackage: String? = null
    private var accumulatedDwellMillis: Long = 0L
    private var lastSampleMillis: Long = 0L
    private var awayStartMillis: Long = 0L

    fun isDoomscrollPackage(pkg: String?): Boolean {
        if (pkg.isNullOrBlank()) return false
        return DOOMSCROLL_PACKAGES.contains(pkg.lowercase())
    }

    fun getAppDisplayName(pkg: String?): String {
        return when (pkg?.lowercase()) {
            "com.instagram.android" -> "Instagram"
            "com.google.android.youtube" -> "YouTube"
            "com.reddit.frontpage" -> "Reddit"
            "com.twitter.android" -> "X / Twitter"
            "com.zhiliaoapp.musically" -> "TikTok"
            "com.snapchat.android" -> "Snapchat"
            else -> "Social Feed"
        }
    }

    /**
     * Evaluates a foreground app sample and determines if velocity breaking is required.
     */
    @Synchronized
    fun evaluateForegroundSample(
        packageName: String,
        nowMillis: Long,
        isHighRiskContext: Boolean
    ): DoomscrollStatus {
        val isTarget = isDoomscrollPackage(packageName)

        if (isTarget) {
            val wasAway = awayStartMillis > 0L || (currentPackage != null && !isDoomscrollPackage(currentPackage))
            if (awayStartMillis > 0L) {
                val awayDuration = (nowMillis - awayStartMillis).coerceAtLeast(0L)
                if (awayDuration >= AWAY_RESET_MILLIS) {
                    accumulatedDwellMillis = 0L
                }
                awayStartMillis = 0L
            }

            // Accumulate active dwell time only if previous sample was already on a doomscroll app and not away
            if (!wasAway && lastSampleMillis > 0L && currentPackage != null && isDoomscrollPackage(currentPackage)) {
                val delta = (nowMillis - lastSampleMillis).coerceAtLeast(0L)
                // Cap single delta at 30s to prevent background jump inflation
                if (delta <= 30_000L) {
                    accumulatedDwellMillis += delta
                }
            }

            currentPackage = packageName
            lastSampleMillis = nowMillis

            val dwellSeconds = accumulatedDwellMillis / 1000L
            val appLabel = getAppDisplayName(packageName)

            val stage = if (isHighRiskContext) {
                when {
                    dwellSeconds >= HIGH_RISK_FIRM_SECONDS -> DoomscrollStage.FIRM_BREAKER
                    dwellSeconds >= HIGH_RISK_GENTLE_SECONDS -> DoomscrollStage.GENTLE_NUDGE
                    else -> DoomscrollStage.NONE
                }
            } else {
                when {
                    dwellSeconds >= STANDARD_FIRM_SECONDS -> DoomscrollStage.FIRM_BREAKER
                    dwellSeconds >= STANDARD_GENTLE_SECONDS -> DoomscrollStage.GENTLE_NUDGE
                    else -> DoomscrollStage.NONE
                }
            }

            val humanMessage = when (stage) {
                DoomscrollStage.FIRM_BREAKER -> {
                    val mins = dwellSeconds / 60
                    "${mins} mins of scrolling — Tap to close and reclaim your flow."
                }
                DoomscrollStage.GENTLE_NUDGE -> {
                    val mins = dwellSeconds / 60
                    if (isHighRiskContext) {
                        "${mins} mins on $appLabel during your focus/bedtime window — Save the rest for break?"
                    } else {
                        "${mins} mins of scrolling — Time for a quick breath?"
                    }
                }
                DoomscrollStage.NONE -> null
            }

            return DoomscrollStatus(
                stage = stage,
                accumulatedDwellSeconds = dwellSeconds,
                packageName = packageName,
                humanMessage = humanMessage
            )
        } else {
            // User switched away to a non-doomscroll app
            if (awayStartMillis == 0L) {
                // If previously on a doomscroll app, away began at lastSampleMillis
                awayStartMillis = if (lastSampleMillis > 0L && currentPackage != null && isDoomscrollPackage(currentPackage)) {
                    lastSampleMillis
                } else {
                    nowMillis
                }
            }

            val awayDuration = (nowMillis - awayStartMillis).coerceAtLeast(0L)
            if (awayDuration >= AWAY_RESET_MILLIS) {
                accumulatedDwellMillis = 0L
            }

            currentPackage = packageName
            lastSampleMillis = nowMillis

            return DoomscrollStatus(
                stage = DoomscrollStage.NONE,
                accumulatedDwellSeconds = accumulatedDwellMillis / 1000L,
                packageName = packageName,
                humanMessage = null
            )
        }
    }

    @Synchronized
    fun reset() {
        accumulatedDwellMillis = 0L
        lastSampleMillis = 0L
        awayStartMillis = 0L
        currentPackage = null
    }

    fun getAccumulatedDwellSeconds(): Long = accumulatedDwellMillis / 1000L
}

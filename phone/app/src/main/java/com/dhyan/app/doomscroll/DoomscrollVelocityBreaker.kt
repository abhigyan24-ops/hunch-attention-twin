package com.dhyan.app.doomscroll

/**
 * Stages of doomscroll intervention.
 */
enum class DoomscrollStage {
    NONE,
    GENTLE_NUDGE,
    FIRM_BREAKER,
    STUDY_MODE_ACTIVE,
    ASK_IF_STUDYING
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
 * Velocity breaker tracking continuous foreground dwell time on infinite-scroll feeds
 * with surgical dual-use study classification, Shorts swipe detection, and browser exemption.
 */
class DoomscrollVelocityBreaker {

    companion object {
        val PURE_SOCIAL_PACKAGES = setOf(
            "com.instagram.android",
            "com.zhiliaoapp.musically",
            "com.snapchat.android",
            "com.facebook.katana",
            "com.twitter.android"
        )

        val DUAL_USE_STUDY_PACKAGES = setOf(
            "com.google.android.youtube",
            "com.reddit.frontpage"
        )

        val ALLOWED_BROWSER_PACKAGES = setOf(
            "com.android.chrome",
            "org.mozilla.firefox",
            "com.brave.browser",
            "com.microsoft.emmx"
        )

        val DOOMSCROLL_PACKAGES = PURE_SOCIAL_PACKAGES + DUAL_USE_STUDY_PACKAGES

        // Thresholds in seconds
        const val HIGH_RISK_GENTLE_SECONDS = 240L // 4 minutes
        const val HIGH_RISK_FIRM_SECONDS = 480L // 8 minutes

        const val STANDARD_GENTLE_SECONDS = 480L // 8 minutes
        const val STANDARD_FIRM_SECONDS = 900L // 15 minutes

        const val BRIEF_SWITCH_TOLERANCE_MILLIS = 15_000L // 15 seconds preserves timer
        const val AWAY_RESET_MILLIS = 60_000L // 60 seconds away resets timer

        fun isEducationalTopic(title: String?): Boolean {
            if (title.isNullOrBlank()) return false
            val lower = title.lowercase()
            val keywords = listOf(
                "lecture", "tutorial", "course", "crash course", "programming", "algorithm",
                "data structures", "dsa", "system design", "distributed systems", "operating systems",
                "dbms", "compiler", "math", "calculus", "linear algebra", "probability", "physics",
                "engineering", "gate", "nptel", "mit", "stanford", "harvard", "cs50", "leetcode",
                "codeforces", "python", "rust", "typescript", "javascript", "react", "nextjs",
                "android", "kotlin", "java", "c++", "golang", "docker", "kubernetes", "aws",
                "linux", "git", "sql", "ai", "machine learning", "deep learning", "debugging",
                "documentation", "conference", "keynote", "api"
            )
            return keywords.any { lower.contains(it) }
        }
    }

    private var currentPackage: String? = null
    private var accumulatedDwellMillis: Long = 0L
    private var lastSampleMillis: Long = 0L
    private var awayStartMillis: Long = 0L
    private var studyPassUntilMillis: Long = 0L

    fun enableStudyPass(nowMillis: Long, durationMinutes: Int = 60) {
        studyPassUntilMillis = nowMillis + durationMinutes * 60_000L
    }

    fun disableStudyPass() {
        studyPassUntilMillis = 0L
    }

    fun isStudyPassActive(nowMillis: Long): Boolean {
        return nowMillis < studyPassUntilMillis
    }

    fun isDoomscrollPackage(pkg: String?): Boolean {
        if (pkg.isNullOrBlank()) return false
        val lower = pkg.lowercase()
        if (ALLOWED_BROWSER_PACKAGES.contains(lower)) return false
        return DOOMSCROLL_PACKAGES.contains(lower)
    }

    fun getAppDisplayName(pkg: String?): String {
        return when (pkg?.lowercase()) {
            "com.instagram.android" -> "Instagram"
            "com.google.android.youtube" -> "YouTube"
            "com.reddit.frontpage" -> "Reddit"
            "com.twitter.android" -> "X / Twitter"
            "com.zhiliaoapp.musically" -> "TikTok"
            "com.snapchat.android" -> "Snapchat"
            "com.facebook.katana" -> "Facebook"
            else -> "Social Feed"
        }
    }

    /**
     * Evaluates a foreground app sample and determines if velocity breaking or study mode bypass applies.
     */
    @Synchronized
    fun evaluateForegroundSample(
        packageName: String,
        nowMillis: Long,
        isHighRiskContext: Boolean,
        isLaptopCodingActive: Boolean = false,
        mediaTitle: String? = null,
        isPortraitRapidSwipe: Boolean = false
    ): DoomscrollStatus {
        val lowerPkg = packageName.lowercase()

        // 1. Web browsers are 100% exempt from doomscroll velocity breaker
        if (ALLOWED_BROWSER_PACKAGES.contains(lowerPkg)) {
            return DoomscrollStatus(
                stage = DoomscrollStage.NONE,
                accumulatedDwellSeconds = 0L,
                packageName = packageName,
                humanMessage = null
            )
        }

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

            // Accumulate active dwell time only if previous sample was already on a target app and not away
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

            // 2. Dual-use study evaluation (YouTube & Reddit)
            if (DUAL_USE_STUDY_PACKAGES.contains(lowerPkg)) {
                // Condition A: Laptop is coding, study pass is active, or educational topic detected
                if (isLaptopCodingActive || isStudyPassActive(nowMillis) || isEducationalTopic(mediaTitle)) {
                    return DoomscrollStatus(
                        stage = DoomscrollStage.STUDY_MODE_ACTIVE,
                        accumulatedDwellSeconds = dwellSeconds,
                        packageName = packageName,
                        humanMessage = "Study Mode Active — Coding or educational topic detected."
                    )
                }

                // Condition B: Mobile shorts / rapid feed swipe signature
                if (isPortraitRapidSwipe) {
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
                        else -> null
                    }

                    return DoomscrollStatus(
                        stage = stage,
                        accumulatedDwellSeconds = dwellSeconds,
                        packageName = packageName,
                        humanMessage = humanMessage
                    )
                }

                // Condition C: Ambiguous passive long-form viewing
                if (dwellSeconds >= 480L) {
                    return DoomscrollStatus(
                        stage = DoomscrollStage.ASK_IF_STUDYING,
                        accumulatedDwellSeconds = dwellSeconds,
                        packageName = packageName,
                        humanMessage = "Watching a lecture or studying? Tap to allow for 60 mins, or park for your break."
                    )
                } else {
                    return DoomscrollStatus(
                        stage = DoomscrollStage.NONE,
                        accumulatedDwellSeconds = dwellSeconds,
                        packageName = packageName,
                        humanMessage = null
                    )
                }
            }

            // 3. Pure social dopamine feeds (Instagram, TikTok, Snapchat, Facebook, Twitter)
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
                else -> null
            }

            return DoomscrollStatus(
                stage = stage,
                accumulatedDwellSeconds = dwellSeconds,
                packageName = packageName,
                humanMessage = humanMessage
            )
        } else {
            // User switched away to a non-target app
            if (awayStartMillis == 0L) {
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

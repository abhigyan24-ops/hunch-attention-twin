package com.dhyan.app.ui.pet

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.dhyan.app.twin.model.FocusState

enum class DistractionStage {
    CALM,                   // Docked peacefully at screen edge (PEEK_MODE), focused/meditating
    STAGE1_SUBTLE_GAZE,     // 0-5s: Docked at edge, eyes narrowed to FOCUSED/CURIOUS, gaze tracking
    STAGE2_EDGE_PEEK_KNOCK, // 5-12s: Slides out from edge, double-hop, "Drifting off?" pill with [Back to Task] and [✕]
    STAGE3_CENTER_INTERCEPTOR, // >12s or high distraction: Launches to center, jelly squash, POUTING, "Refocus Now"
    RETURN_TO_MARGIN        // Smoothly glides back to screen edge after accept/dismiss
}

/**
 * Manages the 4-stage distraction intervention escalation and local telemetry loop:
 *
 * 1. Stage 1 (0–5s): Subtle Gaze & Brow Alert docked at screen edge (PEEK_MODE), gaze tracking.
 * 2. Stage 2 (5–12s): Edge Peek & Knock (slides out 36dp, double-hop, "Drifting off?" micro-actions).
 * 3. Stage 3 (>12s or high distraction): Active Screen Walk-In / Center Interceptor (spring projectile,
 *    jelly squash, POUTING, 1-click "Refocus Now" action).
 * 4. Stage 4: Self-Tuning Validation Counters & Auto-Backoff (nudgesShown, nudgesAccepted, nudgesDismissed,
 *    raising cooldown multiplier by 1.5x if dismissals exceed 60% after >= 5 nudges).
 */
class PetDistractionController(private val context: Context? = null) {

    var stage by mutableStateOf(DistractionStage.CALM)
        internal set

    var nudgesShown by mutableIntStateOf(0)
        internal set

    var nudgesAccepted by mutableIntStateOf(0)
        internal set

    var nudgesDismissed by mutableIntStateOf(0)
        internal set

    var nudgeThresholdMultiplier by mutableFloatStateOf(1.0f)
        internal set

    var nudgeActive by mutableStateOf(false)
        internal set

    var nudgeType by mutableStateOf("none") // "stage2" or "stage3"
        internal set

    var nudgePrompt by mutableStateOf("Drifting off?")
        internal set

    var lastNudgeTimeMs by mutableStateOf(0L)
        internal set

    var distractionStartedMs by mutableStateOf(0L)
        internal set

    var hopCount by mutableIntStateOf(0)
        internal set

    val dismissalRate: Float
        get() = if (nudgesShown == 0) 0f else nudgesDismissed.toFloat() / nudgesShown.coerceAtLeast(1).toFloat()

    init {
        loadPersistedCounters()
    }

    /**
     * Steps the distraction controller state machine based on attention state and elapsed time.
     */
    fun step(focusState: FocusState?, isTaskActive: Boolean, nowMs: Long): DistractionStage {
        val isDistracted = focusState == FocusState.DISTRACTED || focusState == FocusState.DRIFTING

        if (!isDistracted) {
            // User is working or focused
            if (nudgeActive && (nowMs - lastNudgeTimeMs) <= 10_000L) {
                onNudgeAccepted()
            } else if (distractionStartedMs != 0L) {
                if (stage != DistractionStage.CALM && stage != DistractionStage.RETURN_TO_MARGIN) {
                    returnToMargin()
                }
            }
            distractionStartedMs = 0L
            return stage
        }

        // User is drifting or distracted
        if (distractionStartedMs == 0L) {
            distractionStartedMs = nowMs
            hopCount = 0
        }

        val elapsedSec = ((nowMs - distractionStartedMs).coerceAtLeast(0L)) / 1000f
        val stage1Cutoff = 5f * nudgeThresholdMultiplier
        val stage2Cutoff = 12f * nudgeThresholdMultiplier

        when {
            // Stage 3: High distraction or prolonged drift exceeding stage 2 cutoff
            (focusState == FocusState.DISTRACTED && elapsedSec >= 3f) || elapsedSec >= stage2Cutoff -> {
                if (stage != DistractionStage.STAGE3_CENTER_INTERCEPTOR) {
                    triggerStage3(nowMs)
                }
            }

            // Stage 2: Edge peek & knock
            elapsedSec >= stage1Cutoff -> {
                if (stage != DistractionStage.STAGE2_EDGE_PEEK_KNOCK && stage != DistractionStage.STAGE3_CENTER_INTERCEPTOR) {
                    triggerStage2(nowMs)
                }
            }

            // Stage 1: Subtle gaze & alert docked at edge
            else -> {
                if (stage == DistractionStage.CALM || stage == DistractionStage.RETURN_TO_MARGIN) {
                    stage = DistractionStage.STAGE1_SUBTLE_GAZE
                }
            }
        }

        return stage
    }

    /**
     * Stage 2: Slides out from screen edge, performs double-hop, shows "Drifting off?" pill.
     */
    fun triggerStage2(nowMs: Long) {
        if (distractionStartedMs == 0L) distractionStartedMs = nowMs
        stage = DistractionStage.STAGE2_EDGE_PEEK_KNOCK
        nudgeActive = true
        nudgeType = "stage2"
        nudgePrompt = "Drifting off?"
        lastNudgeTimeMs = nowMs
        hopCount = 0
        nudgesShown++
        evaluateAdaptiveThreshold()
        persistCounters()
    }

    /**
     * Stage 3: Launches into visible screen center, triggers jelly squash, shows "Refocus Now".
     */
    fun triggerStage3(nowMs: Long) {
        if (distractionStartedMs == 0L) distractionStartedMs = nowMs
        val wasInStage2 = stage == DistractionStage.STAGE2_EDGE_PEEK_KNOCK
        stage = DistractionStage.STAGE3_CENTER_INTERCEPTOR
        nudgeActive = true
        nudgeType = "stage3"
        nudgePrompt = "Take a breath. Refocus?"
        lastNudgeTimeMs = nowMs
        if (!wasInStage2) {
            nudgesShown++
            evaluateAdaptiveThreshold()
            persistCounters()
        }
    }

    /**
     * User accepts nudge ([Back to Task] or [Refocus Now] clicked, or task resumed within 10s).
     */
    fun onNudgeAccepted() {
        if (!nudgeActive) return
        nudgeActive = false
        nudgesAccepted++
        distractionStartedMs = 0L
        evaluateAdaptiveThreshold()
        persistCounters()
        returnToMargin()
    }

    /**
     * User dismisses nudge ([✕] clicked or pet dragged/thrown away within 4s).
     */
    fun onNudgeDismissed(nowMs: Long = System.currentTimeMillis()) {
        if (!nudgeActive) return
        nudgeActive = false
        nudgesDismissed++
        evaluateAdaptiveThreshold()
        persistCounters()
        returnToMargin()
    }

    fun returnToMargin() {
        stage = DistractionStage.RETURN_TO_MARGIN
    }

    fun onReturnCompleted() {
        stage = DistractionStage.CALM
        nudgeActive = false
        distractionStartedMs = 0L
    }

    /**
     * Auto-Backoff Rule:
     * If dismissals exceed 60% after >= 5 nudges, raise distraction threshold multiplier (1.5x cooldown).
     */
    fun evaluateAdaptiveThreshold() {
        if (nudgesShown >= 5 && dismissalRate > 0.60f && nudgesDismissed > nudgesAccepted) {
            val step = ((dismissalRate - 0.60f) / 0.10f).toInt()
            nudgeThresholdMultiplier = (1.5f + step * 0.25f).coerceIn(1.5f, 3.0f)
        } else if (nudgesShown >= 5 && dismissalRate <= 0.40f) {
            nudgeThresholdMultiplier = 1.0f
        }
    }

    private fun loadPersistedCounters() {
        val prefs = context?.getSharedPreferences("dhyan_pet_telemetry", Context.MODE_PRIVATE) ?: return
        nudgesShown = prefs.getInt("nudges_shown", 0)
        nudgesAccepted = prefs.getInt("nudges_accepted", 0)
        nudgesDismissed = prefs.getInt("nudges_dismissed", 0)
        nudgeThresholdMultiplier = prefs.getFloat("threshold_multiplier", 1.0f)
    }

    private fun persistCounters() {
        val prefs = context?.getSharedPreferences("dhyan_pet_telemetry", Context.MODE_PRIVATE) ?: return
        prefs.edit()
            .putInt("nudges_shown", nudgesShown)
            .putInt("nudges_accepted", nudgesAccepted)
            .putInt("nudges_dismissed", nudgesDismissed)
            .putFloat("threshold_multiplier", nudgeThresholdMultiplier)
            .apply()
    }

    companion object {
        @Volatile
        private var instance: PetDistractionController? = null

        fun getInstance(context: Context? = null): PetDistractionController {
            return instance ?: synchronized(this) {
                instance ?: PetDistractionController(context?.applicationContext).also { instance = it }
            }
        }
    }
}

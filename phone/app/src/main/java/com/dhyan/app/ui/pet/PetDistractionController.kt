package com.dhyan.app.ui.pet

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.dhyan.app.twin.model.FocusState

enum class DistractionStage {
    CALM,
    MILD_DRIFT,
    ACTIVE_DISTRACTION,
    RETURN_TO_MARGIN
}

/**
 * Manages the 4-stage distraction intervention escalation and local telemetry loop.
 *
 * Implements strict local validation counters (nudgesShown, nudgesAccepted, nudgesDismissed)
 * with automatic threshold backoff: when nudgesShown >= 5 and dismissals dominate (> 60%),
 * the nudgeThresholdMultiplier automatically increases (+0.25f increments up to 2.5f)
 * to avoid nagging the user.
 */
class PetDistractionController(private val context: Context? = null) {

    var stage by mutableStateOf(DistractionStage.CALM)
        private set

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

    var nudgePrompt by mutableStateOf("Ready to refocus? 🌿")
        internal set

    var lastNudgeTimeMs by mutableStateOf(0L)
        internal set

    val dismissalRate: Float
        get() = if (nudgesShown == 0) 0f else nudgesDismissed.toFloat() / nudgesShown.coerceAtLeast(1).toFloat()

    init {
        loadPersistedCounters()
    }

    /**
     * Advances the controller state machine based on current attention/session telemetry.
     */
    fun step(focusState: FocusState?, isTaskActive: Boolean, nowMs: Long): DistractionStage {
        when {
            // Calm working state: when user is focused, keep pet docked or resolve accepted nudge
            isTaskActive && (focusState == FocusState.DEEP_FOCUS || focusState == FocusState.WORKING) -> {
                if (nudgeActive && (nowMs - lastNudgeTimeMs) <= 10_000L) {
                    onNudgeAccepted()
                } else if (stage != DistractionStage.CALM && stage != DistractionStage.RETURN_TO_MARGIN) {
                    returnToMargin()
                }
            }

            // Threshold 1: Mild drift -> Curious perk-up and hop at edge
            focusState == FocusState.DRIFTING && stage == DistractionStage.CALM -> {
                stage = DistractionStage.MILD_DRIFT
            }

            // Threshold 2: Active distraction or prolonged drift -> Come up on screen
            (focusState == FocusState.DISTRACTED || (focusState == FocusState.DRIFTING && stage == DistractionStage.MILD_DRIFT)) &&
            stage != DistractionStage.ACTIVE_DISTRACTION && !nudgeActive -> {
                triggerActiveDistraction(nowMs)
            }
        }
        return stage
    }

    /**
     * Stage 2: Physically launches pet into the center-third with an interactive Nudge Bubble.
     */
    fun triggerActiveDistraction(nowMs: Long, customPrompt: String = "Ready to refocus? 🌿") {
        stage = DistractionStage.ACTIVE_DISTRACTION
        nudgeActive = true
        nudgePrompt = customPrompt
        lastNudgeTimeMs = nowMs
        nudgesShown++
        evaluateAdaptiveThreshold()
        persistCounters()
    }

    /**
     * User accepts nudge ([Refocus] tapped or task resumed within 10s).
     */
    fun onNudgeAccepted() {
        if (!nudgeActive) return
        nudgeActive = false
        nudgesAccepted++
        evaluateAdaptiveThreshold()
        persistCounters()
        returnToMargin()
    }

    /**
     * User dismisses nudge ([Dismiss ✕] tapped or pet dragged/flung away).
     */
    fun onNudgeDismissed() {
        if (!nudgeActive) return
        nudgeActive = false
        nudgesDismissed++
        evaluateAdaptiveThreshold()
        persistCounters()
        returnToMargin()
    }

    /**
     * Initiates smooth return glide to the nearest screen margin.
     */
    fun returnToMargin() {
        stage = DistractionStage.RETURN_TO_MARGIN
    }

    /**
     * Called when the return glide completes and pet docks at margin.
     */
    fun onReturnCompleted() {
        stage = DistractionStage.CALM
    }

    /**
     * Evaluates dismissal rate and adapts threshold multiplier automatically.
     */
    fun evaluateAdaptiveThreshold() {
        if (nudgesShown >= 5 && dismissalRate > 0.60f) {
            // Dismissals dominate: back off by increasing threshold multiplier (+0.25f up to 2.5f)
            val stepCount = ((dismissalRate - 0.60f) / 0.10f).toInt() + 1
            nudgeThresholdMultiplier = (1.0f + stepCount * 0.25f).coerceIn(1.0f, 2.5f)
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

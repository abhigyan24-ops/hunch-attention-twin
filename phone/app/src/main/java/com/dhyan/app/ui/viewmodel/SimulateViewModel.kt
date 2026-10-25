package com.dhyan.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import com.dhyan.app.actuator.PersonaSandbox
import com.dhyan.app.personas.PersonaCatalog
import com.dhyan.app.personas.PersonaDef
import com.dhyan.app.twin.SimOutcome
import com.dhyan.app.twin.decision.Action
import com.dhyan.app.twin.decision.NotificationFacts
import com.dhyan.app.twin.decision.PolicyMode
import com.dhyan.app.twin.decision.PolicySettings
import com.dhyan.app.twin.model.ContextSnapshot
import com.dhyan.app.twin.model.FocusState
import com.dhyan.app.twin.model.LaptopState
import com.dhyan.app.twin.model.NotificationInput
import com.dhyan.app.twin.model.PhoneState
import com.dhyan.app.twin.model.TaskSource
import com.dhyan.app.twin.model.TaskType
import com.dhyan.app.ui.model.ActionVerdict
import com.dhyan.app.ui.model.AttentionState
import com.dhyan.app.ui.model.LaptopPresence
import com.dhyan.app.ui.model.PersonaCatalog as UiPersonaCatalog
import com.dhyan.app.ui.model.PersonaProfile
import com.dhyan.app.ui.model.SimulationResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.roundToInt

data class SimulateUiState(
    val selectedPersona: PersonaProfile = UiPersonaCatalog.RAHUL,
    val isTrained: Boolean = false,
    val taskType: String = "coding",
    val attentionState: AttentionState = AttentionState.DEEP_FOCUS,
    val laptopPresence: LaptopPresence = LaptopPresence.FLOWING,
    val sessionElapsedMin: Int = 45,
    val currentHour: Int = 14,
    val fatigueBand: String = "low",
    val simApp: String = "Instagram",
    val simCategory: String = "social",
    val simSender: String = "@deepika_k",
    val simUrgency: Int = 1,
    val simIsVip: Boolean = false,
    val simulationResult: SimulationResult,
    val deltaLMean: Double = 0.0,
    val evidenceStrength: Double = 0.0,
    val isTwinLabExpanded: Boolean = false,
    val isFakePipeline: Boolean = true
)

class SimulateViewModel(
    val sandbox: PersonaSandbox = PersonaSandbox()
) : ViewModel() {

    private val _uiState = MutableStateFlow(computeInitialState())
    val uiState: StateFlow<SimulateUiState> = _uiState.asStateFlow()

    private fun computeInitialState(): SimulateUiState {
        val initial = SimulateUiState(
            simulationResult = computeSimulation(
                personaName = "Rahul",
                isTrained = false,
                task = "coding",
                category = "social",
                isVip = false,
                sessionMin = 45,
                attentionState = AttentionState.DEEP_FOCUS
            )
        )
        return initial
    }

    fun selectPersona(persona: PersonaProfile) {
        _uiState.value = _uiState.value.copy(selectedPersona = persona)
        recompute()
    }

    fun setTrained(trained: Boolean) {
        if (trained && !_uiState.value.isTrained) {
            val def = PersonaCatalog.byName(_uiState.value.selectedPersona.name.split(" ")[0])
            sandbox.fastForward(def, days = 14)
        }
        _uiState.value = _uiState.value.copy(isTrained = trained)
        recompute()
    }

    fun setTask(task: String) {
        _uiState.value = _uiState.value.copy(taskType = task)
        recompute()
    }

    fun setAttentionState(state: AttentionState) {
        _uiState.value = _uiState.value.copy(attentionState = state)
        recompute()
    }

    fun setCategory(category: String) {
        _uiState.value = _uiState.value.copy(simCategory = category)
        recompute()
    }

    fun setIsVip(isVip: Boolean) {
        _uiState.value = _uiState.value.copy(simIsVip = isVip)
        recompute()
    }

    fun setSessionElapsedMin(minutes: Int) {
        _uiState.value = _uiState.value.copy(sessionElapsedMin = minutes)
        recompute()
    }

    fun toggleTwinLab(expanded: Boolean) {
        _uiState.value = _uiState.value.copy(isTwinLabExpanded = expanded)
    }

    private fun recompute() {
        val s = _uiState.value
        val res = computeSimulation(
            personaName = s.selectedPersona.name.split(" ")[0],
            isTrained = s.isTrained,
            task = s.taskType,
            category = s.simCategory,
            isVip = s.simIsVip,
            sessionMin = s.sessionElapsedMin,
            attentionState = s.attentionState
        )
        val deltaL = res.expectedLostShowMin - res.expectedLostSuppressMin
        _uiState.value = s.copy(
            simulationResult = res,
            deltaLMean = deltaL
        )
    }

    private fun computeSimulation(
        personaName: String,
        isTrained: Boolean,
        task: String,
        category: String,
        isVip: Boolean,
        sessionMin: Int,
        attentionState: AttentionState
    ): SimulationResult {
        // ── Focus → fatigue band: deeper focus = lower fatigue; distracted = high fatigue
        val fatigueBand = when (attentionState) {
            AttentionState.DEEP_FOCUS  -> "low"
            AttentionState.WORKING     -> "low"
            AttentionState.DRIFTING    -> "med"
            AttentionState.RECOVERING  -> "med"
            AttentionState.DISTRACTED  -> "high"
            AttentionState.FATIGUED    -> "high"
        }

        // ── Session depth → hour band proxy (deeper session = later in the band)
        val hourBand = when {
            sessionMin < 20  -> "morning"
            sessionMin < 60  -> "afternoon"
            else             -> "evening"
        }

        // ── Focus multiplier: DEEP_FOCUS has lowest pickup propensity;
        //    DISTRACTED has highest (user is already unfocused → more susceptible)
        val focusPickupMultiplier = when (attentionState) {
            AttentionState.DEEP_FOCUS  -> 0.30   // very hard to pull away
            AttentionState.WORKING     -> 0.55
            AttentionState.DRIFTING    -> 0.75
            AttentionState.RECOVERING  -> 0.65
            AttentionState.DISTRACTED  -> 0.92   // already distracted – high susceptibility
            AttentionState.FATIGUED    -> 0.85
        }

        // ── Category hazard: social/entertainment are highest, work lower, promo lowest
        val categoryHazard = when (category.lowercase()) {
            "social"        -> 1.0
            "entertainment" -> 1.15
            "messaging"     -> 0.70
            "work"          -> 0.45
            "promo"         -> 0.25
            else            -> 0.60
        }

        // ── Session depth multiplier: longer session = higher cognitive load = more susceptible
        val sessionDepthMultiplier = 1.0 + (sessionMin.coerceIn(5, 120) - 5) / 115.0 * 0.5

        val focusEnum = when (attentionState) {
            AttentionState.DEEP_FOCUS  -> FocusState.DEEP_FOCUS
            AttentionState.WORKING     -> FocusState.WORKING
            AttentionState.DRIFTING    -> FocusState.DRIFTING
            AttentionState.DISTRACTED  -> FocusState.DISTRACTED
            AttentionState.RECOVERING  -> FocusState.RECOVERING
            AttentionState.FATIGUED    -> FocusState.BREAK
        }
        val taskEnum = when (task.lowercase()) {
            "coding"   -> TaskType.CODING
            "studying" -> TaskType.STUDYING
            "writing"  -> TaskType.WRITING
            "gaming"   -> TaskType.GAMING
            else       -> TaskType.BROWSING
        }

        // ── Task cost: high-focus tasks lose more time per interruption
        val taskCostMultiplier = when (task.lowercase()) {
            "coding"   -> 1.35
            "studying" -> 1.20
            "writing"  -> 1.10
            "gaming"   -> 0.70
            else       -> 0.85
        }

        val snapshot = ContextSnapshot(
            ts = System.currentTimeMillis(),
            focusState = focusEnum,
            taskType = taskEnum,
            taskSource = TaskSource.DECLARED,
            confidence = 0.85,
            hourBand = hourBand,
            minutesInState = sessionMin.toDouble(),
            fatigueBand = fatigueBand,
            recentInterrupts30m = 0,
            laptopState = LaptopState.FLOWING,
            phoneState = PhoneState.SCREEN_OFF,
            secondScreenOverlap = true,
            declaredSession = null
        )

        val notifInput = NotificationInput(
            appCat = category,
            notifCat = if (isVip) "vip_alert" else "post",
            isVip = isVip,
            urgencyScore = if (isVip) 1.0 else 0.2,
            repeatCount5m = 0
        )

        val outcome = sandbox.simulate(snapshot, notifInput, seed = 42L)
        val (alpha, beta) = sandbox.resolvePickupBeta(snapshot, notifInput)

        return when (outcome) {
            is SimOutcome.Ready -> {
                val r = outcome.result

                // ── Scale raw twin output by focus + category + session multipliers
                // The twin returns prior-based uniform estimates; multipliers make the
                // UI reflect the actual factors that change attention cost.
                val combinedMultiplier = focusPickupMultiplier * categoryHazard * sessionDepthMultiplier * taskCostMultiplier
                val scaledShowMean   = (r.show.mean   * combinedMultiplier).coerceIn(0.5, 60.0)
                val scaledSuppMean   = (r.suppress.mean * 0.18 * (1.0 + focusPickupMultiplier * 0.3)).coerceIn(0.3, 8.0)
                val scaledPShow      = (r.show.pLossGt1Min * focusPickupMultiplier * categoryHazard).coerceIn(0.05, 0.98)
                val scaledPSuppress  = (r.suppress.pLossGt1Min * 0.15).coerceIn(0.02, 0.25)
                val scaledDeltaL     = scaledShowMean - scaledSuppMean
                val scaledCiLow      = (r.deltaL80SimIntervalLow  * combinedMultiplier).coerceAtLeast(0.0)
                val scaledCiHigh     = (r.deltaL80SimIntervalHigh * combinedMultiplier).coerceAtLeast(scaledCiLow + 1.0)

                val verdict = when {
                    isVip               -> ActionVerdict.ALLOW
                    scaledDeltaL > 8.0  -> ActionVerdict.DIGEST
                    scaledDeltaL > 3.0  -> ActionVerdict.DELAY
                    category == "promo" -> ActionVerdict.SILENT
                    else                -> ActionVerdict.ALLOW
                }

                val focusLabel = when (attentionState) {
                    AttentionState.DEEP_FOCUS  -> "deep focus (${sessionMin}m session)"
                    AttentionState.WORKING     -> "active work session"
                    AttentionState.DRIFTING    -> "drifting attention"
                    AttentionState.DISTRACTED  -> "already distracted"
                    AttentionState.RECOVERING  -> "recovering focus"
                    AttentionState.FATIGUED    -> "fatigued state"
                }
                val explanation = when {
                    isVip ->
                        "ALLOW: VIP contact override — never-hold safety rule bypasses all suppression."
                    verdict == ActionVerdict.SILENT ->
                        "SILENT: Promotional content suppressed — zero focus value, no interruption justified."
                    verdict == ActionVerdict.DIGEST ->
                        "DIGEST: Net ΔL = ${String.format("%.1f", scaledDeltaL)} min during $focusLabel. " +
                        "$category pickup P=${String.format("%.0f", scaledPShow * 100)}% → batched for next break."
                    verdict == ActionVerdict.DELAY ->
                        "DELAY: Moderate disruption (ΔL = ${String.format("%.1f", scaledDeltaL)} min) during $focusLabel. " +
                        "Snoozed until current block ends."
                    else ->
                        "ALLOW: Predicted focus cost (ΔL = ${String.format("%.1f", scaledDeltaL)} min) is below hold threshold. " +
                        "Low pickup propensity in $focusLabel."
                }

                SimulationResult(
                    pDistractionShow     = scaledPShow,
                    pDistractionSuppress = scaledPSuppress,
                    expectedLostShowMin  = scaledShowMean,
                    ciLowShowMin         = scaledCiLow,
                    ciHighShowMin        = scaledCiHigh,
                    expectedLostSuppressMin = scaledSuppMean,
                    verdict     = verdict,
                    explanation = explanation,
                    betaAlpha   = if (isTrained) alpha * 3.2 else alpha,
                    betaBeta    = if (isTrained) beta  * 1.8 else beta,
                    lognormMu    = when (category) { "social" -> 2.9; "entertainment" -> 3.1; "work" -> 1.8; else -> 2.2 },
                    lognormSigma = 0.6 + fatigueBand.let { if (it == "high") 0.15 else 0.0 },
                    updateCount  = if (isTrained) 140 else 0
                )
            }
            is SimOutcome.NotReady -> {
                // Fail-open: produce a meaningful cold-prior display rather than flat zeros
                val pShow = (focusPickupMultiplier * categoryHazard * 0.6).coerceIn(0.05, 0.95)
                val lostShow = (focusPickupMultiplier * categoryHazard * sessionDepthMultiplier * taskCostMultiplier * 12.0).coerceIn(1.0, 45.0)
                val lostSupp = (lostShow * 0.12).coerceIn(0.3, 4.0)
                val deltaL = lostShow - lostSupp

                val verdict = when {
                    isVip           -> ActionVerdict.ALLOW
                    deltaL > 8.0    -> ActionVerdict.DIGEST
                    deltaL > 3.0    -> ActionVerdict.DELAY
                    category == "promo" -> ActionVerdict.SILENT
                    else            -> ActionVerdict.ALLOW
                }

                SimulationResult(
                    pDistractionShow     = pShow,
                    pDistractionSuppress = (pShow * 0.15).coerceIn(0.02, 0.20),
                    expectedLostShowMin  = lostShow,
                    ciLowShowMin         = lostShow * 0.4,
                    ciHighShowMin        = lostShow * 1.7,
                    expectedLostSuppressMin = lostSupp,
                    verdict     = verdict,
                    explanation = "Using cold priors — ${verdict.name}: ΔL ≈ ${String.format("%.1f", deltaL)} min estimated from focus context.",
                    betaAlpha   = 1.0 + focusPickupMultiplier * 2,
                    betaBeta    = 1.0 + (1.0 - focusPickupMultiplier) * 3,
                    lognormMu    = 2.4,
                    lognormSigma = 0.6,
                    updateCount  = 0
                )
            }
        }
    }
}

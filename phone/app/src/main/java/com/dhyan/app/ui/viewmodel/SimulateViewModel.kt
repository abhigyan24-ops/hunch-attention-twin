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
        val focusEnum = when (attentionState) {
            AttentionState.DEEP_FOCUS -> FocusState.DEEP_FOCUS
            AttentionState.WORKING -> FocusState.WORKING
            AttentionState.DRIFTING -> FocusState.DRIFTING
            AttentionState.DISTRACTED -> FocusState.DISTRACTED
            AttentionState.RECOVERING -> FocusState.RECOVERING
            AttentionState.FATIGUED -> FocusState.BREAK
        }
        val taskEnum = when (task.lowercase()) {
            "coding" -> TaskType.CODING
            "studying" -> TaskType.STUDYING
            "writing" -> TaskType.WRITING
            "gaming" -> TaskType.GAMING
            else -> TaskType.BROWSING
        }

        val snapshot = ContextSnapshot(
            ts = System.currentTimeMillis(),
            focusState = focusEnum,
            taskType = taskEnum,
            taskSource = TaskSource.DECLARED,
            confidence = 0.85,
            hourBand = "afternoon",
            minutesInState = sessionMin.toDouble(),
            fatigueBand = "low",
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
                val verdict = if (isVip) {
                    ActionVerdict.ALLOW
                } else if (r.deltaLMean > 5.0) {
                    ActionVerdict.DIGEST
                } else if (r.deltaLMean > 1.5) {
                    ActionVerdict.DELAY
                } else {
                    ActionVerdict.ALLOW
                }
                val explanation = if (isVip) {
                    "ALLOW: VIP contact override bypassed attention hold."
                } else if (verdict == ActionVerdict.DIGEST) {
                    "DIGEST: Net focus loss ΔL = ${String.format("%.1f", r.deltaLMean)} min exceeds hold threshold during deep focus."
                } else if (verdict == ActionVerdict.DELAY) {
                    "DELAY: Moderate disruption hazard. Snoozed until current work block ends."
                } else {
                    "ALLOW: Predicted distraction cost is lower than importance value."
                }

                SimulationResult(
                    pDistractionShow = r.show.pLossGt1Min,
                    pDistractionSuppress = r.suppress.pLossGt1Min,
                    expectedLostShowMin = r.show.mean,
                    ciLowShowMin = r.deltaL80SimIntervalLow.coerceAtLeast(0.0),
                    ciHighShowMin = r.deltaL80SimIntervalHigh,
                    expectedLostSuppressMin = r.suppress.mean,
                    verdict = verdict,
                    explanation = explanation,
                    betaAlpha = alpha,
                    betaBeta = beta,
                    lognormMu = 2.4,
                    lognormSigma = 0.6,
                    updateCount = if (isTrained) 140 else 0
                )
            }
            is SimOutcome.NotReady -> {
                SimulationResult(
                    pDistractionShow = 0.5,
                    pDistractionSuppress = 0.1,
                    expectedLostShowMin = 10.0,
                    ciLowShowMin = 5.0,
                    ciHighShowMin = 15.0,
                    expectedLostSuppressMin = 2.0,
                    verdict = ActionVerdict.ALLOW,
                    explanation = "ALLOW: Fail-open rule (simulator not ready).",
                    betaAlpha = 1.0,
                    betaBeta = 1.0,
                    lognormMu = 2.0,
                    lognormSigma = 0.5,
                    updateCount = 0
                )
            }
        }
    }
}

package com.dhyan.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dhyan.app.actuator.InMemoryParamPersistence
import com.dhyan.app.personas.GeneratorConfig
import com.dhyan.app.personas.LoggingPolicy
import com.dhyan.app.personas.PersonaCatalog
import com.dhyan.app.personas.World
import com.dhyan.app.personas.world.HistoryGenerator
import com.dhyan.app.twin.SimOutcome
import com.dhyan.app.twin.TwinEngine
import com.dhyan.app.twin.context.ContextBucketer
import com.dhyan.app.twin.context.FatigueIndex
import com.dhyan.app.twin.context.HourBand
import com.dhyan.app.twin.decision.Action
import com.dhyan.app.twin.decision.DecisionPipeline
import com.dhyan.app.twin.decision.ImportanceModel
import com.dhyan.app.twin.decision.ModelImportanceProvider
import com.dhyan.app.twin.decision.NotificationFacts
import com.dhyan.app.twin.decision.PolicyMode
import com.dhyan.app.twin.decision.PolicySettings
import com.dhyan.app.twin.decision.TwinEngineLossPredictor
import com.dhyan.app.twin.episodes.EpisodeBuilder
import com.dhyan.app.twin.episodes.EpisodeCloser
import com.dhyan.app.twin.episodes.EventStreamAdapter
import com.dhyan.app.twin.model.ContextSnapshot
import com.dhyan.app.twin.model.FocusState
import com.dhyan.app.twin.model.LaptopState
import com.dhyan.app.twin.model.NotificationInput
import com.dhyan.app.twin.model.PhoneState
import com.dhyan.app.twin.model.TaskSource
import com.dhyan.app.twin.model.TaskType
import com.dhyan.app.twin.model.TwinConfig
import com.dhyan.app.twin.params.InMemoryParamStore
import com.dhyan.app.ui.model.ActionVerdict
import com.dhyan.app.ui.model.AttentionState
import com.dhyan.app.ui.model.LaptopPresence
import com.dhyan.app.ui.model.PersonaCatalog as UiPersonaCatalog
import com.dhyan.app.ui.model.PersonaProfile
import com.dhyan.app.ui.model.SimulationResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.concurrent.ConcurrentHashMap

data class AbductionReplayState(
    val isExpanded: Boolean = false,
    val abductedShockU: String = "U = (ε_attend=+0.32, ε_excursion=+0.15, ε_resume=+0.21)",
    val shockAttend: Double = 0.32,
    val shockExcursion: Double = 0.15,
    val shockResume: Double = 0.21,
    val counterfactualSavingsMin: Double = 14.5
)

data class SimulateUiState(
    val selectedPersona: PersonaProfile = UiPersonaCatalog.RAHUL,
    val isTrained: Boolean = false,
    val isReplaying: Boolean = false,
    val replayProgress: Float = 0f,
    val taskType: String = "coding",
    val attentionState: AttentionState = AttentionState.DEEP_FOCUS,
    val laptopPresence: LaptopPresence = LaptopPresence.FLOWING,
    val hoursWorked: Double = 4.0,
    val minutesInState: Int = 45,
    val currentHour: Int = 14,
    val fatigueBand: String = "med",
    val fatigueIndex: Double = 0.33,
    val hourBand: String = "afternoon",
    val simApp: String = "Instagram",
    val simCategory: String = "social",
    val simNotifCat: String = "social_notif",
    val simSender: String = "@deepika_k",
    val simUrgency: Double = 0.2,
    val simRepeatCount: Int = 0,
    val simIsVip: Boolean = false,
    val simulationResult: SimulationResult,
    val deltaLMean: Double = 0.0,
    val evidenceStrength: Double = 0.0,
    val nEff: Double = 0.0,
    val modelVersion: Long = 0L,
    val provenance: String = "Simulated by Twin [Cold Population Priors]",
    val isTwinLabExpanded: Boolean = false,
    val isFailOpen: Boolean = false,
    val reasonCode: String = "cost_selected",
    val priorAlpha: Double = 3.0,
    val priorBeta: Double = 4.0,
    // Upgraded Attention Twin Causal Features
    val y1ExcursionMin: Double = 12.5,
    val y1ResumeMin: Double = 6.2,
    val y1TotalLostMin: Double = 18.7,
    val y0ExcursionMin: Double = 0.0,
    val y0ResumeMin: Double = 0.0,
    val y0TotalLostMin: Double = 0.5,
    val netFocusSavedMin: Double = 18.2,
    val ciLowMin: Double = 8.4,
    val ciHighMin: Double = 26.8,
    val abductionReplay: AbductionReplayState = AbductionReplayState()
)

class SimulateViewModel : ViewModel() {

    companion object {
        /** In-memory cache for Stage 4 replay-trained parameter stores. Never writes to Room. */
        val personaStoreCache = ConcurrentHashMap<String, InMemoryParamStore>()

        /** Isolated cold parameter store with zero disk persistence. */
        val coldStore = InMemoryParamStore(persistence = InMemoryParamPersistence(), flushDispatcher = Dispatchers.Unconfined).apply {
            runBlocking { load() }
        }

        suspend fun trainPersona(personaName: String, onProgress: (Float) -> Unit = {}): InMemoryParamStore {
            personaStoreCache[personaName]?.let { return it }
            val personaDef = PersonaCatalog.byName(personaName)
            val config = GeneratorConfig(seed = 42L, days = 14, world = World.MATCHED, loggingPolicy = LoggingPolicy.ShowAll)
            val generated = HistoryGenerator.generate(personaDef, config)
            val store = InMemoryParamStore(persistence = InMemoryParamPersistence(), flushDispatcher = Dispatchers.Unconfined)
            store.load()
            val closer = EpisodeCloser(store)
            val builder = EpisodeBuilder(windowMin = 30.0, onEpisodeClosed = { ep -> closer.onEpisodeClosed(ep) })
            val tz = java.util.TimeZone.getTimeZone(personaDef.defaults.timezone)
            val adapter = EventStreamAdapter(builder, tz)
            val total = generated.events.size
            for (i in 0 until total) {
                val ev = generated.events[i]
                adapter.processEvent(ev.type, ev.ts, ev.payload.values)
                if (i % 500 == 0 && total > 0) {
                    onProgress(i.toFloat() / total)
                }
            }
            if (generated.events.isNotEmpty()) {
                builder.checkTimeout(generated.events.last().ts + 35 * 60 * 1000L)
            }
            onProgress(1.0f)
            personaStoreCache[personaName] = store
            return store
        }
    }

    private val _uiState = MutableStateFlow(computeInitialState())
    val uiState: StateFlow<SimulateUiState> = _uiState.asStateFlow()

    private fun computeInitialState(): SimulateUiState {
        val fIndex = FatigueIndex.compute(hoursWorked = 4.0, switchRateZ = 0.0, stallRatio = 0.0)
        val fBand = FatigueIndex.band(fIndex)
        val hBand = HourBand.from(14).wire

        val initialOutcome = runTwinSimulation(
            store = coldStore,
            personaName = "Rahul",
            isTrained = false,
            task = "coding",
            category = "social",
            notifCat = "social_notif",
            isVip = false,
            urgency = 0.2,
            repeatCount = 0,
            hoursWorked = 4.0,
            minutesInState = 45,
            hourBand = hBand,
            fatigueBand = fBand,
            attentionState = AttentionState.DEEP_FOCUS
        )

        return SimulateUiState(
            simulationResult = initialOutcome.simResult,
            deltaLMean = initialOutcome.simResult.expectedLostShowMin - initialOutcome.simResult.expectedLostSuppressMin,
            evidenceStrength = initialOutcome.evidenceStrength,
            nEff = initialOutcome.nEff,
            modelVersion = initialOutcome.modelVersion,
            provenance = initialOutcome.provenance,
            isFailOpen = initialOutcome.isFailOpen,
            reasonCode = initialOutcome.reasonCode,
            priorAlpha = initialOutcome.priorAlpha,
            priorBeta = initialOutcome.priorBeta,
            fatigueIndex = fIndex,
            fatigueBand = fBand,
            hourBand = hBand,
            y1ExcursionMin = initialOutcome.y1ExcursionMin,
            y1ResumeMin = initialOutcome.y1ResumeMin,
            y1TotalLostMin = initialOutcome.y1TotalLostMin,
            y0ExcursionMin = initialOutcome.y0ExcursionMin,
            y0ResumeMin = initialOutcome.y0ResumeMin,
            y0TotalLostMin = initialOutcome.y0TotalLostMin,
            netFocusSavedMin = initialOutcome.netFocusSavedMin,
            ciLowMin = initialOutcome.ciLowMin,
            ciHighMin = initialOutcome.ciHighMin,
            abductionReplay = initialOutcome.abductionReplay
        )
    }

    fun selectPersona(persona: PersonaProfile) {
        _uiState.value = _uiState.value.copy(selectedPersona = persona)
        if (_uiState.value.isTrained) {
            loadTrainedPersonaAndRecompute(persona.name.split(" ")[0])
        } else {
            recompute()
        }
    }

    fun setTrained(trained: Boolean) {
        _uiState.value = _uiState.value.copy(isTrained = trained)
        if (trained) {
            val name = _uiState.value.selectedPersona.name.split(" ")[0]
            loadTrainedPersonaAndRecompute(name)
        } else {
            recompute()
        }
    }

    private fun loadTrainedPersonaAndRecompute(personaName: String) {
        val cached = personaStoreCache[personaName]
        if (cached != null) {
            recompute(storeOverride = cached)
            return
        }
        // Train in background off main thread with progress
        _uiState.value = _uiState.value.copy(isReplaying = true, replayProgress = 0f)
        viewModelScope.launch(Dispatchers.Default) {
            val store = trainPersona(personaName) { progress ->
                _uiState.value = _uiState.value.copy(replayProgress = progress)
            }
            _uiState.value = _uiState.value.copy(isReplaying = false, replayProgress = 1f)
            recompute(storeOverride = store)
        }
    }

    fun setTask(task: String) {
        _uiState.value = _uiState.value.copy(taskType = task)
        recompute()
    }

    fun setAttentionState(state: AttentionState) {
        _uiState.value = _uiState.value.copy(attentionState = state)
        recompute()
    }

    fun setCategory(category: String, notifCat: String) {
        _uiState.value = _uiState.value.copy(simCategory = category, simNotifCat = notifCat)
        recompute()
    }

    fun setIsVip(isVip: Boolean) {
        _uiState.value = _uiState.value.copy(simIsVip = isVip)
        recompute()
    }

    fun setHoursWorked(hours: Double) {
        val fIndex = FatigueIndex.compute(hoursWorked = hours, switchRateZ = 0.0, stallRatio = 0.0)
        val fBand = FatigueIndex.band(fIndex)
        _uiState.value = _uiState.value.copy(
            hoursWorked = hours,
            fatigueIndex = fIndex,
            fatigueBand = fBand
        )
        recompute()
    }

    fun setMinutesInState(minutes: Int) {
        _uiState.value = _uiState.value.copy(minutesInState = minutes)
        recompute()
    }

    fun setCurrentHour(hour: Int) {
        val hBand = HourBand.from(hour).wire
        _uiState.value = _uiState.value.copy(currentHour = hour, hourBand = hBand)
        recompute()
    }

    fun setUrgency(urgency: Double) {
        _uiState.value = _uiState.value.copy(simUrgency = urgency)
        recompute()
    }

    fun setRepeatCount(repeatCount: Int) {
        _uiState.value = _uiState.value.copy(simRepeatCount = repeatCount)
        recompute()
    }

    fun toggleTwinLab(expanded: Boolean) {
        _uiState.value = _uiState.value.copy(isTwinLabExpanded = expanded)
    }

    fun toggleAbductionDrawer(expanded: Boolean) {
        _uiState.value = _uiState.value.copy(
            abductionReplay = _uiState.value.abductionReplay.copy(isExpanded = expanded)
        )
    }

    private fun recompute(storeOverride: InMemoryParamStore? = null) {
        val s = _uiState.value
        val name = s.selectedPersona.name.split(" ")[0]
        val activeStore = storeOverride ?: if (s.isTrained) personaStoreCache[name] ?: coldStore else coldStore

        val outcome = runTwinSimulation(
            store = activeStore,
            personaName = name,
            isTrained = s.isTrained && activeStore != coldStore,
            task = s.taskType,
            category = s.simCategory,
            notifCat = s.simNotifCat,
            isVip = s.simIsVip,
            urgency = s.simUrgency,
            repeatCount = s.simRepeatCount,
            hoursWorked = s.hoursWorked,
            minutesInState = s.minutesInState,
            hourBand = s.hourBand,
            fatigueBand = s.fatigueBand,
            attentionState = s.attentionState
        )

        val deltaL = outcome.simResult.expectedLostShowMin - outcome.simResult.expectedLostSuppressMin
        _uiState.value = s.copy(
            simulationResult = outcome.simResult,
            deltaLMean = deltaL,
            evidenceStrength = outcome.evidenceStrength,
            nEff = outcome.nEff,
            modelVersion = outcome.modelVersion,
            provenance = outcome.provenance,
            isFailOpen = outcome.isFailOpen,
            reasonCode = outcome.reasonCode,
            priorAlpha = outcome.priorAlpha,
            priorBeta = outcome.priorBeta,
            y1ExcursionMin = outcome.y1ExcursionMin,
            y1ResumeMin = outcome.y1ResumeMin,
            y1TotalLostMin = outcome.y1TotalLostMin,
            y0ExcursionMin = outcome.y0ExcursionMin,
            y0ResumeMin = outcome.y0ResumeMin,
            y0TotalLostMin = outcome.y0TotalLostMin,
            netFocusSavedMin = outcome.netFocusSavedMin,
            ciLowMin = outcome.ciLowMin,
            ciHighMin = outcome.ciHighMin,
            abductionReplay = outcome.abductionReplay
        )
    }

    private data class TwinRunOutcome(
        val simResult: SimulationResult,
        val evidenceStrength: Double,
        val nEff: Double,
        val modelVersion: Long,
        val provenance: String,
        val isFailOpen: Boolean,
        val reasonCode: String,
        val priorAlpha: Double,
        val priorBeta: Double,
        val y1ExcursionMin: Double,
        val y1ResumeMin: Double,
        val y1TotalLostMin: Double,
        val y0ExcursionMin: Double,
        val y0ResumeMin: Double,
        val y0TotalLostMin: Double,
        val netFocusSavedMin: Double,
        val ciLowMin: Double,
        val ciHighMin: Double,
        val abductionReplay: AbductionReplayState
    )

    private fun runTwinSimulation(
        store: InMemoryParamStore,
        personaName: String,
        isTrained: Boolean,
        task: String,
        category: String,
        notifCat: String,
        isVip: Boolean,
        urgency: Double,
        repeatCount: Int,
        hoursWorked: Double,
        minutesInState: Int,
        hourBand: String,
        fatigueBand: String,
        attentionState: AttentionState
    ): TwinRunOutcome {
        val nowMs = System.currentTimeMillis()

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
            ts = nowMs,
            focusState = focusEnum,
            taskType = taskEnum,
            taskSource = TaskSource.DECLARED,
            confidence = 0.85,
            hourBand = hourBand,
            minutesInState = minutesInState.toDouble(),
            fatigueBand = fatigueBand,
            recentInterrupts30m = 0,
            laptopState = LaptopState.FLOWING,
            phoneState = PhoneState.SCREEN_OFF,
            secondScreenOverlap = true,
            declaredSession = null
        )

        val notifInput = NotificationInput(
            appCat = category,
            notifCat = notifCat,
            isVip = isVip,
            urgencyScore = urgency,
            repeatCount5m = repeatCount
        )

        // 1. Run raw TwinEngine simulation (pure physics, no UI multipliers)
        val engine = TwinEngine(store, TwinConfig.default(), clock = { nowMs })
        val simOutcome = engine.simulate(snapshot, notifInput, seed = 42L)

        // 2. Resolve ModelSnapshot for Twin Lab posterior display
        val bucket = ContextBucketer.bucket(snapshot, notifInput)
        val baseline = ContextBucketer.baselineBucket(snapshot)
        val resolvedSnapshot = store.resolve(bucket, baseline, notifInput)
        val (priorAlpha, priorBeta) = com.dhyan.app.twin.params.PriorTable.pickupPrior(category, notifCat, fatigueBand)

        // 3. Evaluate DecisionPipeline for verdict, explanation & safety (Amendment 3)
        val facts = NotificationFacts(
            keyHash = "sim_${category}_${notifCat}",
            postedTsMs = nowMs,
            pkg = when (category) {
                "social" -> "com.instagram.android"
                "communication" -> "com.whatsapp"
                "shopping" -> "com.amazon.mShop.android.shopping"
                else -> "com.google.android.apps.docs"
            },
            appCat = category,
            notifCat = notifCat,
            senderHash = "sim_sender_hash",
            isVip = isVip,
            urgencyScore = urgency,
            hasReplyAction = category == "communication",
            isMessagingStyle = category == "communication",
            repeatCount5m = repeatCount,
            isOtpLike = false,
            hasEmergencyKeyword = false
        )

        val lossPredictor = TwinEngineLossPredictor(store)
        val importanceProvider = ModelImportanceProvider(ImportanceModel())
        val pipeline = DecisionPipeline(lossPredictor, importanceProvider)
        val decision = pipeline.decide(facts, snapshot, PolicySettings(mode = PolicyMode.LIVE, seed = 42L), nowMs)

        return when (simOutcome) {
            is SimOutcome.Ready -> {
                val r = simOutcome.result
                val verdict = when (decision.action) {
                    Action.ALLOW -> ActionVerdict.ALLOW
                    Action.DELAY -> ActionVerdict.DELAY
                    Action.DIGEST -> ActionVerdict.DIGEST
                    Action.SILENT -> ActionVerdict.SILENT
                }

                val provenanceText = if (isTrained) {
                    "Simulated by Twin [Stage 4 Replay-Trained: $personaName]"
                } else {
                    "Simulated by Twin [Cold Population Priors]"
                }

                val simResult = SimulationResult(
                    pDistractionShow = r.show.pLossGt1Min,
                    pDistractionSuppress = r.suppress.pLossGt1Min,
                    expectedLostShowMin = r.show.mean,
                    ciLowShowMin = r.deltaL80SimIntervalLow,
                    ciHighShowMin = r.deltaL80SimIntervalHigh,
                    expectedLostSuppressMin = r.suppress.mean,
                    verdict = verdict,
                    explanation = decision.explanation,
                    betaAlpha = resolvedSnapshot?.pickupAlpha ?: 1.0,
                    betaBeta = resolvedSnapshot?.pickupBeta ?: 1.0,
                    lognormMu = resolvedSnapshot?.dwellMeanLog?.get("${category}/other")
                        ?: resolvedSnapshot?.dwellMeanLog?.values?.firstOrNull() ?: 2.2,
                    lognormSigma = resolvedSnapshot?.dwellSigma?.get("${category}/other")
                        ?: resolvedSnapshot?.dwellSigma?.values?.firstOrNull() ?: 0.6,
                    updateCount = if (isTrained) 140 else 0
                )

                val expectedLostShow = r.show.mean
                val expectedLostSuppress = r.suppress.mean
                val rawExcursion = kotlin.math.exp(simResult.lognormMu + 0.5 * simResult.lognormSigma * simResult.lognormSigma)
                val catMultiplier = when (category) {
                    "social" -> 1.2
                    "shopping" -> 0.9
                    "communication" -> 0.6
                    else -> 0.4
                }
                val y1Excursion = (rawExcursion * catMultiplier).coerceIn(1.0, 45.0)
                val y1Resume = ((expectedLostShow - y1Excursion).coerceAtLeast(0.5) * (1.0 + (hoursWorked / 10.0) * 0.4)).coerceIn(0.5, 20.0)
                val y1Total = y1Excursion + y1Resume
                val y0Total = expectedLostSuppress.coerceIn(0.0, 5.0)
                val netSaved = (y1Total - y0Total).coerceAtLeast(0.0)

                val epsAttend = (urgency - 0.5) * 0.8
                val epsExcursion = (simResult.lognormMu - 2.2) * 0.5
                val epsResume = ((hoursWorked - 4.0) / 6.0) * 0.4
                val shockStr = String.format(java.util.Locale.US, "U = (ε_attend=%+.2f, ε_excursion=%+.2f, ε_resume=%+.2f)", epsAttend, epsExcursion, epsResume)
                val abduction = AbductionReplayState(
                    isExpanded = false,
                    abductedShockU = shockStr,
                    shockAttend = epsAttend,
                    shockExcursion = epsExcursion,
                    shockResume = epsResume,
                    counterfactualSavingsMin = netSaved
                )

                TwinRunOutcome(
                    simResult = simResult,
                    evidenceStrength = r.evidenceStrength,
                    nEff = r.nEff,
                    modelVersion = r.modelVersion,
                    provenance = provenanceText,
                    isFailOpen = decision.failedOpen,
                    reasonCode = decision.reasonCode,
                    priorAlpha = priorAlpha,
                    priorBeta = priorBeta,
                    y1ExcursionMin = y1Excursion,
                    y1ResumeMin = y1Resume,
                    y1TotalLostMin = y1Total,
                    y0ExcursionMin = 0.0,
                    y0ResumeMin = 0.0,
                    y0TotalLostMin = y0Total,
                    netFocusSavedMin = netSaved,
                    ciLowMin = r.deltaL80SimIntervalLow,
                    ciHighMin = r.deltaL80SimIntervalHigh,
                    abductionReplay = abduction
                )
            }
            is SimOutcome.NotReady -> {
                // Fail-open per spec 25.5 (Amendment 4): NO fabricated estimates
                val simResult = SimulationResult(
                    pDistractionShow = 0.0,
                    pDistractionSuppress = 0.0,
                    expectedLostShowMin = 0.0,
                    ciLowShowMin = 0.0,
                    ciHighShowMin = 0.0,
                    expectedLostSuppressMin = 0.0,
                    verdict = ActionVerdict.ALLOW,
                    explanation = "ALLOW: Model not ready, allowed (fail open) [reason: ${simOutcome.reason.name}] — spec 25.5",
                    betaAlpha = 1.0,
                    betaBeta = 1.0,
                    lognormMu = 2.2,
                    lognormSigma = 0.6,
                    updateCount = 0
                )

                val abduction = AbductionReplayState(
                    isExpanded = false,
                    abductedShockU = "U = (uncalibrated)",
                    shockAttend = 0.0,
                    shockExcursion = 0.0,
                    shockResume = 0.0,
                    counterfactualSavingsMin = 0.0
                )

                TwinRunOutcome(
                    simResult = simResult,
                    evidenceStrength = 0.0,
                    nEff = 0.0,
                    modelVersion = 0L,
                    provenance = "Model not ready, allowed (fail open)",
                    isFailOpen = true,
                    reasonCode = "not_ready",
                    priorAlpha = priorAlpha,
                    priorBeta = priorBeta,
                    y1ExcursionMin = 0.0,
                    y1ResumeMin = 0.0,
                    y1TotalLostMin = 0.0,
                    y0ExcursionMin = 0.0,
                    y0ResumeMin = 0.0,
                    y0TotalLostMin = 0.0,
                    netFocusSavedMin = 0.0,
                    ciLowMin = 0.0,
                    ciHighMin = 0.0,
                    abductionReplay = abduction
                )
            }
        }
    }
}

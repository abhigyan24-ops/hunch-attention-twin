package com.dhyan.app.fusion

import com.dhyan.app.inference.RuleBasedFocusStateInferrer
import com.dhyan.app.twin.model.ContextSnapshot
import com.dhyan.app.twin.model.DeclaredSession
import com.dhyan.app.twin.model.FocusState
import com.dhyan.app.twin.model.LaptopState
import com.dhyan.app.twin.model.PhoneState
import com.dhyan.app.twin.model.TaskSource
import com.dhyan.app.twin.model.TaskType
import com.dhyan.app.ui.model.SnapshotSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.max
import kotlin.math.min

/**
 * Merges phone telemetry and laptop sync state into a single fused [ContextSnapshot] (Milestones M1 & M2).
 * Implements [SnapshotSource].
 *
 * Rules:
 * 1. Laptop data older than 15 s means laptopState = OFFLINE and lower confidence; stale laptop is NEVER focus.
 * 2. If relay is down, confidence drops.
 * 3. Declared sessions from either device set taskSource = DECLARED.
 * 4. Tracks second-screen overlap with 10 s debounce and 5 s hysteresis.
 */
class ContextFuser(
    val config: FusionConfig = FusionConfig(),
    private val detector: SecondScreenDetector = SecondScreenDetector(config),
    private val inferrer: RuleBasedFocusStateInferrer = RuleBasedFocusStateInferrer(),
    private val timeProvider: () -> Long = System::currentTimeMillis
) : SnapshotSource {

    private val _snapshot = MutableStateFlow<ContextSnapshot?>(null)
    override val snapshot: StateFlow<ContextSnapshot?> = object : StateFlow<ContextSnapshot?> {
        override val replayCache: List<ContextSnapshot?>
            get() = listOf(value)

        override val value: ContextSnapshot?
            get() = synchronized(this@ContextFuser) {
                if (_snapshot.value != null) recompute()
                _snapshot.value
            }

        override suspend fun collect(collector: kotlinx.coroutines.flow.FlowCollector<ContextSnapshot?>): Nothing {
            var lastEmitted: ContextSnapshot? = null
            while (true) {
                val current = synchronized(this@ContextFuser) {
                    if (_snapshot.value != null) recompute()
                    _snapshot.value
                }
                if (current != lastEmitted) {
                    lastEmitted = current
                    collector.emit(current)
                }
                kotlinx.coroutines.delay(200L)
            }
        }
    }

    @Synchronized override fun currentSnapshot(): ContextSnapshot? {
        if (_snapshot.value != null) recompute()
        return _snapshot.value
    }
    @Volatile var adapterHealthy: Boolean? = null

    fun laptopAvailability(): String = when {
        laptopLastReceivedMs == 0L -> "No laptop data"
        !isRelayConnected -> "Relay offline"
        isLaptopStale() -> "Laptop data is stale"
        remoteLaptopState == LaptopState.OFFLINE && adapterHealthy == true -> "Active but unclassified"
        remoteLaptopState == LaptopState.OFFLINE && adapterHealthy == false -> "Collector offline"
        remoteLaptopState == LaptopState.OFFLINE -> "Laptop activity unavailable"
        else -> "Live companion"
    }

    // State tracking
    private var lastFocusState: FocusState? = null
    private var stateEnteredMs: Long = timeProvider()

    // Local Phone Context State
    private var phoneState: PhoneState = PhoneState.SCREEN_OFF
    private var phoneAppCat: String? = null
    private var localTaskType: TaskType = TaskType.UNKNOWN
    private var phoneDeclaredSession: DeclaredSession? = null
    private var baseConfidence: Double = 0.8
    private var hourBand: String = "afternoon"
    private var fatigueBand: String = "low"
    private var recentInterrupts30m: Int = 0

    // Remote Laptop State
    private var remoteLaptopState: LaptopState = LaptopState.OFFLINE
    private var remoteDeclaredSession: DeclaredSession? = null
    private var laptopLastReceivedMs: Long = 0L

    // Provenance (Spec 25.13 & Milestone M3)
    var isLastResumeEstimated: Boolean = false

    // Relay Connectivity
    private var isRelayConnected: Boolean = false

    /**
     * Updates phone-side context telemetry.
     */
    @Synchronized fun updatePhoneState(
        phoneState: PhoneState,
        phoneAppCat: String? = null,
        taskType: TaskType = this.localTaskType,
        declaredSession: DeclaredSession? = this.phoneDeclaredSession,
        hourBand: String = this.hourBand,
        fatigueBand: String = this.fatigueBand,
        recentInterrupts30m: Int = this.recentInterrupts30m,
        baseConfidence: Double = this.baseConfidence
    ) {
        this.phoneState = phoneState
        this.phoneAppCat = phoneAppCat
        this.localTaskType = taskType
        this.phoneDeclaredSession = declaredSession
        this.hourBand = hourBand
        this.fatigueBand = fatigueBand
        this.recentInterrupts30m = recentInterrupts30m
        this.baseConfidence = baseConfidence
        recompute()
    }

    /**
     * Ingests remote laptop state received from sync session.
     */
    @Synchronized fun updateLaptopState(
        state: LaptopState,
        declaredSession: DeclaredSession? = null,
        receivedAtMs: Long = timeProvider()
    ) {
        this.remoteLaptopState = state
        this.remoteDeclaredSession = declaredSession
        this.laptopLastReceivedMs = receivedAtMs
        recompute()
    }

    /**
     * Updates relay connection status.
     */
    @Synchronized fun updateRelayConnectivity(connected: Boolean) {
        this.isRelayConnected = connected
        recompute()
    }

    /**
     * Recomputes the fused context snapshot and updates [snapshot] flow.
     */
    @Synchronized fun recompute() {
        val nowMs = timeProvider()
        val isLaptopStale = laptopLastReceivedMs == 0L || (nowMs - laptopLastReceivedMs > config.laptopStaleMs)
        val effectiveLaptopState = if (isLaptopStale || !isRelayConnected || adapterHealthy == false) LaptopState.OFFLINE else remoteLaptopState

        // Overlap detection
        val overlap = detector.update(phoneState, effectiveLaptopState, isLaptopStale, nowMs)

        // Declared session resolution: remote laptop or phone
        val validRemoteDeclared = remoteDeclaredSession?.takeIf {
            !isLaptopStale && isRelayConnected && nowMs < it.startedTs + (it.plannedMin * 60_000).toLong()
        }
        val effectiveDeclared = validRemoteDeclared ?: phoneDeclaredSession
        val taskSource = if (effectiveDeclared != null) TaskSource.DECLARED else TaskSource.INFERRED
        val taskType = effectiveDeclared?.task ?: localTaskType

        // Focus State Inference
        val inferredFocus = inferrer.inferFocusState(
            phoneState = phoneState,
            laptopState = effectiveLaptopState,
            isLaptopStale = isLaptopStale,
            isSecondScreenOverlap = overlap,
            declaredSession = effectiveDeclared,
            phoneAppCat = phoneAppCat
        )

        // Track minutes in state
        if (inferredFocus != lastFocusState) {
            lastFocusState = inferredFocus
            stateEnteredMs = nowMs
        }
        val minutesInState = max(0.0, (nowMs - stateEnteredMs) / 60_000.0)

        // Confidence calculation
        var confidence = baseConfidence
        if (!isRelayConnected) {
            confidence -= config.relayDownConfidenceDrop
        }
        if (isLaptopStale) {
            confidence -= config.laptopStaleConfidenceDrop
        }
        confidence = max(0.1, min(1.0, confidence))

        val fused = ContextSnapshot(
            ts = nowMs,
            focusState = inferredFocus,
            taskType = taskType,
            taskSource = taskSource,
            confidence = confidence,
            hourBand = hourBand,
            minutesInState = minutesInState,
            fatigueBand = fatigueBand,
            recentInterrupts30m = recentInterrupts30m,
            laptopState = effectiveLaptopState,
            phoneState = phoneState,
            secondScreenOverlap = overlap,
            declaredSession = effectiveDeclared
        )

        _snapshot.value = fused
    }

    fun isLaptopStale(): Boolean {
        val now = timeProvider()
        return laptopLastReceivedMs == 0L || (now - laptopLastReceivedMs > config.laptopStaleMs)
    }

    /**
     * Computes the provenance of the fused context snapshot per Spec 25.13 & Milestone M3.
     * Flagged as "estimated" when the laptop is stale/offline or when resumption delay was estimated.
     */
    fun currentProvenance(): String {
        val nowMs = timeProvider()
        val isStale = laptopLastReceivedMs == 0L || (nowMs - laptopLastReceivedMs > config.laptopStaleMs)
        return if (isStale || isLastResumeEstimated || remoteLaptopState == LaptopState.OFFLINE) {
            "estimated"
        } else {
            "observed"
        }
    }

    fun isRelayConnected(): Boolean = isRelayConnected

    fun reset() {
        detector.reset()
        remoteLaptopState = LaptopState.OFFLINE
        remoteDeclaredSession = null
        adapterHealthy = null
        laptopLastReceivedMs = 0L
        isRelayConnected = false
        lastFocusState = null
        stateEnteredMs = timeProvider()
        _snapshot.value = null
    }
}

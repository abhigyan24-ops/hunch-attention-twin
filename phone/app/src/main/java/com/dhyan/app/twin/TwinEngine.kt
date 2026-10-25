package com.dhyan.app.twin

import com.dhyan.app.twin.context.ContextBucketer
import com.dhyan.app.twin.model.ContextSnapshot
import com.dhyan.app.twin.model.ModelConfig
import com.dhyan.app.twin.model.NotificationInput
import com.dhyan.app.twin.model.TwinConfig
import com.dhyan.app.twin.params.InMemoryParamStore
import com.dhyan.app.twin.simulator.PairedMonteCarlo
import com.dhyan.app.twin.simulator.SimulationResult

/**
 * Reasons why the TwinEngine cannot produce a simulation result.
 */
enum class NotReadyReason {
    /** The parameter store has not yet completed its initial load from persistence. */
    MODEL_NOT_LOADED,
    /** The ContextSnapshot contains invalid fields (e.g. negative timestamp, confidence not in [0, 1]). */
    INVALID_SNAPSHOT,
    /** The ContextSnapshot is older than the configured staleness threshold. */
    STALE_CONTEXT
}

/**
 * Outcome of a simulation request.
 *
 * CRITICAL SAFETY RULE (spec 25.5):
 * Callers MUST treat [NotReady] as ALLOW. Under no circumstances should a notification
 * be suppressed when the engine is not ready, the model is not loaded, or context is stale.
 */
sealed class SimOutcome {
    data class Ready(val result: SimulationResult) : SimOutcome()
    data class NotReady(val reason: NotReadyReason) : SimOutcome()
}

/**
 * The Twin Engine facade.
 *
 * Provides instant in-memory simulation (<10 ms hot path) without blocking on disk I/O.
 * All state is read from the in-memory store and snapshot.
 *
 * @param store        The in-memory parameter store.
 * @param config       Twin configuration parameters.
 * @param modelConfig  Model-level architectural configurations.
 * @param clock        Clock function returning current epoch ms (default: System::currentTimeMillis).
 */
class TwinEngine(
    val store: InMemoryParamStore,
    val config: TwinConfig = TwinConfig.default(),
    val modelConfig: ModelConfig = ModelConfig(),
    val clock: () -> Long = System::currentTimeMillis
) {

    /**
     * Simulates the counterfactual outcomes of showing vs suppressing [notif] in [snapshot].
     *
     * Returns [SimOutcome.Ready] with paired distributions when valid, or [SimOutcome.NotReady]
     * if the model is not loaded, snapshot is invalid, or context is stale.
     *
     * Callers must treat [SimOutcome.NotReady] as ALLOW.
     */
    fun simulate(
        snapshot: ContextSnapshot,
        notif: NotificationInput,
        seed: Long = 42L
    ): SimOutcome {
        // 1. Validate snapshot
        if (!isSnapshotValid(snapshot)) {
            return SimOutcome.NotReady(NotReadyReason.INVALID_SNAPSHOT)
        }

        // 2. Check context staleness
        val now = clock()
        if (now - snapshot.ts > modelConfig.staleSnapshotMs) {
            return SimOutcome.NotReady(NotReadyReason.STALE_CONTEXT)
        }

        // 3. Resolve model parameters (returns null if store not loaded)
        val bucket = ContextBucketer.bucket(snapshot, notif)
        val baselineBucket = ContextBucketer.baselineBucket(snapshot)
        val modelSnapshot = store.resolve(bucket, baselineBucket, notif)
            ?: return SimOutcome.NotReady(NotReadyReason.MODEL_NOT_LOADED)

        // 4. Run simulation
        val result = PairedMonteCarlo.simulate(
            snapshot = modelSnapshot,
            config = config,
            modelConfig = modelConfig,
            seed = seed
        )

        return SimOutcome.Ready(result)
    }

    private fun isSnapshotValid(s: ContextSnapshot): Boolean {
        if (s.ts <= 0L) return false
        if (s.confidence < 0.0 || s.confidence > 1.0) return false
        if (s.minutesInState < 0.0) return false
        if (s.recentInterrupts30m < 0) return false
        return true
    }
}

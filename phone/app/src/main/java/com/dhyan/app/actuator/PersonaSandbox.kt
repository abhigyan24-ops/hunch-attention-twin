package com.dhyan.app.actuator

import com.dhyan.app.personas.PersonaCatalog
import com.dhyan.app.personas.PersonaDef
import com.dhyan.app.twin.SimOutcome
import com.dhyan.app.twin.TwinEngine
import com.dhyan.app.twin.context.ContextBucket
import com.dhyan.app.twin.context.ContextBucketer
import com.dhyan.app.twin.model.ContextSnapshot
import com.dhyan.app.twin.model.NotificationInput
import com.dhyan.app.twin.model.TwinConfig
import com.dhyan.app.twin.params.InMemoryParamStore
import com.dhyan.app.twin.params.ParamPersistence
import com.dhyan.app.twin.params.ParamRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

/**
 * Isolated parameter persistence for sandbox simulation.
 * Never writes to Room, SQLite, or disk.
 */
class InMemoryParamPersistence(
    initialRecords: List<ParamRecord> = emptyList()
) : ParamPersistence {
    private val records = mutableMapOf<String, ParamRecord>()
    init { initialRecords.forEach { records[it.key] = it } }

    override suspend fun loadAll(): List<ParamRecord> = records.values.toList()
    override suspend fun upsert(records: List<ParamRecord>) {
        records.forEach { this.records[it.key] = it }
    }
}

/**
 * An isolated sandbox for what-if simulation and persona fast-forward.
 *
 * CRITICAL SAFETY INVARIANT:
 * This sandbox uses its own [InMemoryParamStore] and [InMemoryParamPersistence].
 * Under no circumstances does any observation or fast-forward replay here
 * touch or modify the production Room parameter tables.
 */
class PersonaSandbox(
    val config: TwinConfig = TwinConfig.default(),
    val clock: () -> Long = System::currentTimeMillis
) {
    private val persistence = InMemoryParamPersistence()
    val store = InMemoryParamStore(
        persistence = persistence,
        config = config,
        flushDispatcher = Dispatchers.Unconfined
    )

    val engine = TwinEngine(
        store = store,
        config = config,
        clock = clock
    )

    init {
        runBlocking {
            store.load()
        }
    }

    /** Run simulation on the isolated twin engine. */
    fun simulate(
        snapshot: ContextSnapshot,
        notif: NotificationInput,
        seed: Long = 42L
    ): SimOutcome {
        return engine.simulate(snapshot, notif, seed)
    }

    /**
     * Fast-forwards the sandbox by training on persona parameters.
     * Updates the sandbox store's pickup, dwell, and resume stats in memory only.
     */
    fun fastForward(persona: PersonaDef, days: Int = 14) {
        val count = days * 10
        val snapshot = ContextSnapshot(
            ts = 1_000_000L,
            focusState = com.dhyan.app.twin.model.FocusState.DEEP_FOCUS,
            taskType = com.dhyan.app.twin.model.TaskType.CODING,
            taskSource = com.dhyan.app.twin.model.TaskSource.DECLARED,
            confidence = 0.9,
            hourBand = "afternoon",
            minutesInState = 45.0,
            fatigueBand = "low",
            recentInterrupts30m = 0,
            laptopState = com.dhyan.app.twin.model.LaptopState.FLOWING,
            phoneState = com.dhyan.app.twin.model.PhoneState.SCREEN_OFF,
            secondScreenOverlap = true,
            declaredSession = null
        )

        for ((key, prob) in persona.pickupProb) {
            val parts = key.split("|")
            val rawClass = parts.getOrNull(0) ?: "social"
            val appCat = when {
                rawClass.startsWith("social") -> "social"
                rawClass.startsWith("msg") -> "msg"
                rawClass.startsWith("promo") -> "promo"
                else -> "social"
            }
            val notif = NotificationInput(
                appCat = appCat,
                notifCat = "post",
                isVip = false,
                urgencyScore = 0.3,
                repeatCount5m = 0
            )
            val bucket = ContextBucketer.bucket(snapshot, notif)

            val successes = (count * prob).toInt().coerceAtLeast(1)
            val failures = (count - successes).coerceAtLeast(1)
            for (i in 0 until successes) {
                store.observePickup(bucket, picked = true, nowMs = 1_000_000L + i * 60_000L)
            }
            for (i in 0 until failures) {
                store.observePickup(bucket, picked = false, nowMs = 1_000_000L + (successes + i) * 60_000L)
            }
        }
    }

    /**
     * Resolves the current Beta distribution (alpha, beta) for the given bucket
     * to render the Bayesian curve in the Twin Lab drawer.
     */
    fun resolvePickupBeta(snapshot: ContextSnapshot, notif: NotificationInput): Pair<Double, Double> {
        val bucket = ContextBucketer.bucket(snapshot, notif)
        val baseline = ContextBucketer.baselineBucket(snapshot)
        val model = store.resolve(bucket, baseline, notif)
        return if (model != null) {
            Pair(model.pickupAlpha, model.pickupBeta)
        } else {
            Pair(1.0, 1.0)
        }
    }
}

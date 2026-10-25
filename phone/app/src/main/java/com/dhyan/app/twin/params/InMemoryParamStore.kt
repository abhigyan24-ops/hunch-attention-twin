package com.dhyan.app.twin.params

import com.dhyan.app.twin.context.ContextBucket
import com.dhyan.app.twin.context.BaselineBucket
import com.dhyan.app.twin.model.ModelConfig
import com.dhyan.app.twin.model.NotificationInput
import com.dhyan.app.twin.model.TwinConfig
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * In-memory parameter store with async write-through to [ParamPersistence].
 *
 * All reads hit concurrent maps atomically (stats are immutable values replaced atomically).
 * The simulator reads a resolved [ModelSnapshot] — an immutable object — rather than the live maps.
 * Updates mark keys dirty; a background flusher batches writes via [ParamPersistence.upsert].
 *
 * Fail-open rule (spec 25.5): if [load] has not completed, [resolve] returns null and
 * the caller must treat this as a not-ready / ALLOW situation.
 *
 * Privacy: this class handles only statistics (counts, means, sums). No raw notification text.
 *
 * @param persistence         Persistence layer (e.g. Room DAO adapter or FakePersistence for tests).
 * @param config              Twin configuration (kappa, gamma, priorN, etc.).
 * @param modelConfig         Model configuration (S3 assumptions).
 * @param flushDispatcher     Dispatcher for the background flush coroutine.
 */
class InMemoryParamStore(
    private val persistence: ParamPersistence,
    private val config: TwinConfig = TwinConfig.default(),
    private val modelConfig: ModelConfig = ModelConfig(),
    private val flushDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    // ── Internal state ─────────────────────────────────────────────────────

    private val betaMap      = ConcurrentHashMap<String, BetaStat>()
    private val lognormMap   = ConcurrentHashMap<String, LogNormalStat>()
    private val dirichletMap = ConcurrentHashMap<String, DirichletStat>()
    private val gammaMap     = ConcurrentHashMap<String, GammaHazardStat>()
    private val dirtyKeys    = ConcurrentHashMap.newKeySet<String>()

    private val _isLoaded    = AtomicBoolean(false)
    private val _modelVersion = AtomicLong(0L)
    val persistenceFailureCount = AtomicInteger(0)
    private var loadError: Throwable? = null

    private val flushScope = CoroutineScope(flushDispatcher + SupervisorJob())

    // ── Public API ─────────────────────────────────────────────────────────

    /** True once [load] has completed successfully. */
    val isLoaded: Boolean get() = _isLoaded.get()

    /** Monotonic version counter incremented on every parameter update. */
    val modelVersion: Long get() = _modelVersion.get()

    fun getBetaStat(key: String): BetaStat? = betaMap[key]
    fun getLogNormalStat(key: String): LogNormalStat? = lognormMap[key]
    fun getGammaStat(key: String): GammaHazardStat? = gammaMap[key]
    fun getDirichletStat(key: String): DirichletStat? = dirichletMap[key]

    /**
     * Loads all parameter records from persistence. Must be called once at startup.
     * On failure, leaves [isLoaded] as false and records the error; callers get NotReady.
     */
    suspend fun load() {
        try {
            val records = persistence.loadAll()
            applyRecords(records)
            _isLoaded.set(true)
        } catch (e: Throwable) {
            loadError = e
            // isLoaded remains false → resolve() will return null
        }
    }

    /**
     * Forces a synchronous flush of all dirty keys to persistence.
     * Useful for tests and graceful shutdown.
     */
    suspend fun flushNow() {
        flushDirty()
    }

    // ── Learning API (called by EpisodeBuilder/EpisodeCloser in Stage 4) ──

    /** Records a pickup observation in all levels of the pickup hierarchy. */
    fun observePickup(bucket: ContextBucket, picked: Boolean, nowMs: Long) {
        val chain = bucket.pickupChain()
        for (key in chain) {
            betaMap[key] = (betaMap[key] ?: BetaStat()).updated(picked, config.gamma)
            markDirty(key)
        }
        bumpVersion()
        scheduleDirtyFlush()
    }

    /** Records a baseline self-initiated pickup event and exposure in all hazard hierarchy levels. */
    fun observeSelfPickups(baselineBucket: BaselineBucket, events: Double, exposureHours: Double, nowMs: Long) {
        val chain = baselineBucket.chain()
        for (key in chain) {
            gammaMap[key] = (gammaMap[key] ?: GammaHazardStat()).updated(events, exposureHours)
            markDirty(key)
        }
        bumpVersion()
        scheduleDirtyFlush()
    }

    /** Records a destination observation in the Dirichlet map. kind: "self" or "notif". */
    fun observeDestination(kind: String, bucket: ContextBucket, destination: String, nowMs: Long) {
        val baseKey = when (kind) {
            "self"  -> "dirichlet_self|task=${bucket.taskType}|hour=${bucket.hourBand}|fat=${bucket.fatigueBand}"
            "notif" -> "dirichlet_notif|task=${bucket.taskType}|hour=${bucket.hourBand}|fat=${bucket.fatigueBand}|nc=${bucket.notifClass}"
            else    -> return
        }
        val parentKey = when (kind) {
            "self"  -> "dirichlet_self|task=${bucket.taskType}"
            "notif" -> "dirichlet_notif|nc=${bucket.notifClass}"
            else    -> return
        }
        val grandparentKey = when (kind) {
            "self"  -> "dirichlet_self"
            "notif" -> "dirichlet_notif"
            else    -> return
        }
        for (prefix in listOf("$baseKey|dest=$destination", "$parentKey|dest=$destination", "$grandparentKey|dest=$destination")) {
            dirichletMap[prefix] = (dirichletMap[prefix] ?: DirichletStat()).updated(config.gamma)
            markDirty(prefix)
        }
        bumpVersion()
        scheduleDirtyFlush()
    }

    /** Records a dwell duration (minutes) for a (appCat, surface) pair. */
    fun observeDwell(appCat: String, surface: String, durationMin: Double, nowMs: Long) {
        val chain = ParamKeys.lognormDwellChain(appCat, surface)
        for (key in chain) {
            lognormMap[key] = (lognormMap[key] ?: LogNormalStat()).updated(durationMin)
            markDirty(key)
        }
        bumpVersion()
        scheduleDirtyFlush()
    }

    /** Records a resume delay (minutes) for a task type. */
    fun observeResume(taskType: String, delayMin: Double, nowMs: Long) {
        val chain = ParamKeys.lognormResumeChain(taskType)
        for (key in chain) {
            lognormMap[key] = (lognormMap[key] ?: LogNormalStat()).updated(delayMin)
            markDirty(key)
        }
        bumpVersion()
        scheduleDirtyFlush()
    }

    /** Records a pickup latency (minutes). */
    fun observePickupLatency(latencyMin: Double, nowMs: Long) {
        val key = ParamKeys.lognormPickupLatency()
        lognormMap[key] = (lognormMap[key] ?: LogNormalStat()).updated(latencyMin)
        markDirty(key)
        bumpVersion()
        scheduleDirtyFlush()
    }

    // ── Snapshot resolution ────────────────────────────────────────────────

    /**
     * Resolves an immutable [ModelSnapshot] for the given context.
     * Returns null if [isLoaded] is false (caller must treat as NotReady/ALLOW).
     *
     * A cold store with no learned data is Ready — it uses priors.
     */
    fun resolve(
        bucket: ContextBucket,
        baselineBucket: BaselineBucket,
        notif: NotificationInput
    ): ModelSnapshot? {
        if (!_isLoaded.get()) return null

        val (pickupAlpha, pickupBeta, pickupLevel, nEff) = resolvePickup(bucket, notif)
        val (hazardShape, hazardRate, hazardLevel) = resolveHazard(baselineBucket)
        val (selfAlphas, notifAlphas) = resolveDirichlets(bucket, notif)
        val (resumeMean, resumeSigma) = resolveResume(bucket.taskType)
        val dwellMeans = mutableMapOf<String, Double>()
        val dwellSigmas = mutableMapOf<String, Double>()
        for (dest in PriorTable.destinations) {
            val (cat, surf) = dest.split("/", limit = 2).let { it[0] to it[1] }
            val (m, s) = resolveDwell(cat, surf)
            dwellMeans[dest] = m
            dwellSigmas[dest] = s
        }
        val (plMean, plSigma) = resolvePickupLatency()
        val evidenceStrength = nEff / (nEff + 10.0)

        return ModelSnapshot(
            pickupAlpha          = pickupAlpha,
            pickupBeta           = pickupBeta,
            hazardShape          = hazardShape,
            hazardRate           = hazardRate,
            selfDirichletAlphas  = selfAlphas,
            notifDirichletAlphas = notifAlphas,
            destinations         = PriorTable.destinations,
            dwellMeanLog         = dwellMeans,
            dwellSigma           = dwellSigmas,
            resumeMeanLog        = resumeMean,
            resumeSigma          = resumeSigma,
            pickupLatencyMeanLog = plMean,
            pickupLatencySigma   = plSigma,
            nEff                 = nEff,
            evidenceStrength     = evidenceStrength,
            pickupHierarchyLevel = pickupLevel,
            hazardHierarchyLevel = hazardLevel,
            modelVersion         = _modelVersion.get()
        )
    }

    // ── Internal helpers ───────────────────────────────────────────────────

    private data class PickupResolution(
        val alpha: Double, val beta: Double,
        val level: HierarchyLevel, val nEff: Double
    )

    private fun resolvePickup(bucket: ContextBucket, notif: NotificationInput): PickupResolution {
        val kappa = config.kappa
        val (alpha0, beta0) = PriorTable.pickupPrior(notif.appCat, notif.notifCat, bucket.fatigueBand)

        val fullKey = bucket.fullKey()
        val parentKey = bucket.parentKey()

        val fullStat   = betaMap[fullKey]
        val parentStat = betaMap[parentKey]

        val nFull   = fullStat?.n ?: 0.0
        val nParent = parentStat?.n ?: 0.0
        val nEff    = HierarchyResolution.computeNEff(nFull, nParent)

        // Compute parent mean for shrinkage
        val parentMean: Double
        val level: HierarchyLevel
        val effectiveStat: BetaStat

        when {
            fullStat != null -> {
                // Use full bucket, shrink toward parent (or prior if no parent)
                parentMean = parentStat?.pointEstimate(alpha0, beta0, kappa, 0.5)
                    ?: ((alpha0) / (alpha0 + beta0))
                level = HierarchyLevel.FULL
                effectiveStat = fullStat
            }
            parentStat != null -> {
                parentMean = (alpha0) / (alpha0 + beta0)  // grandparent is the prior
                level = HierarchyLevel.PARENT
                effectiveStat = parentStat
            }
            else -> {
                // Use prior directly
                return PickupResolution(alpha0, beta0, HierarchyLevel.PRIOR, 0.0)
            }
        }

        val (alpha, beta) = effectiveStat.posteriorParams(alpha0, beta0, kappa, parentMean)
        return PickupResolution(alpha, beta, level, nEff)
    }

    private data class HazardResolution(val shape: Double, val rate: Double, val level: HierarchyLevel)

    private fun resolveHazard(baselineBucket: BaselineBucket): HazardResolution {
        val shape0 = modelConfig.baselineHazardShape
        val rate0  = modelConfig.baselineHazardRate

        val fullKey   = baselineBucket.fullKey()
        val parentKey = baselineBucket.parentKey()

        val fullStat   = gammaMap[fullKey]
        val parentStat = gammaMap[parentKey]

        return when {
            fullStat != null -> HazardResolution(
                fullStat.posteriorShape(shape0),
                fullStat.posteriorRate(rate0),
                HierarchyLevel.BASELINE_FULL
            )
            parentStat != null -> HazardResolution(
                parentStat.posteriorShape(shape0),
                parentStat.posteriorRate(rate0),
                HierarchyLevel.BASELINE_PARENT
            )
            else -> HazardResolution(shape0, rate0, HierarchyLevel.PRIOR)
        }
    }

    private fun resolveDirichlets(bucket: ContextBucket, notif: NotificationInput): Pair<DoubleArray, DoubleArray> {
        val dests = PriorTable.destinations
        val priorAlphasSelf  = DoubleArray(dests.size) { 1.0 }
        val priorAlphasNotif = PriorTable.dirichletAlphas(notif.appCat, null)
        val kappa = config.kappa

        val selfAlphas = DoubleArray(dests.size) { i ->
            val dest = dests[i]
            val fullKey = "dirichlet_self|task=${bucket.taskType}|hour=${bucket.hourBand}|fat=${bucket.fatigueBand}|dest=$dest"
            val parentKey = "dirichlet_self|task=${bucket.taskType}|dest=$dest"
            val grandKey = "dirichlet_self|dest=$dest"

            val stat = dirichletMap[fullKey] ?: dirichletMap[parentKey] ?: dirichletMap[grandKey]
            val parentMean = priorAlphasSelf[i] / priorAlphasSelf.sum()
            stat?.effectiveAlpha(kappa, parentMean) ?: (priorAlphasSelf[i] + kappa * parentMean)
        }

        val notifAlphas = DoubleArray(dests.size) { i ->
            val dest = dests[i]
            val fullKey = "dirichlet_notif|task=${bucket.taskType}|hour=${bucket.hourBand}|fat=${bucket.fatigueBand}|nc=${bucket.notifClass}|dest=$dest"
            val parentKey = "dirichlet_notif|nc=${bucket.notifClass}|dest=$dest"
            val grandKey = "dirichlet_notif|dest=$dest"

            val stat = dirichletMap[fullKey] ?: dirichletMap[parentKey] ?: dirichletMap[grandKey]
            val parentMean = priorAlphasNotif[i] / priorAlphasNotif.sum()
            stat?.effectiveAlpha(kappa, parentMean) ?: (priorAlphasNotif[i] + kappa * parentMean)
        }

        return selfAlphas to notifAlphas
    }

    private fun resolveDwell(appCat: String, surface: String): Pair<Double, Double> {
        val (priorM, priorS) = PriorTable.dwellPrior(appCat, surface)
        val n0 = config.lognormPriorN
        val fullKey   = ParamKeys.lognormDwellFull(appCat, surface)
        val parentKey = ParamKeys.lognormDwellParent(appCat)

        val stat = lognormMap[fullKey] ?: lognormMap[parentKey]
        return if (stat == null) {
            Pair(priorM, priorS)
        } else {
            Pair(
                stat.posteriorMean(n0, priorM),
                stat.posteriorSigma(n0, priorS, config.sigmaMin)
            )
        }
    }

    private fun resolveResume(taskType: String): Pair<Double, Double> {
        val (priorM, priorS) = PriorTable.resumePrior(taskType)
        val n0 = config.lognormPriorN
        val fullKey = ParamKeys.lognormResumeFull(taskType)
        val stat = lognormMap[fullKey]
        return if (stat == null) Pair(priorM, priorS)
        else Pair(stat.posteriorMean(n0, priorM), stat.posteriorSigma(n0, priorS, config.sigmaMin))
    }

    private fun resolvePickupLatency(): Pair<Double, Double> {
        val key = ParamKeys.lognormPickupLatency()
        val stat = lognormMap[key]
        val priorM = PriorTable.pickupLatencyMeanLog
        val priorS = PriorTable.pickupLatencySigma
        val n0 = config.lognormPriorN
        return if (stat == null) Pair(priorM, priorS)
        else Pair(stat.posteriorMean(n0, priorM), stat.posteriorSigma(n0, priorS, config.sigmaMin))
    }

    // ── Persistence helpers ────────────────────────────────────────────────

    private fun applyRecords(records: List<ParamRecord>) {
        for (r in records) {
            when (r.kind) {
                ParamKind.BETA      -> betaMap[r.key]      = BetaStat(r.a, r.b, r.n)
                ParamKind.LOGNORM   -> lognormMap[r.key]   = LogNormalStat(r.n, r.mean, r.m2)
                ParamKind.DIRICHLET -> dirichletMap[r.key] = DirichletStat(r.a)
                ParamKind.GAMMA     -> gammaMap[r.key]     = GammaHazardStat(r.a, r.b, r.n)
            }
        }
    }

    private fun markDirty(key: String) { dirtyKeys.add(key) }

    private fun bumpVersion() { _modelVersion.incrementAndGet() }

    private fun scheduleDirtyFlush() {
        flushScope.launch {
            try {
                flushDirty()
            } catch (e: Throwable) {
                persistenceFailureCount.incrementAndGet()
                // Never propagate to callers
            }
        }
    }

    private suspend fun flushDirty() {
        val keys = dirtyKeys.toList()
        if (keys.isEmpty()) return
        val records = mutableListOf<ParamRecord>()
        val nowMs = System.currentTimeMillis()
        for (key in keys) {
            betaMap[key]?.let { s ->
                records.add(ParamRecord(key, ParamKind.BETA, s.successes, s.failures, s.n, 0.0, 0.0, nowMs))
            }
            lognormMap[key]?.let { s ->
                records.add(ParamRecord(key, ParamKind.LOGNORM, 0.0, 0.0, s.count, s.welfordMean, s.welfordM2, nowMs))
            }
            dirichletMap[key]?.let { s ->
                records.add(ParamRecord(key, ParamKind.DIRICHLET, s.decayedCount, 0.0, 0.0, 0.0, 0.0, nowMs))
            }
            gammaMap[key]?.let { s ->
                records.add(ParamRecord(key, ParamKind.GAMMA, s.events, s.exposureHours, s.n, 0.0, 0.0, nowMs))
            }
        }
        if (records.isNotEmpty()) {
            persistence.upsert(records)
            dirtyKeys.removeAll(keys.toSet())
        }
    }
}

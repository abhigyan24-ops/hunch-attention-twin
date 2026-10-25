package com.dhyan.app.personas.world

import com.dhyan.app.personas.*
import com.dhyan.app.personas.rng.GenRng
import kotlin.math.*

/**
 * 4A-3: TrueEffect.
 * Computes expected costs by simulating the same trial rules as the generator,
 * using the generator's own samplers. Does NOT import twin.*.
 *
 * Key design: mirrors HistoryGenerator.trial() exactly -- same draw order,
 * same destination selection, same cost formula. Self-initiated uses social_notif destinations.
 */
data class TrueEffectEstimate(
    val eLShow: Double, val eLSuppress: Double, val eDeltaL: Double,
    val pLGreaterThan1: Double, val nDraws: Int
)

data class TruthSummary(
    val meanDeltaLByClass: Map<String, Double>,
    val meanDeltaLByHour: Map<String, Double>,
    val countByClass: Map<String, Int>,
    val countByHour: Map<String, Int>,
    val negDeltaLCount: Int
)

object TrueEffect {

    /**
     * Simulate n draws under the generator trial rules for a given context and persona.
     * [pPickupOverride] optionally replaces the lookup from persona.effectivePickupProb.
     */
    fun estimate(
        persona: PersonaDef,
        context: GenContext,
        config: GeneratorConfig,
        nDraws: Int = 200_000,
        seed: Long = 99L,
        pPickupOverride: Double? = null
    ): TrueEffectEstimate {
        val key = "${context.notifClass}|${context.taskType}"
        val basePickup = pPickupOverride ?: persona.effectivePickupProb.getOrDefault(key, 0.0)
        val lambda = persona.selfInitiatedPerHour   // per hour
        val cGlance = config.cGlanceMin
        val W = config.windowMin
        val truncate = config.truncateCostsAtWindow

        // Self-initiated destination pool (mirrors HistoryGenerator)
        val selfDestinations = persona.defaults.notificationMix.firstOrNull { it.name == "social_notif" }?.destinations
            ?: persona.defaults.notificationMix.first().destinations
        // Notification destination pool for this notifClass
        val notifDestinations = persona.defaults.notificationMix.firstOrNull { it.name == context.notifClass }?.destinations
            ?: selfDestinations

        var sumShow = 0.0; var sumSuppress = 0.0; var pL1 = 0
        for (i in 0 until nDraws) {
            val rng = GenRng(seed).fork(700L, i.toLong())
            val (lShow, lSuppress) = singleTrial(rng, basePickup, lambda, cGlance, W, truncate,
                context, persona, selfDestinations, notifDestinations)
            sumShow += lShow; sumSuppress += lSuppress
            if (lShow > 1.0) pL1++
        }
        val n = nDraws.toDouble()
        return TrueEffectEstimate(sumShow/n, sumSuppress/n, (sumShow-sumSuppress)/n, pL1/n, nDraws)
    }

    private fun singleTrial(
        rng: GenRng, pPickup: Double, lambda: Double,
        cGlance: Double, W: Double, truncate: Boolean,
        context: GenContext, persona: PersonaDef,
        selfDestinations: List<Destination>, notifDestinations: List<Destination>
    ): Pair<Double, Double> {
        // Fixed draw order: uSelf, destSelf, zDself, zRself, uPick, uLatency, destNotif, zDnotif, zRnotif
        val uSelf    = rng.nextDouble()
        val destSelf = rng.nextDouble()
        val zDself   = rng.nextNormal(); val zRself = rng.nextNormal()
        val uPick    = rng.nextDouble()
        val uLatency = rng.nextDouble()
        val destNotif = rng.nextDouble()
        val zDnotif  = rng.nextNormal(); val zRnotif = rng.nextNormal()

        // Baseline: simple exponential (piecewise hazard not simulated here; use constant rate)
        val tSelf = if (lambda == 0.0) Double.POSITIVE_INFINITY else -ln1p(-uSelf) / (lambda / 60.0)
        val baselineExists = tSelf < W

        // Apply fatigue to pickup odds (A4-4)
        val pAdj = applyFatigue(pPickup, context.fatigueBand, persona.fatigueMultiplier)
        val picked = uPick < pAdj
        val latency = exp(persona.defaults.pickupLatency.mu + persona.defaults.pickupLatency.sigma * (-ln1p(-uLatency)))
        val notifEventExists = picked && latency < W

        // Earliest wins; tie to notification
        val owner = when {
            notifEventExists && (!baselineExists || latency <= tSelf) -> Owner.NOTIF
            baselineExists -> Owner.SELF
            else -> Owner.NONE
        }

        // Destination and cost
        val selfDest = pickFromWeighted(destSelf, selfDestinations)
        val notifDest = pickFromWeighted(destNotif, notifDestinations)

        val selfDwellSpec = persona.dwell.getOrElse(selfDest.category) { persona.dwell.values.first() }
        val notifDwellSpec = persona.dwell.getOrElse(notifDest.category) { persona.dwell.values.first() }
        val resumeSpec = persona.resume.getOrElse(context.taskType) { persona.resume.values.first() }

        val dSelf = exp(selfDwellSpec.mu + selfDwellSpec.sigma * zDself)
        val rSelf = exp(resumeSpec.mu + resumeSpec.sigma * zRself)
        val dNotif = exp(notifDwellSpec.mu + notifDwellSpec.sigma * zDnotif)
        val rNotif = exp(resumeSpec.mu + resumeSpec.sigma * zRnotif)

        fun cost(onset: Double, d: Double, r: Double): Double {
            val raw = d + r
            return cGlance + if (truncate) minOf(raw, maxOf(0.0, W - onset)) else raw
        }

        val lShow = when (owner) {
            Owner.NOTIF -> cost(latency, dNotif, rNotif)
            Owner.SELF  -> cost(tSelf, dSelf, rSelf)
            Owner.NONE  -> cGlance
        }
        val lSuppress = if (baselineExists) dSelf + rSelf else 0.0

        return Pair(lShow, lSuppress)
    }

    private fun applyFatigue(p: Double, band: Int, multipliers: List<Double>): Double {
        if (p <= 0.0) return 0.0; if (p >= 1.0) return 1.0
        val m = multipliers.getOrElse(band) { 1.0 }
        val odds = p / (1.0 - p)
        return (odds * m / (1.0 + odds * m)).coerceIn(0.0, 1.0)
    }

    private fun pickFromWeighted(u: Double, destinations: List<Destination>): Destination {
        val total = destinations.sumOf { it.weight }
        var cum = 0.0
        for (d in destinations) { cum += d.weight / total; if (u < cum) return d }
        return destinations.last()
    }

    /** Aggregate over a generated history; excludes inSession records. */
    fun summarize(records: List<TruthRecord>): TruthSummary {
        val eligible = records.filter { !it.inSession && it.trial != null }
        fun meanDelta(group: List<TruthRecord>) = group.mapNotNull { it.trial?.deltaLMin }.let {
            if (it.isEmpty()) Double.NaN else it.average()
        }
        val byClass = eligible.groupBy { it.context.notifClass }
        val byHour = eligible.groupBy { it.context.hourBand }
        return TruthSummary(
            meanDeltaLByClass = byClass.mapValues { meanDelta(it.value) },
            meanDeltaLByHour = byHour.mapValues { meanDelta(it.value) },
            countByClass = byClass.mapValues { it.value.size },
            countByHour = byHour.mapValues { it.value.size },
            negDeltaLCount = eligible.count { (it.trial?.deltaLMin ?: 0.0) < 0.0 }
        )
    }

    /** Per-context convenience. */
    fun byContext(persona: PersonaDef, context: GenContext, config: GeneratorConfig,
                  nDraws: Int = 200_000, seed: Long = 99L) =
        estimate(persona, context, config, nDraws, seed)
}

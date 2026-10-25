package com.dhyan.app.personas.world

import com.dhyan.app.personas.*
import com.dhyan.app.personas.rng.GenRng
import kotlin.math.*

data class GeneratedHistory(val events: List<GenEvent>, val truthRecords: List<TruthRecord>)

/**
 * 4A-3: Full history generator.
 * - Cost computation (lShowMin, lSuppressMin) in TrialDraws, using same draws
 * - MILD_MISSPEC world: per-day pickup drift in [0.85,1.15], 10% heavy-tail dwell (mu+ln3)
 * - RandomizedHold logging policy: SUPPRESS arm realization via separate RNG fork
 * - TruthRecord realizedArm, propensity, eligibleForExploration, world fields
 *
 * No twin.* imports. Pure Kotlin.
 */
object HistoryGenerator {

    fun generate(persona: PersonaDef, config: GeneratorConfig): GeneratedHistory {
        val horizon = config.days * 1440.0
        val planner = DayPlanner(persona, config)
        val blocks = (0 until config.days).flatMap { planner.plan(it) }

        // Per-day drift for MILD_MISSPEC (deterministic from seed)
        val dayDrift: (Int) -> Double = { day ->
            if (config.world == World.MILD_MISSPEC) {
                val rng = GenRng(config.seed).fork(600L, day.toLong())
                0.85 + rng.nextDouble() * 0.30   // in [0.85, 1.15]
            } else 1.0
        }

        data class Pending(val time: Double, val device: String, val payload: GenPayload, val order: Int)
        val pending = mutableListOf<Pending>()
        fun emit(time: Double, device: String, payload: GenPayload) {
            require(time.isFinite() && time >= 0) { "Invalid synthetic event time" }
            pending.add(Pending(time, device, payload, pending.size))
        }
        fun phone(t: Double, p: GenPayload) = emit(t, "phone", p)
        fun laptop(t: Double, p: GenPayload) = emit(t, "laptop", p)
        fun taskCat(task: String) = if (task == "studying") "learning" else if (task == "gaming") "games" else "coding"

        phone(0.0, GenPayload.ScreenOff())
        for (block in blocks) {
            laptop(block.startMin, GenPayload.IdleEnd(0.0))
            laptop(block.startMin, GenPayload.Window("synthetic.${block.taskType}", taskCat(block.taskType), 0.0))
            laptop(block.endMin, GenPayload.Window("synthetic.away", "system", block.endMin - block.startMin))
            laptop(block.endMin, GenPayload.IdleStart(0.0))
        }

        var activeFrom = Double.POSITIVE_INFINITY; var busyUntil = 0.0; var windowUntil = 0.0
        var selfIndex = 0L

        fun choose(u: Double, destinations: List<Destination>): Destination {
            val target = u * destinations.sumOf { it.weight }; var sum = 0.0
            return destinations.firstOrNull { sum += it.weight; target < sum } ?: destinations.last()
        }

        /** Sample lognormal; MILD_MISSPEC applies 10% heavy-tail (mu+ln3). */
        fun sampleDwell(spec: LogNormalSpec, z: Double, day: Int): Double {
            val heavyTail = if (config.world == World.MILD_MISSPEC) {
                GenRng(config.seed).fork(601L, day.toLong()).nextDouble() < 0.10
            } else false
            val mu = if (heavyTail) spec.mu + ln(3.0) else spec.mu
            return exp(mu + spec.sigma * z)
        }

        /** Apply fatigue to pickup probability via odds multiplier (A4-4). */
        fun adjustPickup(p: Double, fatigueBand: Int): Double {
            if (p <= 0.0) return 0.0; if (p >= 1.0) return 1.0
            val m = persona.fatigueMultiplier.getOrElse(fatigueBand) { 1.0 }
            val odds = p / (1.0 - p)
            return (odds * m / (1.0 + odds * m)).coerceIn(0.0, 1.0)
        }

        fun episode(onset: Double, destination: Destination, zD: Double, zR: Double,
                    context: GenContext, key: String?, day: Int): Double {
            val dwellSpec = persona.dwell.getValue(destination.category)
            val resumeSpec = persona.resume.getValue(context.taskType)
            val d = sampleDwell(dwellSpec, zD, day)
            val r = exp(resumeSpec.mu + resumeSpec.sigma * zR)
            require(d.isFinite() && r.isFinite()) { "Nonfinite synthetic duration" }
            val end = onset + d; val recovered = end + r
            val pkg = "synthetic.${destination.category}"
            phone(onset, GenPayload.ScreenOn()); phone(onset, GenPayload.Unlock()); phone(onset, GenPayload.Pickup(key))
            phone(onset, GenPayload.Foreground(pkg, destination.category, destination.surface))
            phone(end, GenPayload.Background(pkg, (d * 60000).roundToLong())); phone(end, GenPayload.ScreenOff())
            val block = blocks.firstOrNull { onset >= it.startMin && onset < it.endMin }
            if (block != null) {
                laptop(onset, GenPayload.Window("synthetic.away", "system", (onset - block.startMin).coerceAtLeast(0.0)))
                laptop(onset, GenPayload.IdleStart(0.0))
                laptop(end, GenPayload.IdleEnd(d * 60))
                if (end < block.endMin) laptop(end, GenPayload.Window("synthetic.${block.taskType}", taskCat(block.taskType), d))
                if (recovered < block.endMin) laptop(recovered, GenPayload.Resume((r * 60000).roundToLong()))
            }
            activeFrom = onset; busyUntil = recovered
            return recovered
        }

        /** Run a trial: all 9 draws in fixed order; costs always computed. */
        fun trial(notification: NotificationArrival, context: GenContext, day: Int): TrialDraws {
            val rng = GenRng(config.seed).fork(200L, notification.index.toLong())
            val uSelf    = rng.nextDouble()
            val destSelf = rng.nextDouble()
            val zDself   = rng.nextNormal(); val zRself = rng.nextNormal()
            val uPick    = rng.nextDouble()
            val uLatency = rng.nextDouble()
            val destNotif = rng.nextDouble()
            val zDnotif  = rng.nextNormal(); val zRnotif = rng.nextNormal()

            val lambdaPerHour = planner.lambdaPerHour(notification.timeMin)
            val basePickup = persona.effectivePickupProb.getOrDefault("${notification.mix.name}|${context.taskType}", 0.0)
            val driftedPickup = (basePickup * dayDrift(day)).coerceIn(0.0, 1.0)
            val pPickup = adjustPickup(driftedPickup, context.fatigueBand)

            val selfDelay = planner.selfDelayMin(notification.timeMin, -ln1p(-uSelf))
            val baselineExists = selfDelay < config.windowMin

            val picked = uPick < pPickup
            val latency = exp(persona.defaults.pickupLatency.mu + persona.defaults.pickupLatency.sigma * (-ln1p(-uLatency)))
            val notifEventExists = picked && latency < config.windowMin

            val owner = when {
                notifEventExists && (!baselineExists || latency <= selfDelay) -> Owner.NOTIF
                baselineExists -> Owner.SELF
                else -> Owner.NONE
            }

            val selfDestinations = persona.defaults.notificationMix.first { it.name == "social_notif" }.destinations
            val selfDest = choose(destSelf, selfDestinations)
            val notifDest = choose(destNotif, notification.mix.destinations)

            val dSelf = sampleDwell(persona.dwell.getValue(selfDest.category), zDself, day)
            val rSelf = exp(persona.resume.getValue(context.taskType).let { it.mu + it.sigma * zRself })
            val dNotif = sampleDwell(persona.dwell.getValue(notifDest.category), zDnotif, day)
            val rNotif = exp(persona.resume.getValue(context.taskType).let { it.mu + it.sigma * zRnotif })

            fun truncatedCost(onset: Double, d: Double, r: Double): Double {
                val raw = d + r
                return config.cGlanceMin + if (config.truncateCostsAtWindow) minOf(raw, maxOf(0.0, config.windowMin - onset)) else raw
            }

            val lShow = when (owner) {
                Owner.NOTIF -> truncatedCost(latency, dNotif, rNotif)
                Owner.SELF  -> truncatedCost(selfDelay, dSelf, rSelf)
                Owner.NONE  -> config.cGlanceMin
            }
            val lSuppress = if (baselineExists) dSelf + rSelf else 0.0

            return TrialDraws(uSelf, destSelf, zDself, zRself, uPick, uLatency, destNotif, zDnotif, zRnotif,
                lambdaPerHour, pPickup, selfDelay, latency, baselineExists, notifEventExists, owner, lShow, lSuppress)
        }

        var cursor = 0.0
        fun fillSelf(until: Double) {
            cursor = maxOf(cursor, busyUntil, windowUntil)
            while (cursor < until) {
                val rng = GenRng(config.seed).fork(300L, selfIndex++)
                val onset = cursor + planner.selfDelayMin(cursor, -ln1p(-rng.nextDouble()))
                if (onset >= until) { cursor = until; break }
                val destinations = persona.defaults.notificationMix.first { it.name == "social_notif" }.destinations
                val dest = choose(rng.nextDouble(), destinations)
                val zd = rng.nextNormal(); val zr = rng.nextNormal()
                val day = (onset / 1440.0).toInt().coerceIn(0, config.days - 1)
                cursor = episode(onset, dest, zd, zr, planner.context(onset, blocks), null, day)
            }
        }

        val truth = mutableListOf<TruthRecord>()
        val worldStr = config.world.name

        for (notification in NotificationProcess.generate(persona, config)) {
            val t = notification.timeMin
            fillSelf(t)
            phone(t, notification.payload())
            val day = (t / 1440.0).toInt().coerceIn(0, config.days - 1)
            val context = planner.context(t, blocks, notifClass = notification.mix.name)
            val inSession = t >= activeFrom && t < busyUntil
            val overlap = t < windowUntil
            val eligible = !inSession && !overlap

            // Always draw trial (draw-alignment guarantee)
            val draw = trial(notification, context, day)

            // Policy decision (separate RNG fork, does not affect world draws)
            val policyDecision = LoggingPolicyEngine.decide(
                config.loggingPolicy, notification, persona, config, inSession, notification.index
            )

            val realizedArm = if (eligible) policyDecision.arm else null
            val propensity  = if (eligible) policyDecision.propensity else 1.0

            truth.add(TruthRecord(
                persona.name, (t / 1440).toInt(), notification.index,
                Math.addExact(config.startEpochMs, (t * 60000).roundToLong()),
                context, inSession, eligible,
                if (inSession) "active_episode" else if (overlap) "overlapping_window" else null,
                draw, realizedArm, propensity,
                eligibleForExploration = eligible && policyDecision.eligibleForExploration,
                world = worldStr
            ))

            if (!eligible) {
                if (!inSession) phone(t + 1.0, GenPayload.Removed(notification.key, "auto"))
                continue
            }

            windowUntil = t + config.windowMin

            if (realizedArm == RealizedArm.SUPPRESS) {
                phone(t + 0.5, GenPayload.Removed(notification.key, "held"))
                if (draw.baselineExists) {
                    val selfDest = choose(draw.destSelf, persona.defaults.notificationMix.first { it.name == "social_notif" }.destinations)
                    episode(t + draw.selfDelayMin, selfDest, draw.zDself, draw.zRself, context, null, day)
                }
                continue
            }

            // SHOW arm
            when (draw.owner) {
                Owner.NONE -> phone(t + 1.0, GenPayload.Removed(notification.key, "auto"))
                else -> {
                    val notif = draw.owner == Owner.NOTIF
                    val onset = t + if (notif) draw.notifDelayMin else draw.selfDelayMin
                    val dest = choose(
                        if (notif) draw.destNotif else draw.destSelf,
                        if (notif) notification.mix.destinations else persona.defaults.notificationMix.first { it.name == "social_notif" }.destinations
                    )
                    episode(onset, dest,
                        if (notif) draw.zDnotif else draw.zDself,
                        if (notif) draw.zRnotif else draw.zRself,
                        context, if (notif) notification.key else null, day)
                    phone(if (notif) onset else maxOf(t + 1.0, busyUntil), GenPayload.Removed(notification.key, if (notif) "clicked" else "auto"))
                }
            }
        }

        fillSelf(horizon)

        val seq = linkedMapOf("phone" to 0L, "laptop" to 0L)
        val ids = GenRng(config.seed).fork(400L, 0L)
        fun word(): Long = (ids.nextDouble() * 4503599627370496.0).toLong().shl(12) xor (ids.nextDouble() * 4096).toLong()

        val events = pending.sortedWith(compareBy<Pending> { it.time }.thenBy { it.device }.thenBy { it.order }).map { p ->
            val reference = Math.addExact(config.startEpochMs, (p.time * 60000).roundToLong())
            val timestamp = Math.addExact(reference, config.deviceClockSkewMs.getValue(p.device))
            require(timestamp >= 0)
            val n = seq.getValue(p.device) + 1; seq[p.device] = n
            GenEvent(deterministicId(word(), word()), "synthetic-${persona.name.lowercase()}-${p.device}", timestamp, reference, p.device, n, p.payload)
        }.sortedWith(compareBy<GenEvent> { it.ts }.thenBy { it.device }.thenBy { it.seq })
        return GeneratedHistory(events, truth)
    }
}

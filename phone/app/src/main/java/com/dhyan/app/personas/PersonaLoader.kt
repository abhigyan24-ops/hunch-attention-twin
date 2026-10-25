package com.dhyan.app.personas

import com.dhyan.app.personas.json.JsonReader

class PersonaValidationException(errors: List<String>) : IllegalArgumentException(errors.joinToString("; ")) {
    val errors: List<String> = frozen(errors)
}

object PersonaLoader {
    fun load(json: String, defaults: PersonaDefaults? = null): PersonaDef {
        val parsed = try { JsonReader.parse(json) } catch (e: IllegalArgumentException) {
            throw PersonaValidationException(listOf("json: ${e.message}"))
        }
        val errors = mutableListOf<String>()
        fun obj(v: Any?, path: String): Map<*, *> = (v as? Map<*, *>) ?: run { errors.add("$path: expected object"); emptyMap<Any, Any>() }
        fun str(v: Any?, path: String): String = (v as? String)?.takeIf { it.isNotBlank() } ?: run { errors.add("$path: expected nonempty string"); "" }
        fun num(v: Any?, path: String): Double = (v as? Number)?.toDouble()?.takeIf { it.isFinite() } ?: run { errors.add("$path: expected finite number"); Double.NaN }
        fun arr(v: Any?, path: String): List<*> = (v as? List<*>) ?: run { errors.add("$path: expected array"); emptyList<Any>() }
        fun keys(m: Map<*, *>, allowed: Set<String>, path: String) { if (m.keys.any { it !in allowed }) errors.add("$path: unknown field") }
        val root = obj(parsed, "persona")
        keys(root, setOf("name", "role", "assumed", "_notes", "pickupProb", "dwell", "resume", "fatigueMultiplier", "focusPeaks", "selfInitiatedPerHour", "importantSenders"), "persona")
        val name = str(root["name"], "name"); val role = str(root["role"], "role")
        val assumed = if (root.containsKey("assumed")) (root["assumed"] as? Boolean) ?: run { errors.add("assumed: expected boolean"); false } else false
        val notes = if (root.containsKey("_notes")) str(root["_notes"], "_notes") else null
        val pickup = linkedMapOf<String, Double>()
        obj(root["pickupProb"], "pickupProb").forEach { (k, v) ->
            val key = str(k, "pickupProb key"); val n = num(v, "pickupProb.$key")
            if (n !in 0.0..1.0) errors.add("pickupProb.$key: outside [0,1]")
            pickup[key] = n
        }
        if (pickup.isEmpty()) errors.add("pickupProb: empty")
        fun distributions(field: String): Map<String, LogNormalSpec> {
            val result = linkedMapOf<String, LogNormalSpec>()
            obj(root[field], field).forEach { (k, v) ->
                val key = str(k, "$field key"); val m = obj(v, "$field.$key")
                keys(m, setOf("mu", "sigma"), "$field.$key")
                val mu = num(m["mu"], "$field.$key.mu"); val sigma = num(m["sigma"], "$field.$key.sigma")
                if (!(sigma > 0)) errors.add("$field.$key.sigma: must be positive")
                result[key] = LogNormalSpec(mu, sigma)
            }
            if (result.isEmpty()) errors.add("$field: empty")
            return result
        }
        val dwell = distributions("dwell"); val resume = distributions("resume")
        val fatigue = arr(root["fatigueMultiplier"], "fatigueMultiplier").mapIndexed { i, v -> num(v, "fatigueMultiplier[$i]") }
        if (fatigue.size != 3 || fatigue.any { !it.isFinite() || it < 1 }) errors.add("fatigueMultiplier: requires three values >=1")
        val hours = arr(root["focusPeaks"], "focusPeaks").mapIndexed { i, v ->
            val n = num(v, "focusPeaks[$i]"); if (n !in 0.0..23.0 || n % 1 != 0.0) errors.add("focusPeaks[$i]: integer hour required"); n.toInt()
        }
        if (hours.isEmpty() || hours.distinct().size != hours.size) errors.add("focusPeaks: nonempty unique hours required")
        val rate = num(root["selfInitiatedPerHour"], "selfInitiatedPerHour")
        if (!(rate >= 0)) errors.add("selfInitiatedPerHour: must be nonnegative")
        val senders = arr(root["importantSenders"], "importantSenders").mapIndexed { i, v -> str(v, "importantSenders[$i]") }
        if (senders.distinct().size != senders.size) errors.add("importantSenders: duplicate")
        val chosen = defaults ?: if (name in listOf("Rahul", "Anika", "Kabir")) DefaultPersonas.forName(name) else null
        if (chosen == null) errors.add("name: defaults required for unknown persona")
        if (chosen != null) {
            for ((key, probability) in chosen.assumedPickupProb) {
                if (!probability.isFinite() || probability !in 0.0..1.0) errors.add("assumedPickupProb: invalid probability")
                if (pickup.containsKey(key)) errors.add("assumedPickupProb: cannot override source")
            }
            for (block in chosen.workBlocks) {
                if (block.weekday !in 1..7 || block.startHour !in 0..23 || block.endHour !in 1..24 || block.startHour >= block.endHour) errors.add("workBlocks: invalid range")
                if (!resume.containsKey(block.taskType)) errors.add("resume: missing ${block.taskType}")
                for (mix in chosen.notificationMix) if (!pickup.containsKey("${mix.name}|${block.taskType}") && !chosen.assumedPickupProb.containsKey("${mix.name}|${block.taskType}")) errors.add("pickupProb: missing ${mix.name}|${block.taskType}")
            }
            for (mix in chosen.notificationMix) {
                if (!mix.ratePerHour.isFinite() || mix.ratePerHour < 0) errors.add("notificationMix: invalid rate")
                if (mix.destinations.isEmpty() || mix.destinations.any { !it.weight.isFinite() || it.weight <= 0 } || !mix.destinations.sumOf { it.weight }.isFinite()) errors.add("destinations: invalid weights")
                for (d in mix.destinations) if (!dwell.containsKey(d.category)) errors.add("dwell: missing ${d.category}")
            }
            if (!chosen.pickupLatency.mu.isFinite() || !chosen.pickupLatency.sigma.isFinite() || chosen.pickupLatency.sigma <= 0) errors.add("pickupLatency: invalid distribution")
        }
        if (errors.isNotEmpty()) throw PersonaValidationException(errors.distinct())
        return PersonaDef(name, role, assumed, notes, pickup, dwell, resume, fatigue, hours, rate, senders, chosen!!)
    }
}

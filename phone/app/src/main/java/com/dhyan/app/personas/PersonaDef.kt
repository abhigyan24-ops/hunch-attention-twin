package com.dhyan.app.personas

import java.util.Collections

internal fun <T> frozen(values: List<T>): List<T> = Collections.unmodifiableList(ArrayList(values))
internal fun <K, V> frozen(values: Map<K, V>): Map<K, V> = Collections.unmodifiableMap(LinkedHashMap(values))

data class LogNormalSpec(val mu: Double, val sigma: Double)
data class Destination(val category: String, val surface: String, val weight: Double)
data class WorkBlock(val weekday: Int, val startHour: Int, val endHour: Int, val taskType: String)

/** Values are copied and frozen: caller-owned mutable collections cannot change hidden truth. */
class NotificationClass(val name: String, val appCat: String, val ratePerHour: Double, destinations: List<Destination>) {
    val destinations = frozen(destinations)
}
class PersonaDefaults(
    notificationMix: List<NotificationClass>, workBlocks: List<WorkBlock>,
    val pickupLatency: LogNormalSpec, val timezone: String = "Asia/Kolkata",
    assumedPickupProb: Map<String, Double> = emptyMap()
) {
    val assumedPickupProb = frozen(assumedPickupProb)
    val notificationMix = frozen(notificationMix)
    val workBlocks = frozen(workBlocks)
}
class PersonaDef internal constructor(
    val name: String, val role: String, val assumed: Boolean, val notes: String?,
    pickupProb: Map<String, Double>, dwell: Map<String, LogNormalSpec>, resume: Map<String, LogNormalSpec>,
    fatigueMultiplier: List<Double>, focusPeaks: List<Int>, val selfInitiatedPerHour: Double,
    importantSenders: List<String>, val defaults: PersonaDefaults
) {
    val pickupProb = frozen(pickupProb)
    val effectivePickupProb = frozen(defaults.assumedPickupProb + pickupProb)
    val dwell = frozen(dwell)
    val resume = frozen(resume)
    val fatigueMultiplier = frozen(fatigueMultiplier)
    val focusPeaks = frozen(focusPeaks)
    val importantSenders = frozen(importantSenders)
}

/** A4-2 assumed fixtures, not measured arrival rates or schedules. */
object DefaultPersonas {
    fun forName(name: String): PersonaDefaults {
        val student = name == "Anika"
        require(name in listOf("Rahul", "Anika", "Kabir")) { "Unknown persona defaults" }
        val destinations = listOf(Destination("social", "feed", 0.6), Destination("short_video", "shorts", 0.4))
        val mix = mutableListOf(
            NotificationClass("social_notif", "social", 0.6, destinations),
            NotificationClass("msg_known", "communication", 0.3, destinations),
            NotificationClass("promo", "shopping", 0.2, destinations)
        )
        if (student) {
            mix.add(NotificationClass("lecture_watch", "learning", 0.2, listOf(Destination("learning", "watch", 1.0))))
            mix.add(NotificationClass("shorts", "short_video", 0.4, listOf(Destination("short_video", "shorts", 1.0))))
        }
        // Explicit A4-2 additions; the source JSON remains unchanged.
        val blocks = (1..5).flatMap { day ->
            if (student) listOf(WorkBlock(day, 18, 20, "studying"), WorkBlock(day, 21, 24, "studying"))
            else listOf(WorkBlock(day, 10, 12, "coding"), WorkBlock(day, 14, 17, "coding"))
        }
        val gaming = if (name == "Kabir") (1..7).map { WorkBlock(it, 20, 23, "gaming") } else emptyList()
        val extra = if (name == "Kabir") mapOf("social_notif|gaming" to 0.05, "msg_known|gaming" to 0.06, "promo|gaming" to 0.015) else emptyMap()
        return PersonaDefaults(mix, blocks + gaming, LogNormalSpec(-1.6, 0.5), assumedPickupProb = extra)
    }
}

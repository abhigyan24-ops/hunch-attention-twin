package com.dhyan.app.personas.world

import com.dhyan.app.personas.*
import java.time.Instant
import java.time.ZoneId

data class PlannedBlock(val dayIndex: Int, val startMin: Double, val endMin: Double, val taskType: String)
/** notifClass is set at trial time (empty string for self-initiated events). */
data class GenContext(val taskType: String, val hourBand: String, val fatigueBand: Int, val hoursWorked: Double, val notifClass: String = "")

class DayPlanner(private val persona: PersonaDef, private val config: GeneratorConfig) {
    private val zone = ZoneId.of(persona.defaults.timezone)
    private val start = Instant.ofEpochMilli(config.startEpochMs)
    fun plan(dayIndex: Int): List<PlannedBlock> {
        val date = start.atZone(zone).toLocalDate().plusDays(dayIndex.toLong())
        return persona.defaults.workBlocks.filter { it.weekday == date.dayOfWeek.value }.sortedBy { it.startHour }.map {
            fun minutes(hour: Int): Double = (date.atStartOfDay(zone).plusHours(hour.toLong()).toInstant().toEpochMilli() - config.startEpochMs) / 60000.0
            PlannedBlock(dayIndex, minutes(it.startHour).coerceAtLeast(0.0), minutes(it.endHour).coerceAtMost(config.days * 1440.0), it.taskType)
        }.filter { it.endMin > it.startMin }
    }
    fun localHour(timeMin: Double): Int = start.plusMillis((timeMin * 60000).toLong()).atZone(zone).hour
    fun context(timeMin: Double, blocks: List<PlannedBlock>, switchRateZ: Double = 0.0, stallRatio: Double = 0.0, notifClass: String = ""): GenContext {
        val active = blocks.firstOrNull { timeMin >= it.startMin && timeMin < it.endMin }
        val day = start.plusMillis((timeMin * 60000).toLong()).atZone(zone).toLocalDate()
        val worked = blocks.filter { start.plusMillis((it.startMin * 60000).toLong()).atZone(zone).toLocalDate() == day }
            .sumOf { (timeMin.coerceAtMost(it.endMin) - it.startMin).coerceAtLeast(0.0) } / 60.0
        // Outside scheduled blocks, explicit assumption: retain the first supported task.
        val task = active?.taskType ?: if (persona.name == "Anika") "studying" else "coding"
        return GenContext(task, hourBand(localHour(timeMin)), fatigueBand(worked, switchRateZ, stallRatio), worked, notifClass)
    }
    fun lambdaPerHour(timeMin: Double): Double {
        val hour = localHour(timeMin)
        return persona.selfInitiatedPerHour * (if (hour in persona.focusPeaks) 0.6 else 1.0) * (if (hour >= 22) 1.2 else 1.0)
    }
    /** Integrated piecewise hourly hazard, consuming an already drawn unit exponential. */
    fun selfDelayMin(timeMin: Double, unitHazard: Double): Double {
        if (persona.selfInitiatedPerHour == 0.0) return Double.POSITIVE_INFINITY
        var remaining = unitHazard; var t = timeMin
        while (true) {
            val current = start.plusMillis((t * 60000).toLong()).atZone(zone)
            val nextHour = current.withMinute(0).withSecond(0).withNano(0).plusHours(1)
            val boundary = (nextHour.toInstant().toEpochMilli() - config.startEpochMs) / 60000.0
            val length = boundary - t
            val rate = lambdaPerHour(t) / 60.0
            if (remaining < length * rate) return t - timeMin + remaining / rate
            remaining -= length * rate; t = boundary
        }
    }
    companion object {
        fun hourBand(hour: Int): String { require(hour in 0..23); return when (hour) { in 0..5 -> "night"; in 6..11 -> "morning"; in 12..16 -> "afternoon"; in 17..21 -> "evening"; else -> "late" } }
        fun fatigueBand(hoursWorked: Double, switchRateZ: Double, stallRatio: Double): Int {
            val f = (0.5 * hoursWorked / 6 + 0.3 * switchRateZ + 0.2 * stallRatio).coerceIn(0.0, 1.0)
            return if (f < 0.33) 0 else if (f < 0.66) 1 else 2
        }
    }
}

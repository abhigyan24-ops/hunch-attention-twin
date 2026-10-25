package com.dhyan.app.personas.export

import com.dhyan.app.personas.*
import com.dhyan.app.personas.world.*
import java.io.File

/**
 * 4A-4: Write TruthRecord list plus config/summary to a JSON file.
 * Format: one JSON object with keys: config, summary, records (array).
 */
object TruthJsonWriter {

    fun writeToFile(
        records: List<TruthRecord>,
        persona: PersonaDef,
        config: GeneratorConfig,
        file: File
    ) {
        file.parentFile?.mkdirs()
        val summary = TrueEffect.summarize(records)
        val obj = linkedMapOf<String, Any?>(
            "persona" to persona.name,
            "config" to linkedMapOf(
                "seed" to config.seed,
                "world" to config.world.name,
                "loggingPolicy" to when (val p = config.loggingPolicy) {
                    LoggingPolicy.ShowAll -> "ShowAll"
                    is LoggingPolicy.RandomizedHold -> "RandomizedHold(eps=${p.eps},maxImportance=${p.maxImportance})"
                },
                "startEpochMs" to config.startEpochMs,
                "days" to config.days,
                "windowMin" to config.windowMin,
                "cGlanceMin" to config.cGlanceMin,
                "truncateCostsAtWindow" to config.truncateCostsAtWindow,
                "synthetic" to true
            ),
            "summary" to linkedMapOf(
                "meanDeltaLByClass" to summary.meanDeltaLByClass,
                "meanDeltaLByHour" to summary.meanDeltaLByHour,
                "countByClass" to summary.countByClass,
                "countByHour" to summary.countByHour,
                "negDeltaLCount" to summary.negDeltaLCount
            ),
            "records" to records.map { r ->
                val base = linkedMapOf<String, Any?>(
                    "persona" to r.persona,
                    "dayIndex" to r.dayIndex,
                    "notificationIndex" to r.notificationIndex,
                    "ts" to r.ts,
                    "context" to linkedMapOf(
                        "taskType" to r.context.taskType,
                        "hourBand" to r.context.hourBand,
                        "fatigueBand" to r.context.fatigueBand,
                        "hoursWorked" to r.context.hoursWorked,
                        "notifClass" to r.context.notifClass
                    ),
                    "inSession" to r.inSession,
                    "trialEligible" to r.trialEligible,
                    "exclusionReason" to r.exclusionReason,
                    "realizedArm" to r.realizedArm?.name,
                    "propensity" to r.propensity,
                    "eligibleForExploration" to r.eligibleForExploration,
                    "world" to r.world
                )
                r.trial?.let { t ->
                    base["lShowMin"] = t.lShowMin
                    base["lSuppressMin"] = t.lSuppressMin
                    base["deltaLMin"] = t.deltaLMin
                    base["owner"] = t.owner.name
                    base["baselineExists"] = t.baselineExists
                    base["notifEventExists"] = t.notifEventExists
                    base["pPickup"] = t.pPickup
                    base["lambdaPerHour"] = t.lambdaPerHour
                }
                base
            }
        )
        file.writer(Charsets.UTF_8).use { it.write(DeterministicJson.write(obj)); it.write("\n") }
    }
}

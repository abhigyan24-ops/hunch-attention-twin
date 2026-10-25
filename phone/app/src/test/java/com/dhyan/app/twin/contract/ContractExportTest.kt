package com.dhyan.app.twin.contract

import com.dhyan.app.twin.model.*
import org.junit.Test
import java.io.File

class ContractExportTest {

    @Test
    fun exportContractFilesForValidation() {
        val buildDir = File("build/contract").apply { mkdirs() }

        // 1. Export context-snapshots.json covering enum values
        val snapshots = listOf(
            ContextSnapshot(
                ts = 1730000000000L,
                focusState = FocusState.DEEP_FOCUS,
                taskType = TaskType.CODING,
                taskSource = TaskSource.DECLARED,
                confidence = 0.95,
                hourBand = "evening",
                minutesInState = 52.0,
                fatigueBand = "med",
                recentInterrupts30m = 1,
                laptopState = LaptopState.FLOWING,
                phoneState = PhoneState.SCREEN_OFF,
                secondScreenOverlap = false,
                declaredSession = DeclaredSession(
                    task = TaskType.CODING,
                    plannedMin = 120.0,
                    strict = true,
                    startedTs = 1730000000000L
                )
            ),
            ContextSnapshot(
                ts = 1730003600000L,
                focusState = FocusState.WORKING,
                taskType = TaskType.STUDYING,
                taskSource = TaskSource.INFERRED,
                confidence = 0.80,
                hourBand = "morning",
                minutesInState = 15.0,
                fatigueBand = "low",
                recentInterrupts30m = 0,
                laptopState = LaptopState.IDLE_OPEN,
                phoneState = PhoneState.IN_APP,
                secondScreenOverlap = true,
                declaredSession = null
            ),
            ContextSnapshot(
                ts = 1730007200000L,
                focusState = FocusState.DRIFTING,
                taskType = TaskType.BROWSING,
                taskSource = TaskSource.INFERRED,
                confidence = 0.60,
                hourBand = "afternoon",
                minutesInState = 30.0,
                fatigueBand = "high",
                recentInterrupts30m = 4,
                laptopState = LaptopState.BROWSING,
                phoneState = PhoneState.SCROLLING,
                secondScreenOverlap = true,
                declaredSession = null
            ),
            ContextSnapshot(
                ts = 1730010800000L,
                focusState = FocusState.DISTRACTED,
                taskType = TaskType.GAMING,
                taskSource = TaskSource.INFERRED,
                confidence = 0.75,
                hourBand = "late",
                minutesInState = 40.0,
                fatigueBand = "high",
                recentInterrupts30m = 5,
                laptopState = LaptopState.GAMING,
                phoneState = PhoneState.IN_APP,
                secondScreenOverlap = false,
                declaredSession = null
            ),
            ContextSnapshot(
                ts = 1730014400000L,
                focusState = FocusState.RECOVERING,
                taskType = TaskType.WRITING,
                taskSource = TaskSource.DECLARED,
                confidence = 0.85,
                hourBand = "night",
                minutesInState = 10.0,
                fatigueBand = "low",
                recentInterrupts30m = 0,
                laptopState = LaptopState.STUCK,
                phoneState = PhoneState.SCREEN_OFF,
                secondScreenOverlap = false,
                declaredSession = null
            ),
            ContextSnapshot(
                ts = 1730018000000L,
                focusState = FocusState.BREAK,
                taskType = TaskType.UNKNOWN,
                taskSource = TaskSource.INFERRED,
                confidence = 0.50,
                hourBand = "morning",
                minutesInState = 20.0,
                fatigueBand = "low",
                recentInterrupts30m = 0,
                laptopState = LaptopState.AWAY,
                phoneState = PhoneState.SCREEN_OFF,
                secondScreenOverlap = false,
                declaredSession = null
            ),
            ContextSnapshot(
                ts = 1730021600000L,
                focusState = FocusState.WORKING,
                taskType = TaskType.CODING,
                taskSource = TaskSource.INFERRED,
                confidence = 0.70,
                hourBand = "evening",
                minutesInState = 25.0,
                fatigueBand = "med",
                recentInterrupts30m = 2,
                laptopState = LaptopState.OFFLINE,
                phoneState = PhoneState.IN_APP,
                secondScreenOverlap = false,
                declaredSession = null
            )
        )

        val jsonSnapshots = buildString {
            append("[\n")
            snapshots.forEachIndexed { idx, s ->
                append("  {\n")
                append("    \"ts\": ${s.ts},\n")
                append("    \"focusState\": \"${s.focusState.wire}\",\n")
                append("    \"taskType\": \"${s.taskType.wire}\",\n")
                append("    \"taskSource\": \"${s.taskSource.wire}\",\n")
                append("    \"confidence\": ${s.confidence},\n")
                append("    \"hourBand\": \"${s.hourBand}\",\n")
                append("    \"minutesInState\": ${s.minutesInState},\n")
                append("    \"fatigueBand\": \"${s.fatigueBand}\",\n")
                append("    \"recentInterrupts30m\": ${s.recentInterrupts30m},\n")
                append("    \"laptopState\": \"${s.laptopState.wire}\",\n")
                append("    \"phoneState\": \"${s.phoneState.wire}\",\n")
                append("    \"secondScreenOverlap\": ${s.secondScreenOverlap},\n")
                if (s.declaredSession == null) {
                    append("    \"declaredSession\": null\n")
                } else {
                    append("    \"declaredSession\": {\n")
                    append("      \"task\": \"${s.declaredSession.task.wire}\",\n")
                    append("      \"plannedMin\": ${s.declaredSession.plannedMin},\n")
                    append("      \"strict\": ${s.declaredSession.strict},\n")
                    append("      \"startedTs\": ${s.declaredSession.startedTs}\n")
                    append("    }\n")
                }
                append("  }${if (idx < snapshots.size - 1) "," else ""}\n")
            }
            append("]\n")
        }

        File(buildDir, "context-snapshots.json").writeText(jsonSnapshots)

        // 2. Export twin-config.json
        val cfg = TwinConfig.default()
        val jsonConfig = buildString {
            append("{\n")
            append("  \"monteCarloN\": ${cfg.monteCarloN},\n")
            append("  \"windowMin\": ${cfg.windowMin},\n")
            append("  \"kappa\": ${cfg.kappa},\n")
            append("  \"gamma\": ${cfg.gamma},\n")
            append("  \"lognormPriorN\": ${cfg.lognormPriorN},\n")
            append("  \"sigmaMin\": ${cfg.sigmaMin},\n")
            append("  \"cGlanceMin\": ${cfg.cGlanceMin},\n")
            append("  \"configVersion\": \"${cfg.configVersion}\"\n")
            append("}\n")
        }

        File(buildDir, "twin-config.json").writeText(jsonConfig)
    }
}

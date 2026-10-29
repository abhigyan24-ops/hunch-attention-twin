package com.dhyan.app.ui.tabs

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dhyan.app.ui.components.*
import com.dhyan.app.ui.model.*
import com.dhyan.app.ui.viewmodel.SimulateViewModel
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun SimulateTab(stateHolder: DhyanStateHolder? = null, modifier: Modifier = Modifier) {
    val viewModel = remember { SimulateViewModel() }
    SimulateTab(viewModel, modifier)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SimulateTab(viewModel: SimulateViewModel, modifier: Modifier = Modifier) {
    val state by viewModel.uiState.collectAsState()
    var advanced by rememberSaveable { mutableStateOf(false) }
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Text("Make room\nfor focus.", style = MaterialTheme.typography.headlineLarge)
        Text("Explore how an interruption could change your next few minutes.",
            style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        SimCard("A safe place to experiment") {
            Text("Synthetic personas · in-memory simulation. This does not change your live notifications.",
                style = MaterialTheme.typography.bodyMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(PersonaCatalog.RAHUL, PersonaCatalog.ANIKA, PersonaCatalog.KABIR).forEach { persona ->
                    ContextChip(persona.name.substringBefore(" "), state.selectedPersona.id == persona.id,
                        { viewModel.selectPersona(persona) })
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Text("Learn from a replay", style = MaterialTheme.typography.titleMedium)
                    Text("14 days of synthetic history", style = MaterialTheme.typography.bodySmall)
                }
                Switch(state.isTrained, { viewModel.setTrained(it) })
            }
            if (state.isReplaying) {
                LinearProgressIndicator(progress = { state.replayProgress.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth())
                Text("Preparing synthetic history…", style = MaterialTheme.typography.bodySmall)
            }
        }
        SimCard("Your context") {
            Text("Primary task", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Coding", "Studying", "Writing", "Gaming", "Browsing").forEach { task ->
                    ContextChip(task, state.taskType.equals(task, true), { viewModel.setTask(task.lowercase()) })
                }
            }
            Text(String.format(Locale.US, "Hours worked today · %.1f h", state.hoursWorked),
                style = MaterialTheme.typography.titleSmall)
            Slider(state.hoursWorked.toFloat(), { viewModel.setHoursWorked(it.toDouble()) }, valueRange = 0f..10f)
            Text("Fatigue uses neutral switch-rate and stall inputs in this sandbox.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        SimCard("The interruption") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(Triple("Social", "social", "social_notif"), Triple("Known contact", "communication", "msg"),
                    Triple("Promo", "shopping", "promo"), Triple("Work docs", "docs", "alert")).forEach { (label, app, notif) ->
                    ContextChip(label, state.simCategory == app && state.simNotifCat == notif,
                        { viewModel.setCategory(app, notif) })
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Text("Trusted sender", style = MaterialTheme.typography.titleMedium)
                    Text("Never-hold safety rule", style = MaterialTheme.typography.bodySmall)
                }
                Switch(state.simIsVip, { viewModel.setIsVip(it) })
            }
        }
        TextButton(onClick = { advanced = !advanced }) {
            Text(if (advanced) "Hide context details −" else "More context options +")
        }
        if (advanced) SimCard("Context details") {
            Text("Attention label · display context only; it does not affect the verdict.",
                style = MaterialTheme.typography.bodySmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(AttentionState.DEEP_FOCUS, AttentionState.WORKING, AttentionState.DRIFTING,
                    AttentionState.DISTRACTED).forEach { attention ->
                    ContextChip(attention.label, state.attentionState == attention, { viewModel.setAttentionState(attention) })
                }
            }
            Text("Minutes in state · ${state.minutesInState}")
            Slider(state.minutesInState.toFloat(), { viewModel.setMinutesInState(it.roundToInt()) }, valueRange = 5f..120f)
            Text("Hour of day · ${state.currentHour}:00 · ${state.hourBand}")
            Slider(state.currentHour.toFloat(), { viewModel.setCurrentHour(it.roundToInt()) }, valueRange = 0f..23f)
            Text(String.format(Locale.US, "Urgency · %.2f", state.simUrgency))
            Slider(state.simUrgency.toFloat(), { viewModel.setUrgency(it.toDouble()) }, valueRange = 0f..1f)
            Text("Repeat count · ${state.simRepeatCount}")
            Slider(state.simRepeatCount.toFloat().coerceIn(0f, 10f),
                { viewModel.setRepeatCount(it.roundToInt()) }, valueRange = 0f..10f, steps = 9)
        }
        if (state.isFailOpen) {
            SimCard("No reliable comparison yet") {
                Text("The policy is allowing this interruption. Reason: ${state.reasonCode}",
                    style = MaterialTheme.typography.bodyLarge)
                Text("A not-ready model is not a zero-loss prediction.", style = MaterialTheme.typography.bodySmall)
            }
        } else {
            VerdictCard(state.simulationResult.verdict, state.simulationResult.explanation)
            TimelineBars(state.simulationResult)
        }
        SimCard("Model evidence") {
            Text(state.provenance, style = MaterialTheme.typography.bodyMedium)
            Text(String.format(Locale.US, "Effective evidence %.1f · model v%d", state.nEff, state.modelVersion),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = { viewModel.toggleTwinLab(!state.isTwinLabExpanded) }) {
                Text(if (state.isTwinLabExpanded) "Close Twin Lab −" else "Open Twin Lab +")
            }
            if (state.isTwinLabExpanded) {
                if (state.isFailOpen) Text("Posterior comparison is unavailable until the model is ready.")
                else BayesianCurveCard(state.simulationResult.betaAlpha, state.simulationResult.betaBeta,
                    state.simulationResult.lognormMu, state.simulationResult.lognormSigma,
                    state.simulationResult.updateCount, state.nEff, state.modelVersion, state.priorAlpha, state.priorBeta)
            }
        }
    }
}

@Composable
private fun SimCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            content()
        }
    }
}

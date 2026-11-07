package com.dhyan.app.ui.tabs

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.dhyan.app.ui.components.*
import com.dhyan.app.ui.model.*
import com.dhyan.app.ui.viewmodel.SimulateViewModel
import com.dhyan.app.ui.viewmodel.SimulateUiState
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun SimulateTab(stateHolder: DhyanStateHolder? = null, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val db = remember(context) { com.dhyan.app.data.DhyanDatabase.getInstance(context) }
    val viewModel = remember(db) { SimulateViewModel(appLabelDao = db.appLabelDao()) }
    SimulateTab(viewModel, modifier)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SimulateTab(viewModel: SimulateViewModel, modifier: Modifier = Modifier) {
    val state by viewModel.uiState.collectAsState()
    var advanced by rememberSaveable { mutableStateOf(false) }
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)) {
        PastelSquircleCard(tone = PastelTone.SAGE) {
            StatusPillBadge("Twin Lab · Research Sandbox")
            Text(androidx.compose.ui.text.buildAnnotatedString {
                append("one. two.\n")
                pushStyle(androidx.compose.ui.text.SpanStyle(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold))
                append("focus."); pop()
            }, style = MaterialTheme.typography.headlineLarge.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Light))
            Text("Interactive attention dynamics and synthetic persona simulations.", style = MaterialTheme.typography.bodyLarge)
        }
        SimCard("A safe place to experiment") {
            Text("Digital Twin profile simulation. This does not change your live notifications.",
                style = MaterialTheme.typography.bodyMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(PersonaCatalog.REAL_USER, PersonaCatalog.RAHUL, PersonaCatalog.ANIKA, PersonaCatalog.KABIR).forEach { persona ->
                    ContextChip(persona.name.substringBefore(" "), state.selectedPersona.id == persona.id,
                        { viewModel.selectPersona(persona) })
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Text("Learn from a replay", style = MaterialTheme.typography.titleMedium)
                    Text("Synthetic or historical trace", style = MaterialTheme.typography.bodySmall)
                }
                Switch(state.isTrained, { viewModel.setTrained(it) })
            }
            if (state.isReplaying) {
                LinearProgressIndicator(progress = { state.replayProgress.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth())
                Text("Preparing trace replay…", style = MaterialTheme.typography.bodySmall)
            }
        }
        SimCard("Your context") {
            Text("Primary task", style = MaterialTheme.typography.titleSmall)
            listOf("Coding", "Studying", "Writing", "Gaming", "Browsing").chunked(2).forEachIndexed { row, tasks ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    tasks.forEachIndexed { column, task ->
                        Surface(onClick = { viewModel.setTask(task.lowercase()) },
                            modifier = Modifier.weight(1f).testTag("task-${task.lowercase()}").semantics {
                                selected = state.taskType.equals(task, true)
                                role = Role.RadioButton
                            }, shape = androidx.compose.foundation.shape.RoundedCornerShape(28.dp),
                            color = pastelSurface(listOf(PastelTone.SAGE, PastelTone.LAVENDER, PastelTone.SAND, PastelTone.SKY)[(row * 2 + column) % 4]),
                            border = androidx.compose.foundation.BorderStroke(if (state.taskType.equals(task, true)) 2.dp else 0.dp,
                                if (state.taskType.equals(task, true)) MaterialTheme.colorScheme.onSurface else androidx.compose.ui.graphics.Color.Transparent)) {
                            Text(task, Modifier.padding(20.dp), style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }
            }
            SemicircularTickDial(state.hoursWorked.toFloat(), 0f..10f,
                String.format(Locale.US, "%.1f", state.hoursWorked), "hours worked today",
                onValueChange = { viewModel.setHoursWorked(it.toDouble()) })
            Slider(state.hoursWorked.toFloat(), { viewModel.setHoursWorked(it.toDouble()) }, valueRange = 0f..10f)
            Text("Fatigue uses neutral switch-rate and stall inputs in this sandbox.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        SimCard("The interruption (Real Apps)") {
            Text("Observed apps from phone & laptop", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                state.availableApps.forEach { appOption ->
                    ContextChip(
                        label = appOption.label,
                        isSelected = state.simApp == appOption.label,
                        onClick = { viewModel.selectApp(appOption) }
                    )
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
            CounterfactualComparisonCard(state)
            PearlAbductionCard(state, onToggle = { viewModel.toggleAbductionDrawer(!state.abductionReplay.isExpanded) })
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
        Spacer(Modifier.height(84.dp))
    }
}

@Composable
private fun CounterfactualComparisonCard(state: SimulateUiState) {
    PastelSquircleCard(tone = PastelTone.SAGE) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("Counterfactual Arm Comparison", style = MaterialTheme.typography.titleMedium)
                Text("Paired Monte Carlo under Common Random Numbers (CRN)", style = MaterialTheme.typography.bodySmall)
            }
            StatusPillBadge(
                text = String.format(Locale.US, "Δ = +%.1f min", state.netFocusSavedMin),
                active = true
            )
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            // Arm Y(1) - Interrupted
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Arm Y(1) · Interrupted", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error)
                Text(
                    text = String.format(Locale.US, "%.1f min lost", state.y1TotalLostMin),
                    style = MaterialTheme.typography.titleLarge
                )
                Text(
                    text = String.format(Locale.US, "• Excursion: %.1f m\n• Resume: %.1f m", state.y1ExcursionMin, state.y1ResumeMin),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Arm Y(0) - Held in Silence
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Arm Y(0) · Held", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Text(
                    text = String.format(Locale.US, "%.1f min lost", state.y0TotalLostMin),
                    style = MaterialTheme.typography.titleLarge
                )
                Text(
                    text = "• Zero excursion\n• Continuous focus",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Text(
            text = String.format(Locale.US, "80%% Simulation Interval: [%.1f min, %.1f min] (CRN SE ±%.2f)",
                state.ciLowMin, state.ciHighMin, state.simulationResult.expectedLostShowMin * 0.08),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun PearlAbductionCard(state: SimulateUiState, onToggle: () -> Unit) {
    PastelSquircleCard(tone = PastelTone.LAVENDER) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("Pearl's Abduction Replay (SCM)", style = MaterialTheme.typography.titleMedium)
                Text("Structural Causal Model counterfactual inference", style = MaterialTheme.typography.bodySmall)
            }
            TextButton(onClick = onToggle) {
                Text(if (state.abductionReplay.isExpanded) "Hide −" else "View +")
            }
        }

        if (state.abductionReplay.isExpanded) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Step 1: Abduction (Exogenous Background Shock)", style = MaterialTheme.typography.labelMedium)
                Text(state.abductionReplay.abductedShockU, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)

                Text("Step 2: Action (Counterfactual Intervention)", style = MaterialTheme.typography.labelMedium)
                Text("Do(T = HOLD) replaces original delivery under identical state.", style = MaterialTheme.typography.bodySmall)

                Text("Step 3: Prediction (Modified Structural Equation)", style = MaterialTheme.typography.labelMedium)
                Text(
                    String.format(Locale.US, "Net Attention Preserved: +%.1f focus minutes recovered", state.netFocusSavedMin),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
private fun SimCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    PastelSquircleCard {
        Text(title, style = MaterialTheme.typography.titleLarge)
        content()
    }
}

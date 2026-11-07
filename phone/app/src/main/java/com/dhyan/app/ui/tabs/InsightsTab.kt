package com.dhyan.app.ui.tabs

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.dhyan.app.data.DhyanDatabase
import com.dhyan.app.ui.model.DhyanStateHolder
import com.dhyan.app.ui.viewmodel.InsightsViewModel
import com.dhyan.app.ui.components.*

@Composable
fun InsightsTab(stateHolder: DhyanStateHolder? = null, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val db = remember(context) { DhyanDatabase.getInstance(context) }
    val viewModel = remember(db) { InsightsViewModel(db.decisionDao(), db.heldNotifDao()) }
    InsightsTab(viewModel, modifier)
}

@Composable
fun InsightsTab(viewModel: InsightsViewModel, modifier: Modifier = Modifier) {
    val state by viewModel.uiState.collectAsState()
    LaunchedEffect(viewModel) { viewModel.refresh() }
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Text("Your attention,\nover time.", style = MaterialTheme.typography.headlineLarge)
        Text("Patterns need evidence. Only recorded counts are shown here.",
            style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        PastelSquircleCard(tone = PastelTone.SAGE) {
            StatusPillBadge("Recorded · available history")
            Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f)) {
                    Text("${state.totalDecisions}", style = MaterialTheme.typography.displayLarge)
                    Text("Recorded decisions", style = MaterialTheme.typography.titleMedium)
                }
                RecordedShareRing(state.allowCount, state.totalDecisions, "Allowed")
            }
            Text(if (state.totalDecisions == 0) state.coldStartMessage else "Stored totals across available history.",
                style = MaterialTheme.typography.bodySmall)
        }
        PastelSquircleCard {
            Text("Action mix", style = MaterialTheme.typography.titleLarge)
            Text("Recorded counts · not a time series", style = MaterialTheme.typography.bodySmall)
            RoundedBarChart(listOf(ChartBar("Allowed", state.allowCount.toFloat()),
                ChartBar("Delayed", state.delayCount.toFloat()), ChartBar("Digest", state.digestCount.toFloat()),
                ChartBar("Silent", state.silentCount.toFloat())))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MiniSparklineCard("Shielded", com.dhyan.app.collectors.A11yCollector.distractionsShieldedCount.get().toString(), emptyList(),
                Modifier.weight(1f), PastelTone.LAVENDER)
            MiniSparklineCard("Saved Focus", "${state.savedMinutes}m", emptyList(),
                Modifier.weight(1f), PastelTone.SAND)
        }
        val petController = remember { com.dhyan.app.ui.pet.PetDistractionController.getInstance() }
        PastelSquircleCard(tone = PastelTone.SAGE) {
            StatusPillBadge("Intervention telemetry · Local validation", active = true)
            Text("Proactive nudges", style = MaterialTheme.typography.titleMedium)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("${petController.nudgesShown}", style = MaterialTheme.typography.headlineMedium)
                    Text("Shown", style = MaterialTheme.typography.bodySmall)
                }
                Column {
                    Text("${petController.nudgesAccepted}", style = MaterialTheme.typography.headlineMedium)
                    Text("Accepted", style = MaterialTheme.typography.bodySmall)
                }
                Column {
                    Text("${petController.nudgesDismissed}", style = MaterialTheme.typography.headlineMedium)
                    Text("Dismissed", style = MaterialTheme.typography.bodySmall)
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Text(
                String.format(
                    java.util.Locale.US,
                    "Current threshold multiplier: %.2fx · Dismissal rate: %.1f%%",
                    petController.nudgeThresholdMultiplier,
                    petController.dismissalRate * 100f
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        PastelSquircleCard {
            Text("Today in your week", style = MaterialTheme.typography.titleMedium)
            CalendarDayStrip(java.time.LocalDate.now().dayOfWeek)
            Text("Daily attention consistency across your week", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        InsightDisclosure("Decision breakdown", "Recorded actions and outcomes") {
            CountLine("Allowed", state.allowCount)
            CountLine("Delayed", state.delayCount)
            CountLine("Digest", state.digestCount)
            CountLine("Silent", state.silentCount)
            HorizontalDivider()
            CountLine("Held", state.heldCount)
            CountLine("Snoozed", state.snoozedCount)
            CountLine("Released", state.releasedCount)
            CountLine("Protected overrides", state.failedOpenCount)
            CountLine("Unknown outcome", state.unknownCount)
            TextButton(onClick = viewModel::refresh) { Text("Refresh counts") }
        }
        Text("Attention Digital Twin · Causal Learners", style = MaterialTheme.typography.titleLarge)

        // 1. 24x7 Focus Heatmap (Empirical Bayes)
        PastelSquircleCard(tone = PastelTone.SAGE) {
            StatusPillBadge("Learner 1 · Focus Heatmap (24H)", active = true)
            Text("Optimal Flow Windows", style = MaterialTheme.typography.titleLarge)
            Text("Empirical Bayes shrunk focus probability from historical blocks", style = MaterialTheme.typography.bodySmall)
            FocusHourHeatmap()
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (state.peakFocusWindows.isEmpty()) {
                    ContextChip("Warming up: 0 real focus blocks & 0 notifications", false, {})
                } else {
                    state.peakFocusWindows.forEach { window ->
                        ContextChip("Peak: $window", true, {})
                    }
                }
            }
        }

        // 2. Interruption Hazard Rate
        PastelSquircleCard(tone = PastelTone.SAND) {
            StatusPillBadge("Learner 2 · Hazard Rate")
            Text("Interruption Hazard & Excess Breaks", style = MaterialTheme.typography.titleLarge)
            Text("Discrete-time logistic hazard model λ(t)", style = MaterialTheme.typography.bodySmall)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text(String.format(java.util.Locale.US, "%.2f/hr", state.baselineHazardPerHour), style = MaterialTheme.typography.headlineSmall)
                    Text("Baseline Hazard", style = MaterialTheme.typography.bodySmall)
                }
                Column {
                    Text(String.format(java.util.Locale.US, "%.2f/hr", state.notifHazardPerHour), style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.error)
                    Text("Interrupted Hazard", style = MaterialTheme.typography.bodySmall)
                }
                Column {
                    Text("+${state.excessBreakAttributionPct}%", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
                    Text("Excess Breaks", style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        // 3. Stickiness Survival Curve
        PastelSquircleCard(tone = PastelTone.LAVENDER) {
            StatusPillBadge("Learner 3 · Survival Inversion")
            Text("App Stickiness (Weibull AFT)", style = MaterialTheme.typography.titleLarge)
            Text("Median excursion duration diverted from primary task", style = MaterialTheme.typography.bodySmall)
            if (state.stickinessMedians.isEmpty()) {
                Text("Warming up: 0 real focus blocks & 0 notifications", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    state.stickinessMedians.forEach { (cat, median) ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(cat.replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.bodyMedium)
                            Text(String.format(java.util.Locale.US, "%.1f min median", median), style = MaterialTheme.typography.titleSmall)
                        }
                    }
                }
            }
        }

        // 4. Notification Utility Value
        PastelSquircleCard(tone = PastelTone.SKY) {
            StatusPillBadge("Learner 4 · Beta-Bernoulli")
            Text("Notification Utility Value", style = MaterialTheme.typography.titleLarge)
            Text("Conjugate update with 7-day exponential half-life decay", style = MaterialTheme.typography.bodySmall)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text(String.format(java.util.Locale.US, "α = %.1f", state.notifValueAlpha), style = MaterialTheme.typography.titleMedium)
                    Text("Attended Priors", style = MaterialTheme.typography.bodySmall)
                }
                Column {
                    Text(String.format(java.util.Locale.US, "β = %.1f", state.notifValueBeta), style = MaterialTheme.typography.titleMedium)
                    Text("Ignored / Dismissed", style = MaterialTheme.typography.bodySmall)
                }
                Column {
                    Text("${state.notifValueHalfLifeDays} Days", style = MaterialTheme.typography.titleMedium)
                    Text("Half-Life Decay", style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        // 5. Attention Fragmentation Card
        PastelSquircleCard {
            StatusPillBadge("Learner 5 · Attention Residue")
            Text("Attention Fragmentation", style = MaterialTheme.typography.titleLarge)
            Text("HC3 heteroskedasticity-consistent fixed effects", style = MaterialTheme.typography.bodySmall)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text(String.format(java.util.Locale.US, "%.2f / 1.00", state.attentionResidueIndex), style = MaterialTheme.typography.headlineSmall)
                    Text("Residue Index", style = MaterialTheme.typography.bodySmall)
                }
                Column {
                    Text(String.format(java.util.Locale.US, "%.1f / hr", state.switchRatePerHour), style = MaterialTheme.typography.headlineSmall)
                    Text("Context Switch Rate", style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        // 6. Cognitive Fatigue State (Gaussian HMM)
        PastelSquircleCard(tone = PastelTone.SAGE) {
            StatusPillBadge("Learner 6 · Gaussian HMM")
            Text("Cognitive Fatigue State", style = MaterialTheme.typography.titleLarge)
            Text("3-state Gaussian HMM inferred from typing cadence and stall ratio", style = MaterialTheme.typography.bodySmall)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("${state.fatigueAlertPct}%", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
                    Text("Alert", style = MaterialTheme.typography.bodySmall)
                }
                Column {
                    Text("${state.fatigueSteadyPct}%", style = MaterialTheme.typography.headlineSmall)
                    Text("Steady", style = MaterialTheme.typography.bodySmall)
                }
                Column {
                    Text("${state.fatigueDepletedPct}%", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.error)
                    Text("Depleted", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        InsightDisclosure("Intentionality & model quality", "Awaiting validated analytics") {
            Text("Intentionality, resumption time, saved minutes and ECE calibration need their own validated evidence. Decision counts alone cannot establish them.",
                style = MaterialTheme.typography.bodyMedium)
        }
        Spacer(Modifier.height(84.dp))
    }
}

@Composable
private fun CountLine(label: String, count: Int) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Text(count.toString(), style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun InsightDisclosure(title: String, subtitle: String, content: @Composable ColumnScope.() -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Card(onClick = { expanded = !expanded },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(if (expanded) "Hide details −" else "View details +",
                style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            if (expanded) content()
        }
    }
}

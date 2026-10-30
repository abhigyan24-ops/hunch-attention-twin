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
            Text(if (state.totalDecisions == 0) "No decisions recorded yet." else "Stored totals across available history.",
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
            MiniSparklineCard("Saved Focus", "${(com.dhyan.app.collectors.A11yCollector.distractionsShieldedCount.get() + com.dhyan.app.collectors.A11yCollector.doomscrollsPreventedCount.get()) * 8}m", emptyList(),
                Modifier.weight(1f), PastelTone.SAND)
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
        Text("Attention patterns", style = MaterialTheme.typography.titleLarge)
        listOf(
            "Best focus times" to "Which hours support uninterrupted work.",
            "Interruption causes" to "Which categories most often interrupt you.",
            "Sessions & surfaces" to "Where sessions continue longer than intended.",
            "Important notifications" to "Which interruptions you find useful.",
            "Fatigue" to "How attention changes over a longer day.",
            "Fragmenting apps" to "Which app categories split your attention."
        ).forEach { (title, description) ->
            InsightDisclosure(title, "Focus recommendation") {
                Text(description, style = MaterialTheme.typography.bodyLarge)
                Text("The current analytics source does not supply this history yet. No estimate is shown.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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

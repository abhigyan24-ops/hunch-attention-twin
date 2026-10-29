package com.dhyan.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.dhyan.app.ui.model.SimulationResult
import java.util.Locale

@Composable
fun TimelineBars(sim: SimulationResult, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val maximum = maxOf(sim.expectedLostShowMin, sim.expectedLostSuppressMin, 0.01)
    Card(modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = colors.surfaceContainerLow)) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Text("Two possible futures", style = MaterialTheme.typography.titleLarge)
            Text("Simulated outcomes, not measured savings", style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant)
            OutcomeRow("If shown", sim.expectedLostShowMin, sim.pDistractionShow, maximum, colors.secondary)
            OutcomeRow("If suppressed", sim.expectedLostSuppressMin, sim.pDistractionSuppress, maximum, colors.primary)
            HorizontalDivider(color = colors.outlineVariant)
            Text(String.format(Locale.US, "Difference in loss · 80%% simulation interval\n%.1f to %.1f min",
                sim.ciLowShowMin, sim.ciHighShowMin), style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant)
        }
    }
}

@Composable
private fun OutcomeRow(label: String, lostMin: Double, probability: Double, maximum: Double, color: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = MaterialTheme.typography.titleMedium)
        Text(String.format(Locale.US, "%.1f min expected loss · %.0f%% simulated distraction",
            lostMin, probability * 100), style = MaterialTheme.typography.bodyMedium)
        Box(Modifier.fillMaxWidth().height(6.dp).background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
            Box(Modifier.fillMaxWidth((lostMin / maximum).toFloat().coerceIn(0f, 1f)).height(6.dp).background(color))
        }
    }
}

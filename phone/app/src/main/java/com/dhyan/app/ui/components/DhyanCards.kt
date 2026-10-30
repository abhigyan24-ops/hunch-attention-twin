package com.dhyan.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dhyan.app.ui.model.ActionVerdict
import com.dhyan.app.ui.model.LaptopPresence

@Composable
fun VerdictCard(verdict: ActionVerdict, explanation: String, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val background = when (verdict) {
        ActionVerdict.ALLOW -> colors.primaryContainer
        ActionVerdict.DIGEST -> colors.tertiaryContainer
        ActionVerdict.DELAY -> colors.secondaryContainer
        ActionVerdict.SILENT -> colors.surfaceContainerHigh
    }
    val foreground = when (verdict) {
        ActionVerdict.ALLOW -> colors.onPrimaryContainer
        ActionVerdict.DIGEST -> colors.onTertiaryContainer
        ActionVerdict.DELAY -> colors.onSecondaryContainer
        ActionVerdict.SILENT -> colors.onSurface
    }
    Card(modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = background)) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(verdict.label, style = MaterialTheme.typography.titleLarge, color = foreground)
            Text(explanation, style = MaterialTheme.typography.bodyLarge, color = foreground)
        }
    }
}

@Composable
fun ContextChip(label: String, isSelected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    FilterChip(selected = isSelected, onClick = onClick, label = { Text(label) },
        modifier = modifier.heightIn(min = 48.dp))
}

@Composable
fun LaptopStateBadge(presence: LaptopPresence, modifier: Modifier = Modifier) {
    Surface(modifier, shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Text("Laptop · ${presence.label}", Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun MetricTile(title: String, value: String, subtitle: String, modifier: Modifier = Modifier) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.headlineMedium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

package com.dhyan.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dhyan.app.ui.model.AttentionState

@Composable
fun FocusRing(
    state: AttentionState, sessionMinutes: Int, modifier: Modifier = Modifier,
    ringSize: Dp = 190.dp, stateLabel: String = state.label
) {
    val colors = MaterialTheme.colorScheme
    val accent = when (state) {
        AttentionState.DEEP_FOCUS -> colors.primary
        AttentionState.WORKING, AttentionState.RECOVERING -> colors.tertiary
        AttentionState.DRIFTING -> colors.secondary
        AttentionState.DISTRACTED -> colors.error
        AttentionState.FATIGUED -> colors.onSurfaceVariant
    }
    Box(modifier.size(ringSize), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(colors.surfaceContainerHigh, radius = size.minDimension / 2)
            drawCircle(accent.copy(alpha = 0.4f), radius = size.minDimension / 2 - 4.dp.toPx(),
                style = Stroke(2.dp.toPx()))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("${sessionMinutes.coerceAtLeast(0)} min", style = MaterialTheme.typography.headlineLarge)
            Text(stateLabel, style = MaterialTheme.typography.titleSmall, color = accent)
            Text("in this state", style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant)
        }
    }
}

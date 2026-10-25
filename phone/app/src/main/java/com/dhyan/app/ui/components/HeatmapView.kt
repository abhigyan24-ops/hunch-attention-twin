package com.dhyan.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dhyan.app.ui.theme.StateDeepFocus
import com.dhyan.app.ui.theme.SurfaceCard
import com.dhyan.app.ui.theme.TextMuted
import com.dhyan.app.ui.theme.TextPrimary
import com.dhyan.app.ui.theme.TextSecondary

@Composable
fun FocusHourHeatmap(
    modifier: Modifier = Modifier
) {
    // 24-hour focus intensities (0.0 = inactive, 1.0 = deep peak focus)
    // Morning peak 9-12, afternoon peak 15-18
    val hourlyIntensity = listOf(
        0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.05f,
        0.15f, 0.35f, 0.70f, 0.95f, 0.88f, 0.82f,
        0.40f, 0.25f, 0.55f, 0.92f, 0.85f, 0.78f,
        0.45f, 0.20f, 0.50f, 0.35f, 0.10f, 0.0f
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(SurfaceCard)
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "FOCUS-HOUR HEATMAP (24H)",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.0.sp
                    ),
                    color = TextSecondary
                )
                Text(
                    text = "When you produce uninterrupted flow episodes",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted
                )
            }
            Text(
                text = "Peak: 09–12h, 15–18h",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                color = StateDeepFocus
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Grid of 24 hours: 2 rows of 12 or 4 rows of 6
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                for (h in 0..11) {
                    HeatmapTile(
                        hour = h,
                        intensity = hourlyIntensity[h],
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                for (h in 12..23) {
                    HeatmapTile(
                        hour = h,
                        intensity = hourlyIntensity[h],
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Legend
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "00:00 (Night)",
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Less",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted
                )
                listOf(0.1f, 0.4f, 0.7f, 1.0f).forEach { alpha ->
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(StateDeepFocus.copy(alpha = alpha))
                    )
                }
                Text(
                    text = "Flow",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted
                )
            }
            Text(
                text = "23:00 (Evening)",
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted
            )
        }
    }
}

@Composable
private fun HeatmapTile(
    hour: Int,
    intensity: Float,
    modifier: Modifier = Modifier
) {
    val tileColor = if (intensity <= 0.05f) {
        Color(0xFF12161D)
    } else {
        StateDeepFocus.copy(alpha = (0.25f + intensity * 0.75f).coerceIn(0.2f, 1.0f))
    }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(28.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(tileColor)
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = String.format("%02d", hour),
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 9.sp),
            color = if (hour in listOf(0, 6, 12, 18)) TextPrimary else TextMuted
        )
    }
}

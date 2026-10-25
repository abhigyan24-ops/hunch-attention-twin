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
import androidx.compose.foundation.layout.width
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
import com.dhyan.app.ui.model.SimulationResult
import com.dhyan.app.ui.theme.BorderSubtle
import com.dhyan.app.ui.theme.StateDeepFocus
import com.dhyan.app.ui.theme.StateDistracted
import com.dhyan.app.ui.theme.SurfaceCard
import com.dhyan.app.ui.theme.TextMuted
import com.dhyan.app.ui.theme.TextPrimary
import com.dhyan.app.ui.theme.TextSecondary
import kotlin.math.roundToInt

@Composable
fun TimelineBars(
    sim: SimulationResult,
    modifier: Modifier = Modifier
) {
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
            Text(
                text = "PAIRED FUTURES (SIMULATION)",
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.0.sp
                ),
                color = TextSecondary
            )
            Text(
                text = "N=500 draws (CRN)",
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Option 1: SHOW NOTIFICATION
        FutureRow(
            label = "IF SHOWN",
            labelColor = StateDistracted,
            pDistraction = sim.pDistractionShow,
            lostMin = sim.expectedLostShowMin,
            ciLow = sim.ciLowShowMin,
            ciHigh = sim.ciHighShowMin,
            barColor = StateDistracted,
            isSuppressed = false
        )

        Spacer(modifier = Modifier.height(14.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(BorderSubtle)
        )
        Spacer(modifier = Modifier.height(14.dp))

        // Option 2: SUPPRESS / DIGEST
        FutureRow(
            label = "IF SUPPRESSED",
            labelColor = StateDeepFocus,
            pDistraction = sim.pDistractionSuppress,
            lostMin = sim.expectedLostSuppressMin,
            ciLow = 0.5,
            ciHigh = sim.expectedLostSuppressMin * 1.5,
            barColor = StateDeepFocus,
            isSuppressed = true
        )
    }
}

@Composable
private fun FutureRow(
    label: String,
    labelColor: Color,
    pDistraction: Double,
    lostMin: Double,
    ciLow: Double,
    ciHigh: Double,
    barColor: Color,
    isSuppressed: Boolean
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                    color = labelColor
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "P(lapse > 1m): ${(pDistraction * 100).roundToInt()}%",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            }
            Text(
                text = "${lostMin.roundToInt()} min lost",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = TextPrimary
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Horizontal Bar
        val barFraction = (lostMin / 45.0).coerceIn(0.04, 1.0).toFloat()
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(12.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0xFF0D1117))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(barFraction)
                    .height(12.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(barColor)
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = if (isSuppressed) "Natural resumption preserved" else "80% CI: ${ciLow.roundToInt()}–${ciHigh.roundToInt()} min lost",
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted
            )
            Text(
                text = if (isSuppressed) "Baseline: 2m" else "Hazard multiplier: 6.8x",
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted
            )
        }
    }
}

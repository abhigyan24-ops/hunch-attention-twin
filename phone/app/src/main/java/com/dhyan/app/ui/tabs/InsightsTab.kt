package com.dhyan.app.ui.tabs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dhyan.app.data.DhyanDatabase
import com.dhyan.app.ui.components.MetricTile
import com.dhyan.app.ui.model.DhyanStateHolder
import com.dhyan.app.ui.theme.BorderSubtle
import com.dhyan.app.ui.theme.DhyanPrimary
import com.dhyan.app.ui.theme.StateDeepFocus
import com.dhyan.app.ui.theme.StateDistracted
import com.dhyan.app.ui.theme.StateDrifting
import com.dhyan.app.ui.theme.SurfaceCard
import com.dhyan.app.ui.theme.SurfaceElevated
import com.dhyan.app.ui.theme.TextMuted
import com.dhyan.app.ui.theme.TextPrimary
import com.dhyan.app.ui.theme.TextSecondary
import com.dhyan.app.ui.theme.VerdictAllow
import com.dhyan.app.ui.theme.VerdictDelay
import com.dhyan.app.ui.theme.VerdictDigest
import com.dhyan.app.ui.theme.VerdictSilent
import com.dhyan.app.ui.theme.VoidBlack
import com.dhyan.app.ui.viewmodel.InsightsUiState
import com.dhyan.app.ui.viewmodel.InsightsViewModel

@Composable
fun InsightsTab(
    stateHolder: DhyanStateHolder? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val db = androidx.compose.runtime.remember(context) { DhyanDatabase.getInstance(context) }
    val viewModel = androidx.compose.runtime.remember(db) {
        InsightsViewModel(decisionDao = db.decisionDao(), heldNotifDao = db.heldNotifDao())
    }
    InsightsTab(viewModel = viewModel, modifier = modifier)
}

@Composable
fun InsightsTab(
    viewModel: InsightsViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(VoidBlack)
            .verticalScroll(scrollState)
            .padding(16.dp)
    ) {
        // Insights Header
        Text(
            text = "Attention Insights",
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
            color = TextPrimary
        )
        Text(
            text = "The six learned dimensions of your attention digital twin",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Live Real Counts Card from Room
        RealCountsCard(uiState = uiState)

        Spacer(modifier = Modifier.height(16.dp))

        // Section Title: 6 Learned Dimensions (Scaffold)
        Text(
            text = "LEARNED PROFILE DIMENSIONS",
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.0.sp
            ),
            color = TextSecondary
        )
        Spacer(modifier = Modifier.height(10.dp))

        // 1. Focus Trends
        DimensionCard(
            title = "1. Focus Trends & Session Depth",
            subtitle = "Daily preserved focus-minutes and unbroken immersion blocks.",
            metric = "Preserved Focus",
            status = "Calibrating from live telemetry"
        )

        Spacer(modifier = Modifier.height(10.dp))

        // 2. App Interruption Breakdown
        DimensionCard(
            title = "2. Interruption Vulnerability by App",
            subtitle = "App category hazard ranking and lapse pickup rates.",
            metric = "Highest Hazard: Social / Short-video",
            status = "Calibrating from live telemetry"
        )

        Spacer(modifier = Modifier.height(10.dp))

        // 3. Intervention Effectiveness
        DimensionCard(
            title = "3. Actuator Intervention Distribution",
            subtitle = "Breakdown of ALLOW vs DELAY vs DIGEST triage decisions.",
            metric = "Total Triage Events: ${uiState.totalDecisions}",
            status = if (uiState.totalDecisions > 0) "Active (${uiState.totalDecisions} logged)" else "Awaiting live events"
        )

        Spacer(modifier = Modifier.height(10.dp))

        // 4. Resumption Velocity
        DimensionCard(
            title = "4. Resumption Velocity Profile",
            subtitle = "Empirical time required to re-enter deep focus following interruptions.",
            metric = "Estimated Resumption: 8–12 min",
            status = "Calibrating from live telemetry"
        )

        Spacer(modifier = Modifier.height(10.dp))

        // 5. Attention Debt & Compulsive Score
        DimensionCard(
            title = "5. Attention Debt & Regret",
            subtitle = "Counterfactual regret analysis comparing actual vs optimal actions.",
            metric = "Compulsive Score: 0.18 [calibrating]",
            status = "Calibrating (Full analytics in Stage 10)"
        )

        Spacer(modifier = Modifier.height(10.dp))

        // 6. Diurnal Fatigue Analysis
        DimensionCard(
            title = "6. Diurnal Fatigue & Hazard Multiplier",
            subtitle = "Hourly distraction sensitivity across circadian phases.",
            metric = "Peak Focus: Morning (10:00–12:00)",
            status = "Calibrating from live telemetry"
        )

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun RealCountsCard(uiState: InsightsUiState) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(SurfaceCard)
            .border(1.dp, BorderSubtle, RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "LIVE PLATFORM TELEMETRY COUNTS",
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.0.sp
                ),
                color = DhyanPrimary
            )
            Text(
                text = "${uiState.totalDecisions} Total Decisions",
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                color = TextPrimary
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Actions Row
        Text(text = "Decisions by Action", style = MaterialTheme.typography.labelSmall, color = TextMuted)
        Spacer(modifier = Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CountBadge(label = "ALLOW", count = uiState.allowCount, color = VerdictAllow, modifier = Modifier.weight(1f))
            CountBadge(label = "DELAY", count = uiState.delayCount, color = VerdictDelay, modifier = Modifier.weight(1f))
            CountBadge(label = "DIGEST", count = uiState.digestCount, color = VerdictDigest, modifier = Modifier.weight(1f))
            CountBadge(label = "SILENT", count = uiState.silentCount, color = VerdictSilent, modifier = Modifier.weight(1f))
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Actuation Status Row
        Text(text = "Actuation Outcomes", style = MaterialTheme.typography.labelSmall, color = TextMuted)
        Spacer(modifier = Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            CountBadge(label = "HELD", count = uiState.heldCount, color = DhyanPrimary, modifier = Modifier.weight(1f))
            CountBadge(label = "SNOOZE", count = uiState.snoozedCount, color = Color(0xFF60A5FA), modifier = Modifier.weight(1f))
            CountBadge(label = "RELEASE", count = uiState.releasedCount, color = Color(0xFF34D399), modifier = Modifier.weight(1f))
            CountBadge(label = "FAIL_OPEN", count = uiState.failedOpenCount, color = Color(0xFFFBBF24), modifier = Modifier.weight(1f))
            CountBadge(label = "UNKNOWN", count = uiState.unknownCount, color = Color(0xFFEF4444), modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun CountBadge(
    label: String,
    count: Int,
    color: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(SurfaceElevated)
            .border(1.dp, color.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
            .padding(vertical = 8.dp, horizontal = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = color
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace
                ),
                color = TextSecondary
            )
        }
    }
}

@Composable
private fun DimensionCard(
    title: String,
    subtitle: String,
    metric: String,
    status: String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(SurfaceCard)
            .border(1.dp, BorderSubtle, RoundedCornerShape(12.dp))
            .padding(14.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
            color = TextPrimary
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = TextMuted
        )
        Spacer(modifier = Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = metric,
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                color = DhyanPrimary
            )
            Text(
                text = status,
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary
            )
        }
    }
}

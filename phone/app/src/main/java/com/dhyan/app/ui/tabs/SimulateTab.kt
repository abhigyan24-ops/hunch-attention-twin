package com.dhyan.app.ui.tabs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dhyan.app.ui.components.BayesianCurveCard
import com.dhyan.app.ui.components.ContextChip
import com.dhyan.app.ui.components.TimelineBars
import com.dhyan.app.ui.components.VerdictCard
import com.dhyan.app.ui.model.DhyanStateHolder
import com.dhyan.app.ui.theme.BorderSubtle
import com.dhyan.app.ui.theme.DhyanPrimary
import com.dhyan.app.ui.theme.SurfaceCard
import com.dhyan.app.ui.theme.TextMuted
import com.dhyan.app.ui.theme.TextPrimary
import com.dhyan.app.ui.theme.TextSecondary
import com.dhyan.app.ui.theme.VoidBlack
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SimulateTab(
    stateHolder: DhyanStateHolder,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    var isTwinLabExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(VoidBlack)
            .verticalScroll(scrollState)
            .padding(16.dp)
    ) {
        // Hero Header
        Text(
            text = "What-If Simulator",
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
            color = TextPrimary
        )
        Text(
            text = "Simulate your attention before it is spent across phone & laptop",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Context Configuration Card
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(SurfaceCard)
                .border(1.dp, BorderSubtle, RoundedCornerShape(16.dp))
                .padding(16.dp)
        ) {
            Text(
                text = "1. ACTIVE ATTENTION CONTEXT",
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.0.sp
                ),
                color = TextSecondary
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Task chips
            Text(text = "Primary Task", style = MaterialTheme.typography.bodySmall, color = TextMuted)
            Spacer(modifier = Modifier.height(6.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("Coding", "Writing Specs", "Research", "Studying", "Break").forEach { task ->
                    ContextChip(
                        label = task,
                        isSelected = stateHolder.simTask == task,
                        onClick = {
                            stateHolder.simTask = task
                            stateHolder.recalculate()
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Sliders: Session Elapsed & Fatigue
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    text = "Session Depth: ${stateHolder.sessionElapsedMin} min",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextPrimary
                )
                Text(
                    text = "Hour: ${stateHolder.currentHour}:00",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted
                )
            }
            Slider(
                value = stateHolder.sessionElapsedMin.toFloat(),
                onValueChange = {
                    stateHolder.sessionElapsedMin = it.roundToInt()
                    stateHolder.recalculate()
                },
                valueRange = 5f..120f,
                colors = SliderDefaults.colors(
                    thumbColor = DhyanPrimary,
                    activeTrackColor = DhyanPrimary
                )
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Second-Screen Overlap",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                        color = TextPrimary
                    )
                    Text(
                        text = "Phone & Laptop concurrently active",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted
                    )
                }
                Switch(
                    checked = stateHolder.isOverlapActive,
                    onCheckedChange = {
                        stateHolder.isOverlapActive = it
                        stateHolder.recalculate()
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = TextPrimary,
                        checkedTrackColor = DhyanPrimary
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Notification Configuration Card
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(SurfaceCard)
                .border(1.dp, BorderSubtle, RoundedCornerShape(16.dp))
                .padding(16.dp)
        ) {
            Text(
                text = "2. INCOMING NOTIFICATION",
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.0.sp
                ),
                color = TextSecondary
            )

            Spacer(modifier = Modifier.height(10.dp))

            // App selection chips
            Text(text = "App & Sender", style = MaterialTheme.typography.bodySmall, color = TextMuted)
            Spacer(modifier = Modifier.height(6.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    Pair("Instagram", "social"),
                    Pair("Slack", "work"),
                    Pair("WhatsApp", "message"),
                    Pair("GitHub", "work"),
                    Pair("YouTube", "entertainment")
                ).forEach { (app, cat) ->
                    ContextChip(
                        label = app,
                        isSelected = stateHolder.simApp == app,
                        onClick = {
                            stateHolder.simApp = app
                            stateHolder.simCategory = cat
                            stateHolder.recalculate()
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Urgency Level: ${stateHolder.simUrgency}/5",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextPrimary
                    )
                    Text(
                        text = if (stateHolder.simUrgency >= 4) "High priority intent detected" else "Routine content",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = "VIP", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                    Spacer(modifier = Modifier.width(6.dp))
                    Switch(
                        checked = stateHolder.simIsVip,
                        onCheckedChange = {
                            stateHolder.simIsVip = it
                            stateHolder.recalculate()
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = TextPrimary,
                            checkedTrackColor = DhyanPrimary
                        )
                    )
                }
            }
            Slider(
                value = stateHolder.simUrgency.toFloat(),
                onValueChange = {
                    stateHolder.simUrgency = it.roundToInt()
                    stateHolder.recalculate()
                },
                valueRange = 1f..5f,
                steps = 3,
                colors = SliderDefaults.colors(
                    thumbColor = DhyanPrimary,
                    activeTrackColor = DhyanPrimary
                )
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Paired Timeline Bars
        TimelineBars(sim = stateHolder.currentSimulation)

        Spacer(modifier = Modifier.height(14.dp))

        // Verdict Card
        VerdictCard(
            verdict = stateHolder.currentSimulation.verdict,
            explanation = stateHolder.currentSimulation.explanation
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Expandable Twin Lab Drawer Toggle
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(SurfaceCard)
                .border(1.dp, BorderSubtle, RoundedCornerShape(12.dp))
                .clickable { isTwinLabExpanded = !isTwinLabExpanded }
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Twin Lab (Bayesian Math)",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = TextPrimary
                    )
                    Text(
                        text = if (isTwinLabExpanded) "Tap to collapse distribution curves" else "Tap to inspect live posterior curves tightening",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted
                    )
                }
                Text(
                    text = if (isTwinLabExpanded) "\u25B2" else "\u25BC",
                    style = MaterialTheme.typography.titleMedium,
                    color = DhyanPrimary
                )
            }
        }

        if (isTwinLabExpanded) {
            Spacer(modifier = Modifier.height(10.dp))
            BayesianCurveCard(
                alpha = stateHolder.currentSimulation.betaAlpha,
                beta = stateHolder.currentSimulation.betaBeta,
                lognormMu = stateHolder.currentSimulation.lognormMu,
                lognormSigma = stateHolder.currentSimulation.lognormSigma,
                updateCount = stateHolder.currentSimulation.updateCount
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dhyan.app.ui.components.BayesianCurveCard
import com.dhyan.app.ui.components.ContextChip
import com.dhyan.app.ui.components.TimelineBars
import com.dhyan.app.ui.components.VerdictCard
import com.dhyan.app.ui.model.AttentionState
import com.dhyan.app.ui.model.DhyanStateHolder
import com.dhyan.app.ui.model.PersonaCatalog as UiPersonaCatalog
import com.dhyan.app.ui.theme.BorderSubtle
import com.dhyan.app.ui.theme.DhyanPrimary
import com.dhyan.app.ui.theme.SurfaceCard
import com.dhyan.app.ui.theme.TextMuted
import com.dhyan.app.ui.theme.TextPrimary
import com.dhyan.app.ui.theme.TextSecondary
import com.dhyan.app.ui.theme.VoidBlack
import com.dhyan.app.ui.viewmodel.SimulateUiState
import com.dhyan.app.ui.viewmodel.SimulateViewModel
import kotlin.math.roundToInt

@Composable
fun SimulateTab(
    stateHolder: DhyanStateHolder? = null,
    modifier: Modifier = Modifier
) {
    val viewModel = remember { SimulateViewModel() }
    SimulateTab(viewModel = viewModel, modifier = modifier)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SimulateTab(
    viewModel: SimulateViewModel,
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
        // Hero Header
        Text(
            text = "What-If Simulator",
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
            color = TextPrimary
        )
        Text(
            text = "Counterfactual twin simulation (SHOW vs SUPPRESS paired Monte Carlo)",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Persona Selection & Fast-Forward Box
        PersonaSelectionCard(
            uiState = uiState,
            onSelectPersona = { viewModel.selectPersona(it) },
            onToggleTrained = { viewModel.setTrained(it) }
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Active Context Card
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
                listOf("Coding", "Studying", "Writing", "Gaming", "Browsing").forEach { task ->
                    ContextChip(
                        label = task,
                        isSelected = uiState.taskType.equals(task, ignoreCase = true),
                        onClick = { viewModel.setTask(task.lowercase()) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Attention State Chips
            Text(text = "Attention State", style = MaterialTheme.typography.bodySmall, color = TextMuted)
            Spacer(modifier = Modifier.height(6.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    AttentionState.DEEP_FOCUS,
                    AttentionState.WORKING,
                    AttentionState.DRIFTING,
                    AttentionState.DISTRACTED
                ).forEach { state ->
                    ContextChip(
                        label = state.label,
                        isSelected = uiState.attentionState == state,
                        onClick = { viewModel.setAttentionState(state) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Sliders: Session Elapsed
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    text = "Session Depth: ${uiState.sessionElapsedMin} min",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextPrimary
                )
                Text(
                    text = "Hour: ${uiState.currentHour}:00",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted
                )
            }
            Slider(
                value = uiState.sessionElapsedMin.toFloat(),
                onValueChange = { viewModel.setSessionElapsedMin(it.roundToInt()) },
                valueRange = 5f..120f,
                colors = SliderDefaults.colors(
                    thumbColor = DhyanPrimary,
                    activeTrackColor = DhyanPrimary
                )
            )
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

            Text(text = "App Category", style = MaterialTheme.typography.bodySmall, color = TextMuted)
            Spacer(modifier = Modifier.height(6.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    Pair("Social", "social"),
                    Pair("Work / Chat", "work"),
                    Pair("Messaging", "messaging"),
                    Pair("Promo / News", "promo")
                ).forEach { (label, cat) ->
                    ContextChip(
                        label = label,
                        isSelected = uiState.simCategory == cat,
                        onClick = { viewModel.setCategory(cat) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "VIP Sender Override",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                        color = TextPrimary
                    )
                    Text(
                        text = "Never-hold safety rule bypasses all suppression",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted
                    )
                }
                Switch(
                    checked = uiState.simIsVip,
                    onCheckedChange = { viewModel.setIsVip(it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = TextPrimary,
                        checkedTrackColor = DhyanPrimary
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Paired Timeline Bars (SHOW vs SUPPRESS)
        TimelineBars(sim = uiState.simulationResult)

        Spacer(modifier = Modifier.height(14.dp))

        // Verdict Card
        VerdictCard(
            verdict = uiState.simulationResult.verdict,
            explanation = uiState.simulationResult.explanation
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Twin Lab (Beta Pickup Curve)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(SurfaceCard)
                .border(1.dp, BorderSubtle, RoundedCornerShape(16.dp))
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { viewModel.toggleTwinLab(!uiState.isTwinLabExpanded) },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "TWIN LAB · BAYESIAN PICKUP POSTERIOR",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.0.sp
                        ),
                        color = DhyanPrimary
                    )
                    Text(
                        text = if (uiState.isTwinLabExpanded) "Tap to collapse distribution" else "Tap to inspect Beta(α, β) belief curve",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted
                    )
                }
                Text(
                    text = if (uiState.isTwinLabExpanded) "▲" else "▼",
                    color = TextMuted,
                    style = MaterialTheme.typography.titleSmall
                )
            }

            if (uiState.isTwinLabExpanded) {
                Spacer(modifier = Modifier.height(14.dp))
                BayesianCurveCard(
                    alpha = uiState.simulationResult.betaAlpha,
                    beta = uiState.simulationResult.betaBeta,
                    lognormMu = uiState.simulationResult.lognormMu,
                    lognormSigma = uiState.simulationResult.lognormSigma,
                    updateCount = uiState.simulationResult.updateCount
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun PersonaSelectionCard(
    uiState: SimulateUiState,
    onSelectPersona: (com.dhyan.app.ui.model.PersonaProfile) -> Unit,
    onToggleTrained: (Boolean) -> Unit
) {
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
                text = "PERSONA PROFILE (SANDBOX)",
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.0.sp
                ),
                color = TextSecondary
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (uiState.isTrained) "14-Day Trained" else "Cold Priors",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = if (uiState.isTrained) DhyanPrimary else TextMuted
                )
                Spacer(modifier = Modifier.width(6.dp))
                Switch(
                    checked = uiState.isTrained,
                    onCheckedChange = onToggleTrained,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = TextPrimary,
                        checkedTrackColor = DhyanPrimary
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(
                UiPersonaCatalog.RAHUL,
                UiPersonaCatalog.ANIKA,
                UiPersonaCatalog.KABIR
            ).forEach { persona ->
                val isSelected = uiState.selectedPersona.id == persona.id
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isSelected) DhyanPrimary.copy(alpha = 0.15f) else Color.Transparent)
                        .border(
                            1.dp,
                            if (isSelected) DhyanPrimary else BorderSubtle,
                            RoundedCornerShape(10.dp)
                        )
                        .clickable { onSelectPersona(persona) }
                        .padding(10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = persona.name.split(" ")[0],
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = if (isSelected) DhyanPrimary else TextPrimary
                        )
                        Text(
                            text = persona.role,
                            style = MaterialTheme.typography.labelSmall,
                            color = TextMuted,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

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
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.ui.text.font.FontFamily
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
import com.dhyan.app.ui.model.PersonaProfile
import com.dhyan.app.ui.theme.BorderSubtle
import com.dhyan.app.ui.theme.DhyanPrimary
import com.dhyan.app.ui.theme.SurfaceCard
import com.dhyan.app.ui.theme.TextMuted
import com.dhyan.app.ui.theme.TextPrimary
import com.dhyan.app.ui.theme.TextSecondary
import com.dhyan.app.ui.theme.VoidBlack
import com.dhyan.app.ui.viewmodel.SimulateUiState
import com.dhyan.app.ui.viewmodel.SimulateViewModel
import java.util.Locale
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

        // Synthetic Persona Banner (Spec 25.13, Amendment 8)
        if (uiState.isTrained) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF332005))
                    .border(1.dp, Color(0xFFD29922), RoundedCornerShape(12.dp))
                    .padding(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xFFD29922))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "SYNTHETIC PERSONA",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Black,
                                letterSpacing = 0.5.sp
                            ),
                            color = Color.Black
                        )
                    }
                    Text(
                        text = "In-memory replay simulation only. Zero Room writes.",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                        color = Color(0xFFF1E05A)
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        // Persona Selection & Fast-Forward Box
        PersonaSelectionCard(
            uiState = uiState,
            onSelectPersona = { viewModel.selectPersona(it) },
            onToggleTrained = { viewModel.setTrained(it) }
        )

        // Progress bar for persona background replay
        if (uiState.isReplaying) {
            Spacer(modifier = Modifier.height(10.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(SurfaceCard)
                    .border(1.dp, BorderSubtle, RoundedCornerShape(10.dp))
                    .padding(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Replaying 14-day history for ${uiState.selectedPersona.name.split(" ")[0]}...",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextPrimary
                    )
                    Text(
                        text = "${(uiState.replayProgress * 100).toInt()}%",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                        color = DhyanPrimary
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(Color(0xFF21262D))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(uiState.replayProgress.coerceIn(0.01f, 1f))
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(3.dp))
                            .background(DhyanPrimary)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Provenance & Evidence Card (Spec 25.13)
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
                    text = "PROVENANCE & VALIDATION (SPEC 25.13)",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.0.sp
                    ),
                    color = TextSecondary
                )
                if (uiState.isFailOpen) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xFF3B2300))
                            .border(1.dp, Color(0xFFD29922), RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "FAIL-OPEN ACTIVE",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = Color(0xFFD29922)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = uiState.provenance,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = TextPrimary
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "nEff: ${String.format(Locale.US, "%.1f", uiState.nEff)}  ·  Evidence Strength: ${String.format(Locale.US, "%.2f", uiState.evidenceStrength)}  ·  Model v${uiState.modelVersion}  ·  RNG Seed: 42L",
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                color = TextMuted
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 1. Active Context Card
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

            // Attention State Chips (Amendment 3: Proven honest label)
            Text(
                text = "Attention State (context only — no effect on the twin or verdict)",
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted
            )
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

            // Fatigue Control (Amendment 1: Hours worked today -> FatigueIndex)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    text = "Hours worked today: ${String.format(Locale.US, "%.1f", uiState.hoursWorked)} h",
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                    color = TextPrimary
                )
                Text(
                    text = "f = ${String.format(Locale.US, "%.2f", uiState.fatigueIndex)} → ${uiState.fatigueBand.uppercase()}",
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                    color = DhyanPrimary
                )
            }
            Text(
                text = "[switchRateZ=0, stallRatio=0 fixed neutral]",
                style = MaterialTheme.typography.labelSmall,
                color = TextMuted
            )
            Slider(
                value = uiState.hoursWorked.toFloat(),
                onValueChange = { viewModel.setHoursWorked(it.toDouble()) },
                valueRange = 0f..10f,
                colors = SliderDefaults.colors(
                    thumbColor = DhyanPrimary,
                    activeTrackColor = DhyanPrimary
                )
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Minutes in current state
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    text = "Minutes in current state: ${uiState.minutesInState} min",
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                    color = TextPrimary
                )
            }
            Slider(
                value = uiState.minutesInState.toFloat(),
                onValueChange = { viewModel.setMinutesInState(it.roundToInt()) },
                valueRange = 5f..120f,
                colors = SliderDefaults.colors(
                    thumbColor = DhyanPrimary,
                    activeTrackColor = DhyanPrimary
                )
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Time of day (Amendment 2: All 5 HourBands mapped)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    text = "Time of day: ${uiState.currentHour}:00",
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                    color = TextPrimary
                )
                Text(
                    text = "Band: ${uiState.hourBand.uppercase()}",
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                    color = DhyanPrimary
                )
            }
            Slider(
                value = uiState.currentHour.toFloat(),
                onValueChange = { viewModel.setCurrentHour(it.roundToInt()) },
                valueRange = 0f..23f,
                colors = SliderDefaults.colors(
                    thumbColor = DhyanPrimary,
                    activeTrackColor = DhyanPrimary
                )
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 2. Incoming Notification Card (Amendment 7: core-spec taxonomy mapped)
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

            Text(
                text = "App & Notification Category (Core Taxonomy)",
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted
            )
            Spacer(modifier = Modifier.height(6.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Verified taxonomy resolving directly to PriorTable branches
                listOf(
                    Triple("Social", "social", "social_notif"),
                    Triple("Known Contact", "communication", "msg"),
                    Triple("Promo / Deals", "shopping", "promo"),
                    Triple("Work Docs", "docs", "alert")
                ).forEach { (label, appCat, notifCat) ->
                    val isSelected = uiState.simCategory == appCat && uiState.simNotifCat == notifCat
                    ContextChip(
                        label = label,
                        isSelected = isSelected,
                        onClick = { viewModel.setCategory(appCat, notifCat) }
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

        // Verdict Card (Amendment 3: Explainer from DecisionPipeline)
        VerdictCard(
            verdict = uiState.simulationResult.verdict,
            explanation = uiState.simulationResult.explanation
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Twin Lab (Beta Pickup Curve & Posterior - Amendment 5)
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
                        text = "TWIN LAB · BAYESIAN POSTERIOR",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.0.sp
                        ),
                        color = DhyanPrimary
                    )
                    Text(
                        text = if (uiState.isTwinLabExpanded) "Tap to collapse distribution" else "Tap to inspect resolved ModelSnapshot beliefs",
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
                    updateCount = uiState.simulationResult.updateCount,
                    nEff = uiState.nEff,
                    modelVersion = uiState.modelVersion,
                    priorAlpha = uiState.priorAlpha,
                    priorBeta = uiState.priorBeta
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun PersonaSelectionCard(
    uiState: SimulateUiState,
    onSelectPersona: (PersonaProfile) -> Unit,
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
                    text = if (uiState.isTrained) "14-Day Replay" else "Cold Priors",
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

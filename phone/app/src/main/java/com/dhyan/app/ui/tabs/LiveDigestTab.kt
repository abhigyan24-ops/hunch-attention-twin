package com.dhyan.app.ui.tabs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dhyan.app.twin.decision.PolicyMode
import com.dhyan.app.twin.model.ContextSnapshot
import com.dhyan.app.twin.model.LaptopState
import com.dhyan.app.twin.model.FocusState
import com.dhyan.app.ui.components.FocusRing
import com.dhyan.app.ui.model.AttentionState
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
import com.dhyan.app.ui.viewmodel.LiveDecisionItemUi
import com.dhyan.app.ui.viewmodel.LiveDigestUiState
import com.dhyan.app.ui.viewmodel.LiveDigestViewModel
import com.dhyan.app.ui.viewmodel.LiveHeldItemUi

@Composable
fun LiveDigestTab(
    stateHolder: com.dhyan.app.ui.model.DhyanStateHolder? = null,
    modifier: Modifier = Modifier
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val db = androidx.compose.runtime.remember(context) { com.dhyan.app.data.DhyanDatabase.getInstance(context) }
    val viewModel = androidx.compose.runtime.remember(db) {
        LiveDigestViewModel(
            heldNotifDao = db.heldNotifDao(),
            decisionDao = db.decisionDao(),
            feedbackDao = db.feedbackDao(),
            killSwitchController = com.dhyan.app.actuator.KillSwitchController(
                com.dhyan.app.actuator.RoomHeldStore(db.heldNotifDao()),
                com.dhyan.app.actuator.release.WorkManagerReleaseJobScheduler(context)
            ),
            isFakeProvider = false
        )
    }
    LiveDigestTab(viewModel = viewModel, modifier = modifier)
}

@Composable
fun LiveDigestTab(
    viewModel: LiveDigestViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    LiveDigestContent(
        uiState = uiState,
        onShowNow = { viewModel.showNow(it) },
        onMarkImportant = { keyHash, id -> viewModel.markImportant(keyHash, id) },
        onMarkNoise = { keyHash, id -> viewModel.markNoise(keyHash, id) },
        onReleaseAll = { viewModel.releaseAll() },
        onToggleKillSwitch = { viewModel.toggleKillSwitch() },
        modifier = modifier
    )
}

@Composable
fun LiveDigestContent(
    uiState: LiveDigestUiState,
    onShowNow: (String) -> Unit,
    onMarkImportant: (String, Long) -> Unit,
    onMarkNoise: (String, Long) -> Unit = onMarkImportant,
    onReleaseAll: () -> Unit = {},
    onToggleKillSwitch: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(VoidBlack)
            .verticalScroll(scrollState)
            .padding(16.dp)
    ) {
        // Mode & Safety Banners
        PolicyModeBanner(
            mode = uiState.mode,
            killSwitch = uiState.killSwitchActive,
            isFake = uiState.isFakeProvider,
            onToggleKillSwitch = onToggleKillSwitch
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Laptop Companion Badge & Sync State (Spec 25.13)
        LaptopCompanionBadge(snapshot = uiState.contextSnapshot, provenance = uiState.provenance, availability = uiState.laptopAvailability)

        Spacer(modifier = Modifier.height(16.dp))

        // Attention State / Focus Ring Section
        AttentionSection(uiState = uiState)

        Spacer(modifier = Modifier.height(24.dp))

        // Held Notifications Digest Queue
        HeldDigestSection(
            heldQueue = uiState.heldQueue,
            onShowNow = onShowNow,
            onMarkImportant = onMarkImportant,
            onMarkNoise = onMarkNoise,
            onReleaseAll = onReleaseAll
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Recent Real-Time Twin Decisions
        DecisionsFeedSection(
            decisions = uiState.decisionFeed,
            isFake = uiState.isFakeProvider
        )

        Spacer(modifier = Modifier.height(24.dp))

        // History Strip (Recently Released / Expired)
        if (uiState.historyStrip.isNotEmpty()) {
            HistoryStripSection(history = uiState.historyStrip)
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun PolicyModeBanner(
    mode: PolicyMode,
    killSwitch: Boolean,
    isFake: Boolean,
    onToggleKillSwitch: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (killSwitch) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF3B1214))
                    .border(1.dp, Color(0xFFEF4444), RoundedCornerShape(10.dp))
                    .padding(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "KILL SWITCH ENGAGED",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = Color(0xFFFCA5A5)
                        )
                        Text(
                            text = "All holds released. Actuation halted unconditionally.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextMuted
                        )
                    }
                    Button(
                        onClick = onToggleKillSwitch,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Resume", style = MaterialTheme.typography.labelSmall, color = Color.White)
                    }
                }
            }
        }

        // Policy Mode Banner
        val (bannerBg, bannerBorder, bannerText, desc) = when (mode) {
            PolicyMode.OFF -> Quadruple(
                Color(0xFF242019),
                Color(0xFFF59E0B),
                "POLICY MODE: OFF",
                "Observing telemetry only. All notifications delivered immediately."
            )
            PolicyMode.DRY_RUN -> Quadruple(
                Color(0xFF141F36),
                Color(0xFF3B82F6),
                "POLICY MODE: DRY RUN",
                "Simulating twin decisions. Zero platform holds executed."
            )
            PolicyMode.LIVE -> Quadruple(
                Color(0xFF0F291E),
                Color(0xFF10B981),
                "POLICY MODE: LIVE",
                "Active actuation enabled on allowlisted applications."
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(bannerBg)
                .border(1.dp, bannerBorder.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = bannerText,
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.8.sp
                        ),
                        color = bannerBorder
                    )
                    Text(
                        text = desc,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            }
        }

        if (isFake) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF261D07))
                    .border(1.dp, Color(0xFFD97706).copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "FAKE DECISION PIPELINE ACTIVE — Stage 6 standalone sandbox mode",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = Color(0xFFFDE68A)
                )
            }
        }
    }
}

private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

@Composable
private fun AttentionSection(uiState: LiveDigestUiState) {
    val snapshot = uiState.contextSnapshot
    if (snapshot != null) {
        val attentionState = when (snapshot.focusState) {
            FocusState.DEEP_FOCUS -> AttentionState.DEEP_FOCUS
            FocusState.WORKING -> AttentionState.WORKING
            FocusState.DRIFTING -> AttentionState.DRIFTING
            FocusState.DISTRACTED -> AttentionState.DISTRACTED
            FocusState.RECOVERING -> AttentionState.RECOVERING
            FocusState.BREAK -> AttentionState.FATIGUED
        }
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            FocusRing(
                state = attentionState,
                sessionMinutes = snapshot.minutesInState.toInt(),
                ringSize = 190.dp
            )
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Chip(text = "Task: ${snapshot.taskType.wire}")
                Chip(text = "Laptop: ${snapshot.laptopState.wire}")
                Chip(text = "Rule-based, not calibrated")
            }
        }
    } else {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(SurfaceCard)
                .border(1.dp, BorderSubtle, RoundedCornerShape(14.dp))
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Awaiting Live Telemetry",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Focus ring calibrates after phone or laptop usage is observed.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted
                )
            }
        }
    }
}

@Composable
private fun Chip(text: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(100.dp))
            .background(SurfaceElevated)
            .border(1.dp, BorderSubtle, RoundedCornerShape(100.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondary
        )
    }
}

@Composable
private fun HeldDigestSection(
    heldQueue: List<LiveHeldItemUi>,
    onShowNow: (String) -> Unit,
    onMarkImportant: (String, Long) -> Unit,
    onMarkNoise: (String, Long) -> Unit,
    onReleaseAll: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "DIGEST QUEUE",
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.0.sp
                ),
                color = TextSecondary
            )
            Spacer(modifier = Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(100.dp))
                    .background(DhyanPrimary.copy(alpha = 0.2f))
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "${heldQueue.size} held",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = DhyanPrimary
                )
            }
        }
        if (heldQueue.isNotEmpty()) {
            Text(
                text = "Release All",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                color = DhyanPrimary,
                modifier = Modifier.clickable { onReleaseAll() }
            )
        }
    }

    Spacer(modifier = Modifier.height(10.dp))

    if (heldQueue.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(SurfaceCard)
                .border(1.dp, BorderSubtle, RoundedCornerShape(14.dp))
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Digest Queue Clear",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = StateDeepFocus
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "No notifications held. All incoming alerts triaged cleanly.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted
                )
            }
        }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            heldQueue.forEach { item ->
                HeldItemCard(
                    item = item,
                    onShowNow = { onShowNow(item.keyHash) },
                    onImportant = { onMarkImportant(item.keyHash, 0L) },
                    onNoise = { onMarkNoise(item.keyHash, 0L) }
                )
            }
        }
    }
}

@Composable
private fun HeldItemCard(
    item: LiveHeldItemUi,
    onShowNow: () -> Unit,
    onImportant: () -> Unit,
    onNoise: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(SurfaceCard)
            .border(1.dp, BorderSubtle, RoundedCornerShape(14.dp))
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = item.appCat.uppercase(),
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = DhyanPrimary
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "(${item.notifCat})",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
            Text(
                text = "${item.heldAgo} · ${item.dueIn}",
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "Key: ${item.keyHash.take(16)}... | Mechanism: ${item.mechanism}",
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            color = TextSecondary
        )

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = onShowNow,
                colors = ButtonDefaults.buttonColors(containerColor = DhyanPrimary),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Show Now", color = VoidBlack, fontWeight = FontWeight.Bold)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onImportant,
                    shape = RoundedCornerShape(8.dp),
                    border = ButtonDefaults.outlinedButtonBorder.copy(brush = androidx.compose.ui.graphics.SolidColor(BorderSubtle))
                ) {
                    Text("Important", color = TextSecondary, style = MaterialTheme.typography.labelSmall)
                }
                OutlinedButton(
                    onClick = onNoise,
                    shape = RoundedCornerShape(8.dp),
                    border = ButtonDefaults.outlinedButtonBorder.copy(brush = androidx.compose.ui.graphics.SolidColor(BorderSubtle))
                ) {
                    Text("Noise", color = TextSecondary, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
private fun DecisionsFeedSection(
    decisions: List<LiveDecisionItemUi>,
    isFake: Boolean
) {
    Text(
        text = "RECENT TWIN DECISIONS",
        style = MaterialTheme.typography.labelMedium.copy(
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.0.sp
        ),
        color = TextSecondary
    )
    Spacer(modifier = Modifier.height(10.dp))

    if (decisions.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(SurfaceCard)
                .border(1.dp, BorderSubtle, RoundedCornerShape(12.dp))
                .padding(20.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "No decisions logged yet. Decisions appear as notifications arrive.",
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted
            )
        }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            decisions.forEach { decision ->
                DecisionRowCard(decision = decision)
            }
        }
    }
}

@Composable
private fun DecisionRowCard(decision: LiveDecisionItemUi) {
    val (badgeBg, badgeText, badgeColor) = when (decision.action) {
        "ALLOW" -> Triple(VerdictAllow.copy(alpha = 0.2f), "ALLOW", VerdictAllow)
        "DELAY" -> Triple(VerdictDelay.copy(alpha = 0.2f), "DELAY", VerdictDelay)
        "DIGEST" -> Triple(VerdictDigest.copy(alpha = 0.2f), "DIGEST", VerdictDigest)
        "SILENT" -> Triple(VerdictSilent.copy(alpha = 0.2f), "SILENT", VerdictSilent)
        else -> Triple(Color.Gray.copy(alpha = 0.2f), decision.action, Color.LightGray)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(SurfaceCard)
            .border(1.dp, BorderSubtle, RoundedCornerShape(12.dp))
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(badgeBg)
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = badgeText,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = badgeColor
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                decision.actuationStatus?.let { status ->
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(SurfaceElevated)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = status,
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                            color = TextSecondary
                        )
                    }
                }
            }
            Text(
                text = decision.timestampAgo,
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "Reason: ${decision.reasonCode} · key: ${decision.keyHash.take(12)}...",
            style = MaterialTheme.typography.bodySmall,
            color = TextPrimary
        )

        if (decision.deltaLMean != null) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "ΔL: ${String.format("%.1f", decision.deltaLMean)}m [estimated] · p(show): ${String.format("%.2f", decision.propensity)}",
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary
            )
        }
    }
}

@Composable
private fun HistoryStripSection(history: List<LiveHeldItemUi>) {
    Text(
        text = "RECENT RELEASED HISTORY",
        style = MaterialTheme.typography.labelMedium.copy(
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.0.sp
        ),
        color = TextSecondary
    )
    Spacer(modifier = Modifier.height(8.dp))
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        history.forEach { item ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(SurfaceElevated)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${item.appCat} (${item.state})",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
                Text(
                    text = item.heldAgo,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted
                )
            }
        }
    }
}

@Composable
private fun LaptopCompanionBadge(
    snapshot: ContextSnapshot?,
    provenance: String = "observed",
    availability: String = "No laptop data"
) {
    if (snapshot == null) return
    val laptopState = snapshot.laptopState
    val isOffline = laptopState == LaptopState.OFFLINE
    val overlap = snapshot.secondScreenOverlap

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(SurfaceElevated)
            .border(0.5.dp, BorderSubtle, RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(if (isOffline) TextMuted else StateDeepFocus)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Laptop: $availability",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = if (isOffline) TextMuted else TextPrimary
                )
                val isEstimated = provenance == "estimated" || isOffline
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isEstimated) "ESTIMATED" else "OBSERVED",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold),
                    color = if (isEstimated) StateDrifting else DhyanPrimary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (isEstimated) StateDrifting.copy(alpha = 0.15f) else DhyanPrimary.copy(alpha = 0.15f))
                        .padding(horizontal = 4.dp, vertical = 1.dp)
                )
            }
            if (overlap) {
                Text(
                    text = "2ND SCREEN OVERLAP",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 9.sp),
                    color = StateDrifting,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(StateDrifting.copy(alpha = 0.15f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
            } else {
                Text(
                    text = availability,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                    color = TextMuted
                )
            }
        }
        if (isOffline) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = availability,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                color = StateDrifting
            )
        }
    }
}

package com.dhyan.app.ui.tabs

import android.content.Intent
import android.provider.Settings
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dhyan.app.collectors.A11yCollector
import com.dhyan.app.twin.decision.PolicyMode
import com.dhyan.app.twin.model.ContextSnapshot
import com.dhyan.app.twin.model.LaptopState
import com.dhyan.app.twin.model.FocusState
import com.dhyan.app.ui.components.FocusRing
import com.dhyan.app.ui.components.ContextChip
import com.dhyan.app.ui.components.PastelSquircleCard
import com.dhyan.app.ui.components.PastelTone
import com.dhyan.app.ui.components.StatusPillBadge
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
    val context = LocalContext.current
    val db = remember(context) { com.dhyan.app.data.DhyanDatabase.getInstance(context) }
    val viewModel = remember(db) {
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
        onStartFocusSession = { min, task -> viewModel.startFocusSession(min, task) },
        onEndFocusSession = { viewModel.endFocusSession() },
        modifier = modifier
    )
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun LiveDigestContent(
    uiState: LiveDigestUiState,
    onShowNow: (String) -> Unit,
    onMarkImportant: (String, Long) -> Unit,
    onMarkNoise: (String, Long) -> Unit = onMarkImportant,
    onReleaseAll: () -> Unit = {},
    onToggleKillSwitch: () -> Unit = {},
    onStartFocusSession: (Double, String) -> Unit = { _, _ -> },
    onEndFocusSession: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showDecisions by remember { mutableStateOf(false) }
    var selectedDuration by remember { mutableIntStateOf(25) }
    var selectedTask by remember { mutableStateOf("coding") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // Top Header
        PastelSquircleCard(tone = PastelTone.SKY) {
            StatusPillBadge("${uiState.mode.name.replace('_', ' ')} · notification policy")
            Text("A little more\nheadspace.", style = MaterialTheme.typography.headlineLarge)
            Text("Your live context & quiet queue", style = MaterialTheme.typography.bodyMedium)
        }

        // 1. Focus Shield & Session Bento Card
        val activeSession = uiState.contextSnapshot?.declaredSession?.takeIf {
            val now = System.currentTimeMillis()
            it.startedTs > 0 && (now - it.startedTs) < (it.plannedMin * 60_000L).toLong()
        }

        if (activeSession != null) {
            val now = System.currentTimeMillis()
            val remSec = (((activeSession.startedTs + (activeSession.plannedMin * 60_000L).toLong()) - now) / 1000L).coerceAtLeast(0L)
            val remMin = remSec / 60
            val remSecOnly = remSec % 60
            PastelSquircleCard(tone = PastelTone.SAGE) {
                StatusPillBadge("● Focus Shield Active", active = true)
                Text(
                    text = String.format(java.util.Locale.US, "%02d:%02d remaining", remMin, remSecOnly),
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${activeSession.task.wire.replaceFirstChar { it.uppercase() }} · Restricting social media & doomscrolling",
                    style = MaterialTheme.typography.bodyMedium
                )
                if (A11yCollector.isServiceConnected) {
                    Text(
                        text = "● Real-Time Interceptor Active · Zero raw text or URLs read",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "⚠️ Accessibility off · Cannot block apps",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                        TextButton(onClick = {
                            context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            })
                        }) { Text("Turn On") }
                    }
                }
                OutlinedButton(
                    onClick = onEndFocusSession,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("End Focus Session")
                }
            }
        } else {
            PastelSquircleCard(tone = PastelTone.LAVENDER) {
                StatusPillBadge("Focus Shield & Session", active = false)
                Text(
                    text = "Start Focus Session",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Shields you from Instagram, TikTok, Reels, Shorts & doomscrolling.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(15, 25, 45, 60).forEach { mins ->
                        ContextChip(
                            label = "$mins min",
                            isSelected = selectedDuration == mins,
                            onClick = { selectedDuration = mins }
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("coding" to "Coding", "reading" to "Reading", "writing" to "Writing").forEach { (wire, label) ->
                        ContextChip(
                            label = label,
                            isSelected = selectedTask == wire,
                            onClick = { selectedTask = wire }
                        )
                    }
                }
                Button(
                    onClick = { onStartFocusSession(selectedDuration.toDouble(), selectedTask) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF121417),
                        contentColor = Color.White
                    )
                ) {
                    Text("Start Focus ($selectedDuration min)")
                }
                if (A11yCollector.isServiceConnected) {
                    Text(
                        text = "● Shield Ready · Zero text or URLs collected",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "⚠️ Enable Accessibility to block distracting apps",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        TextButton(onClick = {
                            context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            })
                        }) { Text("Enable") }
                    }
                }
            }
        }

        // 2. Focus Defense & Shield Analytics Bento Card
        PastelSquircleCard(tone = PastelTone.SAND) {
            StatusPillBadge("Shield Analytics · Today", active = true)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "${A11yCollector.distractionsShieldedCount.get()}",
                        style = MaterialTheme.typography.displayMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Distractions Blocked",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Column {
                    Text(
                        text = "${A11yCollector.doomscrollsPreventedCount.get()}",
                        style = MaterialTheme.typography.displayMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Doomscrolls Interrupted",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            val savedMin = (A11yCollector.distractionsShieldedCount.get() + A11yCollector.doomscrollsPreventedCount.get()) * 8
            Text(
                text = "Preserved focus: ~$savedMin min estimated headspace saved",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = "🌙 Bedtime Guardian Active (11 PM - 6 AM)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // 3. Notification Policy Card
        LiveCard("Notification policy · ${uiState.mode.name.replace('_', ' ')}") {
            Text(when (uiState.mode) {
                PolicyMode.OFF -> "Observation only. Notifications are not held."
                PolicyMode.DRY_RUN -> "Previewing decisions. No platform holds."
                PolicyMode.LIVE -> "Notification holds are enabled for allowed applications."
            }, style = MaterialTheme.typography.bodyMedium)
            if (uiState.isFakeProvider) Text("Sandbox provider · not live decisions",
                color = MaterialTheme.colorScheme.secondary)
            Text(if (uiState.killSwitchActive) "Emergency stop is active." else "Emergency stop is available.",
                style = MaterialTheme.typography.bodySmall)
            Button(onClick = onToggleKillSwitch, modifier = Modifier.fillMaxWidth()) {
                Text(if (uiState.killSwitchActive) "Resume policy" else "Stop all holds")
            }
        }

        // 4. Laptop Availability & Attention State
        Text(uiState.laptopAvailability, style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        val snapshot = uiState.contextSnapshot
        if (snapshot == null) LiveCard("No attention data yet") {
            Text("Waiting for a context snapshot. No focus state is assumed.",
                style = MaterialTheme.typography.bodyLarge)
        } else {
            val attention = when (snapshot.focusState) {
                FocusState.DEEP_FOCUS -> AttentionState.DEEP_FOCUS
                FocusState.WORKING -> AttentionState.WORKING
                FocusState.DRIFTING -> AttentionState.DRIFTING
                FocusState.DISTRACTED -> AttentionState.DISTRACTED
                FocusState.RECOVERING -> AttentionState.RECOVERING
                FocusState.BREAK -> AttentionState.FATIGUED
            }
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                FocusRing(attention, snapshot.minutesInState.toInt(),
                    stateLabel = if (snapshot.focusState == FocusState.BREAK) "Break" else attention.label)
                Text("Rule-based, not calibrated", style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Task · ${snapshot.taskType.wire}  /  ${uiState.provenance}",
                    style = MaterialTheme.typography.bodySmall)
            }
        }

        // 5. Held Digest Queue
        LiveCard("Your digest · ${uiState.heldQueue.size}") {
            Text("Categories only. Notification content stays private.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (uiState.heldQueue.isEmpty()) Text("Nothing waiting. You’re all caught up.",
                style = MaterialTheme.typography.bodyLarge)
            else {
                Button(onClick = onReleaseAll, modifier = Modifier.fillMaxWidth()) { Text("Release all") }
                uiState.heldQueue.forEach { item ->
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Text(item.appCat.replace('_', ' '), style = MaterialTheme.typography.titleMedium)
                    Text("${item.notifCat} · ${item.state} · ${item.mechanism} · ${item.heldAgo} · due ${item.dueIn}",
                        style = MaterialTheme.typography.bodySmall)
                    androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { onShowNow(item.keyHash) }) { Text("Show now") }
                        val record = uiState.decisionFeed.firstOrNull { it.keyHash == item.keyHash }
                        TextButton(enabled = record != null,
                            onClick = { record?.let { onMarkImportant(item.keyHash, it.id) } }) { Text("Important") }
                        TextButton(enabled = record != null,
                            onClick = { record?.let { onMarkNoise(item.keyHash, it.id) } }) { Text("Noise") }
                    }
                    if (uiState.decisionFeed.none { it.keyHash == item.keyHash })
                        Text("Feedback unavailable: matching decision is outside recent history.",
                            style = MaterialTheme.typography.bodySmall)
                }
                Text("Release controls request a release; delivery is not confirmed here.",
                    style = MaterialTheme.typography.bodySmall)
            }
        }

        // 6. Recent Decisions Feed
        LiveCard("Recent decisions") {
            Text(if (uiState.decisionFeed.isEmpty()) "No decisions recorded yet."
                else "${uiState.decisionFeed.size} recent records", style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = { showDecisions = !showDecisions }) {
                Text(if (showDecisions) "Hide records −" else "View records +")
            }
            if (showDecisions) uiState.decisionFeed.forEach { record ->
                HorizontalDivider()
                Text("${record.action} · ${record.timestampAgo}", style = MaterialTheme.typography.titleSmall)
                Text("${record.reasonCode} · ${record.actuationStatus ?: "outcome unavailable"}",
                    style = MaterialTheme.typography.bodySmall)
                Text("Mode · ${record.mode} / propensity · ${record.propensity}", style = MaterialTheme.typography.bodySmall)
                record.deltaLMean?.let { Text("Simulated difference in loss · $it min", style = MaterialTheme.typography.bodySmall) }
                if (record.exploration) Text("Exploration decision", style = MaterialTheme.typography.bodySmall)
                if (record.failedOpen) Text("Failed open", color = MaterialTheme.colorScheme.secondary)
                if (record.safetyReason != null) Text("Safety · ${record.safetyReason}",
                    style = MaterialTheme.typography.bodySmall)
            }
        }

        if (uiState.historyStrip.isNotEmpty()) LiveCard("Recently settled") {
            uiState.historyStrip.forEach { item ->
                Text("${item.appCat} · ${item.state} · ${item.heldAgo}",
                    style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun LiveCard(title: String, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    PastelSquircleCard {
        Text(title, style = MaterialTheme.typography.titleLarge)
        content()
    }
}

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
import androidx.compose.runtime.setValue
import androidx.compose.material3.TextButton
import androidx.compose.material3.HorizontalDivider
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


@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
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
    var showDecisions by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Text("Here & now.", style = MaterialTheme.typography.headlineLarge)
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
            androidx.compose.material3.OutlinedButton(onClick = onToggleKillSwitch) {
                Text(if (uiState.killSwitchActive) "Resume policy" else "Stop all holds")
            }
        }
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
        LiveCard("Your digest · ${uiState.heldQueue.size}") {
            Text("Categories only. Notification content stays private.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (uiState.heldQueue.isEmpty()) Text("Nothing waiting. You’re all caught up.",
                style = MaterialTheme.typography.bodyLarge)
            else {
                TextButton(onClick = onReleaseAll) { Text("Release all") }
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
    androidx.compose.material3.Card(
        colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            content()
        }
    }
}

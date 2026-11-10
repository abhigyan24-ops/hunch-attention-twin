package com.dhyan.app.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.dhyan.app.BuildConfig
import com.dhyan.app.DhyanApplication
import com.dhyan.app.actuator.digest.AndroidDigestPoster
import com.dhyan.app.data.DhyanDatabase
import com.dhyan.app.twin.decision.PolicyMode
import com.dhyan.app.ui.components.ContextChip
import com.dhyan.app.ui.components.*
import com.dhyan.app.ui.model.DhyanStateHolder
import com.dhyan.app.ui.viewmodel.SettingsViewModel
import java.util.Locale
import com.dhyan.app.ui.brand.HunchBrand
import com.dhyan.app.R
import androidx.compose.ui.res.stringResource

@Composable
fun SettingsBottomSheet(stateHolder: DhyanStateHolder? = null, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val db = remember(context) { DhyanDatabase.getInstance(context) }
    val poster = remember(context) { AndroidDigestPoster(context) }
    val sync = remember { runCatching { DhyanApplication.syncManager }.getOrNull() }
    val vm = remember(db, sync) {
        SettingsViewModel(vipDao = db.vipDao(), settingsDao = db.settingsDao(), digestPoster = poster, syncManager = sync, db = db)
    }
    SettingsBottomSheet(vm, onDismiss)
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsBottomSheet(viewModel: SettingsViewModel, onDismiss: () -> Unit) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var contact by remember { mutableStateOf("") }
    var advanced by remember { mutableStateOf(false) }
    var trusted by remember { mutableStateOf(false) }
    var showResetDialog by remember { mutableStateOf(false) }

    if (state.showLiveConfirmDialog) AlertDialog(
        onDismissRequest = viewModel::dismissLiveDialog,
        title = { Text("Enable live holds?") },
        text = { Text("Live mode can cancel and snooze notifications on this device. Use dry run first to check the decisions.") },
        confirmButton = { Button(onClick = viewModel::confirmLiveMode) { Text("Enable live") } },
        dismissButton = { TextButton(onClick = viewModel::dismissLiveDialog) { Text("Keep current mode") } }
    )

    if (showResetDialog) AlertDialog(
        onDismissRequest = { showResetDialog = false },
        title = { Text("Wipe & Reset My Data?") },
        text = { Text("This will permanently delete all held notifications, decisions, feedback, local telemetry events, generate a fresh unique device ID, and trigger a data reset on your laptop companion.") },
        confirmButton = {
            Button(
                onClick = {
                    viewModel.wipeAndResetMyData(context)
                    showResetDialog = false
                },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Text("Wipe Everything")
            }
        },
        dismissButton = {
            TextButton(onClick = { showResetDialog = false }) {
                Text("Cancel")
            }
        }
    )

    ModalBottomSheet(onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Your space.", style = MaterialTheme.typography.headlineMedium)
                TextButton(onClick = onDismiss) { Text("Done") }
            }
            SettingsCard("Notification policy") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PolicyMode.entries.forEach { mode ->
                        ContextChip(mode.name.replace('_', ' '), state.settings.mode == mode,
                            { viewModel.requestModeChange(mode) })
                    }
                }
                Text("Off observes only. Dry run previews decisions. Live can hold notifications.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                SettingSwitch("Emergency stop", "Release held items and suspend actuation.",
                    state.settings.killSwitch) { viewModel.toggleKillSwitch() }
            }
            SettingsCard("Focus Shield & App Restrictor") {
                var shieldEnabled by remember { mutableStateOf(com.dhyan.app.collectors.A11yCollector.focusShieldEnabled) }
                var doomscrollEnabled by remember { mutableStateOf(com.dhyan.app.collectors.A11yCollector.doomscrollBrakeEnabled) }
                val isA11yOn = com.dhyan.app.collectors.A11yCollector.isServiceConnected

                StatusPillBadge(if (isA11yOn && shieldEnabled) "Active Shield" else "Focus Shield",
                    active = isA11yOn && shieldEnabled)

                SettingSwitch(
                    "Block distracting apps during Focus",
                    "Intercepts Instagram, TikTok, Reels, Shorts, and games when a focus session is active.",
                    shieldEnabled
                ) {
                    shieldEnabled = it
                    com.dhyan.app.collectors.A11yCollector.focusShieldEnabled = it
                }

                SettingSwitch(
                    "Doomscroll Brake",
                    "Detects compulsive scroll bursts and triggers gentle haptic brakes to pause endless scrolling.",
                    doomscrollEnabled
                ) {
                    doomscrollEnabled = it
                    com.dhyan.app.collectors.A11yCollector.doomscrollBrakeEnabled = it
                }

                var bedtimeEnabled by remember { mutableStateOf(com.dhyan.app.collectors.A11yCollector.bedtimeGuardianEnabled) }
                SettingSwitch(
                    "Bedtime Doomscroll Guardian",
                    "Detects late night scrolling in bed (11 PM - 6 AM) and gently guides you to sleep.",
                    bedtimeEnabled
                ) {
                    bedtimeEnabled = it
                    com.dhyan.app.collectors.A11yCollector.bedtimeGuardianEnabled = it
                }

                Text(
                    "Essential apps (Phone, 2FA, Clock, Calculator) are never restricted.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (isA11yOn) {
                    Text("● Accessibility Interceptor Active · Zero screen text or URLs collected",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("⚠️ Accessibility service is disabled. Real-time app blocking requires accessibility permissions.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        OutlinedButton(onClick = {
                            val intent = android.content.Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                                flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                            }
                            context.startActivity(intent)
                        }, modifier = Modifier.fillMaxWidth()) {
                            Text("Open Accessibility Settings")
                        }
                    }
                }
            }
            SettingsCard("Digest rhythm") {
                Text("Release interval · ${state.settings.digestIntervalMin} min", style = MaterialTheme.typography.bodyMedium)
                Text("${state.settings.digestIntervalMin}", style = MaterialTheme.typography.displayLarge)
                Text("minutes between digest releases", style = MaterialTheme.typography.bodySmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    (listOf(15, 30, 45, 60, com.dhyan.app.twin.decision.PolicyDefaults.DIGEST_INTERVAL_MIN) +
                        state.settings.digestIntervalMin).distinct().sorted().forEach { minutes ->
                        ContextChip("$minutes min", state.settings.digestIntervalMin == minutes,
                            { viewModel.setDigestIntervalMin(minutes) })
                    }
                }
            }
            CrossDeviceSyncSection(viewModel)
            SettingsCard("Trusted senders · ${state.vipHashes.size}") {
                Text("Trusted senders are never held. Only a privacy-preserving hash is stored.",
                    style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = { trusted = !trusted }) { Text(if (trusted) "Hide senders −" else "Manage senders +") }
                if (trusted) {
                    OutlinedTextField(contact, { contact = it }, label = { Text("Contact or handle") },
                        modifier = Modifier.fillMaxWidth(), singleLine = true)
                    Button(enabled = contact.isNotBlank(), onClick = {
                        viewModel.addVipSender(contact)
                        contact = ""
                    }) { Text("Add trusted sender") }
                    state.vipHashes.forEachIndexed { index, hash ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Trusted sender ${index + 1}", Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyLarge)
                            TextButton(onClick = { viewModel.removeVipSender(hash) }) { Text("Remove") }
                        }
                    }
                }
            }
            SettingsCard("Data & Reset") {
                Text("Clear phone Room databases (held notifications, decisions, feedback, events) and trigger a full reset on the laptop companion.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedButton(
                    onClick = { showResetDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Wipe & Reset My Data")
                }
            }
            TextButton(onClick = { advanced = !advanced }) { Text(if (advanced) "Hide advanced settings −" else "Advanced settings +") }
            if (advanced) SettingsCard("Policy details") {
                Text(String.format(Locale.US, "Caution multiplier · %.1f", state.settings.kappaDPerHour))
                Slider(state.settings.kappaDPerHour.toFloat(),
                    { viewModel.setCautionMultiplier(it.toDouble()) }, valueRange = .5f..5f)
                SettingSwitch("Exploration", String.format(Locale.US,
                    "Configured exploration rate: %.1f%%", state.settings.epsilon * 100),
                    state.settings.explorationEnabled, viewModel::setExplorationEnabled)
                Text("Never-hold safety rules always take priority.", style = MaterialTheme.typography.bodySmall)
                if (BuildConfig.DEBUG) {
                    HorizontalDivider()
                    Text("Developer tools", style = MaterialTheme.typography.titleMedium)
                    Text("Posts a synthetic notification for local testing.", style = MaterialTheme.typography.bodySmall)
                    OutlinedButton(onClick = viewModel::postTestNotification) { Text("Post test notification") }
                }
            }
            SettingsCard(stringResource(R.string.about_title)) {
                Text(HunchBrand.PRODUCT_NAME, style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.brand_descriptor), style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.brand_tagline), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(4.dp))
                Text(stringResource(R.string.brand_disclaimer), style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(4.dp))
                Text(stringResource(R.string.brand_codename_note), style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CrossDeviceSyncSection(viewModel: SettingsViewModel) {
    val status by viewModel.syncStatus.collectAsState()
    val relay by viewModel.relayUrl.collectAsState()
    val paired by viewModel.hasStoredCredentials.collectAsState()
    val session by viewModel.activeDeclaredSession.collectAsState()
    val commands by viewModel.recentCommands.collectAsState()
    var url by remember(relay) { mutableStateOf(relay) }
    var code by remember { mutableStateOf("") }
    var unpair by remember { mutableStateOf(false) }
    var details by remember { mutableStateOf(false) }
    if (unpair) AlertDialog(onDismissRequest = { unpair = false }, title = { Text("Unpair laptop?") },
        text = { Text("Stored pairing credentials and session keys will be cleared.") },
        confirmButton = { TextButton(onClick = { viewModel.unpair(); unpair = false }) { Text("Unpair") } },
        dismissButton = { TextButton(onClick = { unpair = false }) { Text("Cancel") } })
    SettingsCard("Laptop companion") {
        StatusPillBadge(if (status is SyncUiStatus.Connected) "Connected" else "Laptop sync",
            active = status is SyncUiStatus.Connected)
        Text(status.description(), style = MaterialTheme.typography.bodyLarge)
        val sas = status as? SyncUiStatus.SasVerification
        if (sas != null) {
            Text("Compare this code on both devices", style = MaterialTheme.typography.titleSmall)
            Text(sas.sasDigits.replace(" ", "").chunked(3).joinToString(" "), style = MaterialTheme.typography.headlineLarge)
            Text("Confirm only if both codes match.", style = MaterialTheme.typography.bodyMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = viewModel::rejectSas) { Text("Codes differ") }
                Button(onClick = viewModel::confirmSas) { Text("Codes match") }
            }
        }
        val pairingActive = status is SyncUiStatus.Joining || status is SyncUiStatus.WaitingOffer ||
            status is SyncUiStatus.SasVerification || status is SyncUiStatus.Paired ||
            status is SyncUiStatus.Connecting || status is SyncUiStatus.Connected || status is SyncUiStatus.Unsynced
        if (!paired && !pairingActive) {
            OutlinedTextField(url, { url = it }, label = { Text("Relay WebSocket address") },
                modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(code, { if (it.length <= 6) code = it.uppercase(Locale.ROOT) },
                label = { Text("Six-character pairing code") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            Button(modifier = Modifier.fillMaxWidth(), enabled = code.length == 6 && url.isNotBlank(),
                onClick = { viewModel.startPairing(code.trim(), url.trim()); code = "" }) { Text("Pair laptop") }
        }
        if (paired) TextButton(onClick = { unpair = true }) { Text("Unpair laptop") }
        session?.let { active ->
            Text("Declared session", style = MaterialTheme.typography.titleMedium)
            Text("${active.task.wire} · ${active.plannedMin.toInt()} min planned${if (active.strict) " · strict" else ""}",
                style = MaterialTheme.typography.bodyMedium)
        }
        TextButton(onClick = { details = !details }) { Text(if (details) "Hide sync details −" else "Sync details +") }
        if (details) {
            Text(relay, style = MaterialTheme.typography.bodySmall)
            (status as? SyncUiStatus.Connected)?.let { Text("Peer sequence · ${it.peerLastSeq}", style = MaterialTheme.typography.bodySmall) }
            Text("Recent remote commands", style = MaterialTheme.typography.titleSmall)
            if (commands.isEmpty()) Text("No remote commands received.", style = MaterialTheme.typography.bodySmall)
            commands.forEach { command ->
                Text("${command.action} · ${command.status}", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun SettingSwitch(title: String, description: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked, onCheckedChange)
    }
}

@Composable
private fun SettingsCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    PastelSquircleCard {
        Text(title, style = MaterialTheme.typography.titleLarge)
        content()
    }
}

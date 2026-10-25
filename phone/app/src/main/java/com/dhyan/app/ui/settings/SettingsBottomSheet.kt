package com.dhyan.app.ui.settings

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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.dhyan.app.actuator.digest.AndroidDigestPoster
import com.dhyan.app.data.DhyanDatabase
import com.dhyan.app.twin.decision.PolicyMode
import com.dhyan.app.ui.model.DhyanStateHolder
import com.dhyan.app.ui.theme.BorderHighlight
import com.dhyan.app.ui.theme.BorderSubtle
import com.dhyan.app.ui.theme.DhyanPrimary
import com.dhyan.app.ui.theme.StateDistracted
import com.dhyan.app.ui.theme.SurfaceBase
import com.dhyan.app.ui.theme.SurfaceCard
import com.dhyan.app.ui.theme.SurfaceElevated
import com.dhyan.app.ui.theme.TextMuted
import com.dhyan.app.ui.theme.TextPrimary
import com.dhyan.app.ui.theme.TextSecondary
import com.dhyan.app.ui.viewmodel.SettingsViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsBottomSheet(
    stateHolder: DhyanStateHolder? = null,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val db = remember(context) { DhyanDatabase.getInstance(context) }
    val digestPoster = remember(context) { AndroidDigestPoster(context) }
    val viewModel = remember(db) {
        SettingsViewModel(
            vipDao = db.vipDao(),
            settingsDao = db.settingsDao(),
            digestPoster = digestPoster
        )
    }
    SettingsBottomSheet(viewModel = viewModel, onDismiss = onDismiss)
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsBottomSheet(
    viewModel: SettingsViewModel,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    val uiState by viewModel.uiState.collectAsState()
    var newVipContact by remember { mutableStateOf("") }

    if (uiState.showLiveConfirmDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissLiveDialog() },
            title = { Text("Confirm LIVE Policy Mode", fontWeight = FontWeight.Bold, color = TextPrimary) },
            text = {
                Text(
                    "LIVE mode enables real notification cancellation and snoozing on this device.\n\n" +
                    "Make sure you have tested DRY RUN first to observe what Dhyan will hold.",
                    color = TextSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.confirmLiveMode() },
                    colors = ButtonDefaults.buttonColors(containerColor = DhyanPrimary)
                ) {
                    Text("Enable LIVE", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissLiveDialog() }) {
                    Text("Cancel", color = TextMuted)
                }
            },
            containerColor = SurfaceCard
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = SurfaceBase,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 12.dp)
                    .width(40.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(BorderSubtle)
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Settings & Policy Controls",
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    color = TextPrimary
                )
                Text(
                    text = "Close",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = DhyanPrimary,
                    modifier = Modifier.clickable {
                        scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
                    }
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            // 1. Policy Mode Selector
            Text(
                text = "ACTUATION POLICY MODE",
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.0.sp
                ),
                color = TextSecondary
            )
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(PolicyMode.OFF, PolicyMode.DRY_RUN, PolicyMode.LIVE).forEach { mode ->
                    val isSelected = uiState.settings.mode == mode
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isSelected) DhyanPrimary.copy(alpha = 0.2f) else SurfaceCard)
                            .border(1.dp, if (isSelected) DhyanPrimary else BorderSubtle, RoundedCornerShape(10.dp))
                            .clickable { viewModel.requestModeChange(mode) }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = mode.name,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                fontFamily = FontFamily.Monospace
                            ),
                            color = if (isSelected) DhyanPrimary else TextSecondary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 2. Safety & Governance Card
            Text(
                text = "SAFETY & CONTROLS",
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.0.sp
                ),
                color = TextSecondary
            )
            Spacer(modifier = Modifier.height(8.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(SurfaceCard)
                    .border(1.dp, BorderSubtle, RoundedCornerShape(14.dp))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Caution Multiplier (kappaDPerHour)
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Caution Multiplier (κ_d/h)",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                            color = TextPrimary
                        )
                        Text(
                            text = String.format("%.2f", uiState.settings.kappaDPerHour),
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = DhyanPrimary
                        )
                    }
                    Slider(
                        value = uiState.settings.kappaDPerHour.toFloat(),
                        onValueChange = { viewModel.setCautionMultiplier(it.toDouble()) },
                        valueRange = 0.5f..5.0f,
                        colors = SliderDefaults.colors(
                            thumbColor = DhyanPrimary,
                            activeTrackColor = DhyanPrimary
                        )
                    )
                }

                // Exploration Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "5% Counterfactual Exploration",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                            color = TextPrimary
                        )
                        Text(
                            text = "Log propensities & randomize holds to prevent blind spots",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextMuted
                        )
                    }
                    Switch(
                        checked = uiState.settings.explorationEnabled,
                        onCheckedChange = { viewModel.setExplorationEnabled(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = TextPrimary,
                            checkedTrackColor = DhyanPrimary
                        )
                    )
                }

                // Digest Interval (15, 30, 45, 60 min)
                Column {
                    Text(
                        text = "Digest Batch Interval: ${uiState.settings.digestIntervalMin} min",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(15, 30, 45, 60).forEach { min ->
                            val isSel = uiState.settings.digestIntervalMin == min
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSel) DhyanPrimary else SurfaceElevated)
                                    .clickable { viewModel.setDigestIntervalMin(min) }
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = "${min}m",
                                    color = if (isSel) Color.Black else TextSecondary,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                    }
                }

                // Emergency Kill Switch
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Emergency Kill Switch",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = if (uiState.settings.killSwitch) StateDistracted else TextPrimary
                            )
                        )
                        Text(
                            text = "Release all held items immediately; suspend actuation",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextMuted
                        )
                    }
                    Switch(
                        checked = uiState.settings.killSwitch,
                        onCheckedChange = { viewModel.toggleKillSwitch() },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = StateDistracted
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 3. VIP Senders List (Privacy Hashed)
            Text(
                text = "NEVER-HOLD VIP SENDERS",
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.0.sp
                ),
                color = TextSecondary
            )
            Text(
                text = "Stored as privacy-preserving SHA-256 hashes only. Raw names never persisted.",
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted
            )
            Spacer(modifier = Modifier.height(8.dp))

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
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = newVipContact,
                        onValueChange = { newVipContact = it },
                        placeholder = { Text("Contact or handle name", color = TextMuted) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedBorderColor = DhyanPrimary,
                            unfocusedBorderColor = BorderSubtle
                        )
                    )
                    Button(
                        onClick = {
                            viewModel.addVipSender(newVipContact)
                            newVipContact = ""
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = DhyanPrimary),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Add", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                if (uiState.vipHashes.isEmpty()) {
                    Text(
                        text = "No VIP contacts added yet.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        uiState.vipHashes.forEach { hash ->
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
                                    text = "hash: $hash",
                                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                    color = TextSecondary
                                )
                                Text(
                                    text = "Remove",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = StateDistracted,
                                    modifier = Modifier.clickable { viewModel.removeVipSender(hash) }
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 4. Debug & Target Testing Tools
            Text(
                text = "DEBUG & GATE 2 TESTING TOOLS",
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.0.sp
                ),
                color = TextSecondary
            )
            Spacer(modifier = Modifier.height(8.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(SurfaceElevated)
                    .border(1.dp, BorderSubtle, RoundedCornerShape(14.dp))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Post Test Notification",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = TextPrimary
                        )
                        Text(
                            text = "Trigger a synthetic notification in the digest channel to test Reno 14 rendering",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextMuted
                        )
                    }
                    Button(
                        onClick = { viewModel.postTestNotification() },
                        colors = ButtonDefaults.buttonColors(containerColor = DhyanPrimary),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Post", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(30.dp))
        }
    }
}

package com.dhyan.app.ui.components

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dhyan.app.digest.BreakDigestVault
import com.dhyan.app.pocketpark.PocketParkManager

/**
 * Interactive Break Digest Card rendering notifications held in volatile RAM
 * with 1-tap native PendingIntent launch into the target app.
 */
@Composable
fun InteractiveBreakDigestCard(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val vault = remember(context) { BreakDigestVault.getInstance(context) }
    var groups by remember { mutableStateOf(vault.getHeldGroups()) }
    val totalHeld = vault.getTotalHeldCount()

    PastelSquircleCard(modifier = modifier, tone = PastelTone.LAVENDER) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            StatusPillBadge(
                if (totalHeld > 0) "📬 Held for Break · $totalHeld alerts" else "📬 Break Digest · Clear",
                active = totalHeld > 0
            )
            if (totalHeld > 0) {
                TextButton(onClick = {
                    vault.clearAll()
                    groups = vault.getHeldGroups()
                }) {
                    Text("Clear All")
                }
            }
        }

        Text(
            "Actionable Break Digest",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        Text(
            if (totalHeld > 0)
                "Held quietly in memory so you could stay in flow. Tap any app to jump directly into the conversation."
            else
                "No notifications waiting. Your break digest is clean and calm.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (groups.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                groups.forEach { group ->
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.outlineVariant
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = group.appLabel,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                if (group.senderTitles.isNotEmpty()) {
                                    Text(
                                        text = group.senderTitles.joinToString(", "),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Text(
                                    text = "${group.count} alert${if (group.count > 1) "s" else ""} held",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                OutlinedButton(
                                    onClick = {
                                        vault.dismissGroup(group.packageName)
                                        groups = vault.getHeldGroups()
                                    },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text("Dismiss", style = MaterialTheme.typography.labelSmall)
                                }

                                Button(
                                    onClick = {
                                        val launched = vault.launchGroupAndDismiss(context, group.packageName)
                                        groups = vault.getHeldGroups()
                                        if (!launched) {
                                            Toast.makeText(context, "Opened ${group.appLabel}", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                                ) {
                                    Text("Open", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Pocket Park Card allowing users to quick-capture random distracting thoughts or lookup intentions
 * in 1 tap so they don't open a browser tab during focus.
 */
@Composable
fun PocketParkCard(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val manager = remember(context) { PocketParkManager.getInstance(context) }
    var thoughts by remember { mutableStateOf(manager.getParkedThoughts()) }
    var inputText by remember { mutableStateOf("") }

    PastelSquircleCard(modifier = modifier, tone = PastelTone.SKY) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            StatusPillBadge("📝 Pocket Park · Offline", active = true)
            Text(
                "${thoughts.count { !it.isCheckedOff }} active",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
            )
        }

        Text(
            "Quick Thought Dump",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        Text(
            "Park stray thoughts or lookup intentions here so you don't open mobile Chrome and fall down a rabbit hole.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = inputText,
                onValueChange = { inputText = it },
                placeholder = { Text("Look up TURN server docs later...", style = MaterialTheme.typography.bodySmall) },
                modifier = Modifier.weight(1f),
                singleLine = true,
                shape = RoundedCornerShape(16.dp)
            )

            Button(
                onClick = {
                    if (inputText.isNotBlank()) {
                        manager.parkThought(inputText)
                        inputText = ""
                        thoughts = manager.getParkedThoughts()
                    }
                },
                shape = RoundedCornerShape(16.dp)
            ) {
                Text("Park")
            }
        }

        if (thoughts.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                thoughts.take(4).forEach { item ->
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.outlineVariant
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (item.isCheckedOff) "✓ ${item.text}" else "• ${item.text}",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (item.isCheckedOff) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )

                            Row {
                                TextButton(
                                    onClick = {
                                        manager.checkOffThought(item.id)
                                        thoughts = manager.getParkedThoughts()
                                    },
                                    contentPadding = PaddingValues(horizontal = 6.dp)
                                ) {
                                    Text(if (item.isCheckedOff) "Undo" else "Done", style = MaterialTheme.typography.labelSmall)
                                }

                                TextButton(
                                    onClick = {
                                        manager.dismissThought(item.id)
                                        thoughts = manager.getParkedThoughts()
                                    },
                                    contentPadding = PaddingValues(horizontal = 6.dp)
                                ) {
                                    Text("✕", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Smart Flow Triggers Card showing the autonomous on-device behavioral intelligence status.
 */
@Composable
fun SmartFlowTriggersCard(modifier: Modifier = Modifier) {
    PastelSquircleCard(modifier = modifier, tone = PastelTone.SAGE) {
        StatusPillBadge("⚡ Behavioral Intelligence Engines", active = true)
        Text(
            "Autonomous Flow Protectors",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        Text(
            "Real-time hardware & muscle-memory detectors operating 100% on-device with zero battery drain.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // Trigger 1: Earbuds Flow
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("🎧", style = MaterialTheme.typography.titleMedium)
                    Column {
                        Text("Earbuds-In Auto-Flow", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Text(
                            "Surfaces a 1-tap 25m Focus Shield prompt when personal headphones connect outside bedtime.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Trigger 2: Muscle-Memory Unlock
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("📱", style = MaterialTheme.typography.titleMedium)
                    Column {
                        Text("Muscle-Memory Reality Check", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Text(
                            "Catches 3 quick unlocks in 15m or reflex social open (<3s) to break unconscious check habits.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Trigger 3: Doomscroll Velocity Breaker
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("🛑", style = MaterialTheme.typography.titleMedium)
                    Column {
                        Text("Doomscroll Velocity Breaker", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Text(
                            "Gentle nudge at 4m (8m daytime) and firm full-screen breaker at 8m (15m daytime) on Reels & Shorts.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

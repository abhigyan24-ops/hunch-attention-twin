package com.dhyan.app.ui.shield

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dhyan.app.DhyanApplication
import com.dhyan.app.collectors.A11yCollector
import com.dhyan.app.ui.components.PastelSquircleCard
import com.dhyan.app.ui.components.PastelTone
import com.dhyan.app.ui.components.StatusPillBadge
import com.dhyan.app.ui.pet.DhyanPetCharacter
import com.dhyan.app.ui.pet.PetEmotion
import androidx.compose.ui.geometry.Offset
import com.dhyan.app.ui.pet.toFacialParams
import com.dhyan.app.ui.theme.DhyanTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class FocusShieldActivity : ComponentActivity() {

    companion object {
        const val EXTRA_BLOCKED_APP = "extra_blocked_app"
        const val EXTRA_REASON = "extra_reason"
        const val EXTRA_TASK_NAME = "extra_task_name"
        const val EXTRA_REMAINING_MIN = "extra_remaining_min"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val blockedApp = intent.getStringExtra(EXTRA_BLOCKED_APP) ?: "Social Media"
        val reason = intent.getStringExtra(EXTRA_REASON) ?: "app_restricted"
        val taskName = intent.getStringExtra(EXTRA_TASK_NAME) ?: "Focus"
        val remainingMin = intent.getIntExtra(EXTRA_REMAINING_MIN, 25)

        setContent {
            DhyanTheme {
                FocusShieldContent(
                    blockedApp = blockedApp,
                    reason = reason,
                    taskName = taskName,
                    remainingMin = remainingMin,
                    onReturnToFocus = {
                        finish()
                    },
                    onMindfulPause = {
                        A11yCollector.grantMindfulPause(60_000L)
                        finish()
                    },
                    onEndSessionEarly = {
                        CoroutineScope(Dispatchers.IO).launch {
                            runCatching {
                                DhyanApplication.syncManager.phoneControl.endSession()
                            }
                        }
                        finish()
                    }
                )
            }
        }
    }
}

@Composable
fun FocusShieldContent(
    blockedApp: String,
    reason: String,
    taskName: String,
    remainingMin: Int,
    onReturnToFocus: () -> Unit,
    onMindfulPause: () -> Unit,
    onEndSessionEarly: () -> Unit
) {
    var showEndConfirm by remember { mutableStateOf(false) }

    if (showEndConfirm) {
        AlertDialog(
            onDismissRequest = { showEndConfirm = false },
            title = { Text("End focus session early?") },
            text = { Text("Ending your session will remove active app restrictions and reset your current focus streak.") },
            confirmButton = {
                Button(
                    onClick = {
                        showEndConfirm = false
                        onEndSessionEarly()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("End Session")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEndConfirm = false }) {
                    Text("Stay in Focus")
                }
            }
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background
    ) { insets ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(insets)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Spacer(Modifier.height(16.dp))

            // 1. Frosted Cloud Spirit Character Stage
            Box(
                modifier = Modifier.size(120.dp),
                contentAlignment = Alignment.Center
            ) {
                DhyanPetCharacter(
                    emotion = PetEmotion.CURIOUS,
                    facialParams = PetEmotion.CURIOUS.toFacialParams(false),
                    isBlinking = false,
                    lookTarget = Offset(60f, 60f),
                    petCenter = Offset(60f, 60f),
                    scaleX = 1f,
                    scaleY = 1f,
                    speechBubble = null,
                    modifier = Modifier.size(120.dp)
                )
            }

            // 2. Editorial Headline
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = buildAnnotatedString {
                        append("one. two.\n")
                        pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
                        append("pause."); pop()
                    },
                    style = MaterialTheme.typography.displayLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            // 3. Bento Shield Card
            PastelSquircleCard(
                tone = PastelTone.SAGE,
                modifier = Modifier.fillMaxWidth()
            ) {
                StatusPillBadge("Focus Shield · Active", active = true)

                val title = if (reason == "doomscroll_detected") {
                    "Doomscroll loop interrupted"
                } else {
                    "Attention protected"
                }

                val body = if (reason == "doomscroll_detected") {
                    "Rapid feed or reel scrolling detected on ${blockedApp.replaceFirstChar { it.uppercase() }}. Take a deep breath."
                } else {
                    "${blockedApp.replaceFirstChar { it.uppercase() }} is restricted while your ${taskName.replaceFirstChar { it.uppercase() }} session is in progress."
                }

                Text(title, style = MaterialTheme.typography.titleLarge)
                Text(body, style = MaterialTheme.typography.bodyLarge)

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "$remainingMin min",
                            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "Remaining in ${taskName.lowercase()} session",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surface
                    ) {
                        Text(
                            text = "🛡️ Shielded",
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            }

            Spacer(Modifier.weight(1f))

            // 4. Primary & Secondary Actions
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Return to Focus CTA
                Button(
                    onClick = onReturnToFocus,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(28.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF121417),
                        contentColor = Color.White
                    )
                ) {
                    Text(
                        text = "Return to Focus",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                    )
                }

                // Mindful 1-Min Grace Period
                OutlinedButton(
                    onClick = onMindfulPause,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(25.dp)
                ) {
                    Text(
                        text = "Take 1-Min Mindful Pause",
                        style = MaterialTheme.typography.bodyLarge
                    )
                }

                // Emergency Exit
                TextButton(
                    onClick = { showEndConfirm = true }
                ) {
                    Text(
                        text = "End session early",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        }
    }
}

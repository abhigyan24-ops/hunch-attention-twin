package com.dhyan.app.ui.shield

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
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
import com.dhyan.app.ui.pet.toFacialParams
import com.dhyan.app.ui.theme.DhyanTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
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

        val blockedApp = intent.getStringExtra(EXTRA_BLOCKED_APP) ?: "App"
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

    // Box Breathing State
    var breathingActive by remember { mutableStateOf(false) }
    var breathPhase by remember { mutableIntStateOf(0) } // 0: Inhale, 1: Hold, 2: Exhale, 3: Hold
    var breathSeconds by remember { mutableIntStateOf(4) }
    var completedCycles by remember { mutableIntStateOf(0) }

    // Intentional 2.5s Press-and-Hold Friction State
    var holdProgress by remember { mutableFloatStateOf(0f) }
    var isHolding by remember { mutableStateOf(false) }

    // Box Breathing loop
    LaunchedEffect(breathingActive) {
        if (!breathingActive) return@LaunchedEffect
        while (isActive && breathingActive) {
            for (sec in 4 downTo 1) {
                breathSeconds = sec
                delay(1000L)
            }
            breathPhase = (breathPhase + 1) % 4
            if (breathPhase == 0) {
                completedCycles++
            }
        }
    }

    // Intentional hold counter loop
    LaunchedEffect(isHolding) {
        if (!isHolding) {
            holdProgress = 0f
            return@LaunchedEffect
        }
        val startTime = System.currentTimeMillis()
        while (isHolding) {
            val elapsed = System.currentTimeMillis() - startTime
            holdProgress = (elapsed / 2500f).coerceIn(0f, 1f)
            if (holdProgress >= 1f) {
                onMindfulPause()
                break
            }
            delay(50L)
        }
    }

    // Smooth Pet Breathing Scaling
    val petScale by animateFloatAsState(
        targetValue = if (!breathingActive) 1f else when (breathPhase) {
            0 -> 1.32f // Inhale
            1 -> 1.32f // Hold
            2 -> 0.82f // Exhale
            else -> 0.82f // Rest
        },
        animationSpec = tween(durationMillis = if (breathingActive) 3800 else 600),
        label = "petScale"
    )

    val isBedtime = reason == "bedtime_guardian"

    val currentEmotion = when {
        isBedtime -> PetEmotion.SLEEPY
        breathingActive -> PetEmotion.MEDITATING
        else -> PetEmotion.CURIOUS
    }

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
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Spacer(Modifier.height(12.dp))

            // 1. Frosted Cloud Spirit Character Stage with Breathing Physics
            Box(
                modifier = Modifier.size(130.dp),
                contentAlignment = Alignment.Center
            ) {
                DhyanPetCharacter(
                    emotion = currentEmotion,
                    facialParams = currentEmotion.toFacialParams(false),
                    isBlinking = false,
                    lookTarget = Offset(65f, 65f),
                    petCenter = Offset(65f, 65f),
                    scaleX = petScale,
                    scaleY = petScale,
                    speechBubble = null,
                    modifier = Modifier.size(130.dp)
                )
            }

            // 2. Editorial Headline
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (isBedtime) {
                    Text(
                        text = buildAnnotatedString {
                            append("time to rest.\n")
                            pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
                            append("goodnight."); pop()
                        },
                        style = MaterialTheme.typography.displayLarge,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                } else {
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
            }

            // 3. Bento Shield Card
            PastelSquircleCard(
                tone = if (isBedtime) PastelTone.LAVENDER else PastelTone.SAGE,
                modifier = Modifier.fillMaxWidth()
            ) {
                StatusPillBadge(
                    text = if (isBedtime) "Bedtime Guardian · 11 PM - 6 AM" else "Focus Shield · Active",
                    active = true
                )

                val title = when (reason) {
                    "bedtime_guardian" -> "Nighttime doomscroll detected"
                    "doomscroll_detected" -> "Compulsive loop interrupted"
                    else -> "Attention protected"
                }

                val body = when (reason) {
                    "bedtime_guardian" -> "You have been scrolling late into the night. Close your screen, rest your eyes, and recharge for tomorrow."
                    "doomscroll_detected" -> "Rapid feed or short-form scrolling detected on ${blockedApp.replaceFirstChar { it.uppercase() }}. Step back for a moment."
                    else -> "${blockedApp.replaceFirstChar { it.uppercase() }} is restricted while your ${taskName.replaceFirstChar { it.uppercase() }} session is armed."
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
                        if (!isBedtime) {
                            Text(
                                text = "$remainingMin min",
                                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = "Remaining in ${taskName.lowercase()} session",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            Text(
                                text = "Rest Mode",
                                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = "Preserving circadian rhythm",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surface
                    ) {
                        Text(
                            text = if (isBedtime) "🌙 Bedtime" else "🛡️ Shielded",
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            }

            // 4. Interactive Box Breathing Reset Card
            PastelSquircleCard(
                tone = PastelTone.SKY,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "🫁 Box Breathing Reset",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = if (!breathingActive) {
                                "Reset dopamine craving with a 16s guided breath"
                            } else {
                                when (breathPhase) {
                                    0 -> "Inhale slowly (${breathSeconds}s)..."
                                    1 -> "Hold gently (${breathSeconds}s)..."
                                    2 -> "Exhale smoothly (${breathSeconds}s)..."
                                    else -> "Rest before next breath (${breathSeconds}s)..."
                                }
                            },
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Button(
                        onClick = {
                            breathingActive = !breathingActive
                            if (!breathingActive) {
                                breathPhase = 0
                                breathSeconds = 4
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (breathingActive) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer,
                            contentColor = if (breathingActive) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    ) {
                        Text(if (breathingActive) "Stop" else "Breathe")
                    }
                }

                if (breathingActive) {
                    LinearProgressIndicator(
                        progress = { (4 - breathSeconds + 1) / 4f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                    )
                    Text(
                        text = "Completed cycles: $completedCycles",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.weight(1f))

            // 5. Actions & Friction Controls
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
                        text = if (isBedtime) "Put Phone Down & Sleep" else "Return to Focus",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                    )
                }

                // Intentional 2.5s Friction Button ("Need this for work?")
                Surface(
                    shape = RoundedCornerShape(25.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onPress = {
                                    isHolding = true
                                    tryAwaitRelease()
                                    isHolding = false
                                }
                            )
                        }
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.CenterStart) {
                        if (holdProgress > 0f) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth(holdProgress)
                                    .background(Color(0xFF7AD656).copy(alpha = 0.35f))
                            )
                        }
                        Text(
                            text = if (isHolding) "Hold to unlock 1-min pass (${(2.5f - holdProgress * 2.5f).coerceAtLeast(0.1f).toInt()}s)..." else "Hold for 2.5s to unlock intentional pass",
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                        )
                    }
                }

                // Emergency Exit
                if (!isBedtime) {
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
}

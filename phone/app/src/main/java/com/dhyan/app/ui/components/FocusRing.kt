package com.dhyan.app.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dhyan.app.ui.model.AttentionState
import com.dhyan.app.ui.theme.StateDeepFocus
import com.dhyan.app.ui.theme.StateDeepFocusBg
import com.dhyan.app.ui.theme.StateDeepFocusGlow
import com.dhyan.app.ui.theme.StateDistracted
import com.dhyan.app.ui.theme.StateDistractedBg
import com.dhyan.app.ui.theme.StateDistractedGlow
import com.dhyan.app.ui.theme.StateDrifting
import com.dhyan.app.ui.theme.StateDriftingBg
import com.dhyan.app.ui.theme.StateDriftingGlow
import com.dhyan.app.ui.theme.StateFatigued
import com.dhyan.app.ui.theme.StateFatiguedBg
import com.dhyan.app.ui.theme.StateRecovering
import com.dhyan.app.ui.theme.StateRecoveringBg
import com.dhyan.app.ui.theme.StateRecoveringGlow
import com.dhyan.app.ui.theme.StateWorking
import com.dhyan.app.ui.theme.StateWorkingBg
import com.dhyan.app.ui.theme.StateWorkingGlow
import com.dhyan.app.ui.theme.TextMuted
import com.dhyan.app.ui.theme.TextPrimary

@Composable
fun FocusRing(
    state: AttentionState,
    sessionMinutes: Int,
    modifier: Modifier = Modifier,
    ringSize: Dp = 190.dp
) {
    val (primaryColor, glowColor, bgColor) = when (state) {
        AttentionState.DEEP_FOCUS -> Triple(StateDeepFocus, StateDeepFocusGlow, StateDeepFocusBg)
        AttentionState.WORKING -> Triple(StateWorking, StateWorkingGlow, StateWorkingBg)
        AttentionState.DRIFTING -> Triple(StateDrifting, StateDriftingGlow, StateDriftingBg)
        AttentionState.DISTRACTED -> Triple(StateDistracted, StateDistractedGlow, StateDistractedBg)
        AttentionState.RECOVERING -> Triple(StateRecovering, StateRecoveringGlow, StateRecoveringBg)
        AttentionState.FATIGUED -> Triple(StateFatigued, Color(0xFFC9D1D9), StateFatiguedBg)
    }

    val infiniteTransition = rememberInfiniteTransition(label = "ring_pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    val sweepProgress = ((sessionMinutes % 60) / 60f).coerceIn(0.05f, 1.0f)

    Box(
        modifier = modifier.size(ringSize),
        contentAlignment = Alignment.Center
    ) {
        // Outer pulsing aura
        Box(
            modifier = Modifier
                .size(ringSize - 16.dp)
                .clip(CircleShape)
                .background(bgColor.copy(alpha = pulseAlpha * 0.45f))
        )

        // Smooth Canvas Ring
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = 8.dp.toPx()
            val radius = (size.minDimension - strokeWidth) / 2

            // Background subtle track
            drawCircle(
                color = Color(0xFF21262D),
                radius = radius,
                style = Stroke(width = strokeWidth)
            )

            // Dynamic Progress Arc with gradient
            drawArc(
                brush = Brush.sweepGradient(
                    listOf(primaryColor.copy(alpha = 0.5f), primaryColor, glowColor)
                ),
                startAngle = -90f,
                sweepAngle = sweepProgress * 360f,
                useCenter = false,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
        }

        // Inner Content
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "${sessionMinutes}m",
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 32.sp
                ),
                color = TextPrimary
            )
            Text(
                text = state.label.uppercase(),
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp
                ),
                color = glowColor
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "active focus",
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted
            )
        }
    }
}

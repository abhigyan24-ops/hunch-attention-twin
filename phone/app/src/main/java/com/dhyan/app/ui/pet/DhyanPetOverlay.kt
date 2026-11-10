package com.dhyan.app.ui.pet

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.ViewConfiguration
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dhyan.app.ui.model.LiveContextHolder
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/**
 * In-app host for the Frosted Cloud Spirit companion.
 *
 * Implements silky-smooth spring-damper dynamics across 100% of the screen, the 4-Stage
 * Distraction Escalation & Adaptive Threshold Loop:
 *
 * 1. Stage 1 — Subtle Gaze & Ear/Brow Alert (0–5s of Distraction / Idle Drift):
 *    The pet stays docked peacefully at the screen edge (PEEK_MODE), narrowing its eyes into
 *    FOCUSED / CURIOUS and tracking touch toward the distraction.
 * 2. Stage 2 — Edge Peek & Knock (5–12s of Distraction):
 *    The pet slides out from the edge, does a double-hop, and displays a non-intrusive pill
 *    bubble ("Drifting off?") with [Back to Task] and [✕].
 * 3. Stage 3 — Active Screen Walk-In / Center Interceptor (>12s or high distraction):
 *    The pet launches to the screen center using critically damped spring velocity, lands with
 *    a jelly squash, waves/pouts (POUTING / SURPRISED), and presents a 1-click "Refocus Now" action.
 *    Upon accepting or resuming work, it celebrates (HAPPY + sparkles) and glides back to the edge.
 * 4. Stage 4 — Self-Tuning Validation Counters & Auto-Backoff:
 *    Local counters (nudgesShown, nudgesAccepted, nudgesDismissed) with automatic threshold backoff
 *    (1.5x cooldown multiplier when dismissals exceed 60% after >= 5 nudges).
 */
@Composable
fun DhyanPetOverlay(
    isTaskActive: Boolean = false,
    controller: PetDistractionController = remember { PetDistractionController.getInstance() },
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val physicsState = remember { PetPhysicsState(0f, 0f) }
    val particleEmitter = remember { ParticleEmitter(0xB00) }
    val behavior = remember { PetOverlayBehavior() }
    val coordinates = remember { PetCoordinateSpace() }
    var hostSize by remember { mutableStateOf(IntSize.Zero) }
    var minimized by rememberSaveable { mutableStateOf(false) }
    val taskActive by rememberUpdatedState(isTaskActive)
    val density = LocalDensity.current
    val petSizePx by rememberUpdatedState(with(density) { 96.dp.toPx() })

    LaunchedEffect(Unit) {
        var firstFrame = 0L
        var previousFrame = 0L
        var positioned = false
        while (isActive) {
            val frame = withFrameNanos { it }
            if (firstFrame == 0L) firstFrame = frame
            val dt = if (previousFrame == 0L) 0f else
                ((frame - previousFrame) / 1_000_000_000f).coerceIn(0f, .033f)
            previousFrame = frame
            val nowMs = (frame - firstFrame) / 1_000_000L
            behavior.advance(nowMs, taskActive, physicsState.isDragged)

            val screenW = hostSize.width.toFloat()
            val screenH = hostSize.height.toFloat()

            if (screenW > 0f && screenH > 0f && screenW >= petSizePx && screenH >= petSizePx) {
                val marginDockX = (screenW - petSizePx - 16f).coerceAtLeast(0f)
                val marginDockY = (screenH * 0.28f).coerceIn(16f, screenH - petSizePx - 16f)

                if (!positioned) {
                    physicsState.x = marginDockX
                    physicsState.y = marginDockY
                    physicsState.isDockedEdge = true
                    positioned = true
                }

                if (minimized) {
                    physicsState.vx = 0f
                    physicsState.vy = 0f
                }

                // 4-Stage Distraction Escalation State Machine Step
                val snapshot = LiveContextHolder.currentSnapshot()
                val focusState = snapshot?.focusState
                controller.step(focusState, taskActive, nowMs)

                if (!physicsState.isDragged && !minimized) {
                    val targetX: Float
                    val targetY: Float

                    when (controller.stage) {
                        DistractionStage.CALM -> {
                            targetX = marginDockX
                            // Silky smooth floating idle breathing bob
                            targetY = marginDockY + 6f * sin(nowMs / 450.0).toFloat()
                        }
                        DistractionStage.STAGE1_SUBTLE_GAZE -> {
                            // Stage 1 (0–5s): Stays docked at edge (PEEK_MODE), eyes focused/curious, tracks touch
                            targetX = marginDockX
                            targetY = marginDockY + 6f * sin(nowMs / 380.0).toFloat()
                            if (behavior.emotion != PetEmotion.FOCUSED && behavior.emotion != PetEmotion.CURIOUS) {
                                behavior.react(PetEmotion.CURIOUS, 1000L, null)
                            }
                        }
                        DistractionStage.STAGE2_EDGE_PEEK_KNOCK -> {
                            // Stage 2 (5–12s): Slides out 40dp into view, performs double-hop
                            targetX = (screenW - petSizePx - 56f).coerceAtLeast(0f)
                            targetY = marginDockY

                            val elapsed = nowMs - controller.distractionStartedMs
                            if (elapsed in 5200L..5600L && controller.hopCount == 0) {
                                physicsState.undock()
                                physicsState.vy = -220f
                                physicsState.impulseSquash(-1.2f)
                                controller.hopCount = 1
                            } else if (elapsed in 6800L..7200L && controller.hopCount == 1) {
                                physicsState.undock()
                                physicsState.vy = -190f
                                physicsState.impulseSquash(-1.0f)
                                controller.hopCount = 2
                            }
                        }
                        DistractionStage.STAGE3_CENTER_INTERCEPTOR -> {
                            // Stage 3 (>12s or high distraction): Launches into center-third of screen
                            targetX = (screenW * 0.5f - petSizePx * 0.5f).coerceAtLeast(0f)
                            targetY = (screenH * 0.42f).coerceAtLeast(0f)
                        }
                        DistractionStage.RETURN_TO_MARGIN -> {
                            // Glides back to closest docked screen margin
                            targetX = if (physicsState.x < screenW * 0.5f) 16f else marginDockX
                            targetY = marginDockY
                        }
                    }

                    // Analytical Critically-Damped Spring Dynamics
                    // Monotonic, zero-overshoot convergence at any frame rate (60Hz–120Hz)
                    val omega = when (controller.stage) {
                        DistractionStage.STAGE3_CENTER_INTERCEPTOR -> 9f
                        DistractionStage.RETURN_TO_MARGIN -> 10f
                        DistractionStage.STAGE2_EDGE_PEEK_KNOCK -> 11f
                        else -> 12f
                    }
                    physicsState.springTo(
                        targetX = targetX,
                        targetY = targetY,
                        omega = omega,
                        dtSeconds = dt,
                        boundsWidth = screenW,
                        boundsHeight = screenH,
                        petSizePx = petSizePx
                    )

                    val distFromTarget = hypot(targetX - physicsState.x, targetY - physicsState.y)
                    if (controller.stage == DistractionStage.STAGE3_CENTER_INTERCEPTOR &&
                        distFromTarget < 16f && hypot(physicsState.vx, physicsState.vy) < 40f
                    ) {
                        if (behavior.emotion != PetEmotion.POUTING && behavior.emotion != PetEmotion.SURPRISED) {
                            physicsState.impulseSquash(0.4f)
                            behavior.react(PetEmotion.POUTING, 30_000L, null)
                        }
                    } else if (controller.stage == DistractionStage.RETURN_TO_MARGIN && distFromTarget < 10f) {
                        physicsState.isDockedEdge = true
                        controller.onReturnCompleted()
                    }
                } else if (!minimized) {
                    // Free flight / ballistic mode after fling or drag
                    physicsState.step(
                        dt,
                        screenW,
                        screenH,
                        petSizePx,
                        gravityEnabled = false
                    )
                }
            }
            particleEmitter.update(dt)
        }
    }

    LaunchedEffect(Unit) {
        val random = Random(0xB11)
        while (isActive) {
            delay(random.nextLong(2500L, 5501L))
            behavior.blinking = true
            try { delay(100L) } finally { behavior.blinking = false }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { hostSize = it }
            .onGloballyPositioned { coordinates.root = it }
            .pointerInput(behavior) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val change = event.changes.firstOrNull { it.pressed }
                            ?: event.changes.firstOrNull { it.previousPressed }
                        if (change != null) {
                            behavior.touch(
                                change.position,
                                (change.position - change.previousPosition).getDistance() > 2f
                            )
                        }
                    }
                }
            }
    ) {
        content()
        Canvas(Modifier.matchParentSize()) { drawPetParticles(particleEmitter.particles) }
        SpiritInteractionLayer(
            physics = physicsState,
            emitter = particleEmitter,
            behavior = behavior,
            coordinates = coordinates,
            screenWidth = hostSize.width.toFloat(),
            screenHeight = hostSize.height.toFloat(),
            petSizePx = petSizePx,
            minimized = minimized,
            setMinimized = { minimized = it },
            controller = controller,
            modifier = Modifier.matchParentSize()
        )
    }
}

@Composable
private fun SpiritInteractionLayer(
    physics: PetPhysicsState,
    emitter: ParticleEmitter,
    behavior: PetOverlayBehavior,
    coordinates: PetCoordinateSpace,
    screenWidth: Float,
    screenHeight: Float,
    petSizePx: Float,
    minimized: Boolean,
    setMinimized: (Boolean) -> Unit,
    controller: PetDistractionController,
    modifier: Modifier
) {
    if (screenWidth < petSizePx || screenHeight < petSizePx) return
    val haptics = LocalHapticFeedback.current
    val configuration = LocalViewConfiguration.current
    val density = LocalDensity.current
    val strokeThreshold = with(density) { 2.5f.dp.toPx() }
    val orbSizePx = with(density) { 28.dp.toPx() }
    val exactHitbox = remember(configuration) {
        object : ViewConfiguration by configuration {
            override val minimumTouchTargetSize: DpSize = DpSize.Zero
        }
    }

    fun reaction(emotion: PetEmotion, type: ParticleType, text: String?, duration: Long) {
        behavior.react(emotion, duration, text)
        emitter.emit(physics.x + petSizePx * .5f, physics.y + petSizePx * .25f, type, 5)
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    fun boop() {
        physics.impulseSquash(4f)
        physics.vy = -280f
        reaction(PetEmotion.HAPPY, ParticleType.SPARKLE, null, 1500L)
    }

    fun tuckAway() {
        physics.vx = 0f
        physics.vy = 0f
        physics.isDragged = false
        setMinimized(true)
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    fun restore() {
        behavior.touch(behavior.lookTarget, false)
        setMinimized(false)
    }

    CompositionLocalProvider(LocalViewConfiguration provides exactHitbox) {
        Box(modifier = modifier, contentAlignment = Alignment.TopStart) {
            if (minimized) {
                Canvas(
                    Modifier
                        .graphicsLayer {
                            translationX = if (physics.x + petSizePx / 2f < screenWidth / 2f) 12f else screenWidth - orbSizePx - 12f
                            translationY = (physics.y + (petSizePx - orbSizePx) / 2f).coerceIn(12f, screenHeight - orbSizePx - 12f)
                        }
                        .size(28.dp)
                        .semantics {
                            contentDescription = "Restore Pip"
                            role = Role.Button
                            onClick { restore(); true }
                        }
                        .pointerInput(Unit) {
                            detectTapGestures(onTap = { restore() })
                        }
                ) {
                    val radius = size.minDimension / 2f
                    drawCircle(
                        Brush.radialGradient(
                            listOf(Color(0xFFA6CBA3).copy(alpha = .45f), Color.Transparent)
                        ),
                        radius
                    )
                    drawCircle(Color(0xFFA6CBA3), radius * .55f)
                    drawCircle(Color(0xFF26362E), radius * .07f, center + Offset(-radius * .18f, 0f))
                    drawCircle(Color(0xFF26362E), radius * .07f, center + Offset(radius * .18f, 0f))
                }
            } else {
                // Interactive Pet Container with sub-pixel RenderNode translation
                Box(
                    modifier = Modifier
                        .graphicsLayer {
                            translationX = physics.x
                            translationY = physics.y
                        }
                        .size(96.dp)
                        .onGloballyPositioned { coordinates.pet = it }
                        .semantics(mergeDescendants = true) {
                            role = Role.Button
                            onClick(label = "Boop Spirit") { boop(); true }
                            onLongClick(label = "Minimize Spirit") { tuckAway(); true }
                        }
                        .pointerInput(physics, screenWidth, screenHeight, petSizePx) {
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = true)
                                val petRadius = petSizePx / 2f
                                if (hypot(down.position.x - petRadius, down.position.y - petRadius) > petRadius) return@awaitEachGesture
                                val start = coordinates.rootPosition(down.position) ?: return@awaitEachGesture
                                down.consume()
                                behavior.touch(start, false)
                                val tracker = VelocityTracker()
                                tracker.addPosition(down.uptimeMillis, start)
                                val classifier = PetGestureClassifier(start, down.uptimeMillis, petSizePx, strokeThreshold)
                                val startPet = Offset(physics.x, physics.y)
                                var dragged = false
                                var rubbed = false
                                var dizzy = false
                                physics.vx = 0f
                                physics.vy = 0f
                                physics.isDragged = true
                                physics.undock()

                                val nowMs = System.currentTimeMillis()
                                if (controller.nudgeActive && (nowMs - controller.lastNudgeTimeMs) <= 4000L) {
                                    controller.onNudgeDismissed(nowMs)
                                }

                                try {
                                    while (true) {
                                        val event = awaitPointerEvent()
                                        if (event.changes.any { it.id != down.id && it.pressed }) break
                                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                        val position = coordinates.rootPosition(change.position) ?: break
                                        behavior.touch(position, false)
                                        tracker.addPosition(change.uptimeMillis, position)
                                        val displacement = position - start
                                        when (classifier.observe(position, change.uptimeMillis)) {
                                            PetGestureSignal.DIZZY -> if (!dizzy) {
                                                dizzy = true
                                                rubbed = false
                                                dragged = true
                                                reaction(PetEmotion.DIZZY, ParticleType.SPARKLE, "Whoa...", 2500L)
                                            }
                                            PetGestureSignal.LOVE -> if (!dizzy) {
                                                if (!rubbed) {
                                                    rubbed = true
                                                    dragged = false
                                                    physics.x = startPet.x
                                                    physics.y = startPet.y
                                                    physics.impulseSquash(2f)
                                                    reaction(PetEmotion.LOVE, ParticleType.HEART, "Yay! ✨", 2000L)
                                                } else {
                                                    behavior.react(PetEmotion.LOVE, 2000L, null)
                                                }
                                            }
                                            PetGestureSignal.NONE -> Unit
                                        }
                                        if (!rubbed && (dragged ||
                                            abs(displacement.y) > configuration.touchSlop ||
                                            displacement.getDistance() > maxOf(configuration.touchSlop, petSizePx * .3f))
                                        ) {
                                            dragged = true
                                            physics.x = (startPet.x + displacement.x).coerceIn(0f, screenWidth - petSizePx)
                                            physics.y = (startPet.y + displacement.y).coerceIn(0f, screenHeight - petSizePx)
                                        }
                                        change.consume()
                                        if (!change.pressed) {
                                            physics.isDragged = false
                                            when {
                                                rubbed -> { physics.vx = 0f; physics.vy = 0f }
                                                dragged -> {
                                                    val velocity = tracker.calculateVelocity()
                                                    physics.vx = velocity.x.coerceIn(-4000f, 4000f)
                                                    physics.vy = velocity.y.coerceIn(-4000f, 4000f)
                                                    physics.impulseSquash(-2f)
                                                    if (!dizzy) behavior.react(PetEmotion.SURPRISED, 700L, null)
                                                }
                                                change.uptimeMillis - down.uptimeMillis >= 600L -> tuckAway()
                                                else -> boop()
                                            }
                                            break
                                        }
                                    }
                                } finally {
                                    physics.isDragged = false
                                }
                            }
                        }
                ) {
                    val center = Offset(physics.x + petSizePx / 2f, physics.y + petSizePx / 2f)
                    val rotation = physics.rotationDegrees
                    val radians = -rotation * kotlin.math.PI.toFloat() / 180f
                    val delta = behavior.lookTarget - center
                    val localLook = center + Offset(
                        delta.x * cos(radians) - delta.y * sin(radians),
                        delta.x * sin(radians) + delta.y * cos(radians)
                    )

                    DhyanPetCharacter(
                        emotion = behavior.emotion,
                        facialParams = behavior.emotion.toFacialParams(behavior.blinking),
                        isBlinking = behavior.blinking,
                        lookTarget = localLook,
                        petCenter = center,
                        scaleX = physics.scaleX,
                        scaleY = physics.scaleY,
                        speechBubble = behavior.speechBubble,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer { rotationZ = rotation }
                    )

                    // Stage 2: Non-intrusive Edge Peek Pill Bubble ("Drifting off?")
                    if (controller.nudgeActive && controller.stage == DistractionStage.STAGE2_EDGE_PEEK_KNOCK) {
                        Surface(
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .offset(y = (-52).dp)
                                .widthIn(min = 160.dp, max = 210.dp),
                            shape = RoundedCornerShape(16.dp),
                            color = Color(0xFF1E2229),
                            shadowElevation = 6.dp
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "Drifting off?",
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                    color = Color.White,
                                    modifier = Modifier.weight(1f)
                                )
                                Button(
                                    onClick = {
                                        controller.onNudgeAccepted()
                                        reaction(PetEmotion.HAPPY, ParticleType.SPARKLE, "Back on track! 🌿", 2000L)
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFF7AD656),
                                        contentColor = Color(0xFF121417)
                                    ),
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                    modifier = Modifier.height(26.dp)
                                ) {
                                    Text("Back to Task", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                                TextButton(
                                    onClick = { controller.onNudgeDismissed() },
                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                                    modifier = Modifier.height(26.dp)
                                ) {
                                    Text("✕", fontSize = 12.sp, color = Color(0xFFA0AEC0))
                                }
                            }
                        }
                    }

                    // Stage 3: Active Center Interceptor Card ("Refocus Now")
                    if (controller.nudgeActive && controller.stage == DistractionStage.STAGE3_CENTER_INTERCEPTOR) {
                        Surface(
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .offset(y = (-66).dp)
                                .widthIn(min = 190.dp, max = 250.dp),
                            shape = RoundedCornerShape(18.dp),
                            color = Color(0xFF121417),
                            shadowElevation = 10.dp,
                            border = BorderStroke(1.dp, Color(0xFF7AD656).copy(alpha = 0.4f))
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "Take a breath. Refocus?",
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = Color.White,
                                    textAlign = TextAlign.Center
                                )
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Button(
                                        onClick = {
                                            controller.onNudgeAccepted()
                                            reaction(PetEmotion.HAPPY, ParticleType.SPARKLE, "Let's focus! ✨", 2000L)
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Color(0xFF7AD656),
                                            contentColor = Color(0xFF121417)
                                        ),
                                        shape = RoundedCornerShape(12.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp),
                                        modifier = Modifier.height(30.dp)
                                    ) {
                                        Text("Refocus Now", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                    OutlinedButton(
                                        onClick = { controller.onNudgeDismissed() },
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            contentColor = Color(0xFFE2E8F0)
                                        ),
                                        border = BorderStroke(1.dp, Color(0xFF4A5568)),
                                        shape = RoundedCornerShape(12.dp),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                        modifier = Modifier.height(30.dp)
                                    ) {
                                        Text("✕", fontSize = 11.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private class PetCoordinateSpace {
    var root: LayoutCoordinates? = null
    var pet: LayoutCoordinates? = null
    fun rootPosition(local: Offset): Offset? {
        val host = root ?: return null
        val child = pet ?: return null
        if (!host.isAttached || !child.isAttached) return null
        return host.localPositionOf(child, local).takeIf { it.x.isFinite() && it.y.isFinite() }
    }
}

/** Explicit frame-clock time; emotions are presentation, never focus measurements. */
@Stable
internal class PetOverlayBehavior {
    var emotion by mutableStateOf(PetEmotion.CURIOUS)
        private set
    var lookTarget by mutableStateOf(Offset.Zero)
        private set
    var speechBubble by mutableStateOf<PetSpeechBubble?>(null)
        private set
    var blinking by mutableStateOf(false)
    private var nowMs = 0L
    private var lastTouchMs = 0L
    private var focusedUntilMs = 0L
    private var reaction: PetEmotion? = null
    private var reactionUntilMs = 0L
    private var speechUntilMs = 0L

    fun touch(position: Offset, moving: Boolean) {
        if (!position.x.isFinite() || !position.y.isFinite()) return
        lookTarget = position
        lastTouchMs = nowMs
        if (moving) focusedUntilMs = nowMs + 1200L
    }

    fun react(value: PetEmotion, durationMs: Long, text: String?) {
        reaction = value
        reactionUntilMs = nowMs + durationMs.coerceIn(0L, 10_000L)
        lastTouchMs = nowMs
        if (text != null && speechBubble?.text != text) {
            speechBubble = PetSpeechBubble(text)
            speechUntilMs = nowMs + 2500L
        }
        emotion = value
    }

    fun advance(timeMs: Long, taskActive: Boolean, dragging: Boolean) {
        nowMs = timeMs.coerceAtLeast(nowMs)
        if (nowMs >= reactionUntilMs) reaction = null
        if (nowMs >= speechUntilMs) speechBubble = null
        emotion = when {
            reaction == PetEmotion.DIZZY -> PetEmotion.DIZZY
            dragging && reaction == PetEmotion.LOVE -> PetEmotion.LOVE
            dragging -> PetEmotion.SURPRISED
            reaction != null -> reaction!!
            taskActive -> PetEmotion.FOCUSED
            nowMs - lastTouchMs >= 18_000L -> PetEmotion.SLEEPY
            nowMs < focusedUntilMs -> PetEmotion.FOCUSED
            else -> PetEmotion.CURIOUS
        }
    }
}

internal enum class PetGestureSignal { NONE, LOVE, DIZZY }

/** Head-rub intent is bounded; vigorous travel has priority over a gentle rub. */
internal class PetGestureClassifier(
    start: Offset,
    startMs: Long,
    private val petSizePx: Float,
    private val directionThresholdPx: Float
) {
    private var previous = start
    private var anchor = start
    private var lastMs = startMs
    private var windowMs = startMs
    private var direction = 0
    private var accumulatedX = 0f
    private var reversals = 0
    private var horizontalTravel = 0f
    private var verticalTravel = 0f

    fun observe(position: Offset, timeMs: Long): PetGestureSignal {
        if (!position.x.isFinite() || !position.y.isFinite() ||
            !petSizePx.isFinite() || petSizePx <= 0f
        ) return PetGestureSignal.NONE
        if (timeMs < lastMs || timeMs - windowMs > 900L) {
            anchor = position
            previous = position
            windowMs = timeMs
            direction = 0
            accumulatedX = 0f
            reversals = 0
            horizontalTravel = 0f
            verticalTravel = 0f
        }
        lastMs = timeMs
        val delta = position - previous
        previous = position
        horizontalTravel += abs(delta.x)
        verticalTravel += abs(delta.y)
        accumulatedX += delta.x
        if (abs(accumulatedX) >= directionThresholdPx.coerceAtLeast(.5f)) {
            val next = if (accumulatedX > 0f) 1 else -1
            if (direction != 0 && next != direction) reversals++
            direction = next
            accumulatedX = 0f
        }
        return when {
            reversals >= 5 && horizontalTravel + verticalTravel >= petSizePx * 4f ->
                PetGestureSignal.DIZZY
            reversals >= 3 && horizontalTravel >= petSizePx * .6f &&
                verticalTravel <= petSizePx * .3f &&
                hypot(position.x - anchor.x, position.y - anchor.y) <= petSizePx * .65f ->
                PetGestureSignal.LOVE
            else -> PetGestureSignal.NONE
        }
    }
}

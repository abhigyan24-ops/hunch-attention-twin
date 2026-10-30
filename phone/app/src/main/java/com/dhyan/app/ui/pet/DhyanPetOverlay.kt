package com.dhyan.app.ui.pet

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
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
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.ViewConfiguration
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/**
 * In-app host, with a passive Initial-pass observer and a 96.dp pet gesture box.
 * Hold and release the pet to tuck it into a 28.dp edge orb; tap the orb to restore.
 *
 * MainActivity integration (inside setContent):
 *     DhyanTheme { DhyanPetOverlay { DhyanMainScreen() } }
 *
 * For task-aware presentation, collect the existing SettingsViewModel
 * activeDeclaredSession in DhyanMainScreen and pass (activeTask != null) while
 * wrapping its Scaffold. Simulator personas are not active tasks or meditation.
 * This file does not alter MainActivity or create a timer/session source.
 *
 * Observation applies to this Compose subtree. Native dialogs have their own
 * window and retain priority over this in-app pet.
 */
@Composable
fun DhyanPetOverlay(
    isTaskActive: Boolean = false,
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
    val direction = LocalLayoutDirection.current
    val petSizePx by rememberUpdatedState(with(density) { 96.dp.toPx() })
    val safe = WindowInsets.safeDrawing
    // Keep the pet clear of Dhyan's default 64.dp header and 80.dp navigation.
    val left = safe.getLeft(density, direction).toFloat().coerceAtMost(hostSize.width.toFloat())
    val right = (hostSize.width - safe.getRight(density, direction)).toFloat().coerceAtLeast(left)
    val top = (safe.getTop(density) + with(density) { 64.dp.roundToPx() })
        .toFloat().coerceAtMost(hostSize.height.toFloat())
    val bottom = (hostSize.height - safe.getBottom(density) - with(density) { 80.dp.roundToPx() })
        .toFloat().coerceAtLeast(top)
    val viewport by rememberUpdatedState(Rect(left, top, right, bottom))

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
            if (viewport.width >= petSizePx && viewport.height >= petSizePx) {
                if (!positioned) {
                    physicsState.x = (viewport.width - petSizePx - 12f).coerceAtLeast(0f)
                    physicsState.y = (viewport.height * .35f).coerceAtMost(viewport.height - petSizePx)
                    positioned = true
                }
                if (minimized) {
                    physicsState.vx = 0f
                    physicsState.vy = 0f
                }
                physicsState.step(dt, viewport.width, viewport.height, petSizePx,
                    gravityEnabled = !taskActive && !minimized)
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

    Box(modifier.fillMaxSize()
        .onSizeChanged { hostSize = it }
        .onGloballyPositioned { coordinates.root = it }
        .pointerInput(behavior) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes.firstOrNull { it.pressed }
                        ?: event.changes.firstOrNull { it.previousPressed }
                    if (change != null) {
                        behavior.touch(change.position,
                            (change.position - change.previousPosition).getDistance() > 2f)
                    }
                    // Observer only: NEVER consume down, movement, or up here.
                }
            }
        }) {
        content()
        Canvas(Modifier.matchParentSize()) { drawPetParticles(particleEmitter.particles) }
        SpiritInteractionLayer(
            physicsState, particleEmitter, behavior, coordinates, viewport, petSizePx,
            minimized, { minimized = it }, Modifier.matchParentSize()
        )
    }
}

@Composable
private fun SpiritInteractionLayer(
    physics: PetPhysicsState,
    emitter: ParticleEmitter,
    behavior: PetOverlayBehavior,
    coordinates: PetCoordinateSpace,
    viewport: Rect,
    petSizePx: Float,
    minimized: Boolean,
    setMinimized: (Boolean) -> Unit,
    modifier: Modifier
) {
    if (viewport.width < petSizePx || viewport.height < petSizePx) return
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
        emitter.emit(viewport.left + physics.x + petSizePx * .5f,
            viewport.top + physics.y + petSizePx * .25f, type, 5)
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

    // Only the pet subtree uses exact hit bounds; app accessibility defaults remain intact.
    CompositionLocalProvider(LocalViewConfiguration provides exactHitbox) {
        Box(modifier, contentAlignment = AbsoluteAlignment.TopLeft) {
            if (minimized) {
                Canvas(Modifier.absoluteOffset {
                    val x = if (physics.x + petSizePx / 2f < viewport.width / 2f)
                        viewport.left else viewport.right - orbSizePx
                    val y = (viewport.top + physics.y + (petSizePx - orbSizePx) / 2f)
                        .coerceIn(viewport.top, viewport.bottom - orbSizePx)
                    IntOffset(x.roundToInt(), y.roundToInt())
                }.size(28.dp).semantics {
                    contentDescription = "Restore Dhyan Spirit"
                    role = Role.Button
                    onClick { restore(); true }
                }.pointerInput(Unit) {
                    detectTapGestures(onTap = { restore() })
                }) {
                    val radius = size.minDimension / 2f
                    drawCircle(Brush.radialGradient(
                        listOf(Color(0xFFA6CBA3).copy(alpha = .45f), Color.Transparent)),
                        radius)
                    drawCircle(Color(0xFFA6CBA3), radius * .55f)
                    drawCircle(Color(0xFF26362E), radius * .07f,
                        center + Offset(-radius * .18f, 0f))
                    drawCircle(Color(0xFF26362E), radius * .07f,
                        center + Offset(radius * .18f, 0f))
                }
            } else {
                Box(Modifier.absoluteOffset {
                    IntOffset((viewport.left + physics.x).roundToInt(),
                        (viewport.top + physics.y).roundToInt())
                }.size(96.dp).onGloballyPositioned { coordinates.pet = it }
                    .semantics(mergeDescendants = true) {
                        role = Role.Button
                        onClick(label = "Boop Spirit") { boop(); true }
                        onLongClick(label = "Minimize Spirit") { tuckAway(); true }
                    }.pointerInput(physics, viewport, petSizePx) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = true)
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
                            try {
                                while (true) {
                                    val event = awaitPointerEvent()
                                    // A second pointer cancels this pet gesture without taking that pointer.
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
                                                // Sustain affection without repeating haptics every move.
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
                                        physics.x = (startPet.x + displacement.x)
                                            .coerceIn(0f, viewport.width - petSizePx)
                                        physics.y = (startPet.y + displacement.y)
                                            .coerceIn(0f, viewport.height - petSizePx)
                                    }
                                    // Captured pet pointer only, owned by the original 96.dp hit.
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
                    }) {
                    val center = Offset(viewport.left + physics.x + petSizePx / 2f,
                        viewport.top + physics.y + petSizePx / 2f)
                    val rotation = physics.rotationDegrees
                    val radians = -rotation * kotlin.math.PI.toFloat() / 180f
                    val delta = behavior.lookTarget - center
                    val localLook = center + Offset(
                        delta.x * cos(radians) - delta.y * sin(radians),
                        delta.x * sin(radians) + delta.y * cos(radians))
                    DhyanPetCharacter(
                        emotion = behavior.emotion,
                        facialParams = behavior.emotion.toFacialParams(behavior.blinking),
                        isBlinking = behavior.blinking,
                        lookTarget = localLook,
                        petCenter = center,
                        scaleX = physics.scaleX,
                        scaleY = physics.scaleY,
                        speechBubble = behavior.speechBubble,
                        modifier = Modifier.fillMaxSize().graphicsLayer { rotationZ = rotation }
                    )
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

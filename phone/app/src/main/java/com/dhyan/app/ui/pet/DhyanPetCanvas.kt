package com.dhyan.app.ui.pet

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

/** Pure Compose artwork. Global lookTarget and petCenter must share a coordinate space. */
@Composable
fun DhyanPetCharacter(
    emotion: PetEmotion,
    facialParams: PetFacialParams,
    isBlinking: Boolean,
    lookTarget: Offset,
    petCenter: Offset,
    scaleX: Float,
    scaleY: Float,
    speechBubble: PetSpeechBubble?,
    modifier: Modifier = Modifier
) {
    val preview = LocalInspectionMode.current
    val emitter = remember { ParticleEmitter() }
    val random = remember { Random(0x5ACC) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var saccade by remember { mutableStateOf(Offset.Zero) }
    var phase by remember { mutableFloatStateOf(0f) }
    var bubbleContent by remember { mutableStateOf(speechBubble) }
    var bubbleVisible by remember { mutableStateOf(speechBubble != null) }
    val measurer = rememberTextMeasurer()

    LaunchedEffect(lookTarget, emotion, preview) {
        saccade = Offset.Zero
        if (preview || emotion == PetEmotion.MEDITATING || emotion == PetEmotion.SLEEPY) return@LaunchedEffect
        while (isActive) {
            delay(random.nextLong(1500L, 3501L))
            saccade = Offset((random.nextFloat() - .5f) * .24f, (random.nextFloat() - .5f) * .18f)
        }
    }
    LaunchedEffect(speechBubble) {
        bubbleVisible = speechBubble != null
        if (speechBubble != null) {
            bubbleContent = speechBubble
            if (!preview) {
                delay(speechBubble.durationMs.coerceAtLeast(0L))
                bubbleVisible = false
            }
        }
    }
    LaunchedEffect(emotion, canvasSize, preview) {
        val type = when (emotion) {
            PetEmotion.LOVE -> ParticleType.HEART
            PetEmotion.HAPPY, PetEmotion.PROUD -> ParticleType.SPARKLE
            PetEmotion.SLEEPY -> ParticleType.ZZZ
            PetEmotion.MEDITATING -> ParticleType.BREATH_RING
            else -> null
        } ?: return@LaunchedEffect
        if (canvasSize.width <= 0 || canvasSize.height <= 0) return@LaunchedEffect
        do {
            emitter.emit(canvasSize.width * .5f, canvasSize.height * .50f, type,
                if (type == ParticleType.BREATH_RING) 1 else 3)
            if (preview) break
            delay(if (type == ParticleType.BREATH_RING) 2200L else 1400L)
        } while (isActive)
    }
    LaunchedEffect(emotion, preview) {
        if (preview) return@LaunchedEffect
        var previous = 0L
        while (isActive) {
            val animated = emotion == PetEmotion.DIZZY || emotion == PetEmotion.MEDITATING ||
                emotion == PetEmotion.SLEEPY
            if (!animated && emitter.particles.isEmpty()) {
                previous = 0L
                delay(100)
                continue
            }
            val frame = withFrameNanos { it }
            if (previous != 0L) {
                val dt = ((frame - previous) / 1_000_000_000f).coerceIn(0f, .033f)
                emitter.update(dt)
                if (animated) phase = (phase + dt) % 1000f
            }
            previous = frame
        }
    }

    val dx = (lookTarget.x - petCenter.x).finiteOrDefault(0f)
    val dy = (lookTarget.y - petCenter.y).finiteOrDefault(0f)
    val angle = atan2(dy, dx)
    val distance = (hypot(dx, dy) / 180f).coerceIn(0f, 1f)
    val gazeX by animateFloatAsState((cos(angle) * distance + saccade.x).coerceIn(-1f, 1f),
        spring(dampingRatio = .85f, stiffness = Spring.StiffnessLow), label = "pet gaze x")
    val gazeY by animateFloatAsState((sin(angle) * distance + saccade.y).coerceIn(-1f, 1f),
        spring(dampingRatio = .85f, stiffness = Spring.StiffnessLow), label = "pet gaze y")
    val eyeOpen by animateFloatAsState(if (isBlinking) 0f else facialParams.eyeOpen,
        spring(stiffness = Spring.StiffnessMedium), label = "pet eyelids")
    val slant by animateFloatAsState(facialParams.eyeSlant, spring(), label = "pet eye slant")
    val pupil by animateFloatAsState(facialParams.pupilScale, spring(), label = "pet pupils")
    val mouth by animateFloatAsState(facialParams.mouthCurve, spring(), label = "pet smile")
    val blush by animateFloatAsState(facialParams.blushAlpha, spring(), label = "pet blush")
    val aura by animateFloatAsState(facialParams.auraIntensity, spring(), label = "pet aura")
    val color by animateColorAsState(facialParams.primaryColor, spring(), label = "pet body")
    val arcs by animateFloatAsState(
        if (isBlinking || emotion == PetEmotion.HAPPY || emotion == PetEmotion.LOVE ||
            emotion == PetEmotion.MEDITATING) 1f else 0f,
        spring(stiffness = Spring.StiffnessMedium), label = "pet expression"
    )
    val bubbleAlpha by animateFloatAsState(if (bubbleVisible) 1f else 0f, tween(180), label = "pet speech alpha")
    val bubbleScale by animateFloatAsState(if (bubbleVisible) 1f else .85f,
        spring(dampingRatio = .8f), label = "pet speech scale")
    val ink = Color(0xFF182127)

    Canvas(modifier.size(96.dp).onSizeChanged { canvasSize = it }.semantics {
        contentDescription = "Dhyan Spirit, ${emotion.name.lowercase()}" +
            if (bubbleVisible) ". ${bubbleContent?.text.orEmpty()}" else ""
    }) {
        if (size.minDimension <= 0f) return@Canvas
        val r = size.minDimension * .29f
        val center = Offset(size.width * .5f, size.height * .64f)
        val breathing = if (emotion == PetEmotion.MEDITATING || emotion == PetEmotion.SLEEPY)
            sin(phase * (PI.toFloat() / 2f)) else 0f
        val glowRadius = r * (1.7f + breathing * .08f)
        drawCircle(Brush.radialGradient(
            listOf(color.copy(alpha = aura.coerceIn(0f, 1f) * .38f), Color.Transparent),
            center, glowRadius), glowRadius, center)
        drawOval(Color.Black.copy(alpha = .14f),
            Offset(center.x - r * .8f, center.y + r * .85f), Size(r * 1.6f, r * .22f))

        scale(scaleX.finiteOrDefault(1f).coerceIn(.5f, 1.6f),
            scaleY.finiteOrDefault(1f).coerceIn(.5f, 1.6f), center) {
            // A continuous multi-puff outline: no overlapping circle seams or bitmap assets.
            val body = Path().apply {
                moveTo(center.x - r * .95f, center.y + r * .32f)
                cubicTo(center.x - r * 1.30f, center.y + r * .25f,
                    center.x - r * 1.30f, center.y - r * .30f, center.x - r * .78f, center.y - r * .36f)
                cubicTo(center.x - r * .90f, center.y - r * .80f,
                    center.x - r * .37f, center.y - r * 1.10f, center.x - r * .08f, center.y - r * .72f)
                cubicTo(center.x + r * .20f, center.y - r * 1.27f,
                    center.x + r * .83f, center.y - r * 1.02f, center.x + r * .78f, center.y - r * .50f)
                cubicTo(center.x + r * 1.30f, center.y - r * .58f,
                    center.x + r * 1.50f, center.y + r * .12f, center.x + r * 1.04f, center.y + r * .40f)
                cubicTo(center.x + r * .75f, center.y + r * .82f,
                    center.x - r * .80f, center.y + r * .83f, center.x - r * .95f, center.y + r * .32f)
                close()
            }
            drawPath(body, Brush.radialGradient(
                listOf(androidx.compose.ui.graphics.lerp(color, Color.White, .78f), color,
                    androidx.compose.ui.graphics.lerp(color, Color(0xFF477D80), .23f)),
                center + Offset(-r * .45f, -r * .65f), r * 2.2f))
            clipPath(body) {
                drawPath(body, Color.White.copy(alpha = .60f), style = Stroke(r * .12f))
                drawOval(Brush.radialGradient(listOf(Color.White.copy(alpha = .55f), Color.Transparent),
                    center + Offset(-r * .4f, -r * .55f), r * .8f),
                    center + Offset(-r, -r), Size(r * 1.5f, r * 1.1f))
            }
            val sway = (scaleX.finiteOrDefault(1f) - 1f) * r * .25f + gazeX * r * .08f
            val stem = Path().apply {
                moveTo(center.x, center.y - r * .93f)
                quadraticTo(center.x + sway, center.y - r * 1.2f,
                    center.x + sway, center.y - r * 1.38f)
            }
            drawPath(stem, Color(0xFF597D57), style = Stroke(r * .065f, cap = StrokeCap.Round))
            val leaf = Path().apply {
                moveTo(center.x + sway, center.y - r * 1.18f)
                quadraticTo(center.x + sway - r * .38f, center.y - r * 1.65f,
                    center.x + sway - r * .5f, center.y - r * 1.30f)
                quadraticTo(center.x + sway - r * .3f, center.y - r * .99f,
                    center.x + sway, center.y - r * 1.18f)
                quadraticTo(center.x + sway + r * .33f, center.y - r * 1.70f,
                    center.x + sway + r * .46f, center.y - r * 1.4f)
                quadraticTo(center.x + sway + r * .38f, center.y - r * 1.1f,
                    center.x + sway, center.y - r * 1.18f)
                close()
            }
            drawPath(leaf, Color(0xFF8BAF78))
            if (emotion == PetEmotion.CURIOUS || emotion == PetEmotion.FOCUSED) {
                drawRoundRect(Color.White.copy(alpha = .40f),
                    center + Offset(-r * .70f, -r * .43f), Size(r * 1.40f, r * .70f),
                    CornerRadius(r * .32f))
            }
            repeat(2) { index ->
                val side = if (index == 0) -1f else 1f
                drawCircle(Color(0xFFE48CA6).copy(alpha = blush.coerceIn(0f, 1f) * .8f),
                    r * .14f, center + Offset(side * r * .58f, r * .17f))
                drawSpiritEye(center + Offset(side * r * .36f, -r * .08f), r,
                    eyeOpen, slant, pupil, arcs.coerceIn(0f, 1f), gazeX, gazeY,
                    emotion == PetEmotion.DIZZY, phase * side, ink)
            }
            val mouthY = center.y + r * .26f
            when (emotion) {
                PetEmotion.SURPRISED -> drawOval(ink,
                    Offset(center.x - r * .075f, mouthY - r * .035f), Size(r * .15f, r * .19f))
                PetEmotion.DIZZY -> {
                    val wave = Path().apply {
                        moveTo(center.x - r * .2f, mouthY)
                        quadraticTo(center.x - r * .1f, mouthY - r * .13f, center.x, mouthY)
                        quadraticTo(center.x + r * .1f, mouthY + r * .13f, center.x + r * .2f, mouthY)
                    }
                    drawPath(wave, ink, style = Stroke(r * .055f, cap = StrokeCap.Round))
                }
                else -> {
                    val smile = Path().apply {
                        moveTo(center.x - r * .19f, mouthY)
                        quadraticTo(center.x, mouthY + mouth.coerceIn(-1f, 1f) * r * .30f,
                            center.x + r * .19f, mouthY)
                    }
                    drawPath(smile, ink, style = Stroke(r * .055f, cap = StrokeCap.Round))
                }
            }
        }
        drawPetParticles(emitter.particles)

        val text = bubbleContent?.text
        if (!text.isNullOrBlank() && bubbleAlpha > .001f) {
            val padX = 8.dp.toPx()
            val padY = 4.dp.toPx()
            val layout = measurer.measure(text,
                style = TextStyle(color = ink, fontSize = 11.sp, fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center),
                overflow = TextOverflow.Ellipsis, maxLines = 2,
                constraints = Constraints(maxWidth = (size.width * .95f - padX * 2f).toInt().coerceAtLeast(1)))
            val width = (layout.size.width + padX * 2f).coerceAtMost(size.width)
            val height = layout.size.height + padY * 2f
            val topLeft = Offset((size.width - width) / 2f, 0f)
            scale(bubbleScale, bubbleScale, topLeft + Offset(width / 2f, height / 2f)) {
                drawRoundRect(Color(0xFFF5F7F0).copy(alpha = bubbleAlpha), topLeft,
                    Size(width, height), CornerRadius(height / 2f))
                drawText(layout, color = ink.copy(alpha = bubbleAlpha), topLeft = topLeft + Offset(padX, padY))
            }
        }
    }
}

private fun DrawScope.drawSpiritEye(
    center: Offset, r: Float, openness: Float, slant: Float, pupilScale: Float, arcs: Float,
    gazeX: Float, gazeY: Float, dizzy: Boolean, phase: Float, ink: Color
) {
    val width = r * .44f
    val height = r * .61f * openness.coerceIn(.03f, 1.4f)
    val rect = Rect(center.x - width / 2f, center.y - height / 2f,
        center.x + width / 2f, center.y + height / 2f)
    val eyeAlpha = (1f - arcs) * (openness / .25f).coerceIn(0f, 1f)
    if (eyeAlpha > .001f) {
        drawOval(Color.White.copy(alpha = eyeAlpha), rect.topLeft, rect.size)
        clipPath(Path().apply { addOval(rect) }) {
            val gaze = Offset(gazeX * width * .14f, gazeY * height * .12f)
            if (dizzy) {
                val spiral = Path()
                repeat(49) { index ->
                    val t = index / 48f
                    val angle = t * PI.toFloat() * 4f + phase * 5f
                    val point = center + gaze + Offset(cos(angle), sin(angle)) * (width * .4f * t)
                    if (index == 0) spiral.moveTo(point.x, point.y) else spiral.lineTo(point.x, point.y)
                }
                drawPath(spiral, ink.copy(alpha = eyeAlpha), style = Stroke(r * .045f, cap = StrokeCap.Round))
            } else {
                val pupilWidth = width * .7f * pupilScale.coerceIn(.2f, 1.4f)
                val pupilHeight = height * .85f * pupilScale.coerceIn(.2f, 1.4f)
                val pupilCenter = center + gaze
                drawOval(ink.copy(alpha = eyeAlpha), pupilCenter - Offset(pupilWidth / 2f, pupilHeight / 2f),
                    Size(pupilWidth, pupilHeight))
                drawCircle(Color.White.copy(alpha = eyeAlpha), pupilWidth * .18f,
                    pupilCenter + Offset(-pupilWidth * .2f, -pupilHeight * .23f))
                drawCircle(Color.White.copy(alpha = eyeAlpha * .9f), pupilWidth * .085f,
                    pupilCenter + Offset(pupilWidth * .22f, pupilHeight * .22f))
            }
        }
    }
    if (arcs > .001f) {
        val curve = Path().apply {
            moveTo(center.x - width * .55f, center.y)
            quadraticTo(center.x, center.y + slant.coerceIn(-1f, 1f) * r * .28f,
                center.x + width * .55f, center.y)
        }
        drawPath(curve, ink.copy(alpha = arcs), style = Stroke(r * .065f, cap = StrokeCap.Round))
    }
}

private fun Float.finiteOrDefault(default: Float): Float = if (isFinite()) this else default

@Composable
private fun SpiritPreview(emotion: PetEmotion, text: String? = null) {
    DhyanPetCharacter(emotion, emotion.toFacialParams(false), false,
        Offset(140f, 20f), Offset(80f, 90f), 1f, 1f,
        text?.let { PetSpeechBubble(it) }, Modifier.size(160.dp))
}

@Preview(name = "Curious", showBackground = true, backgroundColor = 0xFF0F172A)
@Composable private fun CuriousPreview() = SpiritPreview(PetEmotion.CURIOUS)

@Preview(name = "Happy", showBackground = true, backgroundColor = 0xFF0F172A)
@Composable private fun HappyPreview() = SpiritPreview(PetEmotion.HAPPY, "Hello!")

@Preview(name = "Love", showBackground = true, backgroundColor = 0xFF0F172A)
@Composable private fun LovePreview() = SpiritPreview(PetEmotion.LOVE, "Thank you")

@Preview(name = "Meditating", showBackground = true, backgroundColor = 0xFF0F172A)
@Composable private fun MeditatingPreview() = SpiritPreview(PetEmotion.MEDITATING, "Quiet moment")

@Preview(name = "Sleepy", showBackground = true, backgroundColor = 0xFF0F172A)
@Composable private fun SleepyPreview() = SpiritPreview(PetEmotion.SLEEPY, "Zzz...")

@Preview(name = "Dizzy", showBackground = true, backgroundColor = 0xFF0F172A)
@Composable private fun DizzyPreview() = SpiritPreview(PetEmotion.DIZZY, "Whoa...")

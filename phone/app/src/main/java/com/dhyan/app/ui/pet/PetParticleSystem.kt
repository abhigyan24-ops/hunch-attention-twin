package com.dhyan.app.ui.pet

import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlin.math.exp
import kotlin.random.Random

enum class ParticleType { HEART, SPARKLE, ZZZ, BREATH_RING }

data class PetParticle(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    var alpha: Float = 1f,
    var scale: Float = 1f,
    val type: ParticleType
)

/** Composition-thread, cosmetic simulation. Seeded and bounded; no I/O or content. */
@Stable
class ParticleEmitter(seed: Int = 0xD4A) {
    val particles = mutableStateListOf<PetParticle>()
    private val random = Random(seed)

    fun emit(originX: Float, originY: Float, type: ParticleType, count: Int = 4) {
        if (!originX.isFinite() || !originY.isFinite()) return
        repeat(count.coerceIn(0, 32)) {
            if (particles.size >= 128) particles.removeAt(0)
            val ring = type == ParticleType.BREATH_RING
            particles.add(PetParticle(
                x = originX + if (ring) 0f else (random.nextFloat() - .5f) * 20f,
                y = originY,
                vx = if (ring) 0f else (random.nextFloat() - .5f) * 24f,
                vy = if (ring) 0f else -16f - random.nextFloat() * 26f,
                alpha = if (ring) .8f else 1f,
                scale = if (ring) .6f else .65f + random.nextFloat() * .45f,
                type = type
            ))
        }
    }

    fun update(dt: Float) {
        if (!dt.isFinite() || dt <= 0f) return
        for (index in particles.lastIndex downTo 0) {
            val old = particles[index]
            val alpha = old.alpha - dt * if (old.type == ParticleType.BREATH_RING) .45f else .65f
            val next = old.copy(
                x = old.x + old.vx * dt,
                y = old.y + old.vy * dt,
                vx = old.vx * exp(-.5f * dt),
                vy = if (old.type == ParticleType.BREATH_RING) 0f else old.vy - 4f * dt,
                alpha = alpha,
                scale = old.scale + dt * if (old.type == ParticleType.BREATH_RING) .8f else .1f
            )
            if (alpha <= 0f || !next.x.isFinite() || !next.y.isFinite() ||
                !next.scale.isFinite() || next.scale <= 0f || !alpha.isFinite()
            ) particles.removeAt(index)
            // Replacement is intentional: mutating plain data-class fields would not invalidate Canvas.
            else particles[index] = next
        }
    }
}

/** All glyphs are geometry, including Z letters; no fonts, bitmaps or Android Paint. */
fun DrawScope.drawPetParticles(particles: List<PetParticle>) {
    particles.forEach { particle ->
        if (!particle.x.isFinite() || !particle.y.isFinite() ||
            !particle.scale.isFinite() || particle.scale <= 0f || !particle.alpha.isFinite()
        ) return@forEach
        val x = particle.x
        val y = particle.y
        val r = 5.dp.toPx() * particle.scale.coerceAtMost(6f)
        val alpha = particle.alpha.coerceIn(0f, 1f)
        when (particle.type) {
            ParticleType.HEART -> {
                val heart = Path().apply {
                    moveTo(x, y + r)
                    cubicTo(x - r * 2f, y - r * .2f, x - r, y - r * 1.8f, x, y - r * .55f)
                    cubicTo(x + r, y - r * 1.8f, x + r * 2f, y - r * .2f, x, y + r)
                    close()
                }
                drawPath(heart, Color(0xFFE982A8).copy(alpha = alpha))
            }
            ParticleType.SPARKLE -> {
                val star = Path().apply {
                    moveTo(x, y - r * 1.5f)
                    lineTo(x + r * .3f, y - r * .3f)
                    lineTo(x + r * 1.5f, y)
                    lineTo(x + r * .3f, y + r * .3f)
                    lineTo(x, y + r * 1.5f)
                    lineTo(x - r * .3f, y + r * .3f)
                    lineTo(x - r * 1.5f, y)
                    lineTo(x - r * .3f, y - r * .3f)
                    close()
                }
                drawPath(star, Color(0xFFFFD990).copy(alpha = alpha))
            }
            ParticleType.ZZZ -> repeat(3) { index ->
                val left = x + index * r * .9f
                val top = y - index * r * .8f
                val width = r * (.65f + index * .15f)
                val z = Path().apply {
                    moveTo(left, top)
                    lineTo(left + width, top)
                    lineTo(left, top + width)
                    lineTo(left + width, top + width)
                }
                drawPath(z, Color(0xFFB7CBE2).copy(alpha = alpha),
                    style = Stroke(1.3f.dp.toPx(), cap = StrokeCap.Round))
            }
            ParticleType.BREATH_RING -> {
                val color = Color(0xFFCCD8F2).copy(alpha = alpha)
                drawCircle(color, r * 3f, Offset(x, y), style = Stroke(1.dp.toPx()))
                drawCircle(color.copy(alpha = alpha * .45f), r * 2.5f, Offset(x, y),
                    style = Stroke(.7f.dp.toPx()))
            }
        }
    }
}

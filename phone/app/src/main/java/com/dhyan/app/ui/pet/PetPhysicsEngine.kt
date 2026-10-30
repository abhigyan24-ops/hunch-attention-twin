package com.dhyan.app.ui.pet

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.pow

/**
 * Presentation-only physics. Positions are the pet's top-left corner in pixels;
 * velocities are px/s. Feed frame delta seconds from withFrameNanos into [step].
 * Integration uses at most 1/120 s per substep, not a promise of 120 FPS rendering.
 */
@Stable
class PetPhysicsState(initialX: Float = 500f, initialY: Float = 1000f) {
    var x by mutableFloatStateOf(initialX.finiteOrZero())
    var y by mutableFloatStateOf(initialY.finiteOrZero())
    var vx by mutableFloatStateOf(0f)
    var vy by mutableFloatStateOf(0f)
    var isDragged by mutableStateOf(false)
    var isDockedEdge by mutableStateOf(false)
    var isOnFloor by mutableStateOf(false)
        private set
    var deformation by mutableFloatStateOf(0f)
        private set
    var deformationVel by mutableFloatStateOf(0f)
        private set

    val scaleX: Float get() = 1f + deformation
    val scaleY: Float get() = 1f / scaleX
    private var dockSide = 0
    val rotationDegrees: Float
        get() = if (isDockedEdge) -dockSide * 18f else (vx * .015f).coerceIn(-25f, 25f)

    /** Adds spring velocity. Positive values flatten; negative values stretch. */
    fun impulseSquash(amount: Float) {
        if (amount.isFinite()) deformationVel = (deformationVel + amount).coerceIn(-12f, 12f)
    }

    /** Immediately releases edge magnetism so autonomous movement or dragging is never trapped. */
    fun undock() {
        isDockedEdge = false
        dockSide = 0
    }

    fun step(
        dtSeconds: Float,
        boundsWidth: Float,
        boundsHeight: Float,
        petSizePx: Float,
        gravityEnabled: Boolean
    ) {
        if (!dtSeconds.isFinite() || dtSeconds <= 0f ||
            !boundsWidth.isFinite() || !boundsHeight.isFinite() || !petSizePx.isFinite() ||
            boundsWidth <= 0f || boundsHeight <= 0f || petSizePx < 0f
        ) return

        val availableX = (boundsWidth - petSizePx).coerceAtLeast(0f)
        val availableY = (boundsHeight - petSizePx).coerceAtLeast(0f)
        val minX = min(12f, availableX / 2f)
        val maxX = availableX - minX
        val minY = min(12f, availableY / 2f)
        val maxY = availableY - minY
        x = x.finiteOrZero()
        y = y.finiteOrZero()
        vx = vx.finiteOrZero()
        vy = vy.finiteOrZero()

        var remaining = dtSeconds.coerceAtMost(.033f)
        while (remaining > 0f) {
            val dt = min(remaining, 1f / 120f)
            integrate(dt, minX, maxX, minY, maxY, gravityEnabled)
            remaining = (remaining - dt).coerceAtLeast(0f)
        }
    }

    private fun integrate(dt: Float, minX: Float, maxX: Float, minY: Float, maxY: Float, gravityEnabled: Boolean) {
        val springAcceleration = -180f * deformation - 12f * deformationVel
        deformationVel += springAcceleration * dt
        val nextDeformation = deformation + deformationVel * dt
        deformation = nextDeformation.coerceIn(-.35f, .35f)
        if (nextDeformation != deformation) deformationVel *= .25f

        if (isDragged) {
            isDockedEdge = false
            dockSide = 0
            isOnFloor = false
            x = x.coerceIn(minX, maxX)
            y = y.coerceIn(minY, maxY)
            return
        }

        // Capture slow movement near an edge before gravity accelerates it.
        val band = min(48f, (maxX - minX) * .2f)
        dockSide = when {
            maxX <= minX || hypot(vx, vy) >= 120f -> 0
            x <= minX + band -> -1
            x >= maxX - band -> 1
            else -> 0
        }
        isDockedEdge = dockSide != 0
        if (isDockedEdge) {
            val attraction = 1f - exp(-10f * dt)
            x += ((if (dockSide < 0) minX else maxX) - x) * attraction
            vx *= exp(-12f * dt)
            vy *= exp(-12f * dt)
        } else if (gravityEnabled) {
            vy += 1800f * dt
        }

        // 0.985 damping per reference 60 Hz frame; independent of display rate.
        val damping = .985f.pow(dt * 60f)
        vx *= damping
        vy *= damping
        x += vx * dt
        y += vy * dt

        if (maxX <= minX) {
            x = minX
            vx = 0f
        } else if (x <= minX) {
            x = minX
            if (vx < 0f) {
                if (abs(vx) > 120f) impulseSquash(-min(abs(vx) / 500f, 3f))
                vx = -vx * .65f
            }
        } else if (x >= maxX) {
            x = maxX
            if (vx > 0f) {
                if (abs(vx) > 120f) impulseSquash(-min(abs(vx) / 500f, 3f))
                vx = -vx * .65f
            }
        }

        isOnFloor = false
        if (y >= maxY) {
            y = maxY
            if (vy > 0f) {
                if (vy > 120f) impulseSquash(min(vy / 500f, 3f))
                vy = -vy * .55f
                vx *= .82f
                if (abs(vy) < 45f) vy = 0f
            }
            isOnFloor = vy == 0f
        } else if (y < minY) {
            y = minY
            if (vy < 0f) vy = -vy * .55f
        }
    }
}

private fun Float.finiteOrZero(): Float = if (isFinite()) this else 0f

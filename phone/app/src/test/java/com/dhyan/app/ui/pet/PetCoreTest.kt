package com.dhyan.app.ui.pet

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs
import kotlin.math.pow

class PetCoreTest {
    @Test fun allEmotionsHaveFiniteTargetsAndBlinkOnlyClosesEyes() {
        assertEquals(10, PetEmotion.entries.size)
        PetEmotion.entries.forEach { emotion ->
            val face = emotion.toFacialParams(false)
            assertTrue(face.eyeOpen in 0f..1.5f)
            assertTrue(face.eyeSlant in -1f..1f)
            assertTrue(face.pupilScale in 0f..1.5f)
            assertTrue(face.mouthCurve in -1f..1f)
            assertTrue(face.blushAlpha in 0f..1f)
            assertTrue(face.auraIntensity in 0f..1f)
            assertEquals(1f, face.primaryColor.alpha, 0f)
            assertEquals(face.copy(eyeOpen = 0f), emotion.toFacialParams(true))
        }
    }

    @Test fun speechHasTransientDefaultLifetime() {
        assertEquals(2500L, PetSpeechBubble("Hello").durationMs)
        assertEquals(800L, PetSpeechBubble("Hello", 800L).durationMs)
    }

    @Test fun gravityToggleChangesFallingWithoutMovingWeightlessPet() {
        val floating = PetPhysicsState(200f, 200f)
        val falling = PetPhysicsState(200f, 200f)
        floating.step(1f / 60f, 500f, 900f, 96f, false)
        falling.step(1f / 60f, 500f, 900f, 96f, true)
        assertEquals(200f, floating.y, 0f)
        assertTrue(falling.y > floating.y)
        assertTrue(falling.vy > 0f)
    }

    @Test fun bothWallsReflectVelocityAndTriggerHorizontalSquash() {
        val dt = 1f / 240f
        listOf(12f to -400f, 392f to 400f).forEach { (position, velocity) ->
            val state = PetPhysicsState(position, 200f)
            state.vx = velocity
            state.step(dt, 500f, 500f, 96f, false)
            assertEquals(position, state.x, .001f)
            assertEquals(-velocity * .985f.pow(dt * 60f) * .65f, state.vx, .001f)
            assertTrue(state.deformationVel < 0f)
        }
    }

    @Test fun floorReflectsAndAppliesGroundFriction() {
        val state = PetPhysicsState(200f, 392f)
        state.vx = 100f
        state.vy = 400f
        val dt = 1f / 240f
        val damping = .985f.pow(dt * 60f)
        state.step(dt, 500f, 500f, 96f, false)
        assertEquals(392f, state.y, .001f)
        assertEquals(-400f * damping * .55f, state.vy, .001f)
        assertEquals(100f * damping * .82f, state.vx, .001f)
        assertTrue(state.deformationVel > 0f)
    }

    @Test fun springPreservesVolumeAndReturnsToRest() {
        val state = PetPhysicsState(200f, 200f)
        state.impulseSquash(5f)
        repeat(600) {
            state.step(1f / 120f, 600f, 900f, 96f, false)
            assertEquals(1f, state.scaleX * state.scaleY, .00001f)
            assertTrue(state.scaleX in .65f..1.35f)
        }
        assertTrue(abs(state.deformation) < .001f)
        assertTrue(abs(state.deformationVel) < .001f)
    }

    @Test fun slowEdgesDockAndTiltInwardWithoutFalling() {
        listOf(20f to 18f, 384f to -18f).forEach { (position, tilt) ->
            val state = PetPhysicsState(position, 100f)
            repeat(120) { state.step(1f / 120f, 500f, 900f, 96f, true) }
            assertTrue(state.isDockedEdge)
            assertEquals(if (position < 200f) 12f else 392f, state.x, .01f)
            assertEquals(100f, state.y, .001f)
            assertEquals(tilt, state.rotationDegrees, 0f)
        }
    }

    @Test fun fastThrowDoesNotMagneticallyDock() {
        val state = PetPhysicsState(20f, 100f)
        state.vx = 500f
        state.step(1f / 120f, 500f, 900f, 96f, true)
        assertFalse(state.isDockedEdge)
        assertTrue(state.x > 20f)
    }

    @Test fun draggingLeavesPositionUnderGestureControlAndUndocks() {
        val state = PetPhysicsState(20f, 100f)
        state.isDockedEdge = true
        state.isDragged = true
        state.vx = 200f
        state.vy = 300f
        state.step(.03f, 500f, 900f, 96f, true)
        assertEquals(20f, state.x, 0f)
        assertEquals(100f, state.y, 0f)
        assertFalse(state.isDockedEdge)
    }

    @Test fun longFrameIsClampedAndFramePartitionIsConsistent() {
        val longFrame = PetPhysicsState(200f, 100f)
        val capped = PetPhysicsState(200f, 100f)
        longFrame.step(2f, 1000f, 2000f, 96f, true)
        capped.step(.033f, 1000f, 2000f, 96f, true)
        assertEquals(capped.y, longFrame.y, .0001f)
        val sixty = PetPhysicsState(200f, 100f)
        val oneTwenty = PetPhysicsState(200f, 100f)
        sixty.vx = 30f
        oneTwenty.vx = 30f
        repeat(60) { sixty.step(1f / 60f, 1000f, 2000f, 96f, false) }
        repeat(120) { oneTwenty.step(1f / 120f, 1000f, 2000f, 96f, false) }
        assertEquals(oneTwenty.x, sixty.x, .001f)
        assertEquals(oneTwenty.vx, sixty.vx, .001f)
    }

    @Test fun tinyBoundsAndInvalidInputNeverProduceNonFiniteState() {
        val state = PetPhysicsState()
        state.step(.033f, 20f, 20f, 96f, true)
        assertEquals(0f, state.x, 0f)
        assertEquals(0f, state.y, 0f)
        val before = state.y
        listOf(Float.NaN, Float.POSITIVE_INFINITY, -1f, 0f).forEach {
            state.step(it, 500f, 900f, 96f, true)
            assertEquals(before, state.y, 0f)
        }
        state.impulseSquash(Float.NaN)
        assertTrue(state.scaleX.isFinite())
        assertTrue(state.scaleY.isFinite())
    }
}

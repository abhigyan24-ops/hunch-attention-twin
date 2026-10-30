package com.dhyan.app.ui.pet

import com.dhyan.app.twin.model.FocusState
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

class PetDistractionControllerTest {

    @Test
    fun petCanMoveToAllFourQuadrantsOfScreenWithoutClampingToCenter() {
        val screenW = 1080f
        val screenH = 2400f
        val petSize = 96f
        val dt = 1f / 60f

        // Quadrant 1 (Top-Left: x < 540, y < 1200)
        val q1 = PetPhysicsState(50f, 100f)
        q1.step(dt, screenW, screenH, petSize, gravityEnabled = false)
        assertTrue("Q1 X must be in top-left quadrant", q1.x in 12f..540f)
        assertTrue("Q1 Y must be in top-left quadrant", q1.y in 12f..1200f)

        // Quadrant 2 (Top-Right: x >= 540, y < 1200)
        val q2 = PetPhysicsState(900f, 100f)
        q2.step(dt, screenW, screenH, petSize, gravityEnabled = false)
        assertTrue("Q2 X must be in top-right quadrant", q2.x >= 540f && q2.x <= screenW - petSize)
        assertTrue("Q2 Y must be in top-right quadrant", q2.y in 12f..1200f)

        // Quadrant 3 (Bottom-Left: x < 540, y >= 1200)
        val q3 = PetPhysicsState(50f, 2200f)
        q3.step(dt, screenW, screenH, petSize, gravityEnabled = false)
        assertTrue("Q3 X must be in bottom-left quadrant", q3.x in 12f..540f)
        assertTrue("Q3 Y must be in bottom-right quadrant", q3.y >= 1200f && q3.y <= screenH - petSize)

        // Quadrant 4 (Bottom-Right: x >= 540, y >= 1200)
        val q4 = PetPhysicsState(900f, 2200f)
        q4.step(dt, screenW, screenH, petSize, gravityEnabled = false)
        assertTrue("Q4 X must be in bottom-right quadrant", q4.x >= 540f && q4.x <= screenW - petSize)
        assertTrue("Q4 Y must be in bottom-right quadrant", q4.y >= 1200f && q4.y <= screenH - petSize)
    }

    @Test
    fun draggingFreelyReachesEdgeCoordinatesAndUndockClearsMagnetism() {
        val state = PetPhysicsState(500f, 1000f)
        state.isDockedEdge = true
        assertTrue(state.isDockedEdge)

        // On drag start, undock() must immediately clear edge magnetism
        state.undock()
        assertFalse("isDockedEdge must be false immediately upon undock()", state.isDockedEdge)

        // Simulate dragging to extreme top-left (0, 0)
        state.isDragged = true
        state.x = 0f
        state.y = 0f
        state.step(1f / 60f, 1080f, 2400f, 96f, gravityEnabled = false)
        assertEquals(12f, state.x, 0.1f) // clamped to min edge margin
        assertEquals(12f, state.y, 0.1f)

        // Simulate dragging to extreme bottom-right
        state.x = 1080f - 96f
        state.y = 2400f - 96f
        state.step(1f / 60f, 1080f, 2400f, 96f, gravityEnabled = false)
        assertEquals(1080f - 96f - 12f, state.x, 0.1f)
        assertEquals(2400f - 96f - 12f, state.y, 0.1f)
    }

    @Test
    fun analyticalSpringToConvergesMonotonicallyWithoutOvershoot() {
        val state = PetPhysicsState(100f, 100f)
        val targetX = 500f
        val targetY = 800f
        val dt = 1f / 60f

        // Step spring towards target over 1.5 seconds (90 frames)
        repeat(90) {
            state.springTo(targetX, targetY, omega = 10f, dtSeconds = dt, boundsWidth = 1080f, boundsHeight = 2400f, petSizePx = 96f)
        }

        // Must converge cleanly within 1px of target without overshooting
        assertEquals(targetX, state.x, 1.5f)
        assertEquals(targetY, state.y, 1.5f)
        assertTrue("Residual velocity must be near zero", abs(state.vx) < 5f && abs(state.vy) < 5f)
    }

    @Test
    fun distractionEscalatesThroughAllFourStages() {
        val controller = PetDistractionController(null)
        assertEquals(DistractionStage.CALM, controller.stage)
        assertEquals(0, controller.nudgesShown)
        assertFalse(controller.nudgeActive)

        // Stage 1 (0–5s): Subtle Gaze docked at edge
        controller.step(FocusState.DRIFTING, isTaskActive = true, nowMs = 1000L)
        assertEquals(DistractionStage.STAGE1_SUBTLE_GAZE, controller.stage)
        assertEquals(0, controller.nudgesShown)
        assertFalse(controller.nudgeActive)

        // Step at 3s elapsed -> Still Stage 1
        controller.step(FocusState.DRIFTING, isTaskActive = true, nowMs = 4000L)
        assertEquals(DistractionStage.STAGE1_SUBTLE_GAZE, controller.stage)
        assertEquals(0, controller.nudgesShown)

        // Stage 2 (5–12s): Edge Peek & Knock at 6s elapsed
        controller.step(FocusState.DRIFTING, isTaskActive = true, nowMs = 7000L)
        assertEquals(DistractionStage.STAGE2_EDGE_PEEK_KNOCK, controller.stage)
        assertEquals(1, controller.nudgesShown)
        assertTrue(controller.nudgeActive)
        assertEquals("stage2", controller.nudgeType)
        assertEquals("Drifting off?", controller.nudgePrompt)

        // Stage 3 (>12s): Active Center Interceptor at 13s elapsed
        controller.step(FocusState.DRIFTING, isTaskActive = true, nowMs = 14000L)
        assertEquals(DistractionStage.STAGE3_CENTER_INTERCEPTOR, controller.stage)
        assertTrue(controller.nudgeActive)
        assertEquals("stage3", controller.nudgeType)
        assertEquals("Take a breath. Refocus?", controller.nudgePrompt)
        // Escalation within same distraction episode must not double-count nudgesShown
        assertEquals(1, controller.nudgesShown)
    }

    @Test
    fun acceptingNudgeIncrementsAcceptedAndTransitionsToReturnMargin() {
        val controller = PetDistractionController(null)
        controller.triggerStage2(1000L)
        assertEquals(1, controller.nudgesShown)
        assertEquals(0, controller.nudgesAccepted)
        assertTrue(controller.nudgeActive)

        controller.onNudgeAccepted()
        assertEquals(1, controller.nudgesAccepted)
        assertFalse(controller.nudgeActive)
        assertEquals(DistractionStage.RETURN_TO_MARGIN, controller.stage)

        controller.onReturnCompleted()
        assertEquals(DistractionStage.CALM, controller.stage)
    }

    @Test
    fun resumingTaskWithinTenSecondsAutomaticallyAcceptsNudge() {
        val controller = PetDistractionController(null)
        controller.triggerStage3(10_000L)
        assertEquals(1, controller.nudgesShown)
        assertEquals(0, controller.nudgesAccepted)

        // Task resumed 5s later (< 10s window)
        controller.step(FocusState.DEEP_FOCUS, isTaskActive = true, nowMs = 15_000L)
        assertEquals(1, controller.nudgesAccepted)
        assertFalse(controller.nudgeActive)
        assertEquals(DistractionStage.RETURN_TO_MARGIN, controller.stage)
    }

    @Test
    fun dismissingNudgeIncrementsDismissedAndTransitionsToReturnMargin() {
        val controller = PetDistractionController(null)
        controller.triggerStage2(1000L)
        assertEquals(0, controller.nudgesDismissed)

        controller.onNudgeDismissed()
        assertEquals(1, controller.nudgesDismissed)
        assertFalse(controller.nudgeActive)
        assertEquals(DistractionStage.RETURN_TO_MARGIN, controller.stage)
    }

    @Test
    fun autoBackoffRuleIncreasesCooldownMultiplierWhenDismissalsDominate() {
        val controller = PetDistractionController(null)

        // Set up 5 nudges: 4 dismissed, 1 accepted -> dismissalRate = 80% (> 60%)
        controller.nudgesShown = 5
        controller.nudgesDismissed = 4
        controller.nudgesAccepted = 1

        controller.evaluateAdaptiveThreshold()
        assertEquals(0.80f, controller.dismissalRate, 0.001f)
        assertTrue(
            "Multiplier must be raised to 1.5x cooldown multiplier when dismissals exceed 60%",
            controller.nudgeThresholdMultiplier >= 1.5f
        )
        assertTrue(
            "Multiplier must be capped at 3.0f",
            controller.nudgeThresholdMultiplier <= 3.0f
        )

        // When acceptance improves (4 accepted, 1 dismissed -> dismissalRate = 20% <= 40%)
        controller.nudgesShown = 5
        controller.nudgesAccepted = 4
        controller.nudgesDismissed = 1
        controller.evaluateAdaptiveThreshold()
        assertEquals(1.0f, controller.nudgeThresholdMultiplier, 0.001f)
    }
}

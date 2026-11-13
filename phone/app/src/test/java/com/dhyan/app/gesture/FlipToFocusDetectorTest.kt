package com.dhyan.app.gesture

import org.junit.Assert.assertEquals
import org.junit.Test

class FlipToFocusDetectorTest {
    @Test fun requiresFlatCoveredAndSustainedPlacement() {
        fun sample(z: Float = -9.8f, x: Float = 0f, near: Boolean = true, ms: Long = 1500) =
            FlipToFocusDetector.evaluateFlipSample(z, x, 0f, near, ms)
        assertEquals(FlipState.FOCUS_READY, sample())
        assertEquals(FlipState.FACE_DOWN_FLAT, sample(ms = 1499))
        assertEquals(FlipState.INACTIVE, sample(z = 9.8f))
        assertEquals(FlipState.INACTIVE, sample(x = 2f))
        assertEquals(FlipState.INACTIVE, sample(near = false))
        assertEquals(FlipState.INACTIVE, sample(z = Float.NaN))
    }
}

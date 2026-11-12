package com.dhyan.app.survival

import org.junit.Assert.*
import org.junit.Test

class OemSurvivalHelperTest {

    @Test
    fun `manufacturer guidance is non-empty string`() {
        // OemSurvivalHelper.getManufacturerGuidance() uses Build.MANUFACTURER which is "robolectric" in JVM test
        val guidance = OemSurvivalHelper.getManufacturerGuidance()
        assertTrue(guidance.isNotBlank())
    }

    @Test
    fun `manufacturer property is lowercase`() {
        val mfr = OemSurvivalHelper.manufacturer
        assertEquals(mfr, mfr.lowercase())
    }
}

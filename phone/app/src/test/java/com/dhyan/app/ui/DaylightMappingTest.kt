package com.dhyan.app.ui

import androidx.compose.ui.geometry.Offset
import com.dhyan.app.ui.components.dialFraction
import com.dhyan.app.ui.components.recordedShare
import org.junit.Assert.*
import org.junit.Test

class DaylightMappingTest {
    @Test fun dialMapsTheWholeSemicircleIncludingBothEnds() {
        val center = Offset(100f, 100f)
        assertEquals(0f, dialFraction(Offset(0f, 100f), center), .0001f)
        assertEquals(.5f, dialFraction(Offset(100f, 0f), center), .0001f)
        assertEquals(1f, dialFraction(Offset(200f, 100f), center), .0001f)
    }
    @Test fun dialRejectsNonFiniteInputAndClampsBelowArcTouches() {
        assertEquals(0f, dialFraction(Offset(Float.NaN, 0f), Offset.Zero), 0f)
        assertEquals(1f, dialFraction(Offset(10f, 20f), Offset.Zero), 0f)
    }
    @Test fun missingAndInvalidTotalsAreNotDisplayedAsMeasuredZeroPercent() {
        assertNull(recordedShare(0, 0))
        assertNull(recordedShare(2, 1))
        assertNull(recordedShare(-1, 2))
    }
    @Test fun recordedShareUsesOnlyObservedNumeratorAndDenominator() {
        assertEquals(.25f, recordedShare(1, 4)!!, 0f)
        assertEquals(0f, recordedShare(0, 4)!!, 0f)
        assertEquals(1f, recordedShare(Int.MAX_VALUE, Int.MAX_VALUE)!!, 0f)
    }
}

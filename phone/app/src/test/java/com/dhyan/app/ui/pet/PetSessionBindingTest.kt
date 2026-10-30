package com.dhyan.app.ui.pet

import com.dhyan.app.isPetTaskActive
import com.dhyan.app.twin.model.DeclaredSession
import com.dhyan.app.twin.model.TaskType
import org.junit.Assert.*
import org.junit.Test

class PetSessionBindingTest {
    private fun declared(start: Long = 1000L, minutes: Double = 5.0) =
        DeclaredSession(TaskType.CODING, minutes, false, start)

    @Test fun sessionStartsInclusivelyAndExpiresExclusively() {
        val session = declared()
        assertFalse(isPetTaskActive(session, 999L))
        assertTrue(isPetTaskActive(session, 1000L))
        assertTrue(isPetTaskActive(session, 300_999L))
        assertFalse(isPetTaskActive(session, 301_000L))
    }

    @Test fun absentOrEndedSessionCannotActivateThePet() {
        assertFalse(isPetTaskActive(null, 1000L))
        assertFalse(isPetTaskActive(declared(), 400_000L))
    }

    @Test fun invalidDurationDoesNotCreateAnActiveTask() {
        listOf(0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY, Double.MAX_VALUE).forEach {
            assertFalse(isPetTaskActive(declared(minutes = it), 2000L))
        }
    }

    @Test fun negativeAndFutureTimestampsCannotActivateThePet() {
        assertFalse(isPetTaskActive(declared(), -1L))
        assertFalse(isPetTaskActive(declared(start = -1L), 1000L))
        assertFalse(isPetTaskActive(declared(start = 50_000L), 1000L))
    }

    @Test fun elapsedTimeComparisonDoesNotOverflow() {
        assertFalse(isPetTaskActive(declared(start = 0L), Long.MAX_VALUE))
    }
}

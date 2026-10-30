package com.dhyan.app.inference

import com.dhyan.app.twin.model.DeclaredSession
import com.dhyan.app.twin.model.FocusState
import com.dhyan.app.twin.model.LaptopState
import com.dhyan.app.twin.model.PhoneState
import com.dhyan.app.twin.model.TaskType
import org.junit.Assert.assertEquals
import org.junit.Test

class RuleBasedFocusStateInferrerTest {

    private val inferrer = RuleBasedFocusStateInferrer()

    @Test
    fun testStrictDeclaredSession() {
        val strictSession = DeclaredSession(TaskType.CODING, 60.0, strict = true, startedTs = 1000L)

        // Screen off during strict session -> DEEP_FOCUS
        assertEquals(
            FocusState.DEEP_FOCUS,
            inferrer.inferFocusState(
                phoneState = PhoneState.SCREEN_OFF,
                laptopState = LaptopState.FLOWING,
                isLaptopStale = false,
                isSecondScreenOverlap = false,
                declaredSession = strictSession
            )
        )

        // Phone on during strict session -> DISTRACTED (or DRIFTING if second-screen)
        assertEquals(
            FocusState.DISTRACTED,
            inferrer.inferFocusState(
                phoneState = PhoneState.IN_APP,
                laptopState = LaptopState.FLOWING,
                isLaptopStale = false,
                isSecondScreenOverlap = false,
                declaredSession = strictSession
            )
        )

        assertEquals(
            FocusState.DRIFTING,
            inferrer.inferFocusState(
                phoneState = PhoneState.IN_APP,
                laptopState = LaptopState.FLOWING,
                isLaptopStale = false,
                isSecondScreenOverlap = true,
                declaredSession = strictSession
            )
        )
    }

    @Test
    fun testNonStrictDeclaredSession() {
        val session = DeclaredSession(TaskType.STUDYING, 30.0, strict = false, startedTs = 1000L)

        // Laptop flowing -> DEEP_FOCUS
        assertEquals(
            FocusState.DEEP_FOCUS,
            inferrer.inferFocusState(
                phoneState = PhoneState.SCREEN_OFF,
                laptopState = LaptopState.FLOWING,
                isLaptopStale = false,
                isSecondScreenOverlap = false,
                declaredSession = session
            )
        )

        // Laptop idle -> WORKING
        assertEquals(
            FocusState.WORKING,
            inferrer.inferFocusState(
                phoneState = PhoneState.SCREEN_OFF,
                laptopState = LaptopState.IDLE_OPEN,
                isLaptopStale = false,
                isSecondScreenOverlap = false,
                declaredSession = session
            )
        )
    }

    @Test
    fun testSecondScreenOverlapProducesDrifting() {
        val state = inferrer.inferFocusState(
            phoneState = PhoneState.IN_APP,
            laptopState = LaptopState.FLOWING,
            isLaptopStale = false,
            isSecondScreenOverlap = true,
            declaredSession = null
        )
        assertEquals(FocusState.DRIFTING, state)
    }

    @Test
    fun testPhoneDistractingAppProducesDistracted() {
        val state = inferrer.inferFocusState(
            phoneState = PhoneState.IN_APP,
            laptopState = LaptopState.OFFLINE,
            isLaptopStale = false,
            isSecondScreenOverlap = false,
            declaredSession = null,
            phoneAppCat = "social"
        )
        assertEquals(FocusState.DISTRACTED, state)
    }

    @Test
    fun testLaptopStateGovernsWhenPhoneScreenOff() {
        assertEquals(
            FocusState.DEEP_FOCUS,
            inferrer.inferFocusState(PhoneState.SCREEN_OFF, LaptopState.FLOWING, false, false, null)
        )
        assertEquals(
            FocusState.WORKING,
            inferrer.inferFocusState(PhoneState.SCREEN_OFF, LaptopState.STUCK, false, false, null)
        )
        assertEquals(
            FocusState.DISTRACTED,
            inferrer.inferFocusState(PhoneState.SCREEN_OFF, LaptopState.GAMING, false, false, null)
        )
        assertEquals(
            FocusState.RECOVERING,
            inferrer.inferFocusState(PhoneState.SCREEN_OFF, LaptopState.AWAY, false, false, null)
        )
        assertEquals(
            FocusState.RECOVERING,
            inferrer.inferFocusState(PhoneState.SCREEN_OFF, LaptopState.OFFLINE, false, false, null)
        )
    }

    @Test
    fun testScrollingDuringSessionProducesDistracted() {
        val session = DeclaredSession(TaskType.CODING, 45.0, strict = false, startedTs = 1000L)
        val state = inferrer.inferFocusState(
            phoneState = PhoneState.SCROLLING,
            laptopState = LaptopState.FLOWING,
            isLaptopStale = false,
            isSecondScreenOverlap = false,
            declaredSession = session,
            phoneAppCat = "communication"
        )
        assertEquals(FocusState.DISTRACTED, state)
    }

    @Test
    fun testSocialAppDuringSessionProducesDistracted() {
        val session = DeclaredSession(TaskType.CODING, 45.0, strict = false, startedTs = 1000L)
        val state = inferrer.inferFocusState(
            phoneState = PhoneState.IN_APP,
            laptopState = LaptopState.FLOWING,
            isLaptopStale = false,
            isSecondScreenOverlap = false,
            declaredSession = session,
            phoneAppCat = "social"
        )
        assertEquals(FocusState.DISTRACTED, state)
    }

    @Test
    fun testEntertainmentAppDuringSessionProducesDistracted() {
        val session = DeclaredSession(TaskType.STUDYING, 25.0, strict = false, startedTs = 1000L)
        val state = inferrer.inferFocusState(
            phoneState = PhoneState.IN_APP,
            laptopState = LaptopState.IDLE_OPEN,
            isLaptopStale = false,
            isSecondScreenOverlap = false,
            declaredSession = session,
            phoneAppCat = "entertainment"
        )
        assertEquals(FocusState.DISTRACTED, state)
    }
}

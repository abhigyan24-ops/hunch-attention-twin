package com.dhyan.app.inference

import com.dhyan.app.twin.model.DeclaredSession
import com.dhyan.app.twin.model.FocusState
import com.dhyan.app.twin.model.LaptopState
import com.dhyan.app.twin.model.PhoneState

/**
 * Minimal rule-based focus state inferrer for Stage 8 multi-device fusion (Milestone M2).
 *
 * Infers [FocusState] from declared session, laptop state, phone state,
 * and second-screen overlap. Real-time distraction (social, entertainment, scrolling)
 * during an active focus session is strictly detected as DISTRACTED.
 */
class RuleBasedFocusStateInferrer {

    fun inferFocusState(
        phoneState: PhoneState,
        laptopState: LaptopState,
        isLaptopStale: Boolean,
        isSecondScreenOverlap: Boolean,
        declaredSession: DeclaredSession?,
        phoneAppCat: String? = null
    ): FocusState {
        val effectiveLaptop = if (isLaptopStale) LaptopState.OFFLINE else laptopState

        // 1. Explicit declared focus session takes highest priority
        if (declaredSession != null) {
            if (phoneState != PhoneState.SCREEN_OFF) {
                // Active phone scrolling or distraction during focus session is immediately marked DISTRACTED
                if (phoneAppCat in listOf("social", "entertainment", "gaming") || phoneState == PhoneState.SCROLLING) {
                    return FocusState.DISTRACTED
                }
                return if (declaredSession.strict) {
                    if (isSecondScreenOverlap) FocusState.DRIFTING else FocusState.DISTRACTED
                } else {
                    if (isSecondScreenOverlap) FocusState.DRIFTING else FocusState.WORKING
                }
            } else {
                return if (declaredSession.strict || effectiveLaptop == LaptopState.FLOWING) FocusState.DEEP_FOCUS else FocusState.WORKING
            }
        }

        // 2. Second-screen overlap: working on laptop but actively on phone -> drifting
        if (isSecondScreenOverlap) {
            return FocusState.DRIFTING
        }

        // 3. Phone activity without declared session
        if (phoneState != PhoneState.SCREEN_OFF) {
            if (phoneAppCat in listOf("social", "entertainment", "gaming") || phoneState == PhoneState.SCROLLING) {
                return FocusState.DISTRACTED
            }
            return FocusState.WORKING
        }

        // 4. Phone is SCREEN_OFF: focus governed by laptop telemetry
        return when (effectiveLaptop) {
            LaptopState.FLOWING -> FocusState.DEEP_FOCUS
            LaptopState.STUCK,
            LaptopState.IDLE_OPEN,
            LaptopState.BROWSING -> FocusState.WORKING
            LaptopState.GAMING -> FocusState.DISTRACTED
            LaptopState.AWAY -> FocusState.RECOVERING
            LaptopState.OFFLINE -> FocusState.RECOVERING
        }
    }
}

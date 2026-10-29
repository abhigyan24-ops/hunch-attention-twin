package com.dhyan.app.fusion

import com.dhyan.app.twin.model.LaptopState
import com.dhyan.app.twin.model.PhoneState

/**
 * Detects second-screen overlap between phone and laptop (Milestone M2).
 *
 * SPECIFICATION & ASSUMPTION:
 * Overlap is true when phoneState != SCREEN_OFF continuously for at least 10 s
 * while laptopState is in {FLOWING, STUCK, IDLE_OPEN}, with 5 s hysteresis.
 * Stale laptop or OFFLINE laptop state never triggers or maintains overlap.
 */
class SecondScreenDetector(
    private val config: FusionConfig = FusionConfig(),
    private val timeProvider: () -> Long = System::currentTimeMillis
) {
    private var overlapActive: Boolean = false
    private var candidateStartMs: Long? = null
    private var clearConditionStartMs: Long? = null

    private val laptopEligibleStates = setOf(
        LaptopState.FLOWING,
        LaptopState.STUCK,
        LaptopState.IDLE_OPEN
    )

    fun update(
        phoneState: PhoneState,
        laptopState: LaptopState,
        isLaptopStale: Boolean,
        nowMs: Long = timeProvider()
    ): Boolean {
        if (isLaptopStale || laptopState == LaptopState.OFFLINE) {
            reset()
            return false
        }
        val eligibleLaptop = !isLaptopStale && laptopState in laptopEligibleStates
        val phoneActive = phoneState != PhoneState.SCREEN_OFF

        if (eligibleLaptop && phoneActive) {
            clearConditionStartMs = null
            if (!overlapActive) {
                val start = candidateStartMs ?: nowMs
                candidateStartMs = start
                if (nowMs - start >= config.secondScreenDebounceMs) {
                    overlapActive = true
                }
            }
        } else {
            candidateStartMs = null
            if (overlapActive) {
                val clearStart = clearConditionStartMs ?: nowMs
                clearConditionStartMs = clearStart
                if (nowMs - clearStart >= config.secondScreenHysteresisMs) {
                    overlapActive = false
                    clearConditionStartMs = null
                }
            }
        }
        return overlapActive
    }

    fun isOverlapActive(): Boolean = overlapActive

    fun reset() {
        overlapActive = false
        candidateStartMs = null
        clearConditionStartMs = null
    }
}

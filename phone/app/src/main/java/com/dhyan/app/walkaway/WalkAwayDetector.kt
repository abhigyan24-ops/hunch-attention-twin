package com.dhyan.app.walkaway

import kotlin.math.sqrt

enum class WalkAwayState {
    AT_DESK_ACTIVE,
    AT_DESK_PHONE_DRIFT,
    PACING_ON_CALL,
    WALK_AWAY_CONFIRMED,
    UNDETERMINED
}

data class WalkAwaySensorWindow(
    val stepTimestampsMillis: List<Long>,
    val accelRms: Float,
    val isScreenInteractive: Boolean,
    val hasRecentForegroundTouch: Boolean,
    val baselineRssiDbm: Int? = null,
    val currentRssiDbm: Int? = null,
    val justUnpluggedCharger: Boolean = false,
    val liftedFromFlipToFocus: Boolean = false,
    val laptopIdleSeconds: Int = 0,
    val inCall: Boolean = false
)

data class WalkAwayAssessment(
    val state: WalkAwayState,
    val confidence: Float,
    val gate1CadenceMet: Boolean,
    val gate2ScreenPocketVeto: Boolean,
    val gate3DepartureMet: Boolean,
    val gate4IdleMet: Boolean,
    val reason: String
)

object WalkAwayEvaluator {

    /**
     * Evaluates whether the user has physically walked away from their desk using 4-gate multi-signal sensor fusion.
     * Eliminates false positives from desk foot-tapping, standing desk shifts, phone browsing, or pacing during calls.
     */
    fun evaluateWalkAway(input: WalkAwaySensorWindow): WalkAwayAssessment {
        // Hard Veto 1: User is pacing while in a phone or meeting call
        if (input.inCall) {
            return WalkAwayAssessment(
                state = WalkAwayState.PACING_ON_CALL,
                confidence = 0.0f,
                gate1CadenceMet = false,
                gate2ScreenPocketVeto = false,
                gate3DepartureMet = false,
                gate4IdleMet = false,
                reason = "Active call or meeting vetoes walk-away detection"
            )
        }

        // Hard Veto 2: Laptop is actively being used or carried with key/mouse activity
        if (input.laptopIdleSeconds < 15) {
            return WalkAwayAssessment(
                state = WalkAwayState.AT_DESK_ACTIVE,
                confidence = 0.0f,
                gate1CadenceMet = false,
                gate2ScreenPocketVeto = false,
                gate3DepartureMet = false,
                gate4IdleMet = false,
                reason = "Laptop active ${input.laptopIdleSeconds}s ago (< 15s)"
            )
        }

        // Gate 1: Rhythmic Walking Cadence & Stride Filter (+0.50f)
        val cadenceMet = evaluateCadence(input.stepTimestampsMillis, input.accelRms)

        // Hard Veto 3: Screen interactive with recent touches and NOT in rhythmic walking cadence
        // (Desk distraction / phone drift)
        if (input.isScreenInteractive && input.hasRecentForegroundTouch && !cadenceMet) {
            return WalkAwayAssessment(
                state = WalkAwayState.AT_DESK_PHONE_DRIFT,
                confidence = 0.0f,
                gate1CadenceMet = false,
                gate2ScreenPocketVeto = true,
                gate3DepartureMet = false,
                gate4IdleMet = false,
                reason = "At-desk phone interaction without walking cadence"
            )
        }

        // Gate 3: Physical Departure Boost (+0.25f)
        val rssiDrop = if (input.baselineRssiDbm != null && input.currentRssiDbm != null) {
            input.baselineRssiDbm - input.currentRssiDbm
        } else {
            0
        }
        val departureMet = rssiDrop >= 6 ||
            input.justUnpluggedCharger ||
            input.liftedFromFlipToFocus ||
            input.stepTimestampsMillis.size >= 20

        // Gate 4: Cross-Device Laptop Idle Confirmation (+0.25f)
        val idleMet = input.laptopIdleSeconds >= 45 ||
            (input.laptopIdleSeconds >= 15 && cadenceMet && departureMet)

        var confidence = 0.0f
        if (cadenceMet) confidence += 0.50f
        if (departureMet) confidence += 0.25f
        if (idleMet) confidence += 0.25f

        val state = if (confidence >= 0.75f) {
            WalkAwayState.WALK_AWAY_CONFIRMED
        } else {
            WalkAwayState.UNDETERMINED
        }

        val reason = when (state) {
            WalkAwayState.WALK_AWAY_CONFIRMED ->
                "Walk-away confirmed with confidence $confidence (cadence=$cadenceMet, departure=$departureMet, idle=$idleMet)"
            else ->
                "Insufficient confidence ($confidence < 0.75): cadence=$cadenceMet, departure=$departureMet, idle=$idleMet"
        }

        return WalkAwayAssessment(
            state = state,
            confidence = confidence,
            gate1CadenceMet = cadenceMet,
            gate2ScreenPocketVeto = false,
            gate3DepartureMet = departureMet,
            gate4IdleMet = idleMet,
            reason = reason
        )
    }

    /**
     * Checks if walking cadence meets human biomechanics:
     * - >= 12 steps in sliding window
     * - Median inter-step interval in [380ms, 850ms]
     * - Interval standard deviation <= 180ms
     * - Linear acceleration RMS in [1.1 m/s², 6.5 m/s²]
     */
    fun evaluateCadence(stepTimestampsMillis: List<Long>, accelRms: Float): Boolean {
        if (stepTimestampsMillis.size < 12) return false
        if (accelRms !in 1.1f..6.5f) return false

        val sorted = stepTimestampsMillis.sorted()
        val intervals = (1 until sorted.size).map { sorted[it] - sorted[it - 1] }
        if (intervals.isEmpty()) return false

        val sortedIntervals = intervals.sorted()
        val medianInterval = if (sortedIntervals.size % 2 == 1) {
            sortedIntervals[sortedIntervals.size / 2].toDouble()
        } else {
            (sortedIntervals[sortedIntervals.size / 2 - 1] + sortedIntervals[sortedIntervals.size / 2]) / 2.0
        }

        if (medianInterval !in 380.0..850.0) return false

        val mean = intervals.average()
        val variance = intervals.map { (it - mean) * (it - mean) }.average()
        val stdDev = sqrt(variance)

        return stdDev <= 180.0
    }

    /**
     * Determines if the user has returned to their desk:
     * - Immediate trigger: laptop key/mouse interaction (idle < 3s)
     * - Phone placed face-down on desk and stationary for >= 10s
     */
    fun evaluateReturnToDesk(
        laptopIdleSeconds: Int,
        isFlipToFocusActive: Boolean,
        secondsSinceLastStep: Int
    ): Boolean {
        if (laptopIdleSeconds < 3) return true
        if (isFlipToFocusActive && secondsSinceLastStep >= 10) return true
        return false
    }
}

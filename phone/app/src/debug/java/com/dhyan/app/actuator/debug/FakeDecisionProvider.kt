package com.dhyan.app.actuator.debug

import com.dhyan.app.twin.decision.Action
import com.dhyan.app.twin.decision.Decision
import com.dhyan.app.twin.decision.DecisionProvider
import com.dhyan.app.twin.decision.NotificationFacts
import com.dhyan.app.twin.decision.PolicySettings
import com.dhyan.app.twin.decision.Recheck
import com.dhyan.app.twin.model.ContextSnapshot

/**
 * DEBUG/TEST SOURCE SET ONLY.
 * Always allows all notifications. Used for UI development and manual testing
 * before Stage 5 is integrated (M6).
 *
 * Must NOT appear in the release build. Enforced by being in src/debug/java/.
 * check-actuator-contract.mjs also scans for this class in production sources.
 */
class FakeDecisionProvider : DecisionProvider {

    /** Fake mode flag — expose so UI can show FAKE banner. */
    val isFake: Boolean = true

    override fun decide(
        facts: NotificationFacts,
        snapshot: ContextSnapshot,
        settings: PolicySettings,
        nowMs: Long
    ): Decision = Decision(
        action = Action.ALLOW,
        explanation = "FAKE: always allow",
        factors = emptyList(),
        holdMinutes = null,
        propensity = 1.0,
        eligibleActions = setOf(Action.ALLOW),
        explored = false,
        safetyReason = null,
        failedOpen = false,
        predictedLossShowMin = null,
        predictedLossSuppressMin = null,
        deltaLMean = null,
        deltaLP10 = null,
        deltaLP90 = null,
        evidenceStrength = null,
        provenance = "fake",
        modelVersion = 0L,
        policyVersion = "fake",
        decidedAtMs = nowMs,
        keyHash = facts.keyHash,
        postedTsMs = facts.postedTsMs,
        reasonCode = "fake_always_allow"
    )

    override fun recheck(
        facts: NotificationFacts,
        decision: Decision,
        settings: PolicySettings,
        nowMs: Long
    ): Recheck = Recheck.Proceed
}

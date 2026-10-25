package com.dhyan.app.actuator

import com.dhyan.app.twin.decision.Action
import com.dhyan.app.twin.decision.Decision
import com.dhyan.app.twin.decision.DecisionProvider
import com.dhyan.app.twin.decision.NotificationFacts
import com.dhyan.app.twin.decision.PolicySettings
import com.dhyan.app.twin.decision.Recheck
import com.dhyan.app.twin.model.ContextSnapshot

/**
 * Test-only decision provider that:
 *  - always returns [Recheck.Proceed] from recheck (unlike AlwaysAllowDecisionProvider which aborts)
 *  - returns the pre-supplied decision unchanged
 */
class PassThroughDecisionProvider : DecisionProvider {
    override fun decide(
        facts: NotificationFacts,
        snapshot: ContextSnapshot,
        settings: PolicySettings,
        nowMs: Long
    ): Decision = Decision(
        action = Action.ALLOW,
        explanation = "test: pass-through",
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
        provenance = "test",
        modelVersion = 0L,
        policyVersion = "test",
        decidedAtMs = nowMs,
        keyHash = facts.keyHash,
        postedTsMs = facts.postedTsMs,
        reasonCode = "test"
    )

    override fun recheck(
        facts: NotificationFacts,
        decision: Decision,
        settings: PolicySettings,
        nowMs: Long
    ): Recheck = Recheck.Proceed
}

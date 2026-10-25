package com.dhyan.app.twin.decision

import com.dhyan.app.twin.model.ContextSnapshot

/** Safe dependency-injection default until the reviewed real pipeline is supplied. */
class AlwaysAllowDecisionProvider : DecisionProvider {
    override fun decide(facts: NotificationFacts, snapshot: ContextSnapshot, settings: PolicySettings, nowMs: Long) = Decision(
        Action.ALLOW, "Allowed: not enough information.", emptyList(), null, 1.0, setOf(Action.ALLOW),
        false, "no_provider", true, null, null, null, null, null, null, "low evidence", 0L,
        "contract-$DECISION_CONTRACT_VERSION-no-provider", nowMs, facts.keyHash, facts.postedTsMs)
    override fun recheck(facts: NotificationFacts, decision: Decision, settings: PolicySettings, nowMs: Long): Recheck = Recheck.Abort("no_provider")
}

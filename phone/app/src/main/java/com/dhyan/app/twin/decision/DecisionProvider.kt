package com.dhyan.app.twin.decision

import com.dhyan.app.twin.model.ContextSnapshot

sealed interface Recheck {
    data object Proceed : Recheck
    data class Abort(val reason: String) : Recheck
}
interface DecisionProvider {
    /** Synchronous, no I/O or blocking. Never throws; any problem is ALLOW with failedOpen=true. */
    fun decide(facts: NotificationFacts, snapshot: ContextSnapshot, settings: PolicySettings, nowMs: Long): Decision
    /** Immediately before actuation: revalidate mode, safety, identity and age. Never throws. */
    fun recheck(facts: NotificationFacts, decision: Decision, settings: PolicySettings, nowMs: Long): Recheck
}
interface DecisionLogStore {
    /** Implemented asynchronously by Stage 6; the decision hot path never calls this. */
    suspend fun append(record: DecisionRecord)
}

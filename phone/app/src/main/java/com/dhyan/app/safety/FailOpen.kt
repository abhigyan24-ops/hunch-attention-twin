package com.dhyan.app.safety

import com.dhyan.app.twin.NotReadyReason
import com.dhyan.app.twin.SimOutcome
import com.dhyan.app.twin.decision.*

object FailOpen {
    inline fun guard(facts: NotificationFacts, nowMs: Long, diagnostics: DecisionDiagnostics, block: () -> Decision): Decision =
        try { block() } catch(e: Exception) {
            diagnostics.increment(DiagnosticCounter.EXCEPTION)
            // Exception type is a code identifier, never its message, cause or stack trace.
            decision(facts,nowMs,"exception:${e.javaClass.simpleName.ifEmpty { "Exception" }}")
        }
    fun invalidInput(facts: NotificationFacts, nowMs: Long, diagnostics: DecisionDiagnostics): Decision {
        diagnostics.increment(DiagnosticCounter.INVALID_INPUT)
        return decision(facts,nowMs,"invalid_input")
    }
    fun notReady(facts: NotificationFacts, nowMs: Long, outcome: SimOutcome.NotReady, diagnostics: DecisionDiagnostics): Decision {
        diagnostics.increment(DiagnosticCounter.NOT_READY)
        return when(outcome.reason) {
            NotReadyReason.MODEL_NOT_LOADED,NotReadyReason.INVALID_SNAPSHOT,NotReadyReason.STALE_CONTEXT -> decision(facts,nowMs,"not_ready")
        }
    }
    fun decision(facts: NotificationFacts, nowMs: Long, reasonCode: String): Decision = Decision(
        action=Action.ALLOW,explanation="Allowed: not enough information.",factors=emptyList(),holdMinutes=null,
        propensity=1.0,eligibleActions=setOf(Action.ALLOW),explored=false,safetyReason=null,failedOpen=true,
        predictedLossShowMin=null,predictedLossSuppressMin=null,deltaLMean=null,deltaLP10=null,deltaLP90=null,
        evidenceStrength=null,provenance="low evidence",modelVersion=0L,policyVersion="contract-$DECISION_CONTRACT_VERSION-safety-1",
        decidedAtMs=nowMs,keyHash=facts.keyHash,postedTsMs=facts.postedTsMs,reasonCode=reasonCode)
}

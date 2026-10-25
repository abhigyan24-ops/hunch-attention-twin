package com.dhyan.app.safety

import com.dhyan.app.twin.decision.*

/** No clock/platform access. Implementation helper for DecisionProvider.recheck;
 * Stage 6 calls the frozen DecisionProvider interface, not this helper directly. */
class RecheckLogic(
    private val diagnostics: DecisionDiagnostics = DecisionDiagnostics(),
    private val neverHold: NeverHoldRules = NeverHoldRules(diagnostics)
) {
    fun recheck(facts: NotificationFacts, decision: Decision, settings: PolicySettings, nowMs: Long): Recheck = try {
        when {
            settings.mode!=PolicyMode.LIVE -> abort("mode_not_live",DiagnosticCounter.RECHECK_MODE)
            settings.killSwitch -> abort("kill_switch",DiagnosticCounter.RECHECK_KILL)
            decision.failedOpen -> abort("failed_open",DiagnosticCounter.RECHECK_FAILED_OPEN)
            decision.action==Action.ALLOW -> abort("allow",DiagnosticCounter.RECHECK_ALLOW)
            !SafetyValidation.settingsValid(settings) || !SafetyValidation.factsValid(facts,nowMs) -> abort("invalid_input",DiagnosticCounter.RECHECK_INVALID)
            neverHold.check(facts,settings)!=null -> abort("never_hold",DiagnosticCounter.RECHECK_NEVER_HOLD)
            decision.keyHash!=facts.keyHash || decision.postedTsMs!=facts.postedTsMs -> abort("identity_mismatch",DiagnosticCounter.RECHECK_IDENTITY)
            decision.decidedAtMs<0 || decision.holdMinutes==null || !decision.holdMinutes.isFinite() || decision.holdMinutes<=0 ||
                !decision.propensity.isFinite() || decision.propensity<=0 || decision.propensity>1 || decision.action !in decision.eligibleActions -> abort("invalid_input",DiagnosticCounter.RECHECK_INVALID)
            nowMs<decision.decidedAtMs -> abort("clock_backwards",DiagnosticCounter.RECHECK_CLOCK)
            nowMs-decision.decidedAtMs>PolicyDefaults.MAX_DECISION_AGE_MS -> abort("decision_expired",DiagnosticCounter.RECHECK_EXPIRED)
            else -> Recheck.Proceed
        }
    } catch(_: Exception) {
        diagnostics.increment(DiagnosticCounter.EXCEPTION)
        abort("exception",DiagnosticCounter.RECHECK_EXCEPTION)
    }
    private fun abort(reason: String, counter: DiagnosticCounter): Recheck.Abort {
        diagnostics.increment(counter)
        return Recheck.Abort(reason)
    }
}

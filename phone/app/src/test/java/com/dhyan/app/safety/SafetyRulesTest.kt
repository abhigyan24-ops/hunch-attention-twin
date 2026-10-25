package com.dhyan.app.safety

import com.dhyan.app.twin.NotReadyReason
import com.dhyan.app.twin.SimOutcome
import com.dhyan.app.twin.decision.*
import org.junit.Assert.*
import org.junit.Test

class SafetyRulesTest {
    private val f=SafetyFixtures.facts
    @Test fun everyNeverHoldRuleIsIndependentOfModeKillSwitchAndPredictedCost() {
        val wouldHold=ActionSelector.select(CostFunction.compute(1000.0,1.0,.1,3.0,false,30.0,120.0))
        assertNotEquals(Action.ALLOW,wouldHold.action)
        val rules=NeverHoldRules()
        for((facts,reason) in SafetyFixtures.urgentCases)for(mode in PolicyMode.entries)for(kill in listOf(false,true)) {
            val settings=PolicySettings(mode=mode,killSwitch=kill,allowListPackages=if(reason=="allowlist")setOf(facts.pkg)else emptySet())
            assertEquals(reason,rules.check(facts,settings))
        }
    }
    @Test fun unknownCategoryIsCountOnlyAndCannotNeutralizeVip() {
        val diagnostics=DecisionDiagnostics();val rules=NeverHoldRules(diagnostics)
        assertNull(rules.check(f.copy(notifCat="synthetic-unknown-canary"),SafetyFixtures.live))
        assertEquals("vip",rules.check(f.copy(notifCat="synthetic-unknown-canary",isVip=true),SafetyFixtures.live))
        assertEquals(2L,diagnostics.snapshot().getValue("unknown_notif_cat"))
        assertFalse(diagnostics.snapshot().toString().contains("synthetic-unknown-canary"))
        for(cat in listOf("msg","message","promo","alert","sys","social","other"))assertNull(rules.check(f.copy(notifCat=cat),SafetyFixtures.live))
        assertEquals(2L,diagnostics.snapshot().getValue("unknown_notif_cat"))
    }
    @Test fun failOpenStripsExceptionMessagesAndPassesSuccessfulBlocks() {
        val diagnostics=DecisionDiagnostics()
        val decision=FailOpen.guard(f,SafetyFixtures.NOW,diagnostics){throw IllegalStateException("SYNTHETIC_CONTENT_CANARY")}
        assertEquals("exception:IllegalStateException",decision.reasonCode);assertEquals(Action.ALLOW,decision.action)
        assertTrue(decision.failedOpen);assertNull(decision.safetyReason);assertNull(decision.holdMinutes)
        assertEquals(setOf(Action.ALLOW),decision.eligibleActions);assertEquals(1.0,decision.propensity,0.0)
        assertFalse(decision.toString().contains("SYNTHETIC_CONTENT_CANARY"));assertFalse(diagnostics.snapshot().toString().contains("SYNTHETIC_CONTENT_CANARY"))
        val success=SafetyFixtures.hold();assertSame(success,FailOpen.guard(f,SafetyFixtures.NOW,diagnostics){success})
        assertEquals(1L,diagnostics.snapshot().getValue("exception"))
    }
    @Test fun everyNotReadyVariantAndInvalidInputFailsOpen() {
        val diagnostics=DecisionDiagnostics()
        for(reason in NotReadyReason.entries) {
            val d=FailOpen.notReady(f,SafetyFixtures.NOW,SimOutcome.NotReady(reason),diagnostics)
            assertEquals(Action.ALLOW,d.action);assertTrue(d.failedOpen);assertEquals("not_ready",d.reasonCode)
        }
        val d=FailOpen.invalidInput(f,SafetyFixtures.NOW,diagnostics)
        assertEquals("invalid_input",d.reasonCode);assertTrue(d.failedOpen)
        assertEquals(3L,diagnostics.snapshot().getValue("not_ready"));assertEquals(1L,diagnostics.snapshot().getValue("invalid_input"))
    }
    @Test fun diagnosticsAreAtomicAndSnapshotsAreReadOnly() {
        val diagnostics=DecisionDiagnostics()
        val threads=(1..8).map { Thread { repeat(2500){diagnostics.increment(DiagnosticCounter.EXCEPTION)} }.apply{start()} }
        threads.forEach{it.join()}
        val snapshot=diagnostics.snapshot();assertEquals(20000L,snapshot.getValue("exception"))
        try{(snapshot as MutableMap)["exception"]=0;fail()}catch(_:UnsupportedOperationException){}
    }
    @Test fun expiryHasExplicitDurationsAndNeverWraps() {
        val policy=DefaultExpiryPolicy()
        assertEquals(610000L,policy.expiresAtMs(f.copy(isOtpLike=true),10000))
        assertEquals(610000L,policy.expiresAtMs(f.copy(notifCat="otp"),10000))
        assertEquals(86410000L,policy.expiresAtMs(f,10000))
        assertEquals(Long.MAX_VALUE,policy.expiresAtMs(f,Long.MAX_VALUE-1))
        assertEquals(86400000L,policy.expiresAtMs(f,Long.MIN_VALUE))
    }
}

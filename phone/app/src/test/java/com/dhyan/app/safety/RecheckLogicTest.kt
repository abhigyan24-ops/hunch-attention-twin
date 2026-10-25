package com.dhyan.app.safety

import com.dhyan.app.twin.decision.*
import org.junit.Assert.*
import org.junit.Test

class RecheckLogicTest {
    private val f=SafetyFixtures.facts
    private val d=SafetyFixtures.hold()
    private val live=SafetyFixtures.live
    private fun abort(expected:String,facts:NotificationFacts=f,decision:Decision=d,settings:PolicySettings=live,now:Long=SafetyFixtures.NOW){
        assertEquals(Recheck.Abort(expected),RecheckLogic().recheck(facts,decision,settings,now))
    }
    @Test fun liveHoldCanProceedAndAgeBoundaryIsInclusive() {
        assertEquals(Recheck.Proceed,RecheckLogic().recheck(f,d,live,SafetyFixtures.NOW))
        assertEquals(Recheck.Proceed,RecheckLogic().recheck(f,d,live,SafetyFixtures.NOW+30000))
        abort("decision_expired",now=SafetyFixtures.NOW+30001)
    }
    @Test fun offDryRunAndKillSwitchAbort() {
        for(mode in listOf(PolicyMode.OFF,PolicyMode.DRY_RUN))abort("mode_not_live",settings=PolicySettings(mode=mode))
        abort("kill_switch",settings=PolicySettings(mode=PolicyMode.LIVE,killSwitch=true))
    }
    @Test fun allowOrFailedOpenNeverActuate() {
        abort("allow",decision=d.copy(action=Action.ALLOW))
        abort("failed_open",decision=d.copy(failedOpen=true))
    }
    @Test fun newlyCriticalFactsAndAllowListAbort() {
        for((facts,reason) in SafetyFixtures.urgentCases) {
            abort("never_hold",facts=facts,settings=PolicySettings(mode=PolicyMode.LIVE,allowListPackages=if(reason=="allowlist")setOf(f.pkg)else emptySet()))
        }
    }
    @Test fun changedKeyAndRepostedTimeAbort() {
        abort("identity_mismatch",facts=f.copy(keyHash="different-hash"))
        abort("identity_mismatch",facts=f.copy(postedTsMs=f.postedTsMs+1))
    }
    @Test fun backwardsAndExtremeClocksCannotOverflowIntoProceed() {
        abort("clock_backwards",now=SafetyFixtures.NOW-1)
        abort("invalid_input",now=Long.MIN_VALUE)
        abort("decision_expired",now=Long.MAX_VALUE)
    }
    @Test fun malformedDecisionCollectionExceptionsAbortWithoutLeaking() {
        val diagnostics=DecisionDiagnostics()
        val broken=object:AbstractSet<Action>() {
            override val size:Int get()=1
            override fun iterator():Iterator<Action> = setOf(Action.DIGEST).iterator()
            override fun contains(element:Action):Boolean = throw IllegalStateException("SYNTHETIC_RECHECK_CANARY")
        }
        val result=RecheckLogic(diagnostics).recheck(f,d.copy(eligibleActions=broken),live,SafetyFixtures.NOW)
        assertEquals(Recheck.Abort("exception"),result)
        assertEquals(1L,diagnostics.snapshot().getValue("exception"))
        assertEquals(1L,diagnostics.snapshot().getValue("recheck_abort_exception"))
        assertFalse(result.toString().contains("CANARY"))
        assertFalse(diagnostics.snapshot().toString().contains("CANARY"))
    }
    @Test fun invalidInputsAndMalformedHoldAbortWithFixedCounters() {
        for(settings in listOf(PolicySettings(mode=PolicyMode.LIVE,kappaDPerHour=Double.NaN),PolicySettings(mode=PolicyMode.LIVE,epsilon=1.1),PolicySettings(mode=PolicyMode.LIVE,digestIntervalMin=0)))abort("invalid_input",settings=settings)
        for(facts in listOf(f.copy(keyHash=""),f.copy(urgencyScore=Double.NaN),f.copy(repeatCount5m=-1),f.copy(postedTsMs=SafetyFixtures.NOW+1)))abort("invalid_input",facts=facts)
        for(decision in listOf(d.copy(holdMinutes=null),d.copy(holdMinutes=Double.NaN),d.copy(propensity=0.0),d.copy(eligibleActions=emptySet())))abort("invalid_input",decision=decision)
        val diagnostics=DecisionDiagnostics();val logic=RecheckLogic(diagnostics)
        logic.recheck(f,d,PolicySettings(),SafetyFixtures.NOW)
        logic.recheck(f,d.copy(failedOpen=true),live,SafetyFixtures.NOW)
        assertEquals(1L,diagnostics.snapshot().getValue("recheck_abort_mode_not_live"))
        assertEquals(1L,diagnostics.snapshot().getValue("recheck_abort_failed_open"))
    }
}

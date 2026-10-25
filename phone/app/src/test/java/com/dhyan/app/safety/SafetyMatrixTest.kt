package com.dhyan.app.safety

import com.dhyan.app.twin.*
import com.dhyan.app.twin.decision.*
import com.dhyan.app.twin.model.*
import com.dhyan.app.twin.params.*
import org.junit.Assert.*
import org.junit.Test

/** Primitive-level matrix. Full pipeline order is tested only after 5C exists. */
class SafetyMatrixTest {
    private val f=SafetyFixtures.facts
    private val diagnostics=DecisionDiagnostics()
    @Test fun knownUrgentExamplesBypassOptimization() {
        assertEquals("call",NeverHoldRules(diagnostics).check(f.copy(notifCat="call"),SafetyFixtures.live))
        assertEquals("otp",NeverHoldRules(diagnostics).check(f.copy(isOtpLike=true),SafetyFixtures.live))
        assertEquals("vip",NeverHoldRules(diagnostics).check(f.copy(isVip=true),SafetyFixtures.live))
    }
    @Test fun ambiguousMalformedFactsHaveExplicitFailOpenResult() {
        val ambiguous=f.copy(urgencyScore=Double.NaN)
        assertFalse(SafetyValidation.factsValid(ambiguous,SafetyFixtures.NOW))
        val result=FailOpen.invalidInput(ambiguous,SafetyFixtures.NOW,diagnostics)
        assertEquals(Action.ALLOW,result.action);assertTrue(result.failedOpen)
    }
    @Test fun classifierAndImportanceExceptionsDoNotHold() {
        for(component in listOf("classifier","importance")) {
            val result=FailOpen.guard(f,SafetyFixtures.NOW,diagnostics){throw IllegalArgumentException("synthetic-$component-canary")}
            assertTrue(result.failedOpen);assertEquals(Action.ALLOW,result.action)
            assertFalse(result.toString().contains("canary"))
        }
    }
    @Test fun actualStaleEngineOutcomeFailsOpenWithoutPersistence() {
        val persistence=object:ParamPersistence {
            override suspend fun loadAll():List<ParamRecord> = error("Persistence must not run")
            override suspend fun upsert(records:List<ParamRecord>){error("Persistence must not run")}
        }
        val engine=TwinEngine(InMemoryParamStore(persistence),clock={200001L})
        val snapshot=ContextSnapshot(1,FocusState.WORKING,TaskType.CODING,TaskSource.INFERRED,.8,"morning",1.0,"low",0,LaptopState.OFFLINE,PhoneState.SCREEN_OFF,false,null)
        val outcome=engine.simulate(snapshot,NotificationInput(f.appCat,f.notifCat,f.isVip,f.urgencyScore,f.repeatCount5m))
        assertEquals(SimOutcome.NotReady(NotReadyReason.STALE_CONTEXT),outcome)
        val result=FailOpen.notReady(f,200001L,outcome as SimOutcome.NotReady,diagnostics)
        assertEquals(Action.ALLOW,result.action);assertTrue(result.failedOpen)
    }
    @Test fun invalidSettingsFailOpen() {
        val settings=PolicySettings(mode=PolicyMode.LIVE,kappaDPerHour=Double.POSITIVE_INFINITY)
        assertFalse(SafetyValidation.settingsValid(settings))
        assertTrue(FailOpen.invalidInput(f,SafetyFixtures.NOW,diagnostics).failedOpen)
    }
    @Test fun duplicateOrRepostedIdentityAborts() {
        assertEquals(Recheck.Abort("identity_mismatch"),RecheckLogic().recheck(f.copy(postedTsMs=2000),SafetyFixtures.hold(),SafetyFixtures.live,SafetyFixtures.NOW))
    }
    @Test fun killSwitchActivationAbortsPreviouslyComputedHold() {
        val kill=InMemoryKillSwitch();val previous=SafetyFixtures.hold()
        kill.activate(KillSource.USER_SETTINGS,SafetyFixtures.NOW)
        assertEquals(Recheck.Abort("kill_switch"),RecheckLogic().recheck(f,previous,PolicySettings(mode=PolicyMode.LIVE,killSwitch=kill.active),SafetyFixtures.NOW))
    }
}

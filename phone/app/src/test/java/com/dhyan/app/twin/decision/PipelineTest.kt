package com.dhyan.app.twin.decision

import com.dhyan.app.safety.*
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.sqrt

class PipelineTest {
    private val f=DecisionTestData.facts;private val s=DecisionTestData.snapshot;private val now=DecisionTestData.NOW
    @Test fun shortCircuitsModeKillNeverHoldAndHighImportance() {
        var scored=0
        val predictor=LossPredictor{_,_,_,_->throw AssertionError("Predictor must not run")}
        val p=DecisionPipeline(predictor,ImportanceProvider{scored++;.9})
        assertEquals("mode_off",p.decide(f,s,PolicySettings(),now).reasonCode)
        assertEquals("kill_switch",p.decide(f,s,PolicySettings(mode=PolicyMode.LIVE,killSwitch=true),now).reasonCode)
        assertEquals("never_hold",p.decide(f.copy(isVip=true),s,DecisionTestData.settings,now).reasonCode)
        assertEquals(0,scored)
        assertEquals("high_importance",p.decide(f,s,DecisionTestData.settings,now).reasonCode);assertEquals(1,scored)
    }
    @Test fun malformedInputsExceptionsAndNotReadyFailOpen() {
        val p=DecisionTestData.pipeline()
        val cases=listOf(p.decide(f.copy(keyHash=""),s,DecisionTestData.settings,now),
            p.decide(f,s,PolicySettings(mode=PolicyMode.LIVE,kappaDPerHour=Double.NaN),now),
            p.decide(f,s.copy(ts=now-120001),DecisionTestData.settings,now),
            p.decide(f,s.copy(confidence=Double.NaN),DecisionTestData.settings,now),
            DecisionPipeline(LossPredictor{_,_,_,_->throw IllegalStateException("SYNTHETIC_CANARY")},ImportanceProvider{.2}).decide(f,s,DecisionTestData.settings,now),
            DecisionPipeline(LossPredictor{_,_,_,_->DecisionTestData.prediction},ImportanceProvider{throw IllegalStateException("SYNTHETIC_CANARY")}).decide(f,s,DecisionTestData.settings,now),
            DecisionTestData.pipeline(p=LossPrediction.NotReady("opaque-canary")).decide(f,s,DecisionTestData.settings,now))
        for(d in cases){assertEquals(Action.ALLOW,d.action);assertTrue(d.failedOpen);assertFalse(d.toString().contains("CANARY"));assertFalse(d.toString().contains("opaque-canary"))}
    }
    @Test fun importanceAndEvidenceBoundariesAndModes() {
        assertEquals("high_importance",DecisionTestData.pipeline(.85).decide(f,s,DecisionTestData.settings,now).reasonCode)
        val weak=DecisionTestData.prediction.copy(evidenceStrength=.1)
        assertEquals("low_evidence",DecisionTestData.pipeline(.50001,weak).decide(f,s,DecisionTestData.settings,now).reasonCode)
        assertNotEquals("low_evidence",DecisionTestData.pipeline(.5,weak).decide(f,s,DecisionTestData.settings,now).reasonCode)
        val p=DecisionTestData.pipeline()
        val live=p.decide(f,s,PolicySettings(mode=PolicyMode.LIVE),now)
        assertEquals(live,p.decide(f,s,PolicySettings(mode=PolicyMode.DRY_RUN),now))
        assertEquals(live,p.decide(f,s,PolicySettings(mode=PolicyMode.LIVE),now));assertNotNull(live.holdMinutes)
        assertFalse(live.explanation.contains(f.keyHash));assertFalse(live.explanation.contains(f.senderHash!!))
        assertEquals(Recheck.Proceed,p.recheck(f,live,DecisionTestData.settings,now))
    }
    @Test fun realEngineColdAndUnloadedStoresUseExplicitTime() {
        val cold=DecisionPipeline(TwinEngineLossPredictor(DecisionTestData.store()),ModelImportanceProvider(ImportanceModel()))
        val result=cold.decide(f,s,DecisionTestData.settings,now)
        assertFalse(result.failedOpen);assertNotNull(result.evidenceStrength);assertTrue(result.evidenceStrength!!<.4)
        assertEquals("low evidence",result.provenance)
        val unloaded=DecisionPipeline(TwinEngineLossPredictor(DecisionTestData.store(false)),ImportanceProvider{.2}).decide(f,s,DecisionTestData.settings,now)
        assertTrue(unloaded.failedOpen);assertEquals("not_ready",unloaded.reasonCode)
    }
    @Test fun explorationEligibilityIdempotenceRateAndSupport() {
        val policy=ExplorationPolicy();val on=PolicySettings(mode=PolicyMode.LIVE,explorationEnabled=true,epsilon=.2,seed=42)
        fun choose(settings:PolicySettings=on,facts:NotificationFacts=f,i:Double=.2,delta:Double=10.0,action:Action=Action.DIGEST,failed:Boolean=false,never:Boolean=false)=policy.choose(action,facts,settings,i,delta,failed,never)
        val first=choose();repeat(1000){assertEquals(first,choose())}
        val excluded=listOf(choose(PolicySettings()),choose(PolicySettings(mode=PolicyMode.LIVE,strictSession=true,explorationEnabled=true)),
            choose(PolicySettings(mode=PolicyMode.LIVE,killSwitch=true,explorationEnabled=true)),choose(facts=f.copy(isVip=true)),choose(i=.4),choose(delta=0.0),choose(action=Action.ALLOW),choose(failed=true),choose(never=true),choose(PolicySettings(mode=PolicyMode.LIVE)))
        excluded.forEach{assertFalse(it.explored);assertEquals(1.0,it.propensity,0.0);assertEquals(1,it.eligibleActions.size)}
        var count=0
        repeat(10000){val c=choose(facts=f.copy(keyHash="synthetic-$it"));if(c.explored)count++;assertEquals(if(c.explored).2 else .8,c.propensity,0.0);assertEquals(setOf(Action.ALLOW,Action.DIGEST),c.eligibleActions)}
        assertEquals(.2,count/10000.0,4*sqrt(.2*.8/10000))
        for(eps in listOf(0.0,1.0)){val c=choose(PolicySettings(mode=PolicyMode.LIVE,explorationEnabled=true,epsilon=eps));assertEquals(1,c.eligibleActions.size);assertEquals(1.0,c.propensity,0.0)}
    }
    @Test fun strictSessionDisablesExploration() {
        val p=DecisionTestData.pipeline(.2)
        val d=p.decide(f,s,PolicySettings(mode=PolicyMode.LIVE,strictSession=true,explorationEnabled=true,epsilon=1.0),now)
        assertFalse(d.explored);assertEquals(1.0,d.propensity,0.0)
    }
}

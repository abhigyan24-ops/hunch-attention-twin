package com.dhyan.app.twin.decision

import com.dhyan.app.safety.*
import com.dhyan.app.twin.NotReadyReason
import com.dhyan.app.twin.SimOutcome
import com.dhyan.app.twin.model.*
import java.time.ZoneId

class DecisionPipeline(
    private val predictor:LossPredictor,private val importanceProvider:ImportanceProvider,
    private val holdTimeEstimator:HoldTimeEstimator=HoldTimeEstimator(),private val exploration:ExplorationPolicy=ExplorationPolicy(),
    private val explainer:Explainer=Explainer(),val diagnostics:DecisionDiagnostics=DecisionDiagnostics(),
    private val zone:ZoneId=ZoneId.of("UTC"),private val staleSnapshotMs:Long=ModelConfig().staleSnapshotMs
):DecisionProvider {
    private val neverHold=NeverHoldRules(diagnostics)
    private val rechecker=RecheckLogic(diagnostics,neverHold)
    override fun decide(facts:NotificationFacts,snapshot:ContextSnapshot,settings:PolicySettings,nowMs:Long):Decision =
        FailOpen.guard(facts,nowMs,diagnostics){evaluate(facts,snapshot,settings,nowMs)}.copy(policyVersion=POLICY_VERSION)
    private fun evaluate(f:NotificationFacts,s:ContextSnapshot,settings:PolicySettings,now:Long):Decision {
        fun allowed(reason:String,safety:String?=null,p:LossPrediction.Ready?=null):Decision=FailOpen.decision(f,now,reason).copy(
            failedOpen=false,safetyReason=safety,explanation=explainer.allow(reason,safety),predictedLossShowMin=p?.eShowMin,
            predictedLossSuppressMin=p?.eSuppMin,deltaLMean=p?.let{it.eShowMin-it.eSuppMin},deltaLP10=p?.deltaP10,
            deltaLP90=p?.deltaP90,evidenceStrength=p?.evidenceStrength,modelVersion=p?.modelVersion?:0,provenance=p?.provenance?:"low evidence")
        if(settings.mode==PolicyMode.OFF)return allowed("mode_off")
        if(settings.killSwitch)return allowed("kill_switch")
        if(!SafetyValidation.settingsValid(settings)||!SafetyValidation.factsValid(f,now)||!validSnapshot(s,now))return FailOpen.invalidInput(f,now,diagnostics)
        neverHold.check(f,settings)?.let{return allowed("never_hold",it)}
        if(f.appCat=="unknown" || f.notifCat=="other" || !NeverHoldRules.categoryKnown(f.notifCat))return FailOpen.invalidInput(f,now,diagnostics)
        val importance=importanceProvider.score(f)
        if(!importance.isFinite()||importance !in 0.0..1.0)return FailOpen.invalidInput(f,now,diagnostics)
        if(importance>=PolicyDefaults.I_MAX)return allowed("high_importance")
        if(staleSnapshotMs<0 || now-s.ts>staleSnapshotMs)return FailOpen.notReady(f,now,SimOutcome.NotReady(NotReadyReason.STALE_CONTEXT),diagnostics)
        val loss=predictor.predict(s,f,now,settings.seed)
        if(loss is LossPrediction.NotReady)return FailOpen.notReady(f,now,SimOutcome.NotReady(NotReadyReason.MODEL_NOT_LOADED),diagnostics)
        val p=loss as LossPrediction.Ready
        if(!validPrediction(p))return FailOpen.invalidInput(f,now,diagnostics)
        if(p.evidenceStrength<PolicyDefaults.CONF_MIN && importance>.5)return allowed("low_evidence",p=p)
        val times=holdTimeEstimator.estimate(s,settings,now,zone)
        val costs=CostFunction.compute(p.eShowMin,p.eSuppMin,importance,settings.kappaDPerHour,settings.strictSession,times.tauReleaseMin,times.tauDigestMin)
        val selected=ActionSelector.select(costs)
        val choice=exploration.choose(selected.action,f,settings,importance,p.eShowMin-p.eSuppMin)
        val reason=if(choice.explored)"explored" else "cost_selected"
        val hold=holdMinutesFor(choice.action,times.tauReleaseMin,times.tauDigestMin)
        val factors=if(hold!=null)explainer.factors(p,s,importance,costs.effectiveKappaDPerHour,hold)else emptyList()
        require(factors.all{it.contribution.isFinite()}) { "invalid_factor" }
        return Decision(choice.action,if(hold==null)explainer.allow(reason)else explainer.held(factors),factors,hold,choice.propensity,
            choice.eligibleActions,choice.explored,null,false,p.eShowMin,p.eSuppMin,p.eShowMin-p.eSuppMin,p.deltaP10,p.deltaP90,
            p.evidenceStrength,p.provenance,p.modelVersion,POLICY_VERSION,now,f.keyHash,f.postedTsMs,reason)
    }
    override fun recheck(facts:NotificationFacts,decision:Decision,settings:PolicySettings,nowMs:Long):Recheck=rechecker.recheck(facts,decision,settings,nowMs)
    private fun validSnapshot(s:ContextSnapshot,now:Long):Boolean = s.ts>0 && s.ts<=now && s.confidence.isFinite() && s.confidence in 0.0..1.0 &&
        s.minutesInState.isFinite() && s.minutesInState>=0 && s.recentInterrupts30m>=0 && s.hourBand in setOf("night","morning","afternoon","evening","late") &&
        s.fatigueBand in setOf("low","med","high") && (s.declaredSession?.let{it.plannedMin.isFinite()&&it.plannedMin>=0&&it.startedTs>=0&&it.startedTs<=now}?:true)
    private fun validPrediction(p:LossPrediction.Ready):Boolean = listOf(p.eShowMin,p.eSuppMin,p.deltaP10,p.deltaP90,p.evidenceStrength).all{it.isFinite()} &&
        p.eShowMin>=0 && p.eSuppMin>=0 && p.deltaP10<=p.deltaP90 && (p.pPickupMean==null||(p.pPickupMean.isFinite()&&p.pPickupMean in 0.0..1.0)) && p.evidenceStrength in 0.0..1.0 && p.modelVersion>=0 &&
        p.provenance in setOf("simulated","low evidence","learned","estimated","observed")
    companion object { const val POLICY_REVISION=1;const val POLICY_VERSION="decision-$DECISION_CONTRACT_VERSION-policy-$POLICY_REVISION" }
}

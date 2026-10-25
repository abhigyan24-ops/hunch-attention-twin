package com.dhyan.app.twin.counterfactual

import com.dhyan.app.twin.decision.*

data class ActuatedDecision(val decision:Decision,val status:ActuationStatus,val importance:Double,val kappaDPerHour:Double,val heldMinutes:Double,val strictSession:Boolean=false)
data class SavingsReport(val modelSavedMin:LabeledNumber,val delayCostMin:LabeledNumber,val estimatedSavedMin:LabeledNumber?)
object SavedMinutes {
    /** Apply DR only to confirmed, exploration-supported holds in matching identified buckets.
     * Return null rather than silently report a partial total when any confirmed row lacks support. */
    fun fromReports(records:List<ActuatedDecision>,reports:List<CounterfactualReport>,bucket:(Decision)->String):SavingsReport {
        val confirmed=records.filter{it.decision.action!=Action.ALLOW&&!it.decision.failedOpen&&it.status in setOf(ActuationStatus.HELD,ActuationStatus.SNOOZED,ActuationStatus.RELEASED)}
        val byBucket=reports.associateBy{it.bucketKey}
        var total=0.0;var supported=confirmed.isNotEmpty()
        for(row in confirmed){val d=row.decision;val report=byBucket[bucket(d)]
            if(!d.pShow.isFinite()||d.pShow<=0||d.pShow>=1||Action.ALLOW !in d.eligibleActions||d.action !in d.eligibleActions||report==null||report.status!=EstimateStatus.ESTIMATED||report.drDelta==null||report.drDelta.label!="estimated"){supported=false;continue}
            total+=report.drDelta.value
        }
        return summarize(records,if(supported)total else null)
    }
    fun summarize(records:List<ActuatedDecision>,estimatedSavedMin:Double?=null):SavingsReport {
        require(estimatedSavedMin==null||estimatedSavedMin.isFinite())
        val confirmed=records.filter{it.decision.action!=Action.ALLOW&&!it.decision.failedOpen&&it.status in setOf(ActuationStatus.HELD,ActuationStatus.SNOOZED,ActuationStatus.RELEASED)}
        require(confirmed.map{Triple(it.decision.keyHash,it.decision.postedTsMs,it.decision.decidedAtMs)}.distinct().size==confirmed.size){ "duplicate_decision" }
        require(confirmed.all{it.heldMinutes.isFinite()&&it.heldMinutes>=0&&it.importance.isFinite()&&it.importance in 0.0..1.0&&it.kappaDPerHour.isFinite()&&it.kappaDPerHour>0&&it.decision.deltaLMean?.isFinite()==true})
        val model=confirmed.sumOf{it.decision.deltaLMean!!}
        val delay=confirmed.sumOf{it.kappaDPerHour*(if(it.strictSession).5 else 1.0)*it.importance*it.importance*(it.heldMinutes/60.0)}
        require(model.isFinite()&&delay.isFinite())
        return SavingsReport(LabeledNumber(model,"simulated"),LabeledNumber(delay,"estimated"),estimatedSavedMin?.let{LabeledNumber(it,"estimated")})
    }
}

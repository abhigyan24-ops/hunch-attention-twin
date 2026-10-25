package com.dhyan.app.twin.counterfactual

enum class Arm { SHOW, SUPPRESS }
enum class EstimateStatus { MODEL_BASED_ONLY, ESTIMATED }
data class ExplorationRecord(val bucketKey:String,val arm:Arm,val pShow:Double,val yMin:Double,
    val censored:Boolean,val actuationFailed:Boolean,val predictedShowMin:Double,val predictedSuppressMin:Double,val eligible:Boolean)
data class FilterCounts(val censored:Int=0,val actuationFailed:Int=0,val notEligible:Int=0,val positivity:Int=0,val nonFinite:Int=0)
data class LabeledNumber(val value:Double,val label:String)
data class BootstrapInterval(val p10:Double,val p90:Double,val label:String="80% bootstrap interval")
data class OverlapDiagnostics(val essShow:Double,val essSuppress:Double,val minPShow:Double,val maxPShow:Double,val top5PercentWeightShare:Double)
data class Sensitivity(val cap:Double?,val ipwDelta:Double,val htDelta:Double,val drDelta:Double,val overlap:OverlapDiagnostics)
data class CounterfactualReport(val bucketKey:String,val status:EstimateStatus,val nTotal:Int,val nUsed:Int,val filters:FilterCounts,
    val modelMeanDelta:LabeledNumber?,val ipwDelta:LabeledNumber?,val htDelta:LabeledNumber?,val drDelta:LabeledNumber?,
    val intervals:Map<String,BootstrapInterval>,val overlap:OverlapDiagnostics?,val sensitivity:List<Sensitivity>,
    val observedMean:LabeledNumber?,val bootstrapReplications:Int,val statusReason:String)
internal fun weight(r:ExplorationRecord,cap:Double?=null):Double {
    val raw=1.0/(if(r.arm==Arm.SHOW)r.pShow else 1-r.pShow)
    return if(cap==null)raw else minOf(raw,cap)
}
internal fun valid(records:List<ExplorationRecord>,cap:Double?){
    require(records.isNotEmpty());require(cap==null||(cap.isFinite()&&cap>0))
    require(records.all{it.pShow.isFinite()&&it.pShow>0&&it.pShow<1&&it.yMin.isFinite()&&it.yMin>=0&&it.predictedShowMin.isFinite()&&it.predictedSuppressMin.isFinite()&&it.predictedShowMin>=0&&it.predictedSuppressMin>=0&&weight(it,cap).isFinite()})
}

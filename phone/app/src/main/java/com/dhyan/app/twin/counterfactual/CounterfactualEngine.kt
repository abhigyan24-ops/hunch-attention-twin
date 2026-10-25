package com.dhyan.app.twin.counterfactual

import com.dhyan.app.twin.decision.PolicyRng

class CounterfactualEngine(private val bootstrapB:Int=1000,private val seed:Long=1) {
    fun estimate(records:List<ExplorationRecord>,bucket:(ExplorationRecord)->String={it.bucketKey}):List<CounterfactualReport> =
        records.groupBy(bucket).toSortedMap().map{(key,rows)->
            var censored=0;var failed=0;var excluded=0;var positivity=0;var invalid=0
            val used=rows.filter{r->when{
                r.censored->{censored++;false};r.actuationFailed->{failed++;false};!r.eligible->{excluded++;false}
                !listOf(r.pShow,r.yMin,r.predictedShowMin,r.predictedSuppressMin).all{it.isFinite()}||r.yMin<0||r.predictedShowMin<0||r.predictedSuppressMin<0->{invalid++;false}
                r.pShow<=0||r.pShow>=1->{positivity++;false};!weight(r).isFinite()->{invalid++;false};else->true}}
            val filters=FilterCounts(censored,failed,excluded,positivity,invalid)
            val modelRows=rows.filter{it.predictedShowMin.isFinite()&&it.predictedSuppressMin.isFinite()&&it.predictedShowMin>=0&&it.predictedSuppressMin>=0}
            val model=if(modelRows.isEmpty())null else LabeledNumber(modelRows.sumOf{(it.predictedShowMin-it.predictedSuppressMin)/modelRows.size},"simulated")
            val sufficient=used.size>=20&&used.any{it.arm==Arm.SHOW}&&used.any{it.arm==Arm.SUPPRESS}
            if(!sufficient)CounterfactualReport(key,EstimateStatus.MODEL_BASED_ONLY,rows.size,used.size,filters,model,null,null,null,emptyMap(),null,emptyList(),null,0,
                if(used.size<20)"fewer_than_20"else"missing_sampled_arm")
            else {
                val overlap=Diagnostics.overlap(used);val bootstrap=Bootstrap.run(used,PolicyRng.notificationSeed(seed,key,0),bootstrapB)
                val sensitivity=listOf(null,20.0,10.0,5.0).map{cap->Sensitivity(cap,IpwEstimator.hajek(used,cap),IpwEstimator.horvitzThompson(used,cap),DoublyRobustEstimator.delta(used,cap),Diagnostics.overlap(used,cap))}
                CounterfactualReport(key,EstimateStatus.ESTIMATED,rows.size,used.size,filters,model,
                    LabeledNumber(IpwEstimator.hajek(used),"estimated"),LabeledNumber(IpwEstimator.horvitzThompson(used),"estimated"),
                    LabeledNumber(DoublyRobustEstimator.delta(used),"estimated"),bootstrap.intervals,overlap,sensitivity,
                    LabeledNumber(used.sumOf{it.yMin/used.size},"observed"),bootstrap.validReplications,"eligible_overlap")
            }
        }
}

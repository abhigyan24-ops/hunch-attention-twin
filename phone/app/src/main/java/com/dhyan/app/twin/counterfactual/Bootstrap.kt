package com.dhyan.app.twin.counterfactual

import com.dhyan.app.twin.decision.PolicyRng
import kotlin.math.floor

data class BootstrapResult(val intervals:Map<String,BootstrapInterval>,val validReplications:Int)
object Bootstrap {
    fun percentile(values:List<Double>,q:Double):Double {
        require(values.isNotEmpty()&&q in 0.0..1.0);val sorted=values.sorted();val at=q*(sorted.size-1);val lo=floor(at).toInt();val hi=minOf(lo+1,sorted.lastIndex)
        return sorted[lo]+(sorted[hi]-sorted[lo])*(at-lo)
    }
    fun run(records:List<ExplorationRecord>,seed:Long,b:Int=1000):BootstrapResult {
        require(b>0);valid(records,null);val rng=PolicyRng(seed)
        val ipw=mutableListOf<Double>();val ht=mutableListOf<Double>();val dr=mutableListOf<Double>()
        repeat(b){
            val sample=List(records.size){records[rng.nextInt(records.size)]}
            // Hajek is undefined for a bootstrap sample with one arm; report omissions honestly.
            if(sample.any{it.arm==Arm.SHOW}&&sample.any{it.arm==Arm.SUPPRESS}){
                ipw.add(IpwEstimator.hajek(sample));ht.add(IpwEstimator.horvitzThompson(sample));dr.add(DoublyRobustEstimator.delta(sample))
            }
        }
        if(ipw.isEmpty())return BootstrapResult(emptyMap(),0)
        return BootstrapResult(linkedMapOf("ipw" to ipw,"ht" to ht,"dr" to dr).mapValues{(_,v)->BootstrapInterval(percentile(v,.1),percentile(v,.9))},ipw.size)
    }
}

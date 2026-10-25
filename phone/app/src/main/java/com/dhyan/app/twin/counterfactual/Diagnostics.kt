package com.dhyan.app.twin.counterfactual

import kotlin.math.ceil

object Diagnostics {
    fun overlap(records:List<ExplorationRecord>,cap:Double?=null):OverlapDiagnostics {
        valid(records,cap)
        fun ess(arm:Arm):Double {val w=records.filter{it.arm==arm}.map{weight(it,cap)};if(w.isEmpty())return 0.0;val max=w.max();val scaled=w.map{it/max};val sum=scaled.sum();return sum*sum/scaled.sumOf{it*it}}
        val weights=records.map{weight(it,cap)}.sortedDescending()
        return OverlapDiagnostics(ess(Arm.SHOW),ess(Arm.SUPPRESS),records.minOf{it.pShow},records.maxOf{it.pShow},
            weights.take(ceil(weights.size*.05).toInt().coerceAtLeast(1)).sumOf{it/weights.first()}/weights.sumOf{it/weights.first()})
    }
}

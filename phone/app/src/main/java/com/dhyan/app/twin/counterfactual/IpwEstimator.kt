package com.dhyan.app.twin.counterfactual

object IpwEstimator {
    fun hajek(records:List<ExplorationRecord>,cap:Double?=null):Double {
        valid(records,cap);var sy=0.0;var sw=0.0;var uy=0.0;var uw=0.0
        for(r in records){val w=weight(r,cap);if(r.arm==Arm.SHOW){sy+=w*r.yMin;sw+=w}else{uy+=w*r.yMin;uw+=w}}
        require(sw>0&&uw>0){"missing_arm"};val result=sy/sw-uy/uw;require(result.isFinite()){ "numerical_overflow" };return result
    }
    fun horvitzThompson(records:List<ExplorationRecord>,cap:Double?=null):Double {
        valid(records,cap);val result=records.sumOf{(if(it.arm==Arm.SHOW)1 else -1)*(weight(it,cap)*it.yMin/records.size)};require(result.isFinite()){ "numerical_overflow" };return result
    }
}

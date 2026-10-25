package com.dhyan.app.twin.counterfactual

object DoublyRobustEstimator {
    fun delta(records:List<ExplorationRecord>,cap:Double?=null):Double {
        valid(records,cap)
        val result=records.sumOf{r->(r.predictedShowMin-r.predictedSuppressMin)/records.size+
            (if(r.arm==Arm.SHOW)weight(r,cap)*(r.yMin-r.predictedShowMin)else -weight(r,cap)*(r.yMin-r.predictedSuppressMin))/records.size}
        require(result.isFinite()){ "numerical_overflow" };return result
    }
}

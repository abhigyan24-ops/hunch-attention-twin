package com.dhyan.app.twin.counterfactual

import com.dhyan.app.twin.decision.*

data class RegretRecord(val action:Action,val costs:CostBreakdown,val explored:Boolean)
object Regret {
    fun modelRegretMin(record:RegretRecord):Double=record.costs.cost(record.action)-Action.entries.minOf{record.costs.cost(it)}
    fun explorationCostMin(records:List<RegretRecord>):Double=records.filter{it.explored}.sumOf{it.costs.jAllow-Action.entries.minOf{a->it.costs.cost(a)}}
}

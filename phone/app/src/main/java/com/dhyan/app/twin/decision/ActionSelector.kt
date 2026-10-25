package com.dhyan.app.twin.decision

import kotlin.math.max

data class ActionSelection(val action: Action, val costs: CostBreakdown, val marginMin: Double)
object ActionSelector {
    const val TIE_TOLERANCE_MIN = 1e-9
    fun select(costs: CostBreakdown): ActionSelection {
        val order = Action.entries // Frozen contract order is least intrusive first.
        require(order.all { costs.cost(it).isFinite() }) { "nonfinite_selection_cost" }
        val minimum = order.minOf { costs.cost(it) }
        // Compare to global minimum, avoiding non-transitive chained pairwise ties.
        val chosen = order.first { costs.cost(it) - minimum <= TIE_TOLERANCE_MIN }
        val runnerUp = order.filter { it != chosen }.minOf { costs.cost(it) }
        return ActionSelection(chosen,costs,max(0.0,runnerUp-costs.cost(chosen)))
    }
}

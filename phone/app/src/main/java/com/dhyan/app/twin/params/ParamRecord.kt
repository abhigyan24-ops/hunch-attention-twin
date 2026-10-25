package com.dhyan.app.twin.params

/**
 * A single persisted parameter row.
 *
 * Column semantics by kind (all numeric fields are Double so decayed counts can be fractional):
 *
 *   BETA:      a = decayed successes, b = decayed failures, n = decayed observation count
 *   LOGNORM:   n = count of ln(d) observations, mean = Welford running mean, m2 = Welford M2
 *   DIRICHLET: a = decayed count for ONE destination; key ends with "|dest=<destinationId>"
 *   GAMMA:     a = accumulated events, b = accumulated exposure hours, n = observation count
 *
 * Priors are NOT persisted. They live in [PriorTable].
 */
data class ParamRecord(
    val key: String,
    val kind: ParamKind,
    val a: Double,
    val b: Double,
    val n: Double,
    val mean: Double,
    val m2: Double,
    val updatedTs: Long
)

package com.dhyan.app.twin.simulator

/**
 * Summary statistics for a single simulation arm (SHOW or SUPPRESS).
 *
 * All durations are in focus-minutes.
 *
 * @param mean         Expected focus loss in minutes.
 * @param p10          10th percentile of focus loss.
 * @param p50          Median focus loss (50th percentile).
 * @param p90          90th percentile of focus loss.
 * @param pLossGt1Min  Strict probability P(L > 1.0 min).
 */
data class ArmStats(
    val mean: Double,
    val p10: Double,
    val p50: Double,
    val p90: Double,
    val pLossGt1Min: Double
)

package com.dhyan.app.twin.model

/**
 * Model-level configuration: S3 assumptions that are not in core-spec/default-params.json.
 * These live here (not in TwinConfig) because they are model architecture choices, not
 * user-tunable parameters.
 */
data class ModelConfig(
    // S3-1: Notification pickup latency prior
    val pickupLatencyMeanLog: Double = kotlin.math.ln(1.0),
    val pickupLatencySigma: Double = 0.9,

    // S3-2: Baseline hazard prior
    val baselineHazardShape: Double = 1.5,
    val baselineHazardRate: Double = 1.0,  // per hour

    // S3-6: urgencyBand thresholds
    val urgencyBandLow: Double = 0.33,
    val urgencyBandMed: Double = 0.66,

    // S3-8: Hazard shrinkage pseudo-exposure hours
    val hazardShrinkageKappaHours: Double = 5.0,

    // S3-10: Staleness threshold for context snapshot
    val staleSnapshotMs: Long = 120_000L,

    // S3-15: Cost truncation at the outcome window (off by default)
    val truncateCostsAtWindow: Boolean = false,

    // Minimum effective sample size above which evidence is considered non-trivial
    val minNEffForData: Double = 1.0
)

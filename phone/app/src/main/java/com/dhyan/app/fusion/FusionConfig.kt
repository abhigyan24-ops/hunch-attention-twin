package com.dhyan.app.fusion

/**
 * Constants governing multi-device context fusion (Milestone M1 & M2).
 * Spec: 8.2, 11.6, 25.9, 25.13.
 */
data class FusionConfig(
    /** Laptop data older than this threshold is considered stale and forced to OFFLINE. */
    val laptopStaleMs: Long = 15_000L,
    /** Confidence discount applied when relay connection is down. */
    val relayDownConfidenceDrop: Double = 0.2,
    /** Confidence discount applied when laptop data is stale. */
    val laptopStaleConfidenceDrop: Double = 0.3,
    /** Minimum duration phone must be active while laptop is active to trigger overlap. */
    val secondScreenDebounceMs: Long = 10_000L,
    /** Duration required to clear second-screen overlap after phone or laptop stops. */
    val secondScreenHysteresisMs: Long = 5_000L
)

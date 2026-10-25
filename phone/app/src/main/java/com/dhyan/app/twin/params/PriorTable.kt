package com.dhyan.app.twin.params

import kotlin.math.ln

/**
 * Population priors for the twin model (spec 8.9 and S3-1..S3-16).
 *
 * Priors are coded in Kotlin and NOT persisted, so they can be retuned without
 * corrupting stored evidence.
 *
 * All durations in minutes. Hazard rates in pickups per hour.
 */
object PriorTable {

    // ── Beta pickup priors ────────────────────────────────────────────────────

    /** Default flat prior for unknown notification classes. */
    val pickupDefault = Pair(1.0, 1.0)  // α, β → mean 0.5

    /** Social notifications while working (spec 8.9). */
    val pickupSocial  = Pair(3.0, 4.0)  // mean ≈ 0.43

    /** Message from known contact (spec 8.9, S3-13). */
    val pickupKnownContact = Pair(4.0, 3.0)  // mean ≈ 0.57

    /** Promotional notification (spec 8.9, S3-13). */
    val pickupPromo = Pair(1.0, 6.0)  // mean ≈ 0.14

    /**
     * Returns the (α0, β0) prior for a pickup stat given [appCat] and [notifCat].
     * Also applies the fatigue multiplier on prior odds (S3-12).
     *
     * Fatigue multiplier applies only to the population prior odds:
     *   odds_new = odds_prior * multiplier
     *   p_new = odds_new / (1 + odds_new)
     * We then convert back to (α0, β0) maintaining the total pseudo-count.
     */
    fun pickupPrior(appCat: String, notifCat: String?, fatigueBand: String): Pair<Double, Double> {
        val base = when {
            notifCat == "promo"   -> pickupPromo
            appCat == "communication" -> pickupKnownContact
            appCat in setOf("social", "news", "shopping", "video", "short_video") -> pickupSocial
            else -> pickupDefault
        }
        val multiplier = fatiguePickupOddsMultiplier(fatigueBand)
        if (multiplier == 1.0) return base
        val (alpha, beta) = base
        val totalCount = alpha + beta
        val originalOdds = alpha / beta
        val newOdds = originalOdds * multiplier
        val newAlpha = newOdds * totalCount / (1.0 + newOdds)
        val newBeta  = totalCount / (1.0 + newOdds)
        return Pair(newAlpha, newBeta)
    }

    /** Fatigue multiplier on prior pickup odds (spec 8.9, S3-12). */
    fun fatiguePickupOddsMultiplier(fatigueBand: String): Double = when (fatigueBand) {
        "low"  -> 1.0
        "med"  -> 1.2
        "high" -> 1.5
        else   -> 1.0
    }

    // ── Lognormal dwell priors ─────────────────────────────────────────────

    /** Dwell priors by (appCat, surface?) — returns (m0 = mean of ln(d), s0 = sigma). */
    fun dwellPrior(appCat: String, surface: String? = null): Pair<Double, Double> = when {
        appCat == "short_video" || surface == "shorts" -> Pair(ln(14.0), 0.9)
        appCat == "social"   -> Pair(ln(9.0),  0.8)
        appCat == "communication" || appCat == "messaging" -> Pair(ln(3.0), 0.6)
        appCat == "video"    -> Pair(ln(9.0),  0.8)  // fallback to social values for video
        else -> Pair(ln(8.0), 0.8)  // S3-3 fallback
    }

    // ── Lognormal resume delay priors ─────────────────────────────────────

    /** Resume delay priors by taskType — returns (m0, s0). */
    fun resumePrior(taskType: String): Pair<Double, Double> = when (taskType) {
        "coding"   -> Pair(ln(6.0), 0.6)
        "studying" -> Pair(ln(4.0), 0.6)
        else       -> Pair(ln(5.0), 0.6)  // S3-4 fallback
    }

    // ── Gamma baseline hazard prior ───────────────────────────────────────

    /** Prior for baseline self-initiated hazard (S3-2): Gamma(shape=1.5, rate=1.0/hour). */
    val baselineHazardShape = 1.5
    val baselineHazardRate  = 1.0  // per hour; mean = shape/rate = 1.5 pickups/hour

    // ── Lognormal pickup latency prior ────────────────────────────────────

    /** Pickup latency (time from notification arrival to user opening the phone, S3-1). */
    val pickupLatencyMeanLog = ln(1.0)  // ln(1 minute)
    val pickupLatencySigma   = 0.9

    // ── Prior pseudo-observation strength ─────────────────────────────────

    /** Lognormal prior pseudo-observation count (spec 8.4, n0 = 3). */
    const val LOGNORM_N0 = 3.0

    // ── Destinations (S3-5) ────────────────────────────────────────────────

    /** Canonical destination IDs: (appCat, surface) pairs used in Dirichlet. */
    val destinations = listOf(
        "social/feed",
        "short_video/shorts",
        "video/watch",
        "communication/chat",
        "news/feed",
        "shopping/other",
        "games/other"
    )

    /**
     * Base Dirichlet alpha for each destination: 1.0 for all, +3.0 on the notifying app's own.
     * @param notifyingAppCat  The appCat of the notification that triggered the episode.
     * @param notifyingSurface The surface of the notifying app (may be null).
     */
    fun dirichletAlphas(notifyingAppCat: String?, notifyingSurface: String?): DoubleArray {
        val notifyDest = "${notifyingAppCat ?: ""}/${notifyingSurface ?: "other"}"
        return DoubleArray(destinations.size) { i ->
            if (destinations[i] == notifyDest) 4.0 else 1.0
        }
    }
}

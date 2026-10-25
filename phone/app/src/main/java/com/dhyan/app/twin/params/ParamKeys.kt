package com.dhyan.app.twin.params

/**
 * Canonical, documented key builders for every statistic in the param store.
 *
 * Key format conventions:
 *   - Beta pickup:     "beta_pickup|task=<taskType>|hour=<hourBand>|fat=<fatigueBand>|nc=<notifClass>"
 *   - Beta parent:     "beta_pickup|nc=<notifClass>"
 *   - Beta grandparent: "beta_pickup|prior"
 *   - Lognormal dwell: "lognorm_dwell|cat=<appCat>|surf=<surface>"
 *   - Lognormal resume: "lognorm_resume|task=<taskType>"
 *   - Lognormal pickup latency: "lognorm_pickup_latency"
 *   - Dirichlet:       "<betaKey>|dest=<destinationId>"  (self or notif prefix)
 *   - Gamma hazard:    "gamma_hazard|task=<taskType>|hour=<hourBand>|fat=<fatigueBand>"
 *
 * The hierarchy is encoded in the key: resolvers walk from full → parent → grandparent → prior
 * by stripping key segments.
 */
object ParamKeys {

    // ── Beta pickup ───────────────────────────────────────────────────────────

    fun betaPickupFull(taskType: String, hourBand: String, fatigueBand: String, notifClass: String): String =
        "beta_pickup|task=$taskType|hour=$hourBand|fat=$fatigueBand|nc=$notifClass"

    fun betaPickupParent(notifClass: String): String =
        "beta_pickup|nc=$notifClass"

    fun betaPickupGrandparent(): String = "beta_pickup|prior"

    /** All three levels for a full bucket, from specific to general. */
    fun betaPickupChain(taskType: String, hourBand: String, fatigueBand: String, notifClass: String): List<String> = listOf(
        betaPickupFull(taskType, hourBand, fatigueBand, notifClass),
        betaPickupParent(notifClass),
        betaPickupGrandparent()
    )

    // ── Lognormal dwell ───────────────────────────────────────────────────────

    fun lognormDwellFull(appCat: String, surface: String): String =
        "lognorm_dwell|cat=$appCat|surf=$surface"

    fun lognormDwellParent(appCat: String): String =
        "lognorm_dwell|cat=$appCat"

    fun lognormDwellGrandparent(): String = "lognorm_dwell|prior"

    fun lognormDwellChain(appCat: String, surface: String): List<String> = listOf(
        lognormDwellFull(appCat, surface),
        lognormDwellParent(appCat),
        lognormDwellGrandparent()
    )

    // ── Lognormal resume ──────────────────────────────────────────────────────

    fun lognormResumeFull(taskType: String): String =
        "lognorm_resume|task=$taskType"

    fun lognormResumeParent(): String = "lognorm_resume|prior"

    fun lognormResumeChain(taskType: String): List<String> = listOf(
        lognormResumeFull(taskType),
        lognormResumeParent()
    )

    // ── Lognormal pickup latency ──────────────────────────────────────────────

    fun lognormPickupLatency(): String = "lognorm_pickup_latency"

    // ── Dirichlet self-initiated destinations ─────────────────────────────────

    fun dirichletSelfFull(taskType: String, hourBand: String, fatigueBand: String, destination: String): String =
        "dirichlet_self|task=$taskType|hour=$hourBand|fat=$fatigueBand|dest=$destination"

    fun dirichletSelfParent(taskType: String, destination: String): String =
        "dirichlet_self|task=$taskType|dest=$destination"

    fun dirichletSelfGrandparent(destination: String): String =
        "dirichlet_self|dest=$destination"

    // ── Dirichlet notification-triggered destinations ─────────────────────────

    fun dirichletNotifFull(taskType: String, hourBand: String, fatigueBand: String, notifClass: String, destination: String): String =
        "dirichlet_notif|task=$taskType|hour=$hourBand|fat=$fatigueBand|nc=$notifClass|dest=$destination"

    fun dirichletNotifParent(notifClass: String, destination: String): String =
        "dirichlet_notif|nc=$notifClass|dest=$destination"

    fun dirichletNotifGrandparent(destination: String): String =
        "dirichlet_notif|dest=$destination"

    // ── Gamma baseline hazard ─────────────────────────────────────────────────

    fun gammaHazardFull(taskType: String, hourBand: String, fatigueBand: String): String =
        "gamma_hazard|task=$taskType|hour=$hourBand|fat=$fatigueBand"

    fun gammaHazardParent(taskType: String): String =
        "gamma_hazard|task=$taskType"

    fun gammaHazardGrandparent(): String = "gamma_hazard|prior"

    fun gammaHazardChain(taskType: String, hourBand: String, fatigueBand: String): List<String> = listOf(
        gammaHazardFull(taskType, hourBand, fatigueBand),
        gammaHazardParent(taskType),
        gammaHazardGrandparent()
    )

    // ── Notification class key ────────────────────────────────────────────────

    /**
     * Encodes a notification class into a compact, collision-resistant string segment.
     * Format: "<appCat>|urg=<urgencyBand>|rep=<repeatBand>|vip=<0|1>"
     */
    fun notifClass(appCat: String, urgencyBand: String, repeatBand: String, isVip: Boolean): String =
        "$appCat|urg=$urgencyBand|rep=$repeatBand|vip=${if (isVip) 1 else 0}"
}

package com.dhyan.app.ingest

import android.content.Context
import java.security.MessageDigest

/**
 * App package → attention category mapper.
 *
 * Privacy: resolves categories only. Package names are never stored in events — only categories.
 * Unknown packages fall back to "other".
 */
object AppCategorizer {

    /** Canonical category strings (wire format). */
    object Cat {
        const val SOCIAL       = "social"
        const val COMMS        = "comms"
        const val PRODUCTIVITY = "productivity"
        const val ENTERTAINMENT= "entertainment"
        const val BROWSER      = "browser"
        const val NEWS         = "news"
        const val HEALTH       = "health"
        const val SHOPPING     = "shopping"
        const val FINANCE      = "finance"
        const val SYSTEM       = "system"
        const val OTHER        = "other"
    }

    /** Bundled prefix/package → category map. Extended at runtime via Room app_labels. */
    private val BUNDLED: Map<String, String> = mapOf(
        // Social
        "com.instagram.android"       to Cat.SOCIAL,
        "com.facebook.katana"         to Cat.SOCIAL,
        "com.twitter.android"         to Cat.SOCIAL,
        "com.reddit.frontpage"        to Cat.SOCIAL,
        "com.snapchat.android"        to Cat.SOCIAL,
        "com.zhiliaoapp.musically"    to Cat.SOCIAL, // TikTok
        "com.ss.android.ugc.trill"    to Cat.SOCIAL,
        "com.pinterest"               to Cat.SOCIAL,
        "com.linkedin.android"        to Cat.SOCIAL,

        // Comms / messaging
        "com.whatsapp"                to Cat.COMMS,
        "com.whatsapp.w4b"            to Cat.COMMS,
        "org.telegram.messenger"      to Cat.COMMS,
        "org.thoughtcrime.securesms"  to Cat.COMMS, // Signal
        "com.google.android.gm"       to Cat.COMMS, // Gmail
        "com.microsoft.teams"         to Cat.COMMS,
        "com.discord"                 to Cat.COMMS,
        "com.slack"                   to Cat.COMMS,
        "com.google.android.apps.messaging" to Cat.COMMS,
        "com.android.mms"             to Cat.COMMS,

        // Productivity
        "com.microsoft.office.word"   to Cat.PRODUCTIVITY,
        "com.microsoft.office.excel"  to Cat.PRODUCTIVITY,
        "com.microsoft.office.powerpoint" to Cat.PRODUCTIVITY,
        "com.google.android.apps.docs" to Cat.PRODUCTIVITY,
        "com.google.android.apps.sheets" to Cat.PRODUCTIVITY,
        "com.google.android.apps.slides" to Cat.PRODUCTIVITY,
        "com.todoist.android"         to Cat.PRODUCTIVITY,
        "com.ticktick.task"           to Cat.PRODUCTIVITY,
        "com.notion.android"          to Cat.PRODUCTIVITY,
        "com.evernote"                to Cat.PRODUCTIVITY,

        // Entertainment
        "com.google.android.youtube"  to Cat.ENTERTAINMENT,
        "com.netflix.mediaclient"     to Cat.ENTERTAINMENT,
        "com.spotify.music"           to Cat.ENTERTAINMENT,
        "com.amazon.avod.thirdpartyclient" to Cat.ENTERTAINMENT,
        "com.disney.disneyplus"       to Cat.ENTERTAINMENT,
        "com.twitch.android.app"      to Cat.ENTERTAINMENT,

        // Browser
        "com.android.chrome"          to Cat.BROWSER,
        "org.mozilla.firefox"         to Cat.BROWSER,
        "com.microsoft.emmx"          to Cat.BROWSER,
        "com.opera.browser"           to Cat.BROWSER,
        "com.brave.browser"           to Cat.BROWSER,

        // News
        "com.google.android.apps.magazines" to Cat.NEWS, // Google News
        "flipboard.app"               to Cat.NEWS,
        "com.inshorts.android"        to Cat.NEWS,

        // Health
        "com.google.android.apps.fitness" to Cat.HEALTH,
        "com.fitbit.FitbitMobile"     to Cat.HEALTH,

        // Shopping
        "com.amazon.mShop.android.shopping" to Cat.SHOPPING,
        "com.flipkart.android"        to Cat.SHOPPING,
        "in.myntra.android"           to Cat.SHOPPING,

        // Finance
        "net.one97.paytm"             to Cat.FINANCE,
        "com.phonepe.app"             to Cat.FINANCE,
        "com.google.android.apps.nbu.paisa.user" to Cat.FINANCE,

        // System
        "com.android.settings"        to Cat.SYSTEM,
        "com.android.systemui"        to Cat.SYSTEM,
        "com.oppo.launcher"           to Cat.SYSTEM,
        "com.coloros.launcher"        to Cat.SYSTEM,
    )

    /** Resolved at runtime from Room; keyed by package name. */
    @Volatile private var runtimeMap: Map<String, String> = emptyMap()

    /** Update the runtime map from Room (called at startup and on changes). */
    fun updateRuntimeMap(pkgToCategory: Map<String, String>) {
        runtimeMap = pkgToCategory
    }

    /**
     * Returns the category for [pkg].
     * Checks runtime map first (user corrections), then bundled map, then "other".
     */
    fun categorize(pkg: String): String =
        runtimeMap[pkg] ?: BUNDLED[pkg] ?: inferFromPrefix(pkg)

    private fun inferFromPrefix(pkg: String): String = when {
        pkg.startsWith("com.google.android.gm")   -> Cat.COMMS
        pkg.startsWith("com.microsoft")            -> Cat.PRODUCTIVITY
        pkg.startsWith("com.android")              -> Cat.SYSTEM
        pkg.startsWith("com.coloros")              -> Cat.SYSTEM
        pkg.startsWith("com.oppo")                 -> Cat.SYSTEM
        else                                       -> Cat.OTHER
    }

    /**
     * Produces a salted SHA-256 hash of a sender string (contact name, phone, account id).
     * Used to identify VIP senders without storing identifiable data.
     *
     * @param raw    Raw sender string (e.g. phone number or display name).
     * @param salt   Per-install random salt (stored in SettingsEntity).
     * @return 16-hex-char prefix of SHA-256(salt + "|" + raw).
     */
    fun senderHash(raw: String, salt: String): String {
        val input = "$salt|$raw"
        val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }.take(16)
    }
}

package com.dhyan.app.ingest

/**
 * Normalises a raw notification or usage event into Dhyan's schema event fields.
 *
 * Privacy enforcement point: this is where raw text is discarded.
 * - Notification titles, text, and big-text extras are NEVER written to any output.
 * - Sender strings are hashed via [AppCategorizer.senderHash] before leaving this function.
 * - Package names are resolved to categories; the package name itself is NOT stored.
 */
object Normalizer {

    data class NormalizedNotif(
        val ts: Long,
        val appCat: String,
        val notifCat: String,
        val senderHash: String?,   // SHA-256 prefix, null if no sender
        val surface: String,       // "notification_shade" for posted notifs
        val isMessagingStyle: Boolean
    )

    /**
     * Normalises a raw notification posting event.
     *
     * @param pkg          Package name of the posting app (never stored after categorization).
     * @param notifCategory Raw Android notification category string (can be null).
     * @param extras       Key-value pairs from notification extras. Only non-textual keys used.
     * @param senderRaw    Raw sender string from MessagingStyle (contact name or number). Hashed immediately.
     * @param salt         Per-install salt for hashing.
     * @param ts           Epoch ms of the event.
     * @param isMessaging  Whether the notification uses MessagingStyle.
     */
    fun normalizeNotif(
        pkg: String,
        notifCategory: String?,
        senderRaw: String?,
        salt: String,
        ts: Long,
        isMessaging: Boolean
    ): NormalizedNotif {
        val appCat = AppCategorizer.categorize(pkg)
        val notifCat = classifyAndroidCategory(notifCategory, appCat, isMessaging)
        val hash = senderRaw?.takeIf { it.isNotBlank() }?.let {
            AppCategorizer.senderHash(it, salt)
        }
        return NormalizedNotif(
            ts              = ts,
            appCat          = appCat,
            notifCat        = notifCat,
            senderHash      = hash,
            surface         = "notification_shade",
            isMessagingStyle= isMessaging
        )
    }

    /**
     * Maps Android's notification category constants to Dhyan's simpler wire strings.
     *
     * Wire values: msg | promo | alert | sys | social | other
     */
    private fun classifyAndroidCategory(
        androidCat: String?,
        appCat: String,
        isMessaging: Boolean
    ): String = when {
        isMessaging                                              -> "msg"
        androidCat == "msg" || androidCat == "email"           -> "msg"
        androidCat == "promo" || androidCat == "recommendation"-> "promo"
        androidCat == "alarm" || androidCat == "reminder"      -> "alert"
        androidCat == "sys" || androidCat == "service"
            || androidCat == "status"                          -> "sys"
        androidCat == "social"                                 -> "social"
        appCat == AppCategorizer.Cat.COMMS                     -> "msg"
        appCat == AppCategorizer.Cat.SOCIAL                    -> "social"
        appCat == AppCategorizer.Cat.SYSTEM                    -> "sys"
        else                                                   -> "other"
    }

    /** Classification of a usage window event into a Dhyan surface type. */
    fun surfaceForCat(appCat: String): String = when (appCat) {
        AppCategorizer.Cat.BROWSER      -> "browser"
        AppCategorizer.Cat.SOCIAL       -> "feed"
        AppCategorizer.Cat.ENTERTAINMENT -> "video"
        AppCategorizer.Cat.COMMS        -> "chat"
        AppCategorizer.Cat.PRODUCTIVITY -> "document"
        AppCategorizer.Cat.SYSTEM       -> "system"
        else                            -> "app"
    }
}

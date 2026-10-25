package com.dhyan.app.collectors

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.dhyan.app.ingest.AppCategorizer
import com.dhyan.app.ingest.ClockOffset
import com.dhyan.app.ingest.Normalizer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Notification Listener Service — the primary notification data source.
 *
 * PRIVACY CONTRACT:
 * - This service NEVER reads or stores notification titles, text, bigText, or body.
 * - Only the notification category (from Android's category field), app package (resolved to
 *   category then discarded), sender hash (from MessagingStyle only, salted SHA-256),
 *   and a boolean isVip flag are used.
 * - The package name is resolved to a category string via [AppCategorizer] immediately;
 *   the package name itself is not written to any event or database.
 *
 * Decision flow:
 * 1. [onNotificationPosted] fires on the binder thread.
 * 2. Normalizer strips all text, categorises the app, hashes sender.
 * 3. Event is handed to an in-memory event bus (or directly to the IngestPipeline).
 * 4. Twin Engine (Stage 5) decides allow/delay/digest/silent.
 * 5. Actuation (Stage 6) carries out the decision.
 *
 * For MVP / Stage 2, this service only emits normalised events to the event bus.
 * Decision and actuation wiring happens in Stages 5–6.
 */
class NotifCollector : NotificationListenerService() {

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    /** Salt for sender hashing — loaded from Settings at startup (Stage 4). */
    @Volatile var senderSalt: String = "default-salt-replace-in-stage4"

    /**
     * Listener registry — Stage 4 wires in the real ingest pipeline.
     * Functions receive (appCat, notifCat, senderHash, isVip, ts).
     */
    var onEvent: ((appCat: String, notifCat: String, senderHash: String?, isVip: Boolean, ts: Long) -> Unit)? = null

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn ?: return
        // Skip our own notifications (foreground service notif)
        if (sbn.packageName == packageName) return

        val ts = ClockOffset.adjustedNow()
        val pkg = sbn.packageName ?: return

        // --- Extract only structural metadata, never text ---
        val notification = sbn.notification ?: return
        val androidCat = notification.category         // e.g. "msg", "promo", null
        val isMessaging = hasMessagingStyle(notification)
        val senderRaw = extractSenderRaw(notification) // null if not MessagingStyle

        // Normalise: strips text, resolves category, hashes sender
        val norm = Normalizer.normalizeNotif(
            pkg           = pkg,
            notifCategory = androidCat,
            senderRaw     = senderRaw,
            salt          = senderSalt,
            ts            = ts,
            isMessaging   = isMessaging
        )

        // Check VIP status from hashed set (Stage 4 wires the VIP DAO lookup)
        val isVip = checkVip(norm.senderHash)

        scope.launch {
            onEvent?.invoke(norm.appCat, norm.notifCat, norm.senderHash, isVip, norm.ts)
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        // Used in Stage 5 to detect quick-dismiss (user picked up = importance signal)
        // No-op in Stage 2
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        // Request rebind so we stay connected on ColorOS
        requestRebind(ComponentName(this, NotifCollector::class.java))
    }

    // ── Private helpers ────────────────────────────────────────────────────

    /**
     * Returns true if the notification uses MessagingStyle.
     * This tells us it's a real message (SMS, WhatsApp, etc.) rather than a broadcast.
     * We do NOT read the actual message text.
     */
    private fun hasMessagingStyle(notification: Notification): Boolean {
        val extras = notification.extras ?: return false
        return extras.containsKey(Notification.EXTRA_MESSAGES)
            || extras.containsKey(Notification.EXTRA_MESSAGING_PERSON)
    }

    /**
     * Extracts the sender's display name or ID from MessagingStyle extras.
     * This string is immediately hashed in [Normalizer.normalizeNotif] and discarded.
     * Returns null if not a MessagingStyle notification or no sender available.
     */
    private fun extractSenderRaw(notification: Notification): String? {
        val extras = notification.extras ?: return null
        // MessagingStyle sender is in EXTRA_MESSAGING_PERSON (API 28+)
        val person = extras.getParcelable<android.app.Person>(Notification.EXTRA_MESSAGING_PERSON)
        if (person != null) {
            return person.key ?: person.name?.toString()
        }
        // Fallback: EXTRA_SELF_DISPLAY_NAME from MessagingStyle (generic sender name)
        val selfName = extras.getCharSequence(Notification.EXTRA_SELF_DISPLAY_NAME)
        return selfName?.toString()
    }

    /**
     * Checks if the hashed sender is in the VIP set.
     * Stage 4 replaces this stub with an actual Room DAO lookup via in-memory VIP set.
     */
    private fun checkVip(senderHash: String?): Boolean {
        senderHash ?: return false
        return vipHashSet.contains(senderHash)
    }

    companion object {
        /** In-memory VIP hash set. Stage 4 populates this from the VIP DAO at startup. */
        @Volatile var vipHashSet: Set<String> = emptySet()

        /**
         * Checks if the notification listener permission is granted.
         * Required before starting collectors.
         */
        fun isEnabled(context: Context): Boolean {
            val flat = android.provider.Settings.Secure.getString(
                context.contentResolver,
                "enabled_notification_listeners"
            )
            return flat?.contains(context.packageName) == true
        }
    }
}

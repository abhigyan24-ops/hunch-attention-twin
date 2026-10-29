package com.dhyan.app.collectors

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.dhyan.app.actuator.ActuationCoordinator
import com.dhyan.app.actuator.AndroidNotificationGateway
import com.dhyan.app.data.RoomDecisionLogStore
import com.dhyan.app.ingest.AppCategorizer
import com.dhyan.app.ingest.ClockOffset
import com.dhyan.app.ingest.EpisodeRuntime
import com.dhyan.app.ingest.Normalizer
import com.dhyan.app.ingest.NotifExtractor
import com.dhyan.app.twin.decision.Action
import com.dhyan.app.twin.decision.DecisionProvider
import com.dhyan.app.twin.decision.DecisionRecordFactory
import com.dhyan.app.twin.decision.ImportanceProvider
import com.dhyan.app.twin.decision.NotificationFacts
import com.dhyan.app.twin.decision.PolicyMode
import com.dhyan.app.twin.model.ContextSnapshot
import com.dhyan.app.twin.model.FocusState
import com.dhyan.app.twin.model.LaptopState
import com.dhyan.app.twin.model.PhoneState
import com.dhyan.app.twin.model.TaskSource
import com.dhyan.app.twin.model.TaskType
import com.dhyan.app.ui.model.LiveContextHolder
import com.dhyan.app.ui.model.PolicySettingsHolder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.security.MessageDigest

/**
 * Notification Listener Service — the primary notification data source.
 *
 * PRIVACY CONTRACT:
 * - This service NEVER persists notification titles, text, or body in plaintext.
 * - Only the notification category, app package (resolved to category then discarded),
 *   sender hash (from MessagingStyle only, salted SHA-256), and VIP flag are stored.
 * - In M6, [NotifExtractor] extracts transient in-memory booleans (isOtpLike, hasEmergencyKeyword)
 *   and an ephemeral holdPayload passed directly to KeystoreHeldTextCipher.
 */
class NotifCollector : NotificationListenerService() {

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    /** Salt for sender hashing — loaded from Settings at startup. */
    @Volatile var senderSalt: String = "default-salt-replace-in-stage4"

    /**
     * Listener registry — wires in the real ingest pipeline.
     * Functions receive (appCat, notifCat, senderHash, isVip, ts).
     */
    var onEvent: ((appCat: String, notifCat: String, senderHash: String?, isVip: Boolean, ts: Long) -> Unit)? = null

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn ?: return
        // Skip our own notifications (foreground service notif, digest notifs)
        if (sbn.packageName == packageName) return

        val ts = ClockOffset.adjustedNow()
        val pkg = sbn.packageName ?: return

        val notification = sbn.notification ?: return
        val androidCat = notification.category
        val isMessaging = hasMessagingStyle(notification)
        val senderRaw = extractSenderRaw(notification)

        val norm = Normalizer.normalizeNotif(
            pkg = pkg,
            notifCategory = androidCat,
            senderRaw = senderRaw,
            salt = senderSalt,
            ts = ts,
            isMessaging = isMessaging
        )

        val isVip = checkVip(norm.senderHash)

        // Ephemeral text-derived safety flags and transient hold payload (D56-1, D6-1)
        val extracted = NotifExtractor.extract(notification)

        // Notify telemetry / ingest listeners
        scope.launch {
            onEvent?.invoke(norm.appCat, norm.notifCat, norm.senderHash, isVip, norm.ts)
            EpisodeRuntime.instance?.onNotificationPosted(
                key = sbn.key ?: "",
                appCat = norm.appCat,
                notifCat = norm.notifCat,
                senderHash = norm.senderHash,
                isVip = isVip,
                urgencyScore = 0.0,
                ts = norm.ts
            )
        }

        // Decision and Actuation pipeline (Stage 5 + Stage 6 integration)
        scope.launch(Dispatchers.Default) {
            processDecisionAndActuation(sbn, pkg, norm, isVip, extracted, ts)
        }
    }

    private suspend fun processDecisionAndActuation(
        sbn: StatusBarNotification,
        pkg: String,
        norm: Normalizer.NormalizedNotif,
        isVip: Boolean,
        extracted: NotifExtractor.ExtractedNotif,
        ts: Long
    ) {
        val provider = decisionProvider ?: return
        val logStore = decisionLogStore ?: return
        val keyHash = computeKeyHash(sbn, norm.senderHash)

        val facts = NotificationFacts(
            keyHash = keyHash,
            postedTsMs = sbn.postTime,
            pkg = pkg,
            appCat = norm.appCat,
            notifCat = norm.notifCat,
            senderHash = norm.senderHash,
            isVip = isVip,
            urgencyScore = 0.0,
            hasReplyAction = hasReplyAction(sbn.notification),
            isMessagingStyle = norm.isMessagingStyle,
            repeatCount5m = 0,
            isOtpLike = extracted.isOtpLike,
            hasEmergencyKeyword = extracted.hasEmergencyKeyword
        )

        val snapshot = LiveContextHolder.snapshot.value ?: fallbackSnapshot(ts)
        val settings = PolicySettingsHolder.get()

        // 1. Compute decision via Stage 5 pipeline (FailOpen guarded)
        val decision = provider.decide(facts, snapshot, settings, ts)

        // 2. Score importance for logging
        val importance = importanceProvider?.score(facts) ?: 0.5

        // 3. Log decision record (privacy preserved: no content)
        val record = DecisionRecordFactory.from(facts, decision, snapshot, settings, importance)
        logStore.append(record)

        // 4. Actuate if action is DELAY, DIGEST, or SILENT in LIVE mode
        if (decision.action in listOf(Action.DELAY, Action.DIGEST, Action.SILENT) && settings.mode == PolicyMode.LIVE) {
            val actCoordinator = coordinator ?: return
            val req = ActuationCoordinator.ActuateRequest(
                facts = facts,
                decision = decision,
                snapshot = snapshot,
                settings = settings,
                platformId = sbn.id,
                packageName = pkg,
                holdPayload = extracted.holdPayload
            )
            val result = actCoordinator.actuate(req)
            logStore.updateStatus(keyHash, sbn.postTime, result.status.name)
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        // Track removal event if needed
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        AndroidNotificationGateway.listenerService = this
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        AndroidNotificationGateway.listenerService = null
        requestRebind(ComponentName(this, NotifCollector::class.java))
    }

    // ── Private helpers ────────────────────────────────────────────────────

    private fun hasMessagingStyle(notification: Notification): Boolean {
        val extras = notification.extras ?: return false
        return extras.containsKey(Notification.EXTRA_MESSAGES)
            || extras.containsKey(Notification.EXTRA_MESSAGING_PERSON)
    }

    private fun extractSenderRaw(notification: Notification): String? {
        val extras = notification.extras ?: return null
        val person = extras.getParcelable<android.app.Person>(Notification.EXTRA_MESSAGING_PERSON)
        if (person != null) {
            return person.key ?: person.name?.toString()
        }
        val selfName = extras.getCharSequence(Notification.EXTRA_SELF_DISPLAY_NAME)
        return selfName?.toString()
    }

    private fun hasReplyAction(notification: Notification?): Boolean {
        val actions = notification?.actions ?: return false
        for (action in actions) {
            if (action?.remoteInputs?.isNotEmpty() == true) return true
        }
        return false
    }

    private fun computeKeyHash(sbn: StatusBarNotification, senderHash: String?): String {
        val raw = "${sbn.packageName}|${sbn.id}|${sbn.postTime}|${senderHash ?: ""}"
        val digest = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray(Charsets.UTF_8))
        return digest.take(8).joinToString("") { "%02x".format(it) }
    }

    private fun fallbackSnapshot(nowMs: Long) = ContextSnapshot(
        ts = nowMs,
        focusState = FocusState.WORKING,
        taskType = TaskType.UNKNOWN,
        taskSource = TaskSource.INFERRED,
        confidence = 0.5,
        hourBand = "afternoon",
        minutesInState = 0.0,
        fatigueBand = "low",
        recentInterrupts30m = 0,
        laptopState = LaptopState.OFFLINE,
        phoneState = PhoneState.IN_APP,
        secondScreenOverlap = false,
        declaredSession = null
    )

    private fun checkVip(senderHash: String?): Boolean {
        senderHash ?: return false
        return vipHashSet.contains(senderHash)
    }

    companion object {
        @Volatile var vipHashSet: Set<String> = emptySet()
        @Volatile var coordinator: ActuationCoordinator? = null
        @Volatile var decisionProvider: DecisionProvider? = null
        @Volatile var decisionLogStore: RoomDecisionLogStore? = null
        @Volatile var importanceProvider: ImportanceProvider? = null

        fun isEnabled(context: Context): Boolean {
            val flat = android.provider.Settings.Secure.getString(
                context.contentResolver,
                "enabled_notification_listeners"
            )
            return flat?.contains(context.packageName) == true
        }
    }
}

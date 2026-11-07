package com.dhyan.app.collectors

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.dhyan.app.actuator.ActuationCoordinator
import com.dhyan.app.actuator.AndroidNotificationGateway
import com.dhyan.app.actuator.FocusLeaseController
import com.dhyan.app.data.AppLabelDao
import com.dhyan.app.data.AppLabelEntity
import com.dhyan.app.data.HeldNotifDao
import com.dhyan.app.data.HeldNotifEntity
import com.dhyan.app.data.RoomDecisionLogStore
import com.dhyan.app.ingest.ClockOffset
import com.dhyan.app.ingest.EpisodeRuntime
import com.dhyan.app.ingest.Normalizer
import com.dhyan.app.ingest.NotifExtractor
import com.dhyan.app.safety.FailOpen
import com.dhyan.app.twin.decision.Action
import com.dhyan.app.twin.decision.ActuationStatus
import com.dhyan.app.twin.decision.Decision
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

        // 1. Ignore ongoing foreground notifications (e.g. media player, active timer)
        if (sbn.isOngoing) return

        val isTestNotif = sbn.notification?.channelId == TEST_CHANNEL_ID

        // 2. Ignore our own app's notifications (foreground service, digest cards), unless it's the test channel
        if (sbn.packageName == packageName && !isTestNotif) return

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

        // Dynamically record app label into Room app_labels for Simulate screen
        scope.launch(Dispatchers.IO) {
            appLabelDaoInstance?.let { dao ->
                val appLabel = runCatching {
                    packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
                }.getOrDefault(pkg.substringAfterLast('.'))
                dao.upsert(
                    AppLabelEntity(
                        pkg = pkg,
                        category = norm.appCat,
                        label = appLabel,
                        updatedMs = ts
                    )
                )
            }
        }

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

        // Decision and Actuation pipeline
        scope.launch(Dispatchers.Default) {
            processDecisionAndActuation(sbn, pkg, norm, isVip, extracted, ts, isTestNotif, androidCat)
        }
    }

    private suspend fun processDecisionAndActuation(
        sbn: StatusBarNotification,
        pkg: String,
        norm: Normalizer.NormalizedNotif,
        isVip: Boolean,
        extracted: NotifExtractor.ExtractedNotif,
        ts: Long,
        isTestNotif: Boolean = false,
        androidCat: String? = null
    ) {
        val provider = decisionProvider ?: return
        val logStore = decisionLogStore ?: return
        val keyHash = computeKeyHash(sbn, norm.senderHash)
        sbn.key?.let {
            AndroidNotificationGateway.platformKeys[keyHash] = it
            AndroidNotificationGateway.platformKeys[it] = it
        }

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

        // Absolute Safety / Never-Hold Rules:
        // Never suppress call (CATEGORY_CALL), calendar_alarm (CATEGORY_ALARM), otp (isOtpLike),
        // emergency keywords (hasEmergencyKeyword), or VIP senders.
        val isCallOrAlarm = androidCat == Notification.CATEGORY_CALL ||
            androidCat == Notification.CATEGORY_ALARM ||
            (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S && androidCat == Notification.CATEGORY_WORKOUT) ||
            androidCat == "call" || androidCat == "alarm"

        val isNeverSuppress = isCallOrAlarm || extracted.isOtpLike || extracted.hasEmergencyKeyword || isVip

        val isDeepFocus = runCatching { FocusLeaseController.getInstance(applicationContext).isDeepFocusActive.value }.getOrDefault(false) ||
            snapshot.focusState == FocusState.DEEP_FOCUS ||
            snapshot.declaredSession != null

        // 1. Compute decision
        val computedDecision = if (isNeverSuppress) {
            FailOpen.decision(facts, ts, if (isVip) "vip_sender" else "safety_never_hold").copy(
                failedOpen = false,
                safetyReason = if (isVip) "VIP sender" else "Critical safety classification",
                explanation = "Allowed immediately: high-priority essential alert."
            )
        } else if (isTestNotif) {
            FailOpen.decision(facts, ts, "test_interception").copy(
                action = Action.DELAY,
                failedOpen = false,
                holdMinutes = 15.0,
                deltaLMean = 4.5,
                explanation = "Test notification intercepted during active focus."
            )
        } else {
            provider.decide(facts, snapshot, settings, ts)
        }

        // 2. Score importance for logging
        val importance = importanceProvider?.score(facts) ?: 0.5

        // 3. Log decision record
        val record = DecisionRecordFactory.from(facts, computedDecision, snapshot, settings, importance)
        logStore.append(record)

        // 4. Interception and Actuation:
        // For any notification where decision is defer/batch/silent or during active focus lease/state:
        val shouldIntercept = !isNeverSuppress && (
            computedDecision.action in listOf(Action.DELAY, Action.DIGEST, Action.SILENT) || isDeepFocus
        )

        if (shouldIntercept) {
            val waitMinutes = computedDecision.holdMinutes ?: 15.0
            val waitMs = (waitMinutes * 60_000).toLong().coerceAtLeast(60_000L)
            val gw = gatewayInstance ?: AndroidNotificationGateway(applicationContext)

            // Step A: Immediately snooze the notification so it is physically removed from status bar/lock screen
            val realKey = sbn.key ?: keyHash
            gw.snooze(
                keyHash = realKey,
                platformId = sbn.id,
                packageName = pkg,
                snoozeDurationMs = waitMs
            )

            // Step B: Persist into local Room held_notifs table
            val heldDao = heldNotifDaoInstance
            val heldEntity = HeldNotifEntity(
                keyHash = keyHash,
                mechanism = "SNOOZE",
                state = ActuationStatus.HELD.name,
                appCat = norm.appCat,
                notifCat = norm.notifCat,
                senderHash = norm.senderHash,
                postedTsMs = sbn.postTime,
                heldSinceMs = ts,
                holdUntilMs = ts + waitMs,
                expiresAtMs = ts + (7 * 24 * 3600 * 1000L),
                ciphertext = null,
                iv = null,
                platformId = sbn.id,
                pkgHash = pkg.hashCode().toString(),
                decisionAction = computedDecision.action.name,
                propensity = computedDecision.propensity
            )
            heldDao?.insert(heldEntity)
            logStore.updateStatus(keyHash, sbn.postTime, ActuationStatus.HELD.name)

            // Step C: Send URGENT sync event to laptop
            val tauMean = computedDecision.deltaLMean ?: 4.5
            runCatching {
                com.dhyan.app.DhyanApplication.syncManager.sendUrgentHeldNotification(heldEntity, tauMean)
            }
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        sbn?.key?.let { AndroidNotificationGateway.platformKeys.values.remove(it) }
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
        const val TEST_CHANNEL_ID = "dhyan_interception_test"

        @Volatile var vipHashSet: Set<String> = emptySet()
        @Volatile var coordinator: ActuationCoordinator? = null
        @Volatile var decisionProvider: DecisionProvider? = null
        @Volatile var decisionLogStore: RoomDecisionLogStore? = null
        @Volatile var importanceProvider: ImportanceProvider? = null
        @Volatile var appLabelDaoInstance: AppLabelDao? = null
        @Volatile var heldNotifDaoInstance: HeldNotifDao? = null
        @Volatile var gatewayInstance: AndroidNotificationGateway? = null

        fun isEnabled(context: Context): Boolean {
            val flat = android.provider.Settings.Secure.getString(
                context.contentResolver,
                "enabled_notification_listeners"
            )
            return flat?.contains(context.packageName) == true
        }
    }
}

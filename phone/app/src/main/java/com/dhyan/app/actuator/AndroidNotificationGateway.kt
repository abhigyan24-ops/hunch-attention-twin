package com.dhyan.app.actuator

import android.app.NotificationManager
import android.content.Context

/**
 * Production implementation. The ONLY file in the codebase that may call
 * NotificationManager.cancel() or snooze platform APIs.
 *
 * Privacy/Architecture contract: check-actuator-contract.mjs enforces that
 * no other file in the codebase calls cancel/snooze on the platform.
 *
 * NOTE: Full snooze via NotificationListenerService.snoozeNotification() is
 * wired in M6 (after NotifCollector integration). Until then, both SNOOZE
 * and CANCEL_DIGEST mechanisms use NotificationManager.cancel().
 */
class AndroidNotificationGateway(private val context: Context) : NotificationGateway {

    private val nm: NotificationManager
        get() = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    override fun cancel(
        keyHash: String, platformId: Int, packageName: String, timeoutMs: Long
    ): NotificationGateway.Result = runCatching {
        nm.cancel(platformId)
        NotificationGateway.Result.Ok
    }.getOrElse { e -> NotificationGateway.Result.Failed(e.javaClass.simpleName) }

    override fun snooze(
        keyHash: String, platformId: Int, packageName: String,
        snoozeDurationMs: Long, timeoutMs: Long
    ): NotificationGateway.Result = runCatching {
        // M6: wire to NotifCollector.snoozeNotification(sbn.key, snoozeDurationMs)
        // Until then, cancel and re-post on release (same user-visible effect).
        nm.cancel(platformId)
        NotificationGateway.Result.Ok
    }.getOrElse { e -> NotificationGateway.Result.Failed(e.javaClass.simpleName) }
}

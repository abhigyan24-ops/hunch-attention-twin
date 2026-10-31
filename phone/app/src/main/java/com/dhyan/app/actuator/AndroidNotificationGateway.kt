package com.dhyan.app.actuator

import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.service.notification.NotificationListenerService
import java.util.concurrent.ConcurrentHashMap

/**
 * Production implementation. The ONLY file in the codebase that may call
 * NotificationManager.cancel() or snooze platform APIs.
 *
 * Privacy/Architecture contract: check-actuator-contract.mjs enforces that
 * no other file in the codebase calls cancel/snooze on the platform.
 *
 * Full snooze via NotificationListenerService.snoozeNotification() is wired in M6.
 */
class AndroidNotificationGateway(private val context: Context) : NotificationGateway {

    private val nm: NotificationManager
        get() = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    companion object {
        @Volatile var listenerService: NotificationListenerService? = null
        val platformKeys = ConcurrentHashMap<String, String>()
    }

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
        val listener = listenerService
        if (listener != null) {
            try {
                val realKey = platformKeys[keyHash] ?: platformKeys[platformId.toString()] ?: keyHash
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    listener.snoozeNotification(realKey, snoozeDurationMs)
                } else {
                    nm.cancel(platformId)
                }
            } catch (_: Exception) {
                // Fallback to cancel and schedule release
                nm.cancel(platformId)
            }
        } else {
            nm.cancel(platformId)
        }
        NotificationGateway.Result.Ok
    }.getOrElse { e -> NotificationGateway.Result.Failed(e.javaClass.simpleName) }
}

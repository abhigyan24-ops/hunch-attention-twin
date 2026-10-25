package com.dhyan.app.actuator

import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi

/**
 * All calls to [NotificationManager.cancelNotification] and [NotificationManager.snoozeNotification]
 * MUST go through this interface. No other file may cancel or snooze notifications.
 */
interface NotificationGateway {
    sealed interface Result {
        data object Ok : Result
        data class Failed(val reason: String) : Result
        /** Platform call succeeded but we could not confirm within the timeout. */
        data object Unknown : Result
    }

    /**
     * Cancel (remove) a notification. Returns [Result.Ok] if confirmed within [timeoutMs].
     * NEVER throws — any error returns [Result.Failed].
     */
    fun cancel(keyHash: String, platformId: Int, packageName: String, timeoutMs: Long = ActuatorConfig.CONFIRM_TIMEOUT_MS): Result

    /**
     * Snooze a notification (API 26+). Returns [Result.Ok] if confirmed.
     * Falls back to cancel when snooze is unavailable.
     * NEVER throws.
     */
    fun snooze(keyHash: String, platformId: Int, packageName: String, snoozeDurationMs: Long, timeoutMs: Long = ActuatorConfig.CONFIRM_TIMEOUT_MS): Result
}

/** In-process fake for unit tests. Records calls without touching the platform. */
class FakeNotificationGateway(
    private val cancelResult: NotificationGateway.Result = NotificationGateway.Result.Ok,
    private val snoozeResult: NotificationGateway.Result = NotificationGateway.Result.Ok
) : NotificationGateway {
    data class Call(val type: String, val keyHash: String, val platformId: Int)
    val calls = mutableListOf<Call>()

    override fun cancel(keyHash: String, platformId: Int, packageName: String, timeoutMs: Long): NotificationGateway.Result {
        calls.add(Call("cancel", keyHash, platformId))
        return cancelResult
    }

    override fun snooze(keyHash: String, platformId: Int, packageName: String, snoozeDurationMs: Long, timeoutMs: Long): NotificationGateway.Result {
        calls.add(Call("snooze", keyHash, platformId))
        return snoozeResult
    }
}

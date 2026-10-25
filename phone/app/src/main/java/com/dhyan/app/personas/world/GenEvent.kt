package com.dhyan.app.personas.world

import com.dhyan.app.personas.frozen
import java.util.UUID

/** Payload construction is typed; there is no arbitrary-key constructor. */
sealed class GenPayload(val type: String, values: Map<String, Any>) {
    val values: Map<String, Any> = frozen(values)
    class Notification(key: String, pkg: String, appCat: String, notifCat: String, senderId: String,
        isVip: Boolean, urgencyScore: Double, hasReplyAction: Boolean, repeatCount5m: Int, isMessagingStyle: Boolean) : GenPayload("notif_posted", linkedMapOf(
        "key" to key, "pkg" to pkg, "appCat" to appCat, "notifCat" to notifCat, "senderId" to senderId,
        "isVip" to isVip, "urgencyScore" to urgencyScore, "hasReplyAction" to hasReplyAction,
        "repeatCount5m" to repeatCount5m, "isMessagingStyle" to isMessagingStyle))
    class Removed(key: String, reason: String) : GenPayload("notif_removed", linkedMapOf("key" to key, "reason" to reason))
    class Foreground(pkg: String, cat: String, surface: String) : GenPayload("app_foreground", linkedMapOf("pkg" to pkg, "appCat" to cat, "surface" to surface))
    class Background(pkg: String, durationMs: Long) : GenPayload("app_background", linkedMapOf("pkg" to pkg, "durationMs" to durationMs))
    class ScreenOn : GenPayload("screen_on", emptyMap())
    class ScreenOff : GenPayload("screen_off", emptyMap())
    class Unlock : GenPayload("unlock", emptyMap())
    class Pickup(key: String? = null) : GenPayload("pickup", if (key == null) emptyMap() else linkedMapOf("triggeredByNotifKey" to key))
    class Window(app: String, cat: String, minutesInPrev: Double) : GenPayload("window_change", linkedMapOf("app" to app, "appCat" to cat, "minutesInPrev" to minutesInPrev))
    class IdleStart(seconds: Double) : GenPayload("idle_start", linkedMapOf("idleSec" to seconds))
    class IdleEnd(seconds: Double) : GenPayload("idle_end", linkedMapOf("idleSec" to seconds))
    class Resume(delayMs: Long) : GenPayload("resume_first_edit", linkedMapOf("delayMs" to delayMs))
}

data class GenEvent internal constructor(
    val id: String, val deviceId: String, val ts: Long, val ingestedTs: Long,
    val device: String, val seq: Long, val payload: GenPayload
) {
    val schemaVersion = "1.0.0"
    val source = "persona"
    val synthetic = true
    val type get() = payload.type
    fun envelope(): Map<String, Any> = linkedMapOf("schemaVersion" to schemaVersion, "id" to id,
        "deviceId" to deviceId, "device" to device, "seq" to seq, "source" to source,
        "type" to type, "ts" to ts, "ingestedTs" to ingestedTs, "synthetic" to synthetic, "payload" to payload.values)
}

internal fun deterministicId(a: Long, b: Long): String = UUID(
    (a and -61441L) or 16384L, (b and 4611686018427387903L) or Long.MIN_VALUE).toString()

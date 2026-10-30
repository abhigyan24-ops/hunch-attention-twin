package com.dhyan.app.sync.data

import com.dhyan.app.sync.session.RelayClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class CanonicalEventsExportTest {

    @Test
    fun exportAllCanonicalPhoneEventTypesToJsonl() {
        val deviceId = "phone-device-001"
        var seq = 1L
        val baseTs = 1729864000000L

        fun header(type: String, source: String, payload: Map<String, Any?>): Map<String, Any?> {
            return mapOf(
                "schemaVersion" to "1.1.0",
                "id" to java.util.UUID.randomUUID().toString(),
                "deviceId" to deviceId,
                "device" to "phone",
                "seq" to seq++,
                "source" to source,
                "type" to type,
                "ts" to (baseTs + seq * 1000L),
                "synthetic" to false,
                "payload" to payload
            )
        }

        val events = listOf(
            // 1. screen_on
            header("screen_on", "screen", emptyMap()),
            // 2. screen_off
            header("screen_off", "screen", emptyMap()),
            // 3. unlock
            header("unlock", "screen", emptyMap()),
            // 4. pickup
            header("pickup", "screen", mapOf("triggeredByNotifKey" to "notif_key_123")),
            // 5. app_foreground
            header("app_foreground", "usage", mapOf("pkg" to "com.android.chrome", "appCat" to "social", "surface" to "feed")),
            // 6. app_background
            header("app_background", "usage", mapOf("pkg" to "com.android.chrome", "durationMs" to 15000)),
            // 7. scroll_burst
            header("scroll_burst", "a11y", mapOf("pkg" to "com.google.android.youtube", "count" to 14, "windowSec" to 5.0, "surface" to "shorts")),
            // 8. surface_change
            header("surface_change", "a11y", mapOf("pkg" to "com.google.android.youtube", "surface" to "shorts")),
            // 9. notif_posted
            header("notif_posted", "notif", mapOf(
                "key" to "notif_001",
                "pkg" to "com.whatsapp",
                "appCat" to "communication",
                "notifCat" to "msg",
                "senderId" to "sender_hash_99",
                "isVip" to false,
                "urgencyScore" to 0.45,
                "hasReplyAction" to true,
                "repeatCount5m" to 0,
                "isMessagingStyle" to true
            )),
            // 10. notif_removed
            header("notif_removed", "notif", mapOf(
                "key" to "notif_001",
                "reason" to "dismissed"
            )),
            // 11. gap
            header("gap", "system", mapOf(
                "deviceFrom" to "phone",
                "deviceTo" to "phone",
                "reason" to "collector_restart"
            ))
        )

        assertEquals("Must export all 11 canonical phone event types", 11, events.size)

        // Write to build directory for validator
        val reportDir = File("build/reports").apply { mkdirs() }
        val outputFile = File(reportDir, "canonical-phone-events.jsonl")
        outputFile.bufferedWriter().use { writer ->
            events.forEach { event ->
                writer.write(RelayClient.serializeJson(event))
                writer.newLine()
            }
        }

        assertTrue("Generated JSONL must exist and be non-empty", outputFile.exists() && outputFile.length() > 0)
    }
}

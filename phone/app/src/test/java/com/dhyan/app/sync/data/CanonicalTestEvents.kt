package com.dhyan.app.sync.data

import com.dhyan.app.personas.json.JsonReader

object CanonicalTestEvents {
    fun event(seq: Long, type: String = "window_change", id: String = uuid(seq), device: String = "laptop", now: Long = 100_000L): Map<String, Any?> {
        @Suppress("UNCHECKED_CAST")
        val fixture = JsonReader.parse(requireNotNull(javaClass.classLoader?.getResourceAsStream("fixtures/core/valid/$type.json"))
            .bufferedReader().use { it.readText() }) as Map<String, Any?>
        return fixture + mapOf("seq" to seq, "id" to id, "deviceId" to "$device-installation", "device" to device, "ts" to now, "ingestedTs" to now)
    }
    fun uuid(n: Long) = "00000000-0000-4000-8000-${n.toString().padStart(12, '0')}"
}

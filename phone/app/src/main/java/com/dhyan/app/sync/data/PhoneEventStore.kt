package com.dhyan.app.sync.data

import androidx.room.withTransaction
import com.dhyan.app.data.DhyanDatabase
import com.dhyan.app.data.EventEntity
import com.dhyan.app.data.SettingsEntity
import com.dhyan.app.sync.session.RelayClient
import java.util.UUID

/** Identity allocation and insertion share a Room transaction; never re-identify historical rows. */
class PhoneEventStore(private val db: DhyanDatabase) {
    suspend fun deviceId(): String = db.withTransaction {
        db.settingsDao().get("sync_installation_id") ?: UUID.randomUUID().toString().also {
            db.settingsDao().set(SettingsEntity("sync_installation_id", it, System.currentTimeMillis()))
        }
    }

    suspend fun append(type: String, source: String, payload: Map<String, Any?>, ts: Long): Long = db.withTransaction {
        val device = deviceId()
        val settings = db.settingsDao()
        val previous = settings.get("sync_next_seq")?.toLongOrNull() ?: (db.eventDao().getHighestSeq(device) ?: 0L)
        val seq = Math.addExact(previous, 1L)
        val id = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val event = mapOf("schemaVersion" to "1.1.0", "id" to id, "seq" to seq, "deviceId" to device,
            "device" to "phone", "type" to type, "source" to source, "ts" to ts,
            "ingestedTs" to now, "synthetic" to false, "payload" to payload)
        require(WireSchemas.current.event(event)) { "schema_invalid" }
        db.eventDao().insert(EventEntity(ts = ts, kind = type, appCat = payload["appCat"] as? String ?: "unknown",
            surface = payload["surface"] as? String ?: "other", durationMin = 0.0, senderHash = null,
            notifCat = null, scrollCount = 0, extrasJson = RelayClient.serializeJson(mapOf("event" to event, "tsAdj" to ts)),
            eventId = id, seq = seq, deviceId = device))
        settings.set(SettingsEntity("sync_next_seq", seq.toString(), now))
        seq
    }
}

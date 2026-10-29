package com.dhyan.app.sync.data

import com.dhyan.app.data.EventDao
import com.dhyan.app.data.EventEntity
import com.dhyan.app.data.SettingsDao
import com.dhyan.app.data.SettingsEntity
import com.dhyan.app.ingest.ClockOffset
import com.dhyan.app.personas.json.JsonReader
import com.dhyan.app.sync.session.RelayClient
import com.dhyan.app.twin.model.ContextSnapshot
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.nio.charset.StandardCharsets

fun interface InboundEventListener {
    fun onInboundEvent(type: String, ts: Long, payload: Map<String, Any?>)
}

/** Production supplies one Room transaction covering checks, rows and peer binding. */
class SyncDataEngine(
    private val eventDao: EventDao,
    private val relayClient: RelayClient,
    private val localDeviceId: String = "phone",
    private val peerDeviceId: String? = null,
    private val timeProvider: () -> Long = System::currentTimeMillis,
    var inboundEventListener: InboundEventListener? = null,
    private val settingsDao: SettingsDao? = null,
    private val transaction: suspend (suspend () -> Long) -> Long = { it() },
    private val schemas: WireSchemas = WireSchemas.current
) {
    private val mutex = Mutex()
    private var boundPeer = peerDeviceId
    var highestSettledSeq = 0L
        private set
    var listenerFailures = 0L
        private set
    var lastExportedSeq = 0L
        private set

    suspend fun restore() {
        boundPeer = boundPeer ?: settingsDao?.get("sync_peer_device_id")
        highestSettledSeq = boundPeer?.let { eventDao.getHighestSeq(it) } ?: 0L
    }

    suspend fun exportOutboundBatch(afterSeq: Long, maxEvents: Int = 50): Boolean {
        val rows = eventDao.getOutboundAfterSeq(localDeviceId, afterSeq, maxEvents.coerceIn(1, 50))
        if (rows.isEmpty()) return false
        val events = rows.mapNotNull { row ->
            val stored = row.extrasJson?.let { runCatching { JsonReader.parse(it) as? Map<*, *> }.getOrNull() }
            val storedEvent = stored?.get("event") as? Map<*, *>
            if (storedEvent != null && storedEvent["device"] == "phone" && storedEvent["deviceId"] == localDeviceId &&
                storedEvent["id"] == row.eventId && integer(storedEvent["seq"]) == row.seq && schemas.event(storedEvent)) {
                storedEvent
            } else {
                // Historical mapping is not a wire-validation bypass. Keep these rows local.
                null
            }
        }
        if (events.isEmpty()) return false
        val json = RelayClient.serializeJson(mapOf("fromSeq" to events.first()["seq"], "events" to events))
        if (json.toByteArray(StandardCharsets.UTF_8).size > 32_000) return if (maxEvents > 1) exportOutboundBatch(afterSeq, maxEvents / 2) else false
        val sent = relayClient.sendEncrypted("events", json)
        if (sent) lastExportedSeq = integer(events.last()["seq"])!!
        return sent
    }

    suspend fun processInboundBatch(fromSeq: Long, rawEvents: List<Map<*, *>>): Long = mutex.withLock {
        require(fromSeq >= 0 && rawEvents.size <= 50) { "invalid_identity" }
        if (rawEvents.isEmpty()) return@withLock highestSettledSeq
        require(integer(rawEvents.first()["seq"]) == fromSeq) { "invalid_identity" }
        var previous = -1L
        var incomingPeer = boundPeer ?: settingsDao?.get("sync_peer_device_id")
        for (event in rawEvents) {
            val seq = integer(event["seq"])
            val deviceId = event["deviceId"] as? String
            val id = event["id"] as? String
            require(seq != null && seq >= 0 && seq > previous && id != null && UUID_PATTERN.matches(id) &&
                event["device"] == "laptop" && !deviceId.isNullOrBlank() && deviceId != localDeviceId &&
                (incomingPeer == null || incomingPeer == deviceId) && event["schemaVersion"] == "1.1.0") { "invalid_identity" }
            incomingPeer = deviceId
            previous = seq
        }
        val peer = requireNotNull(incomingPeer)
        val applied = mutableListOf<Pair<Map<*, *>, Long>>()
        val cursor = transaction {
            val durableCursor = eventDao.getHighestSeq(peer) ?: -1L
            val stagedIds = mutableSetOf<String>()
            val entities = mutableListOf<EventEntity>()
            for (event in rawEvents) {
                val seq = integer(event["seq"])!!
                val id = event["id"] as String
                val existing = eventDao.getOutboundAfterSeq(peer, seq - 1, 1).firstOrNull()?.takeIf { it.seq == seq }
                if (existing != null) {
                    require(existing.eventId == id) { "conflicting_identity" }
                    continue
                }
                require(seq > durableCursor) { "unsettled_hole" }
                val rawTs = integer(event["ts"]) ?: 0L
                val adjusted = ClockOffset.adjustIncomingTs(rawTs)
                val duplicate = !stagedIds.add(id) || eventDao.findByDeviceAndId(peer, id) != null
                val reason = when {
                    !schemas.event(event) -> "schema_invalid"
                    adjusted > timeProvider() + 300_000L -> "future_timestamp"
                    duplicate -> "duplicate"
                    else -> null
                }
                val payload = event["payload"] as? Map<*, *> ?: emptyMap<Any?, Any?>()
                val stored = if (reason == null) mapOf("event" to event, "tsAdj" to adjusted) else
                    mapOf("deviceId" to peer, "device" to "laptop", "seq" to seq, "id" to id, "reason" to reason)
                entities.add(EventEntity(ts = if (reason == null) rawTs else 0L,
                    kind = if (reason == null) event["type"] as String else "rejected",
                    appCat = if (reason == null) payload["appCat"] as? String ?: "unknown" else "system",
                    surface = if (reason == null) payload["surface"] as? String ?: "unknown" else "unknown",
                    durationMin = 0.0, senderHash = null, notifCat = null, scrollCount = 0,
                    extrasJson = RelayClient.serializeJson(stored), eventId = id, seq = seq,
                    deviceId = peer, settledStatus = if (reason == null) "valid" else "tombstone", schemaVersion = "1.1.0"))
                if (reason == null) applied.add(event to adjusted)
            }
            eventDao.insertAll(entities)
            settingsDao?.set(SettingsEntity("sync_peer_device_id", peer, timeProvider()))
            maxOf(durableCursor, previous)
        }
        boundPeer = peer
        highestSettledSeq = cursor
        relayClient.sendEncrypted("ack", """{"upToSeq":$cursor}""")
        for ((event, adjusted) in applied) {
            @Suppress("UNCHECKED_CAST") val payload = event["payload"] as Map<String, Any?>
            if (runCatching { inboundEventListener?.onInboundEvent(event["type"] as String, adjusted, payload) }.isFailure) listenerFailures++
        }
        cursor
    }

    fun sendStateSnapshot(snapshot: ContextSnapshot, heldCount: Int): Boolean {
        val declared = snapshot.declaredSession?.let {
            mapOf("task" to it.task.wire, "plannedMin" to it.plannedMin, "strict" to it.strict, "startedTs" to it.startedTs)
        }
        val body = mapOf("ts" to snapshot.ts, "focusState" to snapshot.focusState.wire,
            "phoneState" to snapshot.phoneState.wire, "confidence" to snapshot.confidence,
            "declared" to declared, "heldCount" to heldCount, "minutesInState" to snapshot.minutesInState)
        return schemas.valid("state.schema.json", body) && relayClient.sendEncrypted("state", RelayClient.serializeJson(body))
    }

    companion object {
        private val UUID_PATTERN = Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")
        fun integer(value: Any?): Long? = (value as? Number)?.toDouble()?.let {
            if (it.isFinite() && it % 1.0 == 0.0 && kotlin.math.abs(it) <= 9_007_199_254_740_991.0) it.toLong() else null
        }
    }
}

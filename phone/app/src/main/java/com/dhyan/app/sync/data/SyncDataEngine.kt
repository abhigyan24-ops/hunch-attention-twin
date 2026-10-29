package com.dhyan.app.sync.data

import com.dhyan.app.data.EventDao
import com.dhyan.app.data.EventEntity
import com.dhyan.app.ingest.ClockOffset
import com.dhyan.app.personas.json.JsonReader
import com.dhyan.app.sync.session.RelayClient
import com.dhyan.app.twin.model.ContextSnapshot
import com.dhyan.app.twin.model.DeclaredSession
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.nio.charset.StandardCharsets
import java.util.UUID

/**
 * Data flow engine for outbound event streaming, transactional inbound event
 * settlement with tombstones, and cross-device state synchronization (Milestone M5).
 * References: Contract sections 6, 7.1, 7.2, 10, and 16.
 */
class SyncDataEngine(
    private val eventDao: EventDao,
    private val relayClient: RelayClient,
    private val localDeviceId: String = "phone",
    private val peerDeviceId: String = "laptop",
    private val timeProvider: () -> Long = System::currentTimeMillis
) {
    private val settlementMutex = Mutex()
    var highestSettledSeq: Long = 0L
        private set

    /**
     * Exports outbound phone events after [afterSeq] in batches of up to 50 events and 32 KB.
     * Returns true if events were sent, false if nothing to export.
     */
    suspend fun exportOutboundBatch(afterSeq: Long, maxEvents: Int = 50): Boolean {
        val candidates = eventDao.getOutboundAfterSeq(localDeviceId, afterSeq, maxEvents)
        if (candidates.isEmpty()) return false

        val eventObjects = mutableListOf<Map<String, Any?>>()
        for (item in candidates) {
            val payloadMap = try {
                if (!item.extrasJson.isNullOrBlank()) {
                    JsonReader.parse(item.extrasJson) as? Map<*, *>
                } else null
            } catch (_: Exception) {
                null
            }

            val envelope = mutableMapOf<String, Any?>(
                "id" to (item.eventId ?: UUID.randomUUID().toString()),
                "ts" to item.ts,
                "device" to localDeviceId,
                "deviceId" to localDeviceId,
                "schemaVersion" to item.schemaVersion,
                "seq" to item.seq,
                "kind" to item.kind,
                "appCat" to item.appCat,
                "surface" to item.surface,
                "durationMin" to item.durationMin
            )
            if (payloadMap != null) {
                envelope["payload"] = payloadMap
            }
            eventObjects.add(envelope)
        }

        val fromSeq = candidates.first().seq
        val batchMap = mapOf(
            "fromSeq" to fromSeq,
            "events" to eventObjects
        )
        val batchJson = RelayClient.serializeJson(batchMap)

        // Ensure payload does not exceed 32 KB framing limit
        if (batchJson.toByteArray(StandardCharsets.UTF_8).size > 32_000 && candidates.size > 1) {
            // Recurse with smaller batch size
            return exportOutboundBatch(afterSeq, maxEvents / 2)
        }

        return relayClient.sendEncrypted("events", batchJson)
    }

    /**
     * Transactionally processes and settles an incoming event batch per Contract Section 16.
     * Throws IllegalArgumentException on invalid batch header or incompatible version.
     * Returns the highest settled sequence number ACKed.
     */
    suspend fun processInboundBatch(fromSeq: Long, rawEvents: List<Map<*, *>>): Long = settlementMutex.withLock {
        if (rawEvents.isEmpty()) return highestSettledSeq

        // Batch header verification
        var lastSeqSeen = fromSeq - 1
        for (event in rawEvents) {
            val id = event["id"] as? String
            val seq = (event["seq"] as? Number)?.toLong()
            val senderDevice = event["deviceId"] as? String ?: event["device"] as? String
            val schemaVer = event["schemaVersion"] as? String ?: "1.0.0"

            if (id.isNullOrBlank() || seq == null || senderDevice != peerDeviceId || !schemaVer.startsWith("1.")) {
                throw IllegalArgumentException("Invalid batch identity or incompatible schemaVersion")
            }
            if (seq <= lastSeqSeen) {
                throw IllegalArgumentException("Events in batch must be strictly monotonic")
            }
            lastSeqSeen = seq
        }

        val entitiesToPersist = mutableListOf<EventEntity>()
        val nowMs = timeProvider()

        for (event in rawEvents) {
            val id = event["id"] as String
            val seq = (event["seq"] as Number).toLong()
            val rawTs = (event["ts"] as? Number)?.toLong() ?: nowMs
            val tsAdj = ClockOffset.adjustIncomingTs(rawTs)
            val kind = event["kind"] as? String ?: "laptop_state"
            val appCat = event["appCat"] as? String ?: "unclassified"
            val surface = event["surface"] as? String ?: "main"
            val durationMin = (event["durationMin"] as? Number)?.toDouble() ?: 0.0

            // 1. PrivacyGuard inspection
            if (!PrivacyGuard.isClean(event)) {
                entitiesToPersist.add(createTombstone(id, seq, tsAdj, "schema_invalid"))
                continue
            }

            // 2. Future timestamp check (> 5 minutes in future)
            if (tsAdj > nowMs + 300_000L) {
                entitiesToPersist.add(createTombstone(id, seq, tsAdj, "future_timestamp"))
                continue
            }

            // 3. Deduplication check
            val existing = eventDao.findByDeviceAndId(peerDeviceId, id)
            if (existing != null) {
                entitiesToPersist.add(createTombstone(id, seq, tsAdj, "duplicate"))
                continue
            }

            // Valid event
            val payload = event["payload"]
            val extrasJson = if (payload != null) RelayClient.serializeJson(payload) else null

            entitiesToPersist.add(
                EventEntity(
                    ts = tsAdj,
                    kind = kind,
                    appCat = appCat,
                    surface = surface,
                    durationMin = durationMin,
                    senderHash = null,
                    notifCat = null,
                    scrollCount = 0,
                    extrasJson = extrasJson,
                    eventId = id,
                    seq = seq,
                    deviceId = peerDeviceId,
                    settledStatus = "valid",
                    schemaVersion = "1.1.0"
                )
            )
        }

        // Commit all records atomically to database before issuing cumulative ACK
        eventDao.insertAll(entitiesToPersist)

        val batchHighestSeq = (rawEvents.last()["seq"] as Number).toLong()
        if (batchHighestSeq > highestSettledSeq) {
            highestSettledSeq = batchHighestSeq
        }

        // Transmit durable cumulative ACK
        relayClient.sendEncrypted("ack", """{"upToSeq":$highestSettledSeq}""")
        return highestSettledSeq
    }

    /**
     * Emits a local gap event when peer is silent or connection breaks.
     */
    suspend fun emitGapEvent(reason: String) {
        val now = timeProvider()
        val gapEntity = EventEntity(
            ts = now,
            kind = "laptop_state",
            appCat = "system",
            surface = "gap",
            durationMin = 0.0,
            senderHash = null,
            notifCat = null,
            scrollCount = 0,
            extrasJson = """{"reason":"$reason"}""",
            eventId = UUID.randomUUID().toString(),
            seq = (eventDao.getHighestSeq(localDeviceId) ?: 0L) + 1L,
            deviceId = localDeviceId,
            settledStatus = "valid",
            schemaVersion = "1.1.0"
        )
        eventDao.insert(gapEntity)
    }

    /**
     * Sends periodic or state-change context snapshot to laptop peer.
     */
    fun sendStateSnapshot(snapshot: ContextSnapshot, heldCount: Int): Boolean {
        val declaredJson = snapshot.declaredSession?.let { declared ->
            """{"task":"${declared.task.wire}","plannedMin":${declared.plannedMin},"strict":${declared.strict},"startedTs":${declared.startedTs}}"""
        } ?: "null"

        val stateJson = """{"ts":${snapshot.ts},"focusState":"${snapshot.focusState.name.lowercase()}","phoneState":"${snapshot.phoneState.name.lowercase()}","confidence":"uncalibrated","declared":$declaredJson,"heldCount":$heldCount,"minutesInState":${snapshot.minutesInState}}"""
        return relayClient.sendEncrypted("state", stateJson)
    }

    private fun createTombstone(id: String, seq: Long, ts: Long, reason: String): EventEntity {
        return EventEntity(
            ts = ts,
            kind = "rejected",
            appCat = "system",
            surface = "tombstone",
            durationMin = 0.0,
            senderHash = null,
            notifCat = null,
            scrollCount = 0,
            extrasJson = """{"reason":"$reason"}""",
            eventId = id,
            seq = seq,
            deviceId = peerDeviceId,
            settledStatus = "tombstone",
            schemaVersion = "1.1.0"
        )
    }
}

package com.dhyan.app.sync.data

import com.dhyan.app.data.EventDao
import com.dhyan.app.data.EventEntity
import com.dhyan.app.ingest.ClockOffset
import com.dhyan.app.personas.json.JsonReader
import com.dhyan.app.sync.session.*
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class SyncDataEngineTest {
    class MemoryDao : EventDao {
        val rows = mutableListOf<EventEntity>()
        var failInsert = false
        override suspend fun insert(event: EventEntity): Long { rows.add(event); return rows.size.toLong() }
        override suspend fun insertAll(events: List<EventEntity>) { if (failInsert) error("storage_unavailable"); rows.addAll(events) }
        override suspend fun recentEvents(sinceMs: Long, limit: Int) = rows.filter { it.ts >= sinceMs }.take(limit)
        override suspend fun recentByKind(kind: String, sinceMs: Long, limit: Int) = rows.filter { it.kind == kind && it.ts >= sinceMs }.take(limit)
        override suspend fun purgeOlderThan(beforeMs: Long) = 0
        override suspend fun getOutboundAfterSeq(deviceId: String, afterSeq: Long, limit: Int) =
            rows.filter { it.deviceId == deviceId && it.seq > afterSeq }.sortedBy { it.seq }.take(limit)
        override suspend fun getHighestSeq(deviceId: String) = rows.filter { it.deviceId == deviceId }.maxOfOrNull { it.seq }
        override suspend fun findByDeviceAndId(deviceId: String, eventId: String) = rows.firstOrNull { it.deviceId == deviceId && it.eventId == eventId }
    }
    class Client : RelayClient(ByteArray(32), "aa".repeat(32), "ws://127.0.0.1/ws", object : SessionTransport {
        override fun connect(url: String, roomId: String, device: String, listener: SessionTransportListener) {}
        override fun send(text: String) = true
        override fun close(code: Int, reason: String) {}
    }, TestScope()) {
        val sent = mutableListOf<Pair<String, String>>()
        override fun sendEncrypted(type: String, bodyJson: String): Boolean { sent.add(type to bodyJson); return true }
    }
    @Before fun resetClock() { ClockOffset.reset() }

    @Test fun canonicalInstallationIdentityAndPartialSettlement() = runTest {
        val dao = MemoryDao(); val client = Client(); val delivered = mutableListOf<String>()
        val engine = SyncDataEngine(dao, client, timeProvider = { 100_000L },
            inboundEventListener = InboundEventListener { type, _, payload ->
                delivered.add(type); assertEquals("coding", payload["appCat"])
            })
        val valid = CanonicalTestEvents.event(1)
        val bad = CanonicalTestEvents.event(2) + ("payload" to mapOf("title" to "CANARY_SYNTHETIC"))
        val future = CanonicalTestEvents.event(3, now = 500_001L)
        assertEquals(3L, engine.processInboundBatch(1, listOf(valid, bad, future)))
        assertEquals(listOf("window_change"), delivered)
        assertEquals(listOf("valid", "tombstone", "tombstone"), dao.rows.map { it.settledStatus })
        assertFalse(dao.rows.joinToString { it.extrasJson.orEmpty() }.contains("CANARY_SYNTHETIC"))
        val marker = JsonReader.parse(dao.rows[1].extrasJson!!) as Map<*, *>
        assertEquals(setOf("deviceId", "device", "seq", "id", "reason"), marker.keys)
        assertEquals("schema_invalid", marker["reason"])
        assertEquals("ack", client.sent.last().first)
    }

    @Test fun replayRestartSameBatchIdsAndConflictingSequences() = runTest {
        val dao = MemoryDao(); val client = Client()
        val engine = SyncDataEngine(dao, client)
        val one = CanonicalTestEvents.event(1)
        val duplicate = CanonicalTestEvents.event(2, id = one["id"] as String)
        engine.processInboundBatch(1, listOf(one, duplicate))
        assertEquals("tombstone", dao.rows[1].settledStatus)
        engine.processInboundBatch(1, listOf(one, duplicate))
        assertEquals(2, dao.rows.size)
        val restarted = SyncDataEngine(dao, client, peerDeviceId = "laptop-installation")
        restarted.restore()
        assertEquals(2L, restarted.highestSettledSeq)
        val before = client.sent.size
        assertTrue(runCatching { restarted.processInboundBatch(1, listOf(one + ("id" to CanonicalTestEvents.uuid(99)))) }.isFailure)
        assertEquals(before, client.sent.size)
    }

    @Test fun invalidIdentityAndStorageFailureNeverAck() = runTest {
        val dao = MemoryDao(); val client = Client(); val engine = SyncDataEngine(dao, client)
        for (bad in listOf(CanonicalTestEvents.event(1) + ("id" to "bad"),
            CanonicalTestEvents.event(1) + ("schemaVersion" to "1.0.0"),
            CanonicalTestEvents.event(1) + ("seq" to 1.5))) {
            val result = runCatching { engine.processInboundBatch(1, listOf(bad)) }
            assertTrue("identity mutation accepted: id=${bad["id"]}, seq=${bad["seq"]}, schema=${bad["schemaVersion"]}", result.isFailure)
        }
        dao.failInsert = true
        assertTrue(runCatching { engine.processInboundBatch(1, listOf(CanonicalTestEvents.event(1))) }.isFailure)
        assertEquals(0L, engine.highestSettledSeq)
        assertTrue(client.sent.isEmpty()); assertTrue(dao.rows.isEmpty())
    }

    @Test fun canonicalExportPreservesIdentityAndOriginalTimestamp() = runTest {
        val dao = MemoryDao(); val client = Client()
        val event = CanonicalTestEvents.event(7, "screen_on", device = "phone")
        dao.insert(EventEntity(ts = 100_000, kind = "screen_on", appCat = "system", surface = "other",
            durationMin = 0.0, senderHash = null, notifCat = null, scrollCount = 0,
            extrasJson = RelayClient.serializeJson(mapOf("event" to event, "tsAdj" to 99_000)),
            eventId = event["id"] as String, seq = 7, deviceId = "phone-installation"))
        val engine = SyncDataEngine(dao, client, localDeviceId = "phone-installation")
        assertTrue(engine.exportOutboundBatch(0)); assertTrue(engine.exportOutboundBatch(0))
        assertEquals(client.sent[0], client.sent[1])
        val body = JsonReader.parse(client.sent[0].second) as Map<*, *>
        val output = (body["events"] as List<*>).first() as Map<*, *>
        assertTrue(WireSchemas.current.event(output))
        assertEquals(event["id"], output["id"])
        assertEquals(100_000L, (output["ts"] as Number).toLong())
    }


    @Test
    fun inboundLegacyFlatEventWithoutCanonicalEnvelopeMustBeRejectedAsSchemaInvalid() = runTest {
        val dao = MemoryDao()
        val client = Client()
        val engine = SyncDataEngine(dao, client, timeProvider = { 100_000L })

        // Non-canonical event: uses top-level 'kind' instead of canonical envelope (missing type, source, synthetic, payload)
        val legacyFlatEvent = mapOf(
            "schemaVersion" to "1.1.0",
            "id" to "00000000-0000-4000-8000-000000000001",
            "deviceId" to "laptop",
            "device" to "laptop",
            "seq" to 1L,
            "ts" to 100_000L,
            "kind" to "window_change",
            "appCat" to "coding",
            "surface" to "ide"
        )

        engine.processInboundBatch(1L, listOf(legacyFlatEvent))

        // Strict contract: Non-canonical events must NOT be settled as 'valid'.
        // They must be marked as 'tombstone' with reason 'schema_invalid'.
        assertEquals(1, dao.rows.size)
        assertEquals("tombstone", dao.rows[0].settledStatus)
        val marker = JsonReader.parse(dao.rows[0].extrasJson!!) as Map<*, *>
        assertEquals("schema_invalid", marker["reason"])
    }

}

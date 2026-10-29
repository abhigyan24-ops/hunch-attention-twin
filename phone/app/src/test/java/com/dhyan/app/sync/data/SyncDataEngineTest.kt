package com.dhyan.app.sync.data

import com.dhyan.app.data.EventDao
import com.dhyan.app.data.EventEntity
import com.dhyan.app.personas.json.JsonReader
import com.dhyan.app.sync.session.RelayClient
import com.dhyan.app.sync.session.SessionTransport
import com.dhyan.app.sync.session.SessionTransportListener
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class SyncDataEngineTest {

    private class MockEventDao : EventDao {
        val database = mutableListOf<EventEntity>()

        override suspend fun insert(event: EventEntity): Long {
            database.add(event)
            return database.size.toLong()
        }

        override suspend fun insertAll(events: List<EventEntity>) {
            database.addAll(events)
        }

        override suspend fun recentEvents(sinceMs: Long, limit: Int): List<EventEntity> =
            database.filter { it.ts >= sinceMs }.take(limit)

        override suspend fun recentByKind(kind: String, sinceMs: Long, limit: Int): List<EventEntity> =
            database.filter { it.kind == kind && it.ts >= sinceMs }.take(limit)

        override suspend fun purgeOlderThan(beforeMs: Long): Int {
            val before = database.size
            database.removeAll { it.ts < beforeMs }
            return before - database.size
        }

        override suspend fun getOutboundAfterSeq(deviceId: String, afterSeq: Long, limit: Int): List<EventEntity> =
            database.filter { it.deviceId == deviceId && it.seq > afterSeq }.take(limit)

        override suspend fun getHighestSeq(deviceId: String): Long? =
            database.filter { it.deviceId == deviceId }.maxOfOrNull { it.seq }

        override suspend fun findByDeviceAndId(deviceId: String, eventId: String): EventEntity? =
            database.find { it.deviceId == deviceId && it.eventId == eventId }
    }

    private class MockRelayTransport : SessionTransport {
        val sentPayloads = mutableListOf<Pair<String, String>>()
        override fun connect(url: String, roomId: String, device: String, listener: SessionTransportListener) {}
        override fun send(text: String): Boolean = true
        override fun close(code: Int, reason: String) {}
    }

    private class TestableRelayClient : RelayClient(
        pk = ByteArray(32) { 1 },
        sidHex = "aa".repeat(32),
        relayUrl = "ws://127.0.0.1",
        transport = MockRelayTransport(),
        scope = TestScope()
    ) {
        val sentFrames = mutableListOf<Pair<String, String>>()
        override fun sendEncrypted(type: String, bodyJson: String): Boolean {
            sentFrames.add(type to bodyJson)
            return true
        }
    }

    @Test
    fun testExportOutboundBatch() = runTest {
        val dao = MockEventDao()
        val client = TestableRelayClient()
        val engine = SyncDataEngine(eventDao = dao, relayClient = client)

        dao.insert(
            EventEntity(
                ts = 1000L,
                kind = "screen_on",
                appCat = "system",
                surface = "lockscreen",
                durationMin = 0.0,
                senderHash = null,
                notifCat = null,
                scrollCount = 0,
                extrasJson = null,
                eventId = "phone-ev-1",
                seq = 1L,
                deviceId = "phone"
            )
        )

        val exported = engine.exportOutboundBatch(afterSeq = 0L)
        assertTrue(exported)
        assertEquals(1, client.sentFrames.size)
        assertEquals("events", client.sentFrames[0].first)

        val batchJson = client.sentFrames[0].second
        val parsed = JsonReader.parse(batchJson) as Map<*, *>
        assertEquals(1L, (parsed["fromSeq"] as Number).toLong())
        val events = parsed["events"] as List<*>
        assertEquals(1, events.size)
        val ev1 = events[0] as Map<*, *>
        assertEquals("phone-ev-1", ev1["id"])
        assertEquals("phone", ev1["device"])
        assertEquals(1L, (ev1["seq"] as Number).toLong())
    }

    @Test
    fun testProcessInboundBatchValidAndTombstones() = runTest {
        val dao = MockEventDao()
        val client = TestableRelayClient()
        val now = 100_000L
        val engine = SyncDataEngine(
            eventDao = dao,
            relayClient = client,
            timeProvider = { now }
        )

        // Pre-populate an existing event to trigger duplicate detection
        dao.insert(
            EventEntity(
                ts = 50_000L,
                kind = "laptop_state",
                appCat = "code",
                surface = "editor",
                durationMin = 1.0,
                senderHash = null,
                notifCat = null,
                scrollCount = 0,
                extrasJson = null,
                eventId = "lap-dup",
                seq = 5L,
                deviceId = "laptop"
            )
        )

        val rawBatch = listOf(
            // 1. Valid event
            mapOf(
                "id" to "lap-1",
                "seq" to 10L,
                "ts" to now,
                "deviceId" to "laptop",
                "schemaVersion" to "1.1.0",
                "kind" to "laptop_state",
                "appCat" to "code"
            ),
            // 2. Privacy violation event (contains forbidden field "title")
            mapOf(
                "id" to "lap-2",
                "seq" to 11L,
                "ts" to now,
                "deviceId" to "laptop",
                "schemaVersion" to "1.1.0",
                "kind" to "laptop_state",
                "appCat" to "social",
                "payload" to mapOf("title" to "Secret Chat")
            ),
            // 3. Future timestamp event (> 5 min)
            mapOf(
                "id" to "lap-3",
                "seq" to 12L,
                "ts" to now + 400_000L,
                "deviceId" to "laptop",
                "schemaVersion" to "1.1.0",
                "kind" to "laptop_state",
                "appCat" to "code"
            ),
            // 4. Duplicate event
            mapOf(
                "id" to "lap-dup",
                "seq" to 13L,
                "ts" to now,
                "deviceId" to "laptop",
                "schemaVersion" to "1.1.0",
                "kind" to "laptop_state",
                "appCat" to "code"
            )
        )

        val acked = engine.processInboundBatch(fromSeq = 10L, rawEvents = rawBatch)
        assertEquals(13L, acked)

        // Verify ACK was transmitted
        assertEquals(1, client.sentFrames.size)
        assertEquals("ack", client.sentFrames[0].first)
        val ackBody = JsonReader.parse(client.sentFrames[0].second) as Map<*, *>
        assertEquals(13L, (ackBody["upToSeq"] as Number).toLong())

        // Verify entities in Room
        val lap1 = dao.findByDeviceAndId("laptop", "lap-1")
        assertNotNull(lap1)
        assertEquals("valid", lap1!!.settledStatus)

        val lap2 = dao.findByDeviceAndId("laptop", "lap-2")
        assertNotNull(lap2)
        assertEquals("tombstone", lap2!!.settledStatus)
        assertTrue(lap2.extrasJson!!.contains("schema_invalid"))

        val lap3 = dao.findByDeviceAndId("laptop", "lap-3")
        assertNotNull(lap3)
        assertEquals("tombstone", lap3!!.settledStatus)
        assertTrue(lap3.extrasJson!!.contains("future_timestamp"))
    }

    @Test
    fun testProcessInboundBatchInvalidHeaderRejectsWithoutAck() = runTest {
        val dao = MockEventDao()
        val client = TestableRelayClient()
        val engine = SyncDataEngine(eventDao = dao, relayClient = client)

        val invalidBatch = listOf(
            mapOf(
                "id" to "lap-1",
                "seq" to 10L,
                "deviceId" to "laptop",
                "schemaVersion" to "2.0.0" // Incompatible major version
            )
        )

        try {
            engine.processInboundBatch(fromSeq = 10L, rawEvents = invalidBatch)
            fail("Expected IllegalArgumentException for incompatible schemaVersion")
        } catch (_: IllegalArgumentException) {
            // Expected
        }

        // Must not have sent any ACK
        assertEquals(0, client.sentFrames.size)
    }
}

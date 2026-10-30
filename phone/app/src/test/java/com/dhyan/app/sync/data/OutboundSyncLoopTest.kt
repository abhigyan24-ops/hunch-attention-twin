package com.dhyan.app.sync.data

import com.dhyan.app.data.EventDao
import com.dhyan.app.data.EventEntity
import com.dhyan.app.personas.json.JsonReader
import com.dhyan.app.sync.session.RelayClient
import com.dhyan.app.sync.session.SessionTransport
import com.dhyan.app.sync.session.SessionTransportListener
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.StandardCharsets

@OptIn(ExperimentalCoroutinesApi::class)
class OutboundSyncLoopTest {

    private class MemoryDao : EventDao {
        val rows = mutableListOf<EventEntity>()
        override suspend fun insert(event: EventEntity): Long { rows.add(event); return rows.size.toLong() }
        override suspend fun insertAll(events: List<EventEntity>) { rows.addAll(events) }
        override suspend fun recentEvents(sinceMs: Long, limit: Int) = rows.filter { it.ts >= sinceMs }.take(limit)
        override suspend fun recentByKind(kind: String, sinceMs: Long, limit: Int) = rows.filter { it.kind == kind && it.ts >= sinceMs }.take(limit)
        override suspend fun purgeOlderThan(beforeMs: Long) = 0
        override suspend fun getOutboundAfterSeq(deviceId: String, afterSeq: Long, limit: Int) =
            rows.filter { it.deviceId == deviceId && it.seq > afterSeq }.sortedBy { it.seq }.take(limit)
        override suspend fun getHighestSeq(deviceId: String) = rows.filter { it.deviceId == deviceId }.maxOfOrNull { it.seq }
        override suspend fun findByDeviceAndId(deviceId: String, eventId: String) = rows.firstOrNull { it.deviceId == deviceId && it.eventId == eventId }
    }

    private class TestRelayClient : RelayClient(
        ByteArray(32),
        "aa".repeat(32),
        "ws://127.0.0.1/ws",
        object : SessionTransport {
            override fun connect(url: String, roomId: String, device: String, listener: SessionTransportListener) {}
            override fun send(text: String) = true
            override fun close(code: Int, reason: String) {}
        },
        TestScope()
    ) {
        val sent = mutableListOf<Pair<String, String>>()
        override fun sendEncrypted(type: String, bodyJson: String): Boolean {
            sent.add(type to bodyJson)
            return true
        }
    }

    @Test
    fun batchLimitCappedAt50Events() = runTest {
        val dao = MemoryDao()
        val client = TestRelayClient()
        val deviceId = "phone-test-device"

        // Insert 120 events
        for (i in 1..120) {
            dao.insert(
                EventEntity(
                    id = i.toLong(),
                    ts = 1000L + i,
                    kind = "screen_on",
                    appCat = "system",
                    surface = "screen",
                    durationMin = 0.0,
                    senderHash = null,
                    notifCat = null,
                    scrollCount = 0,
                    extrasJson = null,
                    eventId = "00000000-0000-4000-8000-${String.format("%012d", i)}",
                    seq = i.toLong(),
                    deviceId = deviceId,
                    settledStatus = "valid",
                    schemaVersion = "1.1.0"
                )
            )
        }

        val engine = SyncDataEngine(
            eventDao = dao,
            relayClient = client,
            localDeviceId = deviceId,
            transaction = { block -> block() }
        )

        // Export first batch starting at seq 0
        val exported = engine.exportOutboundBatch(afterSeq = 0L)
        assertTrue("Batch must be exported successfully", exported)
        assertEquals("lastExportedSeq must be 50", 50L, engine.lastExportedSeq)

        assertEquals(1, client.sent.size)
        val (type, bodyJson) = client.sent.first()
        assertEquals("events", type)

        val batch = JsonReader.parse(bodyJson) as Map<*, *>
        val eventsList = batch["events"] as List<*>
        assertEquals("Max batch size must be strictly capped at 50 events", 50, eventsList.size)
        assertEquals(1L, (batch["fromSeq"] as Number).toLong())
    }

    @Test
    fun resendFromLastSeqAckedWithoutDuplicates() = runTest {
        val dao = MemoryDao()
        val client = TestRelayClient()
        val deviceId = "phone-test-device"

        for (i in 1..80) {
            dao.insert(
                EventEntity(
                    id = i.toLong(),
                    ts = 1000L + i,
                    kind = "screen_on",
                    appCat = "system",
                    surface = "screen",
                    durationMin = 0.0,
                    senderHash = null,
                    notifCat = null,
                    scrollCount = 0,
                    extrasJson = null,
                    eventId = "00000000-0000-4000-8000-${String.format("%012d", i)}",
                    seq = i.toLong(),
                    deviceId = deviceId,
                    settledStatus = "valid",
                    schemaVersion = "1.1.0"
                )
            )
        }

        val engine = SyncDataEngine(
            eventDao = dao,
            relayClient = client,
            localDeviceId = deviceId,
            transaction = { block -> block() }
        )

        // Peer acknowledges up to seq 40
        val lastSeqAcked = 40L
        val exported = engine.exportOutboundBatch(afterSeq = lastSeqAcked)
        assertTrue(exported)

        assertEquals(1, client.sent.size)
        val (type, bodyJson) = client.sent.first()
        assertEquals("events", type)

        val batch = JsonReader.parse(bodyJson) as Map<*, *>
        val eventsList = batch["events"] as List<Map<*, *>>

        // Must start from seq 41 and contain 40 events (seq 41 to 80), with zero duplicates
        assertEquals(40, eventsList.size)
        assertEquals(41L, (batch["fromSeq"] as Number).toLong())
        assertEquals(41L, (eventsList.first()["seq"] as Number).toLong())
        assertEquals(80L, (eventsList.last()["seq"] as Number).toLong())
        assertEquals(80L, engine.lastExportedSeq)
    }

    @Test
    fun batchShrinksWhenExceeding32KbPayload() = runTest {
        val dao = MemoryDao()
        val client = TestRelayClient()
        val deviceId = "phone-test-device"

        // Insert events with large extrasJson to trigger > 32KB payload
        val largeJson = """{"blob":"${"A".repeat(1500)}"}"""
        for (i in 1..40) {
            dao.insert(
                EventEntity(
                    id = i.toLong(),
                    ts = 1000L + i,
                    kind = "screen_on",
                    appCat = "system",
                    surface = "screen",
                    durationMin = 0.0,
                    senderHash = null,
                    notifCat = null,
                    scrollCount = 0,
                    extrasJson = largeJson,
                    eventId = "00000000-0000-4000-8000-${String.format("%012d", i)}",
                    seq = i.toLong(),
                    deviceId = deviceId,
                    settledStatus = "valid",
                    schemaVersion = "1.1.0"
                )
            )
        }

        val engine = SyncDataEngine(
            eventDao = dao,
            relayClient = client,
            localDeviceId = deviceId,
            transaction = { block -> block() }
        )

        val exported = engine.exportOutboundBatch(afterSeq = 0L)
        assertTrue(exported)

        assertEquals(1, client.sent.size)
        val (type, bodyJson) = client.sent.first()
        assertEquals("events", type)

        val batch = JsonReader.parse(bodyJson) as Map<*, *>
        val eventsList = batch["events"] as List<*>

        assertTrue("Batch size in bytes must be <= 32,000 bytes", bodyJson.toByteArray(StandardCharsets.UTF_8).size <= 32_000)
        assertTrue("Event count must be bisected to less than 40", eventsList.size < 40)
    }
}

package com.dhyan.app.sync.command

import com.dhyan.app.actuator.ControlResult
import com.dhyan.app.actuator.HeldItemSummary
import com.dhyan.app.actuator.HeldItemsView
import com.dhyan.app.actuator.PhoneControl
import com.dhyan.app.data.CmdLogDao
import com.dhyan.app.data.CmdLogEntity
import com.dhyan.app.personas.json.JsonReader
import com.dhyan.app.sync.session.RelayClient
import com.dhyan.app.sync.session.SessionTransport
import com.dhyan.app.sync.session.SessionTransportListener
import com.dhyan.app.twin.decision.ActuationStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import com.dhyan.app.ingest.ClockOffset
import org.junit.Before
import org.junit.Test

class SyncCommandDispatcherTest {

    @Before
    fun setUp() {
        ClockOffset.reset()
    }

    private class MockPhoneControl : PhoneControl {
        var killSwitchState: Boolean? = null
        var lastShowNowId: String? = null
        var lastShowNowAll: Boolean = false

        override suspend fun showNow(heldId: String?, all: Boolean): ControlResult {
            lastShowNowId = heldId
            lastShowNowAll = all
            return if (all || heldId == "held-valid") {
                ControlResult.Applied("released")
            } else {
                ControlResult.Rejected("not_found")
            }
        }

        override suspend fun setKillSwitch(on: Boolean): ControlResult {
            killSwitchState = on
            return ControlResult.Applied(if (on) "kill_switch_activated" else "kill_switch_deactivated")
        }

        override suspend fun declareSession(task: String, plannedMin: Double, strict: Boolean): ControlResult =
            ControlResult.Applied("session_declared")

        override suspend fun endSession(): ControlResult =
            ControlResult.Applied("session_ended")
    }

    private class MockCmdLogDao : CmdLogDao {
        val database = mutableListOf<CmdLogEntity>()

        override suspend fun insert(entry: CmdLogEntity) {
            database.removeAll { it.cmdId == entry.cmdId }
            database.add(entry)
        }

        override suspend fun getById(cmdId: String): CmdLogEntity? =
            database.find { it.cmdId == cmdId }

        override suspend fun getRecent(limit: Int): List<CmdLogEntity> =
            database.take(limit)

        override fun observeAll(): Flow<List<CmdLogEntity>> = emptyFlow()

        override suspend fun pruneOlderThan(cutoffMs: Long): Int = 0
    }

    private class MockRelayTransport : SessionTransport {
        override fun connect(url: String, roomId: String, device: String, listener: SessionTransportListener) {}
        override fun send(text: String): Boolean = true
        override fun close(code: Int, reason: String) {}
    }

    private class TestableRelayClient : RelayClient(
        pk = ByteArray(32) { 1 },
        sidHex = "bb".repeat(32),
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
    fun testKillSwitchOnTrueApplied() = runTest {
        val phoneControl = MockPhoneControl()
        val dao = MockCmdLogDao()
        val client = TestableRelayClient()
        val dispatcher = SyncCommandDispatcher(phoneControl, dao, client, timeProvider = { 10_000L })

        val cmdJson = """{"cmdId":"00000000-0000-4000-8000-000000000001","cmd":"kill_switch","args":{"on":true},"issuedTs":10000,"exp":20000}"""
        val handled = dispatcher.handleInboundCommand(cmdJson)
        assertTrue(handled)

        assertEquals(true, phoneControl.killSwitchState)
        assertEquals(1, dao.database.size)
        assertEquals("applied", dao.database[0].status)

        assertEquals(1, client.sentFrames.size)
        assertEquals("cmd_ack", client.sentFrames[0].first)
        val ack = JsonReader.parse(client.sentFrames[0].second) as Map<*, *>
        assertEquals("applied", ack["status"])
    }

    @Test
    fun testKillSwitchOnFalseFromLaptopRejectedUnauthorized() = runTest {
        val phoneControl = MockPhoneControl()
        val dao = MockCmdLogDao()
        val client = TestableRelayClient()
        val dispatcher = SyncCommandDispatcher(phoneControl, dao, client, timeProvider = { 10_000L })

        val cmdJson = """{"cmdId":"00000000-0000-4000-8000-000000000002","cmd":"kill_switch","args":{"on":false},"issuedTs":10000,"exp":20000}"""
        val handled = dispatcher.handleInboundCommand(cmdJson)
        assertTrue(handled)

        // Kill switch must NOT have been changed
        assertEquals(null, phoneControl.killSwitchState)

        // Must be recorded as rejected unauthorized
        assertEquals(1, dao.database.size)
        assertEquals("rejected", dao.database[0].status)
        assertEquals("unauthorized", dao.database[0].reason)

        assertEquals(1, client.sentFrames.size)
        assertEquals("cmd_ack", client.sentFrames[0].first)
        val ack = JsonReader.parse(client.sentFrames[0].second) as Map<*, *>
        assertEquals("rejected", ack["status"])
        assertEquals("unauthorized", ack["reason"])
    }

    @Test
    fun testShowNowValidAndInvalid() = runTest {
        val phoneControl = MockPhoneControl()
        val dao = MockCmdLogDao()
        val client = TestableRelayClient()
        val dispatcher = SyncCommandDispatcher(phoneControl, dao, client, timeProvider = { 10_000L })

        // Valid show_now
        val cmdValid = """{"cmdId":"00000000-0000-4000-8000-000000000003","cmd":"show_now","args":{"heldId":"held-valid"},"issuedTs":10000,"exp":20000}"""
        dispatcher.handleInboundCommand(cmdValid)
        assertEquals("held-valid", phoneControl.lastShowNowId)
        val ackValid = JsonReader.parse(client.sentFrames[0].second) as Map<*, *>
        assertEquals("applied", ackValid["status"])

        // Unknown show_now
        val cmdInvalid = """{"cmdId":"00000000-0000-4000-8000-000000000004","cmd":"show_now","args":{"heldId":"held-unknown"},"issuedTs":10000,"exp":20000}"""
        dispatcher.handleInboundCommand(cmdInvalid)
        val ackInvalid = JsonReader.parse(client.sentFrames[1].second) as Map<*, *>
        assertEquals("rejected", ackInvalid["status"])
        assertEquals("not_found", ackInvalid["reason"])
    }

    @Test
    fun testExpiredAndDuplicateCommands() = runTest {
        val phoneControl = MockPhoneControl()
        val dao = MockCmdLogDao()
        val client = TestableRelayClient()
        val dispatcher = SyncCommandDispatcher(phoneControl, dao, client, timeProvider = { 50_000L })

        // Expired command (now = 50000, exp = 20000)
        val cmdExpired = """{"cmdId":"00000000-0000-4000-8000-000000000005","cmd":"end_session","args":{},"issuedTs":10000,"exp":20000}"""
        dispatcher.handleInboundCommand(cmdExpired)
        val ackExpired = JsonReader.parse(client.sentFrames[0].second) as Map<*, *>
        assertEquals("expired", ackExpired["status"])

        // Duplicate command
        dao.insert(
            CmdLogEntity(
                cmdId = "00000000-0000-4000-8000-000000000099",
                source = "laptop",
                action = "end_session",
                status = "applied",
                reason = "session_ended",
                tsMs = 10000L,
                executedAtMs = 10000L
            )
        )
        val cmdDup = """{"cmdId":"00000000-0000-4000-8000-000000000099","cmd":"end_session","args":{},"issuedTs":49000,"exp":60000}"""
        dispatcher.handleInboundCommand(cmdDup)
        val ackDup = JsonReader.parse(client.sentFrames[1].second) as Map<*, *>
        assertEquals("duplicate", ackDup["status"])
    }

    @Test
    fun testHeldListMirrorZeroContent() = runTest {
        val mockView = object : HeldItemsView {
            override suspend fun currentHeldItems(): List<HeldItemSummary> = listOf(
                HeldItemSummary(
                    heldId = "opaque-hash-1",
                    appCat = "comms",
                    heldAt = 1000L,
                    expiresAt = 5000L,
                    status = ActuationStatus.HELD
                )
            )

            override fun observeHeldItems(): Flow<List<HeldItemSummary>> = emptyFlow()
        }

        val phoneControl = MockPhoneControl()
        val dao = MockCmdLogDao()
        val client = TestableRelayClient()
        val dispatcher = SyncCommandDispatcher(phoneControl, dao, client, mockView)

        val sent = dispatcher.sendHeldList()
        assertTrue(sent)
        assertEquals(1, client.sentFrames.size)
        assertEquals("held_list", client.sentFrames[0].first)

        val heldListJson = client.sentFrames[0].second
        assertFalse(heldListJson.contains("title"))
        assertFalse(heldListJson.contains("text"))
        assertFalse(heldListJson.contains("package"))

        val parsed = JsonReader.parse(heldListJson) as Map<*, *>
        assertEquals(1, (parsed["total"] as Number).toInt())
    }
}

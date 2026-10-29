package com.dhyan.app.sync

import com.dhyan.app.actuator.ActuationEvent
import com.dhyan.app.actuator.ActuationEventBus
import com.dhyan.app.actuator.ControlResult
import com.dhyan.app.actuator.HeldItemSummary
import com.dhyan.app.actuator.HeldItemsView
import com.dhyan.app.actuator.PhoneControl
import com.dhyan.app.data.CmdLogDao
import com.dhyan.app.data.CmdLogEntity
import com.dhyan.app.sync.command.SyncCommandDispatcher
import com.dhyan.app.sync.session.RelayClient
import com.dhyan.app.sync.session.SessionTransport
import com.dhyan.app.sync.session.SessionTransportListener
import com.dhyan.app.twin.decision.ActuationStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ActuationMirrorTest {

    private class MockRelayTransport : SessionTransport {
        override fun connect(url: String, roomId: String, device: String, listener: SessionTransportListener) {}
        override fun send(text: String): Boolean = true
        override fun close(code: Int, reason: String) {}
    }

    private class TestRelayClient : RelayClient(
        pk = ByteArray(32) { 1 },
        sidHex = "aa".repeat(32),
        relayUrl = "ws://127.0.0.1",
        transport = MockRelayTransport(),
        scope = TestScope()
    ) {
        val outboundMessages = mutableListOf<Pair<String, String>>()
        override fun sendEncrypted(type: String, bodyJson: String): Boolean {
            outboundMessages.add(type to bodyJson)
            return true
        }
    }

    private class DummyPhoneControl : PhoneControl {
        override suspend fun showNow(heldId: String?, all: Boolean) = ControlResult.Applied("ok")
        override suspend fun setKillSwitch(on: Boolean) = ControlResult.Applied("ok")
        override suspend fun declareSession(task: String, plannedMin: Double, strict: Boolean) = ControlResult.Applied("ok")
        override suspend fun endSession() = ControlResult.Applied("ok")
    }

    private class DummyCmdLogDao : CmdLogDao {
        override suspend fun insert(entry: CmdLogEntity) {}
        override suspend fun getById(cmdId: String): CmdLogEntity? = null
        override suspend fun getRecent(limit: Int): List<CmdLogEntity> = emptyList()
        override fun observeAll(): Flow<List<CmdLogEntity>> = emptyFlow()
        override suspend fun pruneOlderThan(cutoffMs: Long): Int = 0
    }

    @Test
    fun testPetEventMappingAndDispatch() = runTest {
        val client = TestRelayClient()
        val dispatcher = SyncCommandDispatcher(
            phoneControl = DummyPhoneControl(),
            cmdLogDao = DummyCmdLogDao(),
            relayClient = client
        )

        val busFlow = MutableSharedFlow<ActuationEvent>()
        val mockBus = object : ActuationEventBus {
            override val events: SharedFlow<ActuationEvent> = busFlow.asSharedFlow()
            override suspend fun emit(event: ActuationEvent) {
                busFlow.emit(event)
            }
        }

        val testScope = CoroutineScope(Dispatchers.Unconfined)
        testScope.launch {
            mockBus.events.collect { event ->
                val kind = when (event.status) {
                    ActuationStatus.HELD -> "held"
                    ActuationStatus.RELEASED, ActuationStatus.FAILED_OPEN -> "released"
                    else -> "nudge"
                }
                dispatcher.sendPetEvent(kind)
            }
        }

        mockBus.emit(ActuationEvent("h1", ActuationStatus.HELD, "social", 1000L))
        mockBus.emit(ActuationEvent("h2", ActuationStatus.RELEASED, "social", 2000L))
        mockBus.emit(ActuationEvent("h3", ActuationStatus.FAILED_OPEN, "news", 3000L))
        mockBus.emit(ActuationEvent("h4", ActuationStatus.SNOOZED, "comms", 4000L))

        assertEquals(4, client.outboundMessages.size)
        assertTrue(client.outboundMessages[0].second.contains("\"kind\":\"held\""))
        assertTrue(client.outboundMessages[1].second.contains("\"kind\":\"released\""))
        assertTrue(client.outboundMessages[2].second.contains("\"kind\":\"released\""))
        assertTrue(client.outboundMessages[3].second.contains("\"kind\":\"nudge\""))
    }

    @Test
    fun testHeldListObservationAndDispatch() = runTest {
        val client = TestRelayClient()
        val heldFlow = MutableStateFlow<List<HeldItemSummary>>(emptyList())
        val mockView = object : HeldItemsView {
            override suspend fun currentHeldItems(): List<HeldItemSummary> = heldFlow.value
            override fun observeHeldItems(): Flow<List<HeldItemSummary>> = heldFlow
        }

        val dispatcher = SyncCommandDispatcher(
            phoneControl = DummyPhoneControl(),
            cmdLogDao = DummyCmdLogDao(),
            relayClient = client,
            heldItemsView = mockView
        )

        val testScope = CoroutineScope(Dispatchers.Unconfined)
        testScope.launch {
            mockView.observeHeldItems().collect {
                dispatcher.sendHeldList()
            }
        }

        heldFlow.value = listOf(
            HeldItemSummary("h1", "comms", 1000L, 5000L, ActuationStatus.HELD)
        )

        // Initial empty + updated list = 2 dispatches
        assertTrue(client.outboundMessages.size >= 2)
        val latest = client.outboundMessages.last()
        assertEquals("held_list", latest.first)
        assertTrue(latest.second.contains("\"total\":1"))
    }
}

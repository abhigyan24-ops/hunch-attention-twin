package com.dhyan.app.sync.pairing

import com.dhyan.app.data.SettingsDao
import com.dhyan.app.data.SettingsEntity
import com.dhyan.app.sync.crypto.KeyWrapper
import com.dhyan.app.sync.crypto.TestKeyWrapper
import com.dhyan.app.sync.crypto.SyncCrypto
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.security.KeyPair
import java.security.interfaces.ECPrivateKey
import java.security.interfaces.ECPublicKey
import java.util.Base64

@OptIn(ExperimentalCoroutinesApi::class)
class PairingControllerTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private val settingsStore = mutableMapOf<String, SettingsEntity>()

    val setRecordLog = mutableListOf<SettingsEntity>()

    private val fakeSettingsDao = object : SettingsDao {
        override suspend fun set(setting: SettingsEntity) {
            setRecordLog.add(setting)
            settingsStore[setting.key] = setting
        }

        override suspend fun get(key: String): String? {
            return settingsStore[key]?.value
        }

        override suspend fun delete(key: String): Int {
            return if (settingsStore.remove(key) != null) 1 else 0
        }
    }

    private val keyWrapper: KeyWrapper = TestKeyWrapper()

    private class MockRendezvousTransport : RendezvousTransport {
        var connectedUrl: String? = null
        var connectedRoomId: String? = null
        var onMessageCallback: ((String) -> Unit)? = null
        var onErrorCallback: ((Throwable) -> Unit)? = null
        var onCloseCallback: (() -> Unit)? = null
        val sentMessages = mutableListOf<String>()
        var isClosed = false

        override fun connect(url: String, roomId: String, onMessage: (String) -> Unit, onError: (Throwable) -> Unit, onClose: () -> Unit) {
            connectedUrl = url
            connectedRoomId = roomId
            onMessageCallback = onMessage
            onErrorCallback = onError
            onCloseCallback = onClose
        }

        override fun send(messageJson: String) {
            sentMessages.add(messageJson)
        }

        override fun close() {
            isClosed = true
        }

        fun receive(msg: String) {
            onMessageCallback?.invoke(msg)
        }
    }

    private lateinit var mockTransport: MockRendezvousTransport
    private lateinit var controller: PairingController

    @Before
    fun setup() {
        settingsStore.clear()
        mockTransport = MockRendezvousTransport()
        controller = PairingController(
            settingsDao = fakeSettingsDao,
            keyWrapper = keyWrapper,
            transport = mockTransport,
            scope = testScope
        )
    }

    @Test
    fun successfulPairingFlowEndToEnd() = testScope.runTest {
        // Laptop generates its key pair and commit
        val kpL = SyncCrypto.generateP256KeyPair()
        val pkLBytes = SyncCrypto.encodePublicKeyUncompressed(kpL.public as ECPublicKey)
        val nL = ByteArray(16) { 1 }
        val commitL = SyncCrypto.computePairCommit(pkLBytes, nL)
        val commitBase64 = Base64.getEncoder().encodeToString(commitL)

        // 1. Phone starts pairing
        controller.startPairing("234567", "ws://127.0.0.1:47820")
        assertFalse(mockTransport.sentMessages.any { it.contains("pair_join") })
        mockTransport.receive("{\"v\":1,\"t\":\"pair_offer\",\"device\":\"laptop\",\"commit\":\"$commitBase64\"}")
        assertTrue(mockTransport.sentMessages.any { it.contains("pair_join") })

        // Extract phone pk and nonce from pair_join
        val joinMsg = mockTransport.sentMessages.first { it.contains("pair_join") }
        val joinMap = com.dhyan.app.personas.json.JsonReader.parse(joinMsg) as Map<*, *>
        val pkPBytes = Base64.getDecoder().decode(joinMap["pk"] as String)
        val nPBytes = Base64.getDecoder().decode(joinMap["n"] as String)

        // 2. Laptop sends pair_offer
        mockTransport.receive("{\"v\":1,\"t\":\"pair_offer\",\"device\":\"laptop\",\"commit\":\"$commitBase64\"}")

        // 3. Laptop sends pair_reveal
        val pkLBase64 = Base64.getEncoder().encodeToString(pkLBytes)
        val nLBase64 = Base64.getEncoder().encodeToString(nL)
        mockTransport.receive("{\"v\":1,\"t\":\"pair_reveal\",\"device\":\"laptop\",\"pk\":\"$pkLBase64\",\"n\":\"$nLBase64\"}")

        // Phone should transition to AwaitingUserConfirmation with correct SAS
        val secretL = SyncCrypto.computeECDH(kpL.private as ECPrivateKey, SyncCrypto.decodePublicKeyUncompressed(pkPBytes))
        val tL = SyncCrypto.computeTranscriptHash(pkLBytes, pkPBytes, nL, nPBytes)
        val expectedSas = SyncCrypto.computeSas(tL)

        val state = controller.state.value
        assertTrue(state is PairingState.AwaitingUserConfirmation)
        assertEquals(expectedSas, (state as PairingState.AwaitingUserConfirmation).sasDigits)

        // 4. User confirms SAS on phone
        controller.confirmSas(true)
        assertTrue(mockTransport.sentMessages.any { it.contains("pair_confirm") })

        // 5. Laptop sends pair_confirm
        val macL = SyncCrypto.computePairConfirmMac(secretL, tL, "laptop")
        val macLBase64 = Base64.getEncoder().encodeToString(macL)
        mockTransport.receive("{\"v\":1,\"t\":\"pair_confirm\",\"device\":\"laptop\",\"mac\":\"$macLBase64\"}")

        testScheduler.runCurrent()

        // 6. Final state: Paired
        val finalState = controller.state.value
        assertTrue("Expected Paired state but got $finalState", finalState is PairingState.Paired)
        assertNotNull(settingsStore[PairingController.KEY_PAIRING_PK])
        assertNotNull(settingsStore[PairingController.KEY_SESSION_ROOM_ID])

        // Verify unpair deletes credentials
        controller.unpair()
        assertTrue(controller.state.value is PairingState.Idle)
        assertFalse(settingsStore.containsKey(PairingController.KEY_PAIRING_PK))
        assertFalse(settingsStore.containsKey(PairingController.KEY_SESSION_ROOM_ID))
    }

    @Test
    fun commitMismatchFailsProtocol() = testScope.runTest {
        val kpL = SyncCrypto.generateP256KeyPair()
        val pkLBytes = SyncCrypto.encodePublicKeyUncompressed(kpL.public as ECPublicKey)
        val nL = ByteArray(16) { 1 }
        val commitL = SyncCrypto.computePairCommit(pkLBytes, nL)
        val commitBase64 = Base64.getEncoder().encodeToString(commitL)

        controller.startPairing("234567", "ws://127.0.0.1:47820")
        mockTransport.receive("{\"v\":1,\"t\":\"pair_offer\",\"device\":\"laptop\",\"commit\":\"$commitBase64\"}")

        // Corrupt laptop nonce in reveal
        val corruptNL = ByteArray(16) { 99 }
        val pkLBase64 = Base64.getEncoder().encodeToString(pkLBytes)
        val corruptNLBase64 = Base64.getEncoder().encodeToString(corruptNL)
        mockTransport.receive("{\"v\":1,\"t\":\"pair_reveal\",\"device\":\"laptop\",\"pk\":\"$pkLBase64\",\"n\":\"$corruptNLBase64\"}")

        val state = controller.state.value
        assertTrue("Expected Failed state on commit mismatch, got $state", state is PairingState.Failed)
        assertTrue(mockTransport.sentMessages.any { it.contains("pair_cancel") })
    }

    @Test
    fun userRejectionSendsCancel() = testScope.runTest {
        val kpL = SyncCrypto.generateP256KeyPair()
        val pkLBytes = SyncCrypto.encodePublicKeyUncompressed(kpL.public as ECPublicKey)
        val nL = ByteArray(16) { 1 }
        val commitL = SyncCrypto.computePairCommit(pkLBytes, nL)
        val commitBase64 = Base64.getEncoder().encodeToString(commitL)

        controller.startPairing("234567", "ws://127.0.0.1:47820")
        mockTransport.receive("{\"v\":1,\"t\":\"pair_offer\",\"device\":\"laptop\",\"commit\":\"$commitBase64\"}")

        val pkLBase64 = Base64.getEncoder().encodeToString(pkLBytes)
        val nLBase64 = Base64.getEncoder().encodeToString(nL)
        mockTransport.receive("{\"v\":1,\"t\":\"pair_reveal\",\"device\":\"laptop\",\"pk\":\"$pkLBase64\",\"n\":\"$nLBase64\"}")

        assertTrue(controller.state.value is PairingState.AwaitingUserConfirmation)

        // User clicks "No" (rejects SAS)
        controller.confirmSas(false)

        println("DEBUG_STATE: " + controller.state.value)
        assertTrue(controller.state.value is PairingState.Cancelled)
        assertTrue(mockTransport.sentMessages.any { it.contains("pair_cancel") })
    }

    private fun peerConfirmation(): String {
        val laptop = SyncCrypto.generateP256KeyPair()
        val pkL = SyncCrypto.encodePublicKeyUncompressed(laptop.public as ECPublicKey)
        val nL = ByteArray(16) { 7 }
        fun b64(bytes: ByteArray) = Base64.getEncoder().encodeToString(bytes)
        controller.startPairing("234567", "ws://127.0.0.1:47820")
        mockTransport.receive("""{"v":1,"t":"pair_offer","device":"laptop","commit":"${b64(SyncCrypto.computePairCommit(pkL, nL))}"}""")
        val join = com.dhyan.app.personas.json.JsonReader.parse(mockTransport.sentMessages.last()) as Map<*, *>
        val pkP = Base64.getDecoder().decode(join["pk"] as String)
        val nP = Base64.getDecoder().decode(join["n"] as String)
        mockTransport.receive("""{"v":1,"t":"pair_reveal","device":"laptop","pk":"${b64(pkL)}","n":"${b64(nL)}"}""")
        val secret = SyncCrypto.computeECDH(laptop.private as ECPrivateKey, SyncCrypto.decodePublicKeyUncompressed(pkP))
        val transcript = SyncCrypto.computeTranscriptHash(pkL, pkP, nL, nP)
        return """{"v":1,"t":"pair_confirm","device":"laptop","mac":"${b64(SyncCrypto.computePairConfirmMac(secret, transcript, "laptop"))}"}"""
    }

    @Test
    fun peerConfirmationBeforeLocalConsentCannotPersist() = testScope.runTest {
        val confirm = peerConfirmation()
        mockTransport.receive(confirm)
        testScheduler.runCurrent()
        assertTrue(settingsStore.isEmpty())
        assertTrue(controller.state.value is PairingState.AwaitingUserConfirmation)
        controller.confirmSas(true)
        testScheduler.runCurrent()
        assertTrue(controller.state.value is PairingState.Paired)
        assertEquals(2, settingsStore.size)
    }

    @Test
    fun rejectionInvalidatesAlreadyAuthenticatedPeerConfirmation() = testScope.runTest {
        val confirm = peerConfirmation()
        mockTransport.receive(confirm)
        controller.confirmSas(false)
        mockTransport.receive(confirm)
        testScheduler.runCurrent()
        assertTrue(settingsStore.isEmpty())
        println("DEBUG_STATE: " + controller.state.value)
        assertTrue(controller.state.value is PairingState.Cancelled)
    }

    @Test
    fun timeoutDiscardsAttemptAndStaleCallbacks() = testScope.runTest {
        val confirm = peerConfirmation()
        val oldCallback = mockTransport.onMessageCallback!!
        testScheduler.advanceTimeBy(300_001L)
        testScheduler.runCurrent()
        assertTrue(controller.state.value is PairingState.Failed)
        oldCallback(confirm)
        controller.confirmSas(true)
        testScheduler.runCurrent()
        assertTrue(settingsStore.isEmpty())
    }


    

    @Test
    fun peerConfirmBeforeLocalYes_persistsOnlyAfterLocalYes_andWritesZeroBefore() = testScope.runTest {
        setRecordLog.clear()
        val kpL = SyncCrypto.generateP256KeyPair()
        val pkLBytes = SyncCrypto.encodePublicKeyUncompressed(kpL.public as ECPublicKey)
        val nL = ByteArray(16) { 1 }
        val commitL = SyncCrypto.computePairCommit(pkLBytes, nL)
        val commitBase64 = Base64.getEncoder().encodeToString(commitL)

        controller.startPairing("234567", "ws://127.0.0.1:47820")
        mockTransport.receive("""{"v":1,"t":"pair_offer","device":"laptop","commit":"$commitBase64"}""")

        val joinMsg = mockTransport.sentMessages.first { it.contains("pair_join") }
        val joinMap = com.dhyan.app.personas.json.JsonReader.parse(joinMsg) as Map<*, *>
        val pkPBytes = Base64.getDecoder().decode(joinMap["pk"] as String)
        val nPBytes = Base64.getDecoder().decode(joinMap["n"] as String)

        val pkLBase64 = Base64.getEncoder().encodeToString(pkLBytes)
        val nLBase64 = Base64.getEncoder().encodeToString(nL)
        mockTransport.receive("""{"v":1,"t":"pair_reveal","device":"laptop","pk":"$pkLBase64","n":"$nLBase64"}""")
        assertTrue(controller.state.value is PairingState.AwaitingUserConfirmation)

        // Peer sends pair_confirm BEFORE local user confirms
        val secretL = SyncCrypto.computeECDH(kpL.private as ECPrivateKey, SyncCrypto.decodePublicKeyUncompressed(pkPBytes))
        val tL = SyncCrypto.computeTranscriptHash(pkLBytes, pkPBytes, nL, nPBytes)
        val macL = SyncCrypto.computePairConfirmMac(secretL, tL, "laptop")
        val macLBase64 = Base64.getEncoder().encodeToString(macL)
        mockTransport.receive("""{"v":1,"t":"pair_confirm","device":"laptop","mac":"$macLBase64"}""")

        testScheduler.runCurrent()

        // Zero writes before local human confirmation!
        assertEquals("Expected 0 writes before local Yes", 0, setRecordLog.size)
        assertTrue(controller.state.value is PairingState.AwaitingUserConfirmation)

        // Now local user confirms SAS
        controller.confirmSas(true)
        testScheduler.runCurrent()

        // After local Yes, credentials are persisted (pk and roomId)
        assertEquals(2, setRecordLog.size)
        assertTrue(controller.state.value is PairingState.Paired)
    }

    @Test
    fun localNo_discardsContextAndWritesZero() = testScope.runTest {
        setRecordLog.clear()
        val kpL = SyncCrypto.generateP256KeyPair()
        val pkLBytes = SyncCrypto.encodePublicKeyUncompressed(kpL.public as ECPublicKey)
        val nL = ByteArray(16) { 1 }
        val commitL = SyncCrypto.computePairCommit(pkLBytes, nL)
        val commitBase64 = Base64.getEncoder().encodeToString(commitL)

        controller.startPairing("234567", "ws://127.0.0.1:47820")
        mockTransport.receive("""{"v":1,"t":"pair_offer","device":"laptop","commit":"$commitBase64"}""")

        val pkLBase64 = Base64.getEncoder().encodeToString(pkLBytes)
        val nLBase64 = Base64.getEncoder().encodeToString(nL)
        mockTransport.receive("""{"v":1,"t":"pair_reveal","device":"laptop","pk":"$pkLBase64","n":"$nLBase64"}""")
        assertTrue(controller.state.value is PairingState.AwaitingUserConfirmation)

        // Local user rejects SAS (presses No)
        controller.confirmSas(false)
        testScheduler.runCurrent()

        assertTrue(controller.state.value is PairingState.Cancelled)
        assertEquals(0, setRecordLog.size)
        assertTrue(mockTransport.sentMessages.any { it.contains("pair_cancel") })
    }

    @Test
    fun wrongMac_failsProtocolAndWritesZero() = testScope.runTest {
        setRecordLog.clear()
        val kpL = SyncCrypto.generateP256KeyPair()
        val pkLBytes = SyncCrypto.encodePublicKeyUncompressed(kpL.public as ECPublicKey)
        val nL = ByteArray(16) { 1 }
        val commitL = SyncCrypto.computePairCommit(pkLBytes, nL)
        val commitBase64 = Base64.getEncoder().encodeToString(commitL)

        controller.startPairing("234567", "ws://127.0.0.1:47820")
        mockTransport.receive("""{"v":1,"t":"pair_offer","device":"laptop","commit":"$commitBase64"}""")

        val pkLBase64 = Base64.getEncoder().encodeToString(pkLBytes)
        val nLBase64 = Base64.getEncoder().encodeToString(nL)
        mockTransport.receive("""{"v":1,"t":"pair_reveal","device":"laptop","pk":"$pkLBase64","n":"$nLBase64"}""")

        // Peer delivers invalid MAC
        val badMac = Base64.getEncoder().encodeToString(ByteArray(32) { 0xFF.toByte() })
        mockTransport.receive("""{"v":1,"t":"pair_confirm","device":"laptop","mac":"$badMac"}""")
        testScheduler.runCurrent()

        assertTrue(controller.state.value is PairingState.Failed)
        assertEquals(0, setRecordLog.size)
    }

    @Test
    fun pairingTimeout_failsAndWritesZero() = testScope.runTest {
        setRecordLog.clear()
        controller.startPairing("234567", "ws://127.0.0.1:47820")
        assertTrue(controller.state.value is PairingState.WaitingForLaptopOffer)

        // Advance past 300s timeout
        testScheduler.advanceTimeBy(300_001L)
        testScheduler.runCurrent()

        assertTrue(controller.state.value is PairingState.Failed)
        assertEquals("pairing_expired", (controller.state.value as PairingState.Failed).error)
        assertEquals(0, setRecordLog.size)
    }

}

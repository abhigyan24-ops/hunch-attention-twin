package com.dhyan.app.sync.session

import com.dhyan.app.personas.json.JsonReader
import com.dhyan.app.sync.crypto.SyncCrypto
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.StandardCharsets
import java.util.Base64

class RelayClientTest {

    private class MockSessionTransport : SessionTransport {
        var listener: SessionTransportListener? = null
        val sentMessages = mutableListOf<String>()
        var lastClosedCode: Int? = null
        var lastClosedReason: String? = null
        var isClosed: Boolean = false

        override fun connect(
            url: String,
            roomId: String,
            device: String,
            listener: SessionTransportListener
        ) {
            this.listener = listener
            isClosed = false
        }

        override fun send(text: String): Boolean {
            sentMessages.add(text)
            return true
        }

        override fun close(code: Int, reason: String) {
            isClosed = true
            lastClosedCode = code
            lastClosedReason = reason
            listener?.onClosed(code, reason)
        }

        fun triggerOpen() {
            listener?.onOpen()
        }

        fun triggerMessage(text: String) {
            listener?.onMessage(text)
        }

        fun triggerFailure(t: Throwable) {
            listener?.onFailure(t)
        }
    }

    private val pk = ByteArray(32) { (it + 1).toByte() }
    private val sidHex = "11223344556677889900aabbccddeeff11223344556677889900aabbccddeeff"
    private val mockTransport = MockSessionTransport()
    private val testScope = TestScope()

    private val fixedRandomBytes: (Int) -> ByteArray = { size ->
        ByteArray(size) { (it + 10).toByte() }
    }

    @Test
    fun testHelloHandshakeAndKeyDerivation() {
        val client = RelayClient(
            pk = pk,
            sidHex = sidHex,
            relayUrl = "ws://127.0.0.1:8080/ws",
            transport = mockTransport,
            scope = testScope,
            getLastSeqAcked = { 42L },
            secureRandomBytes = fixedRandomBytes
        )

        client.connect()
        assertTrue(client.state.value is SessionState.Connecting)

        mockTransport.triggerOpen()
        assertTrue(client.state.value is SessionState.Handshaking)
        assertEquals(1, mockTransport.sentMessages.size)

        val phoneHelloJson = mockTransport.sentMessages[0]
        val phoneHello = JsonReader.parse(phoneHelloJson) as Map<*, *>
        assertEquals("phone", phoneHello["device"])
        assertEquals(42L, (phoneHello["lastSeqAcked"] as Number).toLong())
        val phoneSnBase64 = phoneHello["sn"] as String

        // Simulate Laptop responding with Hello
        val laptopSn = ByteArray(16) { (it + 20).toByte() }
        val laptopSnBase64 = Base64.getEncoder().encodeToString(laptopSn)
        val laptopMac = SyncCrypto.computeHelloMac(pk, "laptop", laptopSnBase64, 100L)
        val laptopMacBase64 = Base64.getEncoder().encodeToString(laptopMac)

        val laptopHelloJson = """{"v":1,"t":"hello","device":"laptop","sn":"$laptopSnBase64","proto":[1],"lastSeqAcked":100,"mac":"$laptopMacBase64"}"""
        mockTransport.triggerMessage(laptopHelloJson)

        assertTrue(client.state.value is SessionState.Connected)
        assertEquals(100L, (client.state.value as SessionState.Connected).peerLastSeqAcked)
    }

    @Test
    fun testEncryptedFrameTransmissionAndReceipt() {
        val client = RelayClient(
            pk = pk,
            sidHex = sidHex,
            relayUrl = "ws://127.0.0.1:8080/ws",
            transport = mockTransport,
            scope = testScope,
            getLastSeqAcked = { 0L },
            secureRandomBytes = fixedRandomBytes
        )

        client.connect()
        mockTransport.triggerOpen()

        val phoneSn = fixedRandomBytes(16)
        val laptopSn = ByteArray(16) { 5.toByte() }
        val laptopSnBase64 = Base64.getEncoder().encodeToString(laptopSn)
        val laptopMac = SyncCrypto.computeHelloMac(pk, "laptop", laptopSnBase64, 0L)
        val laptopMacBase64 = Base64.getEncoder().encodeToString(laptopMac)

        mockTransport.triggerMessage(
            """{"v":1,"t":"hello","device":"laptop","sn":"$laptopSnBase64","proto":[1],"lastSeqAcked":0,"mac":"$laptopMacBase64"}"""
        )
        assertTrue(client.state.value is SessionState.Connected)

        // Derive keys as the laptop peer
        val peerSessionKeys = SyncCrypto.deriveSessionKeys(pk, laptopSn, phoneSn)

        // 1. Phone sends encrypted frame
        val sendOk = client.sendEncrypted("events", """{"fromSeq":1,"events":[]}""")
        assertTrue(sendOk)
        assertTrue(mockTransport.sentMessages.size >= 2)

        val sentFrameJson = mockTransport.sentMessages.last()
        val sentFrame = JsonReader.parse(sentFrameJson) as Map<*, *>
        assertEquals("events", sentFrame["t"])
        assertEquals("p2l", sentFrame["d"])
        assertEquals(1L, (sentFrame["ctr"] as Number).toLong())

        val decryptedByPeer = SyncCrypto.decryptFramePayload(
            peerSessionKeys.kP2L,
            "events",
            "p2l",
            1L,
            sentFrame["c"] as String
        )
        val decryptedJson = String(decryptedByPeer, StandardCharsets.UTF_8)
        val payload = JsonReader.parse(decryptedJson) as Map<*, *>
        assertNotNull(payload["mid"])
        assertNotNull(payload["ts"])

        // 2. Peer sends encrypted frame to phone
        var receivedType: String? = null
        var receivedBody: String? = null
        client.onMessageReceived = { type, body, _, _ ->
            receivedType = type
            receivedBody = body
        }

        val testBody = """{"sample":"data"}"""
        val mid = "test-mid-1234"
        val ts = 1729864000000L
        val peerPlaintext = """{"mid":"$mid","ts":$ts,"b":$testBody}"""
        val c = SyncCrypto.encryptFramePayload(
            peerSessionKeys.kL2P,
            "state",
            "l2p",
            1L,
            peerPlaintext.toByteArray(StandardCharsets.UTF_8)
        )

        mockTransport.triggerMessage("""{"v":1,"t":"state","d":"l2p","ctr":1,"c":"$c"}""")

        assertEquals("state", receivedType)
        assertNotNull(receivedBody)
        val parsedBody = JsonReader.parse(receivedBody!!) as Map<*, *>
        assertEquals("data", parsedBody["sample"])
    }

    @Test
    fun testReplayAndDirectionRejection() {
        val client = RelayClient(
            pk = pk,
            sidHex = sidHex,
            relayUrl = "ws://127.0.0.1:8080/ws",
            transport = mockTransport,
            scope = testScope,
            getLastSeqAcked = { 0L },
            secureRandomBytes = fixedRandomBytes
        )

        client.connect()
        mockTransport.triggerOpen()

        val phoneSn = fixedRandomBytes(16)
        val laptopSn = ByteArray(16) { 5.toByte() }
        val laptopSnBase64 = Base64.getEncoder().encodeToString(laptopSn)
        val laptopMac = SyncCrypto.computeHelloMac(pk, "laptop", laptopSnBase64, 0L)
        val laptopMacBase64 = Base64.getEncoder().encodeToString(laptopMac)

        mockTransport.triggerMessage(
            """{"v":1,"t":"hello","device":"laptop","sn":"$laptopSnBase64","proto":[1],"lastSeqAcked":0,"mac":"$laptopMacBase64"}"""
        )

        val peerSessionKeys = SyncCrypto.deriveSessionKeys(pk, laptopSn, phoneSn)
        var messageCount = 0
        client.onMessageReceived = { _, _, _, _ -> messageCount++ }

        val plain = """{"mid":"m1","ts":1000,"b":{}}"""
        val c1 = SyncCrypto.encryptFramePayload(peerSessionKeys.kL2P, "ping", "l2p", 1L, plain.toByteArray(StandardCharsets.UTF_8))

        // Valid frame ctr=1
        mockTransport.triggerMessage("""{"v":1,"t":"ping","d":"l2p","ctr":1,"c":"$c1"}""")
        assertEquals(1, messageCount)

        // Replay frame ctr=1 -> rejected
        mockTransport.triggerMessage("""{"v":1,"t":"ping","d":"l2p","ctr":1,"c":"$c1"}""")
        assertEquals(1, messageCount)

        // Wrong direction frame -> rejected
        val c2 = SyncCrypto.encryptFramePayload(peerSessionKeys.kL2P, "ping", "p2l", 2L, plain.toByteArray(StandardCharsets.UTF_8))
        mockTransport.triggerMessage("""{"v":1,"t":"ping","d":"p2l","ctr":2,"c":"$c2"}""")
        assertEquals(1, messageCount)
    }

    @Test
    fun testThreeTagFailuresClosesConnectionWith1008() {
        val client = RelayClient(
            pk = pk,
            sidHex = sidHex,
            relayUrl = "ws://127.0.0.1:8080/ws",
            transport = mockTransport,
            scope = testScope,
            getLastSeqAcked = { 0L },
            secureRandomBytes = fixedRandomBytes
        )

        client.connect()
        mockTransport.triggerOpen()

        val laptopSn = ByteArray(16) { 5.toByte() }
        val laptopSnBase64 = Base64.getEncoder().encodeToString(laptopSn)
        val laptopMac = SyncCrypto.computeHelloMac(pk, "laptop", laptopSnBase64, 0L)
        val laptopMacBase64 = Base64.getEncoder().encodeToString(laptopMac)

        mockTransport.triggerMessage(
            """{"v":1,"t":"hello","device":"laptop","sn":"$laptopSnBase64","proto":[1],"lastSeqAcked":0,"mac":"$laptopMacBase64"}"""
        )

        // Corrupt ciphertext
        val badCiphertext = Base64.getEncoder().encodeToString(ByteArray(32) { 0xFF.toByte() })

        mockTransport.triggerMessage("""{"v":1,"t":"ping","d":"l2p","ctr":1,"c":"$badCiphertext"}""")
        assertFalse(mockTransport.isClosed)

        mockTransport.triggerMessage("""{"v":1,"t":"ping","d":"l2p","ctr":2,"c":"$badCiphertext"}""")
        assertFalse(mockTransport.isClosed)

        mockTransport.triggerMessage("""{"v":1,"t":"ping","d":"l2p","ctr":3,"c":"$badCiphertext"}""")
        assertTrue(mockTransport.isClosed)
        assertEquals(1008, mockTransport.lastClosedCode)
        assertEquals("tag_failure_limit", mockTransport.lastClosedReason)
    }

    @Test
    fun testPingRespondsWithPong() {
        val client = RelayClient(
            pk = pk,
            sidHex = sidHex,
            relayUrl = "ws://127.0.0.1:8080/ws",
            transport = mockTransport,
            scope = testScope,
            getLastSeqAcked = { 0L },
            secureRandomBytes = fixedRandomBytes
        )

        client.connect()
        mockTransport.triggerOpen()

        val phoneSn = fixedRandomBytes(16)
        val laptopSn = ByteArray(16) { 7.toByte() }
        val laptopSnBase64 = Base64.getEncoder().encodeToString(laptopSn)
        val laptopMac = SyncCrypto.computeHelloMac(pk, "laptop", laptopSnBase64, 0L)
        val laptopMacBase64 = Base64.getEncoder().encodeToString(laptopMac)

        mockTransport.triggerMessage(
            """{"v":1,"t":"hello","device":"laptop","sn":"$laptopSnBase64","proto":[1],"lastSeqAcked":0,"mac":"$laptopMacBase64"}"""
        )

        val peerSessionKeys = SyncCrypto.deriveSessionKeys(pk, laptopSn, phoneSn)

        // Peer sends ping {t0: 9999}
        val pingPlain = """{"mid":"m-ping","ts":1000,"b":{"t0":9999}}"""
        val c = SyncCrypto.encryptFramePayload(peerSessionKeys.kL2P, "ping", "l2p", 1L, pingPlain.toByteArray(StandardCharsets.UTF_8))

        val initialSentCount = mockTransport.sentMessages.size
        mockTransport.triggerMessage("""{"v":1,"t":"ping","d":"l2p","ctr":1,"c":"$c"}""")

        // Phone should have sent pong frame
        assertEquals(initialSentCount + 1, mockTransport.sentMessages.size)
        val pongFrameJson = mockTransport.sentMessages.last()
        val pongFrame = JsonReader.parse(pongFrameJson) as Map<*, *>
        assertEquals("pong", pongFrame["t"])
        assertEquals("p2l", pongFrame["d"])

        val decryptedPong = SyncCrypto.decryptFramePayload(
            peerSessionKeys.kP2L,
            "pong",
            "p2l",
            (pongFrame["ctr"] as Number).toLong(),
            pongFrame["c"] as String
        )
        val pongPayload = JsonReader.parse(String(decryptedPong, StandardCharsets.UTF_8)) as Map<*, *>
        val pongBody = pongPayload["b"] as Map<*, *>
        assertEquals(9999L, (pongBody["t0"] as Number).toLong())
        assertNotNull(pongBody["t1"])
        assertNotNull(pongBody["t2"])
    }
}

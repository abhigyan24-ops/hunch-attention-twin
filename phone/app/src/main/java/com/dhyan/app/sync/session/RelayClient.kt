package com.dhyan.app.sync.session

import com.dhyan.app.personas.json.JsonReader
import com.dhyan.app.sync.crypto.SyncCrypto
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.UUID
import kotlin.math.min
import kotlin.math.pow

/**
 * Full Session Client for Dhyan Sync Protocol (Milestone M4).
 * Spec sections: 5.1-5.5, 6, 7.1, 16.
 *
 * Handles authenticated hello handshake, symmetric AES-256-GCM framing,
 * counter sequencing, tamper/replay rejection, 3-tag-failure policy close,
 * automatic pong response, and exponential reconnect backoff with jitter.
 */
class RelayClient(
    private val pk: ByteArray,
    private val sidHex: String,
    private val relayUrl: String,
    private val transport: SessionTransport,
    private val scope: CoroutineScope,
    private val getLastSeqAcked: () -> Long = { 0L },
    private val timeProvider: () -> Long = System::currentTimeMillis,
    private val secureRandomBytes: (Int) -> ByteArray = { size ->
        val bytes = ByteArray(size)
        SecureRandom().nextBytes(bytes)
        bytes
    },
    private val device: String = "phone",
    private val peerDevice: String = "laptop"
) {
    private val _state = MutableStateFlow<SessionState>(SessionState.Disconnected)
    val state: StateFlow<SessionState> = _state.asStateFlow()

    private var sessionKeys: SyncCrypto.SessionKeys? = null
    var outboundCtr: Long = 0L
        private set
    var inboundCtr: Long = 0L
        private set

    private var localSnBytes: ByteArray? = null
    private var reconnectAttempt: Int = 0
    private var reconnectJob: Job? = null
    private var isIntentionalDisconnect: Boolean = false

    private val tagFailures = mutableListOf<Long>()
    private val helloFailures = mutableListOf<Long>()

    var onMessageReceived: ((type: String, bodyJson: String, mid: String, ts: Long) -> Unit)? = null
    var onPongReceived: ((t0: Long, t1: Long, t2: Long, rxTime: Long) -> Unit)? = null

    private val transportListener = object : SessionTransportListener {
        override fun onOpen() {
            tagFailures.clear()
            helloFailures.clear()
            _state.value = SessionState.Handshaking
            sendLocalHello()
        }

        override fun onMessage(text: String) {
            handleRawIncoming(text)
        }

        override fun onFailure(t: Throwable) {
            handleConnectionDrop(t.message ?: "Transport failure")
        }

        override fun onClosed(code: Int, reason: String) {
            if (code == 1000 || isIntentionalDisconnect) {
                _state.value = SessionState.Disconnected
            } else {
                handleConnectionDrop("Closed ($code): $reason")
            }
        }
    }

    /**
     * Initiates connection to the session room.
     */
    fun connect() {
        isIntentionalDisconnect = false
        reconnectJob?.cancel()
        _state.value = SessionState.Connecting
        transport.connect(relayUrl, sidHex, device, transportListener)
    }

    /**
     * Gracefully disconnects and cancels any scheduled reconnects.
     */
    fun disconnect() {
        isIntentionalDisconnect = true
        reconnectJob?.cancel()
        transport.close(1000, "Normal closure")
        _state.value = SessionState.Disconnected
        sessionKeys = null
    }

    /**
     * Sends an encrypted frame with the specified message type and JSON body.
     * Returns true if queued to transport, false if not connected or error.
     */
    fun sendEncrypted(type: String, bodyJson: String): Boolean {
        val keys = sessionKeys ?: return false
        if (_state.value !is SessionState.Connected) return false

        outboundCtr++
        if (outboundCtr > 1_000_000_000L) {
            // Reconnect to rekey safely (Section 5.4)
            reconnect()
            return false
        }

        val mid = UUID.randomUUID().toString()
        val ts = timeProvider()
        val plaintext = """{"mid":"$mid","ts":$ts,"b":$bodyJson}"""
        val c = SyncCrypto.encryptFramePayload(
            keys.kP2L,
            type,
            "p2l",
            outboundCtr,
            plaintext.toByteArray(StandardCharsets.UTF_8)
        )

        val frameJson = """{"v":1,"t":"$type","d":"p2l","ctr":$outboundCtr,"c":"$c"}"""
        return transport.send(frameJson)
    }

    /**
     * Sends a ping frame with the sender's clock timestamp.
     */
    fun sendPing(t0: Long = timeProvider()): Boolean {
        return sendEncrypted("ping", """{"t0":$t0}""")
    }

    /**
     * Sends a pong reply to a received ping frame.
     */
    fun sendPong(t0: Long, rxTime: Long, txTime: Long = timeProvider()): Boolean {
        return sendEncrypted("pong", """{"t0":$t0,"t1":$rxTime,"t2":$txTime}""")
    }

    private fun sendLocalHello() {
        val snBytes = secureRandomBytes(16)
        localSnBytes = snBytes
        val snBase64 = Base64.getEncoder().encodeToString(snBytes)
        val lastSeq = getLastSeqAcked()
        val mac = SyncCrypto.computeHelloMac(pk, device, snBase64, lastSeq)
        val macBase64 = Base64.getEncoder().encodeToString(mac)

        val helloJson = """{"v":1,"t":"hello","device":"$device","sn":"$snBase64","proto":[1],"lastSeqAcked":$lastSeq,"mac":"$macBase64"}"""
        transport.send(helloJson)
    }

    private fun handleRawIncoming(text: String) {
        val parsed = try {
            JsonReader.parse(text) as? Map<*, *>
        } catch (_: Exception) {
            return
        } ?: return

        val v = (parsed["v"] as? Number)?.toInt() ?: return
        if (v != 1) return
        val t = parsed["t"] as? String ?: return

        if (t == "hello") {
            handlePeerHello(parsed)
            return
        }

        handleEncryptedFrame(t, parsed)
    }

    private fun handlePeerHello(map: Map<*, *>) {
        val fromDevice = map["device"] as? String ?: return
        if (fromDevice != peerDevice) return

        val snBase64 = map["sn"] as? String ?: return
        val lastSeq = (map["lastSeqAcked"] as? Number)?.toLong() ?: 0L
        val macBase64 = map["mac"] as? String ?: return

        // Authenticate peer hello MAC
        val expectedMac = SyncCrypto.computeHelloMac(pk, peerDevice, snBase64, lastSeq)
        val actualMac = try {
            Base64.getDecoder().decode(macBase64)
        } catch (_: Exception) {
            ByteArray(0)
        }

        val now = timeProvider()
        if (!MessageDigest.isEqual(expectedMac, actualMac)) {
            helloFailures.add(now)
            pruneFailures(helloFailures, now)
            if (helloFailures.size >= 3) {
                transport.close(1008, "hello_mac_limit")
                _state.value = SessionState.Failed("hello_mac_limit")
            }
            return
        }

        // Protocol version negotiation
        val protoList = (map["proto"] as? List<*>)?.mapNotNull { (it as? Number)?.toInt() } ?: emptyList()
        if (!protoList.contains(1)) {
            val err = """{"v":1,"t":"error","code":"unsupported_version","detail":"No matching protocol version"}"""
            transport.send(err)
            _state.value = SessionState.Failed("unsupported_version")
            transport.close(1008, "unsupported_version")
            return
        }

        // Handshake complete: derive session keys
        val peerSn = Base64.getDecoder().decode(snBase64)
        val localSn = localSnBytes ?: return

        // Contract Section 5.3: salt = snL || snP
        val keys = SyncCrypto.deriveSessionKeys(pk, peerSn, localSn)
        sessionKeys = keys
        outboundCtr = 0L
        inboundCtr = 0L
        reconnectAttempt = 0
        _state.value = SessionState.Connected(peerLastSeqAcked = lastSeq)
    }

    private fun handleEncryptedFrame(t: String, map: Map<*, *>) {
        val keys = sessionKeys ?: return
        if (_state.value !is SessionState.Connected) return

        val d = map["d"] as? String ?: return
        if (d != "l2p") return // Only accept frames directed to phone

        val ctr = (map["ctr"] as? Number)?.toLong() ?: return
        if (ctr <= inboundCtr) return // Replay or reorder rejection

        val c = map["c"] as? String ?: return
        val decryptedBytes = try {
            SyncCrypto.decryptFramePayload(keys.kL2P, t, "l2p", ctr, c)
        } catch (_: Exception) {
            val now = timeProvider()
            tagFailures.add(now)
            pruneFailures(tagFailures, now)
            if (tagFailures.size >= 3) {
                transport.close(1008, "tag_failure_limit")
                _state.value = SessionState.Failed("tag_failure_limit")
            }
            return
        }

        // Section 16 byte limit on total decrypted frame
        if (decryptedBytes.size > 32_768) {
            return
        }

        // Advance inbound counter only after successful AEAD authentication
        inboundCtr = ctr

        val plaintextStr = String(decryptedBytes, StandardCharsets.UTF_8)
        val parsedPayload = try {
            JsonReader.parse(plaintextStr) as? Map<*, *>
        } catch (_: Exception) {
            return
        } ?: return

        val mid = parsedPayload["mid"] as? String ?: return
        val ts = (parsedPayload["ts"] as? Number)?.toLong() ?: return
        val b = parsedPayload["b"] ?: return

        // Dispatch ping / pong or message
        if (t == "ping") {
            val bMap = b as? Map<*, *>
            val t0 = (bMap?.get("t0") as? Number)?.toLong() ?: ts
            val rx = timeProvider()
            sendPong(t0, rxTime = rx, txTime = rx)
        } else if (t == "pong") {
            val bMap = b as? Map<*, *>
            val t0 = (bMap?.get("t0") as? Number)?.toLong() ?: 0L
            val t1 = (bMap?.get("t1") as? Number)?.toLong() ?: 0L
            val t2 = (bMap?.get("t2") as? Number)?.toLong() ?: 0L
            onPongReceived?.invoke(t0, t1, t2, timeProvider())
        }

        val bodyJson = serializeJson(b)
        onMessageReceived?.invoke(t, bodyJson, mid, ts)
    }

    private fun handleConnectionDrop(reason: String) {
        if (isIntentionalDisconnect) return
        scheduleReconnect()
    }

    private fun scheduleReconnect() {
        reconnectAttempt++
        val base = min(1000L * (1.5.pow(reconnectAttempt - 1)).toLong(), 30_000L)
        // Jitter between 0.8x and 1.2x
        val jitter = 0.8 + 0.4 * (secureRandomBytes(1)[0].toInt() and 0xFF) / 255.0
        val delayMs = (base * jitter).toLong()

        _state.value = SessionState.Reconnecting(reconnectAttempt, delayMs)
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            delay(delayMs)
            if (!isIntentionalDisconnect) {
                connect()
            }
        }
    }

    private fun reconnect() {
        transport.close(1000, "Rekeying")
        connect()
    }

    private fun pruneFailures(list: MutableList<Long>, now: Long) {
        val it = list.iterator()
        while (it.hasNext()) {
            if (now - it.next() > 60_000L) {
                it.remove()
            }
        }
    }

    companion object {
        fun serializeJson(value: Any?): String = when (value) {
            null -> "null"
            is Boolean -> value.toString()
            is Number -> value.toString()
            is String -> buildString {
                append('"')
                for (c in value) {
                    when (c) {
                        '\\' -> append("\\\\")
                        '"' -> append("\\\"")
                        '\b' -> append("\\b")
                        '\u000c' -> append("\\f")
                        '\n' -> append("\\n")
                        '\r' -> append("\\r")
                        '\t' -> append("\\t")
                        else -> if (c.code < 32) append(String.format("\\u%04x", c.code)) else append(c)
                    }
                }
                append('"')
            }
            is Map<*, *> -> value.entries.joinToString(prefix = "{", postfix = "}") { (k, v) ->
                "${serializeJson(k.toString())}:${serializeJson(v)}"
            }
            is List<*> -> value.joinToString(prefix = "[", postfix = "]") { serializeJson(it) }
            else -> serializeJson(value.toString())
        }
    }
}

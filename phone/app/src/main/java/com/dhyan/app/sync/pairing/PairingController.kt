package com.dhyan.app.sync.pairing

import com.dhyan.app.data.SettingsDao
import com.dhyan.app.data.SettingsEntity
import com.dhyan.app.personas.json.JsonReader
import com.dhyan.app.sync.crypto.KeystoreKeyWrapper
import com.dhyan.app.sync.crypto.SyncCrypto
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.security.KeyPair
import java.security.interfaces.ECPrivateKey
import java.security.interfaces.ECPublicKey
import java.util.Base64

class PairingController(
    private val settingsDao: SettingsDao,
    private val keyWrapper: KeystoreKeyWrapper,
    private val transport: RendezvousTransport,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default)
) {
    companion object {
        const val KEY_PAIRING_PK = "sync_permanent_pairing_key"
        const val KEY_SESSION_ROOM_ID = "sync_session_room_id"
    }

    private val _state = MutableStateFlow<PairingState>(PairingState.Idle)
    val state: StateFlow<PairingState> = _state.asStateFlow()

    // Ephemeral pairing context (cleared upon completion/failure)
    private var phoneKeyPair: KeyPair? = null
    private var phoneNonce: ByteArray? = null
    private var laptopCommit: ByteArray? = null
    private var laptopPublicKey: ECPublicKey? = null
    private var laptopNonce: ByteArray? = null
    private var sharedSecret: ByteArray? = null
    private var transcriptHash: ByteArray? = null
    private var pendingPermanentKey: SyncCrypto.PairingKeyAndRoom? = null

    init {
        scope.launch {
            checkExistingPairing()
        }
    }

    suspend fun checkExistingPairing(): Boolean {
        val wrappedPk = settingsDao.get(KEY_PAIRING_PK)
        val sid = settingsDao.get(KEY_SESSION_ROOM_ID)
        return if (!wrappedPk.isNullOrBlank() && !sid.isNullOrBlank()) {
            _state.value = PairingState.Paired(sid)
            true
        } else {
            if (_state.value is PairingState.Paired) {
                _state.value = PairingState.Idle
            }
            false
        }
    }

    fun startPairing(code: String, relayUrl: String) {
        val cleanCode = code.uppercase().trim()
        require(cleanCode.length == 6) { "Pairing code must be exactly 6 characters" }
        require(cleanCode.all { it in SyncCrypto.CODE_ALPHABET }) { "Invalid character in pairing code" }

        clearEphemeralContext()
        val roomId = SyncCrypto.getRendezvousRoomId(cleanCode)

        // Generate ephemeral P-256 key pair & 16-byte nonce
        val kp = SyncCrypto.generateP256KeyPair()
        phoneKeyPair = kp
        val nonce = ByteArray(16).also { java.security.SecureRandom().nextBytes(it) }
        phoneNonce = nonce

        _state.value = PairingState.Connecting(roomId)

        transport.connect(
            url = relayUrl,
            roomId = roomId,
            onMessage = { msg -> handleIncomingFrame(msg) },
            onError = { err ->
                _state.value = PairingState.Failed("Relay connection error")
                clearEphemeralContext()
            },
            onClose = {
                if (_state.value !is PairingState.Paired && _state.value !is PairingState.Cancelled) {
                    _state.value = PairingState.Failed("Relay disconnected prematurely")
                    clearEphemeralContext()
                }
            }
        )

        // Dispatch pair_join immediately upon connecting
        val pkBase64 = Base64.getEncoder().encodeToString(SyncCrypto.encodePublicKeyUncompressed(kp.public as ECPublicKey))
        val nonceBase64 = Base64.getEncoder().encodeToString(nonce)
        val joinJson = "{\"v\":1,\"t\":\"pair_join\",\"device\":\"phone\",\"pk\":\"$pkBase64\",\"n\":\"$nonceBase64\"}"
        transport.send(joinJson)

        if (laptopCommit == null) {
            _state.value = PairingState.WaitingForLaptopOffer
        }
    }

    fun confirmSas(accepted: Boolean) {
        if (!accepted) {
            sendCancel("User rejected SAS code")
            _state.value = PairingState.Cancelled("Rejected by user")
            clearEphemeralContext()
            return
        }

        val secret = sharedSecret ?: run {
            _state.value = PairingState.Failed("Missing shared secret")
            return
        }
        val t = transcriptHash ?: run {
            _state.value = PairingState.Failed("Missing transcript hash")
            return
        }

        _state.value = PairingState.ExchangingConfirmation

        // Send pair_confirm
        val macP = SyncCrypto.computePairConfirmMac(secret, t, "phone")
        val macBase64 = Base64.getEncoder().encodeToString(macP)
        val confirmJson = "{\"v\":1,\"t\":\"pair_confirm\",\"device\":\"phone\",\"mac\":\"$macBase64\"}"
        transport.send(confirmJson)
    }

    fun cancelPairing(reason: String = "Cancelled by user") {
        sendCancel(reason)
        _state.value = PairingState.Cancelled(reason)
        clearEphemeralContext()
    }

    suspend fun unpair() {
        settingsDao.delete(KEY_PAIRING_PK)
        settingsDao.delete(KEY_SESSION_ROOM_ID)
        clearEphemeralContext()
        _state.value = PairingState.Idle
    }

    @Suppress("UNCHECKED_CAST")
    private fun handleIncomingFrame(messageJson: String) {
        try {
            val map = JsonReader.parse(messageJson) as? Map<String, Any?> ?: return
            val t = map["t"] as? String ?: return

            when (t) {
                "pair_offer" -> {
                    val commitBase64 = map["commit"] as? String ?: return
                    laptopCommit = Base64.getDecoder().decode(commitBase64)
                    if (_state.value is PairingState.WaitingForLaptopOffer) {
                        _state.value = PairingState.WaitingForLaptopReveal
                    }
                }
                "pair_reveal" -> {
                    handlePairReveal(map)
                }
                "pair_confirm" -> {
                    handlePairConfirm(map)
                }
                "pair_cancel" -> {
                    val reason = map["reason"] as? String ?: "Remote peer cancelled"
                    _state.value = PairingState.Cancelled(reason)
                    transport.close()
                    clearEphemeralContext()
                }
            }
        } catch (e: Exception) {
            _state.value = PairingState.Failed("Malformed message from peer")
            transport.close()
            clearEphemeralContext()
        }
    }

    private fun handlePairReveal(map: Map<String, Any?>) {
        val commit = laptopCommit
        if (commit == null) {
            sendCancel("Reveal received before offer")
            _state.value = PairingState.Failed("Protocol error: reveal before offer")
            return
        }

        val pkBase64 = map["pk"] as? String ?: return
        val nBase64 = map["n"] as? String ?: return

        val pkLBytes = Base64.getDecoder().decode(pkBase64)
        val nLBytes = Base64.getDecoder().decode(nBase64)

        // Verify laptop commitment: SHA256(pkL || nL) == commitL
        val computedCommit = SyncCrypto.computePairCommit(pkLBytes, nLBytes)
        if (!computedCommit.contentEquals(commit)) {
            sendCancel("Commitment verification failed")
            _state.value = PairingState.Failed("Commitment mismatch")
            return
        }

        val pubL = SyncCrypto.decodePublicKeyUncompressed(pkLBytes)
        laptopPublicKey = pubL
        laptopNonce = nLBytes

        val kp = phoneKeyPair ?: run {
            _state.value = PairingState.Failed("Internal error: missing phone key pair")
            return
        }
        val nPBytes = phoneNonce ?: run {
            _state.value = PairingState.Failed("Internal error: missing phone nonce")
            return
        }

        // Compute ECDH shared secret & transcript
        val secret = SyncCrypto.computeECDH(kp.private as ECPrivateKey, pubL)
        sharedSecret = secret

        val pkPBytes = SyncCrypto.encodePublicKeyUncompressed(kp.public as ECPublicKey)
        val t = SyncCrypto.computeTranscriptHash(pkLBytes, pkPBytes, nLBytes, nPBytes)
        transcriptHash = t

        val sas = SyncCrypto.computeSas(t)
        pendingPermanentKey = SyncCrypto.derivePairingKeyAndRoom(secret, t)

        _state.value = PairingState.AwaitingUserConfirmation(sas)
    }

    private fun handlePairConfirm(map: Map<String, Any?>) {
        val device = map["device"] as? String ?: return
        if (device != "laptop") return

        val macBase64 = map["mac"] as? String ?: return
        val macL = Base64.getDecoder().decode(macBase64)

        val secret = sharedSecret ?: return
        val t = transcriptHash ?: return
        val keyAndRoom = pendingPermanentKey ?: return

        val expectedMacL = SyncCrypto.computePairConfirmMac(secret, t, "laptop")
        if (!expectedMacL.contentEquals(macL)) {
            sendCancel("Confirmation MAC verification failed")
            _state.value = PairingState.Failed("Peer confirmation mismatch")
            return
        }

        // MAC verified! Persist permanent pairing key & sid
        scope.launch {
            try {
                val wrappedPk = keyWrapper.wrap(keyAndRoom.pk)
                val now = System.currentTimeMillis()
                settingsDao.set(SettingsEntity(KEY_PAIRING_PK, wrappedPk, now))
                settingsDao.set(SettingsEntity(KEY_SESSION_ROOM_ID, keyAndRoom.sidHex, now))

                _state.value = PairingState.Paired(keyAndRoom.sidHex)
                transport.close()
                clearEphemeralContext()
            } catch (e: Exception) {
                _state.value = PairingState.Failed("Failed to save credentials")
                clearEphemeralContext()
            }
        }
    }

    private fun sendCancel(reason: String) {
        try {
            val json = "{\"v\":1,\"t\":\"pair_cancel\",\"device\":\"phone\",\"reason\":\"$reason\"}"
            transport.send(json)
            transport.close()
        } catch (_: Exception) {}
    }

    private fun clearEphemeralContext() {
        phoneKeyPair = null
        phoneNonce = null
        laptopCommit = null
        laptopPublicKey = null
        laptopNonce = null
        sharedSecret = null
        transcriptHash = null
        pendingPermanentKey = null
    }
}

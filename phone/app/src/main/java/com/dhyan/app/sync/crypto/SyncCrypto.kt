package com.dhyan.app.sync.crypto

import java.math.BigInteger
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.security.AlgorithmParameters
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.interfaces.ECPrivateKey
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import java.security.spec.ECParameterSpec
import java.security.spec.ECPoint
import java.security.spec.ECPrivateKeySpec
import java.security.spec.ECPublicKeySpec
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Pure JCA implementation of Dhyan Sync Protocol Cryptography.
 * References: contracts/sync/ref/crypto.mjs and SYNC_CONTRACT.md sections 3, 4, 5, 6.
 */
object SyncCrypto {

    const val CODE_ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ"

    private val ecSpec: ECParameterSpec by lazy {
        val params = AlgorithmParameters.getInstance("EC")
        params.init(ECGenParameterSpec("secp256r1"))
        params.getParameterSpec(ECParameterSpec::class.java)
    }

    private val keyFactory: KeyFactory by lazy {
        KeyFactory.getInstance("EC")
    }

    private val secureRandom = SecureRandom()

    /**
     * Generates a 6-character pairing code from the 32-character alphabet.
     */
    fun generatePairingCode(): String {
        val sb = java.lang.StringBuilder(6)
        for (i in 0 until 6) {
            val idx = secureRandom.nextInt(CODE_ALPHABET.length)
            sb.append(CODE_ALPHABET[idx])
        }
        return sb.toString()
    }

    /**
     * Derives the 64-hex rendezvous room ID:
     * hex(sha256("dhyan-rendezvous-v1|" + code.toUpperCase().trim()))
     */
    fun getRendezvousRoomId(code: String): String {
        val norm = code.uppercase().trim()
        val input = "dhyan-rendezvous-v1|$norm".toByteArray(StandardCharsets.UTF_8)
        val digest = MessageDigest.getInstance("SHA-256").digest(input)
        return digest.toHex()
    }

    /**
     * Generates a P-256 ephemeral key pair.
     */
    fun generateP256KeyPair(): KeyPair {
        val kpg = KeyPairGenerator.getInstance("EC")
        kpg.initialize(ECGenParameterSpec("secp256r1"))
        return kpg.generateKeyPair()
    }

    /**
     * Decodes a 32-byte private scalar into an ECPrivateKey.
     */
    fun privateKeyFromScalar(scalarBytes: ByteArray): ECPrivateKey {
        val s = BigInteger(1, scalarBytes)
        val spec = ECPrivateKeySpec(s, ecSpec)
        return keyFactory.generatePrivate(spec) as ECPrivateKey
    }

    /**
     * Encodes an ECPublicKey into 65-byte uncompressed SEC1 format: 0x04 || X || Y.
     */
    fun encodePublicKeyUncompressed(publicKey: ECPublicKey): ByteArray {
        val w = publicKey.w
        val xBytes = toFixedByteArray(w.affineX, 32)
        val yBytes = toFixedByteArray(w.affineY, 32)
        val out = ByteArray(65)
        out[0] = 0x04.toByte()
        System.arraycopy(xBytes, 0, out, 1, 32)
        System.arraycopy(yBytes, 0, out, 33, 32)
        return out
    }

    /**
     * Decodes a 65-byte uncompressed SEC1 format (0x04 || X || Y) into an ECPublicKey.
     */
    fun decodePublicKeyUncompressed(bytes: ByteArray): ECPublicKey {
        require(bytes.size == 65 && bytes[0] == 0x04.toByte()) {
            "Invalid uncompressed public key: expected 65 bytes starting with 0x04"
        }
        val x = BigInteger(1, bytes.copyOfRange(1, 33))
        val y = BigInteger(1, bytes.copyOfRange(33, 65))
        val point = ECPoint(x, y)
        val spec = ECPublicKeySpec(point, ecSpec)
        return keyFactory.generatePublic(spec) as ECPublicKey
    }

    /**
     * Computes ECDH shared secret: 32-byte big-endian X-coordinate.
     */
    fun computeECDH(privateKey: ECPrivateKey, peerPublicKey: ECPublicKey): ByteArray {
        val ka = KeyAgreement.getInstance("ECDH")
        ka.init(privateKey)
        ka.doPhase(peerPublicKey, true)
        val rawSecret = ka.generateSecret()
        // Ensure exact 32 bytes (big endian)
        return if (rawSecret.size == 32) {
            rawSecret
        } else if (rawSecret.size > 32) {
            rawSecret.copyOfRange(rawSecret.size - 32, rawSecret.size)
        } else {
            val padded = ByteArray(32)
            System.arraycopy(rawSecret, 0, padded, 32 - rawSecret.size, rawSecret.size)
            padded
        }
    }

    /**
     * HKDF-SHA256 (RFC 5869) Extract and Expand.
     */
    fun hkdfSha256(secret: ByteArray, salt: ByteArray?, info: ByteArray?, length: Int): ByteArray {
        val effectiveSalt = if (salt == null || salt.isEmpty()) ByteArray(32) else salt
        val effectiveInfo = info ?: ByteArray(0)

        // 1. Extract: PRK = HMAC-SHA256(salt, secret)
        val hmacExtract = Mac.getInstance("HmacSHA256")
        hmacExtract.init(SecretKeySpec(effectiveSalt, "HmacSHA256"))
        val prk = hmacExtract.doFinal(secret)

        // 2. Expand: T(1) || T(2) ... up to length
        val hmacExpand = Mac.getInstance("HmacSHA256")
        hmacExpand.init(SecretKeySpec(prk, "HmacSHA256"))

        val result = ByteArray(length)
        var t = ByteArray(0)
        var generated = 0
        var round = 1

        while (generated < length) {
            hmacExpand.reset()
            if (round > 1) {
                hmacExpand.update(t)
            }
            hmacExpand.update(effectiveInfo)
            hmacExpand.update(round.toByte())
            t = hmacExpand.doFinal()

            val toCopy = Math.min(t.size, length - generated)
            System.arraycopy(t, 0, result, generated, toCopy)
            generated += toCopy
            round++
        }

        return result
    }

    /**
     * Computes pair commit: SHA-256(pk || nonce)
     */
    fun computePairCommit(pk: ByteArray, nonce: ByteArray): ByteArray {
        val md = MessageDigest.getInstance("SHA-256")
        md.update(pk)
        md.update(nonce)
        return md.digest()
    }

    /**
     * Transcript hash T = SHA-256("dhyan-pair-v1" || pkL || pkP || nL || nP)
     */
    fun computeTranscriptHash(pkL: ByteArray, pkP: ByteArray, nL: ByteArray, nP: ByteArray): ByteArray {
        val md = MessageDigest.getInstance("SHA-256")
        md.update("dhyan-pair-v1".toByteArray(StandardCharsets.UTF_8))
        md.update(pkL)
        md.update(pkP)
        md.update(nL)
        md.update(nP)
        return md.digest()
    }

    /**
     * Short Authentication String (SAS): uint32_be(T[0..4]) mod 1000000, 6 digits grouped "123 456".
     */
    fun computeSas(t: ByteArray): String {
        require(t.size >= 4) { "Transcript hash too short" }
        val num = ((t[0].toInt() and 0xFF).toLong() shl 24) or
                ((t[1].toInt() and 0xFF).toLong() shl 16) or
                ((t[2].toInt() and 0xFF).toLong() shl 8) or
                (t[3].toInt() and 0xFF).toLong()
        val sasVal = (num % 1000000).toInt()
        val str = String.format("%06d", sasVal)
        return "${str.substring(0, 3)} ${str.substring(3)}"
    }

    /**
     * Derives kConf = HKDF(secret, salt=T, info="dhyan-pair-confirm-v1", 32)
     * and computes confirm MAC = HMAC-SHA256(kConf, "confirm|" + device).
     */
    fun computePairConfirmMac(secret: ByteArray, t: ByteArray, device: String): ByteArray {
        val kConf = hkdfSha256(secret, t, "dhyan-pair-confirm-v1".toByteArray(StandardCharsets.UTF_8), 32)
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(kConf, "HmacSHA256"))
        return mac.doFinal("confirm|$device".toByteArray(StandardCharsets.UTF_8))
    }

    /**
     * Derives permanent pairing key PK = HKDF(secret, salt=T, info="dhyan-pairing-key-v1", 32)
     * and permanent session room sid = hex(HKDF(PK, salt="", info="dhyan-room-v1", 32)).
     */
    data class PairingKeyAndRoom(val pk: ByteArray, val sidHex: String)

    fun derivePairingKeyAndRoom(secret: ByteArray, t: ByteArray): PairingKeyAndRoom {
        val pk = hkdfSha256(secret, t, "dhyan-pairing-key-v1".toByteArray(StandardCharsets.UTF_8), 32)
        val sidBuf = hkdfSha256(pk, ByteArray(0), "dhyan-room-v1".toByteArray(StandardCharsets.UTF_8), 32)
        return PairingKeyAndRoom(pk, sidBuf.toHex())
    }

    /**
     * Derives kHello = HKDF(PK, salt="", info="dhyan-hello-mac-v1", 32)
     * and computes hello MAC = HMAC-SHA256(kHello, "hello|" + device + "|" + snBase64 + "|" + lastSeqAcked).
     */
    fun computeHelloMac(pk: ByteArray, device: String, snBase64: String, lastSeqAcked: Long): ByteArray {
        val kHello = hkdfSha256(pk, ByteArray(0), "dhyan-hello-mac-v1".toByteArray(StandardCharsets.UTF_8), 32)
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(kHello, "HmacSHA256"))
        val data = "hello|$device|$snBase64|$lastSeqAcked".toByteArray(StandardCharsets.UTF_8)
        return mac.doFinal(data)
    }

    /**
     * Derives session keys:
     * okm = HKDF(PK, salt = snL || snP, info = "dhyan-session-v1", 64)
     * k_l2p = okm[0..32], k_p2l = okm[32..64]
     */
    data class SessionKeys(val kL2P: ByteArray, val kP2L: ByteArray)

    fun deriveSessionKeys(pk: ByteArray, snL: ByteArray, snP: ByteArray): SessionKeys {
        val salt = ByteArray(snL.size + snP.size)
        System.arraycopy(snL, 0, salt, 0, snL.size)
        System.arraycopy(snP, 0, salt, snL.size, snP.size)
        val okm = hkdfSha256(pk, salt, "dhyan-session-v1".toByteArray(StandardCharsets.UTF_8), 64)
        return SessionKeys(
            kL2P = okm.copyOfRange(0, 32),
            kP2L = okm.copyOfRange(32, 64)
        )
    }

    /**
     * Computes 12-byte GCM nonce: 0x00000000 || ctr (64-bit big endian).
     */
    fun buildNonce(ctr: Long): ByteArray {
        val buffer = ByteBuffer.allocate(12)
        buffer.putInt(0) // 4 bytes zeros
        buffer.putLong(ctr) // 8 bytes ctr big endian
        return buffer.array()
    }

    /**
     * Builds AAD: ASCII "dhyan|1|" + t + "|" + d + "|" + ctr.
     */
    fun buildAad(t: String, d: String, ctr: Long): ByteArray {
        return "dhyan|1|$t|$d|$ctr".toByteArray(StandardCharsets.US_ASCII)
    }

    /**
     * Encrypts frame payload using AES-256-GCM.
     * Returns Base64 of (ciphertext || 16-byte tag).
     */
    fun encryptFramePayload(key: ByteArray, t: String, d: String, ctr: Long, plaintext: ByteArray): String {
        val nonce = buildNonce(ctr)
        val aad = buildAad(t, d, ctr)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
        cipher.updateAAD(aad)
        val ciphertextWithTag = cipher.doFinal(plaintext)
        return Base64.getEncoder().encodeToString(ciphertextWithTag)
    }

    /**
     * Decrypts frame payload using AES-256-GCM.
     * Takes Base64 of (ciphertext || 16-byte tag). Throws AEADBadTagException if altered.
     */
    fun decryptFramePayload(key: ByteArray, t: String, d: String, ctr: Long, cBase64: String): ByteArray {
        val full = Base64.getDecoder().decode(cBase64)
        val nonce = buildNonce(ctr)
        val aad = buildAad(t, d, ctr)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
        cipher.updateAAD(aad)
        return cipher.doFinal(full)
    }

    private fun toFixedByteArray(bi: BigInteger, size: Int): ByteArray {
        val raw = bi.toByteArray()
        if (raw.size == size) return raw
        val out = ByteArray(size)
        if (raw.size > size) {
            // Drop sign byte
            System.arraycopy(raw, raw.size - size, out, 0, size)
        } else {
            System.arraycopy(raw, 0, out, size - raw.size, raw.size)
        }
        return out
    }

    private fun ByteArray.toHex(): String {
        val sb = java.lang.StringBuilder(size * 2)
        for (b in this) {
            sb.append(String.format("%02x", b.toInt() and 0xFF))
        }
        return sb.toString()
    }
}

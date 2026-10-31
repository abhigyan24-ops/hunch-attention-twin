package com.dhyan.app.sync

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.nio.ByteBuffer
import java.security.*
import java.security.spec.ECGenParameterSpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.security.auth.x500.X500Principal

/**
 * P-256 Device Identity stored securely in the hardware-backed AndroidKeyStore.
 */
class DeviceIdentity(private val keyStoreProvider: String = "AndroidKeyStore") {
    private val ks = KeyStore.getInstance(keyStoreProvider).apply { load(null) }
    private val alias = "dhyan_attwin_id_v1"

    init {
        if (!ks.containsAlias(alias)) {
            val kpg = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, keyStoreProvider)
            kpg.initialize(
                KeyGenParameterSpec.Builder(
                    alias,
                    KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
                )
                    .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
                    .setDigests(KeyProperties.DIGEST_SHA256)
                    .setCertificateSubject(X500Principal("CN=DhyanPhone"))
                    .build()
            )
            kpg.generateKeyPair()
        }
    }

    val publicKeyDer: ByteArray
        get() = ks.getCertificate(alias).publicKey.encoded

    val id: ByteArray
        get() = MessageDigest.getInstance("SHA-256").digest(publicKeyDer).copyOf(16)

    fun sign(message: ByteArray): ByteArray {
        val s = Signature.getInstance("SHA256withECDSA")
        s.initSign(ks.getKey(alias, null) as PrivateKey)
        s.update(message)
        return s.sign()
    }
}

/**
 * Cryptographic primitives matching Node.js session crypto.
 */
object CryptoPrimitives {
    fun transcript(vararg parts: ByteArray): ByteArray {
        val md = MessageDigest.getInstance("SHA-256")
        md.update("attwin-v1".toByteArray(Charsets.UTF_8))
        val lenBuf = ByteBuffer.allocate(4)
        for (p in parts) {
            lenBuf.clear()
            lenBuf.putInt(p.size)
            md.update(lenBuf.array())
            md.update(p)
        }
        return md.digest()
    }

    fun roleTag(label: String, th: ByteArray): ByteArray {
        val md = MessageDigest.getInstance("SHA-256")
        md.update(label.toByteArray(Charsets.UTF_8))
        md.update(th)
        return md.digest()
    }

    fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(data)
    }

    /** RFC 5869 HKDF-SHA256 matching Node crypto.hkdfSync('sha256', ikm, salt, info, length) */
    fun hkdfSha256(ikm: ByteArray, salt: ByteArray, info: ByteArray, length: Int): ByteArray {
        val prk = hmacSha256(if (salt.isEmpty()) ByteArray(32) else salt, ikm)
        val out = ByteArray(length)
        var t = ByteArray(0)
        var offset = 0
        var counter = 1
        while (offset < length) {
            val mac = Mac.getInstance("HmacSHA256")
            mac.init(SecretKeySpec(prk, "HmacSHA256"))
            mac.update(t)
            mac.update(info)
            mac.update(counter.toByte())
            t = mac.doFinal()
            val copyLen = minOf(t.size, length - offset)
            System.arraycopy(t, 0, out, offset, copyLen)
            offset += copyLen
            counter++
        }
        return out
    }

    fun verifyEcdsa(spkiDer: ByteArray, message: ByteArray, sigDer: ByteArray): Boolean =
        runCatching {
            val pub = KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(spkiDer))
            Signature.getInstance("SHA256withECDSA").run {
                initVerify(pub)
                update(message)
                verify(sigDer)
            }
        }.getOrDefault(false)
}

/**
 * End-to-end encrypted channel with AES-256-GCM and strictly monotonic sequence numbering.
 */
class SecureChannel(private val txKey: ByteArray, private val rxKey: ByteArray) {
    private var txSeq = 0L
    private var rxSeq = 0L

    private fun makeNonce(seq: Long): ByteArray =
        ByteBuffer.allocate(12).putInt(0).putLong(seq).array()

    @Synchronized
    fun seal(plaintext: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(txKey, "AES"), GCMParameterSpec(128, makeNonce(txSeq++)))
        return cipher.doFinal(plaintext)
    }

    @Synchronized
    fun open(frame: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(rxKey, "AES"), GCMParameterSpec(128, makeNonce(rxSeq++)))
        return cipher.doFinal(frame)
    }
}

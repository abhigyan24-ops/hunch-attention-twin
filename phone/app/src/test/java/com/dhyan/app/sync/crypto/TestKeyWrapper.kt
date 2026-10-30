package com.dhyan.app.sync.crypto

import java.nio.ByteBuffer
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Isolated test fake for KeyWrapper used exclusively in local JVM unit tests.
 * Never available or bundled in production sources.
 */
class TestKeyWrapper(rawKey: ByteArray = ByteArray(32) { (it + 7).toByte() }) : KeyWrapper {
    private val secretKey: SecretKey = SecretKeySpec(rawKey, "AES")
    private val secureRandom = SecureRandom()

    override fun wrap(plaintext: ByteArray): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val iv = ByteArray(12).also { secureRandom.nextBytes(it) }
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(128, iv))
        val ciphertext = cipher.doFinal(plaintext)
        val combined = ByteBuffer.allocate(iv.size + ciphertext.size).put(iv).put(ciphertext).array()
        return Base64.getEncoder().encodeToString(combined)
    }

    override fun unwrap(wrappedBase64: String): ByteArray {
        val combined = Base64.getDecoder().decode(wrappedBase64)
        val buffer = ByteBuffer.wrap(combined)
        val iv = ByteArray(12).also { buffer.get(it) }
        val ciphertext = ByteArray(buffer.remaining()).also { buffer.get(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(128, iv))
        return cipher.doFinal(ciphertext)
    }
}

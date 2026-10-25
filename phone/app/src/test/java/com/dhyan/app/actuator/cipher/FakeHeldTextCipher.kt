package com.dhyan.app.actuator.cipher

import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * JVM-only fake cipher using javax.crypto for unit tests.
 * Uses a fixed in-memory key — NOT backed by AndroidKeyStore.
 * Never used in production or instrumented builds.
 */
class FakeHeldTextCipher : HeldTextCipher {

    private companion object {
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_TAG_LENGTH_BITS = 128
    }

    private val key: SecretKey = KeyGenerator.getInstance("AES")
        .also { it.init(256) }
        .generateKey()

    override fun encrypt(plaintext: String): HeldTextCipher.CipherBlob {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val iv = cipher.iv
        val encrypted = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        return HeldTextCipher.CipherBlob(
            ciphertext = Base64.getEncoder().encodeToString(encrypted),
            iv = Base64.getEncoder().encodeToString(iv)
        )
    }

    override fun decrypt(blob: HeldTextCipher.CipherBlob): String {
        val iv = Base64.getDecoder().decode(blob.iv)
        val encrypted = Base64.getDecoder().decode(blob.ciphertext)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
        return String(cipher.doFinal(encrypted), Charsets.UTF_8)
    }
}

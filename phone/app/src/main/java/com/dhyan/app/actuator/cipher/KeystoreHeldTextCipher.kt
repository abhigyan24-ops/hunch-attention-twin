package com.dhyan.app.actuator.cipher

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.dhyan.app.actuator.ActuatorConfig
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * AES-256-GCM cipher backed by Android Keystore.
 *
 * - Key is created once and stored in the Keystore; never leaves secure hardware.
 * - Random 12-byte IV is generated per encryption and stored alongside the ciphertext.
 * - No user-auth requirement (must survive screen lock).
 */
class KeystoreHeldTextCipher : HeldTextCipher {

    private companion object {
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val KEY_SIZE_BITS = 256
        const val IV_LENGTH_BYTES = 12
        const val GCM_TAG_LENGTH_BITS = 128
        const val KEYSTORE_PROVIDER = "AndroidKeyStore"
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).also { it.load(null) }
        keyStore.getKey(ActuatorConfig.KEYSTORE_ALIAS, null)?.let { return it as SecretKey }

        val spec = KeyGenParameterSpec.Builder(
            ActuatorConfig.KEYSTORE_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(KEY_SIZE_BITS)
            .setRandomizedEncryptionRequired(true)
            .build()

        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER)
            .also { it.init(spec) }
            .generateKey()
    }

    override fun encrypt(plaintext: String): HeldTextCipher.CipherBlob {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val iv = cipher.iv // Keystore generates random IV when randomizedEncryptionRequired=true
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
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
        return String(cipher.doFinal(encrypted), Charsets.UTF_8)
    }
}

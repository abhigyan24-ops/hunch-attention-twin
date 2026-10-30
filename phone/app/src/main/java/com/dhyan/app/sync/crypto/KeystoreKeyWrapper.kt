package com.dhyan.app.sync.crypto

import java.nio.ByteBuffer
import java.security.KeyStore
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Android Keystore AES-256 GCM key wrapper for storing permanent pairing keys (PK, sid).
 * Uses hardware-backed StrongBox when available, falling back to standard TEE.
 * If hardware keystore is unavailable or key is lost/invalidated, fails closed
 * with an IllegalStateException, requiring re-pairing. Zero software fallback keys.
 */
class KeystoreKeyWrapper(
    private val keyAlias: String = "dhyan_sync_master_key"
) : KeyWrapper {
    private val secureRandom = SecureRandom()

    private fun generateKey(useStrongBox: Boolean) {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        if (!ks.containsAlias(keyAlias)) {
            val keyGenClass = Class.forName("android.security.keystore.KeyGenParameterSpec\$Builder")
            val purposes = 1 or 2 // PURPOSE_ENCRYPT | PURPOSE_DECRYPT
            val builder = keyGenClass.getConstructor(String::class.java, Int::class.java)
                .newInstance(keyAlias, purposes)

            val blockModes = arrayOf("GCM")
            keyGenClass.getMethod("setBlockModes", blockModes.javaClass).invoke(builder, blockModes)

            val encPaddings = arrayOf("NoPadding")
            keyGenClass.getMethod("setEncryptionPaddings", encPaddings.javaClass).invoke(builder, encPaddings)

            keyGenClass.getMethod("setKeySize", Int::class.javaPrimitiveType).invoke(builder, 256)

            if (useStrongBox) {
                try {
                    keyGenClass.getMethod("setIsStrongBoxBacked", Boolean::class.javaPrimitiveType)
                        .invoke(builder, true)
                } catch (_: Exception) {
                    // Method not available on older API levels
                }
            }

            val spec = keyGenClass.getMethod("build").invoke(builder)
            val kg = KeyGenerator.getInstance("AES", "AndroidKeyStore")
            kg.javaClass.getMethod("init", java.security.spec.AlgorithmParameterSpec::class.java).invoke(kg, spec)
            kg.generateKey()
        }
    }

    private fun isStrongBoxException(t: Throwable): Boolean {
        var current: Throwable? = t
        while (current != null) {
            val name = current::class.java.name
            if (name == "android.security.keystore.StrongBoxUnavailableException" ||
                (current is java.security.ProviderException && name.contains("StrongBox", ignoreCase = true))) {
                return true
            }
            if (current is NoSuchMethodException && current.message?.contains("setIsStrongBoxBacked") == true) {
                return true
            }
            current = current.cause
        }
        return false
    }

    private fun ensureKeystoreKey() {
        try {
            // First attempt StrongBox hardware isolation (DD-8 & Spec 25.13)
            try {
                generateKey(useStrongBox = true)
            } catch (e: Throwable) {
                if (isStrongBoxException(e)) {
                    // Specifically catch StrongBoxUnavailableException -> fallback to standard TEE
                    generateKey(useStrongBox = false)
                } else {
                    throw e
                }
            }
        } catch (_: Exception) {
            throw IllegalStateException("secure_storage_unavailable")
        }
    }

    private fun getSecretKey(): SecretKey {
        ensureKeystoreKey()
        val ks = try {
            KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        } catch (_: Exception) {
            throw IllegalStateException("secure_storage_unavailable")
        }
        val key = ks.getKey(keyAlias, null) as? SecretKey
        return key ?: throw IllegalStateException("key_invalidated_repair_required")
    }

    override fun wrap(plaintext: ByteArray): String {
        val cipher = try {
            Cipher.getInstance("AES/GCM/NoPadding")
        } catch (_: Exception) {
            throw IllegalStateException("secure_storage_unavailable")
        }
        cipher.init(Cipher.ENCRYPT_MODE, getSecretKey())
        val iv = cipher.iv ?: ByteArray(12).also { secureRandom.nextBytes(it) }
        val ciphertext = cipher.doFinal(plaintext)

        val combined = ByteBuffer.allocate(iv.size + ciphertext.size)
            .put(iv)
            .put(ciphertext)
            .array()
        return Base64.getEncoder().encodeToString(combined)
    }

    override fun unwrap(wrappedBase64: String): ByteArray {
        val combined = Base64.getDecoder().decode(wrappedBase64)
        require(combined.size > 28) { "Invalid wrapped payload: too short" }
        val iv = combined.copyOfRange(0, 12)
        val ciphertext = combined.copyOfRange(12, combined.size)

        val cipher = try {
            Cipher.getInstance("AES/GCM/NoPadding")
        } catch (_: Exception) {
            throw IllegalStateException("secure_storage_unavailable")
        }
        cipher.init(Cipher.DECRYPT_MODE, getSecretKey(), GCMParameterSpec(128, iv))
        return cipher.doFinal(ciphertext)
    }
}

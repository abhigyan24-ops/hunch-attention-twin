// phone/app/src/main/java/com/dhyan/app/privacy/SecretStore.kt
package com.dhyan.app.privacy

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Android Keystore-backed secure storage.
 * Does NOT use deprecated EncryptedSharedPreferences.
 * Backed by AES/GCM/NoPadding (256-bit). Excluded from OS cloud/device backups.
 */
class SecretStore(ctx: Context) {
    private val prefs = ctx.getSharedPreferences("dhyan_secrets", Context.MODE_PRIVATE)
    private val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    private val alias = "dhyan_secret_key_v1"

    private fun key(): SecretKey {
        (ks.getKey(alias, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return gen.generateKey()
    }

    fun put(name: String, value: String?) {
        if (value.isNullOrBlank()) {
            prefs.edit().remove(name).commit()
            return
        }
        val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        val ct = c.doFinal(value.toByteArray(Charsets.UTF_8))
        prefs.edit().putString(name, enc(c.iv) + ":" + enc(ct)).commit()
    }

    fun get(name: String): String? {
        val s = prefs.getString(name, null) ?: return null
        return try {
            val parts = s.split(":")
            if (parts.size != 2) return null
            val iv = dec(parts[0])
            val ct = dec(parts[1])
            val c = Cipher.getInstance("AES/GCM/NoPadding").apply {
                init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
            }
            String(c.doFinal(ct), Charsets.UTF_8)
        } catch (e: Exception) {
            prefs.edit().remove(name).apply() // Key lost (restore, reinstall, OS reset): drop it cleanly
            null
        }
    }

    fun remove(name: String) {
        prefs.edit().remove(name).commit()
    }

    private fun enc(b: ByteArray): String = Base64.encodeToString(b, Base64.NO_WRAP)
    private fun dec(s: String): ByteArray = Base64.decode(s, Base64.NO_WRAP)
}

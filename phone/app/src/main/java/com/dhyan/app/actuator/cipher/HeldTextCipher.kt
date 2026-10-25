package com.dhyan.app.actuator.cipher

/**
 * Encrypts and decrypts held notification text.
 *
 * PRIVACY CONTRACT:
 * - encrypt() input is title + "\n" + text — never stored in plaintext anywhere.
 * - Result is a [CipherBlob] containing Base64-encoded ciphertext and IV.
 * - decrypt() is called only by the actuator when re-posting a released notification.
 */
interface HeldTextCipher {
    data class CipherBlob(
        /** Base64-encoded AES-256-GCM ciphertext. */
        val ciphertext: String,
        /** Base64-encoded 12-byte random IV. */
        val iv: String
    )

    fun encrypt(plaintext: String): CipherBlob
    fun decrypt(blob: CipherBlob): String
}

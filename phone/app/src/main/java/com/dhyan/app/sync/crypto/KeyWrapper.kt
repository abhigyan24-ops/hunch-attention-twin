package com.dhyan.app.sync.crypto

/**
 * Interface for wrapping and unwrapping permanent pairing key material.
 * Implementations in production must fail closed if hardware-backed storage is unavailable.
 */
interface KeyWrapper {
    /**
     * Encrypts plaintext bytes with AES-GCM-256.
     * Returns Base64 encoded string: 12-byte IV || CiphertextWithTag.
     */
    fun wrap(plaintext: ByteArray): String

    /**
     * Decrypts wrapped Base64 string back into plaintext bytes.
     */
    fun unwrap(wrappedBase64: String): ByteArray
}

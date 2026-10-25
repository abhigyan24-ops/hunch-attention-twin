package com.dhyan.app.actuator.cipher

import org.junit.Assert.*
import org.junit.Test

class CipherRoundTripTest {

    private val cipher = FakeHeldTextCipher()

    @Test
    fun `encrypt then decrypt returns original text`() {
        val plaintext = "Hello from a test\nThis is the body text"
        val blob = cipher.encrypt(plaintext)
        val result = cipher.decrypt(blob)
        assertEquals(plaintext, result)
    }

    @Test
    fun `encrypted text is not plaintext`() {
        val plaintext = "Secret notification body"
        val blob = cipher.encrypt(plaintext)
        assertFalse("Ciphertext should not contain plaintext", blob.ciphertext.contains("Secret"))
        assertFalse("IV should not be empty", blob.iv.isEmpty())
    }

    @Test
    fun `two encryptions of same plaintext produce different ciphertext`() {
        val plaintext = "Same text"
        val blob1 = cipher.encrypt(plaintext)
        val blob2 = cipher.encrypt(plaintext)
        // Random IV → different ciphertext each time
        assertNotEquals("Should use random IV", blob1.iv, blob2.iv)
    }

    @Test
    fun `tampered ciphertext throws on decrypt`() {
        val plaintext = "original text"
        val blob = cipher.encrypt(plaintext)
        val tampered = blob.copy(ciphertext = blob.ciphertext + "TAMPERED==")
        try {
            cipher.decrypt(tampered)
            fail("Expected exception on tampered ciphertext")
        } catch (e: Exception) {
            // Expected — GCM authentication tag verification fails
            assertTrue(true)
        }
    }

    @Test
    fun `empty string round-trips correctly`() {
        val blob = cipher.encrypt("")
        assertEquals("", cipher.decrypt(blob))
    }

    @Test
    fun `unicode text round-trips correctly`() {
        val plaintext = "नमस्ते दुनिया — ধন্যবাদ 🙏"
        val blob = cipher.encrypt(plaintext)
        assertEquals(plaintext, cipher.decrypt(blob))
    }
}

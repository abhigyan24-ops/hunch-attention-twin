package com.dhyan.app.sync.crypto

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.nio.charset.StandardCharsets

class KeystoreKeyWrapperTest {
    @Test
    fun productionKeystoreFailureNeverUsesTestKey() {
        val wrapper = KeystoreKeyWrapper()
        val failure = runCatching { wrapper.wrap(ByteArray(32)) }.exceptionOrNull()
        org.junit.Assert.assertTrue(failure is IllegalStateException)
        org.junit.Assert.assertEquals("secure_storage_unavailable", failure?.message)
    }

    @Test
    fun wrapAndUnwrapRoundtrip() {
        val wrapper = KeystoreKeyWrapper(isTestFallback = true)
        val original = "dhyan-pairing-secret-key-32bytes!".toByteArray(StandardCharsets.UTF_8)

        val wrapped = wrapper.wrap(original)
        assertNotEquals(String(original), wrapped)

        val unwrapped = wrapper.unwrap(wrapped)
        assertArrayEquals(original, unwrapped)
    }
}

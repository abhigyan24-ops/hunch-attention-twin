package com.dhyan.app.sync.crypto

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.StandardCharsets

class KeystoreKeyWrapperTest {

    @Test
    fun productionKeystoreFailureNeverUsesTestKey() {
        val wrapper = KeystoreKeyWrapper()
        val failure = runCatching { wrapper.wrap(ByteArray(32)) }.exceptionOrNull()
        assertTrue(failure is IllegalStateException)
        assertEquals("secure_storage_unavailable", failure?.message)
    }

    @Test
    fun productionKeystoreWrapperHasNoFallbackConstructorOrField() {
        val constructors = KeystoreKeyWrapper::class.java.constructors
        val fields = KeystoreKeyWrapper::class.java.declaredFields
        assertFalse(
            "Production wrapper must not have isTestFallback parameter in constructor",
            constructors.any { it.parameterTypes.contains(Boolean::class.javaPrimitiveType) }
        )
        assertFalse(
            "Production wrapper must not contain testFallbackKey field",
            fields.any { it.name.contains("testFallback") }
        )
    }

    @Test
    fun wrapAndUnwrapRoundtripWithTestKeyWrapper() {
        val wrapper: KeyWrapper = TestKeyWrapper()
        val original = "dhyan-pairing-secret-key-32bytes!".toByteArray(StandardCharsets.UTF_8)

        val wrapped = wrapper.wrap(original)
        assertNotEquals(String(original), wrapped)

        val unwrapped = wrapper.unwrap(wrapped)
        assertArrayEquals(original, unwrapped)
    }
}

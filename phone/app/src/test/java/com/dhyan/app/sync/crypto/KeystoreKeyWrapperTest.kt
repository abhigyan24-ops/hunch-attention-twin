package com.dhyan.app.sync.crypto

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.nio.charset.StandardCharsets

class KeystoreKeyWrapperTest {

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

package com.dhyan.app.vault

import org.junit.Assert.*
import org.junit.Test

class EncryptedVaultManagerTest {
    @Test fun encryptedProfileAuthenticatesBeforeReturningData() {
        val profile = """{"bedtimeSchedule":{"bedHour":23},"vipPackages":["com.example.private"]}""".toByteArray()
        val password = "test recovery passphrase"
        val envelope = EncryptedVaultManager.encryptPayload(profile, password)
        assertArrayEquals(profile, EncryptedVaultManager.decryptPayload(envelope, password))
        assertFalse(envelope.toString(Charsets.ISO_8859_1).contains("com.example.private"))
        assertFalse(envelope.toString(Charsets.ISO_8859_1).contains("bedtimeSchedule"))
        assertThrows(SecurityException::class.java) {
            EncryptedVaultManager.decryptPayload(envelope, "wrong recovery passphrase")
        }
        envelope[envelope.lastIndex] = (envelope.last().toInt() xor 1).toByte()
        assertThrows(SecurityException::class.java) {
            EncryptedVaultManager.decryptPayload(envelope, password)
        }
    }
}

package com.dhyan.app.sync.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PrivacyGuardTest {

    @Test
    fun testRejectsForbiddenFields() {
        val clean = mapOf(
            "id" to "123",
            "appCat" to "social",
            "count" to 5
        )
        assertTrue(PrivacyGuard.isClean(clean))

        val withTitle = mapOf(
            "id" to "123",
            "payload" to mapOf("title" to "Secret message")
        )
        assertFalse(PrivacyGuard.isClean(withTitle))

        val withText = mapOf(
            "text" to "Hello world"
        )
        assertFalse(PrivacyGuard.isClean(withText))

        val withUrlKey = mapOf(
            "url" to "https://example.com"
        )
        assertFalse(PrivacyGuard.isClean(withUrlKey))

        val withKeystrokes = mapOf(
            "keystrokes" to "password123"
        )
        assertFalse(PrivacyGuard.isClean(withKeystrokes))
    }

    @Test
    fun testRejectsCanariesAndSensitivePatterns() {
        val withCanary = mapOf(
            "note" to "CANARY_ALERT_TEST"
        )
        assertFalse(PrivacyGuard.isClean(withCanary))

        val withUrlValue = mapOf(
            "meta" to "Visit https://phishing.site/now"
        )
        assertFalse(PrivacyGuard.isClean(withUrlValue))

        val withEmailValue = mapOf(
            "sender" to "user@example.com"
        )
        assertFalse(PrivacyGuard.isClean(withEmailValue))
    }
}

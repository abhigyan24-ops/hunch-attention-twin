package com.dhyan.app.ingest

import org.junit.Assert.*
import org.junit.Test

class NotifExtractorTest {

    @Test
    fun testNullAndEmptyInputs() {
        val res = NotifExtractor.extract(title = null, text = null, bigText = null)
        assertFalse(res.isOtpLike)
        assertFalse(res.hasEmergencyKeyword)
        assertNull(res.holdPayload)

        val resBlank = NotifExtractor.extract(title = "   ", text = "", bigText = " ")
        assertFalse(resBlank.isOtpLike)
        assertFalse(resBlank.hasEmergencyKeyword)
        assertNull(resBlank.holdPayload)
    }

    @Test
    fun testOtpDetection() {
        val res1 = NotifExtractor.extract(
            title = "Bank of India",
            text = "Your OTP for transaction is 482910. Do not share."
        )
        assertTrue("Expected isOtpLike to be true", res1.isOtpLike)
        assertFalse(res1.hasEmergencyKeyword)
        assertEquals("Bank of India\nYour OTP for transaction is 482910. Do not share.", res1.holdPayload)

        val res2 = NotifExtractor.extract(
            title = "Google Verification",
            text = "G-948201 is your verification code."
        )
        assertTrue(res2.isOtpLike)

        val res3 = NotifExtractor.extract(
            title = "Amazon",
            text = "Use passcode 3948 to log in to your account."
        )
        assertTrue(res3.isOtpLike)

        // Numbers without OTP keywords should NOT trigger OTP
        val res4 = NotifExtractor.extract(
            title = "Runner App",
            text = "You ran 5420 meters today!"
        )
        assertFalse(res4.isOtpLike)
    }

    @Test
    fun testEmergencyKeywordDetection() {
        val res1 = NotifExtractor.extract(
            title = "Hospital Alert",
            text = "Urgent: Cardiac response required in Ward B"
        )
        assertFalse(res1.isOtpLike)
        assertTrue(res1.hasEmergencyKeyword)

        val res2 = NotifExtractor.extract(
            title = "Family",
            text = "SOS please call me right now emergency"
        )
        assertTrue(res2.hasEmergencyKeyword)

        val res3 = NotifExtractor.extract(
            title = "Weather",
            text = "Severe fire danger warning in your area"
        )
        assertTrue(res3.hasEmergencyKeyword)
    }

    @Test
    fun testNormalNotification() {
        val res = NotifExtractor.extract(
            title = "Alice",
            text = "Hey, are we still meeting for lunch?"
        )
        assertFalse(res.isOtpLike)
        assertFalse(res.hasEmergencyKeyword)
        assertEquals("Alice\nHey, are we still meeting for lunch?", res.holdPayload)
    }

    @Test
    fun testHoldPayloadTransientFormatting() {
        val titleOnly = NotifExtractor.extract(title = "System Update", text = null)
        assertEquals("System Update", titleOnly.holdPayload)

        val textOnly = NotifExtractor.extract(title = null, text = "New message received")
        assertEquals("New message received", textOnly.holdPayload)

        val withBigText = NotifExtractor.extract(
            title = "Chat",
            text = "Preview",
            bigText = "Full expanded message body"
        )
        assertEquals("Chat\nPreview", withBigText.holdPayload)
    }
}

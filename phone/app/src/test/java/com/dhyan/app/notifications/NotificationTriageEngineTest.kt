package com.dhyan.app.notifications

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class NotificationTriageEngineTest {

    @Before
    fun setup() {
        NotificationTriageEngine.clearSenderCache()
        // Reset VIP packages
        listOf("com.vip.app", "com.bank.app").forEach { NotificationTriageEngine.removeVipPackage(it) }
    }

    @Test
    fun `system category call always passes`() {
        val decision = NotificationTriageEngine.triage(
            packageName = "com.phone.app",
            senderKey = "caller",
            title = "Incoming call",
            text = "John Doe is calling",
            category = "android.intent.category.CALL",
            nowMs = 1_000_000L
        )
        assertTrue(decision is TriageDecision.PASS_THROUGH_SYSTEM_CATEGORY)
    }

    @Test
    fun `vip package always passes`() {
        NotificationTriageEngine.addVipPackage("com.vip.app")
        val decision = NotificationTriageEngine.triage(
            packageName = "com.vip.app",
            senderKey = "sender",
            title = "Hello",
            text = "World",
            category = null,
            nowMs = 1_000_000L
        )
        assertTrue(decision is TriageDecision.PASS_THROUGH_VIP)
    }

    @Test
    fun `otp with keyword passes through with extracted code`() {
        val decision = NotificationTriageEngine.triage(
            packageName = "com.bank.app",
            senderKey = "HDFC",
            title = "HDFC Bank OTP",
            text = "Your OTP is 482910. Valid for 5 mins.",
            category = null,
            nowMs = 1_000_000L
        )
        assertTrue("Expected OTP pass-through, got $decision", decision is TriageDecision.PASS_THROUGH_OTP)
        assertEquals("482910", (decision as TriageDecision.PASS_THROUGH_OTP).extractedCode)
    }

    @Test
    fun `otp without keyword does not pass through`() {
        val decision = NotificationTriageEngine.triage(
            packageName = "com.random.app",
            senderKey = "sender",
            title = "New message",
            text = "Your number is 12345",
            category = null,
            nowMs = 1_000_000L
        )
        assertTrue(decision is TriageDecision.HOLD)
    }

    @Test
    fun `repeated sender 3 times in 180s passes through`() {
        val base = 1_000_000L
        // First two: HOLD
        repeat(2) { i ->
            val r = NotificationTriageEngine.triage("com.chat.app", "Alice", "Msg $i", "Hey $i", null, base + i * 1000L)
            assertTrue(r is TriageDecision.HOLD)
        }
        // Third: PASS
        val r3 = NotificationTriageEngine.triage("com.chat.app", "Alice", "Msg 3", "Hey 3", null, base + 3000L)
        assertTrue(r3 is TriageDecision.PASS_THROUGH_REPEATED_SENDER)
    }

    @Test
    fun `repeated sender window expires after 180s`() {
        val base = 1_000_000L
        repeat(2) { i ->
            NotificationTriageEngine.triage("com.chat.app", "Bob", "Msg $i", "Hey", null, base + i * 1000L)
        }
        // Send 3rd notification >180s later — window should expire, back to HOLD
        val r3 = NotificationTriageEngine.triage("com.chat.app", "Bob", "Msg 3", "Hey", null, base + 200_000L)
        assertTrue(r3 is TriageDecision.HOLD)
    }

    @Test
    fun `extractOtpCode companion helper extracts correctly`() {
        assertEquals("123456", NotificationTriageEngine.extractOtpCode("Your OTP is 123456"))
        assertEquals("A-9876", NotificationTriageEngine.extractOtpCode("Code: A-9876"))
        assertNull(NotificationTriageEngine.extractOtpCode("No code here abc"))
    }

    @Test
    fun `regular notification is held`() {
        val decision = NotificationTriageEngine.triage(
            packageName = "com.social.app",
            senderKey = "user123",
            title = "New post",
            text = "Someone liked your photo",
            category = null,
            nowMs = 1_000_000L
        )
        assertTrue(decision is TriageDecision.HOLD)
    }
}

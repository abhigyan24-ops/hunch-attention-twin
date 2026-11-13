package com.dhyan.app.digest

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class BreakDigestVaultTest {

    private lateinit var vault: BreakDigestVault

    @Before
    fun setUp() {
        vault = BreakDigestVault()
    }

    @Test
    fun `records and groups notifications by package name`() {
        vault.recordHeldNotification(
            packageName = "com.whatsapp",
            appLabel = "WhatsApp",
            senderTitle = "Alex",
            category = "messaging",
            nowMillis = 1000L
        )

        vault.recordHeldNotification(
            packageName = "com.whatsapp",
            appLabel = "WhatsApp",
            senderTitle = "Mom",
            category = "messaging",
            nowMillis = 2000L
        )

        vault.recordHeldNotification(
            packageName = "com.slack",
            appLabel = "Slack",
            senderTitle = "#dev-team",
            category = "work",
            nowMillis = 3000L
        )

        assertEquals(3, vault.getTotalHeldCount())
        val groups = vault.getHeldGroups()
        assertEquals(2, groups.size)

        // Sorted by latest timestamp descending: Slack first, then WhatsApp
        assertEquals("com.slack", groups[0].packageName)
        assertEquals(1, groups[0].count)
        assertEquals(listOf("#dev-team"), groups[0].senderTitles)

        assertEquals("com.whatsapp", groups[1].packageName)
        assertEquals(2, groups[1].count)
        assertEquals(listOf("Alex", "Mom"), groups[1].senderTitles)
    }

    @Test
    fun `caps sender titles at 3 most recent and deduplicates`() {
        val pkg = "com.google.android.talk"
        vault.recordHeldNotification(pkg, "Hangouts", "Alice", nowMillis = 100L)
        vault.recordHeldNotification(pkg, "Hangouts", "Bob", nowMillis = 200L)
        vault.recordHeldNotification(pkg, "Hangouts", "Alice", nowMillis = 300L) // Duplicate
        vault.recordHeldNotification(pkg, "Hangouts", "Charlie", nowMillis = 400L)
        vault.recordHeldNotification(pkg, "Hangouts", "Dave", nowMillis = 500L)

        val groups = vault.getHeldGroups()
        assertEquals(1, groups.size)
        assertEquals(5, groups[0].count)
        // Alice was re-added before Charlie, then Dave pushed Bob out -> ["Alice", "Charlie", "Dave"]
        assertEquals(listOf("Alice", "Charlie", "Dave"), groups[0].senderTitles)
    }

    @Test
    fun `dismissGroup removes only target package and decrements count`() {
        vault.recordHeldNotification("com.app.a", "App A", nowMillis = 100L)
        vault.recordHeldNotification("com.app.b", "App B", nowMillis = 200L)
        vault.recordHeldNotification("com.app.b", "App B", nowMillis = 300L)

        assertEquals(3, vault.getTotalHeldCount())
        assertTrue(vault.dismissGroup("com.app.a"))
        assertFalse(vault.dismissGroup("com.nonexistent"))

        assertEquals(2, vault.getTotalHeldCount())
        val remaining = vault.getHeldGroups()
        assertEquals(1, remaining.size)
        assertEquals("com.app.b", remaining[0].packageName)
    }

    @Test
    fun `clearAll empties all groups`() {
        vault.recordHeldNotification("com.app.a", "App A", nowMillis = 100L)
        vault.recordHeldNotification("com.app.b", "App B", nowMillis = 200L)

        vault.clearAll()
        assertEquals(0, vault.getTotalHeldCount())
        assertTrue(vault.getHeldGroups().isEmpty())
    }
}

package com.dhyan.app.collectors

import com.dhyan.app.ingest.AppCategorizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FocusShieldTest {

    @Test
    fun testRestrictedAppPackagesIncludesSocialPlatforms() {
        assertTrue(A11yCollector.RESTRICTED_PACKAGES.contains("com.instagram.android"))
        assertTrue(A11yCollector.RESTRICTED_PACKAGES.contains("com.zhiliaoapp.musically"))
        assertTrue(A11yCollector.RESTRICTED_PACKAGES.contains("com.twitter.android"))
        assertTrue(A11yCollector.RESTRICTED_PACKAGES.contains("com.reddit.frontpage"))
        assertTrue(A11yCollector.RESTRICTED_PACKAGES.contains("com.facebook.katana"))
        assertTrue(A11yCollector.RESTRICTED_PACKAGES.contains("com.snapchat.android"))
    }

    @Test
    fun testRestrictedCategoriesIncludesSocialAndEntertainment() {
        assertTrue(A11yCollector.RESTRICTED_APP_CATEGORIES.contains(AppCategorizer.Cat.SOCIAL))
        assertTrue(A11yCollector.RESTRICTED_APP_CATEGORIES.contains(AppCategorizer.Cat.ENTERTAINMENT))
        assertTrue(A11yCollector.RESTRICTED_APP_CATEGORIES.contains("gaming"))
        assertFalse(A11yCollector.RESTRICTED_APP_CATEGORIES.contains(AppCategorizer.Cat.PRODUCTIVITY))
    }

    @Test
    fun testMindfulPauseDuration() {
        val now = System.currentTimeMillis()
        A11yCollector.grantMindfulPause(60_000L)
        assertTrue(A11yCollector.mindfulPauseUntilMs >= now + 50_000L)
        assertTrue(A11yCollector.mindfulPauseUntilMs <= now + 65_000L)
    }

    @Test
    fun testShieldToggles() {
        A11yCollector.focusShieldEnabled = true
        assertTrue(A11yCollector.focusShieldEnabled)
        A11yCollector.focusShieldEnabled = false
        assertFalse(A11yCollector.focusShieldEnabled)
        A11yCollector.focusShieldEnabled = true

        A11yCollector.doomscrollBrakeEnabled = true
        assertTrue(A11yCollector.doomscrollBrakeEnabled)
        A11yCollector.doomscrollBrakeEnabled = false
        assertFalse(A11yCollector.doomscrollBrakeEnabled)
        A11yCollector.doomscrollBrakeEnabled = true

        A11yCollector.bedtimeGuardianEnabled = true
        assertTrue(A11yCollector.bedtimeGuardianEnabled)
        A11yCollector.bedtimeGuardianEnabled = false
        assertFalse(A11yCollector.bedtimeGuardianEnabled)
        A11yCollector.bedtimeGuardianEnabled = true
    }

    @Test
    fun testEssentialWhitelistContainsAuthAndUtilities() {
        assertTrue(A11yCollector.ESSENTIAL_WHITELIST_PACKAGES.contains("com.google.android.apps.authenticator2"))
        assertTrue(A11yCollector.ESSENTIAL_WHITELIST_PACKAGES.contains("com.authy.authy"))
        assertTrue(A11yCollector.ESSENTIAL_WHITELIST_PACKAGES.contains("com.google.android.dialer"))
        assertTrue(A11yCollector.ESSENTIAL_WHITELIST_PACKAGES.contains("com.google.android.calculator"))
        assertTrue(A11yCollector.ESSENTIAL_WHITELIST_PACKAGES.contains("com.android.settings"))
    }

    @Test
    fun testCustomAllowedPackages() {
        val custom = setOf("com.slack", "com.notion.id")
        A11yCollector.customAllowedPackages = custom
        assertEquals(custom, A11yCollector.customAllowedPackages)
        A11yCollector.customAllowedPackages = emptySet()
    }

    @Test
    fun testStatsCounters() {
        val initialShielded = A11yCollector.distractionsShieldedCount.get()
        A11yCollector.distractionsShieldedCount.incrementAndGet()
        assertEquals(initialShielded + 1, A11yCollector.distractionsShieldedCount.get())

        val initialPrevented = A11yCollector.doomscrollsPreventedCount.get()
        A11yCollector.doomscrollsPreventedCount.incrementAndGet()
        assertEquals(initialPrevented + 1, A11yCollector.doomscrollsPreventedCount.get())
    }
}

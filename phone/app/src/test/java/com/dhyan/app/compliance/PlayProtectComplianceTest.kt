package com.dhyan.app.compliance

import com.dhyan.app.safety.NotificationTriageEngine
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/**
 * PlayProtectComplianceTest — automated pre-flight compliance verification.
 *
 * These tests parse the actual project XML files and verify runtime classes
 * to ensure no future commit can break Google Play Protect / MUwS compliance.
 * Runs as a standard JVM unit test (no Android device required).
 */
class PlayProtectComplianceTest {

    // Resolve project root relative to the test working directory
    private val projectRoot: File by lazy {
        var dir = File(System.getProperty("user.dir") ?: ".")
        // Walk up until we find AndroidManifest.xml in the expected place
        repeat(6) {
            if (File(dir, "src/main/AndroidManifest.xml").exists()) return@lazy dir
            dir = dir.parentFile ?: dir
        }
        dir
    }

    private fun manifestText(): String =
        File(projectRoot, "src/main/AndroidManifest.xml").readText()

    private fun xmlText(relativePath: String): String =
        File(projectRoot, relativePath).readText()

    // ── Test 1 ──────────────────────────────────────────────────────────────

    @Test
    fun manifest_neverDeclaresBannedPhaPermissions() {
        val manifest = manifestText()
        val bannedPermissions = listOf(
            "READ_SMS", "RECEIVE_SMS", "SEND_SMS",
            "READ_CALL_LOG", "WRITE_CALL_LOG", "PROCESS_OUTGOING_CALLS",
            "READ_CONTACTS", "GET_ACCOUNTS",
            "RECORD_AUDIO", "CAMERA",
            "ACCESS_FINE_LOCATION", "ACCESS_COARSE_LOCATION",
            "REQUEST_INSTALL_PACKAGES", "QUERY_ALL_PACKAGES"
        )
        for (perm in bannedPermissions) {
            assertFalse(
                "AndroidManifest.xml must NOT declare banned PHA permission: $perm",
                manifest.contains(perm)
            )
        }
    }

    // ── Test 2 ──────────────────────────────────────────────────────────────

    @Test
    fun accessibilityConfig_enforcesNonDeceptiveFlags() {
        val config = xmlText("src/main/res/xml/accessibility_service_config.xml")

        assertTrue(
            "accessibility_service_config.xml must set android:isAccessibilityTool=\"false\"",
            config.contains("""android:isAccessibilityTool="false"""")
        )
        assertTrue(
            "accessibility_service_config.xml must set android:canRetrieveWindowContent=\"false\"",
            config.contains("""android:canRetrieveWindowContent="false"""")
        )
        assertFalse(
            "accessibility_service_config.xml must NOT listen for typeViewTextChanged (screen-scraping risk)",
            config.contains("typeViewTextChanged")
        )
        assertFalse(
            "accessibility_service_config.xml must NOT listen for typeViewClicked (input-logging risk)",
            config.contains("typeViewClicked")
        )
        // Must be scoped to window state only (no typeViewScrolled which reveals reading speed)
        assertTrue(
            "accessibility_service_config.xml must only listen for typeWindowStateChanged",
            config.contains("typeWindowStateChanged")
        )
        assertFalse(
            "accessibility_service_config.xml must NOT include typeViewScrolled",
            config.contains("typeViewScrolled")
        )
    }

    // ── Test 3 ──────────────────────────────────────────────────────────────

    @Test
    fun backupRules_excludePlaintextAndOnlyAllowEncryptedVault() {
        val backupRules = xmlText("src/main/res/xml/backup_rules.xml")
        val dataExtractionRules = xmlText("src/main/res/xml/data_extraction_rules.xml")

        assertTrue(
            "backup_rules.xml must include hunch_vault_backup.enc",
            backupRules.contains("hunch_vault_backup.enc")
        )
        assertTrue(
            "data_extraction_rules.xml must include hunch_vault_backup.enc",
            dataExtractionRules.contains("hunch_vault_backup.enc")
        )
        // Must not back up raw databases or unencrypted shared prefs
        assertFalse(
            "backup_rules.xml must not include raw databases",
            backupRules.contains("""domain="database"""")
        )
        assertFalse(
            "backup_rules.xml must not include raw sharedpref",
            backupRules.contains("""domain="sharedpref"""")
        )
    }

    // ── Test 4 ──────────────────────────────────────────────────────────────

    @Test
    fun systemSecurityNotifications_areNeverSuppressed() {
        val exemptPackages = listOf(
            "com.android.vending",
            "com.android.systemui",
            "android",
            "com.google.android.gms"
        )
        for (pkg in exemptPackages) {
            assertTrue(
                "NotificationTriageEngine must classify '$pkg' as system-exempt",
                NotificationTriageEngine.isSystemExemptPackage(pkg)
            )
        }
        // Non-exempt packages must NOT be classified as exempt
        assertFalse(
            "NotificationTriageEngine must NOT classify social apps as exempt",
            NotificationTriageEngine.isSystemExemptPackage("com.instagram.android")
        )
        assertFalse(
            "NotificationTriageEngine must NOT classify arbitrary packages as exempt",
            NotificationTriageEngine.isSystemExemptPackage("com.example.foo")
        )
    }

    // ── Test 5 ──────────────────────────────────────────────────────────────

    @Test
    fun prominentDisclosureGate_disclosureStringsContainNoPlayProtectDisableLanguage() {
        val tiers = ProminentDisclosureGate.PermissionTier.values()
        val forbiddenPhrases = listOf(
            "disable Play Protect",
            "turn off Play Protect",
            "disable play protect",
            "turn off play protect",
            "disable google play protect",
            "disable Google Play Protect"
        )
        for (tier in tiers) {
            val copy = ProminentDisclosureGate.getDisclosureStatement(tier)
            val allText = "${copy.title} ${copy.whatIsAccessed} ${copy.onDeviceGuarantee} ${copy.revocabilityStatement}"
            for (phrase in forbiddenPhrases) {
                assertFalse(
                    "Disclosure copy for ${tier.name} must not contain forbidden phrase: '$phrase'",
                    allText.contains(phrase, ignoreCase = true)
                )
            }
        }
    }

    @Test
    fun prominentDisclosureGate_onDeviceGuaranteeStates0BytesUploaded() {
        val tiers = ProminentDisclosureGate.PermissionTier.values()
        for (tier in tiers) {
            val copy = ProminentDisclosureGate.getDisclosureStatement(tier)
            assertTrue(
                "Disclosure copy for ${tier.name} must state '0 bytes' on-device guarantee",
                copy.onDeviceGuarantee.contains("0 bytes", ignoreCase = true)
            )
        }
    }
}

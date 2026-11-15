package com.dhyan.app.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckerManagerTest {

    @Test
    fun testIsRemoteVersionNewerBasicProgression() {
        // Higher major
        assertTrue(UpdateCheckerManager.isRemoteVersionNewer("1.0.0", "v2.0.0"))
        assertTrue(UpdateCheckerManager.isRemoteVersionNewer("1.9.9", "2.0.0"))

        // Higher minor
        assertTrue(UpdateCheckerManager.isRemoteVersionNewer("2.0.0", "v2.1.0"))
        assertTrue(UpdateCheckerManager.isRemoteVersionNewer("2.0.0", "2.1.0"))

        // Higher patch
        assertTrue(UpdateCheckerManager.isRemoteVersionNewer("2.0.0", "v2.0.1"))
    }

    @Test
    fun testIsRemoteVersionNewerPreReleaseHandling() {
        // Stable release is newer than pre-release of the same version
        assertTrue(UpdateCheckerManager.isRemoteVersionNewer("2.0.0-beta", "v2.0.0"))
        assertTrue(UpdateCheckerManager.isRemoteVersionNewer("2.0.0-alpha.1", "2.0.0"))

        // Pre-release vs older major is still newer
        assertFalse(UpdateCheckerManager.isRemoteVersionNewer("2.0.0", "1.0.0-beta"))
    }

    @Test
    fun testIsRemoteVersionNewerIdenticalAndOlder() {
        // Identical versions
        assertFalse(UpdateCheckerManager.isRemoteVersionNewer("2.0.0", "v2.0.0"))
        assertFalse(UpdateCheckerManager.isRemoteVersionNewer("2.0.0", "2.0.0"))

        // Remote is older
        assertFalse(UpdateCheckerManager.isRemoteVersionNewer("2.0.0", "v1.0.0"))
        assertFalse(UpdateCheckerManager.isRemoteVersionNewer("2.1.0", "v2.0.9"))
        assertFalse(UpdateCheckerManager.isRemoteVersionNewer("2.0.0", ""))
    }

    @Test
    fun testParseGitHubReleaseJsonWithApkAsset() {
        val sampleJson = """
            {
              "tag_name": "v2.1.0",
              "html_url": "https://github.com/abhigyan24-ops/hunch-attention-twin/releases/tag/v2.1.0",
              "body": "### Hunch v2.1.0 Highlights\n- Enhanced LinUCB exploration bonus\n- Faster offline rehydration",
              "assets": [
                {
                  "name": "Hunch-Setup-2.1.0.exe",
                  "browser_download_url": "https://github.com/abhigyan24-ops/hunch-attention-twin/releases/download/v2.1.0/Hunch-Setup-2.1.0.exe"
                },
                {
                  "name": "Hunch-v2.1.0.apk",
                  "browser_download_url": "https://github.com/abhigyan24-ops/hunch-attention-twin/releases/download/v2.1.0/Hunch-v2.1.0.apk"
                }
              ]
            }
        """.trimIndent()

        val info = UpdateCheckerManager.parseGitHubReleaseJson(sampleJson, currentVersion = "2.0.0")

        assertEquals("v2.1.0", info.latestVersionTag)
        assertTrue(info.updateAvailable)
        assertEquals("https://github.com/abhigyan24-ops/hunch-attention-twin/releases/download/v2.1.0/Hunch-v2.1.0.apk", info.apkDownloadUrl)
        assertEquals("https://github.com/abhigyan24-ops/hunch-attention-twin/releases/tag/v2.1.0", info.releaseHtmlUrl)
        assertTrue(info.releaseNotes.contains("Enhanced LinUCB"))
    }

    @Test
    fun testParseGitHubReleaseJsonWithoutApkAssetFallsBackToHtmlUrl() {
        val sampleJson = """
            {
              "tag_name": "v2.1.0",
              "html_url": "https://github.com/abhigyan24-ops/hunch-attention-twin/releases/tag/v2.1.0",
              "body": "Draft release notes without attached binaries yet",
              "assets": []
            }
        """.trimIndent()

        val info = UpdateCheckerManager.parseGitHubReleaseJson(sampleJson, currentVersion = "2.0.0")

        assertEquals("v2.1.0", info.latestVersionTag)
        assertTrue(info.updateAvailable)
        // Falls back to html_url when no .apk asset is present
        assertEquals("https://github.com/abhigyan24-ops/hunch-attention-twin/releases/tag/v2.1.0", info.apkDownloadUrl)
    }

    @Test
    fun testShouldCheckFromNetwork24HourThrottling() {
        val interval = 86_400_000L
        val t0 = 1_000_000_000L

        // Never checked before -> check
        assertTrue(UpdateCheckerManager.shouldCheckFromNetwork(0L, t0, interval))

        // Checked 1 hour ago -> don't check
        assertFalse(UpdateCheckerManager.shouldCheckFromNetwork(t0, t0 + 3_600_000L, interval))

        // Checked 23 hours ago -> don't check
        assertFalse(UpdateCheckerManager.shouldCheckFromNetwork(t0, t0 + 82_800_000L, interval))

        // Checked 24 hours ago -> check
        assertTrue(UpdateCheckerManager.shouldCheckFromNetwork(t0, t0 + 86_400_000L, interval))

        // Checked 25 hours ago -> check
        assertTrue(UpdateCheckerManager.shouldCheckFromNetwork(t0, t0 + 90_000_000L, interval))

        // Clock rollback / skew -> check
        assertTrue(UpdateCheckerManager.shouldCheckFromNetwork(t0 + 10_000L, t0, interval))
    }
}

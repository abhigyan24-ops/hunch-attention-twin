package com.dhyan.app.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.dhyan.app.personas.json.JsonReader
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

data class ReleaseUpdateInfo(
    val latestVersionTag: String,
    val updateAvailable: Boolean,
    val apkDownloadUrl: String,
    val releaseHtmlUrl: String,
    val releaseNotes: String,
    val lastCheckedAtMillis: Long = 0L
)

data class ParsedSemVer(
    val major: Int,
    val minor: Int,
    val patch: Int,
    val isPreRelease: Boolean
)

object UpdateCheckerManager {

    const val GITHUB_RELEASE_API_URL = "https://api.github.com/repos/abhigyan24-ops/hunch-attention-twin/releases/latest"
    const val PREFS_NAME = "hunch_update_checker"
    const val KEY_LAST_CHECKED = "last_checked_millis"
    const val KEY_LATEST_TAG = "latest_tag"
    const val KEY_UPDATE_AVAILABLE = "update_available"
    const val KEY_APK_URL = "apk_download_url"
    const val KEY_RELEASE_HTML_URL = "release_html_url"
    const val KEY_RELEASE_NOTES = "release_notes"

    const val DEFAULT_MIN_INTERVAL_MILLIS = 86_400_000L // 24 hours
    const val CURRENT_APP_VERSION = "2.0.0"

    /**
     * Parses a SemVer string such as "v2.0.0", "1.0.0-beta.1", or "2.1.0"
     * into a normalized ParsedSemVer object.
     */
    fun parseSemVer(versionStr: String): ParsedSemVer {
        val trimmed = versionStr.trim().removePrefix("v").removePrefix("V")
        val isPreRelease = trimmed.contains("-")
        val basePart = if (isPreRelease) trimmed.substringBefore("-") else trimmed
        val segments = basePart.split(".")

        val major = segments.getOrNull(0)?.toIntOrNull() ?: 0
        val minor = segments.getOrNull(1)?.toIntOrNull() ?: 0
        val patch = segments.getOrNull(2)?.toIntOrNull() ?: 0

        return ParsedSemVer(major, minor, patch, isPreRelease)
    }

    /**
     * Deterministic Semantic Version comparison.
     * Returns true if remoteTag represents a newer version than currentVersion.
     *
     * Rules:
     * 1. Higher major, minor, or patch strictly wins (e.g. 2.0.0 > 1.0.0, 2.1.0 > 2.0.0).
     * 2. If (major, minor, patch) are equal, stable release > pre-release (e.g. 2.0.0 > 2.0.0-beta).
     */
    fun isRemoteVersionNewer(currentVersion: String, remoteTag: String): Boolean {
        if (remoteTag.isBlank()) return false
        val curr = parseSemVer(currentVersion)
        val rem = parseSemVer(remoteTag)

        if (rem.major != curr.major) return rem.major > curr.major
        if (rem.minor != curr.minor) return rem.minor > curr.minor
        if (rem.patch != curr.patch) return rem.patch > curr.patch

        // Equal (major, minor, patch): a stable release is newer than a pre-release
        return curr.isPreRelease && !rem.isPreRelease
    }

    /**
     * Determines whether enough time has elapsed to query the GitHub network endpoint,
     * protecting against API rate limiting and battery consumption.
     */
    fun shouldCheckFromNetwork(
        lastCheckedMillis: Long,
        nowMillis: Long,
        minIntervalMillis: Long = DEFAULT_MIN_INTERVAL_MILLIS
    ): Boolean {
        if (lastCheckedMillis <= 0L) return true
        if (nowMillis < lastCheckedMillis) return true // Clock rollback
        return (nowMillis - lastCheckedMillis) >= minIntervalMillis
    }

    /**
     * Parses the GitHub Releases API JSON payload into structured ReleaseUpdateInfo.
     * Scans assets for the first asset ending in '.apk'.
     */
    fun parseGitHubReleaseJson(
        rawJson: String,
        currentVersion: String = CURRENT_APP_VERSION
    ): ReleaseUpdateInfo {
        val root = (JsonReader.parse(rawJson) as? Map<*, *>) ?: emptyMap<String, Any?>()
        val tagName = root["tag_name"]?.toString()?.trim() ?: ""
        val htmlUrl = root["html_url"]?.toString()?.trim() ?: ""
        val rawBody = root["body"]?.toString()?.trim() ?: ""
        val notes = if (rawBody.length > 240) rawBody.take(240).trim() + "..." else rawBody

        var apkUrl = ""
        val assets = root["assets"] as? List<*>
        if (assets != null) {
            for (item in assets) {
                val assetMap = item as? Map<*, *> ?: continue
                val name = assetMap["name"]?.toString() ?: ""
                val downloadUrl = assetMap["browser_download_url"]?.toString() ?: ""
                if (name.endsWith(".apk", ignoreCase = true) && downloadUrl.isNotBlank()) {
                    apkUrl = downloadUrl
                    break
                }
            }
        }
        if (apkUrl.isBlank()) {
            apkUrl = htmlUrl
        }

        val updateAvailable = isRemoteVersionNewer(currentVersion, tagName)

        return ReleaseUpdateInfo(
            latestVersionTag = tagName,
            updateAvailable = updateAvailable,
            apkDownloadUrl = apkUrl,
            releaseHtmlUrl = htmlUrl,
            releaseNotes = notes,
            lastCheckedAtMillis = System.currentTimeMillis()
        )
    }

    /**
     * Saves the update information into SharedPreferences.
     */
    fun saveUpdateInfo(context: Context, info: ReleaseUpdateInfo) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putLong(KEY_LAST_CHECKED, info.lastCheckedAtMillis)
            .putString(KEY_LATEST_TAG, info.latestVersionTag)
            .putBoolean(KEY_UPDATE_AVAILABLE, info.updateAvailable)
            .putString(KEY_APK_URL, info.apkDownloadUrl)
            .putString(KEY_RELEASE_HTML_URL, info.releaseHtmlUrl)
            .putString(KEY_RELEASE_NOTES, info.releaseNotes)
            .apply()
    }

    /**
     * Retrieves cached update information from SharedPreferences, if present.
     */
    fun getCachedUpdateInfo(context: Context): ReleaseUpdateInfo? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lastChecked = prefs.getLong(KEY_LAST_CHECKED, 0L)
        if (lastChecked == 0L) return null

        val tag = prefs.getString(KEY_LATEST_TAG, "") ?: ""
        val available = prefs.getBoolean(KEY_UPDATE_AVAILABLE, false)
        val apkUrl = prefs.getString(KEY_APK_URL, "") ?: ""
        val htmlUrl = prefs.getString(KEY_RELEASE_HTML_URL, "") ?: ""
        val notes = prefs.getString(KEY_RELEASE_NOTES, "") ?: ""

        return ReleaseUpdateInfo(
            latestVersionTag = tag,
            updateAvailable = available,
            apkDownloadUrl = apkUrl,
            releaseHtmlUrl = htmlUrl,
            releaseNotes = notes,
            lastCheckedAtMillis = lastChecked
        )
    }

    /**
     * Asynchronously queries GitHub Releases with rate limiting and timeout.
     * Never throws exceptions; falls back to cached data or safe defaults.
     */
    suspend fun checkForUpdate(
        context: Context,
        force: Boolean = false,
        currentVersion: String = CURRENT_APP_VERSION
    ): ReleaseUpdateInfo = withContext(Dispatchers.IO) {
        val cached = getCachedUpdateInfo(context)
        val now = System.currentTimeMillis()

        if (!force && cached != null && !shouldCheckFromNetwork(cached.lastCheckedAtMillis, now)) {
            return@withContext cached
        }

        try {
            val url = URL(GITHUB_RELEASE_API_URL)
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 5000
            conn.readTimeout = 5000
            conn.requestMethod = "GET"
            conn.setRequestProperty("Accept", "application/vnd.github+json")
            conn.setRequestProperty("User-Agent", "Hunch-Attention-Twin/$CURRENT_APP_VERSION")

            if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                val reader = BufferedReader(InputStreamReader(conn.inputStream))
                val responseText = reader.use { it.readText() }
                conn.disconnect()

                val info = parseGitHubReleaseJson(responseText, currentVersion)
                saveUpdateInfo(context, info)
                return@withContext info
            }
            conn.disconnect()
        } catch (_: Exception) {
            // Network unreachable, timeout, or rate-limited: fail silently
        }

        return@withContext cached ?: ReleaseUpdateInfo(
            latestVersionTag = currentVersion,
            updateAvailable = false,
            apkDownloadUrl = "",
            releaseHtmlUrl = "",
            releaseNotes = "",
            lastCheckedAtMillis = now
        )
    }

    /**
     * Creates an explicit Intent to open the APK download URL or GitHub Release page in browser.
     */
    fun createDownloadUpdateIntent(downloadUrl: String): Intent {
        val targetUri = if (downloadUrl.isNotBlank()) Uri.parse(downloadUrl) else Uri.parse("https://github.com/abhigyan24-ops/hunch-attention-twin/releases")
        return Intent(Intent.ACTION_VIEW, targetUri).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }
}

package com.dhyan.app.digest

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import java.util.concurrent.ConcurrentHashMap

/**
 * Held notification group aggregated by package for 1-tap break resolution.
 * Raw notification message bodies are NEVER retained or persisted to disk.
 */
data class HeldDigestGroup(
    val packageName: String,
    val appLabel: String,
    val category: String,
    val count: Int,
    val firstHeldAtMillis: Long,
    val latestHeldAtMillis: Long,
    val senderTitles: List<String> = emptyList(),
    @Transient val latestContentIntent: PendingIntent? = null
)

/**
 * In-memory vault holding notifications deferred during Focus Shield sessions.
 * Caches volatile PendingIntents so tapping a group launches straight into the app,
 * and maintains "hunch_held_count" in "hunch_companion_prefs" for Quick Settings & Home Widget.
 */
class BreakDigestVault(private val context: Context? = null) {

    private val groups = ConcurrentHashMap<String, HeldDigestGroup>()

    companion object {
        const val PREFS_NAME = "hunch_companion_prefs"
        const val KEY_HELD_COUNT = "hunch_held_count"
        private const val MAX_SENDER_TITLES = 3

        @Volatile
        private var instance: BreakDigestVault? = null

        fun getInstance(context: Context): BreakDigestVault {
            return instance ?: synchronized(this) {
                instance ?: BreakDigestVault(context.applicationContext).also { instance = it }
            }
        }
    }

    /**
     * Records an intercepted notification into the in-memory digest vault.
     */
    @Synchronized
    fun recordHeldNotification(
        packageName: String,
        appLabel: String,
        senderTitle: String? = null,
        category: String = "general",
        contentIntent: PendingIntent? = null,
        nowMillis: Long = System.currentTimeMillis()
    ) {
        val existing = groups[packageName]
        val updatedSenders = if (!senderTitle.isNullOrBlank()) {
            val current = existing?.senderTitles ?: emptyList()
            // Deduplicate and cap at 3 most recent sender titles
            val filtered = current.filterNot { it.equals(senderTitle, ignoreCase = true) }
            (filtered + senderTitle).takeLast(MAX_SENDER_TITLES)
        } else {
            existing?.senderTitles ?: emptyList()
        }

        val updatedGroup = if (existing != null) {
            existing.copy(
                count = existing.count + 1,
                latestHeldAtMillis = nowMillis,
                senderTitles = updatedSenders,
                latestContentIntent = contentIntent ?: existing.latestContentIntent
            )
        } else {
            HeldDigestGroup(
                packageName = packageName,
                appLabel = appLabel,
                category = category,
                count = 1,
                firstHeldAtMillis = nowMillis,
                latestHeldAtMillis = nowMillis,
                senderTitles = updatedSenders,
                latestContentIntent = contentIntent
            )
        }

        groups[packageName] = updatedGroup
        syncHeldCountToPreferences()
    }

    /**
     * Launches the target application's intent and removes the group from the vault.
     */
    @Synchronized
    fun launchGroupAndDismiss(ctx: Context, packageName: String): Boolean {
        val group = groups[packageName] ?: return false
        var launchSuccess = false

        // 1. Attempt native volatile PendingIntent first
        if (group.latestContentIntent != null) {
            try {
                group.latestContentIntent.send()
                launchSuccess = true
            } catch (_: PendingIntent.CanceledException) {
                launchSuccess = false
            } catch (_: Exception) {
                launchSuccess = false
            }
        }

        // 2. Fall back to package manager launcher intent if PendingIntent failed or expired
        if (!launchSuccess) {
            try {
                val launchIntent = ctx.packageManager.getLaunchIntentForPackage(packageName)
                if (launchIntent != null) {
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    ctx.startActivity(launchIntent)
                    launchSuccess = true
                }
            } catch (_: Exception) {
                launchSuccess = false
            }
        }

        // Dismiss from vault once launch attempt completes
        groups.remove(packageName)
        syncHeldCountToPreferences()
        return launchSuccess
    }

    /**
     * Dismisses a held notification group without launching.
     */
    @Synchronized
    fun dismissGroup(packageName: String): Boolean {
        val removed = groups.remove(packageName) != null
        if (removed) {
            syncHeldCountToPreferences()
        }
        return removed
    }

    /**
     * Returns a snapshot list of all held groups sorted by most recent first.
     */
    fun getHeldGroups(): List<HeldDigestGroup> {
        return groups.values.sortedByDescending { it.latestHeldAtMillis }
    }

    /**
     * Returns total number of held notifications across all groups.
     */
    fun getTotalHeldCount(): Int {
        return groups.values.sumOf { it.count }
    }

    /**
     * Clears all held notifications from the vault.
     */
    @Synchronized
    fun clearAll() {
        groups.clear()
        syncHeldCountToPreferences()
    }

    private fun syncHeldCountToPreferences() {
        val ctx = context ?: return
        val totalCount = getTotalHeldCount()
        try {
            val prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putInt(KEY_HELD_COUNT, totalCount).apply()
        } catch (_: Exception) {
            // Ignore in unit tests or when context unavailable
        }
    }
}

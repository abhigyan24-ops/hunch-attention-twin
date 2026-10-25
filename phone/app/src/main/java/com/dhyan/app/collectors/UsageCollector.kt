package com.dhyan.app.collectors

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import com.dhyan.app.ingest.AppCategorizer
import com.dhyan.app.ingest.ClockOffset
import com.dhyan.app.ingest.Normalizer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * UsageStats-based foreground app poller.
 *
 * Polls [UsageStatsManager.queryEvents] every [POLL_INTERVAL_MS] to detect:
 * - App foreground / background transitions (MOVE_TO_FOREGROUND, MOVE_TO_BACKGROUND)
 * - Keyboard hide (proxy for "done with text input")
 *
 * Privacy:
 * - Package names are resolved to categories immediately; the package name is NEVER stored.
 * - No keystroke or input content is observed.
 *
 * Emits usage window events (appCat, surface, durationMin) via [onWindow].
 *
 * NOTE: PACKAGE_USAGE_STATS must be granted by the user in Settings → Usage access.
 */
class UsageCollector(private val context: Context) {

    companion object {
        /** How often to poll UsageStatsManager for new events. */
        private const val POLL_INTERVAL_MS = 20_000L  // 20 seconds

        /** Minimum foreground duration (minutes) to emit as a usage window. */
        private const val MIN_WINDOW_MIN = 0.25 // 15 seconds
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var pollerJob: Job? = null

    /** Emits (appCat, surface, durationMin, ts) when a foreground window closes. */
    var onWindow: ((appCat: String, surface: String, durationMin: Double, ts: Long) -> Unit)? = null

    /** Last successfully polled timestamp. */
    @Volatile private var lastPollMs: Long = System.currentTimeMillis() - POLL_INTERVAL_MS

    /** Track the currently foregrounded package and when it was last seen in foreground. */
    @Volatile private var activePkg: String? = null
    @Volatile private var activeStartMs: Long = 0L

    fun start() {
        pollerJob?.cancel()
        pollerJob = scope.launch {
            while (isActive) {
                try {
                    poll()
                } catch (_: Exception) {
                    // Usage stats not granted or other error — log silently, never crash
                }
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    fun stop() {
        pollerJob?.cancel()
        pollerJob = null
    }

    private fun poll() {
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager ?: return
        val now = ClockOffset.adjustedNow()
        val query = usm.queryEvents(lastPollMs, now)
        lastPollMs = now

        val event = UsageEvents.Event()
        while (query.hasNextEvent()) {
            query.getNextEvent(event)
            val pkg = event.packageName ?: continue

            when (event.eventType) {
                UsageEvents.Event.MOVE_TO_FOREGROUND -> {
                    activePkg = pkg
                    activeStartMs = event.timeStamp
                }
                UsageEvents.Event.MOVE_TO_BACKGROUND -> {
                    val startPkg = activePkg
                    if (startPkg == pkg && activeStartMs > 0L) {
                        val durationMs = event.timeStamp - activeStartMs
                        val durationMin = durationMs / 60_000.0
                        if (durationMin >= MIN_WINDOW_MIN) {
                            val appCat = AppCategorizer.categorize(pkg)
                            val surface = Normalizer.surfaceForCat(appCat)
                            onWindow?.invoke(appCat, surface, durationMin, event.timeStamp)
                        }
                        activePkg = null
                        activeStartMs = 0L
                    }
                }
            }
        }
    }
}

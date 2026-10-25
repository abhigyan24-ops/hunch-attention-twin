package com.dhyan.app.collectors

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import com.dhyan.app.ingest.AppCategorizer
import com.dhyan.app.ingest.ClockOffset
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Accessibility Service — scroll burst and surface-change collector.
 *
 * PRIVACY CONTRACT — CRITICAL:
 * - canRetrieveWindowContent = FALSE (declared in accessibility_service_config.xml)
 * - This service NEVER reads window content, text nodes, or input fields.
 * - It counts [AccessibilityEvent.TYPE_VIEW_SCROLLED] events to measure engagement intensity.
 * - It reads only the SOURCE PACKAGE NAME from [AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED]
 *   to track app switches — immediately resolved to a category, never stored.
 *
 * Event emission:
 * - Every [BURST_INTERVAL_MS] ms, if scroll count > 0, emit a "scroll_burst" event.
 * - On window state change, emit a "surface_change" event (appCat, surface only).
 */
class A11yCollector : AccessibilityService() {

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var burstJob: Job? = null

    private val scrollCount = AtomicInteger(0)
    private val burstStartMs = AtomicLong(0L)

    /** Emits (appCat, surface, scrollCount, durationMs, ts) for scroll bursts. */
    var onScrollBurst: ((appCat: String, surface: String, scrollCount: Int, durationMs: Long, ts: Long) -> Unit)? = null

    /** Emits (appCat, surface, ts) on surface/app changes. */
    var onSurfaceChange: ((appCat: String, surface: String, ts: Long) -> Unit)? = null

    @Volatile private var currentPkg: String = ""

    companion object {
        /** How often to flush scroll counts as a burst event. */
        const val BURST_INTERVAL_MS = 15_000L // 15 seconds
    }

    override fun onServiceConnected() {
        serviceInfo = serviceInfo.apply {
            // Ensure we only handle the two event types we need
            eventTypes = AccessibilityEvent.TYPE_VIEW_SCROLLED or
                         AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = 0
            notificationTimeout = 100L
        }
        burstStartMs.set(ClockOffset.adjustedNow())
        startBurstFlusher()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return

        when (event.eventType) {
            AccessibilityEvent.TYPE_VIEW_SCROLLED -> {
                scrollCount.incrementAndGet()
            }
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                val pkg = event.packageName?.toString() ?: return
                if (pkg != currentPkg) {
                    currentPkg = pkg
                    val appCat = AppCategorizer.categorize(pkg)
                    val surface = com.dhyan.app.ingest.Normalizer.surfaceForCat(appCat)
                    onSurfaceChange?.invoke(appCat, surface, ClockOffset.adjustedNow())
                }
            }
        }
    }

    override fun onInterrupt() {
        // Service interrupted (e.g. user toggled accessibility off)
        burstJob?.cancel()
    }

    override fun onUnbind(intent: Intent?): Boolean {
        burstJob?.cancel()
        return super.onUnbind(intent)
    }

    private fun startBurstFlusher() {
        burstJob?.cancel()
        burstJob = scope.launch {
            while (isActive) {
                delay(BURST_INTERVAL_MS)
                flushBurst()
            }
        }
    }

    private fun flushBurst() {
        val count = scrollCount.getAndSet(0)
        if (count <= 0) return

        val now = ClockOffset.adjustedNow()
        val start = burstStartMs.getAndSet(now)
        val durationMs = now - start
        val pkg = currentPkg
        if (pkg.isBlank()) return

        val appCat = AppCategorizer.categorize(pkg)
        val surface = com.dhyan.app.ingest.Normalizer.surfaceForCat(appCat)
        onScrollBurst?.invoke(appCat, surface, count, durationMs, now)
    }
}

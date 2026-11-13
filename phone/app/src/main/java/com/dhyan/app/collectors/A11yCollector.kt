package com.dhyan.app.collectors

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast
import com.dhyan.app.DhyanApplication
import com.dhyan.app.ingest.AppCategorizer
import com.dhyan.app.ingest.ClockOffset
import com.dhyan.app.ingest.EpisodeRuntime
import com.dhyan.app.ingest.Normalizer
import com.dhyan.app.twin.model.DeclaredSession
import com.dhyan.app.twin.model.PhoneState
import com.dhyan.app.ui.model.LiveContextHolder
import com.dhyan.app.ui.shield.FocusShieldActivity
import java.util.concurrent.ConcurrentLinkedQueue
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
 * Accessibility Service — scroll burst, surface-change collector, and Focus Shield Interceptor.
 *
 * PRIVACY CONTRACT — CRITICAL:
 * - canRetrieveWindowContent = FALSE (declared in accessibility_service_config.xml)
 * - This service NEVER reads window content, text nodes, or input fields.
 * - It counts [AccessibilityEvent.TYPE_VIEW_SCROLLED] events to measure engagement intensity.
 * - It reads only the SOURCE PACKAGE NAME from [AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED]
 *   to track app switches — immediately resolved to a canonical category, never storing raw text.
 *
 * REAL-TIME ATTENTION PROCESSING & FOCUS RESTRICTION:
 * - When in an active Focus Session, monitors and restricts distracting apps (social media,
 *   reels, shorts, gaming) and compulsive doomscrolling.
 * - Immediately intercepts with [FocusShieldActivity] and returns user to focus.
 * - Supports a 1-minute mindful pause grace period.
 */
class A11yCollector : AccessibilityService() {

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var burstJob: Job? = null

    private val scrollCount = AtomicInteger(0)
    private val burstStartMs = AtomicLong(0L)
    private val recentScrollTimestamps = ConcurrentLinkedQueue<Long>()

    /** Emits (appCat, surface, scrollCount, durationMs, ts) for scroll bursts. */
    var onScrollBurst: ((appCat: String, surface: String, scrollCount: Int, durationMs: Long, ts: Long) -> Unit)? = null

    /** Emits (appCat, surface, ts) on surface/app changes. */
    var onSurfaceChange: ((appCat: String, surface: String, ts: Long) -> Unit)? = null

    @Volatile private var currentPkg: String = ""
    @Volatile private var currentAppCat: String = "other"
    @Volatile private var currentSurface: String = "other"

    companion object {
        const val BURST_INTERVAL_MS = 15_000L // 15 seconds

        var instance: A11yCollector? = null
            private set

        @Volatile var isServiceConnected: Boolean = false
            private set

        /** Temporary mindful pass until timestamp */
        @Volatile var mindfulPauseUntilMs: Long = 0L
            private set

        /** User toggle for Focus Shield blocking */
        @Volatile var focusShieldEnabled: Boolean = true

        /** User toggle for Doomscroll Brake haptic warnings */
        @Volatile var doomscrollBrakeEnabled: Boolean = true

        /** User toggle for Late-Night Bedtime Guardian (11 PM - 6 AM) */
        @Volatile var bedtimeGuardianEnabled: Boolean = true

        /** Running count of blocked distractions and doomscroll loops today */
        val distractionsShieldedCount = AtomicInteger(0)
        val doomscrollsPreventedCount = AtomicInteger(0)

        val ALLOWED_BROWSER_PACKAGES = setOf(
            "com.android.chrome",
            "org.mozilla.firefox",
            "com.brave.browser",
            "com.microsoft.emmx"
        )

        /** Essential utility apps that are NEVER restricted (2FA, Phone, Clock, Calculator, Web Browsers) */
        val ESSENTIAL_WHITELIST_PACKAGES = setOf(
            "com.google.android.dialer",
            "com.samsung.android.dialer",
            "com.android.dialer",
            "com.google.android.apps.authenticator2",
            "com.authy.authy",
            "com.azure.authenticator",
            "com.x8bit.bitwarden",
            "com.onepassword.android",
            "com.google.android.calculator",
            "com.sec.android.app.popupcalculator",
            "com.google.android.deskclock",
            "com.sec.android.app.clockpackage",
            "com.android.settings",
            "com.android.chrome",
            "org.mozilla.firefox",
            "com.brave.browser",
            "com.microsoft.emmx"
        )

        /** User-defined whitelisted packages */
        @Volatile var customAllowedPackages: Set<String> = emptySet()

        val RESTRICTED_APP_CATEGORIES = setOf(
            AppCategorizer.Cat.SOCIAL,
            AppCategorizer.Cat.ENTERTAINMENT,
            "gaming"
        )

        val RESTRICTED_PACKAGES = setOf(
            "com.instagram.android",
            "com.zhiliaoapp.musically", // TikTok
            "com.ss.android.ugc.trill",
            "com.twitter.android",
            "com.reddit.frontpage",
            "com.facebook.katana",
            "com.snapchat.android",
            "com.pinterest"
        )

        fun grantMindfulPause(durationMs: Long = 60_000L) {
            mindfulPauseUntilMs = System.currentTimeMillis() + durationMs
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        isServiceConnected = true

        serviceInfo = serviceInfo.apply {
            eventTypes = AccessibilityEvent.TYPE_VIEW_SCROLLED or
                         AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = 0
            notificationTimeout = 50L
        }
        burstStartMs.set(ClockOffset.adjustedNow())
        startBurstFlusher()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return

        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                val pkg = event.packageName?.toString() ?: return
                if (pkg != currentPkg && pkg != packageName) {
                    currentPkg = pkg
                    val appCat = AppCategorizer.categorize(pkg)
                    val surface = Normalizer.surfaceForCat(appCat)
                    currentAppCat = appCat
                    currentSurface = surface
                    val now = ClockOffset.adjustedNow()

                    // 1. Real-time context feed to ContextFuser
                    val sync = runCatching { DhyanApplication.syncManager }.getOrNull()
                    sync?.contextFuser?.updatePhoneState(
                        phoneState = PhoneState.IN_APP,
                        phoneAppCat = appCat
                    )

                    // 2. Canonical event persistence and real-time outbound sync
                    val payload = mapOf("pkg" to pkg, "surface" to surface)
                    sync?.recordLocalEvent("surface_change", "a11y", payload, now)
                    EpisodeRuntime.instance?.onAppForeground(appCat, surface, now)
                    onSurfaceChange?.invoke(appCat, surface, now)

                    // 3. Real-Time Focus Shield Enforcement
                    checkAndEnforceFocusShield(pkg, appCat, surface, now)
                }
            }

            AccessibilityEvent.TYPE_VIEW_SCROLLED -> {
                scrollCount.incrementAndGet()
                val now = ClockOffset.adjustedNow()
                recentScrollTimestamps.add(now)

                // Prune scrolls older than 10 seconds
                while (true) {
                    val first = recentScrollTimestamps.peek() ?: break
                    if (now - first > 10_000L) {
                        recentScrollTimestamps.poll()
                    } else {
                        break
                    }
                }

                val recentScrollCount = recentScrollTimestamps.size
                val sync = runCatching { DhyanApplication.syncManager }.getOrNull()

                // If scrolling rapidly (>8 scrolls in 10s), mark phone state as SCROLLING
                if (recentScrollCount >= 8) {
                    sync?.contextFuser?.updatePhoneState(
                        phoneState = PhoneState.SCROLLING,
                        phoneAppCat = currentAppCat
                    )

                    // Check for Doomscroll in distracting surfaces
                    val activeSession = getActiveFocusSession(now)
                    val isDistractingSurface = currentAppCat in RESTRICTED_APP_CATEGORIES ||
                        currentPkg in RESTRICTED_PACKAGES || currentSurface in setOf("feed", "shorts")

                    if (activeSession != null && isDistractingSurface && focusShieldEnabled) {
                        if (now > mindfulPauseUntilMs) {
                            recentScrollTimestamps.clear()
                            doomscrollsPreventedCount.incrementAndGet()
                            interceptDistraction(currentPkg, currentAppCat, activeSession, "doomscroll_detected")
                        }
                    } else {
                        // Bedtime Guardian Check (11 PM - 6 AM)
                        val cal = java.util.Calendar.getInstance()
                        val hour = cal.get(java.util.Calendar.HOUR_OF_DAY)
                        val isLateNight = hour >= 23 || hour < 6

                        if (bedtimeGuardianEnabled && isLateNight && isDistractingSurface && recentScrollCount >= 10) {
                            if (now > mindfulPauseUntilMs) {
                                recentScrollTimestamps.clear()
                                doomscrollsPreventedCount.incrementAndGet()
                                triggerBedtimeInterception(currentPkg, currentAppCat)
                            }
                        } else if (doomscrollBrakeEnabled && isDistractingSurface && recentScrollCount >= 12) {
                            // Gentle Doomscroll Brake haptic pulse when scrolling without active session
                            triggerDoomscrollHaptic()
                        }
                    }
                }
            }
        }
    }

    private fun checkAndEnforceFocusShield(pkg: String, appCat: String, surface: String, nowMs: Long) {
        if (!focusShieldEnabled) return

        // Respect essential utilities and custom whitelist
        if (pkg in ESSENTIAL_WHITELIST_PACKAGES || pkg in customAllowedPackages) {
            return
        }

        val activeSession = getActiveFocusSession(nowMs) ?: return
        val isRestricted = pkg in RESTRICTED_PACKAGES || appCat in RESTRICTED_APP_CATEGORIES

        if (isRestricted) {
            if (nowMs > mindfulPauseUntilMs) {
                distractionsShieldedCount.incrementAndGet()
                interceptDistraction(pkg, appCat, activeSession, "app_restricted")
            }
        }
    }

    private fun getActiveFocusSession(nowMs: Long): DeclaredSession? {
        val sync = runCatching { DhyanApplication.syncManager }.getOrNull()
        val snapshot = sync?.contextFuser?.currentSnapshot() ?: LiveContextHolder.currentSnapshot()
        val session = snapshot?.declaredSession ?: return null
        val durationMs = (session.plannedMin * 60_000L).toLong()
        return if (nowMs >= session.startedTs && nowMs < session.startedTs + durationMs) {
            session
        } else {
            null
        }
    }

    private fun interceptDistraction(
        pkg: String,
        appCat: String,
        activeSession: DeclaredSession,
        reason: String
    ) {
        // 1. Immediately exit the distracting app via Home action
        performGlobalAction(GLOBAL_ACTION_HOME)

        // 2. Gentle haptic alert
        triggerDoomscrollHaptic()

        // 3. Mark state as DISTRACTED and sync across to laptop & VS Code
        val sync = runCatching { DhyanApplication.syncManager }.getOrNull()
        sync?.contextFuser?.updatePhoneState(
            phoneState = PhoneState.SCROLLING,
            phoneAppCat = appCat
        )

        // 4. Launch Daylight Focus Shield Activity
        val remainingMin = maxOf(
            1,
            ((activeSession.startedTs + (activeSession.plannedMin * 60_000L) - System.currentTimeMillis()) / 60_000L).toInt()
        )

        val shieldIntent = Intent(this, FocusShieldActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra(FocusShieldActivity.EXTRA_BLOCKED_APP, appCat)
            putExtra(FocusShieldActivity.EXTRA_REASON, reason)
            putExtra(FocusShieldActivity.EXTRA_TASK_NAME, activeSession.task.wire)
            putExtra(FocusShieldActivity.EXTRA_REMAINING_MIN, remainingMin)
        }
        startActivity(shieldIntent)
    }

    private fun triggerBedtimeInterception(pkg: String, appCat: String) {
        performGlobalAction(GLOBAL_ACTION_HOME)
        triggerDoomscrollHaptic()

        val sync = runCatching { DhyanApplication.syncManager }.getOrNull()
        sync?.contextFuser?.updatePhoneState(
            phoneState = PhoneState.SCROLLING,
            phoneAppCat = appCat
        )

        val shieldIntent = Intent(this, FocusShieldActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra(FocusShieldActivity.EXTRA_BLOCKED_APP, appCat)
            putExtra(FocusShieldActivity.EXTRA_REASON, "bedtime_guardian")
            putExtra(FocusShieldActivity.EXTRA_TASK_NAME, "Sleep")
            putExtra(FocusShieldActivity.EXTRA_REMAINING_MIN, 0)
        }
        startActivity(shieldIntent)
    }

    private fun triggerDoomscrollHaptic() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator?.vibrate(
                    VibrationEffect.createWaveform(longArrayOf(0, 100, 80, 100), -1)
                )
            } else {
                @Suppress("DEPRECATION")
                val v = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    v?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 100, 80, 100), -1))
                } else {
                    @Suppress("DEPRECATION")
                    v?.vibrate(150L)
                }
            }
        } catch (_: Exception) {}
    }

    override fun onInterrupt() {
        burstJob?.cancel()
        instance = null
        isServiceConnected = false
    }

    override fun onUnbind(intent: Intent?): Boolean {
        burstJob?.cancel()
        instance = null
        isServiceConnected = false
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        burstJob?.cancel()
        instance = null
        isServiceConnected = false
        super.onDestroy()
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
        val durationMs = maxOf(1000L, now - start)
        val pkg = currentPkg.ifBlank { "unknown" }
        val appCat = currentAppCat
        val surface = currentSurface

        onScrollBurst?.invoke(appCat, surface, count, durationMs, now)

        val sync = runCatching { DhyanApplication.syncManager }.getOrNull()
        val windowSec = durationMs / 1000.0
        val payload = mapOf(
            "pkg" to pkg,
            "count" to count,
            "windowSec" to windowSec,
            "surface" to surface
        )
        sync?.recordLocalEvent("scroll_burst", "a11y", payload, now)
    }
}

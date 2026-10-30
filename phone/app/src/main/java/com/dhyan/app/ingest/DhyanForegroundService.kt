package com.dhyan.app.ingest

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import com.dhyan.app.R
import com.dhyan.app.collectors.ScreenReceiver
import com.dhyan.app.collectors.UsageCollector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Dhyan foreground service — keeps all collectors alive.
 *
 * Runs as a foreground service with type "dataSync" so it survives background-process
 * killing on ColorOS (Oppo Reno 14). The persistent notification tells the user that
 * monitoring is active — required for transparency.
 *
 * Lifecycle:
 * - Started by [BootReceiver] on device boot and by the UI on first setup.
 * - Starts [UsageCollector] (polls UsageStatsManager every 20s).
 * - Registers [ScreenReceiver] dynamically (cannot be static for screen events on API 26+).
 * - [NotifCollector] and [A11yCollector] are declared in the manifest and started by Android
 *   when their respective permissions are granted.
 *
 * Stage 4 will wire the Room database and InMemoryParamStore into this service.
 * Stage 5 will wire the decision engine.
 */
class DhyanForegroundService : Service() {

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private lateinit var usageCollector: UsageCollector
    private lateinit var screenReceiver: ScreenReceiver
    private var screenReceiverRegistered = false

    companion object {
        private const val CHANNEL_ID   = "dhyan_service"
        private const val NOTIF_ID     = 1001
        const val ACTION_STOP          = "com.dhyan.app.STOP_SERVICE"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIF_ID, buildForegroundNotification(), android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIF_ID, buildForegroundNotification(), android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_NONE)
        } else {
            startForeground(NOTIF_ID, buildForegroundNotification())
        }

        // Start usage poller
        usageCollector = UsageCollector(this)
        usageCollector.onWindow = { appCat, surface, durationMin, ts ->
            // Stage 4: write to Room EventDao, update InMemoryParamStore
            onUsageWindow(appCat, surface, durationMin, ts)
        }
        usageCollector.start()

        // Register screen receiver dynamically
        screenReceiver = ScreenReceiver()
        screenReceiver.onScreenEvent = { kind, ts ->
            onScreenEvent(kind, ts)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(screenReceiver, ScreenReceiver.intentFilter(),
                RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(screenReceiver, ScreenReceiver.intentFilter())
        }
        screenReceiverRegistered = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onDestroy() {
        usageCollector.stop()
        if (screenReceiverRegistered) {
            try { unregisterReceiver(screenReceiver) } catch (_: Exception) {}
            screenReceiverRegistered = false
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onTimeout(startId: Int, fgsType: Int) { stopSelf() }

    // ── Event stubs — Stage 4 replaces these with real Room writes ─────────

    private fun onUsageWindow(appCat: String, surface: String, durationMin: Double, ts: Long) {
        val durationMs = (durationMin * 60_000).toLong()
        val startTs = ts - durationMs

        // 1. Feed the fuser with live in-app state
        com.dhyan.app.DhyanApplication.syncManager.contextFuser.updatePhoneState(
            phoneState = com.dhyan.app.twin.model.PhoneState.IN_APP,
            phoneAppCat = appCat
        )

        // 2. Record canonical usage events for outbound sync & Room persistence
        val canonicalSurface = when (surface) {
            "feed", "shorts", "watch", "chat", "editor" -> surface
            else -> "other"
        }
        com.dhyan.app.DhyanApplication.syncManager.recordLocalEvent(
            type = "app_foreground",
            source = "usage",
            payload = mapOf("pkg" to "unknown", "appCat" to appCat, "surface" to canonicalSurface),
            ts = startTs
        )
        com.dhyan.app.DhyanApplication.syncManager.recordLocalEvent(
            type = "app_background",
            source = "usage",
            payload = mapOf("pkg" to "unknown", "durationMs" to durationMs),
            ts = ts
        )

        // 3. Feed EpisodeRuntime
        EpisodeRuntime.instance?.let { runtime ->
            runtime.onAppForeground(appCat, surface, startTs)
            runtime.onAppBackground(durationMs, ts)
        }
    }

    private fun onScreenEvent(kind: String, ts: Long) {
        if (kind in setOf("screen_on", "screen_off", "pickup", "unlock")) {
            com.dhyan.app.DhyanApplication.syncManager.recordLocalEvent(kind, "screen", emptyMap(), ts)

            val pState = when (kind) {
                "screen_off" -> com.dhyan.app.twin.model.PhoneState.SCREEN_OFF
                "screen_on", "unlock", "pickup" -> com.dhyan.app.twin.model.PhoneState.IN_APP
                else -> com.dhyan.app.twin.model.PhoneState.SCREEN_OFF
            }
            com.dhyan.app.DhyanApplication.syncManager.contextFuser.updatePhoneState(phoneState = pState)
        }
        EpisodeRuntime.instance?.let { runtime ->
            when (kind) {
                "screen_off" -> runtime.onScreenOff(ts)
                "screen_on"  -> runtime.onScreenOn(ts)
                "pickup"     -> runtime.onPickup(ts)
            }
        }
    }

    // ── Notification helpers ────────────────────────────────────────────────

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notif_channel_name),
                NotificationManager.IMPORTANCE_MIN
            ).apply {
                description = getString(R.string.notif_channel_desc)
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java)
                ?.createNotificationChannel(channel)
        }
    }

    private fun buildForegroundNotification(): Notification {
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
        return builder
            .setContentTitle(getString(R.string.notif_title))
            .setContentText(getString(R.string.notif_text))
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setOngoing(true)
            .build()
    }
}

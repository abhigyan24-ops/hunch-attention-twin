package com.dhyan.app.onboarding

import android.app.AppOpsManager
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.provider.Settings
import com.dhyan.app.collectors.A11yCollector
import com.dhyan.app.collectors.NotifCollector

data class TrustStatus(
    val usageAccess: Boolean,
    val notificationAccess: Boolean,
    val accessibilityAccess: Boolean,
    val overlayAccess: Boolean,
) {
    val coreAvailable: Boolean get() = true

    fun isGranted(capability: TrustCapability): Boolean = when (capability) {
        TrustCapability.BEDTIME -> usageAccess
        TrustCapability.QUIET_FOCUS -> notificationAccess
        TrustCapability.ADVANCED_CONTEXT -> accessibilityAccess
        TrustCapability.FLOATING_HUD -> overlayAccess
    }
}

class ProgressiveTrustManager(context: Context) {
    private val app = context.applicationContext
    private val preferences = app.getSharedPreferences("progressive_trust", Context.MODE_PRIVATE)

    @Suppress("DEPRECATION")
    fun status(): TrustStatus {
        val ops = app.getSystemService(AppOpsManager::class.java)
        val operation = AppOpsManager.OPSTR_GET_USAGE_STATS
        val uid = app.applicationInfo.uid
        val usageMode = if (Build.VERSION.SDK_INT >= 29) {
            ops?.unsafeCheckOpNoThrow(operation, uid, app.packageName)
        } else {
            ops?.checkOpNoThrow(operation, uid, app.packageName)
        }
        val listener = ComponentName(app, NotifCollector::class.java)
        val notificationAccess = if (Build.VERSION.SDK_INT >= 27) {
            app.getSystemService(NotificationManager::class.java)
                ?.isNotificationListenerAccessGranted(listener) == true
        } else {
            componentEnabled("enabled_notification_listeners", listener)
        }
        val accessibility = ComponentName(app, A11yCollector::class.java)
        return TrustStatus(
            usageAccess = usageMode == AppOpsManager.MODE_ALLOWED,
            notificationAccess = notificationAccess,
            accessibilityAccess = componentEnabled(Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, accessibility),
            overlayAccess = Settings.canDrawOverlays(app),
        )
    }

    private fun componentEnabled(setting: String, component: ComponentName): Boolean =
        Settings.Secure.getString(app.contentResolver, setting).orEmpty().split(':')
            .any { ComponentName.unflattenFromString(it) == component }

    fun wasDismissed(capability: TrustCapability): Boolean =
        preferences.getBoolean(capability.name, false)

    fun recordDismissal(capability: TrustCapability) {
        preferences.edit().putBoolean(capability.name, true).apply()
    }
}

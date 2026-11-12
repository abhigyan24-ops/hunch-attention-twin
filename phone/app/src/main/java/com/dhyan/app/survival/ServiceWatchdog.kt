package com.dhyan.app.survival

import android.content.ComponentName
import android.content.Context
import android.service.notification.NotificationListenerService

object ServiceWatchdog {
    /**
     * Requests Android to rebind the NotificationListenerService.
     * Call this if the notification listener is detected as unbound.
     */
    fun ensureNotificationListenerBound(context: Context, componentName: ComponentName) {
        try {
            NotificationListenerService.requestRebind(componentName)
        } catch (e: Exception) {
            // Ignore — cannot rebind in some system states
        }
    }
}

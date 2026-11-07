package com.dhyan.app.sync.data

import android.content.Context
import java.util.UUID

/**
 * Manages unique persistent device identification in SharedPreferences.
 * Ensures every installation starts with its own distinct UUID.
 */
object DeviceIdProvider {
    private const val PREFS_NAME = "dhyan_device_prefs"
    private const val KEY_DEVICE_ID = "device_id"

    @Volatile
    private var cachedDeviceId: String? = null

    /**
     * Retrieves the persistent unique device ID, generating and persisting a new UUID if absent.
     */
    fun getDeviceId(context: Context): String {
        cachedDeviceId?.let { return it }
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        var id = prefs.getString(KEY_DEVICE_ID, null)
        if (id.isNullOrBlank()) {
            id = UUID.randomUUID().toString()
            prefs.edit().putString(KEY_DEVICE_ID, id).apply()
        }
        cachedDeviceId = id
        return id
    }

    /**
     * Resets the persistent device ID with a fresh UUID.
     * Called during "Wipe & Reset My Data".
     */
    fun resetDeviceId(context: Context): String {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val id = UUID.randomUUID().toString()
        prefs.edit().putString(KEY_DEVICE_ID, id).apply()
        cachedDeviceId = id
        return id
    }
}

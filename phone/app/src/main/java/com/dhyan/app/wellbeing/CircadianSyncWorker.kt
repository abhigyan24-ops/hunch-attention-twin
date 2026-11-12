package com.dhyan.app.wellbeing

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Posts the BedtimeSchedule to the engine's /v1/wellbeing/circadian endpoint.
 * Uses OkHttp (already in the project). Call after any BedtimeManager save.
 */
class CircadianSyncWorker(context: Context) {

    private val prefs = context.getSharedPreferences("sync_config", Context.MODE_PRIVATE)

    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    /** Engine base URL — stored by SyncManager in "engine_url" prefs key */
    private fun engineUrl(): String {
        return prefs.getString("engine_url", "http://127.0.0.1:8765") ?: "http://127.0.0.1:8765"
    }

    suspend fun syncSchedule(schedule: BedtimeSchedule) = withContext(Dispatchers.IO) {
        try {
            val lastNightSleepMinutes = schedule.durationMinutes()
            val body = JSONObject().apply {
                put("bedtimeHour", schedule.bedtimeHour)
                put("bedtimeMinute", schedule.bedtimeMinute)
                put("wakeHour", schedule.wakeHour)
                put("wakeMinute", schedule.wakeMinute)
                put("lastNightSleepMinutes", lastNightSleepMinutes)
                put("isAutoDetected", schedule.isAutoDetected)
            }.toString()
            val request = Request.Builder()
                .url("${engineUrl()}/v1/wellbeing/circadian")
                .post(body.toRequestBody("application/json".toMediaType()))
                .build()
            client.newCall(request).execute().use { /* fire and forget */ }
        } catch (_: Exception) {
            // Network unavailable — silently ignore, OfflineEventQueue would handle retry
        }
    }
}

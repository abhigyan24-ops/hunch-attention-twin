package com.dhyan.app.share

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

class ShareToDeskWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val config = applicationContext.getSharedPreferences("sync_config", Context.MODE_PRIVATE)
        val endpoint = config.getString("engine_url", null)?.toHttpUrlOrNull()
        val token = config.getString("engine_token", null)
        if (endpoint == null || !endpoint.isHttps || token.isNullOrBlank()) return@withContext Result.retry()
        val url = endpoint.newBuilder().encodedPath("/v1/flow/share-to-desk").query(null).fragment(null).build()
        val queue = ShareToDeskManager(applicationContext).queue
        repeat(200) {
            val item = queue.first() ?: return@withContext Result.success()
            val request = Request.Builder().url(url).header("X-Twin-Token", token)
                .post(item.json().toString().toRequestBody("application/json".toMediaType())).build()
            val accepted = try {
                client.newCall(request).execute().use { it.isSuccessful }
            } catch (_: IOException) {
                false
            }
            if (!accepted || !queue.acknowledge(item.id)) return@withContext Result.retry()
        }
        if (queue.first() == null) Result.success() else Result.retry()
    }

    private companion object {
        val client = OkHttpClient.Builder().followRedirects(false).followSslRedirects(false)
            .callTimeout(10, TimeUnit.SECONDS).build()
    }
}

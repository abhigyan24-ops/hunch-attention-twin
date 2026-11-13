package com.dhyan.app.share

import android.content.Context
import androidx.work.*
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class SharedDeskItem(
    val id: String, val rawText: String, val extractedUrl: String?,
    val title: String?, val itemType: String, val createdAtMillis: Long,
) {
    internal fun json() = JSONObject().apply {
        put("id", id)
        put("rawText", rawText)
        put("extractedUrl", extractedUrl ?: JSONObject.NULL)
        put("title", title ?: JSONObject.NULL)
        put("itemType", itemType)
        put("createdAtMillis", createdAtMillis)
    }
}

internal class DeskQueue(
    private val read: () -> List<SharedDeskItem>,
    private val write: (List<SharedDeskItem>) -> Boolean,
) {
    fun append(item: SharedDeskItem) = synchronized(lock) { write((read() + item).takeLast(200)) }
    fun first() = synchronized(lock) { read().firstOrNull() }
    fun acknowledge(id: String) = synchronized(lock) { write(read().filterNot { it.id == id }) }
    private companion object { val lock = Any() }
}

class ShareToDeskManager(context: Context) {
    private val app = context.applicationContext
    private val preferences = app.getSharedPreferences("hunch_desk_queue", Context.MODE_PRIVATE)
    internal val queue = DeskQueue(
        read = {
            val array = JSONArray(preferences.getString("items", "[]"))
            List(array.length()) { index ->
                val item = array.getJSONObject(index)
                SharedDeskItem(
                    item.getString("id"), item.getString("rawText"),
                    item.optString("extractedUrl").takeUnless { item.isNull("extractedUrl") },
                    item.optString("title").takeUnless { item.isNull("title") },
                    item.getString("itemType"), item.getLong("createdAtMillis"),
                )
            }
        },
        write = { items -> preferences.edit().putString("items", JSONArray(items.map { it.json() }).toString()).commit() },
    )

    fun save(item: SharedDeskItem): Boolean = queue.append(item.copy(id = UUID.randomUUID().toString()))

    fun scheduleSync() {
        val request = OneTimeWorkRequestBuilder<ShareToDeskWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(app).enqueueUniqueWork("hunch-desk-sync", ExistingWorkPolicy.APPEND_OR_REPLACE, request)
    }

    companion object {
        private val urls = Regex("""https?://[^\s<>"']+""", RegexOption.IGNORE_CASE)
        private val code = Regex("""\b(?:fun|function|const|def|class)\s+|[{}]|=>|(?m)^\s*at\s+|\b\w*Exception\b""")
        fun parseIncomingShare(extraText: String?, extraSubject: String?, nowMillis: Long): SharedDeskItem? {
            val raw = extraText?.takeUnless(String::isBlank) ?: return null
            val url = urls.findAll(raw).map { it.value.trimEnd('.', ',', ';', '!', ')', ']', '}') }
                .firstOrNull { it.toHttpUrlOrNull() != null }
            val type = when {
                url != null -> "url"
                code.containsMatchIn(raw) -> "code_snippet"
                else -> "note"
            }
            val title = extraSubject?.trim()?.takeIf(String::isNotEmpty)
            val id = UUID.nameUUIDFromBytes("$nowMillis\u0000$raw\u0000$title".toByteArray()).toString()
            return SharedDeskItem(id, raw, url, title, type, nowMillis)
        }
    }
}

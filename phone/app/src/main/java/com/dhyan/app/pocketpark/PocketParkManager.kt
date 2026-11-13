package com.dhyan.app.pocketpark

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Parked thought or lookup intention captured to avoid browser tab rabbit holes.
 */
data class ParkedThought(
    val id: String,
    val text: String,
    val createdAtMillis: Long,
    val isCheckedOff: Boolean = false
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("id", id)
            put("text", text)
            put("createdAtMillis", createdAtMillis)
            put("isCheckedOff", isCheckedOff)
        }
    }

    companion object {
        fun fromJson(json: JSONObject): ParkedThought {
            return ParkedThought(
                id = json.optString("id", UUID.randomUUID().toString()),
                text = json.optString("text", ""),
                createdAtMillis = json.optLong("createdAtMillis", System.currentTimeMillis()),
                isCheckedOff = json.optBoolean("isCheckedOff", false)
            )
        }
    }
}

/**
 * Quick-capture manager for offloading distracting thoughts during deep work.
 * Persists locally in SharedPreferences with a 100-item FIFO cap.
 */
class PocketParkManager(private val context: Context? = null) {

    private val thoughts = mutableListOf<ParkedThought>()

    companion object {
        const val PREFS_NAME = "hunch_pocket_park_prefs"
        const val KEY_THOUGHTS_JSON = "parked_thoughts_json"
        const val FIFO_CAP = 100

        @Volatile
        private var instance: PocketParkManager? = null

        fun getInstance(context: Context): PocketParkManager {
            return instance ?: synchronized(this) {
                instance ?: PocketParkManager(context.applicationContext).also { instance = it }
            }
        }
    }

    init {
        loadThoughts()
    }

    @Synchronized
    fun parkThought(text: String, nowMillis: Long = System.currentTimeMillis()): ParkedThought {
        val trimmed = text.trim()
        val thought = ParkedThought(
            id = UUID.randomUUID().toString(),
            text = trimmed,
            createdAtMillis = nowMillis,
            isCheckedOff = false
        )

        thoughts.add(0, thought) // Insert at top
        if (thoughts.size > FIFO_CAP) {
            thoughts.removeAt(thoughts.size - 1)
        }

        saveThoughts()
        return thought
    }

    @Synchronized
    fun getParkedThoughts(): List<ParkedThought> {
        return thoughts.toList()
    }

    @Synchronized
    fun getActiveThoughts(): List<ParkedThought> {
        return thoughts.filter { !it.isCheckedOff }
    }

    @Synchronized
    fun checkOffThought(id: String): Boolean {
        val idx = thoughts.indexOfFirst { it.id == id }
        if (idx != -1) {
            val item = thoughts[idx]
            thoughts[idx] = item.copy(isCheckedOff = !item.isCheckedOff)
            saveThoughts()
            return true
        }
        return false
    }

    @Synchronized
    fun dismissThought(id: String): Boolean {
        val removed = thoughts.removeAll { it.id == id }
        if (removed) {
            saveThoughts()
        }
        return removed
    }

    @Synchronized
    fun clearCheckedThoughts() {
        thoughts.removeAll { it.isCheckedOff }
        saveThoughts()
    }

    @Synchronized
    fun clearAll() {
        thoughts.clear()
        saveThoughts()
    }

    private fun loadThoughts() {
        val ctx = context ?: return
        try {
            val prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val jsonStr = prefs.getString(KEY_THOUGHTS_JSON, null) ?: return
            val array = JSONArray(jsonStr)
            thoughts.clear()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                thoughts.add(ParkedThought.fromJson(obj))
            }
        } catch (_: Exception) {}
    }

    private fun saveThoughts() {
        val ctx = context ?: return
        try {
            val array = JSONArray()
            for (t in thoughts) {
                array.put(t.toJson())
            }
            val prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putString(KEY_THOUGHTS_JSON, array.toString()).apply()
        } catch (_: Exception) {}
    }
}

package com.dhyan.app.sync.data

import com.dhyan.app.personas.json.JsonReader
import com.dhyan.app.sync.session.RelayClient

/** Evaluates the frozen schemas' vocabulary. No network resolution or payload diagnostics. */
class WireSchemas(private val read: (String) -> String) {
    companion object {
        @Volatile var current = WireSchemas { name ->
            requireNotNull(WireSchemas::class.java.classLoader?.getResourceAsStream("wire/$name"))
                .bufferedReader().use { it.readText() }
        }
    }
    private val cache = mutableMapOf<String, Map<*, *>>()
    @Synchronized
    private fun schema(name: String): Map<*, *> = cache.getOrPut(name) {
        JsonReader.parse(read(name)) as Map<*, *>
    }

    fun valid(name: String, value: Any?): Boolean = runCatching {
        matches(schema(name), value)
    }.getOrDefault(false)

    fun event(value: Map<*, *>): Boolean =
        RelayClient.serializeJson(value).toByteArray(Charsets.UTF_8).size <= 8192 &&
            PrivacyGuard.isClean(value) && valid("event.schema.json", value)

    private fun same(a: Any?, b: Any?): Boolean =
        if (a is Number && b is Number) a.toDouble() == b.toDouble() else a == b

    private fun matches(raw: Any?, value: Any?): Boolean {
        if (raw is Boolean) return raw
        val s = raw as? Map<*, *> ?: return false
        (s["\$ref"] as? String)?.let { ref ->
            if (!matches(schema(ref.substringAfterLast('/')), value)) return false
        }
        if (s.containsKey("const") && !same(s["const"], value)) return false
        (s["enum"] as? List<*>)?.let { if (it.none { candidate -> same(candidate, value) }) return false }
        fun type(t: Any?): Boolean = when (t) {
            "null" -> value == null
            "object" -> value is Map<*, *>
            "array" -> value is List<*>
            "string" -> value is String
            "boolean" -> value is Boolean
            "number" -> value is Number && value.toDouble().isFinite()
            "integer" -> value is Number && value.toDouble().isFinite() && value.toDouble() % 1.0 == 0.0 && kotlin.math.abs(value.toDouble()) <= 9_007_199_254_740_991.0
            else -> false
        }
        s["type"]?.let { if (it is List<*>) { if (it.none(::type)) return false } else if (!type(it)) return false }
        (s["allOf"] as? List<*>)?.let { if (!it.all { x -> matches(x, value) }) return false }
        (s["anyOf"] as? List<*>)?.let { if (!it.any { x -> matches(x, value) }) return false }
        (s["oneOf"] as? List<*>)?.let { if (it.count { x -> matches(x, value) } != 1) return false }
        if (s.containsKey("if") && matches(s["if"], value) && s.containsKey("then") && !matches(s["then"], value)) return false
        if (value is Map<*, *>) {
            val props = s["properties"] as? Map<*, *> ?: emptyMap<Any?, Any?>()
            if ((s["required"] as? List<*>)?.any { !value.containsKey(it) } == true) return false
            if (s["additionalProperties"] == false && value.keys.any { !props.containsKey(it) }) return false
            if (value.any { (k, v) -> props.containsKey(k) && !matches(props[k], v) }) return false
        }
        if (value is List<*>) {
            if ((s["minItems"] as? Number)?.let { value.size < it.toInt() } == true) return false
            if ((s["maxItems"] as? Number)?.let { value.size > it.toInt() } == true) return false
            if (s.containsKey("items") && !value.all { matches(s["items"], it) }) return false
            if (s.containsKey("contains") && !value.any { matches(s["contains"], it) }) return false
        }
        if (value is Number) {
            if ((s["minimum"] as? Number)?.let { value.toDouble() < it.toDouble() } == true) return false
            if ((s["maximum"] as? Number)?.let { value.toDouble() > it.toDouble() } == true) return false
        }
        if (value is String) {
            val len = value.codePointCount(0, value.length)
            if ((s["minLength"] as? Number)?.let { len < it.toInt() } == true) return false
            if ((s["maxLength"] as? Number)?.let { len > it.toInt() } == true) return false
            if ((s["pattern"] as? String)?.let { !Regex(it).containsMatchIn(value) } == true) return false
            if (s["format"] == "uuid" && !Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$").matches(value)) return false
        }
        return true
    }
}

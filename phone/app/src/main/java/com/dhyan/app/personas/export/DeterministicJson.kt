package com.dhyan.app.personas.export

/**
 * 4A-4: Minimal deterministic JSON serializer for the export layer.
 * Only handles the types produced by GenEvent.envelope() and truth records.
 * Key ordering is preserved (LinkedHashMap from Kotlin map literals).
 * No external JSON library required; the bundled JsonReader (4A-1) is input-only.
 */
internal object DeterministicJson {
    fun write(value: Any?): String = buildString { appendValue(value, this) }

    private fun appendValue(value: Any?, sb: StringBuilder) {
        when (value) {
            null -> sb.append("null")
            is Boolean -> sb.append(value.toString())
            is Int -> sb.append(value.toString())
            is Long -> sb.append(value.toString())
            is Double -> {
                if (value.isNaN() || value.isInfinite()) throw IllegalArgumentException("Non-finite number in JSON output")
                sb.append(value.toBigDecimal().stripTrailingZeros().toPlainString())
            }
            is String -> {
                sb.append('"')
                for (c in value) when (c) {
                    '"'  -> sb.append("\\\"")
                    '\\' -> sb.append("\\\\")
                    '\n' -> sb.append("\\n")
                    '\r' -> sb.append("\\r")
                    '\t' -> sb.append("\\t")
                    else -> if (c.code < 0x20) sb.append("\\u%04x".format(c.code)) else sb.append(c)
                }
                sb.append('"')
            }
            is Map<*, *> -> {
                sb.append('{')
                value.entries.forEachIndexed { i, (k, v) ->
                    if (i > 0) sb.append(',')
                    appendValue(k.toString(), sb); sb.append(':'); appendValue(v, sb)
                }
                sb.append('}')
            }
            is Iterable<*> -> {
                sb.append('[')
                value.forEachIndexed { i, v -> if (i > 0) sb.append(','); appendValue(v, sb) }
                sb.append(']')
            }
            else -> throw IllegalArgumentException("Unsupported JSON value type: ${value::class.simpleName}")
        }
    }
}

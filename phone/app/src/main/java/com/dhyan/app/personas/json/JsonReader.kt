package com.dhyan.app.personas.json

/** Independent strict JSON reader. No platform JSON stubs or runtime dependencies. */
object JsonReader {
    fun parse(input: String): Any? = Parser(input).parse()
    private class Parser(val input: String) {
        var pos = 0
        fun bad(): Nothing = throw IllegalArgumentException("Invalid JSON at offset $pos")
        fun ws() { while (pos < input.length && input[pos] in " \t\r\n") pos++ }
        fun take(c: Char): Boolean { ws(); if (pos < input.length && input[pos] == c) { pos++; return true }; return false }
        fun parse(): Any? {
            require(input.length <= 1_048_576) { "JSON exceeds size limit" }
            val result = value(0); ws(); if (pos != input.length) bad(); return result
        }
        fun value(depth: Int): Any? {
            if (depth > 64) bad()
            ws(); if (pos >= input.length) bad()
            return when (input[pos]) {
                '{' -> {
                    pos++; val map = linkedMapOf<String, Any?>()
                    if (!take('}')) do {
                        ws(); if (pos >= input.length || input[pos] != '"') bad()
                        val key = string(); if (map.containsKey(key) || !take(':')) bad()
                        map[key] = value(depth + 1)
                        if (take('}')) break
                        if (!take(',')) bad()
                    } while (true)
                    map
                }
                '[' -> {
                    pos++; val list = arrayListOf<Any?>()
                    if (!take(']')) do {
                        list.add(value(depth + 1)); if (take(']')) break
                        if (!take(',')) bad()
                    } while (true)
                    list
                }
                '"' -> string()
                't' -> literal("true", true)
                'f' -> literal("false", false)
                'n' -> literal("null", null)
                else -> number()
            }
        }
        fun literal(word: String, result: Any?): Any? {
            if (!input.startsWith(word, pos)) bad(); pos += word.length; return result
        }
        fun string(): String {
            pos++; val out = StringBuilder()
            while (pos < input.length) {
                val c = input[pos++]
                if (c == '"') {
                    val s = out.toString(); var i = 0
                    while (i < s.length) {
                        if (s[i].isHighSurrogate()) { if (i + 1 >= s.length || !s[i + 1].isLowSurrogate()) bad(); i++ }
                        else if (s[i].isLowSurrogate()) bad()
                        i++
                    }
                    return s
                }
                if (c.code < 32) bad()
                if (c != '\\') out.append(c) else {
                    if (pos >= input.length) bad()
                    when (val e = input[pos++]) {
                        '"', '\\', '/' -> out.append(e)
                        'b' -> out.append('\b'); 'f' -> out.append('\u000c')
                        'n' -> out.append('\n'); 'r' -> out.append('\r'); 't' -> out.append('\t')
                        'u' -> {
                            if (pos + 4 > input.length) bad()
                            val hex = input.substring(pos, pos + 4)
                            if (!hex.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }) bad()
                            out.append(hex.toInt(16).toChar()); pos += 4
                        }
                        else -> bad()
                    }
                }
            }
            bad()
        }
        fun number(): Number {
            val start = pos
            if (pos < input.length && input[pos] == '-') pos++
            if (pos >= input.length) bad()
            if (input[pos] == '0') pos++ else {
                if (input[pos] !in '1'..'9') bad()
                while (pos < input.length && input[pos] in '0'..'9') pos++
            }
            if (pos < input.length && input[pos] == '.') { pos++; digits() }
            if (pos < input.length && input[pos] in "eE") {
                pos++; if (pos < input.length && input[pos] in "+-") pos++; digits()
            }
            val token = input.substring(start, pos)
            return token.toLongOrNull() ?: (token.toDoubleOrNull()?.takeIf { it.isFinite() } ?: bad())
        }
        fun digits() {
            val start = pos
            while (pos < input.length && input[pos] in '0'..'9') pos++
            if (pos == start) bad()
        }
    }
}

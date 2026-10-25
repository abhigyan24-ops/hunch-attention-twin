package com.dhyan.app.personas.export

import com.dhyan.app.personas.json.JsonReader
import com.dhyan.app.personas.world.*
import java.io.File

/**
 * 4A-5: Read events.jsonl back into GenEvent-like envelopes for validation.
 * Strict: rejects partial lines (no trailing newline on last line is an error).
 * Returns a list of raw maps (one per event) for checker use.
 */
object EventJsonlReader {
    /**
     * Parse all lines. Throws [IllegalArgumentException] if any line is blank (partial),
     * malformed JSON, or missing required envelope fields.
     */
    fun readMaps(file: File): List<Map<*, *>> {
        val text = file.readText(Charsets.UTF_8)
        // Must end with exactly one LF
        require(text.endsWith("\n")) { "JSONL file does not end with newline: ${file.name}" }
        val lines = text.trimEnd('\n').split('\n')
        return lines.mapIndexed { idx, line ->
            require(line.isNotBlank()) { "${file.name}:${idx + 1}: blank or partial line" }
            val parsed = try { JsonReader.parse(line) } catch (e: IllegalArgumentException) {
                throw IllegalArgumentException("${file.name}:${idx + 1}: JSON parse error: ${e.message}")
            }
            val m = parsed as? Map<*, *>
                ?: throw IllegalArgumentException("${file.name}:${idx + 1}: not a JSON object")
            for (key in listOf("schemaVersion", "id", "device", "seq", "source", "type", "ts", "synthetic", "payload")) {
                require(m.containsKey(key)) { "${file.name}:${idx + 1}: missing field '$key'" }
            }
            m
        }
    }
}

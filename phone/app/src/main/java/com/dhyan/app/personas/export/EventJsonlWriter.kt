package com.dhyan.app.personas.export

import com.dhyan.app.personas.world.GenEvent
import java.io.File
import java.io.Writer

/**
 * 4A-4: JSONL writer for GenEvent lists.
 * One JSON object per line, LF line endings, no trailing partial lines.
 * Key order is deterministic (GenEvent.envelope() uses linkedMapOf).
 */
object EventJsonlWriter {
    fun write(events: List<GenEvent>, writer: Writer) {
        val sb = StringBuilder()
        for (event in events) {
            sb.clear()
            sb.append(DeterministicJson.write(event.envelope()))
            sb.append('\n')
            writer.write(sb.toString())
        }
        writer.flush()
    }

    fun writeToFile(events: List<GenEvent>, file: File) {
        file.parentFile?.mkdirs()
        file.writer(Charsets.UTF_8).use { write(events, it) }
    }
}

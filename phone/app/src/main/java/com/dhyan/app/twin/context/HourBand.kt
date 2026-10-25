package com.dhyan.app.twin.context

/**
 * Hour-of-day band classifications (spec 8.3).
 *
 * Bands are half-open intervals [low, high) on the local hour-of-day (0..23).
 * Wire strings match context-snapshot.schema.json.
 */
enum class HourBand(val wire: String, val lowInclusive: Int, val highExclusive: Int) {
    NIGHT    ("night",     0,  6),
    MORNING  ("morning",   6, 12),
    AFTERNOON("afternoon", 12, 17),
    EVENING  ("evening",   17, 22),
    LATE     ("late",      22, 24);

    companion object {
        /** Returns the HourBand for a given local hour (0..23). */
        fun from(localHour: Int): HourBand {
            val h = localHour % 24
            return entries.first { h >= it.lowInclusive && h < it.highExclusive }
        }

        fun fromWire(wire: String): HourBand =
            entries.first { it.wire == wire }
    }
}

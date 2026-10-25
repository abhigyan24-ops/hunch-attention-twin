package com.dhyan.app.twin.decision

import java.time.Instant
import java.time.ZoneId

object DigestSchedule {
    /** Strictly next local-midnight grid boundary; both offsets are considered at DST overlaps. */
    fun nextDigestTsMs(nowMs: Long, intervalMin: Int = PolicyDefaults.DIGEST_INTERVAL_MIN,
        zone: ZoneId = ZoneId.systemDefault()): Long {
        require(intervalMin in 1..1440) { "invalid_interval" }
        val now = Instant.ofEpochMilli(nowMs)
        val today = now.atZone(zone).toLocalDate()
        var best: Instant? = null
        // Include tomorrow: at interval1440 the next boundary is the next local midnight.
        for (day in 0L..2L) {
            val date = today.plusDays(day)
            var minute = 0
            while (minute < 1440) {
                val local = date.atStartOfDay().plusMinutes(minute.toLong())
                val offsets = zone.rules.getValidOffsets(local)
                val candidates = if (offsets.isEmpty()) listOf(local.atZone(zone).toInstant()) else offsets.map { local.toInstant(it) }
                for (candidate in candidates) if (candidate > now && (best == null || candidate < best)) best=candidate
                minute += intervalMin
            }
            if (best != null) return best.toEpochMilli()
        }
        error("no_digest_boundary")
    }
}

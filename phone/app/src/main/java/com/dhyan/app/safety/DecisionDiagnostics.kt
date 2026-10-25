package com.dhyan.app.safety

import java.util.Collections
import java.util.concurrent.atomic.AtomicLongArray

/** Fixed buckets prevent raw categories, hashes or exception messages entering diagnostics. */
enum class DiagnosticCounter(val token: String) {
    EXCEPTION("exception"), INVALID_INPUT("invalid_input"), NOT_READY("not_ready"), UNKNOWN_NOTIF_CAT("unknown_notif_cat"),
    RECHECK_MODE("recheck_abort_mode_not_live"), RECHECK_KILL("recheck_abort_kill_switch"),
    RECHECK_ALLOW("recheck_abort_allow"), RECHECK_FAILED_OPEN("recheck_abort_failed_open"),
    RECHECK_NEVER_HOLD("recheck_abort_never_hold"), RECHECK_IDENTITY("recheck_abort_identity_mismatch"),
    RECHECK_EXPIRED("recheck_abort_decision_expired"), RECHECK_CLOCK("recheck_abort_clock_backwards"),
    RECHECK_INVALID("recheck_abort_invalid_input"), RECHECK_EXCEPTION("recheck_abort_exception")
}
class DecisionDiagnostics {
    private val counts=AtomicLongArray(DiagnosticCounter.entries.size)
    fun increment(counter: DiagnosticCounter) { counts.incrementAndGet(counter.ordinal) }
    /** Thread-safe individual counts; not a transactional snapshot across buckets. */
    fun snapshot(): Map<String, Long> = Collections.unmodifiableMap(linkedMapOf<String,Long>().apply {
        for(counter in DiagnosticCounter.entries)put(counter.token,counts.get(counter.ordinal))
    })
}

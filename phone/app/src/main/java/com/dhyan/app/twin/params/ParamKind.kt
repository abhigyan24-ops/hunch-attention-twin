package com.dhyan.app.twin.params

/**
 * Kind of parameter stored in the persistence layer.
 * The [wire] string is used as the `kind` column in Room / JSONL.
 *
 * Column mapping:
 *   BETA:      a = decayed successes, b = decayed failures, n = decayed observation count; mean/m2 unused (0.0)
 *   LOGNORM:   a, b unused (0.0); n = count; mean = mean of ln(d); m2 = Welford M2
 *   DIRICHLET: a = decayed count for ONE destination (one row per destination; key ends with "|dest=<id>"); other fields unused
 *   GAMMA:     a = events, b = exposure hours, n = observation count; mean/m2 unused (0.0)
 *
 * Priors are NOT persisted. They come from [PriorTable] so that retuning a prior later
 * does not corrupt stored evidence.
 */
enum class ParamKind(val wire: String) {
    BETA("beta"),
    LOGNORM("lognorm"),
    DIRICHLET("dirichlet"),
    GAMMA("gamma");

    companion object {
        /** Parses a wire string, throwing [IllegalArgumentException] for unknown values. */
        fun fromWire(wire: String): ParamKind =
            entries.firstOrNull { it.wire == wire }
                ?: throw IllegalArgumentException("Unknown ParamKind wire value: '$wire'")
    }
}

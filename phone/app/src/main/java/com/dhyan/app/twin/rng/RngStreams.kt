package com.dhyan.app.twin.rng

/**
 * Tags used to derive independent RNG streams for a single simulation run.
 *
 * EPISTEMIC:      parameter uncertainty draws. Shared by both SHOW and SUPPRESS arms.
 * SELF:           baseline self-initiated behaviour. Shared by both arms (CRN coupling).
 * NOTIF:          notification-specific behaviour. Used by SHOW arm only.
 * SELF_UNCOUPLED: alternate baseline stream for CRN variance-reduction testing.
 *                 Produces an independent (uncoupled) baseline draw so that variance
 *                 comparison tests can verify CRN reduces variance versus uncoupled runs.
 *
 * Each (seed, runIndex, tag) triple uniquely determines a SeededRng instance so that
 * streams are independent and results are reproducible.
 */
enum class StreamTag(val id: Int) {
    EPISTEMIC(0),
    SELF(1),
    NOTIF(2),
    SELF_UNCOUPLED(3)
}

/**
 * Creates independent, reproducible RNG streams for a given seed and run index.
 *
 * The stream seed is derived by mixing (masterSeed, runIndex, tag.id) with splitmix64
 * so that each combination produces a completely independent stream.
 */
object RngStreams {

    /**
     * Returns a new [SeededRng] for the given [masterSeed], [runIndex], and [tag].
     * The derived seed is deterministic and unique for each (masterSeed, runIndex, tag) triple.
     */
    fun stream(masterSeed: Long, runIndex: Int, tag: StreamTag): SeededRng {
        val derived = deriveSeed(masterSeed, runIndex.toLong(), tag.id.toLong())
        return SeededRng(derived)
    }

    private fun deriveSeed(a: Long, b: Long, c: Long): Long {
        // Three rounds of splitmix64 mixing to scramble the combined inputs.
        var x = a xor (b * -0x3C6EF372FE94F82BL) xor (c * 0x6C62272E07BB0142L)
        x = (x xor (x ushr 30)) * -0x40C4001F3407E67AL
        x = (x xor (x ushr 27)) * -0x43BC0993AEBE166BL
        return x xor (x ushr 31)
    }
}

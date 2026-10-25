package com.dhyan.app.twin.rng

/**
 * A deterministic pseudo-random number generator based on the xoshiro256** algorithm.
 * Seeded via splitmix64. All randomness in the twin module must come from this class;
 * never use Math.random, kotlin.random.Random.Default, or System.nanoTime for sampling.
 *
 * Thread safety: NOT thread-safe. Each simulation run must use its own instance.
 */
class SeededRng(seed: Long) {

    private var s0: Long
    private var s1: Long
    private var s2: Long
    private var s3: Long

    init {
        // Initialise the four state words by running splitmix64 four times on the seed.
        var z = seed
        s0 = splitmix64Step(z).also { z = z + SPLITMIX64_C }
        s1 = splitmix64Step(z).also { z = z + SPLITMIX64_C }
        s2 = splitmix64Step(z).also { z = z + SPLITMIX64_C }
        s3 = splitmix64Step(z)
    }

    /** Returns the next raw 64-bit output from xoshiro256**. */
    fun nextLong(): Long {
        val result = java.lang.Long.rotateLeft(s1 * 5, 7) * 9
        val t = s1 shl 17
        s2 = s2 xor s0
        s3 = s3 xor s1
        s1 = s1 xor s2
        s0 = s0 xor s3
        s2 = s2 xor t
        s3 = java.lang.Long.rotateLeft(s3, 45)
        return result
    }

    /**
     * Returns a Double in [0, 1) using the top 53 bits of the next raw output.
     * May return exactly 0.0.
     */
    fun nextDouble(): Double = (nextLong() ushr 11) * (1.0 / (1L shl 53))

    /**
     * Returns a Double in (0, 1) — never returns 0, safe for use in ln().
     * Replaces a 0 result with the smallest positive double.
     */
    fun nextOpenDouble(): Double {
        var d = nextDouble()
        if (d == 0.0) d = Double.MIN_VALUE
        return d
    }

    companion object {
        private const val SPLITMIX64_C = -0x783C846EEEBDAC5EL  // 0x9e3779b97f4a7c15L

        /** One step of splitmix64 mixing, not a full PRNG — used only during initialisation. */
        private fun splitmix64Step(z: Long): Long {
            var x = z + SPLITMIX64_C
            x = (x xor (x ushr 30)) * -0x40C4001F3407E67AL  // 0xbf58476d1ce4e5b9L
            x = (x xor (x ushr 27)) * -0x43BC0993AEBE166BL  // 0x94d049bb133111ebL
            return x xor (x ushr 31)
        }
    }
}

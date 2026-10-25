package com.dhyan.app.twin.rng

import kotlin.math.*

/**
 * Pure sampling functions. Every function takes a [SeededRng] and advances its state.
 * No function uses any external source of randomness.
 *
 * Units: all duration results are in minutes (callers must label accordingly).
 */
object Samplers {

    // ── Normal (Box-Muller) ────────────────────────────────────────────────────

    /**
     * Samples one standard normal N(0,1) using Box-Muller.
     * Consumes exactly 2 uniform draws; discards one Box-Muller output to keep
     * stream alignment predictable. Call this consistently (always take the cos branch).
     */
    fun nextNormal(rng: SeededRng): Double {
        val u1 = rng.nextOpenDouble()
        val u2 = rng.nextDouble()
        return sqrt(-2.0 * ln(u1)) * cos(2.0 * PI * u2)
    }

    // ── Exponential ────────────────────────────────────────────────────────────

    /**
     * Samples from Exponential(rate). Returns ∞ when rate == 0.0.
     * Duration unit: determined by caller (rate must match the window unit).
     */
    fun nextExponential(rng: SeededRng, ratePerMin: Double): Double {
        if (ratePerMin == 0.0) return Double.POSITIVE_INFINITY
        return -ln(rng.nextOpenDouble()) / ratePerMin
    }

    // ── Gamma ─────────────────────────────────────────────────────────────────

    /**
     * Samples from Gamma(shape, rate=1) using the Marsaglia-Tsang method.
     * For shape < 1, uses the boost: Gamma(shape+1) * U^(1/shape).
     * Shape must be > 0. Rate is applied by the caller via division.
     *
     * This is the standard Marsaglia-Tsang 2000 method plus the Ahrens-Dieter
     * boost for shape < 1.
     */
    fun nextGammaShape1(rng: SeededRng, shape: Double): Double {
        require(shape > 0.0) { "Gamma shape must be positive, got $shape" }
        return if (shape < 1.0) {
            // Boost: Gamma(shape) = Gamma(shape+1) * U^(1/shape)
            val g = nextGammaShape1(rng, shape + 1.0)
            g * rng.nextOpenDouble().pow(1.0 / shape)
        } else {
            marsagliaTsang(rng, shape)
        }
    }

    /**
     * Samples from Gamma(shape, rate): result = nextGammaShape1(rng, shape) / rate.
     * Rate must be > 0.
     */
    fun nextGamma(rng: SeededRng, shape: Double, rate: Double): Double {
        require(rate > 0.0) { "Gamma rate must be positive, got $rate" }
        return nextGammaShape1(rng, shape) / rate
    }

    private fun marsagliaTsang(rng: SeededRng, shape: Double): Double {
        val d = shape - 1.0 / 3.0
        val c = 1.0 / sqrt(9.0 * d)
        while (true) {
            var x: Double
            var v: Double
            do {
                x = nextNormal(rng)
                v = 1.0 + c * x
            } while (v <= 0.0)
            v = v * v * v
            val u = rng.nextOpenDouble()
            val xSq = x * x
            if (u < 1.0 - 0.0331 * xSq * xSq) return d * v
            if (ln(u) < 0.5 * xSq + d * (1.0 - v + ln(v))) return d * v
        }
    }

    // ── Beta ──────────────────────────────────────────────────────────────────

    /**
     * Samples from Beta(alpha, beta) via two gamma samples.
     * Both alpha and beta must be > 0.
     */
    fun nextBeta(rng: SeededRng, alpha: Double, beta: Double): Double {
        require(alpha > 0.0 && beta > 0.0) { "Beta parameters must be positive: α=$alpha, β=$beta" }
        val g1 = nextGammaShape1(rng, alpha)
        val g2 = nextGammaShape1(rng, beta)
        val sum = g1 + g2
        // Guard against both being 0 (extremely unlikely with positive params)
        return if (sum == 0.0) alpha / (alpha + beta) else g1 / sum
    }

    // ── Dirichlet ─────────────────────────────────────────────────────────────

    /**
     * Samples from Dirichlet(alphas). Result is a probability vector that sums to 1.
     * All alphas must be > 0.
     */
    fun nextDirichlet(rng: SeededRng, alphas: DoubleArray): DoubleArray {
        require(alphas.isNotEmpty()) { "Dirichlet alphas must be non-empty" }
        val gammas = DoubleArray(alphas.size) { nextGammaShape1(rng, alphas[it]) }
        val sum = gammas.sum()
        return if (sum == 0.0) DoubleArray(alphas.size) { 1.0 / alphas.size }
        else DoubleArray(alphas.size) { gammas[it] / sum }
    }

    // ── Categorical ───────────────────────────────────────────────────────────

    /**
     * Samples a category index using the precomputed probability vector [probs] and
     * a uniform [u] draw. Uses inverse CDF (linear scan), O(k).
     * [probs] need not sum exactly to 1; the last bucket catches the remainder.
     */
    fun categoricalIndex(probs: DoubleArray, u: Double): Int {
        var cumulative = 0.0
        for (i in 0 until probs.size - 1) {
            cumulative += probs[i]
            if (u < cumulative) return i
        }
        return probs.size - 1
    }

    // ── Lognormal ─────────────────────────────────────────────────────────────

    /**
     * Samples from LogNormal(meanLog, sigmaLog) where the result is in minutes.
     * sigmaLog is floored at [sigmaFloor] before sampling.
     */
    fun nextLogNormal(rng: SeededRng, meanLog: Double, sigmaLog: Double, sigmaFloor: Double = 0.25): Double {
        val s = maxOf(sigmaLog, sigmaFloor)
        val z = nextNormal(rng)
        return exp(meanLog + s * z)
    }
}

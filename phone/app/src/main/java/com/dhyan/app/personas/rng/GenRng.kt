package com.dhyan.app.personas.rng

import kotlin.math.*

/** SplitMix64. Each uniform consumes one word; normal consumes two uniforms per pair. */
class GenRng(private val seed: Long) {
    private var state = seed
    private var cachedNormal: Double? = null
    private fun word(): Long {
        state += -7046029254386353131L
        return mix(state)
    }
    fun nextDouble(): Double = (word().ushr(11)).toDouble() / 9007199254740992.0
    fun nextNormal(): Double {
        cachedNormal?.let { cachedNormal = null; return it }
        val radius = sqrt(-2.0 * ln(1.0 - nextDouble()))
        val angle = 2.0 * PI * nextDouble()
        cachedNormal = radius * sin(angle)
        return radius * cos(angle)
    }
    fun nextExp(rate: Double): Double {
        require(rate.isFinite() && rate >= 0)
        val u = nextDouble()
        return if (rate == 0.0) Double.POSITIVE_INFINITY else -ln1p(-u) / rate
    }
    /** Sequential exponential arrivals, including the final arrival past the unit interval. */
    fun nextPoisson(lambda: Double): Int {
        require(lambda.isFinite() && lambda in 0.0..1000000.0)
        if (lambda == 0.0) return 0
        var time = 0.0; var count = 0
        while (true) { time += nextExp(lambda); if (time >= 1.0) return count; count++ }
    }
    fun fork(tag: Long, index: Long): GenRng = GenRng(mix(mix(seed xor tag) + mix(index)))
    fun logNormal(mu: Double, sigma: Double): Double {
        require(mu.isFinite() && sigma.isFinite() && sigma > 0)
        return exp(mu + sigma * nextNormal())
    }
    fun bernoulli(p: Double): Boolean { require(p.isFinite() && p in 0.0..1.0); return nextDouble() < p }
    fun categorical(weights: List<Double>): Int {
        require(weights.isNotEmpty() && weights.all { it.isFinite() && it >= 0 })
        val total = weights.sum(); require(total.isFinite() && total > 0)
        val draw = nextDouble() * total
        var cumulative = 0.0
        for (i in weights.indices) { cumulative += weights[i]; if (draw < cumulative) return i }
        return weights.indexOfLast { it > 0 }
    }
    companion object {
        private fun mix(input: Long): Long {
            var z = input
            z = (z xor z.ushr(30)) * -4658895280553007687L
            z = (z xor z.ushr(27)) * -7723592293110705685L
            return z xor z.ushr(31)
        }
    }
}

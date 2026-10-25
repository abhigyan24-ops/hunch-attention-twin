package com.dhyan.app.twin.rng

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.*

class SamplersTest {

    private val testSeeds = longArrayOf(101L, 202L, 303L, 404L, 505L)

    @Test
    fun normalEmpiricalMeanAndVarianceMatchAnalytic() {
        val n = 20000
        for (seed in testSeeds) {
            val rng = SeededRng(seed)
            var sum = 0.0
            var sumSq = 0.0
            for (i in 0 until n) {
                val z = Samplers.nextNormal(rng)
                sum += z
                sumSq += z * z
            }
            val mean = sum / n
            val variance = (sumSq - n * mean * mean) / (n - 1)

            // 4 standard errors for N(0,1): SE_mean = 1/sqrt(N), SE_var = sqrt(2/(N-1))
            val seMean = 1.0 / sqrt(n.toDouble())
            val seVar = sqrt(2.0 / (n - 1.0))

            assertEquals("Normal mean off for seed $seed", 0.0, mean, 4.0 * seMean)
            assertEquals("Normal variance off for seed $seed", 1.0, variance, 4.0 * seVar)
        }
    }

    @Test
    fun gammaShapeLessThanOneMatchesAnalytic() {
        // Test shape < 1 (uses Ahrens-Dieter boost)
        val shape = 0.3
        val rate = 2.0
        val expectedMean = shape / rate
        val expectedVar = shape / (rate * rate)
        val n = 25000

        for (seed in testSeeds) {
            val rng = SeededRng(seed)
            var sum = 0.0
            var sumSq = 0.0
            for (i in 0 until n) {
                val g = Samplers.nextGamma(rng, shape, rate)
                sum += g
                sumSq += g * g
            }
            val mean = sum / n
            val variance = (sumSq - n * mean * mean) / (n - 1)
            val seMean = sqrt(expectedVar / n)

            assertEquals("Gamma(0.3) mean off for seed $seed", expectedMean, mean, 4.0 * seMean)
            assertEquals("Gamma(0.3) variance off for seed $seed", expectedVar, variance, expectedVar * 0.25)
        }
    }

    @Test
    fun gammaShapeOneAndGreaterThanOneMatchAnalytic() {
        val shapes = doubleArrayOf(1.0, 4.0)
        val rate = 1.5
        val n = 20000

        for (shape in shapes) {
            val expectedMean = shape / rate
            val expectedVar = shape / (rate * rate)
            val seMean = sqrt(expectedVar / n)

            for (seed in testSeeds) {
                val rng = SeededRng(seed)
                var sum = 0.0
                var sumSq = 0.0
                for (i in 0 until n) {
                    val g = Samplers.nextGamma(rng, shape, rate)
                    sum += g
                    sumSq += g * g
                }
                val mean = sum / n
                val variance = (sumSq - n * mean * mean) / (n - 1)

                assertEquals("Gamma($shape) mean off for seed $seed", expectedMean, mean, 4.0 * seMean)
                assertEquals("Gamma($shape) variance off for seed $seed", expectedVar, variance, expectedVar * 0.20)
            }
        }
    }

    @Test
    fun betaDistributionMatchesAnalytic() {
        val alpha = 3.0
        val beta = 4.0
        val expectedMean = alpha / (alpha + beta)
        val expectedVar = (alpha * beta) / ((alpha + beta).pow(2) * (alpha + beta + 1.0))
        val n = 20000
        val seMean = sqrt(expectedVar / n)

        for (seed in testSeeds) {
            val rng = SeededRng(seed)
            var sum = 0.0
            var sumSq = 0.0
            for (i in 0 until n) {
                val b = Samplers.nextBeta(rng, alpha, beta)
                assertTrue("Beta sample out of (0,1)", b in 0.0..1.0)
                sum += b
                sumSq += b * b
            }
            val mean = sum / n
            val variance = (sumSq - n * mean * mean) / (n - 1)

            assertEquals("Beta(3,4) mean off for seed $seed", expectedMean, mean, 4.0 * seMean)
            assertEquals("Beta(3,4) variance off for seed $seed", expectedVar, variance, expectedVar * 0.15)
        }
    }

    @Test
    fun dirichletDistributionSumsToOneAndMatchesMeans() {
        val alphas = doubleArrayOf(2.0, 5.0, 3.0)
        val sumAlphas = alphas.sum()
        val n = 15000

        val rng = SeededRng(777L)
        val sums = DoubleArray(3)

        for (i in 0 until n) {
            val sample = Samplers.nextDirichlet(rng, alphas)
            assertEquals(3, sample.size)
            assertEquals(1.0, sample.sum(), 1e-10)
            for (k in 0 until 3) {
                sums[k] += sample[k]
            }
        }

        for (k in 0 until 3) {
            val expected = alphas[k] / sumAlphas
            val actual = sums[k] / n
            assertEquals("Dirichlet mean for component $k off", expected, actual, 0.03)
        }
    }

    @Test
    fun categoricalFrequenciesMatchProbabilities() {
        val probs = doubleArrayOf(0.1, 0.4, 0.5)
        val counts = IntArray(3)
        val n = 10000
        val rng = SeededRng(888L)

        for (i in 0 until n) {
            val idx = Samplers.categoricalIndex(probs, rng.nextDouble())
            counts[idx]++
        }

        assertEquals(0.10, counts[0].toDouble() / n, 0.02)
        assertEquals(0.40, counts[1].toDouble() / n, 0.03)
        assertEquals(0.50, counts[2].toDouble() / n, 0.03)
    }
}

package com.dhyan.app.ml

import kotlin.math.exp

/**
 * Plain-English distraction forecast produced by survival analysis rollout.
 */
data class MlDistractionForecast(
    val predictedMinutesRemaining: Int,
    val primaryDriverLabel: String,
    val humanHeadline: String,
    val isCalibrated: Boolean
)

/**
 * Pure Kotlin On-Device Online Logistic Hazard Survival Model.
 * Evaluates discrete 5-minute hazard rates and rolls out an 18-step survival curve.
 * Trains on-device via online AdaGrad backpropagation with zero native ML dependencies.
 */
class OnDeviceDistractionMLModel(
    var weights: DoubleArray = DEFAULT_PRIOR_WEIGHTS.clone(),
    var gradSquares: DoubleArray = DoubleArray(AttentionFeatureExtractor.NUM_FEATURES) { 1e-4 },
    var trainedSamplesCount: Int = 0,
    var rollingBrierLoss: Double = 0.10
) {

    companion object {
        val DEFAULT_PRIOR_WEIGHTS = doubleArrayOf(
            -2.2, // x[0]: Bias (baseline ~10% 5-min hazard)
             0.1, // x[1]: Hour sine
            -0.1, // x[2]: Hour cosine
             0.8, // x[3]: Session elapsed
             0.9, // x[4]: Sleep debt
             0.7, // x[5]: Unlocks velocity
             0.6, // x[6]: App switches
             0.5, // x[7]: Notification pressure
             1.0, // x[8]: Cognitive thrash
             0.7, // x[9]: Cumulative work fatigue
             0.8, // x[10]: High-dopamine feed exposure
             0.6  // x[11]: Fatigue x Sleep interaction
        )

        const val CALIBRATION_SAMPLE_THRESHOLD = 6
        private const val EPSILON = 1e-8
        private const val SIGMOID_CLAMP = 1e-6

        fun fromJsonString(jsonStr: String): OnDeviceDistractionMLModel {
            val weightsRegex = """"weights"\s*:\s*\[([^\]]*)\]""".toRegex()
            val gradRegex = """"gradSquares"\s*:\s*\[([^\]]*)\]""".toRegex()
            val countRegex = """"trainedSamplesCount"\s*:\s*(\d+)""".toRegex()
            val lossRegex = """"rollingBrierLoss"\s*:\s*([0-9.eE+-]+)""".toRegex()

            val wMatch = weightsRegex.find(jsonStr)?.groupValues?.get(1)
            val gMatch = gradRegex.find(jsonStr)?.groupValues?.get(1)
            val cMatch = countRegex.find(jsonStr)?.groupValues?.get(1)
            val lMatch = lossRegex.find(jsonStr)?.groupValues?.get(1)

            val w = if (!wMatch.isNullOrBlank()) {
                val parsed = wMatch.split(",").mapNotNull { it.trim().toDoubleOrNull() }.toDoubleArray()
                if (parsed.size == AttentionFeatureExtractor.NUM_FEATURES) parsed else DEFAULT_PRIOR_WEIGHTS.clone()
            } else DEFAULT_PRIOR_WEIGHTS.clone()

            val g = if (!gMatch.isNullOrBlank()) {
                val parsed = gMatch.split(",").mapNotNull { it.trim().toDoubleOrNull() }.toDoubleArray()
                if (parsed.size == AttentionFeatureExtractor.NUM_FEATURES) parsed else DoubleArray(AttentionFeatureExtractor.NUM_FEATURES) { 1e-4 }
            } else DoubleArray(AttentionFeatureExtractor.NUM_FEATURES) { 1e-4 }

            val count = cMatch?.toIntOrNull() ?: 0
            val loss = lMatch?.toDoubleOrNull() ?: 0.10
            return OnDeviceDistractionMLModel(w, g, count, loss)
        }
    }

    /**
     * Computes numerically stable instantaneous 5-minute hazard probability: h_t = sigmoid(w^T x).
     */
    fun predictHazardProbability(features: DoubleArray): Double {
        var dot = 0.0
        val n = minOf(weights.size, features.size)
        for (i in 0 until n) {
            dot += weights[i] * features[i]
        }

        val prob = if (dot >= 0.0) {
            1.0 / (1.0 + exp(-dot))
        } else {
            val ez = exp(dot)
            ez / (1.0 + ez)
        }

        return prob.coerceIn(SIGMOID_CLAMP, 1.0 - SIGMOID_CLAMP)
    }

    /**
     * Rolls out an 18-step forward discrete survival curve S(k) = prod(1 - h_j).
     * Identifies the median survival step where S(k) <= 0.50 to predict minutes until distraction.
     */
    fun predictMinutesUntilDistraction(ctx: RawAttentionContext): MlDistractionForecast {
        var survival = 1.0
        var predictedStep = 18 // Default 90m

        for (k in 1..18) {
            val forwardMinutes = 5.0 * k
            val forwardHour = (ctx.hourOfDay % 24) + ((ctx.minuteOfHour + forwardMinutes) / 60.0)
            val forwardHourNorm = (forwardHour.toInt() % 24)
            val forwardMinuteNorm = ((ctx.minuteOfHour + forwardMinutes) % 60.0).toInt()

            val stepCtx = ctx.copy(
                hourOfDay = forwardHourNorm,
                minuteOfHour = forwardMinuteNorm,
                sessionElapsedMins = ctx.sessionElapsedMins + forwardMinutes,
                minsSinceLastBreak = ctx.minsSinceLastBreak + forwardMinutes
            )

            val stepFeatures = AttentionFeatureExtractor.extractFeatures(stepCtx)
            val stepHazard = predictHazardProbability(stepFeatures)
            survival *= (1.0 - stepHazard)

            if (survival <= 0.50) {
                predictedStep = k
                break
            }
        }

        val predictedMinutes = predictedStep * 5

        // Determine primary risk driver from current context
        val currentFeatures = AttentionFeatureExtractor.extractFeatures(ctx)
        var maxContribution = -Double.MAX_VALUE
        var maxIdx = -1
        for (i in 3 until AttentionFeatureExtractor.NUM_FEATURES) {
            val contribution = weights[i] * currentFeatures[i]
            if (contribution > maxContribution && currentFeatures[i] > 0.1) {
                maxContribution = contribution
                maxIdx = i
            }
        }

        val primaryDriver = when (maxIdx) {
            3 -> "Long session duration"
            4 -> "Circadian sleep debt"
            5 -> "Frequent phone pickups"
            6 -> "Rapid app / window switching"
            7 -> "Notification pressure"
            8 -> "Cognitive thrash / build churn"
            9 -> "Extended time without a break"
            10 -> "Recent social feed exposure"
            11 -> "Sleep debt compounding with session length"
            else -> "Natural attention cycle"
        }

        val isCalibrated = trainedSamplesCount >= CALIBRATION_SAMPLE_THRESHOLD

        val headline = when {
            predictedMinutes <= 20 -> "Likely distraction around minute $predictedMinutes — $primaryDriver"
            predictedMinutes <= 45 -> "Focus holding well for ~$predictedMinutes mins before natural dip"
            else -> "Strong flow forecast — high focus stability for ~$predictedMinutes mins"
        }

        return MlDistractionForecast(
            predictedMinutesRemaining = predictedMinutes,
            primaryDriverLabel = primaryDriver,
            humanHeadline = headline,
            isCalibrated = isCalibrated
        )
    }

    /**
     * Executes one online AdaGrad parameter update step given true focus outcome.
     * @param actualDistracted true if user was distracted within the 5m interval, false if focus held.
     */
    @Synchronized
    fun trainOnlineStep(
        features: DoubleArray,
        actualDistracted: Boolean,
        learningRate: Double = 0.15,
        l2Lambda: Double = 0.001
    ) {
        val y = if (actualDistracted) 1.0 else 0.0
        val pred = predictHazardProbability(features)
        val error = pred - y

        // Update rolling Brier Loss EWMA: L <- 0.95 * L + 0.05 * e^2
        val brierSample = error * error
        rollingBrierLoss = 0.95 * rollingBrierLoss + 0.05 * brierSample

        val n = minOf(weights.size, features.size)
        for (i in 0 until n) {
            val grad = error * features[i] + l2Lambda * weights[i]
            gradSquares[i] += grad * grad
            val step = (learningRate / kotlin.math.sqrt(gradSquares[i] + EPSILON)) * grad
            weights[i] -= step
        }

        trainedSamplesCount++
    }

    /**
     * Performs sample-weighted Federated Averaging with remote weights from the engine sidecar.
     */
    @Synchronized
    fun mergeFederatedWeights(
        remoteWeights: DoubleArray,
        remoteGradSquares: DoubleArray? = null,
        remoteSampleCount: Int = 1
    ) {
        val totalSamples = (trainedSamplesCount + remoteSampleCount).coerceAtLeast(1)
        val localWeight = trainedSamplesCount.toDouble() / totalSamples
        val remoteWeight = remoteSampleCount.toDouble() / totalSamples

        val n = minOf(weights.size, remoteWeights.size)
        for (i in 0 until n) {
            weights[i] = weights[i] * localWeight + remoteWeights[i] * remoteWeight
            if (remoteGradSquares != null && i < remoteGradSquares.size) {
                gradSquares[i] = gradSquares[i] * localWeight + remoteGradSquares[i] * remoteWeight
            }
        }

        trainedSamplesCount = totalSamples
    }

    fun toJsonString(): String {
        val wStr = weights.joinToString(",", prefix = "[", postfix = "]")
        val gStr = gradSquares.joinToString(",", prefix = "[", postfix = "]")
        return """{"weights":$wStr,"gradSquares":$gStr,"trainedSamplesCount":$trainedSamplesCount,"rollingBrierLoss":$rollingBrierLoss}"""
    }
}

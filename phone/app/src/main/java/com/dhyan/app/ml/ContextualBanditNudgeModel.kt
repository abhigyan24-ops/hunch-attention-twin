package com.dhyan.app.ml

import kotlin.math.sqrt

/**
 * 4 discrete action arms available to the contextual bandit.
 */
enum class NudgeArm(val code: String, val humanLabel: String) {
    STAY_SILENT("stay_silent", "Stay Silent"),
    AUTO_QUIET_SHIELD("auto_quiet_shield", "Activate Quiet Shield"),
    MICRO_BREAK_PROMPT("micro_break_prompt", "Suggest 3-Minute Walk-Away"),
    PARK_CONTEXT_PROMPT("park_context_prompt", "Suggest Intention Parking");

    companion object {
        fun fromCode(code: String): NudgeArm {
            return entries.firstOrNull { it.code.equals(code, ignoreCase = true) } ?: STAY_SILENT
        }
    }
}

/**
 * 4-Arm Diagonal LinUCB Contextual Bandit.
 * Selects optimal, personalized flow-protection actions using contextual Upper Confidence Bound.
 */
class ContextualBanditNudgeModel {

    companion object {
        const val NUM_ARMS = 4
        const val NUM_FEATURES = AttentionFeatureExtractor.NUM_FEATURES

        private fun extract2DArray(jsonStr: String, key: String): List<DoubleArray> {
            val keyIdx = jsonStr.indexOf("\"$key\"")
            if (keyIdx == -1) return emptyList()
            val colonIdx = jsonStr.indexOf(':', keyIdx)
            if (colonIdx == -1) return emptyList()
            val firstOpenBracket = jsonStr.indexOf('[', colonIdx)
            if (firstOpenBracket == -1) return emptyList()

            var depth = 0
            var endIdx = -1
            for (i in firstOpenBracket until jsonStr.length) {
                if (jsonStr[i] == '[') depth++
                else if (jsonStr[i] == ']') {
                    depth--
                    if (depth == 0) {
                        endIdx = i
                        break
                    }
                }
            }
            if (endIdx == -1) return emptyList()

            val inner = jsonStr.substring(firstOpenBracket + 1, endIdx)
            val rowRegex = """\[([^\]]*)\]""".toRegex()
            return rowRegex.findAll(inner).map { m ->
                m.groupValues[1].split(",")
                    .mapNotNull { it.trim().toDoubleOrNull() }
                    .toDoubleArray()
            }.toList()
        }

        fun fromJsonString(jsonStr: String): ContextualBanditNudgeModel {
            val model = ContextualBanditNudgeModel()
            val parsedThetas = extract2DArray(jsonStr, "thetas")
            val parsedCounts = extract2DArray(jsonStr, "counts")

            for (a in 0 until minOf(NUM_ARMS, parsedThetas.size)) {
                for (i in 0 until minOf(NUM_FEATURES, parsedThetas[a].size)) {
                    model.thetas[a][i] = parsedThetas[a][i]
                }
            }

            for (a in 0 until minOf(NUM_ARMS, parsedCounts.size)) {
                for (i in 0 until minOf(NUM_FEATURES, parsedCounts[a].size)) {
                    model.counts[a][i] = parsedCounts[a][i].coerceAtLeast(1.0)
                }
            }

            return model
        }
    }

    // thetas[arm][feature] - Arm preference vectors
    val thetas: Array<DoubleArray> = Array(NUM_ARMS) { a ->
        DoubleArray(NUM_FEATURES) { i ->
            // Prior bias term for sensible baseline defaults
            if (i == 0) {
                when (a) {
                    0 -> 0.5 // STAY_SILENT initial preference
                    1 -> 0.3 // AUTO_QUIET_SHIELD
                    2 -> 0.2 // MICRO_BREAK_PROMPT
                    3 -> 0.2 // PARK_CONTEXT_PROMPT
                    else -> 0.0
                }
            } else 0.0
        }
    }

    // counts[arm][feature] - Diagonal feature counts for exploration bonus
    val counts: Array<DoubleArray> = Array(NUM_ARMS) { DoubleArray(NUM_FEATURES) { 1.0 } }

    /**
     * Selects the arm with the highest Upper Confidence Bound score:
     * Score(a | x) = theta_a^T x + alpha * sqrt(sum_i (x_i^2 / c_{a,i}))
     */
    fun selectBestArm(features: DoubleArray, explorationAlpha: Double = 0.25): NudgeArm {
        var bestScore = -Double.MAX_VALUE
        var bestArmIdx = 0

        for (a in 0 until NUM_ARMS) {
            var dot = 0.0
            var varianceSum = 0.0
            for (i in 0 until NUM_FEATURES) {
                val xi = features[i]
                dot += thetas[a][i] * xi
                varianceSum += (xi * xi) / counts[a][i]
            }

            val ucbBonus = explorationAlpha * sqrt(varianceSum)
            val score = dot + ucbBonus

            if (score > bestScore) {
                bestScore = score
                bestArmIdx = a
            }
        }

        return NudgeArm.entries[bestArmIdx]
    }

    /**
     * Updates the contextual bandit weights based on observed feedback reward.
     * @param reward float in [-1.0, 1.0]. Positive (+1.0) for continued focus; negative (-0.5) for dismiss/interruption.
     */
    @Synchronized
    fun recordReward(arm: NudgeArm, features: DoubleArray, reward: Double) {
        val a = arm.ordinal
        val clampedReward = reward.coerceIn(-1.0, 1.0)

        var predictedReward = 0.0
        for (i in 0 until NUM_FEATURES) {
            predictedReward += thetas[a][i] * features[i]
        }
        val rewardError = clampedReward - predictedReward

        for (i in 0 until NUM_FEATURES) {
            val xi = features[i]
            counts[a][i] += xi * xi
            // Online parameter update with feature-normalized step
            val step = (0.10 * xi) / sqrt(counts[a][i])
            thetas[a][i] += step * rewardError
        }
    }

    /**
     * Merges remote bandit parameters with local parameters.
     */
    @Synchronized
    fun mergeFederatedBandit(remoteThetas: Array<DoubleArray>, remoteCounts: Array<DoubleArray>) {
        for (a in 0 until minOf(NUM_ARMS, remoteThetas.size)) {
            for (i in 0 until NUM_FEATURES) {
                val c1 = counts[a][i]
                val c2 = if (a < remoteCounts.size && i < remoteCounts[a].size) remoteCounts[a][i] else 1.0
                val totalC = c1 + c2
                thetas[a][i] = (thetas[a][i] * c1 + remoteThetas[a][i] * c2) / totalC
                counts[a][i] = totalC
            }
        }
    }

    fun toJsonString(): String {
        val thetaRows = thetas.joinToString(",") { row ->
            row.joinToString(",", prefix = "[", postfix = "]")
        }
        val countRows = counts.joinToString(",") { row ->
            row.joinToString(",", prefix = "[", postfix = "]")
        }
        return """{"thetas":[$thetaRows],"counts":[$countRows]}"""
    }
}

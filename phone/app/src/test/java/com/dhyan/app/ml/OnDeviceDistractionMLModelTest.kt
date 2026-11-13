package com.dhyan.app.ml

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OnDeviceDistractionMLModelTest {

    @Test
    fun `feature extractor clamps all 12 features to unit range and encodes cyclical time`() {
        val ctx = RawAttentionContext(
            hourOfDay = 14,
            minuteOfHour = 30,
            sessionElapsedMins = 120.0, // Should clamp to 1.0 (max 90m)
            lastNightSleepMins = 300.0, // 5 hours -> 180m debt / 240m = 0.75
            unlocksLast15m = 12, // Should clamp to 1.0 (max 8)
            appSwitchesLast15m = 30, // Should clamp to 1.0 (max 20)
            notificationsLast15m = 20, // Should clamp to 1.0 (max 15)
            isThrashing = true,
            minsSinceLastBreak = 200.0, // Should clamp to 1.0 (max 180m)
            socialScrollSecsLast30m = 700.0 // Should clamp to 1.0 (max 600s)
        )

        val features = AttentionFeatureExtractor.extractFeatures(ctx)
        assertEquals(12, features.size)
        assertEquals(1.0, features[0], 1e-6) // Bias

        for (i in 0 until 12) {
            assertTrue("Feature $i was ${features[i]} (out of range)", features[i] in 0.0..1.0)
        }

        // x[11] is interaction x[3] * x[4]
        assertEquals(features[3] * features[4], features[11], 1e-6)
    }

    @Test
    fun `training online steps on distraction increases hazard and decreases brier loss`() {
        val model = OnDeviceDistractionMLModel()
        val ctx = RawAttentionContext(sessionElapsedMins = 45.0, lastNightSleepMins = 360.0, unlocksLast15m = 4)
        val features = AttentionFeatureExtractor.extractFeatures(ctx)

        val initialHazard = model.predictHazardProbability(features)

        // Train 25 consecutive distraction outcomes
        for (i in 1..25) {
            model.trainOnlineStep(features, actualDistracted = true)
        }

        val trainedHazard = model.predictHazardProbability(features)
        assertTrue(
            "Expected trained hazard ($trainedHazard) > initial ($initialHazard)",
            trainedHazard > initialHazard
        )
        assertEquals(25, model.trainedSamplesCount)
        assertTrue(model.rollingBrierLoss < 0.20)
    }

    @Test
    fun `fatigued context with high sleep debt predicts fewer minutes until distraction than well-rested`() {
        val model = OnDeviceDistractionMLModel()

        val tiredCtx = RawAttentionContext(
            hourOfDay = 15,
            sessionElapsedMins = 50.0,
            lastNightSleepMins = 240.0, // 4 hours sleep
            unlocksLast15m = 6,
            appSwitchesLast15m = 12,
            minsSinceLastBreak = 90.0
        )

        val freshCtx = RawAttentionContext(
            hourOfDay = 10,
            sessionElapsedMins = 10.0,
            lastNightSleepMins = 480.0, // 8 hours sleep
            unlocksLast15m = 0,
            appSwitchesLast15m = 2,
            minsSinceLastBreak = 10.0
        )

        val tiredForecast = model.predictMinutesUntilDistraction(tiredCtx)
        val freshForecast = model.predictMinutesUntilDistraction(freshCtx)

        assertTrue(
            "Tired minutes (${tiredForecast.predictedMinutesRemaining}) should be <= Fresh minutes (${freshForecast.predictedMinutesRemaining})",
            tiredForecast.predictedMinutesRemaining < freshForecast.predictedMinutesRemaining
        )
    }

    @Test
    fun `contextual bandit converges to rewarded arm`() {
        val bandit = ContextualBanditNudgeModel()
        val features = AttentionFeatureExtractor.extractFeatures(
            RawAttentionContext(sessionElapsedMins = 60.0, isThrashing = true)
        )

        // Heavily reward MICRO_BREAK_PROMPT and penalize STAY_SILENT
        for (i in 1..30) {
            bandit.recordReward(NudgeArm.MICRO_BREAK_PROMPT, features, reward = 1.0)
            bandit.recordReward(NudgeArm.STAY_SILENT, features, reward = -0.5)
        }

        val bestArm = bandit.selectBestArm(features, explorationAlpha = 0.05)
        assertEquals(NudgeArm.MICRO_BREAK_PROMPT, bestArm)
    }

    @Test
    fun `federated averaging correctly weights local and remote models`() {
        val localModel = OnDeviceDistractionMLModel(trainedSamplesCount = 10)
        localModel.weights[0] = 1.0

        val remoteWeights = DoubleArray(12) { 3.0 }
        // Local: 10 samples with w[0]=1.0
        // Remote: 10 samples with w[0]=3.0
        // Expected merged: (10*1.0 + 10*3.0)/20 = 2.0
        localModel.mergeFederatedWeights(remoteWeights, remoteSampleCount = 10)

        assertEquals(20, localModel.trainedSamplesCount)
        assertEquals(2.0, localModel.weights[0], 1e-6)
    }

    @Test
    fun `json serialization round trips model parameters accurately`() {
        val model = OnDeviceDistractionMLModel(trainedSamplesCount = 12, rollingBrierLoss = 0.045)
        model.weights[3] = 1.45
        model.gradSquares[3] = 0.089

        val json = model.toJsonString()
        val restored = OnDeviceDistractionMLModel.fromJsonString(json)

        assertEquals(model.trainedSamplesCount, restored.trainedSamplesCount)
        assertEquals(model.rollingBrierLoss, restored.rollingBrierLoss, 1e-6)
        assertEquals(1.45, restored.weights[3], 1e-6)
        assertEquals(0.089, restored.gradSquares[3], 1e-6)
    }

    @Test
    fun `contextual bandit json serialization round trips accurate arm parameters`() {
        val bandit = ContextualBanditNudgeModel()
        bandit.thetas[1][4] = 0.85
        bandit.counts[1][4] = 14.5

        val json = bandit.toJsonString()
        val restored = ContextualBanditNudgeModel.fromJsonString(json)

        assertEquals(0.85, restored.thetas[1][4], 1e-6)
        assertEquals(14.5, restored.counts[1][4], 1e-6)
        // Check prior on arm 0 bias
        assertEquals(0.5, restored.thetas[0][0], 1e-6)
    }
}

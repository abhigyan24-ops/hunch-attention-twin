package com.dhyan.app.audio

import android.media.AudioDeviceInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EarbudsFlowTriggerTest {

    @Test
    fun `prompts flow when personal Bluetooth A2DP connects in clear conditions`() {
        val now = 10_000_000L
        val decision = EarbudsFlowEvaluator.shouldPromptFlowOnAudioConnect(
            deviceType = AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
            isSink = true,
            isFocusAlreadyActive = false,
            isInCall = false,
            isBedtimeNow = false,
            lastPromptedAtMillis = 0L,
            nowMillis = now
        )

        assertTrue(decision is EarbudsFlowDecision.PromptStartFlow)
        val msg = (decision as EarbudsFlowDecision.PromptStartFlow).humanMessage
        assertTrue(msg.contains("Earbuds connected"))
        assertTrue(msg.contains("Quiet Shield"))
    }

    @Test
    fun `prompts flow for wired headsets and USB headphones`() {
        val now = 10_000_000L
        val wiredDecision = EarbudsFlowEvaluator.shouldPromptFlowOnAudioConnect(
            deviceType = AudioDeviceInfo.TYPE_WIRED_HEADSET,
            isSink = true,
            isFocusAlreadyActive = false,
            isInCall = false,
            isBedtimeNow = false,
            lastPromptedAtMillis = 0L,
            nowMillis = now
        )
        assertTrue(wiredDecision is EarbudsFlowDecision.PromptStartFlow)

        val usbDecision = EarbudsFlowEvaluator.shouldPromptFlowOnAudioConnect(
            deviceType = AudioDeviceInfo.TYPE_USB_HEADSET,
            isSink = true,
            isFocusAlreadyActive = false,
            isInCall = false,
            isBedtimeNow = false,
            lastPromptedAtMillis = 0L,
            nowMillis = now
        )
        assertTrue(usbDecision is EarbudsFlowDecision.PromptStartFlow)
    }

    @Test
    fun `suppresses prompt if focus shield is already running`() {
        val decision = EarbudsFlowEvaluator.shouldPromptFlowOnAudioConnect(
            deviceType = AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
            isSink = true,
            isFocusAlreadyActive = true,
            isInCall = false,
            isBedtimeNow = false,
            lastPromptedAtMillis = 0L,
            nowMillis = 10_000_000L
        )

        assertEquals(EarbudsFlowDecision.Suppressed, decision)
    }

    @Test
    fun `suppresses prompt during active phone call`() {
        val decision = EarbudsFlowEvaluator.shouldPromptFlowOnAudioConnect(
            deviceType = AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
            isSink = true,
            isFocusAlreadyActive = false,
            isInCall = true,
            isBedtimeNow = false,
            lastPromptedAtMillis = 0L,
            nowMillis = 10_000_000L
        )

        assertEquals(EarbudsFlowDecision.Suppressed, decision)
    }

    @Test
    fun `suppresses prompt during digital wellbeing bedtime window`() {
        val decision = EarbudsFlowEvaluator.shouldPromptFlowOnAudioConnect(
            deviceType = AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
            isSink = true,
            isFocusAlreadyActive = false,
            isInCall = false,
            isBedtimeNow = true,
            lastPromptedAtMillis = 0L,
            nowMillis = 10_000_000L
        )

        assertEquals(EarbudsFlowDecision.Suppressed, decision)
    }

    @Test
    fun `suppresses prompt when not an audio sink`() {
        val decision = EarbudsFlowEvaluator.shouldPromptFlowOnAudioConnect(
            deviceType = AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
            isSink = false, // Source or mic only
            isFocusAlreadyActive = false,
            isInCall = false,
            isBedtimeNow = false,
            lastPromptedAtMillis = 0L,
            nowMillis = 10_000_000L
        )

        assertEquals(EarbudsFlowDecision.Suppressed, decision)
    }

    @Test
    fun `suppresses prompt if triggered within 30-minute debounce window`() {
        val lastPrompt = 10_000_000L
        val reconnectNow = lastPrompt + (10 * 60_000L) // 10 minutes later (< 30 min debounce)

        val decision = EarbudsFlowEvaluator.shouldPromptFlowOnAudioConnect(
            deviceType = AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
            isSink = true,
            isFocusAlreadyActive = false,
            isInCall = false,
            isBedtimeNow = false,
            lastPromptedAtMillis = lastPrompt,
            nowMillis = reconnectNow
        )

        assertEquals(EarbudsFlowDecision.Suppressed, decision)

        // After 30 minutes, prompt is permitted again
        val allowedLater = lastPrompt + (31 * 60_000L)
        val validDecision = EarbudsFlowEvaluator.shouldPromptFlowOnAudioConnect(
            deviceType = AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
            isSink = true,
            isFocusAlreadyActive = false,
            isInCall = false,
            isBedtimeNow = false,
            lastPromptedAtMillis = lastPrompt,
            nowMillis = allowedLater
        )
        assertTrue(validDecision is EarbudsFlowDecision.PromptStartFlow)
    }
}

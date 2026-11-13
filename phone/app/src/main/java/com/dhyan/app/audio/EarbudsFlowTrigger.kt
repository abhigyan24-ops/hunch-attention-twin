package com.dhyan.app.audio

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build

/**
 * Output decision for the Earbuds-In auto-flow trigger.
 */
sealed class EarbudsFlowDecision {
    data class PromptStartFlow(val humanMessage: String) : EarbudsFlowDecision()
    data object Suppressed : EarbudsFlowDecision()
}

/**
 * Pure, deterministic evaluator for personal headphone/earbud connection events.
 */
object EarbudsFlowEvaluator {

    const val DEBOUNCE_MILLIS = 30 * 60_000L // 30 minutes between prompts

    /**
     * Set of AudioDeviceInfo device types that represent personal focus listening devices.
     */
    val PERSONAL_AUDIO_TYPES: Set<Int> = buildSet {
        add(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP) // 8
        add(AudioDeviceInfo.TYPE_WIRED_HEADPHONES) // 4
        add(AudioDeviceInfo.TYPE_WIRED_HEADSET) // 3
        add(AudioDeviceInfo.TYPE_USB_HEADSET) // 22
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            add(AudioDeviceInfo.TYPE_BLE_HEADSET) // 26
            add(AudioDeviceInfo.TYPE_BLE_SPEAKER) // 27
        } else {
            add(26) // TYPE_BLE_HEADSET constant value
            add(27) // TYPE_BLE_SPEAKER constant value
        }
    }

    fun isPersonalAudioSink(deviceType: Int, isSink: Boolean): Boolean {
        if (!isSink) return false
        return PERSONAL_AUDIO_TYPES.contains(deviceType)
    }

    /**
     * Determines whether connecting an audio device should trigger a Quiet Shield focus prompt.
     */
    fun shouldPromptFlowOnAudioConnect(
        deviceType: Int,
        isSink: Boolean,
        isFocusAlreadyActive: Boolean,
        isInCall: Boolean,
        isBedtimeNow: Boolean,
        lastPromptedAtMillis: Long,
        nowMillis: Long
    ): EarbudsFlowDecision {
        // 1. Must be a personal listening sink (headphones/earbuds)
        if (!isPersonalAudioSink(deviceType, isSink)) {
            return EarbudsFlowDecision.Suppressed
        }

        // 2. Must not already be in a Focus Shield session
        if (isFocusAlreadyActive) {
            return EarbudsFlowDecision.Suppressed
        }

        // 3. Must not be in an active phone/VoIP call
        if (isInCall) {
            return EarbudsFlowDecision.Suppressed
        }

        // 4. Must not be within Digital Wellbeing bedtime hours
        if (isBedtimeNow) {
            return EarbudsFlowDecision.Suppressed
        }

        // 5. Enforce 30-minute debounce to prevent reconnect spam
        if (lastPromptedAtMillis > 0 &&
            (nowMillis - lastPromptedAtMillis) in 0 until DEBOUNCE_MILLIS
        ) {
            return EarbudsFlowDecision.Suppressed
        }

        return EarbudsFlowDecision.PromptStartFlow(
            humanMessage = "Earbuds connected — Start a 25m Quiet Shield?"
        )
    }
}

/**
 * Stateful manager that registers with AudioManager and dispatches flow prompt callbacks.
 */
class EarbudsFlowTrigger(
    private val context: Context,
    private val onPromptFlow: (humanMessage: String) -> Unit
) {
    private var lastPromptedAtMillis: Long = 0L
    private var audioManager: AudioManager? = null
    private var deviceCallback: AudioDeviceCallback? = null

    companion object {
        const val PREFS_NAME = "hunch_audio_trigger_prefs"
        const val KEY_LAST_PROMPT = "last_earbuds_prompt_ms"
    }

    init {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            lastPromptedAtMillis = prefs.getLong(KEY_LAST_PROMPT, 0L)
        } catch (_: Exception) {}
    }

    fun startListening(
        isFocusAlreadyActiveProvider: () -> Boolean,
        isInCallProvider: () -> Boolean,
        isBedtimeNowProvider: () -> Boolean
    ) {
        audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        deviceCallback = object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
                if (addedDevices == null) return
                val now = System.currentTimeMillis()
                for (device in addedDevices) {
                    val decision = EarbudsFlowEvaluator.shouldPromptFlowOnAudioConnect(
                        deviceType = device.type,
                        isSink = device.isSink,
                        isFocusAlreadyActive = isFocusAlreadyActiveProvider(),
                        isInCall = isInCallProvider(),
                        isBedtimeNow = isBedtimeNowProvider(),
                        lastPromptedAtMillis = lastPromptedAtMillis,
                        nowMillis = now
                    )

                    if (decision is EarbudsFlowDecision.PromptStartFlow) {
                        lastPromptedAtMillis = now
                        saveLastPrompt(now)
                        onPromptFlow(decision.humanMessage)
                        break
                    }
                }
            }
        }
        audioManager?.registerAudioDeviceCallback(deviceCallback, null)
    }

    fun stopListening() {
        deviceCallback?.let {
            audioManager?.unregisterAudioDeviceCallback(it)
        }
        deviceCallback = null
    }

    private fun saveLastPrompt(ts: Long) {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putLong(KEY_LAST_PROMPT, ts).apply()
        } catch (_: Exception) {}
    }
}

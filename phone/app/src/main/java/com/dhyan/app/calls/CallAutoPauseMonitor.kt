package com.dhyan.app.calls

import android.media.AudioManager
import android.os.Handler
import android.os.Looper

/** Reports audio-mode transitions on the main thread without accessing call records or microphone data. */
class CallAutoPauseMonitor(
    private val audio: AudioManager,
    private val onPauseChanged: (Boolean) -> Unit,
) {
    private val handler = Handler(Looper.getMainLooper())
    private var running = false
    private var paused = false
    private val sample = object : Runnable {
        override fun run() {
            if (!running) return
            val active = isCallMode(audio.mode)
            if (active != paused) {
                paused = active
                onPauseChanged(active)
            }
            if (running) handler.postDelayed(this, 500L)
        }
    }

    /** Start and stop on the main thread. The caller owns any session pause it applies. */
    fun start() {
        if (running) return
        running = true
        sample.run()
    }

    fun stop() {
        running = false
        handler.removeCallbacks(sample)
        paused = false
    }

    companion object {
        fun isCallMode(mode: Int): Boolean =
            mode == AudioManager.MODE_RINGTONE || mode == AudioManager.MODE_IN_CALL ||
                mode == AudioManager.MODE_IN_COMMUNICATION
    }
}

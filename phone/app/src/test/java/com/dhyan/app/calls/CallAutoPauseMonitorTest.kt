package com.dhyan.app.calls

import android.media.AudioManager
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CallAutoPauseMonitorTest {
    @Test fun classifiesOnlyCallAudioModes() {
        assertTrue(CallAutoPauseMonitor.isCallMode(AudioManager.MODE_RINGTONE))
        assertTrue(CallAutoPauseMonitor.isCallMode(AudioManager.MODE_IN_CALL))
        assertTrue(CallAutoPauseMonitor.isCallMode(AudioManager.MODE_IN_COMMUNICATION))
        assertFalse(CallAutoPauseMonitor.isCallMode(AudioManager.MODE_NORMAL))
        assertFalse(CallAutoPauseMonitor.isCallMode(-1))
    }
}

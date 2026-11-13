package com.dhyan.app.gesture

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.abs

enum class FlipState { INACTIVE, FACE_DOWN_FLAT, FOCUS_READY }

/** Call start/stop from the owning foreground lifecycle. Emits one trigger per placement. */
class FlipToFocusDetector(
    private val sensors: SensorManager,
    private val onFocus: () -> Unit,
) : SensorEventListener {
    private var near = false
    private var since: Long? = null
    private var fired = false

    fun start(): Boolean {
        stop()
        val proximity = sensors.getDefaultSensor(Sensor.TYPE_PROXIMITY) ?: return false
        val gravity = sensors.getDefaultSensor(Sensor.TYPE_GRAVITY)
            ?: sensors.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) ?: return false
        val registered = sensors.registerListener(this, proximity, SensorManager.SENSOR_DELAY_NORMAL) &&
            sensors.registerListener(this, gravity, SensorManager.SENSOR_DELAY_NORMAL)
        if (!registered) stop()
        return registered
    }

    fun stop() {
        sensors.unregisterListener(this)
        near = false
        since = null
        fired = false
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type == Sensor.TYPE_PROXIMITY) {
            near = event.values[0] < event.sensor.maximumRange
            if (!near) {
                since = null
                fired = false
            }
            return
        }
        val now = event.timestamp / 1_000_000
        val (x, y, z) = event.values
        when (evaluateFlipSample(z, x, y, near, now - (since ?: now))) {
            FlipState.INACTIVE -> { since = null; fired = false }
            FlipState.FACE_DOWN_FLAT -> if (since == null) since = now
            FlipState.FOCUS_READY -> if (!fired) {
                fired = true
                onFocus()
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    companion object {
        const val HOLD_MS = 1_500L

        fun evaluateFlipSample(z: Float, x: Float, y: Float, isNear: Boolean, heldDurationMs: Long): FlipState {
            if (!isNear || !z.isFinite() || !x.isFinite() || !y.isFinite() ||
                z !in -11f..-9f || abs(x) > 1.5f || abs(y) > 1.5f
            ) return FlipState.INACTIVE
            return if (heldDurationMs >= HOLD_MS) FlipState.FOCUS_READY else FlipState.FACE_DOWN_FLAT
        }
    }
}

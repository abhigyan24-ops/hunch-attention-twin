package com.dhyan.app.sensor

/**
 * Sensor battery duty-cycling and power-saving policy.
 * Protects battery by pausing motion, accelerometer, and proximity sensor listeners
 * during Digital Wellbeing bedtime, when Android Power Saver is active, or after extended inactivity.
 */
object SensorDutyCyclePolicy {

    const val MAX_INACTIVITY_MINUTES_BEFORE_SLEEP = 30

    /**
     * Determines whether hardware sensors should remain active.
     *
     * @param isBedtimeWindow True if the user is in their scheduled Digital Wellbeing bedtime window.
     * @param isPowerSaveMode True if Android PowerManager.isPowerSaveMode is active.
     * @param minutesSinceLastInteraction Minutes since user last touched/unlocked device.
     * @param isCharging True if device is plugged into AC or wireless charger.
     * @return True if sensors should be registered/active; false if they should be paused.
     */
    fun shouldEnableHardwareSensors(
        isBedtimeWindow: Boolean,
        isPowerSaveMode: Boolean,
        minutesSinceLastInteraction: Int,
        isCharging: Boolean
    ): Boolean {
        // Bedtime window: user is asleep, pause hardware sensors
        if (isBedtimeWindow) return false

        // Power save mode: pause unless charging
        if (isPowerSaveMode && !isCharging) return false

        // Inactivity: after 30 minutes of no interaction, pause sensors to prevent drain
        if (minutesSinceLastInteraction >= MAX_INACTIVITY_MINUTES_BEFORE_SLEEP && !isCharging) return false

        return true
    }
}

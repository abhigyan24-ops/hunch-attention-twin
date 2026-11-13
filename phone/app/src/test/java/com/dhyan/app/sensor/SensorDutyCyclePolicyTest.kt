package com.dhyan.app.sensor

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SensorDutyCyclePolicyTest {

    @Test
    fun `bedtime window disables hardware sensors to prevent overnight drain`() {
        val enabled = SensorDutyCyclePolicy.shouldEnableHardwareSensors(
            isBedtimeWindow = true,
            isPowerSaveMode = false,
            minutesSinceLastInteraction = 0,
            isCharging = false
        )
        assertFalse(enabled)
    }

    @Test
    fun `power save mode disables sensors unless charging`() {
        val onBattery = SensorDutyCyclePolicy.shouldEnableHardwareSensors(
            isBedtimeWindow = false,
            isPowerSaveMode = true,
            minutesSinceLastInteraction = 5,
            isCharging = false
        )
        assertFalse(onBattery)

        val charging = SensorDutyCyclePolicy.shouldEnableHardwareSensors(
            isBedtimeWindow = false,
            isPowerSaveMode = true,
            minutesSinceLastInteraction = 5,
            isCharging = true
        )
        assertTrue(charging)
    }

    @Test
    fun `extended inactivity over 30 mins duty cycles sensors to sleep unless plugged in`() {
        val inactiveUnplugged = SensorDutyCyclePolicy.shouldEnableHardwareSensors(
            isBedtimeWindow = false,
            isPowerSaveMode = false,
            minutesSinceLastInteraction = 35,
            isCharging = false
        )
        assertFalse(inactiveUnplugged)

        val inactiveCharging = SensorDutyCyclePolicy.shouldEnableHardwareSensors(
            isBedtimeWindow = false,
            isPowerSaveMode = false,
            minutesSinceLastInteraction = 35,
            isCharging = true
        )
        assertTrue(inactiveCharging)
    }

    @Test
    fun `active daytime usage with battery allows sensor operation`() {
        val active = SensorDutyCyclePolicy.shouldEnableHardwareSensors(
            isBedtimeWindow = false,
            isPowerSaveMode = false,
            minutesSinceLastInteraction = 2,
            isCharging = false
        )
        assertTrue(active)
    }
}

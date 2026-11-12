package com.dhyan.app.survival

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager

object OemSurvivalHelper {

    val manufacturer: String get() = (Build.MANUFACTURER ?: "generic").lowercase()

    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return false
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    fun createIgnoreBatteryOptimizationsIntent(context: Context): Intent {
        return Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = android.net.Uri.parse("package:${context.packageName}")
        }
    }

    /**
     * Returns tailored instructions for the user's OEM.
     * Covers the most common OEMs that aggressively kill background apps.
     */
    fun getManufacturerGuidance(): String = when {
        manufacturer.contains("xiaomi") || manufacturer.contains("redmi") || manufacturer.contains("poco") ->
            "Settings -> Apps -> Manage Apps -> Hunch -> Battery Saver -> No Restrictions. Also enable Background Autostart in Security -> Autostart."
        manufacturer.contains("samsung") ->
            "Settings -> Battery -> Background Usage Limits -> Apps that won't be put to sleep -> Add Hunch. Also set Battery to Unrestricted."
        manufacturer.contains("oneplus") ->
            "Settings -> Battery -> Battery Optimization -> All Apps -> Hunch -> Don't Optimize. Enable Background activity in App settings."
        manufacturer.contains("oppo") || manufacturer.contains("realme") ->
            "Settings -> Battery -> Power Saving -> Custom -> Add Hunch. Enable 'Allow background activity' in App settings."
        manufacturer.contains("vivo") ->
            "Settings -> Battery -> Background Power Consumption -> Hunch -> Allow background activity."
        manufacturer.contains("nothing") || manufacturer.contains("google") ->
            "Settings -> Apps -> Hunch -> Battery -> Unrestricted."
        manufacturer.contains("huawei") || manufacturer.contains("honor") ->
            "Settings -> Battery -> App Launch -> Hunch -> Manage Manually. Enable Auto-launch and Secondary launch."
        else ->
            "Settings -> Battery -> Battery Optimization -> All Apps -> Hunch -> Don't Optimize."
    }
}

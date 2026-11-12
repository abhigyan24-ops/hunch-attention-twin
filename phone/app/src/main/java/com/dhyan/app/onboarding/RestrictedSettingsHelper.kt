package com.dhyan.app.onboarding

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings

enum class TrustCapability(
    val title: String,
    val disclosure: String,
    private val canBeRestricted: Boolean = false,
) {
    BEDTIME(
        "Sync bedtime from device activity",
        "Hunch uses recent device activity on this phone to estimate a bedtime and wake time. You can keep using a manual schedule.",
    ),
    QUIET_FOCUS(
        "Hold distracting notifications",
        "Notification access lets Hunch inspect incoming notifications and hold interruptions during a focus session. Grant access only if you want Hunch to manage notifications.",
        true,
    ),
    ADVANCED_CONTEXT(
        "Advanced app context",
        "Accessibility access gives Hunch app and interaction events, such as app changes and scrolling, for focus context. Android grants sensitive access through this service. Enable it only if you want this feature.",
        true,
    ),
    FLOATING_HUD(
        "Floating focus HUD",
        "Display over other apps lets Hunch show the focus companion while you use other apps.",
    );

    fun canShowRestrictedSettingsHelp(context: Context): Boolean =
        canBeRestricted && RestrictedSettingsHelper.mayHaveRestrictedSettings(context)

    fun settingsIntent(context: Context): Intent = when (this) {
        BEDTIME -> RestrictedSettingsHelper.createUsageAccessIntent()
        QUIET_FOCUS -> RestrictedSettingsHelper.createNotificationListenerIntent()
        ADVANCED_CONTEXT -> RestrictedSettingsHelper.createAccessibilityIntent()
        FLOATING_HUD -> RestrictedSettingsHelper.createOverlayIntent(context)
    }
}

object RestrictedSettingsHelper {
    fun mayHaveRestrictedSettings(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return false
        val installer = runCatching {
            context.packageManager.getInstallSourceInfo(context.packageName).installingPackageName
        }.getOrNull()
        return installer != "com.android.vending"
    }

    fun createUnlockRestrictedSettingsIntent(context: Context): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri(context))

    fun createUsageAccessIntent(): Intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
    fun createNotificationListenerIntent(): Intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
    fun createAccessibilityIntent(): Intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
    fun createOverlayIntent(context: Context): Intent =
        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, packageUri(context))

    private fun packageUri(context: Context): Uri = Uri.fromParts("package", context.packageName, null)
}

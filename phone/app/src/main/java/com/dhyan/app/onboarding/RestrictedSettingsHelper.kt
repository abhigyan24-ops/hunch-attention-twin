package com.dhyan.app.onboarding

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings

enum class TrustCapability(
    val title: String,
    val disclosure: String,
    private val settingsAction: String,
    private val canBeRestricted: Boolean = false,
) {
    BEDTIME(
        "Sync bedtime from device activity",
        "Hunch uses recent device activity on this phone to estimate a bedtime and wake time. You can keep using a manual schedule.",
        Settings.ACTION_USAGE_ACCESS_SETTINGS,
    ),
    QUIET_FOCUS(
        "Hold distracting notifications",
        "Notification access lets Hunch inspect incoming notifications and hold interruptions during a focus session. Grant access only if you want Hunch to manage notifications.",
        Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS,
        true,
    ),
    ADVANCED_CONTEXT(
        "Advanced app context",
        "Accessibility access gives Hunch app and interaction events, such as app changes and scrolling, for focus context. Android grants sensitive access through this service. Enable it only if you want this feature.",
        Settings.ACTION_ACCESSIBILITY_SETTINGS,
        true,
    ),
    FLOATING_HUD(
        "Floating focus HUD",
        "Display over other apps lets Hunch show the focus companion while you use other apps.",
        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
    );

    val canShowRestrictedSettingsHelp: Boolean
        get() = canBeRestricted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    fun settingsIntent(packageName: String): Intent = if (this == FLOATING_HUD) {
        Intent(settingsAction, Uri.fromParts("package", packageName, null))
    } else {
        Intent(settingsAction)
    }
}

internal fun appDetailsIntent(packageName: String): Intent =
    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))

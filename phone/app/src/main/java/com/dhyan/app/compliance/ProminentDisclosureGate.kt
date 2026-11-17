package com.dhyan.app.compliance

import android.content.Context
import android.content.SharedPreferences

/**
 * ProminentDisclosureGate — Play Protect & MUwS compliance gate.
 *
 * Enforces Google Play Protect requirement:
 * Before launching ANY system settings intent (Usage Access, Notification Listener,
 * Accessibility, Overlay), the user MUST have seen and explicitly accepted the
 * Prominent Disclosure card for that specific permission tier.
 *
 * canLaunchSystemSettingsIntent() returns false unless explicit consent is recorded.
 * No system settings Intent may ever fire without prior prominent disclosure.
 */
object ProminentDisclosureGate {

    private const val PREFS_NAME = "hunch_disclosure_consent"

    enum class PermissionTier(val key: String) {
        USAGE_STATS("consent_usage_stats"),
        NOTIFICATION_LISTENER("consent_notification_listener"),
        ACCESSIBILITY_HUD("consent_accessibility_hud"),
        OVERLAY_HUD("consent_overlay_hud")
    }

    data class ProminentDisclosureCopy(
        val tier: PermissionTier,
        val title: String,
        val whatIsAccessed: String,
        val onDeviceGuarantee: String,
        val revocabilityStatement: String
    )

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** Returns true if the user has explicitly accepted the disclosure for [tier]. */
    fun hasUserAcceptedDisclosure(context: Context, tier: PermissionTier): Boolean =
        prefs(context).getBoolean(tier.key, false)

    /** Records explicit user consent for [tier]. Call only after the disclosure card is shown and user taps Accept. */
    fun recordUserConsent(context: Context, tier: PermissionTier) {
        prefs(context).edit().putBoolean(tier.key, true).apply()
    }

    /** Revokes user consent for [tier] (e.g. user revokes in settings). */
    fun revokeUserConsent(context: Context, tier: PermissionTier) {
        prefs(context).edit().putBoolean(tier.key, false).apply()
    }

    /**
     * Returns true only if the user has explicitly accepted the prominent disclosure for [tier].
     * [hasUserCheckedConsent] is the value from the UI checkbox/toggle at the moment of the call.
     *
     * USAGE: Call this immediately before constructing any system settings Intent.
     * If false, show the ProminentDisclosureCard UI instead.
     */
    fun canLaunchSystemSettingsIntent(
        context: Context,
        tier: PermissionTier,
        hasUserCheckedConsent: Boolean = false
    ): Boolean {
        if (hasUserCheckedConsent) {
            recordUserConsent(context, tier)
        }
        return hasUserAcceptedDisclosure(context, tier)
    }

    /**
     * Returns the exact disclosure copy for [tier].
     * All copy is plain English with zero dark patterns.
     * Users should keep Play Protect enabled.
     */
    fun getDisclosureStatement(tier: PermissionTier): ProminentDisclosureCopy = when (tier) {
        PermissionTier.USAGE_STATS -> ProminentDisclosureCopy(
            tier = tier,
            title = "Allow Hunch to see which apps you use",
            whatIsAccessed = "App usage timestamps (which apps were open and for how long). " +
                "No app content, messages, or personal data is accessed.",
            onDeviceGuarantee = "All processing happens on your device only. 0 bytes uploaded to any server.",
            revocabilityStatement = "You can revoke this at any time: Settings → Apps → Special app access → Usage access → Hunch → toggle off."
        )
        PermissionTier.NOTIFICATION_LISTENER -> ProminentDisclosureCopy(
            tier = tier,
            title = "Allow Hunch to see notification metadata",
            whatIsAccessed = "Notification category and app package only. " +
                "Hunch never reads notification text, titles, or content. " +
                "Sender identity is hashed with a local salt and never stored in plaintext.",
            onDeviceGuarantee = "All processing happens on your device only. 0 bytes uploaded to any server.",
            revocabilityStatement = "You can revoke this at any time: Settings → Apps → Special app access → Notification access → Hunch → toggle off."
        )
        PermissionTier.ACCESSIBILITY_HUD -> ProminentDisclosureCopy(
            tier = tier,
            title = "Allow Hunch to detect foreground app switches",
            whatIsAccessed = "Which app moves to the foreground (package name only, typeWindowStateChanged events). " +
                "Hunch never reads screen text, keystrokes, passwords, or any UI content.",
            onDeviceGuarantee = "All processing happens on your device only. 0 bytes uploaded to any server.",
            revocabilityStatement = "You can revoke this at any time: Settings → Accessibility → Hunch → toggle off."
        )
        PermissionTier.OVERLAY_HUD -> ProminentDisclosureCopy(
            tier = tier,
            title = "Allow Hunch to show a focus HUD overlay",
            whatIsAccessed = "No data is accessed. This permission only allows Hunch to draw a small status overlay above other apps.",
            onDeviceGuarantee = "No data is collected or uploaded. 0 bytes uploaded to any server.",
            revocabilityStatement = "You can revoke this at any time: Settings → Apps → Special app access → Display over other apps → Hunch → toggle off."
        )
    }
}

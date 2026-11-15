package com.dhyan.app.safety

/**
 * NotificationTriageEngine — MUwS-compliant notification pass-through guard.
 *
 * Per Google Mobile Unwanted Software (MUwS) policy:
 * "Do not suppress alerts to the user from other apps or from the operating system,
 *  notably those which inform the user of changes to their OS."
 *
 * Any package in SYSTEM_SECURITY_EXEMPT_PACKAGES MUST always be delivered immediately
 * and MUST NEVER be held, deferred, or canceled — even during active Focus Shield sessions.
 */
object NotificationTriageEngine {

    /**
     * OS, security and payment packages that are unconditionally exempt from notification holding.
     * This list is append-only: never remove an entry.
     */
    val SYSTEM_SECURITY_EXEMPT_PACKAGES: Set<String> = setOf(
        "android",
        "com.android.systemui",
        "com.android.vending",           // Google Play Store / Play Protect
        "com.google.android.gms",        // Google Play Services
        "com.google.android.dialer",
        "com.android.server.telecom",
        "com.android.incallui",
        "com.android.settings",
        // Critical payment / security alerts (India)
        "com.google.android.apps.nbu.paisa.user",
        "com.phonepe.app",
        "net.one97.paytm"
    )

    /**
     * Returns true if [pkg] must bypass all notification holding mechanisms.
     * This check runs FIRST in the notification pipeline, before any ML decision.
     */
    fun isSystemExemptPackage(pkg: String): Boolean =
        SYSTEM_SECURITY_EXEMPT_PACKAGES.contains(pkg)
}

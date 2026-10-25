package com.dhyan.app.actuator

import com.dhyan.app.twin.decision.Action
import com.dhyan.app.twin.decision.PolicyMode

/**
 * Pure function: given app category and decision action, choose the hold mechanism.
 *
 * Rules:
 *  - ALLOW → no hold (should not be called, but handled defensively)
 *  - DELAY for messaging apps → SNOOZE (preserves original notification intent)
 *  - DIGEST for social/content/promo → CANCEL_DIGEST
 *  - SILENT (all) → CANCEL_DIGEST
 *  - Per-package override table checked first.
 */
object MechanismChooser {

    enum class Mechanism { SNOOZE, CANCEL_DIGEST, NONE }

    /** Packages that always use SNOOZE regardless of action. */
    private val snoozePackages = setOf(
        "com.whatsapp",
        "com.whatsapp.w4b",
        "org.telegram.messenger",
        "com.Slack",
        "com.microsoft.teams",
        "com.discord"
    )

    /** App categories that use SNOOZE for DELAY actions. */
    private val snoozeCats = setOf("msg", "social_msg", "messaging")

    fun choose(
        appCat: String,
        action: Action,
        packageName: String,
        mode: PolicyMode
    ): Mechanism {
        if (mode == PolicyMode.OFF || mode == PolicyMode.DRY_RUN) return Mechanism.NONE
        return when (action) {
            Action.ALLOW -> Mechanism.NONE
            Action.DELAY -> {
                if (packageName in snoozePackages || appCat in snoozeCats) Mechanism.SNOOZE
                else Mechanism.CANCEL_DIGEST
            }
            Action.DIGEST -> Mechanism.CANCEL_DIGEST
            Action.SILENT -> Mechanism.CANCEL_DIGEST
        }
    }
}

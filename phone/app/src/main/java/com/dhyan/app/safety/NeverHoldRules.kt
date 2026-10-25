package com.dhyan.app.safety

import com.dhyan.app.twin.decision.NotificationFacts
import com.dhyan.app.twin.decision.PolicySettings

class NeverHoldRules(private val diagnostics: DecisionDiagnostics = DecisionDiagnostics()) {
    /** Category tokens are explicit metadata, never content classification performed here. */
    fun check(facts: NotificationFacts, settings: PolicySettings): String? {
        if(facts.notifCat !in knownCategories)diagnostics.increment(DiagnosticCounter.UNKNOWN_NOTIF_CAT)
        return when {
            facts.notifCat=="call" -> "call"
            facts.notifCat=="alarm" -> "alarm"
            facts.notifCat=="navigation" -> "navigation"
            facts.isOtpLike || facts.notifCat=="otp" -> "otp"
            facts.hasEmergencyKeyword -> "emergency"
            facts.isVip -> "vip"
            facts.pkg in settings.allowListPackages -> "allowlist"
            else -> null
        }
    }
    companion object {
        internal fun categoryKnown(category:String):Boolean = category in knownCategories
        // The schema leaves notifCat open. These are documented policy/observed wire tokens,
        // not a fabricated canonical enum. 'alert' cannot recover a lost alarm distinction.
        private val knownCategories=setOf("call","alarm","navigation","otp","msg","message","email","promo","alert","sys","social","other","transport","delivery","security","social_notif","msg_known","lecture_watch","shorts")
    }
}

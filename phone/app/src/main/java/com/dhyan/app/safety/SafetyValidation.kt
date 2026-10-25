package com.dhyan.app.safety

import com.dhyan.app.twin.decision.NotificationFacts
import com.dhyan.app.twin.decision.PolicySettings

/** Shared pure input checks for recheck and the future 5C pipeline; no policy decision here. */
internal object SafetyValidation {
    private val categories=setOf("coding","docs","communication","social","video","short_video","games","shopping","news","music","learning","utility","system","unknown")
    fun settingsValid(settings: PolicySettings): Boolean = settings.kappaDPerHour.isFinite() && settings.kappaDPerHour>0 &&
        settings.epsilon.isFinite() && settings.epsilon in 0.0..1.0 && settings.digestIntervalMin in 1..1440 && settings.allowListPackages.none { it.isBlank() }
    fun factsValid(facts: NotificationFacts, nowMs: Long): Boolean = nowMs>=0 && facts.keyHash.isNotBlank() &&
        facts.postedTsMs>=0 && facts.postedTsMs<=nowMs && facts.pkg.isNotBlank() && facts.appCat in categories &&
        facts.notifCat.isNotBlank() && (facts.senderHash==null || facts.senderHash.isNotBlank()) &&
        facts.urgencyScore.isFinite() && facts.urgencyScore in 0.0..1.0 && facts.repeatCount5m>=0
}

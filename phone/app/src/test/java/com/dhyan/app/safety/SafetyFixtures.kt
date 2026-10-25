package com.dhyan.app.safety

import com.dhyan.app.twin.decision.*

internal object SafetyFixtures {
    const val NOW=10000L
    val facts=NotificationFacts("synthetic-key-hash",1000L,"synthetic.app","social","social",null,false,.2,false,false,0,false,false)
    val live=PolicySettings(mode=PolicyMode.LIVE)
    fun hold(f: NotificationFacts = facts): Decision = AlwaysAllowDecisionProvider().decide(f,
        com.dhyan.app.twin.model.ContextSnapshot(NOW,com.dhyan.app.twin.model.FocusState.WORKING,com.dhyan.app.twin.model.TaskType.CODING,
            com.dhyan.app.twin.model.TaskSource.INFERRED,.8,"morning",1.0,"low",0,com.dhyan.app.twin.model.LaptopState.OFFLINE,
            com.dhyan.app.twin.model.PhoneState.SCREEN_OFF,false,null),live,NOW).copy(
        action=Action.DIGEST,holdMinutes=30.0,propensity=.95,eligibleActions=setOf(Action.DIGEST,Action.ALLOW),
        failedOpen=false,predictedLossShowMin=1000.0,predictedLossSuppressMin=1.0,deltaLMean=999.0,
        provenance="simulated",reasonCode="cost_selected")
    val urgentCases=listOf(
        facts.copy(notifCat="call") to "call",facts.copy(notifCat="alarm") to "alarm",
        facts.copy(notifCat="navigation") to "navigation",facts.copy(isOtpLike=true) to "otp",
        facts.copy(notifCat="otp") to "otp",facts.copy(hasEmergencyKeyword=true) to "emergency",
        facts.copy(isVip=true) to "vip",facts to "allowlist")
}

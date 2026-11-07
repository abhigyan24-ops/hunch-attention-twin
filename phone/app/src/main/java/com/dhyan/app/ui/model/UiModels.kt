package com.dhyan.app.ui.model

enum class AttentionState(val label: String, val description: String) {
    DEEP_FOCUS("Deep Focus", "High cognitive immersion; distractions severely penalised"),
    WORKING("Working", "Active task engagement with normal responsiveness"),
    DRIFTING("Drifting", "Rapid context switching detected; high vulnerability"),
    DISTRACTED("Distracted", "Off-task activity active on secondary surface"),
    RECOVERING("Recovering", "Returning to primary workflow after interruption"),
    FATIGUED("Fatigued", "Long session without break; elevated lapse hazard")
}

enum class LaptopPresence(val label: String, val activeApp: String) {
    FLOWING("Flowing", "VS Code (Active Editing)"),
    STUCK("Stuck", "VS Code (No edits >3m)"),
    BROWSING("Browsing", "Chrome (Documentation)"),
    IDLE("Idle", "Screen On (No input >2m)"),
    AWAY("Away", "Away (>5m)"),
    OFFLINE("Offline", "Relay Disconnected")
}

enum class ActionVerdict(val label: String, val summary: String) {
    ALLOW("ALLOW", "Importance outweighs predicted attention cost"),
    DIGEST("DIGEST", "Held for next natural focus boundary / break"),
    DELAY("DELAY", "Snoozed 15 min until current task block finishes"),
    SILENT("SILENT", "Suppressed silently without interrupting")
}

data class SimulationResult(
    val pDistractionShow: Double,
    val pDistractionSuppress: Double,
    val expectedLostShowMin: Double,
    val ciLowShowMin: Double,
    val ciHighShowMin: Double,
    val expectedLostSuppressMin: Double,
    val verdict: ActionVerdict,
    val explanation: String,
    val betaAlpha: Double,
    val betaBeta: Double,
    val lognormMu: Double,
    val lognormSigma: Double,
    val updateCount: Int
)

data class DigestItem(
    val id: String,
    val appName: String,
    val sender: String,
    val category: String,
    val receivedAgo: String,
    val heldReason: String,
    val importanceScore: Double
)

data class DecisionFeedItem(
    val id: String,
    val appName: String,
    val action: ActionVerdict,
    val timestamp: String,
    val reason: String
)

data class TriggerStat(
    val name: String,
    val category: String,
    val incidentCount: Int,
    val avgLostMin: Double,
    val isSelfInitiated: Boolean
)

data class PersonaProfile(
    val id: String,
    val name: String,
    val role: String,
    val description: String,
    val medianSessionMin: Int,
    val pPickupDefault: Double,
    val typicalApps: List<String>
)

object PersonaCatalog {
    val REAL_USER = PersonaProfile(
        id = "real_user",
        name = "My Real Twin",
        role = "Your Real Profile",
        description = "Empirically calibrated from your unique device telemetry and focus sessions.",
        medianSessionMin = 30,
        pPickupDefault = 0.50,
        typicalApps = emptyList()
    )
    val RAHUL = PersonaProfile(
        id = "rahul",
        name = "Rahul Sharma",
        role = "Backend Systems Engineer",
        description = "Long 45-90 min coding stretches in VS Code. High cost when interrupted; slow 8-12 min resumption.",
        medianSessionMin = 48,
        pPickupDefault = 0.62,
        typicalApps = listOf("Slack", "GitHub", "WhatsApp", "Twitter/X")
    )
    val ANIKA = PersonaProfile(
        id = "anika",
        name = "Anika Patel",
        role = "Product Operations Lead",
        description = "Rapid 15-25 min task switching, 80+ daily alerts. Low resumption cost, high notification volume.",
        medianSessionMin = 22,
        pPickupDefault = 0.78,
        typicalApps = listOf("Slack", "Gmail", "Calendar", "Linear")
    )
    val KABIR = PersonaProfile(
        id = "kabir",
        name = "Kabir Mehta",
        role = "Design & Content Creator",
        description = "Visual workflows, high media consumption, variable cadence. Prone to rabbit holes from social alerts.",
        medianSessionMin = 35,
        pPickupDefault = 0.71,
        typicalApps = listOf("Figma", "Instagram", "YouTube", "Behance")
    )
}

package com.dhyan.app.twin.decision

/** text is controlled template output, never notification text. contribution is focus-minutes. */
data class Factor(val kind: String, val text: String, val contribution: Double)

/** Read-only value record. Callers must provide immutable factor/action collections. */
data class Decision(
    val action: Action, val explanation: String, val factors: List<Factor>,
    val holdMinutes: Double?, val propensity: Double, val eligibleActions: Set<Action>,
    val explored: Boolean,
    /** Reserved for a never-hold rule token; general decision causes belong in reasonCode. */
    val safetyReason: String?, val failedOpen: Boolean,
    val predictedLossShowMin: Double?, val predictedLossSuppressMin: Double?,
    val deltaLMean: Double?, val deltaLP10: Double?, val deltaLP90: Double?,
    val evidenceStrength: Double?, val provenance: String, val modelVersion: Long,
    val policyVersion: String, val decidedAtMs: Long, val keyHash: String, val postedTsMs: Long,
    /** Controlled token only, never input content or exception messages. */
    val reasonCode: String = "unspecified"
) {
    /** SHOW=ALLOW. SUPPRESS=DELAY|DIGEST|SILENT. Exact chosen-action propensity after filtering. */
    val pShow: Double get() = if (action == Action.ALLOW) propensity else 1.0 - propensity
}

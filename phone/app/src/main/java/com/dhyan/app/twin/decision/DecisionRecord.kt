package com.dhyan.app.twin.decision

import com.dhyan.app.twin.model.ContextSnapshot
import java.util.Collections

data class RecordedFactor(val kind: String, val contribution: Double)
data class SnapshotSummary(val focusState: String, val taskType: String, val hourBand: String,
    val fatigueBand: String, val laptopState: String, val confidence: Double)
/** Hashes, controlled enum/reason tokens and numbers only; no explanation or factor text. */
data class DecisionRecord(
    val keyHash: String, val postedTsMs: Long, val decidedAtMs: Long, val action: Action,
    val propensity: Double, val pShow: Double, val eligibleActions: Set<Action>, val explored: Boolean,
    val safetyReason: String?, val failedOpen: Boolean, val predictedLossShowMin: Double?,
    val predictedLossSuppressMin: Double?, val deltaLMean: Double?, val deltaLP10: Double?, val deltaLP90: Double?,
    val evidenceStrength: Double?, val factors: List<RecordedFactor>, val snapshotSummary: SnapshotSummary,
    val importance: Double, val modelVersion: Long, val policyVersion: String, val mode: PolicyMode,
    val actuationStatus: ActuationStatus? = null,
    /** Controlled general decision cause; safetyReason is reserved for never-hold rule tokens. */
    val reasonCode: String = "unspecified"
)
object DecisionRecordFactory {
    fun from(facts: NotificationFacts, decision: Decision, snapshot: ContextSnapshot,
        settings: PolicySettings, importance: Double): DecisionRecord {
        require(facts.keyHash == decision.keyHash && facts.postedTsMs == decision.postedTsMs) { "identity_mismatch" }
        return DecisionRecord(facts.keyHash,facts.postedTsMs,decision.decidedAtMs,decision.action,
            decision.propensity,decision.pShow,Collections.unmodifiableSet(LinkedHashSet(decision.eligibleActions)),
            decision.explored,decision.safetyReason,decision.failedOpen,decision.predictedLossShowMin,
            decision.predictedLossSuppressMin,decision.deltaLMean,decision.deltaLP10,decision.deltaLP90,
            decision.evidenceStrength,Collections.unmodifiableList(decision.factors.map { RecordedFactor(it.kind,it.contribution) }),
            SnapshotSummary(snapshot.focusState.wire,snapshot.taskType.wire,snapshot.hourBand,snapshot.fatigueBand,snapshot.laptopState.wire,snapshot.confidence),
            importance,decision.modelVersion,decision.policyVersion,settings.mode,reasonCode=decision.reasonCode)
    }
}

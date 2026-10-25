package com.dhyan.app.data

import com.dhyan.app.twin.decision.DecisionLogStore
import com.dhyan.app.twin.decision.DecisionRecord

/**
 * Room-backed implementation of [DecisionLogStore].
 *
 * PRIVACY CONTRACT:
 *  - Maps DecisionRecord fields to DecisionEntity — hashes, enums, numbers only.
 *  - No text, title, sender name, or explanation text is stored.
 */
class RoomDecisionLogStore(private val dao: DecisionDao) : DecisionLogStore {

    override suspend fun append(record: DecisionRecord) {
        val entity = DecisionEntity(
            ts = record.decidedAtMs,
            keyHash = record.keyHash,
            postedTsMs = record.postedTsMs,
            action = record.action.name,
            reasonCode = record.reasonCode,
            safetyReason = record.safetyReason,
            pLossShow = record.predictedLossShowMin,
            pLossSuppress = record.predictedLossSuppressMin,
            deltaLMean = record.deltaLMean,
            deltaLP10 = record.deltaLP10,
            deltaLP90 = record.deltaLP90,
            evidenceStrength = record.evidenceStrength,
            propensity = record.propensity,
            pShow = record.pShow,
            exploration = record.explored,
            failedOpen = record.failedOpen,
            importance = record.importance,
            modelVersion = record.modelVersion,
            policyVersion = record.policyVersion,
            mode = record.mode.name,
            actuationStatus = record.actuationStatus?.name
        )
        dao.insert(entity)
    }

    /** Update actuation outcome after platform call. */
    suspend fun updateStatus(keyHash: String, postedTsMs: Long, status: String) {
        dao.updateActuationStatus(keyHash, postedTsMs, status)
    }
}

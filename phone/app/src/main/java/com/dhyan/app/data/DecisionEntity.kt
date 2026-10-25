package com.dhyan.app.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Logged decision for counterfactual analysis and exploration tracking.
 *
 * PRIVACY CONTRACT: No text columns. All identifiers are hashes or controlled enum tokens.
 */
@Entity(
    tableName = "decisions",
    indices = [Index(value = ["ts"]), Index(value = ["key_hash"])]
)
data class DecisionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "ts") val ts: Long,
    @ColumnInfo(name = "key_hash") val keyHash: String,
    @ColumnInfo(name = "posted_ts_ms") val postedTsMs: Long,
    @ColumnInfo(name = "action") val action: String,           // ALLOW | DELAY | DIGEST | SILENT
    @ColumnInfo(name = "reason_code") val reasonCode: String,
    @ColumnInfo(name = "safety_reason") val safetyReason: String?,
    @ColumnInfo(name = "p_loss_show") val pLossShow: Double?,
    @ColumnInfo(name = "p_loss_suppress") val pLossSuppress: Double?,
    @ColumnInfo(name = "delta_l_mean") val deltaLMean: Double?,
    @ColumnInfo(name = "delta_l_p10") val deltaLP10: Double?,
    @ColumnInfo(name = "delta_l_p90") val deltaLP90: Double?,
    @ColumnInfo(name = "evidence_strength") val evidenceStrength: Double?,
    @ColumnInfo(name = "propensity") val propensity: Double,
    @ColumnInfo(name = "p_show") val pShow: Double,
    @ColumnInfo(name = "exploration") val exploration: Boolean,
    @ColumnInfo(name = "failed_open") val failedOpen: Boolean,
    @ColumnInfo(name = "importance") val importance: Double,
    @ColumnInfo(name = "model_version") val modelVersion: Long,
    @ColumnInfo(name = "policy_version") val policyVersion: String,
    @ColumnInfo(name = "mode") val mode: String,              // OFF | DRY_RUN | LIVE
    /** Filled in by ActuationCoordinator after platform call. */
    @ColumnInfo(name = "actuation_status") val actuationStatus: String? = null
)

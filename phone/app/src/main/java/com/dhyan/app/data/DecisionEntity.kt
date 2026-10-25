package com.dhyan.app.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Logged decision for counterfactual analysis and exploration tracking. */
@Entity(
    tableName = "decisions",
    indices = [Index(value = ["ts"])]
)
data class DecisionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "ts") val ts: Long,
    @ColumnInfo(name = "notif_id") val notifId: String,
    @ColumnInfo(name = "action") val action: String,         // allow | delay | digest | silent
    @ColumnInfo(name = "reason") val reason: String,
    @ColumnInfo(name = "p_loss_show") val pLossShow: Double,
    @ColumnInfo(name = "p_loss_suppress") val pLossSuppress: Double,
    @ColumnInfo(name = "delta_l_mean") val deltaLMean: Double,
    @ColumnInfo(name = "propensity") val propensity: Double, // P(action=allow) for counterfactual
    @ColumnInfo(name = "exploration") val exploration: Boolean,
    @ColumnInfo(name = "model_version") val modelVersion: Long
)

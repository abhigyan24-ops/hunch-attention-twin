package com.dhyan.app.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/** User explicit feedback on a decision. */
@Entity(tableName = "feedback")
data class FeedbackEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "ts") val ts: Long,
    @ColumnInfo(name = "decision_id") val decisionId: Long,
    @ColumnInfo(name = "type") val type: String,   // thumbs_up | thumbs_down | override_allow | override_block
    @ColumnInfo(name = "comment") val comment: String?  // free text — user-initiated only, kept private
)

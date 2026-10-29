package com.dhyan.app.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Persisted command execution record for expiry, idempotency, and audit trails
 * per Dhyan Sync Contract Section 8.
 */
@Entity(
    tableName = "cmd_log",
    indices = [
        Index("ts_ms"),
        Index("action")
    ]
)
data class CmdLogEntity(
    @PrimaryKey
    @ColumnInfo(name = "cmd_id")
    val cmdId: String,

    @ColumnInfo(name = "source")
    val source: String,

    @ColumnInfo(name = "action")
    val action: String,

    @ColumnInfo(name = "status")
    val status: String, // "executed" | "rejected" | "expired" | "duplicate"

    @ColumnInfo(name = "reason")
    val reason: String? = null,

    @ColumnInfo(name = "ts_ms")
    val tsMs: Long,

    @ColumnInfo(name = "executed_at_ms")
    val executedAtMs: Long
)

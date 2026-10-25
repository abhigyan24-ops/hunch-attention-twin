package com.dhyan.app.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/** Tracks relay sync cursor position per device. */
@Entity(tableName = "sync_cursors")
data class SyncCursorEntity(
    @PrimaryKey val deviceId: String,
    @ColumnInfo(name = "last_seq") val lastSeq: Long,
    @ColumnInfo(name = "updated_ms") val updatedMs: Long
)

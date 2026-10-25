package com.dhyan.app.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/** Key-value settings store. */
@Entity(tableName = "settings")
data class SettingsEntity(
    @PrimaryKey val key: String,
    @ColumnInfo(name = "value") val value: String,
    @ColumnInfo(name = "updated_ms") val updatedMs: Long
)

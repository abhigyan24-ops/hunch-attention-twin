package com.dhyan.app.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/** Maps a package name to a category label. Cached from AppCategorizer. */
@Entity(tableName = "app_labels")
data class AppLabelEntity(
    @PrimaryKey val pkg: String,
    @ColumnInfo(name = "category") val category: String,
    @ColumnInfo(name = "label") val label: String,
    @ColumnInfo(name = "updated_ms") val updatedMs: Long
)

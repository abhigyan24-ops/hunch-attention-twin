package com.dhyan.app.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/** VIP sender list: salted hashes only, never raw contact names. */
@Entity(tableName = "vips")
data class VipEntity(
    @PrimaryKey val senderHash: String,
    @ColumnInfo(name = "added_ms") val addedMs: Long
)

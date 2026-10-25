package com.dhyan.app.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A notification that has been delayed or batched (snoozed/digested).
 * Privacy: only category/sender hash stored, never text.
 */
@Entity(tableName = "held_notifs")
data class HeldNotifEntity(
    @PrimaryKey val notifId: String,          // synthetic ID (appCat|senderHash|ts)
    @ColumnInfo(name = "app_cat") val appCat: String,
    @ColumnInfo(name = "notif_cat") val notifCat: String,
    @ColumnInfo(name = "sender_hash") val senderHash: String?,
    @ColumnInfo(name = "held_since_ms") val heldSinceMs: Long,
    @ColumnInfo(name = "deliver_after_ms") val deliverAfterMs: Long,
    @ColumnInfo(name = "decision") val decision: String,       // "delay" | "digest"
    @ColumnInfo(name = "delivered") val delivered: Boolean
)

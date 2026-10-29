package com.dhyan.app.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Persisted event record — categories and counts only, never raw content.
 *
 * Maps to the core event schema (spec section 6, contract section 10 & 16).
 */
@Entity(
    tableName = "events",
    indices = [
        Index(value = ["ts"]),
        Index(value = ["kind"]),
        Index(value = ["device_id", "seq", "event_id"])
    ]
)
data class EventEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    /** Epoch ms */
    @ColumnInfo(name = "ts") val ts: Long,
    /** Event kind: notif_posted, notif_removed, pickup, screen_on, scroll_burst, usage_window, laptop_state */
    @ColumnInfo(name = "kind") val kind: String,
    /** App category (social, productivity, comms, etc.) — never the package name raw */
    @ColumnInfo(name = "app_cat") val appCat: String,
    /** Surface type: notification_shade, feed, chat, browser, etc. */
    @ColumnInfo(name = "surface") val surface: String,
    /** Duration in minutes (0 if not applicable) */
    @ColumnInfo(name = "duration_min") val durationMin: Double,
    /** Salted hash of sender ID (for VIP/importance only). Null if not a message. */
    @ColumnInfo(name = "sender_hash") val senderHash: String?,
    /** Notification category classification (msg, promo, alert, sys). Null if not a notif. */
    @ColumnInfo(name = "notif_cat") val notifCat: String?,
    /** Scroll count (for scroll_burst events only, 0 otherwise) */
    @ColumnInfo(name = "scroll_count") val scrollCount: Int,
    /** JSON blob for extra fields that don't warrant columns. Kept minimal — no text content. */
    @ColumnInfo(name = "extras_json") val extrasJson: String?,

    // Stage 8 canonical sync envelope columns (Schema 1.1.0)
    @ColumnInfo(name = "event_id") val eventId: String? = null,
    @ColumnInfo(name = "seq", defaultValue = "0") val seq: Long = 0,
    @ColumnInfo(name = "device_id", defaultValue = "'phone'") val deviceId: String = "phone",
    @ColumnInfo(name = "settled_status", defaultValue = "'valid'") val settledStatus: String = "valid",
    @ColumnInfo(name = "schema_version", defaultValue = "'1.1.0'") val schemaVersion: String = "1.1.0"
)

package com.dhyan.app.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A notification that has been held (snoozed or digested) by the actuator.
 *
 * PRIVACY CONTRACT:
 *  - [ciphertext] stores AES-256-GCM encrypted bytes (title + text joined by '\n').
 *    It is the ONLY place notification text exists in persistent storage.
 *  - All other columns contain hashes, enums, and timestamps — never raw text.
 *  - The [iv] column holds the 12-byte random GCM IV (Base64).
 *
 * State machine:
 *  PENDING  →  HELD / SNOOZED  →  RELEASED
 *                                  EXPIRED
 *                                  FAILED_OPEN
 *                                  ACTUATION_UNKNOWN
 */
@Entity(
    tableName = "held_notifs",
    indices = [
        Index(value = ["state", "hold_until_ms"]),
        Index(value = ["keyHash"])
    ]
)
data class HeldNotifEntity(
    /** Synthetic key: keyHash from NotificationFacts. */
    @PrimaryKey @ColumnInfo(name = "keyHash") val keyHash: String,

    /** Mechanism: "SNOOZE" | "CANCEL_DIGEST" */
    @ColumnInfo(name = "mechanism") val mechanism: String,

    /** Current state: PENDING | HELD | SNOOZED | RELEASED | EXPIRED | FAILED_OPEN | ACTUATION_UNKNOWN */
    @ColumnInfo(name = "state") val state: String,

    @ColumnInfo(name = "app_cat") val appCat: String,
    @ColumnInfo(name = "notif_cat") val notifCat: String,
    @ColumnInfo(name = "sender_hash") val senderHash: String?,

    /** When the notification was posted (epoch ms). */
    @ColumnInfo(name = "posted_ts_ms") val postedTsMs: Long,

    /** When the row was inserted (epoch ms). */
    @ColumnInfo(name = "held_since_ms") val heldSinceMs: Long,

    /** When the held notification should be released (epoch ms). 0 = no auto-release. */
    @ColumnInfo(name = "hold_until_ms") val holdUntilMs: Long,

    /** When the row expires and can be cleaned up (epoch ms). */
    @ColumnInfo(name = "expires_at_ms") val expiresAtMs: Long,

    /** AES-256-GCM ciphertext of title+"\n"+text, Base64-encoded. Null if text unavailable. */
    @ColumnInfo(name = "ciphertext") val ciphertext: String?,

    /** 12-byte GCM IV used for encryption, Base64-encoded. */
    @ColumnInfo(name = "iv") val iv: String?,

    /** Original platform notification ID (for snoozeNotification). */
    @ColumnInfo(name = "platform_id") val platformId: Int,

    /** Package name hash — used for loop guard check, never used to re-derive the package. */
    @ColumnInfo(name = "pkg_hash") val pkgHash: String,

    /** Decision action that caused this hold: DELAY | DIGEST | SILENT. */
    @ColumnInfo(name = "decision_action") val decisionAction: String,

    /** propensity from the DecisionRecord. */
    @ColumnInfo(name = "propensity") val propensity: Double,

    /** Whether the user explicitly overrode (Show-now). */
    @ColumnInfo(name = "user_override") val userOverride: Boolean = false
)

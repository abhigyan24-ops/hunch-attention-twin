package com.dhyan.app.actuator

/**
 * All Stage 6 constants in one place.
 * Change here, compile, done — no magic numbers scattered around.
 */
object ActuatorConfig {
    /** Timeout to confirm a platform cancel/snooze call (ms). */
    const val CONFIRM_TIMEOUT_MS: Long = 3_000L

    /** Max time budget for the full decide + recheck + actuate sequence (ms). */
    const val DECISION_TIMEOUT_MS: Long = 250L

    /** WorkManager sweep interval (minutes). */
    const val SWEEP_INTERVAL_MIN: Long = 15L

    /** If a row stays in PENDING longer than this, the startup sweep treats it as FAILED_OPEN (ms). */
    const val PENDING_RECOVERY_MS: Long = 60_000L

    /** Retained release records (loop guard) window (ms). 24 hours. */
    const val LOOP_GUARD_WINDOW_MS: Long = 24L * 60L * 60L * 1_000L

    /** Days to keep held_notifs rows before expiry cleanup. */
    const val RETENTION_DAYS: Long = 7L

    /** AndroidKeyStore alias for AES-256-GCM encryption of held notification text. */
    const val KEYSTORE_ALIAS: String = "dhyan_held_text"

    /** Notification channel IDs. */
    const val CHANNEL_DIGEST: String = "dhyan_digest"
    const val CHANNEL_FOREGROUND: String = "dhyan_service"

    /** WorkManager tags. */
    const val WORK_TAG_RELEASE: String = "dhyan_release"
    const val WORK_TAG_SWEEP: String = "dhyan_sweep"
    const val WORK_NAME_SWEEP: String = "dhyan_periodic_sweep"

    /** Notification IDs. */
    const val NOTIF_ID_DIGEST: Int = 9_001
    const val NOTIF_ID_FOREGROUND: Int = 9_000

    /** Snooze duration for messaging apps that don't get cancelled (ms). Default 30 min. */
    const val DEFAULT_SNOOZE_MS: Long = 30L * 60L * 1_000L

    /** If true, DRY_RUN logs but never calls cancel/snooze. LIVE respects live-scope only. */
    const val DRY_RUN_ZERO_GATEWAY: Boolean = true

    /** Maximum number of items shown in the digest notification at once. */
    const val DIGEST_MAX_ITEMS: Int = 5
}

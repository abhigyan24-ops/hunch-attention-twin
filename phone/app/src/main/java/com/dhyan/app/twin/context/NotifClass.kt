package com.dhyan.app.twin.context

/**
 * Urgency band and repeat band classification for notifications (S3-6, S3-7).
 *
 * These are components of the notification class key used in context bucketing.
 */
object NotifClass {

    /** S3-6: urgencyBand thresholds 0.33/0.66. */
    fun urgencyBand(urgencyScore: Double): String = when {
        urgencyScore < 0.33 -> "low"
        urgencyScore < 0.66 -> "med"
        else                -> "high"
    }

    /**
     * S3-7: repeatBand.
     *   R0 = 0 or 1 recent messages in the last 5 minutes.
     *   R2 = exactly 2.
     *   R3 = 3 or more.
     */
    fun repeatBand(repeatCount5m: Int): String = when {
        repeatCount5m <= 1 -> "R0"
        repeatCount5m == 2 -> "R2"
        else               -> "R3"
    }
}

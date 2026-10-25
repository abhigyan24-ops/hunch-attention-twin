package com.dhyan.app.twin.model

/**
 * Input describing a notification arriving for the twin to evaluate.
 *
 * PRIVACY: no raw text, titles, or URLs. Only categorized metadata.
 *
 * @param appCat         App category (from categories.json).
 * @param notifCat       Notification category from the OS (e.g. "promo", "msg", "social").
 * @param isVip          Whether the sender is on the VIP/never-hold list.
 * @param urgencyScore   Urgency in [0,1] from the importance model heuristic.
 * @param repeatCount5m  How many notifications from this sender arrived in the last 5 minutes.
 */
data class NotificationInput(
    val appCat: String,
    val notifCat: String?,
    val isVip: Boolean,
    val urgencyScore: Double,
    val repeatCount5m: Int
)

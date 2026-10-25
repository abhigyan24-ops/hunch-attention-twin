package com.dhyan.app.twin.decision

/** No notification content. Text-derived flags are ephemeral Stage 6 inputs, never logged. */
data class NotificationFacts(
    val keyHash: String, val postedTsMs: Long, val pkg: String, val appCat: String,
    val notifCat: String, val senderHash: String?, val isVip: Boolean,
    val urgencyScore: Double, val hasReplyAction: Boolean, val isMessagingStyle: Boolean,
    val repeatCount5m: Int, val isOtpLike: Boolean, val hasEmergencyKeyword: Boolean
)

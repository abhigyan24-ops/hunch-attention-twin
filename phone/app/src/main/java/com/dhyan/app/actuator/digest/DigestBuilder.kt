package com.dhyan.app.actuator.digest

import com.dhyan.app.data.HeldNotifEntity

/**
 * Builds the content for the Dhyan digest notification.
 * Pure function — no I/O, no Android dependencies.
 */
object DigestBuilder {

    data class DigestContent(
        val title: String,
        val summaryLine: String,
        /** Up to [ActuatorConfig.DIGEST_MAX_ITEMS] items. */
        val items: List<DigestItem>
    )

    data class DigestItem(
        val keyHash: String,
        val appCat: String,
        val notifCat: String,
        /** Formatted relative time, e.g. "3m ago". */
        val heldAgo: String,
        val decisionAction: String
    )

    fun build(held: List<HeldNotifEntity>, nowMs: Long): DigestContent {
        val maxItems = com.dhyan.app.actuator.ActuatorConfig.DIGEST_MAX_ITEMS
        val items = held.take(maxItems).map { e ->
            DigestItem(
                keyHash = e.keyHash,
                appCat = e.appCat,
                notifCat = e.notifCat,
                heldAgo = formatAgo(nowMs - e.heldSinceMs),
                decisionAction = e.decisionAction
            )
        }
        val total = held.size
        val title = if (total == 1) "1 notification held" else "$total notifications held"
        val summary = items.joinToString(", ") { it.appCat }.ifEmpty { "Dhyan digest" }
        return DigestContent(title = title, summaryLine = summary, items = items)
    }

    private fun formatAgo(elapsedMs: Long): String {
        val s = elapsedMs / 1_000
        return when {
            s < 60 -> "${s}s ago"
            s < 3600 -> "${s / 60}m ago"
            else -> "${s / 3600}h ago"
        }
    }
}

package com.dhyan.app.actuator.digest

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.dhyan.app.actuator.ActuatorConfig

/** Posts the grouped Hunch digest notification. */
interface DigestPoster {
    fun post(content: DigestBuilder.DigestContent)
    fun cancel()
}

/**
 * Android implementation.
 *
 * Channel: [ActuatorConfig.CHANNEL_DIGEST]
 *  - Importance: LOW (no sound, no heads-up)
 *  - Visibility: PRIVATE with a public version showing count only
 *
 * Actions: Show now (via broadcast), Important, Noise, Pause Dhyan.
 */
class AndroidDigestPoster(private val context: Context) : DigestPoster {

    init { createChannel() }

    private fun createChannel() {
        val nm = context.getSystemService(NotificationManager::class.java)
        val ch = NotificationChannel(
            ActuatorConfig.CHANNEL_DIGEST,
            context.getString(R.string.digest_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = context.getString(R.string.digest_channel_desc)
            setShowBadge(true)
            lockscreenVisibility = Notification.VISIBILITY_PRIVATE
        }
        nm.createNotificationChannel(ch)
    }

    override fun post(content: DigestBuilder.DigestContent) {
        val nm = context.getSystemService(NotificationManager::class.java)

        // Public version shown on lock screen — count only, no details
        val publicVersion = NotificationCompat.Builder(context, ActuatorConfig.CHANNEL_DIGEST)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(content.title)
            .setContentText("Unlock to see details")
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()

        // Main notification (VISIBILITY_PRIVATE)
        val builder = NotificationCompat.Builder(context, ActuatorConfig.CHANNEL_DIGEST)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(content.title)
            .setContentText(content.summaryLine)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(publicVersion)
            .setOngoing(false)
            .setAutoCancel(false)
            .setGroup(ActuatorConfig.CHANNEL_DIGEST)

        // Inbox style for items
        if (content.items.isNotEmpty()) {
            val style = NotificationCompat.InboxStyle()
            content.items.forEach { item ->
                style.addLine("${item.appCat} · ${item.heldAgo}")
            }
            style.setSummaryText(content.title)
            builder.setStyle(style)
        }

        // Action: Show now (opens app)
        val openIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        val openPi = PendingIntent.getActivity(
            context, 0, openIntent ?: Intent(),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        builder.addAction(0, "Show now", openPi)

        nm.notify(ActuatorConfig.NOTIF_ID_DIGEST, builder.build())
    }

    override fun cancel() {
        context.getSystemService(NotificationManager::class.java)
            .cancel(ActuatorConfig.NOTIF_ID_DIGEST)
    }
}

/** No-op fake for JVM tests. */
class FakeDigestPoster : DigestPoster {
    val posts = mutableListOf<DigestBuilder.DigestContent>()
    var cancelled = false
    override fun post(content: DigestBuilder.DigestContent) { posts.add(content) }
    override fun cancel() { cancelled = true }
}

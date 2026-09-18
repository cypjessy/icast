package com.prc.app.push

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.prc.app.MainActivity
import com.prc.app.R

/**
 * Receives FCM push messages and shows them as real system notifications
 * (lock screen / status bar / heads-up), not just in the in-app bell.
 */
class PushMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        PushTokenStore.register(token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val title = message.notification?.title
            ?: message.data["title"] ?: "PRC Jobs"
        val body = message.notification?.body
            ?: message.data["body"] ?: ""
        val channel = when (message.data["type"]) {
            "accepted", "rejected" -> CHANNEL_DECISIONS
            "applicant" -> CHANNEL_APPLICANTS
            "boost", "subscription" -> CHANNEL_PAYMENTS
            else -> CHANNEL_GENERAL
        }
        show(title, body, channel)
    }

    private fun show(title: String, body: String, channel: String) {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(channel, channelName(channel), NotificationManager.IMPORTANCE_HIGH)
            )
        }
        val intent = Intent(this, MainActivity::class.java)
            .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pi = PendingIntent.getActivity(
            this, title.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notif = NotificationCompat.Builder(this, channel)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setContentIntent(pi)
            .build()
        try {
            NotificationManagerCompat.from(this).notify(title.hashCode(), notif)
        } catch (_: SecurityException) {
            // POST_NOTIFICATIONS denied — the bell still shows everything.
        }
    }

    private fun channelName(channel: String) = when (channel) {
        CHANNEL_DECISIONS -> "Application decisions"
        CHANNEL_APPLICANTS -> "New applicants"
        CHANNEL_PAYMENTS -> "Payments & boosts"
        else -> "General"
    }

    companion object {
        const val CHANNEL_GENERAL = "prc_general"
        const val CHANNEL_DECISIONS = "prc_decisions"
        const val CHANNEL_APPLICANTS = "prc_applicants"
        const val CHANNEL_PAYMENTS = "prc_payments"
    }
}

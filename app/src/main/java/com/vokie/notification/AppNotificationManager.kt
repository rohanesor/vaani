package com.vokie.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.vokie.MainActivity
import com.vokie.R

class AppNotificationManager(private val context: Context) {

    companion object {
        const val CHANNEL_ID = "itantra_messages"
        const val CHANNEL_NAME = "iTantra Messages"
        const val CHANNEL_DESC = "Notifications for incoming iTantra messages"
        const val EXTRA_PEER_ID = "extra_peer_id"
    }

    private val notificationManager: NotificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = CHANNEL_DESC
                enableLights(true)
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun showIncomingMessageNotification(
        senderId: String,
        senderName: String,
        text: String,
        isVoice: Boolean = false,
        isUrgent: Boolean = false,
    ) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_PEER_ID, senderId)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            senderId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = if (isUrgent) "🚨 Urgent: $senderName" else senderName
        val content = if (isVoice) "🎤 Voice note: $text" else text

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(content)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content))
            .setPriority(if (isUrgent) NotificationCompat.PRIORITY_MAX else NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(senderId.hashCode(), notification)
        } catch (e: SecurityException) {
            // Permission not granted yet
        }
    }

    fun cancelNotificationForPeer(peerId: String) {
        try {
            notificationManager.cancel(peerId.hashCode())
        } catch (_: Throwable) {}
    }

    fun cancelAll() {
        try {
            notificationManager.cancelAll()
        } catch (_: Throwable) {}
    }
}

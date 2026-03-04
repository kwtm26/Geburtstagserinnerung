package com.graphene.geburtstagfinal

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

object BirthdayNotificationHelper {

    fun sendBirthdayNotification(context: Context, names: List<String>) {

        if (names.isEmpty()) return

        val title = if (names.size == 1) {
            "Heute hat Geburtstag:"
        } else {
            "Heute haben Geburtstag:"
        }

        val contentText = names.joinToString(", ")

        val channelId = "birthday_channel"

        // Channel erstellen (ab Android 8)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Geburtstage",
                NotificationManager.IMPORTANCE_HIGH
            )
            val manager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_stat_cake)
            .setContentTitle(title)
            .setContentText(contentText)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)

        val managerCompat = NotificationManagerCompat.from(context)
        managerCompat.notify(1, builder.build())
    }
    fun sendCustomNotification(
        context: Context,
        title: String,
        text: String,
        notificationId: Int
    ) {
        val channelId = "birthday_channel"

        // Channel sicherstellen
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Geburtstagserinnerungen",
                NotificationManager.IMPORTANCE_HIGH
            )
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_stat_cake)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.InboxStyle().also { style -> text.split("\n").filter { it.isNotBlank() }.forEach { style.addLine(it) } })
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)

        NotificationManagerCompat.from(context)
            .notify(notificationId, builder.build())
    }
}

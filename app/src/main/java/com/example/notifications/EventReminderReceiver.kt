package com.example.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R

class EventReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val eventId = intent.getStringExtra(EXTRA_EVENT_ID) ?: return
        val title = intent.getStringExtra(EXTRA_EVENT_TITLE) ?: "Upcoming Event"
        val timeLabel = intent.getStringExtra(EXTRA_EVENT_TIME) ?: ""
        val location = intent.getStringExtra(EXTRA_EVENT_LOCATION).orEmpty()

        showReminderNotification(
            context = context,
            eventId = eventId,
            title = title,
            timeLabel = timeLabel,
            location = location,
        )
    }

    companion object {
        const val CHANNEL_ID = "daymark_event_reminders_v2"
        const val EXTRA_EVENT_ID = "extra_event_id"
        const val EXTRA_EVENT_TITLE = "extra_event_title"
        const val EXTRA_EVENT_TIME = "extra_event_time"
        const val EXTRA_EVENT_LOCATION = "extra_event_location"

        fun showReminderNotification(
            context: Context,
            eventId: String,
            title: String,
            timeLabel: String,
            location: String = "",
        ) {
            ensureChannel(context)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                return
            }

            val launchIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val contentPendingIntent = PendingIntent.getActivity(
                context,
                eventId.hashCode(),
                launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val contentText = buildString {
                if (timeLabel.isNotBlank()) append(timeLabel) else append("Scheduled on Daymark")
                if (location.isNotBlank()) {
                    append(" • ")
                    append(location)
                }
            }

            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification_calendar)
                .setColor(0xFFD84E31.toInt())
                .setContentTitle(title)
                .setContentText(contentText)
                .setStyle(NotificationCompat.BigTextStyle().bigText(contentText))
                .setCategory(NotificationCompat.CATEGORY_EVENT)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setAutoCancel(true)
                .setContentIntent(contentPendingIntent)
                .build()

            NotificationManagerCompat.from(context).notify(eventId.hashCode(), notification)
        }

        fun ensureChannel(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                    ?: return
                if (manager.getNotificationChannel(CHANNEL_ID) == null) {
                    val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                    val audioAttrs = AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()

                    val channel = NotificationChannel(
                        CHANNEL_ID,
                        "Event Reminders",
                        NotificationManager.IMPORTANCE_HIGH
                    ).apply {
                        description = "Exact alerts and reminders for upcoming calendar events"
                        enableLights(true)
                        enableVibration(true)
                        setSound(soundUri, audioAttrs)
                        lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
                    }
                    manager.createNotificationChannel(channel)
                }
            }
        }
    }
}

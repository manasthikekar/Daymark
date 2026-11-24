package com.example.notifications

import android.Manifest
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.model.CalendarEvent
import com.example.model.ReminderOption
import com.example.util.CalendarDate
import org.json.JSONObject

enum class ReminderScheduleOutcome {
    SCHEDULED_EXACT,
    SCHEDULED_INEXACT,
    NOT_NEEDED,
    PAST_TIME_SKIPPED,
    PERMISSION_MISSING,
}

object ReminderScheduler {
    private const val TAG = "ReminderScheduler"
    private const val PREFS_NAME = "daymark_scheduled_reminders"

    fun hasNotificationPermission(context: Context): Boolean {
        val runtimeGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
        return runtimeGranted && NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    fun canScheduleExactAlarms(context: Context): Boolean {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }
    }

    fun scheduleReminder(
        context: Context,
        eventId: String,
        title: String,
        location: String?,
        dateEpochDay: Long,
        startMinute: Long,
        endMinute: Long,
        allDay: Boolean,
        reminderOption: ReminderOption,
    ): ReminderScheduleOutcome {
        cancelReminder(context, eventId)
        if (reminderOption == ReminderOption.NONE) return ReminderScheduleOutcome.NOT_NEEDED

        val now = System.currentTimeMillis()
        val date = CalendarDate.fromEpochDay(dateEpochDay)
        val eventStartMinute = if (allDay) 9 * 60 else startMinute.toInt()
        val eventStartMillis = date.toLocalTriggerMillis(eventStartMinute)
        val rawTriggerMillis = eventStartMillis - reminderOption.minutesBefore * 60_000L

        if (eventStartMillis <= now) {
            return ReminderScheduleOutcome.PAST_TIME_SKIPPED
        }

        val triggerMillis = if (rawTriggerMillis <= now) now + 1_500L else rawTriggerMillis
        val timeLabel = CalendarDate.formatTimeRange(startMinute, endMinute, allDay)
        val safeLocation = location?.trim().orEmpty()

        saveReminderRecord(
            context = context,
            eventId = eventId,
            title = title,
            timeLabel = timeLabel,
            location = safeLocation,
            triggerMillis = triggerMillis,
        )

        val outcome = scheduleAlarmInternal(
            context = context,
            eventId = eventId,
            title = title,
            timeLabel = timeLabel,
            location = safeLocation,
            triggerMillis = triggerMillis,
        )

        return if (!hasNotificationPermission(context)) {
            ReminderScheduleOutcome.PERMISSION_MISSING
        } else {
            outcome
        }
    }

    fun cancelReminder(context: Context, eventId: String) {
        removeReminderRecord(context, eventId)
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = buildReceiverIntent(context, eventId, "", "", "")
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            eventId.hashCode(),
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
        }
    }

    fun syncAllUpcomingReminders(context: Context, events: List<CalendarEvent>) {
        val activeIds = events.filter { it.reminderOption() != ReminderOption.NONE }.map { it.id }.toSet()
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val storedIds = prefs.all.keys.toList()
        storedIds.forEach { storedId ->
            if (storedId !in activeIds) {
                cancelReminder(context, storedId)
            }
        }
        events.forEach { event ->
            val option = event.reminderOption()
            if (option != ReminderOption.NONE && event.id.isNotBlank()) {
                scheduleReminder(
                    context = context,
                    eventId = event.id,
                    title = event.title,
                    location = event.location,
                    dateEpochDay = event.dateEpochDay,
                    startMinute = event.startMinute,
                    endMinute = event.endMinute,
                    allDay = event.allDay,
                    reminderOption = option,
                )
            }
        }
    }

    fun reschedulePersistedReminders(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val staleKeys = mutableListOf<String>()

        prefs.all.forEach { (eventId, rawValue) ->
            val rawJson = rawValue as? String ?: return@forEach
            try {
                val json = JSONObject(rawJson)
                val triggerMillis = json.getLong("triggerMillis")
                if (triggerMillis <= now) {
                    staleKeys.add(eventId)
                } else {
                    scheduleAlarmInternal(
                        context = context,
                        eventId = eventId,
                        title = json.optString("title", "Upcoming Event"),
                        timeLabel = json.optString("timeLabel", ""),
                        location = json.optString("location", ""),
                        triggerMillis = triggerMillis,
                    )
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to restore reminder $eventId: ${e.message}")
                staleKeys.add(eventId)
            }
        }

        if (staleKeys.isNotEmpty()) {
            prefs.edit().apply {
                staleKeys.forEach { remove(it) }
            }.apply()
        }
    }

    fun sendTestNotification(context: Context): Boolean {
        if (!hasNotificationPermission(context)) return false
        EventReminderReceiver.showReminderNotification(
            context = context,
            eventId = "daymark_test_notification",
            title = "Daymark Reminders Active",
            timeLabel = "Your calendar alerts are working properly",
            location = "Mobile Device",
        )
        return true
    }

    private fun scheduleAlarmInternal(
        context: Context,
        eventId: String,
        title: String,
        timeLabel: String,
        location: String,
        triggerMillis: Long,
    ): ReminderScheduleOutcome {
        EventReminderReceiver.ensureChannel(context)
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
            ?: return ReminderScheduleOutcome.SCHEDULED_INEXACT

        val intent = buildReceiverIntent(context, eventId, title, timeLabel, location)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            eventId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val exactAllowed = canScheduleExactAlarms(context)
        return try {
            if (exactAllowed) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerMillis,
                        pendingIntent
                    )
                } else {
                    alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerMillis, pendingIntent)
                }
                ReminderScheduleOutcome.SCHEDULED_EXACT
            } else {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerMillis,
                        pendingIntent
                    )
                } else {
                    alarmManager.set(AlarmManager.RTC_WAKEUP, triggerMillis, pendingIntent)
                }
                ReminderScheduleOutcome.SCHEDULED_INEXACT
            }
        } catch (se: SecurityException) {
            Log.w(TAG, "Exact alarm rejected by system, falling back to inexact: ${se.message}")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMillis, pendingIntent)
            } else {
                alarmManager.set(AlarmManager.RTC_WAKEUP, triggerMillis, pendingIntent)
            }
            ReminderScheduleOutcome.SCHEDULED_INEXACT
        }
    }

    private fun buildReceiverIntent(
        context: Context,
        eventId: String,
        title: String,
        timeLabel: String,
        location: String,
    ): Intent {
        return Intent(context, EventReminderReceiver::class.java).apply {
            data = Uri.parse("daymark://reminder/$eventId")
            putExtra(EventReminderReceiver.EXTRA_EVENT_ID, eventId)
            putExtra(EventReminderReceiver.EXTRA_EVENT_TITLE, title)
            putExtra(EventReminderReceiver.EXTRA_EVENT_TIME, timeLabel)
            putExtra(EventReminderReceiver.EXTRA_EVENT_LOCATION, location)
        }
    }

    private fun saveReminderRecord(
        context: Context,
        eventId: String,
        title: String,
        timeLabel: String,
        location: String,
        triggerMillis: Long,
    ) {
        val json = JSONObject().apply {
            put("title", title)
            put("timeLabel", timeLabel)
            put("location", location)
            put("triggerMillis", triggerMillis)
        }.toString()
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(eventId, json)
            .apply()
    }

    private fun removeReminderRecord(context: Context, eventId: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(eventId)
            .apply()
    }
}

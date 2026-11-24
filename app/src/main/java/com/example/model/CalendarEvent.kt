package com.example.model

import androidx.annotation.Keep
import androidx.compose.ui.graphics.Color
import com.example.ui.theme.CategoryFocusDark
import com.example.ui.theme.CategoryFocusLight
import com.example.ui.theme.CategoryHealthDark
import com.example.ui.theme.CategoryHealthLight
import com.example.ui.theme.CategoryPersonalDark
import com.example.ui.theme.CategoryPersonalLight
import com.example.ui.theme.CategorySocialDark
import com.example.ui.theme.CategorySocialLight
import com.example.ui.theme.CategoryWorkDark
import com.example.ui.theme.CategoryWorkLight
import com.example.util.CalendarDate
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class EventCategory(
    val displayName: String,
    val lightColor: Color,
    val darkColor: Color,
) {
    WORK("Work", CategoryWorkLight, CategoryWorkDark),
    PERSONAL("Personal", CategoryPersonalLight, CategoryPersonalDark),
    HEALTH("Health", CategoryHealthLight, CategoryHealthDark),
    SOCIAL("Social", CategorySocialLight, CategorySocialDark),
    FOCUS("Focus", CategoryFocusLight, CategoryFocusDark);

    fun color(isDarkTheme: Boolean): Color = if (isDarkTheme) darkColor else lightColor

    companion object {
        fun fromNameSafe(raw: String): EventCategory =
            entries.firstOrNull { it.name == raw } ?: PERSONAL
    }
}

enum class ReminderOption(val minutesBefore: Long, val label: String) {
    NONE(-1L, "No reminder"),
    AT_TIME(0L, "At time of event"),
    TEN_MIN(10L, "10 minutes before"),
    THIRTY_MIN(30L, "30 minutes before"),
    ONE_HOUR(60L, "1 hour before");

    companion object {
        fun fromMinutes(minutes: Long): ReminderOption =
            entries.firstOrNull { it.minutesBefore == minutes } ?: NONE
    }
}

@Keep
@IgnoreExtraProperties
data class CalendarEvent(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "",
    val notes: String? = null,
    val location: String? = null,
    val dateEpochDay: Long = 0L,
    val startMinute: Long = 540L,
    val endMinute: Long = 600L,
    val allDay: Boolean = false,
    val category: String = EventCategory.PERSONAL.name,
    val reminderMinutes: Long = ReminderOption.NONE.minutesBefore,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
) {
    fun eventCategory(): EventCategory = EventCategory.fromNameSafe(category)

    fun reminderOption(): ReminderOption = ReminderOption.fromMinutes(reminderMinutes)

    fun date(): CalendarDate = CalendarDate.fromEpochDay(dateEpochDay)
}

package com.example.util

import java.util.Calendar
import java.util.Locale
import kotlin.math.floor

/**
 * API-24-safe Gregorian calendar date backed by exact proleptic Gregorian epoch-day math
 * matching java.time.LocalDate.toEpochDay() without requiring API 26 or desugaring.
 */
data class CalendarDate(
    val year: Int,
    val month: Int, // 1..12
    val day: Int,   // 1..31
) : Comparable<CalendarDate> {

    override fun compareTo(other: CalendarDate): Int =
        compareValuesBy(this, other, { it.year }, { it.month }, { it.day })

    /**
     * Exact epoch day (days since 1970-01-01) using Howard Hinnant's days_from_civil algorithm.
     */
    fun toEpochDay(): Long {
        val y = year.toLong() - if (month <= 2) 1L else 0L
        val era = (if (y >= 0L) y else y - 399L) / 400L
        val yoe = y - era * 400L
        val m = month.toLong()
        val doy = (153L * (m + if (m > 2L) -3L else 9L) + 2L) / 5L + day.toLong() - 1L
        val doe = yoe * 365L + yoe / 4L - yoe / 100L + doy
        return era * 146097L + doe - 719468L
    }

    /**
     * Day of week: 1 = Monday .. 7 = Sunday (ISO-8601 convention).
     */
    fun dayOfWeekIso(): Int {
        // 1970-01-01 (epochDay = 0) was Thursday (4)
        return ((toEpochDay() + 3L).mod(7L)).toInt() + 1
    }

    /**
     * Day of week index 0..6 where 0 is Sunday (or Monday if [startOnMonday] is true).
     */
    fun dayOfWeekIndex(startOnMonday: Boolean): Int {
        val iso = dayOfWeekIso() // 1=Mon .. 7=Sun
        return if (startOnMonday) {
            iso - 1
        } else {
            iso % 7 // Sun=0, Mon=1 .. Sat=6
        }
    }

    fun plusDays(days: Long): CalendarDate = fromEpochDay(toEpochDay() + days)

    fun minusDays(days: Long): CalendarDate = fromEpochDay(toEpochDay() - days)

    fun plusMonths(months: Int): CalendarDate {
        val totalMonths = year * 12 + (month - 1) + months
        val newYear = floor(totalMonths / 12.0).toInt()
        val newMonth = totalMonths.mod(12) + 1
        val maxDay = lengthOfMonth(newYear, newMonth)
        return CalendarDate(newYear, newMonth, minOf(day, maxDay))
    }

    fun withDayOfMonth(newDay: Int): CalendarDate {
        val maxDay = lengthOfMonth(year, month)
        return copy(day = newDay.coerceIn(1, maxDay))
    }

    fun startOfWeek(startOnMonday: Boolean): CalendarDate {
        val offset = dayOfWeekIndex(startOnMonday)
        return minusDays(offset.toLong())
    }

    fun toLocalTriggerMillis(minuteOfDay: Int): Long {
        val cal = Calendar.getInstance().apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month - 1)
            set(Calendar.DAY_OF_MONTH, day)
            set(Calendar.HOUR_OF_DAY, (minuteOfDay / 60).coerceIn(0, 23))
            set(Calendar.MINUTE, (minuteOfDay % 60).coerceIn(0, 59))
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return cal.timeInMillis
    }

    fun monthName(): String = MONTH_NAMES.getOrElse(month - 1) { "" }

    fun shortMonthName(): String = SHORT_MONTH_NAMES.getOrElse(month - 1) { "" }

    fun weekdayFullName(): String = WEEKDAY_FULL_NAMES[dayOfWeekIso() - 1]

    fun weekdayShortName(): String = WEEKDAY_SHORT_NAMES_MON[dayOfWeekIso() - 1]

    fun formatHeaderDate(): String = "${weekdayFullName()}, ${shortMonthName()} $day"

    fun formatFullDate(): String = "${weekdayFullName()}, ${monthName()} $day, $year"

    fun formatCompactDate(): String = "${shortMonthName()} $day, $year"

    companion object {
        private val MONTH_NAMES = listOf(
            "January", "February", "March", "April", "May", "June",
            "July", "August", "September", "October", "November", "December"
        )
        private val SHORT_MONTH_NAMES = listOf(
            "Jan", "Feb", "Mar", "Apr", "May", "Jun",
            "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"
        )
        private val WEEKDAY_FULL_NAMES = listOf(
            "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"
        )
        private val WEEKDAY_SHORT_NAMES_MON = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
        private val WEEKDAY_NARROW_MON = listOf("M", "T", "W", "T", "F", "S", "S")
        private val WEEKDAY_NARROW_SUN = listOf("S", "M", "T", "W", "T", "F", "S")
        private val WEEKDAY_SHORT_SUN = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")

        fun weekdayNarrowHeaders(startOnMonday: Boolean): List<String> =
            if (startOnMonday) WEEKDAY_NARROW_MON else WEEKDAY_NARROW_SUN

        fun weekdayShortHeaders(startOnMonday: Boolean): List<String> =
            if (startOnMonday) WEEKDAY_SHORT_NAMES_MON else WEEKDAY_SHORT_SUN

        fun today(): CalendarDate {
            val cal = Calendar.getInstance()
            return CalendarDate(
                year = cal.get(Calendar.YEAR),
                month = cal.get(Calendar.MONTH) + 1,
                day = cal.get(Calendar.DAY_OF_MONTH),
            )
        }

        fun currentMinuteOfDay(): Int {
            val cal = Calendar.getInstance()
            return cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        }

        fun isLeapYear(year: Int): Boolean =
            (year % 4 == 0 && year % 100 != 0) || (year % 400 == 0)

        fun lengthOfMonth(year: Int, month: Int): Int = when (month) {
            1, 3, 5, 7, 8, 10, 12 -> 31
            4, 6, 9, 11 -> 30
            2 -> if (isLeapYear(year)) 29 else 28
            else -> 30
        }

        fun fromEpochDay(epochDay: Long): CalendarDate {
            val z = epochDay + 719468L
            val era = (if (z >= 0L) z else z - 146096L) / 146097L
            val doe = z - era * 146097L
            val yoe = (doe - doe / 1460L + doe / 36524L - doe / 146096L) / 365L
            val y = yoe + era * 400L
            val doy = doe - (365L * yoe + yoe / 4L - yoe / 100L)
            val mp = (5L * doy + 2L) / 153L
            val d = (doy - (153L * mp + 2L) / 5L + 1L).toInt()
            val m = (mp + if (mp < 10L) 3L else -9L).toInt()
            val finalYear = (y + if (m <= 2) 1L else 0L).toInt()
            return CalendarDate(finalYear, m, d)
        }

        fun fromUtcMillis(utcMillis: Long): CalendarDate {
            val epochDay = floor(utcMillis / 86_400_000.0).toLong()
            return fromEpochDay(epochDay)
        }

        fun toUtcMillis(date: CalendarDate): Long = date.toEpochDay() * 86_400_000L

        fun formatMinute(minuteOfDay: Int): String {
            val clamped = minuteOfDay.coerceIn(0, 1440)
            if (clamped == 1440) return "12:00 AM"
            val hour24 = clamped / 60
            val minute = clamped % 60
            val amPm = if (hour24 < 12) "AM" else "PM"
            val hour12 = when {
                hour24 == 0 -> 12
                hour24 > 12 -> hour24 - 12
                else -> hour24
            }
            return String.format(Locale.US, "%d:%02d %s", hour12, minute, amPm)
        }

        fun formatShortHour(hour24: Int): String {
            val h = hour24.coerceIn(0, 23)
            val amPm = if (h < 12) "AM" else "PM"
            val h12 = when {
                h == 0 -> 12
                h > 12 -> h - 12
                else -> h
            }
            return "$h12 $amPm"
        }

        fun formatTimeRange(startMinute: Long, endMinute: Long, allDay: Boolean): String {
            if (allDay) return "All day"
            return "${formatMinute(startMinute.toInt())} – ${formatMinute(endMinute.toInt())}"
        }
    }
}

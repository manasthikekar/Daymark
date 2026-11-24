package com.example

import com.example.util.CalendarDate
import org.junit.Assert.assertEquals
import org.junit.Test

class ExampleUnitTest {
    @Test
    fun epochDay_roundTrip_matchesKnownDates() {
        val epoch = CalendarDate(1970, 1, 1)
        assertEquals(0L, epoch.toEpochDay())
        assertEquals(4, epoch.dayOfWeekIso()) // Thursday
        assertEquals(epoch, CalendarDate.fromEpochDay(0L))

        val date2026 = CalendarDate(2026, 11, 23)
        val roundTrip = CalendarDate.fromEpochDay(date2026.toEpochDay())
        assertEquals(date2026, roundTrip)
        assertEquals(1, date2026.dayOfWeekIso()) // Monday
    }

    @Test
    fun leapYear_and_monthShift_clampCorrectly() {
        assertEquals(29, CalendarDate.lengthOfMonth(2024, 2))
        assertEquals(28, CalendarDate.lengthOfMonth(2026, 2))
        assertEquals(28, CalendarDate.lengthOfMonth(1900, 2))
        assertEquals(29, CalendarDate.lengthOfMonth(2000, 2))

        val jan31 = CalendarDate(2026, 1, 31)
        assertEquals(CalendarDate(2026, 2, 28), jan31.plusMonths(1))
        assertEquals(CalendarDate(2025, 12, 31), jan31.plusMonths(-1))
    }
}

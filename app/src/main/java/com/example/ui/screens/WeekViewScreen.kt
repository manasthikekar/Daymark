package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.AssistChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.CalendarEvent
import com.example.util.CalendarDate

@Composable
fun WeekViewScreen(
    selectedDate: CalendarDate,
    startWeekOnMonday: Boolean,
    events: List<CalendarEvent>,
    onSelectDate: (CalendarDate) -> Unit,
    onShiftWeek: (Int) -> Unit,
    onTodayClick: () -> Unit,
    onEventClick: (CalendarEvent) -> Unit,
    onEmptySlotClick: (CalendarDate, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val today = remember { CalendarDate.today() }
    val weekStart = remember(selectedDate, startWeekOnMonday) {
        selectedDate.startOfWeek(startWeekOnMonday)
    }
    val weekDays = remember(weekStart) {
        List(7) { offset -> weekStart.plusDays(offset.toLong()) }
    }
    val weekEnd = weekDays.last()

    val eventsByDay = remember(events) { events.groupBy { it.dateEpochDay } }
    val selectedEvents = remember(eventsByDay, selectedDate) {
        eventsByDay[selectedDate.toEpochDay()].orEmpty()
    }
    val allDayEvents = remember(selectedEvents) { selectedEvents.filter { it.allDay } }
    val timedEvents = remember(selectedEvents) { selectedEvents.filter { !it.allDay } }

    val hourHeight = 64.dp
    val startHour = 6
    val endHour = 23
    val isDark = isSystemInDarkTheme()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        // Week range header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${weekStart.shortMonthName()} ${weekStart.day} – ${weekEnd.shortMonthName()} ${weekEnd.day}",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                AssistChip(
                    onClick = onTodayClick,
                    label = {
                        Text(
                            text = stringResource(R.string.today_label),
                            style = MaterialTheme.typography.labelMedium
                        )
                    },
                    modifier = Modifier.testTag("week_today_button")
                )
                IconButton(
                    onClick = { onShiftWeek(-1) },
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("prev_week_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
                        contentDescription = stringResource(R.string.cd_prev_week)
                    )
                }
                IconButton(
                    onClick = { onShiftWeek(1) },
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("next_week_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                        contentDescription = stringResource(R.string.cd_next_week)
                    )
                }
            }
        }

        // 7-day strip
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant,
                    shape = MaterialTheme.shapes.medium
                )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp, horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                weekDays.forEach { day ->
                    val isSelected = day == selectedDate
                    val isToday = day == today
                    val hasEvents = !eventsByDay[day.toEpochDay()].isNullOrEmpty()

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier
                            .weight(1f)
                            .clip(MaterialTheme.shapes.small)
                            .background(
                                when {
                                    isSelected -> MaterialTheme.colorScheme.primary
                                    isToday -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                    else -> MaterialTheme.colorScheme.surface
                                }
                            )
                            .clickable { onSelectDate(day) }
                            .padding(vertical = 8.dp)
                            .testTag("week_strip_day_${day.day}")
                    ) {
                        Text(
                            text = day.weekdayShortName(),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.onPrimary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                        Text(
                            text = day.day.toString(),
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Medium
                            ),
                            color = when {
                                isSelected -> MaterialTheme.colorScheme.onPrimary
                                isToday -> MaterialTheme.colorScheme.primary
                                else -> MaterialTheme.colorScheme.onSurface
                            }
                        )
                        Box(
                            modifier = Modifier
                                .size(5.dp)
                                .clip(CircleShape)
                                .background(
                                    when {
                                        !hasEvents -> androidx.compose.ui.graphics.Color.Transparent
                                        isSelected -> MaterialTheme.colorScheme.onPrimary
                                        else -> MaterialTheme.colorScheme.primary
                                    }
                                )
                        )
                    }
                }
            }
        }

        // All-day events banner if any
        if (allDayEvents.isNotEmpty()) {
            Spacer(modifier = Modifier.height(10.dp))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                allDayEvents.forEach { event ->
                    val accent = event.eventCategory().color(isDark)
                    Surface(
                        onClick = { onEventClick(event) },
                        shape = MaterialTheme.shapes.small,
                        color = accent.copy(alpha = 0.16f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                width = 1.dp,
                                color = accent.copy(alpha = 0.4f),
                                shape = MaterialTheme.shapes.small
                            )
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = event.title,
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = stringResource(R.string.all_day),
                                style = MaterialTheme.typography.labelSmall,
                                color = accent
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Hourly vertical timeline
        val scrollState = rememberScrollState(initial = 120)
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(scrollState)
                .padding(bottom = 96.dp)
        ) {
            val totalHours = endHour - startHour + 1
            Column(modifier = Modifier.fillMaxWidth()) {
                for (hour in startHour..endHour) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(hourHeight)
                            .clickable { onEmptySlotClick(selectedDate, hour * 60) },
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = CalendarDate.formatShortHour(hour),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .width(54.dp)
                                .padding(top = 2.dp)
                        )
                        Box(modifier = Modifier.weight(1f)) {
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant,
                                thickness = 1.dp
                            )
                        }
                    }
                }
            }

            // Positioned timed event blocks
            val timelineHeight = hourHeight * totalHours
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(timelineHeight)
                    .padding(start = 58.dp, end = 4.dp)
            ) {
                timedEvents.forEach { event ->
                    val startMin = event.startMinute.toInt().coerceIn(startHour * 60, (endHour + 1) * 60 - 15)
                    val endMin = event.endMinute.toInt().coerceIn(startMin + 15, (endHour + 1) * 60)
                    val topOffsetDp = ((startMin - startHour * 60) / 60f) * hourHeight.value
                    val blockHeightDp = (((endMin - startMin) / 60f) * hourHeight.value).coerceAtLeast(36f)
                    val accent = event.eventCategory().color(isDark)

                    Surface(
                        onClick = { onEventClick(event) },
                        shape = MaterialTheme.shapes.small,
                        color = accent.copy(alpha = 0.16f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .offset(y = topOffsetDp.dp)
                            .height(blockHeightDp.dp)
                            .padding(vertical = 2.dp)
                            .border(
                                width = 1.dp,
                                color = accent.copy(alpha = 0.45f),
                                shape = MaterialTheme.shapes.small
                            )
                            .testTag("timeline_event_${event.id}")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(3.dp)
                                    .height(24.dp)
                                    .clip(CircleShape)
                                    .background(accent)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = event.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = CalendarDate.formatTimeRange(
                                        event.startMinute,
                                        event.endMinute,
                                        false
                                    ),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = accent,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

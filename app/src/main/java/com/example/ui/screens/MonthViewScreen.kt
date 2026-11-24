package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.CalendarEvent
import com.example.ui.components.EmptyDayCard
import com.example.ui.components.EventCard
import com.example.ui.components.MonthYearPickerDialog
import com.example.util.CalendarDate

@Composable
fun MonthViewScreen(
    displayedMonth: CalendarDate,
    selectedDate: CalendarDate,
    startWeekOnMonday: Boolean,
    events: List<CalendarEvent>,
    onSelectDate: (CalendarDate) -> Unit,
    onShiftMonth: (Int) -> Unit,
    onJumpToMonth: (Int, Int) -> Unit,
    onTodayClick: () -> Unit,
    onEventClick: (CalendarEvent) -> Unit,
    onAddEventForDate: (CalendarDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showMonthPicker by remember { mutableStateOf(false) }
    val today = remember { CalendarDate.today() }

    val eventsByEpochDay = remember(events) {
        events.groupBy { it.dateEpochDay }
    }
    val selectedDayEvents = remember(eventsByEpochDay, selectedDate) {
        eventsByEpochDay[selectedDate.toEpochDay()].orEmpty()
    }

    if (showMonthPicker) {
        MonthYearPickerDialog(
            initialMonth = displayedMonth,
            onDismiss = { showMonthPicker = false },
            onConfirm = { year, month ->
                onJumpToMonth(year, month)
                showMonthPicker = false
            }
        )
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val isWide = maxWidth >= 600.dp

        if (isWide) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                MonthGridCard(
                    displayedMonth = displayedMonth,
                    selectedDate = selectedDate,
                    today = today,
                    startWeekOnMonday = startWeekOnMonday,
                    eventsByEpochDay = eventsByEpochDay,
                    onSelectDate = onSelectDate,
                    onShiftMonth = onShiftMonth,
                    onOpenMonthPicker = { showMonthPicker = true },
                    onTodayClick = onTodayClick,
                    modifier = Modifier
                        .weight(1.15f)
                        .fillMaxHeight()
                )

                LazyColumn(
                    modifier = Modifier
                        .weight(0.85f)
                        .fillMaxHeight(),
                    contentPadding = PaddingValues(bottom = 88.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        SelectedDayHeader(
                            selectedDate = selectedDate,
                            eventCount = selectedDayEvents.size,
                            onAddClick = { onAddEventForDate(selectedDate) }
                        )
                    }
                    if (selectedDayEvents.isEmpty()) {
                        item {
                            EmptyDayCard(
                                selectedDate = selectedDate,
                                onAddEventClick = { onAddEventForDate(selectedDate) }
                            )
                        }
                    } else {
                        items(selectedDayEvents, key = { it.id }) { event ->
                            EventCard(
                                event = event,
                                onClick = { onEventClick(event) }
                            )
                        }
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item {
                    MonthGridCard(
                        displayedMonth = displayedMonth,
                        selectedDate = selectedDate,
                        today = today,
                        startWeekOnMonday = startWeekOnMonday,
                        eventsByEpochDay = eventsByEpochDay,
                        onSelectDate = onSelectDate,
                        onShiftMonth = onShiftMonth,
                        onOpenMonthPicker = { showMonthPicker = true },
                        onTodayClick = onTodayClick,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                item {
                    SelectedDayHeader(
                        selectedDate = selectedDate,
                        eventCount = selectedDayEvents.size,
                        onAddClick = { onAddEventForDate(selectedDate) }
                    )
                }

                if (selectedDayEvents.isEmpty()) {
                    item {
                        EmptyDayCard(
                            selectedDate = selectedDate,
                            onAddEventClick = { onAddEventForDate(selectedDate) }
                        )
                    }
                } else {
                    items(selectedDayEvents, key = { it.id }) { event ->
                        EventCard(
                            event = event,
                            onClick = { onEventClick(event) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MonthGridCard(
    displayedMonth: CalendarDate,
    selectedDate: CalendarDate,
    today: CalendarDate,
    startWeekOnMonday: Boolean,
    eventsByEpochDay: Map<Long, List<CalendarEvent>>,
    onSelectDate: (CalendarDate) -> Unit,
    onShiftMonth: (Int) -> Unit,
    onOpenMonthPicker: () -> Unit,
    onTodayClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val firstOfMonth = remember(displayedMonth) { displayedMonth.withDayOfMonth(1) }
    val leadingDays = firstOfMonth.dayOfWeekIndex(startWeekOnMonday)
    val gridStart = remember(firstOfMonth, leadingDays) { firstOfMonth.minusDays(leadingDays.toLong()) }
    val gridDates = remember(gridStart) {
        List(42) { offset -> gridStart.plusDays(offset.toLong()) }
    }
    val weekdayHeaders = remember(startWeekOnMonday) {
        CalendarDate.weekdayNarrowHeaders(startWeekOnMonday)
    }

    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = modifier.border(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant,
            shape = MaterialTheme.shapes.large
        )
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(MaterialTheme.shapes.small)
                        .clickable(onClick = onOpenMonthPicker)
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                        .testTag("month_picker_button")
                ) {
                    Text(
                        text = "${displayedMonth.monthName()} ${displayedMonth.year}",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Rounded.KeyboardArrowDown,
                        contentDescription = "Choose month and year",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    AssistChip(
                        onClick = onTodayClick,
                        label = {
                            Text(
                                text = stringResource(R.string.today_label),
                                style = MaterialTheme.typography.labelMedium
                            )
                        },
                        modifier = Modifier.testTag("month_today_button")
                    )
                    IconButton(
                        onClick = { onShiftMonth(-1) },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("prev_month_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
                            contentDescription = stringResource(R.string.cd_prev_month)
                        )
                    }
                    IconButton(
                        onClick = { onShiftMonth(1) },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("next_month_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                            contentDescription = stringResource(R.string.cd_next_month)
                        )
                    }
                }
            }

            Row(modifier = Modifier.fillMaxWidth()) {
                weekdayHeaders.forEach { label ->
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                for (weekIndex in 0 until 6) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        for (dayIndex in 0 until 7) {
                            val cellDate = gridDates[weekIndex * 7 + dayIndex]
                            val isCurrentMonth = cellDate.month == displayedMonth.month &&
                                cellDate.year == displayedMonth.year
                            val isToday = cellDate == today
                            val isSelected = cellDate == selectedDate
                            val dayEvents = eventsByEpochDay[cellDate.toEpochDay()].orEmpty()

                            MonthDayCell(
                                date = cellDate,
                                isCurrentMonth = isCurrentMonth,
                                isToday = isToday,
                                isSelected = isSelected,
                                dayEvents = dayEvents,
                                onClick = { onSelectDate(cellDate) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MonthDayCell(
    date: CalendarDate,
    isCurrentMonth: Boolean,
    isToday: Boolean,
    isSelected: Boolean,
    dayEvents: List<CalendarEvent>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isDark = isSystemInDarkTheme()
    val containerColor = when {
        isSelected -> MaterialTheme.colorScheme.primary
        isToday -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
        else -> MaterialTheme.colorScheme.surface
    }
    val textColor = when {
        isSelected -> MaterialTheme.colorScheme.onPrimary
        isToday -> MaterialTheme.colorScheme.primary
        isCurrentMonth -> MaterialTheme.colorScheme.onSurface
        else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.42f)
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .height(48.dp)
            .clip(MaterialTheme.shapes.small)
            .background(containerColor)
            .then(
                if (isToday && !isSelected) {
                    Modifier.border(
                        width = 1.5.dp,
                        color = MaterialTheme.colorScheme.primary,
                        shape = MaterialTheme.shapes.small
                    )
                } else {
                    Modifier
                }
            )
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp)
            .testTag("day_cell_${date.year}_${date.month}_${date.day}")
    ) {
        Text(
            text = date.day.toString(),
            style = MaterialTheme.typography.labelLarge.copy(
                fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Medium
            ),
            color = textColor
        )
        Spacer(modifier = Modifier.height(3.dp))
        if (dayEvents.isNotEmpty()) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                dayEvents.take(3).forEach { event ->
                    val dotColor = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        event.eventCategory().color(isDark)
                    }
                    Box(
                        modifier = Modifier
                            .size(5.dp)
                            .clip(CircleShape)
                            .background(dotColor)
                    )
                }
            }
        } else {
            Spacer(modifier = Modifier.height(5.dp))
        }
    }
}

@Composable
private fun SelectedDayHeader(
    selectedDate: CalendarDate,
    eventCount: Int,
    onAddClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = selectedDate.formatHeaderDate(),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = when (eventCount) {
                    0 -> "No events scheduled"
                    1 -> "1 event scheduled"
                    else -> "$eventCount events scheduled"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        TextButton(
            onClick = onAddClick,
            modifier = Modifier
                .minimumInteractiveComponentSize()
                .testTag("day_header_add_button")
        ) {
            Icon(
                imageVector = Icons.Rounded.Add,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(text = stringResource(R.string.add_event))
        }
    }
}

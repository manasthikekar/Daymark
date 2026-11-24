package com.example.ui.screens

import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.EventNote
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.CalendarEvent
import com.example.model.EventCategory
import com.example.ui.components.EventCard
import com.example.util.CalendarDate

@Composable
fun AgendaViewScreen(
    events: List<CalendarEvent>,
    searchQuery: String,
    selectedCategoryFilter: EventCategory?,
    onSearchQueryChange: (String) -> Unit,
    onToggleCategoryFilter: (EventCategory?) -> Unit,
    onEventClick: (CalendarEvent) -> Unit,
    onCreateEventClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val todayEpoch = remember { CalendarDate.today().toEpochDay() }
    val isDark = isSystemInDarkTheme()

    val filteredEvents = remember(events, searchQuery, selectedCategoryFilter) {
        val q = searchQuery.trim().lowercase()
        events.filter { event ->
            val matchesCategory = selectedCategoryFilter == null ||
                event.eventCategory() == selectedCategoryFilter
            val matchesQuery = q.isEmpty() ||
                event.title.lowercase().contains(q) ||
                (event.location?.lowercase()?.contains(q) == true) ||
                (event.notes?.lowercase()?.contains(q) == true) ||
                event.eventCategory().displayName.lowercase().contains(q)
            matchesCategory && matchesQuery
        }
    }

    val upcomingCount = remember(events, todayEpoch) {
        events.count { it.dateEpochDay >= todayEpoch }
    }
    val nextEvent = remember(events, todayEpoch) {
        val nowMinute = CalendarDate.currentMinuteOfDay()
        events.firstOrNull { event ->
            event.dateEpochDay > todayEpoch ||
                (event.dateEpochDay == todayEpoch && (event.allDay || event.endMinute >= nowMinute))
        }
    }

    val groupedByDay = remember(filteredEvents) {
        filteredEvents.groupBy { it.dateEpochDay }.toSortedMap()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(6.dp))

        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            placeholder = { Text(stringResource(R.string.search_placeholder)) },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Rounded.Search,
                    contentDescription = null
                )
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(
                        onClick = { onSearchQueryChange("") },
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = stringResource(R.string.cd_clear_search)
                        )
                    }
                }
            },
            singleLine = true,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("agenda_search_input")
        )

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = selectedCategoryFilter == null,
                onClick = { onToggleCategoryFilter(null) },
                label = { Text("All (${events.size})") },
                modifier = Modifier.testTag("filter_chip_all")
            )
            EventCategory.entries.forEach { category ->
                val accent = category.color(isDark)
                val isSelected = selectedCategoryFilter == category
                FilterChip(
                    selected = isSelected,
                    onClick = { onToggleCategoryFilter(category) },
                    label = { Text(category.displayName) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = accent.copy(alpha = 0.2f),
                        selectedLabelColor = accent
                    ),
                    modifier = Modifier.testTag("filter_chip_${category.name}")
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                            shape = MaterialTheme.shapes.large
                        )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = if (upcomingCount == 1) {
                                "1 upcoming event"
                            } else {
                                "$upcomingCount upcoming events"
                            },
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = if (nextEvent != null) {
                                "Next up: ${nextEvent.title} • ${nextEvent.date().formatCompactDate()}"
                            } else {
                                "Your upcoming schedule is open."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                        )
                    }
                }
            }

            if (groupedByDay.isEmpty()) {
                item {
                    Surface(
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.outlineVariant,
                                shape = MaterialTheme.shapes.large
                            )
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.padding(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.EventNote,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(36.dp)
                            )
                            Text(
                                text = stringResource(R.string.empty_agenda_title),
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = stringResource(R.string.empty_agenda_subtitle),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            OutlinedButton(
                                onClick = onCreateEventClick,
                                modifier = Modifier.minimumInteractiveComponentSize()
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Add,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(stringResource(R.string.add_event))
                            }
                        }
                    }
                }
            } else {
                groupedByDay.forEach { (epochDay, dayEvents) ->
                    val date = CalendarDate.fromEpochDay(epochDay)
                    val dateLabel = when (epochDay - todayEpoch) {
                        0L -> "Today • ${date.formatCompactDate()}"
                        1L -> "Tomorrow • ${date.formatCompactDate()}"
                        -1L -> "Yesterday • ${date.formatCompactDate()}"
                        else -> date.formatFullDate()
                    }
                    item(key = "header_$epochDay") {
                        Text(
                            text = dateLabel,
                            style = MaterialTheme.typography.titleSmall,
                            color = if (epochDay == todayEpoch) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    }
                    items(dayEvents, key = { it.id }) { event ->
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

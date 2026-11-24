package com.example.ui.sheets

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.R
import com.example.model.EventCategory
import com.example.model.ReminderOption
import com.example.ui.EventEditorDraft
import com.example.util.CalendarDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventEditorSheet(
    initialDraft: EventEditorDraft,
    isSaving: Boolean,
    onSave: (EventEditorDraft) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val isDark = isSystemInDarkTheme()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var draft by remember(initialDraft) { mutableStateOf(initialDraft) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showStartTimePicker by remember { mutableStateOf(false) }
    var showEndTimePicker by remember { mutableStateOf(false) }
    var titleError by remember { mutableStateOf<String?>(null) }
    var pendingSaveAfterPermission by remember { mutableStateOf<EventEditorDraft?>(null) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ ->
        pendingSaveAfterPermission?.let { pending ->
            pendingSaveAfterPermission = null
            onSave(pending)
        }
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = CalendarDate.toUtcMillis(draft.date)
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        val selectedMillis = datePickerState.selectedDateMillis
                        if (selectedMillis != null) {
                            draft = draft.copy(date = CalendarDate.fromUtcMillis(selectedMillis))
                        }
                        showDatePicker = false
                    }
                ) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showStartTimePicker) {
        val timeState = rememberTimePickerState(
            initialHour = (draft.startMinute / 60).coerceIn(0, 23),
            initialMinute = (draft.startMinute % 60).coerceIn(0, 59),
            is24Hour = false
        )
        TimePickerDialogWrapper(
            title = "Select start time",
            onDismiss = { showStartTimePicker = false },
            onConfirm = {
                val newStart = (timeState.hour * 60 + timeState.minute).coerceIn(0, 1439)
                val newEnd = if (draft.endMinute <= newStart) {
                    (newStart + 60).coerceAtMost(1440)
                } else {
                    draft.endMinute
                }
                draft = draft.copy(startMinute = newStart, endMinute = newEnd)
                showStartTimePicker = false
            }
        ) {
            TimePicker(state = timeState)
        }
    }

    if (showEndTimePicker) {
        val timeState = rememberTimePickerState(
            initialHour = (draft.endMinute / 60).coerceIn(0, 23),
            initialMinute = (draft.endMinute % 60).coerceIn(0, 59),
            is24Hour = false
        )
        TimePickerDialogWrapper(
            title = "Select end time",
            onDismiss = { showEndTimePicker = false },
            onConfirm = {
                val pickedEnd = (timeState.hour * 60 + timeState.minute).coerceIn(1, 1440)
                val safeEnd = maxOf(draft.startMinute + 15, pickedEnd).coerceAtMost(1440)
                draft = draft.copy(endMinute = safeEnd)
                showEndTimePicker = false
            }
        ) {
            TimePicker(state = timeState)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = if (draft.editingEventId == null) {
                    stringResource(R.string.add_event)
                } else {
                    stringResource(R.string.edit_event)
                },
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            OutlinedTextField(
                value = draft.title,
                onValueChange = { input ->
                    if (input.length <= 100) {
                        draft = draft.copy(title = input)
                        if (titleError != null && input.isNotBlank()) {
                            titleError = null
                        }
                    }
                },
                label = { Text(stringResource(R.string.title_label)) },
                placeholder = { Text(stringResource(R.string.title_placeholder)) },
                isError = titleError != null,
                supportingText = {
                    val err = titleError
                    if (err != null) {
                        Text(err)
                    } else if (draft.title.length >= 80) {
                        Text("${draft.title.length}/100")
                    }
                },
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("event_title_input")
            )

            // Date & All-Day Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = { showDatePicker = true },
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                        .testTag("event_date_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.CalendarToday,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = draft.date.formatCompactDate(),
                        style = MaterialTheme.typography.labelLarge
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.all_day),
                        style = MaterialTheme.typography.labelLarge
                    )
                    Switch(
                        checked = draft.allDay,
                        onCheckedChange = { checked ->
                            draft = draft.copy(allDay = checked)
                        },
                        modifier = Modifier.testTag("event_all_day_switch")
                    )
                }
            }

            // Start & End Time Pickers
            AnimatedVisibility(visible = !draft.allDay) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = { showStartTimePicker = true },
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier
                            .weight(1f)
                            .minimumInteractiveComponentSize()
                            .testTag("event_start_time_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.AccessTime,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = CalendarDate.formatMinute(draft.startMinute))
                    }

                    OutlinedButton(
                        onClick = { showEndTimePicker = true },
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier
                            .weight(1f)
                            .minimumInteractiveComponentSize()
                            .testTag("event_end_time_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.AccessTime,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = CalendarDate.formatMinute(draft.endMinute))
                    }
                }
            }

            // Category chips
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = stringResource(R.string.category_label),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    EventCategory.entries.forEach { category ->
                        val accent = category.color(isDark)
                        val isSelected = draft.category == category
                        FilterChip(
                            selected = isSelected,
                            onClick = { draft = draft.copy(category = category) },
                            label = { Text(category.displayName) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = accent.copy(alpha = 0.2f),
                                selectedLabelColor = accent
                            ),
                            modifier = Modifier.testTag("editor_category_${category.name}")
                        )
                    }
                }
            }

            // Reminder chips
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Notifications,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = stringResource(R.string.reminder_label),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ReminderOption.entries.forEach { option ->
                        FilterChip(
                            selected = draft.reminderOption == option,
                            onClick = {
                                draft = draft.copy(reminderOption = option)
                                if (option != ReminderOption.NONE &&
                                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                    ContextCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.POST_NOTIFICATIONS
                                    ) != PackageManager.PERMISSION_GRANTED
                                ) {
                                    notificationPermissionLauncher.launch(
                                        Manifest.permission.POST_NOTIFICATIONS
                                    )
                                }
                            },
                            label = { Text(option.label) },
                            modifier = Modifier.testTag("editor_reminder_${option.name}")
                        )
                    }
                }
            }

            OutlinedTextField(
                value = draft.location,
                onValueChange = { input ->
                    if (input.length <= 120) {
                        draft = draft.copy(location = input)
                    }
                },
                label = { Text(stringResource(R.string.location_label)) },
                placeholder = { Text(stringResource(R.string.location_placeholder)) },
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("event_location_input")
            )

            OutlinedTextField(
                value = draft.notes,
                onValueChange = { input ->
                    if (input.length <= 500) {
                        draft = draft.copy(notes = input)
                    }
                },
                label = { Text(stringResource(R.string.notes_label)) },
                placeholder = { Text(stringResource(R.string.notes_placeholder)) },
                minLines = 2,
                maxLines = 4,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("event_notes_input")
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    enabled = !isSaving,
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp)
                ) {
                    Text(stringResource(R.string.cancel))
                }
                Button(
                    onClick = {
                        if (draft.title.trim().isEmpty()) {
                            titleError = "Title is required"
                        } else if (
                            draft.reminderOption != ReminderOption.NONE &&
                            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                            ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.POST_NOTIFICATIONS
                            ) != PackageManager.PERMISSION_GRANTED
                        ) {
                            pendingSaveAfterPermission = draft
                            notificationPermissionLauncher.launch(
                                Manifest.permission.POST_NOTIFICATIONS
                            )
                        } else {
                            onSave(draft)
                        }
                    },
                    enabled = !isSaving,
                    modifier = Modifier
                        .weight(1.4f)
                        .height(50.dp)
                        .testTag("save_event_button")
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(stringResource(R.string.save_event))
                    }
                }
            }
        }
    }
}

@Composable
private fun TimePickerDialogWrapper(
    title: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    content: @Composable () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = content,
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("OK")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

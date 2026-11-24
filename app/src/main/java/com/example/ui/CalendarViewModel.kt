package com.example.ui

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.CalendarRepository
import com.example.data.PreferencesRepository
import com.example.model.CalendarEvent
import com.example.model.EventCategory
import com.example.model.ReminderOption
import com.example.notifications.ReminderScheduleOutcome
import com.example.notifications.ReminderScheduler
import com.example.util.CalendarDate
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

enum class CalendarTab {
    MONTH,
    WEEK,
    AGENDA,
}

data class EventEditorDraft(
    val editingEventId: String? = null,
    val title: String = "",
    val notes: String = "",
    val location: String = "",
    val date: CalendarDate = CalendarDate.today(),
    val startMinute: Int = 540,
    val endMinute: Int = 600,
    val allDay: Boolean = false,
    val category: EventCategory = EventCategory.PERSONAL,
    val reminderOption: ReminderOption = ReminderOption.TEN_MIN,
)

class CalendarViewModel(
    private val repository: CalendarRepository,
    private val preferencesRepository: PreferencesRepository,
    private val currentUserId: String,
    private val appContext: Context,
) : ViewModel() {

    private val _selectedTab = MutableStateFlow(CalendarTab.MONTH)
    val selectedTab: StateFlow<CalendarTab> = _selectedTab.asStateFlow()

    private val _selectedDate = MutableStateFlow(CalendarDate.today())
    val selectedDate: StateFlow<CalendarDate> = _selectedDate.asStateFlow()

    private val _displayedMonth = MutableStateFlow(CalendarDate.today().withDayOfMonth(1))
    val displayedMonth: StateFlow<CalendarDate> = _displayedMonth.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedCategoryFilter = MutableStateFlow<EventCategory?>(null)
    val selectedCategoryFilter: StateFlow<EventCategory?> = _selectedCategoryFilter.asStateFlow()

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    private val _editorDraft = MutableStateFlow<EventEditorDraft?>(null)
    val editorDraft: StateFlow<EventEditorDraft?> = _editorDraft.asStateFlow()

    private val _detailEventId = MutableStateFlow<String?>(null)
    val detailEventId: StateFlow<String?> = _detailEventId.asStateFlow()

    private val _snackbarMessages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val snackbarMessages = _snackbarMessages.asSharedFlow()

    val startWeekOnMonday: StateFlow<Boolean> = preferencesRepository.startWeekOnMonday
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = false,
        )

    private val _retryTrigger = MutableStateFlow(0)

    val eventsState: StateFlow<UiState<List<CalendarEvent>>> = combine(
        repository.observeEvents(currentUserId),
        _retryTrigger
    ) { events, _ -> events }
        .onEach { events ->
            ReminderScheduler.syncAllUpcomingReminders(appContext, events)
        }
        .map<List<CalendarEvent>, UiState<List<CalendarEvent>>> { UiState.Success(it) }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val rootError = firestoreError ?: error
            Log.w(TAG, "Error observing calendar events", rootError)
            emit(UiState.Error(rootError.localizedMessage ?: "Unable to sync calendar events"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading,
        )

    fun selectTab(tab: CalendarTab) {
        _selectedTab.value = tab
    }

    fun selectDate(date: CalendarDate) {
        _selectedDate.value = date
        if (date.year != _displayedMonth.value.year || date.month != _displayedMonth.value.month) {
            _displayedMonth.value = date.withDayOfMonth(1)
        }
    }

    fun goToToday() {
        val today = CalendarDate.today()
        _selectedDate.value = today
        _displayedMonth.value = today.withDayOfMonth(1)
    }

    fun shiftMonth(delta: Int) {
        val nextMonth = _displayedMonth.value.plusMonths(delta).withDayOfMonth(1)
        _displayedMonth.value = nextMonth
        val clampedDay = minOf(
            _selectedDate.value.day,
            CalendarDate.lengthOfMonth(nextMonth.year, nextMonth.month)
        )
        _selectedDate.value = nextMonth.copy(day = clampedDay)
    }

    fun jumpToMonth(year: Int, month: Int) {
        val targetMonth = CalendarDate(year, month, 1)
        _displayedMonth.value = targetMonth
        val clampedDay = minOf(
            _selectedDate.value.day,
            CalendarDate.lengthOfMonth(year, month)
        )
        _selectedDate.value = CalendarDate(year, month, clampedDay)
    }

    fun shiftWeek(deltaWeeks: Int) {
        val shifted = _selectedDate.value.plusDays(deltaWeeks * 7L)
        selectDate(shifted)
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun toggleCategoryFilter(category: EventCategory?) {
        _selectedCategoryFilter.value = if (_selectedCategoryFilter.value == category) null else category
    }

    fun setStartWeekOnMonday(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setStartWeekOnMonday(enabled)
        }
    }

    fun openCreateEditor(
        initialDate: CalendarDate = _selectedDate.value,
        initialStartMinute: Int = 540,
    ) {
        val safeStart = initialStartMinute.coerceIn(0, 1380)
        _editorDraft.value = EventEditorDraft(
            date = initialDate,
            startMinute = safeStart,
            endMinute = (safeStart + 60).coerceAtMost(1440),
        )
    }

    fun openEditEditor(event: CalendarEvent) {
        _detailEventId.value = null
        _editorDraft.value = EventEditorDraft(
            editingEventId = event.id,
            title = event.title,
            notes = event.notes.orEmpty(),
            location = event.location.orEmpty(),
            date = event.date(),
            startMinute = event.startMinute.toInt(),
            endMinute = event.endMinute.toInt(),
            allDay = event.allDay,
            category = event.eventCategory(),
            reminderOption = event.reminderOption(),
        )
    }

    fun dismissEditor() {
        _editorDraft.value = null
    }

    fun openEventDetail(eventId: String) {
        _detailEventId.value = eventId
    }

    fun dismissEventDetail() {
        _detailEventId.value = null
    }

    fun saveDraft(draft: EventEditorDraft) {
        val trimmedTitle = draft.title.trim()
        if (trimmedTitle.isEmpty()) {
            _snackbarMessages.tryEmit("Please enter an event title.")
            return
        }
        viewModelScope.launch {
            _isSaving.value = true
            val dateEpochDay = draft.date.toEpochDay()
            val startMin = if (draft.allDay) 0L else draft.startMinute.toLong()
            val endMin = if (draft.allDay) 1440L else maxOf(startMin + 15L, draft.endMinute.toLong()).coerceAtMost(1440L)

            val existingId = draft.editingEventId
            if (existingId == null) {
                val result = repository.createEvent(
                    title = trimmedTitle,
                    notes = draft.notes,
                    location = draft.location,
                    dateEpochDay = dateEpochDay,
                    startMinute = startMin,
                    endMinute = endMin,
                    allDay = draft.allDay,
                    category = draft.category,
                    reminderOption = draft.reminderOption,
                )
                result.onSuccess { newId ->
                    val outcome = ReminderScheduler.scheduleReminder(
                        context = appContext,
                        eventId = newId,
                        title = trimmedTitle,
                        location = draft.location,
                        dateEpochDay = dateEpochDay,
                        startMinute = startMin,
                        endMinute = endMin,
                        allDay = draft.allDay,
                        reminderOption = draft.reminderOption,
                    )
                    _selectedDate.value = draft.date
                    _displayedMonth.value = draft.date.withDayOfMonth(1)
                    _editorDraft.value = null
                    _snackbarMessages.tryEmit(outcomeMessage(isUpdate = false, outcome = outcome))
                }.onFailure { err ->
                    _snackbarMessages.tryEmit(err.localizedMessage ?: "Could not save event")
                }
            } else {
                val result = repository.updateEvent(
                    eventId = existingId,
                    title = trimmedTitle,
                    notes = draft.notes,
                    location = draft.location,
                    dateEpochDay = dateEpochDay,
                    startMinute = startMin,
                    endMinute = endMin,
                    allDay = draft.allDay,
                    category = draft.category,
                    reminderOption = draft.reminderOption,
                )
                result.onSuccess {
                    val outcome = ReminderScheduler.scheduleReminder(
                        context = appContext,
                        eventId = existingId,
                        title = trimmedTitle,
                        location = draft.location,
                        dateEpochDay = dateEpochDay,
                        startMinute = startMin,
                        endMinute = endMin,
                        allDay = draft.allDay,
                        reminderOption = draft.reminderOption,
                    )
                    _selectedDate.value = draft.date
                    _displayedMonth.value = draft.date.withDayOfMonth(1)
                    _editorDraft.value = null
                    _snackbarMessages.tryEmit(outcomeMessage(isUpdate = true, outcome = outcome))
                }.onFailure { err ->
                    _snackbarMessages.tryEmit(err.localizedMessage ?: "Could not update event")
                }
            }
            _isSaving.value = false
        }
    }

    fun deleteEvent(eventId: String) {
        viewModelScope.launch {
            val result = repository.deleteEvent(eventId)
            result.onSuccess {
                ReminderScheduler.cancelReminder(appContext, eventId)
                _detailEventId.value = null
                _snackbarMessages.tryEmit("Event deleted")
            }.onFailure { err ->
                _snackbarMessages.tryEmit(err.localizedMessage ?: "Could not delete event")
            }
        }
    }

    fun retrySync() {
        _retryTrigger.value += 1
    }

    private fun outcomeMessage(isUpdate: Boolean, outcome: ReminderScheduleOutcome): String {
        val verb = if (isUpdate) "updated" else "saved"
        return when (outcome) {
            ReminderScheduleOutcome.SCHEDULED_EXACT -> "Event $verb with exact reminder"
            ReminderScheduleOutcome.SCHEDULED_INEXACT -> "Event $verb with reminder"
            ReminderScheduleOutcome.NOT_NEEDED -> "Event $verb"
            ReminderScheduleOutcome.PAST_TIME_SKIPPED ->
                "Event $verb (reminder skipped — time already passed)"
            ReminderScheduleOutcome.PERMISSION_MISSING ->
                "Event $verb. Allow notifications in Settings for alerts."
        }
    }

    private companion object {
        const val TAG = "CalendarVM"
        const val STOP_TIMEOUT_MILLIS = 5000L
    }
}

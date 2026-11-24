package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.example.notifications.EventReminderReceiver
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.EventNote
import androidx.compose.material.icons.automirrored.rounded.EventNote
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.ViewWeek
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.ViewWeek
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.auth.AuthScreen
import com.example.auth.signOut
import com.example.data.CalendarRepository
import com.example.data.PreferencesRepository
import com.example.ui.CalendarTab
import com.example.ui.CalendarViewModel
import com.example.ui.UiState
import com.example.ui.components.AccountAndSettingsDialog
import com.example.ui.screens.AgendaViewScreen
import com.example.ui.screens.MonthViewScreen
import com.example.ui.screens.WeekViewScreen
import com.example.ui.sheets.EventDetailSheet
import com.example.ui.sheets.EventEditorSheet
import com.example.ui.theme.DaymarkTheme
import com.google.firebase.Firebase
import kotlinx.coroutines.launch
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        EventReminderReceiver.ensureChannel(this)
        enableEdgeToEdge()
        setContent {
            DaymarkTheme {
                AppContent()
            }
        }
    }
}

@Composable
fun AppContent() {
    var currentUser by remember { mutableStateOf(Firebase.auth.currentUser) }

    DisposableEffect(Unit) {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            currentUser = auth.currentUser
        }
        Firebase.auth.addAuthStateListener(listener)
        onDispose { Firebase.auth.removeAuthStateListener(listener) }
    }

    val user = currentUser
    if (user == null) {
        AuthScreen(
            onAuthSuccess = { currentUser = Firebase.auth.currentUser }
        )
    } else {
        val uid = user.uid
        key(uid) {
            DaymarkHome(
                currentUserId = uid,
                userEmail = user.email,
                userDisplayName = user.displayName,
                onSignedOut = { currentUser = null }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DaymarkHome(
    currentUserId: String,
    userEmail: String?,
    userDisplayName: String?,
    onSignedOut: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    val viewModel: CalendarViewModel = viewModel(
        key = currentUserId,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                }
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                CalendarViewModel(
                    repository = CalendarRepository(db),
                    preferencesRepository = PreferencesRepository(app),
                    currentUserId = currentUserId,
                    appContext = app,
                )
            }
        }
    )

    val selectedTab by viewModel.selectedTab.collectAsStateWithLifecycle()
    val selectedDate by viewModel.selectedDate.collectAsStateWithLifecycle()
    val displayedMonth by viewModel.displayedMonth.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val selectedCategoryFilter by viewModel.selectedCategoryFilter.collectAsStateWithLifecycle()
    val startWeekOnMonday by viewModel.startWeekOnMonday.collectAsStateWithLifecycle()
    val eventsState by viewModel.eventsState.collectAsStateWithLifecycle()
    val editorDraft by viewModel.editorDraft.collectAsStateWithLifecycle()
    val isSaving by viewModel.isSaving.collectAsStateWithLifecycle()
    val detailEventId by viewModel.detailEventId.collectAsStateWithLifecycle()

    var showAccountDialog by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            scope.launch {
                snackbarHostState.showSnackbar("Enable notifications in Settings to receive event alerts.")
            }
        }
    }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.snackbarMessages.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    if (selectedTab != CalendarTab.MONTH) {
        BackHandler {
            viewModel.selectTab(CalendarTab.MONTH)
        }
    }

    val allEvents = (eventsState as? UiState.Success)?.data.orEmpty()
    val activeDetailEvent = remember(allEvents, detailEventId) {
        detailEventId?.let { id -> allEvents.firstOrNull { it.id == id } }
    }

    if (showAccountDialog) {
        AccountAndSettingsDialog(
            userEmail = userEmail,
            userDisplayName = userDisplayName,
            startWeekOnMonday = startWeekOnMonday,
            onToggleStartWeekOnMonday = viewModel::setStartWeekOnMonday,
            onSignOutClick = {
                showAccountDialog = false
                signOut(
                    credentialManager = credentialManager,
                    scope = scope,
                    onSignOutComplete = onSignedOut
                )
            },
            onDismiss = { showAccountDialog = false }
        )
    }

    editorDraft?.let { draft ->
        EventEditorSheet(
            initialDraft = draft,
            isSaving = isSaving,
            onSave = viewModel::saveDraft,
            onDismiss = viewModel::dismissEditor
        )
    }

    activeDetailEvent?.let { event ->
        EventDetailSheet(
            event = event,
            onEdit = { viewModel.openEditEditor(event) },
            onDelete = { viewModel.deleteEvent(event.id) },
            onDismiss = viewModel::dismissEventDetail
        )
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useNavigationRail = maxWidth >= 600.dp

        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            containerColor = MaterialTheme.colorScheme.background,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            topBar = {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Rounded.CalendarMonth,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.app_name),
                                style = MaterialTheme.typography.headlineMedium,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = { showAccountDialog = true },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("account_settings_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.AccountCircle,
                                contentDescription = stringResource(R.string.cd_account_menu),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                )
            },
            floatingActionButton = {
                FloatingActionButton(
                    onClick = { viewModel.openCreateEditor() },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.testTag("add_event_fab")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = stringResource(R.string.add_event)
                    )
                }
            },
            bottomBar = {
                if (!useNavigationRail) {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface
                    ) {
                        CalendarTab.entries.forEach { tab ->
                            val selected = selectedTab == tab
                            NavigationBarItem(
                                selected = selected,
                                onClick = { viewModel.selectTab(tab) },
                                icon = {
                                    Icon(
                                        imageVector = tabIcon(tab, selected),
                                        contentDescription = null
                                    )
                                },
                                label = { Text(tabLabel(tab)) },
                                modifier = Modifier.testTag("nav_tab_${tab.name.lowercase()}")
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useNavigationRail) {
                    NavigationRail(
                        containerColor = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.fillMaxHeight()
                    ) {
                        Spacer(modifier = Modifier.weight(1f))
                        CalendarTab.entries.forEach { tab ->
                            val selected = selectedTab == tab
                            NavigationRailItem(
                                selected = selected,
                                onClick = { viewModel.selectTab(tab) },
                                icon = {
                                    Icon(
                                        imageVector = tabIcon(tab, selected),
                                        contentDescription = null
                                    )
                                },
                                label = { Text(tabLabel(tab)) },
                                modifier = Modifier.testTag("rail_tab_${tab.name.lowercase()}")
                            )
                        }
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }

                Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                    when (val state = eventsState) {
                        UiState.Loading -> {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.fillMaxSize()
                            ) {
                                CircularProgressIndicator(
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        is UiState.Error -> {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .padding(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.ErrorOutline,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(40.dp)
                                )
                                Text(
                                    text = state.message,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                Button(onClick = viewModel::retrySync) {
                                    Text(stringResource(R.string.retry))
                                }
                            }
                        }

                        is UiState.Success -> {
                            Crossfade(
                                targetState = selectedTab,
                                label = "calendar_tab_crossfade"
                            ) { tab ->
                                when (tab) {
                                    CalendarTab.MONTH -> MonthViewScreen(
                                        displayedMonth = displayedMonth,
                                        selectedDate = selectedDate,
                                        startWeekOnMonday = startWeekOnMonday,
                                        events = state.data,
                                        onSelectDate = viewModel::selectDate,
                                        onShiftMonth = viewModel::shiftMonth,
                                        onJumpToMonth = viewModel::jumpToMonth,
                                        onTodayClick = viewModel::goToToday,
                                        onEventClick = { event ->
                                            viewModel.openEventDetail(event.id)
                                        },
                                        onAddEventForDate = { date ->
                                            viewModel.openCreateEditor(initialDate = date)
                                        }
                                    )

                                    CalendarTab.WEEK -> WeekViewScreen(
                                        selectedDate = selectedDate,
                                        startWeekOnMonday = startWeekOnMonday,
                                        events = state.data,
                                        onSelectDate = viewModel::selectDate,
                                        onShiftWeek = viewModel::shiftWeek,
                                        onTodayClick = viewModel::goToToday,
                                        onEventClick = { event ->
                                            viewModel.openEventDetail(event.id)
                                        },
                                        onEmptySlotClick = { date, startMinute ->
                                            viewModel.openCreateEditor(
                                                initialDate = date,
                                                initialStartMinute = startMinute
                                            )
                                        }
                                    )

                                    CalendarTab.AGENDA -> AgendaViewScreen(
                                        events = state.data,
                                        searchQuery = searchQuery,
                                        selectedCategoryFilter = selectedCategoryFilter,
                                        onSearchQueryChange = viewModel::updateSearchQuery,
                                        onToggleCategoryFilter = viewModel::toggleCategoryFilter,
                                        onEventClick = { event ->
                                            viewModel.openEventDetail(event.id)
                                        },
                                        onCreateEventClick = { viewModel.openCreateEditor() }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun tabLabel(tab: CalendarTab): String = when (tab) {
    CalendarTab.MONTH -> stringResource(R.string.nav_month)
    CalendarTab.WEEK -> stringResource(R.string.nav_week)
    CalendarTab.AGENDA -> stringResource(R.string.nav_agenda)
}

private fun tabIcon(tab: CalendarTab, selected: Boolean) = when (tab) {
    CalendarTab.MONTH -> if (selected) Icons.Rounded.CalendarMonth else Icons.Outlined.CalendarMonth
    CalendarTab.WEEK -> if (selected) Icons.Rounded.ViewWeek else Icons.Outlined.ViewWeek
    CalendarTab.AGENDA -> if (selected) Icons.AutoMirrored.Rounded.EventNote else Icons.AutoMirrored.Outlined.EventNote
}

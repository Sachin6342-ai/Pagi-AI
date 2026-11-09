package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.launch
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.core.configureAppCheck
import com.example.ui.JarvisViewModel
import com.example.ui.screens.CommandHudScreen
import com.example.ui.screens.MemoryAndProductivityScreen
import com.example.ui.screens.PermissionStatusEntry
import com.example.ui.screens.RoutinesAndHistoryScreen
import com.example.ui.screens.SettingsDashboardScreen
import com.example.ui.screens.SystemsControlScreen
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // STEP 1: Configure Firebase App Check with launch intent before any UI or Firebase calls
        configureAppCheck(this, intent)

        // STEP 2: Enable Edge-to-Edge and render Jarvis AI HUD
        enableEdgeToEdge()
        setContent {
            val jarvisViewModel: JarvisViewModel = viewModel()
            val preferences by jarvisViewModel.preferences.collectAsStateWithLifecycle()

            MyApplicationTheme(darkTheme = preferences.darkTheme) {
                JarvisRootApp(viewModel = jarvisViewModel)
            }
        }
    }
}

@Composable
fun JarvisRootApp(viewModel: JarvisViewModel) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val memories by viewModel.memories.collectAsStateWithLifecycle()
    val notes by viewModel.notes.collectAsStateWithLifecycle()
    val todos by viewModel.todos.collectAsStateWithLifecycle()
    val routines by viewModel.routines.collectAsStateWithLifecycle()
    val actionLogs by viewModel.actionLogs.collectAsStateWithLifecycle()
    val preferences by viewModel.preferences.collectAsStateWithLifecycle()
    val telemetry by viewModel.telemetry.collectAsStateWithLifecycle()
    val installedApps by viewModel.installedApps.collectAsStateWithLifecycle()
    val liveWeather by viewModel.liveWeather.collectAsStateWithLifecycle()
    val pendingConfirmations by viewModel.pendingConfirmations.collectAsStateWithLifecycle()
    val automationPipeline by viewModel.automationPipeline.collectAsStateWithLifecycle()
    val statusBanner by viewModel.statusBannerMessage.collectAsStateWithLifecycle()
    val voiceCoreState by viewModel.voiceCoreState.collectAsStateWithLifecycle()
    val isBrainThinking by viewModel.isBrainThinking.collectAsStateWithLifecycle()
    val waveformLevels by viewModel.waveformLevels.collectAsStateWithLifecycle()
    val partialTranscript by viewModel.partialTranscript.collectAsStateWithLifecycle()

    var currentTab by rememberSaveable { mutableIntStateOf(0) }
    var permissionRefreshTick by remember { mutableIntStateOf(0) }

    val hasMicPermission = remember(permissionRefreshTick) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
    }

    val singlePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ ->
        permissionRefreshTick++
        viewModel.syncVoiceConfiguration()
        viewModel.refreshTelemetryAndApps()
    }

    val multiPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        permissionRefreshTick++
        viewModel.syncVoiceConfiguration()
        viewModel.refreshTelemetryAndApps()
    }

    val cameraVisionPreviewLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        if (bitmap != null) {
            viewModel.analyzeCameraSnapshot(bitmap)
        }
    }

    val cameraPermissionForVisionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        permissionRefreshTick++
        if (granted) {
            cameraVisionPreviewLauncher.launch()
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                permissionRefreshTick++
                viewModel.refreshTelemetryAndApps()
                viewModel.syncVoiceConfiguration()
            } else if (event == Lifecycle.Event.ON_PAUSE) {
                viewModel.stopVoiceActivity()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // BackHandler for non-home tabs per navigation guidelines
    if (currentTab != 0) {
        BackHandler {
            currentTab = 0
        }
    }

    val permissionEntries = remember(permissionRefreshTick) {
        listOf(
            PermissionStatusEntry(
                label = "Microphone (Voice & Wake Word)",
                permissionName = Manifest.permission.RECORD_AUDIO,
                isGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
            ),
            PermissionStatusEntry(
                label = "Contacts (Call & Message Lookup)",
                permissionName = Manifest.permission.READ_CONTACTS,
                isGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
            ),
            PermissionStatusEntry(
                label = "Calendar (Schedule & Prepare My Day)",
                permissionName = Manifest.permission.READ_CALENDAR,
                isGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED
            ),
            PermissionStatusEntry(
                label = "Camera (Photos & Vision Analysis)",
                permissionName = Manifest.permission.CAMERA,
                isGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
            ),
            PermissionStatusEntry(
                label = "Coarse Location (Weather & Local Search)",
                permissionName = Manifest.permission.ACCESS_COARSE_LOCATION,
                isGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
            )
        )
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                NavigationBarItem(
                    selected = currentTab == 0,
                    onClick = { currentTab = 0 },
                    icon = { Icon(Icons.Default.Mic, contentDescription = "Command HUD") },
                    label = { Text("Command") },
                    modifier = Modifier.testTag("nav_tab_command")
                )
                NavigationBarItem(
                    selected = currentTab == 1,
                    onClick = { currentTab = 1 },
                    icon = { Icon(Icons.Default.Tune, contentDescription = "Phone Systems") },
                    label = { Text("Systems") },
                    modifier = Modifier.testTag("nav_tab_systems")
                )
                NavigationBarItem(
                    selected = currentTab == 2,
                    onClick = { currentTab = 2 },
                    icon = { Icon(Icons.Default.Memory, contentDescription = "Memory and Tasks") },
                    label = { Text("Memory") },
                    modifier = Modifier.testTag("nav_tab_memory")
                )
                NavigationBarItem(
                    selected = currentTab == 3,
                    onClick = { currentTab = 3 },
                    icon = { Icon(Icons.Default.AutoAwesome, contentDescription = "Routines and History") },
                    label = { Text("Routines") },
                    modifier = Modifier.testTag("nav_tab_routines")
                )
                NavigationBarItem(
                    selected = currentTab == 4,
                    onClick = { currentTab = 4 },
                    icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                    label = { Text("Settings") },
                    modifier = Modifier.testTag("nav_tab_settings")
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (currentTab) {
                0 -> CommandHudScreen(
                    messages = messages,
                    voiceCoreState = voiceCoreState,
                    isBrainThinking = isBrainThinking,
                    waveformLevels = waveformLevels,
                    partialTranscript = partialTranscript,
                    telemetry = telemetry,
                    liveWeather = liveWeather,
                    pendingConfirmations = pendingConfirmations,
                    automationPipeline = automationPipeline,
                    hasMicPermission = hasMicPermission,
                    onRequestMicPermission = {
                        singlePermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    },
                    onAiCoreTap = {
                        viewModel.onAiCoreTapped(
                            hasMicPermission = hasMicPermission,
                            onRequestMicPermission = {
                                singlePermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        )
                    },
                    onStopVoiceTap = { viewModel.stopVoiceActivity() },
                    onSendCommand = { cmd, act ->
                        // Proactively request contacts/calendar permissions if the command mentions calling/messaging/calendar
                        val lower = cmd.lowercase()
                        if ((lower.contains("call") || lower.contains("message") || lower.contains("sms") || lower.contains("whatsapp")) &&
                            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED
                        ) {
                            singlePermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
                        }
                        viewModel.submitUserCommand(cmd, fromVoice = false, activity = act)
                    },
                    onPrepareMyDay = {
                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED) {
                            multiPermissionLauncher.launch(
                                arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.ACCESS_COARSE_LOCATION)
                            )
                        }
                        viewModel.runPrepareMyDayAutomation()
                    },
                    onToggleFlashlight = { viewModel.toggleFlashlightDirect() },
                    onCaptureVisionPhoto = {
                        val camGranted = ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.CAMERA
                        ) == PackageManager.PERMISSION_GRANTED
                        if (camGranted) {
                            cameraVisionPreviewLauncher.launch()
                        } else {
                            cameraPermissionForVisionLauncher.launch(Manifest.permission.CAMERA)
                        }
                    },
                    onConfirmAction = { item, num, msg ->
                        viewModel.confirmPendingAction(item, num, msg)
                    },
                    onCancelAction = { item ->
                        viewModel.cancelPendingAction(item)
                    },
                    onDismissAutomation = { viewModel.dismissAutomationPipeline() },
                    onSpeakMessageAloud = { text, lang ->
                        viewModel.voiceManager.speakResponse(text, lang)
                    },
                    onClearConversation = { viewModel.clearConversation() }
                )

                1 -> SystemsControlScreen(
                    telemetry = telemetry,
                    installedApps = installedApps,
                    actionExecutor = viewModel.actionExecutor,
                    onToggleFlashlight = { viewModel.toggleFlashlightDirect() },
                    onBrightnessChange = { act, pct -> viewModel.updateBrightnessDirect(act, pct) },
                    onVolumeChange = { pct -> viewModel.updateMediaVolumeDirect(pct) },
                    onRefreshTelemetry = { viewModel.refreshTelemetryAndApps() },
                    onQueueActionViaBrain = { cmd, act ->
                        currentTab = 0
                        viewModel.submitUserCommand(cmd, fromVoice = false, activity = act)
                    }
                )

                2 -> MemoryAndProductivityScreen(
                    memories = memories,
                    todos = todos,
                    notes = notes,
                    onSaveMemory = { cat, k, v -> viewModel.saveMemoryFact(cat, k, v) },
                    onDeleteMemory = { id -> viewModel.deleteMemoryFact(id) },
                    onAddTodo = { task, due, prio -> viewModel.addTodoItem(task, due, prio) },
                    onToggleTodo = { todo -> viewModel.toggleTodoCompleted(todo) },
                    onDeleteTodo = { id -> viewModel.deleteTodoItem(id) },
                    onSaveNote = { title, body, pinned -> viewModel.saveNoteItem(title, body, pinned) },
                    onToggleNotePinned = { note -> viewModel.toggleNotePinned(note) },
                    onDeleteNote = { id -> viewModel.deleteNoteItem(id) }
                )

                3 -> RoutinesAndHistoryScreen(
                    routines = routines,
                    actionLogs = actionLogs,
                    onRunRoutine = { routine, act ->
                        currentTab = 0
                        viewModel.executeCustomRoutine(routine, act)
                    },
                    onCreateRoutine = { title, trigger, steps ->
                        viewModel.createCustomRoutine(title, trigger, steps)
                    },
                    onToggleRoutine = { routine -> viewModel.toggleRoutineEnabled(routine) },
                    onDeleteRoutine = { id -> viewModel.deleteRoutineItem(id) },
                    onClearActionLogs = { viewModel.clearExecutionLogs() }
                )

                4 -> SettingsDashboardScreen(
                    preferences = preferences,
                    permissionStatuses = permissionEntries,
                    onUpdatePreferences = { transform -> viewModel.updatePreferences(transform) },
                    onRefreshWeatherCity = { city -> viewModel.refreshWeather(city) },
                    onTestVoiceSample = {
                        viewModel.voiceManager.speakResponse(
                            "Hello! Pagi AI voice synthesis is online and calibrated. Namaste, main Pagi hoon aur aapki madad ke liye taiyar hoon.",
                            preferences.speechLocaleTag.take(2)
                        )
                    },
                    onRequestPermission = { perm ->
                        singlePermissionLauncher.launch(perm)
                    }
                )
            }

            if (statusBanner != null) {
                Snackbar(
                    action = {
                        TextButton(onClick = { viewModel.dismissBanner() }) {
                            Text("OK")
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(12.dp)
                ) {
                    Text(statusBanner.orEmpty())
                }
            }
        }
    }
}

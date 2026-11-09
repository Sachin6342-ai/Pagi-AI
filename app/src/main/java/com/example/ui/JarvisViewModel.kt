package com.example.ui

import android.app.Activity
import android.app.Application
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.view.KeyEvent
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai.JarvisBrainService
import com.example.ai.ParsedCommandAction
import com.example.control.AndroidActionExecutor
import com.example.control.DeviceTelemetry
import com.example.control.InstalledAppInfo
import com.example.data.JarvisPreferences
import com.example.data.JarvisRepository
import com.example.data.local.ActionLogEntity
import com.example.data.local.ChatMessageEntity
import com.example.data.local.JarvisDatabase
import com.example.data.local.MemoryFactEntity
import com.example.data.local.NoteEntity
import com.example.data.local.RoutineEntity
import com.example.data.local.TodoItemEntity
import com.example.data.remote.LiveWeatherReport
import com.example.data.remote.WeatherService
import com.example.voice.JarvisVoiceManager
import com.example.voice.VoiceCoreState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class PendingConfirmationItem(
    val id: Long = System.currentTimeMillis(),
    val actionType: String, // "CALL_CONTACT", "SEND_SMS", "SEND_WHATSAPP", "NAVIGATE_MAPS"
    val title: String,
    val contactName: String,
    val phoneNumberOrQuery: String,
    val messageDraft: String = "",
    val contactResolvedFromPhonebook: Boolean = false
)

enum class StepProgressStatus {
    PENDING,
    RUNNING,
    COMPLETED
}

data class AutomationStepStatus(
    val stepTitle: String,
    val detail: String,
    val status: StepProgressStatus
)

data class AutomationPipelineState(
    val routineName: String,
    val isRunning: Boolean,
    val steps: List<AutomationStepStatus>
)

class JarvisViewModel(application: Application) : AndroidViewModel(application) {

    private val appContext = application.applicationContext
    private val database = JarvisDatabase.getInstance(appContext)
    private val repository = JarvisRepository(appContext, database.jarvisDao())
    private val brainService = JarvisBrainService()
    private val weatherService = WeatherService()
    val actionExecutor = AndroidActionExecutor(appContext)

    val messages: StateFlow<List<ChatMessageEntity>> = repository.messages
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val memories: StateFlow<List<MemoryFactEntity>> = repository.memories
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val notes: StateFlow<List<NoteEntity>> = repository.notes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val todos: StateFlow<List<TodoItemEntity>> = repository.todos
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val routines: StateFlow<List<RoutineEntity>> = repository.routines
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val actionLogs: StateFlow<List<ActionLogEntity>> = repository.actionLogs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _preferences = MutableStateFlow(repository.loadPreferences())
    val preferences: StateFlow<JarvisPreferences> = _preferences.asStateFlow()

    private val _telemetry = MutableStateFlow(actionExecutor.readLiveTelemetry())
    val telemetry: StateFlow<DeviceTelemetry> = _telemetry.asStateFlow()

    private val _installedApps = MutableStateFlow<List<InstalledAppInfo>>(emptyList())
    val installedApps: StateFlow<List<InstalledAppInfo>> = _installedApps.asStateFlow()

    private val _liveWeather = MutableStateFlow<LiveWeatherReport?>(null)
    val liveWeather: StateFlow<LiveWeatherReport?> = _liveWeather.asStateFlow()

    private val _pendingConfirmations = MutableStateFlow<List<PendingConfirmationItem>>(emptyList())
    val pendingConfirmations: StateFlow<List<PendingConfirmationItem>> = _pendingConfirmations.asStateFlow()

    private val _automationPipeline = MutableStateFlow<AutomationPipelineState?>(null)
    val automationPipeline: StateFlow<AutomationPipelineState?> = _automationPipeline.asStateFlow()

    private val _statusBannerMessage = MutableStateFlow<String?>(null)
    val statusBannerMessage: StateFlow<String?> = _statusBannerMessage.asStateFlow()

    private val _isBrainThinking = MutableStateFlow(false)
    val isBrainThinking: StateFlow<Boolean> = _isBrainThinking.asStateFlow()

    val voiceManager = JarvisVoiceManager(
        context = appContext,
        onCommandRecognized = { recognizedText ->
            submitUserCommand(recognizedText, fromVoice = true)
        },
        onWakeWordTriggered = {
            _statusBannerMessage.value = "Wake Word Detected: Listening for your command..."
        },
        onSpeechStatusMessage = { msg ->
            _statusBannerMessage.value = msg
        }
    )

    val voiceCoreState: StateFlow<VoiceCoreState> = voiceManager.coreState
    val partialTranscript: StateFlow<String> = voiceManager.partialTranscript
    val waveformLevels: StateFlow<List<Float>> = voiceManager.waveformLevels

    init {
        viewModelScope.launch {
            repository.ensureSeeded()
            refreshTelemetryAndApps()
            syncVoiceConfiguration()
            refreshWeather(_preferences.value.defaultCity)
        }
    }

    fun dismissBanner() {
        _statusBannerMessage.value = null
    }

    fun refreshTelemetryAndApps() {
        _telemetry.value = actionExecutor.readLiveTelemetry()
        viewModelScope.launch {
            _installedApps.value = actionExecutor.getInstalledApps()
        }
    }

    fun syncVoiceConfiguration() {
        val prefs = _preferences.value
        val hasAudioPerm = ContextCompat.checkSelfPermission(
            appContext,
            android.Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        voiceManager.updateConfiguration(
            localeTag = prefs.speechLocaleTag,
            rate = prefs.speechRate,
            pitch = prefs.speechPitch,
            wakeEnabled = prefs.wakeWordEnabled,
            continuous = prefs.continuousConversation,
            outputEnabled = prefs.voiceOutputEnabled,
            listeningDisabled = prefs.voiceListeningDisabled,
            audioPermGranted = hasAudioPerm
        )

        if (hasAudioPerm && prefs.wakeWordEnabled && !prefs.voiceListeningDisabled &&
            voiceCoreState.value == VoiceCoreState.IDLE
        ) {
            voiceManager.startWakeWordStandby()
        }
    }

    fun updatePreferences(transform: (JarvisPreferences) -> JarvisPreferences) {
        val updated = transform(_preferences.value)
        _preferences.value = updated
        repository.savePreferences(updated)
        syncVoiceConfiguration()
    }

    fun onAiCoreTapped(hasMicPermission: Boolean, onRequestMicPermission: () -> Unit) {
        val prefs = _preferences.value
        if (prefs.voiceListeningDisabled) {
            _statusBannerMessage.value = "Voice listening is muted for privacy. Enable it in Settings or tap the Mic Lock toggle."
            return
        }
        if (!hasMicPermission) {
            onRequestMicPermission()
            return
        }

        when (voiceCoreState.value) {
            VoiceCoreState.SPEAKING -> {
                // Barge-in interruption: stop speaking and immediately listen
                voiceManager.interruptSpeaking()
                voiceManager.startDirectCommandListening()
            }
            VoiceCoreState.LISTENING_COMMAND -> {
                voiceManager.stopListening()
            }
            else -> {
                voiceManager.startDirectCommandListening()
            }
        }
    }

    fun stopVoiceActivity() {
        voiceManager.interruptSpeaking()
        voiceManager.stopListening()
    }

    fun refreshWeather(cityQuery: String = _preferences.value.defaultCity) {
        viewModelScope.launch {
            val res = weatherService.fetchWeatherForCity(cityQuery)
            res.onSuccess { report ->
                _liveWeather.value = report
            }
        }
    }

    // ------------------------------------------------------------------------
    // MAIN COMMAND EXECUTION & GEMINI BRAIN PIPELINE
    // ------------------------------------------------------------------------
    fun submitUserCommand(
        rawCommand: String,
        fromVoice: Boolean = false,
        activity: Activity? = null
    ) {
        val command = rawCommand.trim()
        if (command.isBlank()) return

        viewModelScope.launch {
            _isBrainThinking.value = true
            voiceManager.setProcessingState()

            repository.addChatMessage(
                role = "USER",
                spokenText = command,
                language = _preferences.value.speechLocaleTag.take(2)
            )

            // Check if the user triggered a custom saved routine by phrase
            val allRoutines = repository.getRoutinesSnapshot()
            val matchedRoutine = allRoutines.firstOrNull { routine ->
                routine.isEnabled && command.lowercase().contains(
                    routine.triggerPhrase.lowercase()
                        .removePrefix("pagi,")
                        .removePrefix("jarvis,")
                        .trim()
                )
            }

            if (command.lowercase().contains("prepare my day") ||
                command.lowercase().contains("din ki taiyari") ||
                matchedRoutine?.title?.equals("Prepare My Day", ignoreCase = true) == true
            ) {
                _isBrainThinking.value = false
                runPrepareMyDayAutomation()
                return@launch
            }

            val currentTelemetry = actionExecutor.readLiveTelemetry()
            _telemetry.value = currentTelemetry
            val telemetryStr = "Battery ${currentTelemetry.batteryPercent}% (Charging=${currentTelemetry.isCharging}), Network=${currentTelemetry.networkType}, Torch=${currentTelemetry.isFlashlightOn}, Brightness=${currentTelemetry.brightnessPercent}%"

            val brainResult = brainService.processUserCommand(
                userPrompt = command,
                recentHistory = messages.value,
                memories = repository.getMemoriesSnapshot(),
                pendingTodos = repository.getPendingTodosSnapshot(),
                notes = repository.getNotesSnapshot(),
                routines = allRoutines,
                preferredLanguage = _preferences.value.preferredLanguage,
                deviceTelemetrySummary = telemetryStr
            ).getOrElse { error ->
                _statusBannerMessage.value = error.localizedMessage ?: "Cloud AI unreachable; using local command engine."
                brainService.buildOfflineFallbackResult(command)
            }

            // Save any newly learned preference or personal memory
            if (brainResult.memoryKeyToSave.isNotBlank() && brainResult.memoryValueToSave.isNotBlank()) {
                repository.saveOrUpdateMemory(
                    category = "PERSONAL_FACT",
                    key = brainResult.memoryKeyToSave,
                    value = brainResult.memoryValueToSave
                )
            }

            // Execute parsed actions (supports multiple actions in a single sentence!)
            val executionSummaries = mutableListOf<String>()
            for (action in brainResult.actions) {
                if (action.actionType == "PREPARE_MY_DAY") {
                    _isBrainThinking.value = false
                    runPrepareMyDayAutomation()
                    return@launch
                }
                val summary = executeOrQueueAction(action, activity)
                if (summary.isNotBlank()) {
                    executionSummaries.add(summary)
                }
            }

            val combinedActionBadge = executionSummaries.joinToString(" • ")

            repository.addChatMessage(
                role = "JARVIS",
                spokenText = brainResult.spokenReply,
                detailedText = brainResult.detailedReply,
                language = brainResult.detectedLanguage,
                actionSummary = combinedActionBadge
            )

            _isBrainThinking.value = false
            refreshTelemetryAndApps()
            voiceManager.speakResponse(brainResult.spokenReply, brainResult.detectedLanguage)
        }
    }

    private suspend fun executeOrQueueAction(
        action: ParsedCommandAction,
        activity: Activity?
    ): String {
        return when (action.actionType) {
            "CALL_CONTACT" -> {
                val contacts = actionExecutor.searchContacts(action.targetName)
                val topMatch = contacts.firstOrNull()
                val resolvedName = topMatch?.displayName ?: action.targetName.ifBlank { "Contact" }
                val resolvedNumber = topMatch?.phoneNumber ?: action.targetNumberOrQuery
                val item = PendingConfirmationItem(
                    actionType = "CALL_CONTACT",
                    title = "Confirm Outgoing Phone Call",
                    contactName = resolvedName,
                    phoneNumberOrQuery = resolvedNumber,
                    contactResolvedFromPhonebook = topMatch != null
                )
                _pendingConfirmations.value = _pendingConfirmations.value + item
                "Awaiting Call Confirmation: $resolvedName"
            }

            "OPEN_DIALER" -> {
                actionExecutor.openDialerWithNumber(action.targetNumberOrQuery)
                repository.logAction("OPEN_DIALER", "Opened Phone Dialer", action.targetNumberOrQuery.ifBlank { "Dialpad" }, "EXECUTED")
                "Opened Dialer"
            }

            "SEND_SMS", "SEND_WHATSAPP" -> {
                val isWhatsApp = action.actionType == "SEND_WHATSAPP"
                val contacts = actionExecutor.searchContacts(action.targetName)
                val topMatch = contacts.firstOrNull()
                val resolvedName = topMatch?.displayName ?: action.targetName.ifBlank { "Recipient" }
                val resolvedNumber = topMatch?.phoneNumber ?: action.targetNumberOrQuery
                val item = PendingConfirmationItem(
                    actionType = action.actionType,
                    title = if (isWhatsApp) "Confirm WhatsApp Message" else "Confirm SMS Message",
                    contactName = resolvedName,
                    phoneNumberOrQuery = resolvedNumber,
                    messageDraft = action.messageBody.ifBlank { "Hello! Sent via Pagi AI." },
                    contactResolvedFromPhonebook = topMatch != null
                )
                _pendingConfirmations.value = _pendingConfirmations.value + item
                "Prepared ${if (isWhatsApp) "WhatsApp" else "SMS"} to $resolvedName (Awaiting Confirmation)"
            }

            "NAVIGATE_MAPS" -> {
                val dest = action.targetNumberOrQuery.ifBlank { action.targetName }.ifBlank { "Destination" }
                if (action.requiresConfirmation) {
                    val item = PendingConfirmationItem(
                        actionType = "NAVIGATE_MAPS",
                        title = "Confirm Google Maps Navigation",
                        contactName = dest,
                        phoneNumberOrQuery = dest
                    )
                    _pendingConfirmations.value = _pendingConfirmations.value + item
                    "Prepared Maps Navigation to $dest"
                } else {
                    actionExecutor.openGoogleMapsNavigation(dest)
                    repository.logAction("NAVIGATE_MAPS", "Started Maps Navigation", dest, "EXECUTED")
                    "Navigating to $dest"
                }
            }

            "SEARCH_MAPS" -> {
                val query = action.targetNumberOrQuery.ifBlank { action.targetName }
                actionExecutor.openGoogleMapsSearch(query)
                repository.logAction("SEARCH_MAPS", "Searched Google Maps", query, "EXECUTED")
                "Maps Search: $query"
            }

            "OPEN_APP" -> {
                val appQuery = action.targetName.ifBlank { action.targetNumberOrQuery }
                val (ok, msg) = actionExecutor.launchAppByNameOrQuery(appQuery)
                repository.logAction("OPEN_APP", msg, appQuery, if (ok) "EXECUTED" else "FAILED")
                msg
            }

            "FLASHLIGHT_ON" -> {
                val (_, msg) = actionExecutor.setFlashlight(true)
                repository.logAction("FLASHLIGHT_ON", "Turned Flashlight ON", "Rear Torch", "EXECUTED")
                msg
            }

            "FLASHLIGHT_OFF" -> {
                val (_, msg) = actionExecutor.setFlashlight(false)
                repository.logAction("FLASHLIGHT_OFF", "Turned Flashlight OFF", "Rear Torch", "EXECUTED")
                msg
            }

            "SET_BRIGHTNESS" -> {
                val pct = if (action.numericValue in 1..100) action.numericValue else 75
                val msg = actionExecutor.setScreenBrightness(activity, pct)
                repository.logAction("SET_BRIGHTNESS", "Adjusted Screen Brightness", "$pct%", "EXECUTED")
                msg
            }

            "OPEN_WIFI_SETTINGS" -> {
                actionExecutor.openWifiSettings()
                repository.logAction("OPEN_WIFI_SETTINGS", "Opened Wi-Fi Settings", "System Panel", "EXECUTED")
                "Opened Wi-Fi Settings"
            }

            "OPEN_BLUETOOTH_SETTINGS" -> {
                actionExecutor.openBluetoothSettings()
                repository.logAction("OPEN_BLUETOOTH_SETTINGS", "Opened Bluetooth Settings", "System Panel", "EXECUTED")
                "Opened Bluetooth Settings"
            }

            "OPEN_SOUND_SETTINGS" -> {
                actionExecutor.openSoundSettings()
                repository.logAction("OPEN_SOUND_SETTINGS", "Opened Sound Settings", "System Panel", "EXECUTED")
                "Opened Sound Settings"
            }

            "OPEN_DISPLAY_SETTINGS" -> {
                actionExecutor.openDisplaySettings()
                repository.logAction("OPEN_DISPLAY_SETTINGS", "Opened Display Settings", "System Panel", "EXECUTED")
                "Opened Display Settings"
            }

            "MEDIA_PLAY_PAUSE" -> {
                val msg = actionExecutor.sendMediaKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
                repository.logAction("MEDIA_PLAY_PAUSE", msg, "AudioManager", "EXECUTED")
                msg
            }

            "MEDIA_NEXT" -> {
                val msg = actionExecutor.sendMediaKey(KeyEvent.KEYCODE_MEDIA_NEXT)
                repository.logAction("MEDIA_NEXT", msg, "AudioManager", "EXECUTED")
                msg
            }

            "MEDIA_PREVIOUS" -> {
                val msg = actionExecutor.sendMediaKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS)
                repository.logAction("MEDIA_PREVIOUS", msg, "AudioManager", "EXECUTED")
                msg
            }

            "SET_VOLUME" -> {
                val pct = if (action.numericValue in 0..100) action.numericValue else 65
                val msg = actionExecutor.setMediaVolumePercent(pct)
                repository.logAction("SET_VOLUME", "Adjusted Media Volume", "$pct%", "EXECUTED")
                msg
            }

            "CHECK_BATTERY" -> {
                val t = actionExecutor.readLiveTelemetry()
                val summary = "Battery ${t.batteryPercent}% (${if (t.isCharging) "Charging" else "Discharging"}, ${t.batteryTempCelsius}°C)"
                repository.logAction("CHECK_BATTERY", "Read Battery Status", summary, "EXECUTED")
                summary
            }

            "CHECK_NETWORK" -> {
                val t = actionExecutor.readLiveTelemetry()
                val summary = "Network: ${t.networkType} (Online=${t.isOnline})"
                repository.logAction("CHECK_NETWORK", "Checked Network Connectivity", summary, "EXECUTED")
                summary
            }

            "SET_ALARM" -> {
                val hour = if (action.numericValue in 0..23) action.numericValue else 7
                val min = action.extraMode.toIntOrNull()?.coerceIn(0, 59) ?: 0
                val (_, msg) = actionExecutor.setSystemAlarm(hour, min, action.targetName)
                repository.logAction("SET_ALARM", msg, "$hour:${String.format("%02d", min)}", "EXECUTED")
                msg
            }

            "SET_TIMER" -> {
                val secs = if (action.numericValue > 0) action.numericValue else 300
                val (_, msg) = actionExecutor.setSystemTimer(secs, action.targetName)
                repository.logAction("SET_TIMER", msg, "${secs}s", "EXECUTED")
                msg
            }

            "CREATE_NOTE" -> {
                val title = action.targetName.ifBlank { "Voice Note" }
                val body = action.messageBody.ifBlank { action.targetNumberOrQuery }.ifBlank { "Saved via Pagi voice command." }
                repository.saveNote(title = title, content = body, isPinned = false)
                repository.logAction("CREATE_NOTE", "Saved Note: $title", body.take(50), "EXECUTED")
                "Saved Note: $title"
            }

            "ADD_TODO", "ADD_REMINDER" -> {
                val taskText = action.messageBody.ifBlank { action.targetName }.ifBlank { "New Reminder" }
                val due = action.extraMode.ifBlank { "Today" }
                repository.addTodo(task = taskText, dueLabel = due, priority = "HIGH")
                repository.logAction(action.actionType, "Added Reminder/To-Do", "$taskText ($due)", "EXECUTED")
                "Added Task: $taskText"
            }

            "CHECK_CALENDAR" -> {
                val events = actionExecutor.readTodayCalendarEvents()
                val summary = if (events.isEmpty()) {
                    "No calendar events found for today"
                } else {
                    "${events.size} event(s): " + events.joinToString { "${it.title} at ${it.startTimeFormatted}" }
                }
                repository.logAction("CHECK_CALENDAR", "Checked Calendar", summary, "EXECUTED")
                summary
            }

            "ADD_CALENDAR_EVENT" -> {
                val title = action.targetName.ifBlank { "Scheduled Meeting" }
                actionExecutor.launchCalendarEventCreator(title, action.messageBody)
                repository.logAction("ADD_CALENDAR_EVENT", "Opened Calendar Event Creator", title, "EXECUTED")
                "Calendar Event: $title"
            }

            "OPEN_REAR_CAMERA" -> {
                actionExecutor.openCamera(frontCamera = false)
                repository.logAction("OPEN_REAR_CAMERA", "Launched Rear Camera", "Camera Intent", "EXECUTED")
                "Opened Rear Camera"
            }

            "OPEN_FRONT_CAMERA" -> {
                actionExecutor.openCamera(frontCamera = true)
                repository.logAction("OPEN_FRONT_CAMERA", "Launched Front Camera", "Selfie Camera Intent", "EXECUTED")
                "Opened Front Camera"
            }

            "WEATHER_LOOKUP" -> {
                val city = action.targetNumberOrQuery.ifBlank { action.targetName }.ifBlank { _preferences.value.defaultCity }
                val res = weatherService.fetchWeatherForCity(city)
                res.fold(
                    onSuccess = { report ->
                        _liveWeather.value = report
                        val summary = "${report.locationName}: ${report.temperatureCelsius}°C (${report.conditionDescription})"
                        repository.logAction("WEATHER_LOOKUP", "Fetched Live Weather", summary, "EXECUTED")
                        summary
                    },
                    onFailure = {
                        "Weather lookup completed"
                    }
                )
            }

            "WEB_SEARCH" -> {
                val q = action.targetNumberOrQuery.ifBlank { action.targetName }
                if (q.isNotBlank()) {
                    actionExecutor.openWebSearch(q)
                    repository.logAction("WEB_SEARCH", "Launched Web Search", q, "EXECUTED")
                }
                "Web Search: $q"
            }

            "SAVE_MEMORY" -> {
                val key = action.targetName.ifBlank { "User Note" }
                val value = action.messageBody.ifBlank { action.targetNumberOrQuery }
                if (value.isNotBlank()) {
                    repository.saveOrUpdateMemory("PERSONAL_FACT", key, value)
                    repository.logAction("SAVE_MEMORY", "Saved Personal Memory", "$key = $value", "EXECUTED")
                }
                "Saved to Memory: $key"
            }

            else -> ""
        }
    }

    // ------------------------------------------------------------------------
    // USER CONFIRMATION HANDLERS (For Calls, SMS, WhatsApp, Maps)
    // ------------------------------------------------------------------------
    fun confirmPendingAction(
        item: PendingConfirmationItem,
        editedNumberOrQuery: String,
        editedMessage: String
    ) {
        viewModelScope.launch {
            _pendingConfirmations.value = _pendingConfirmations.value.filterNot { it.id == item.id }
            val finalTarget = editedNumberOrQuery.trim()
            val finalMessage = editedMessage.trim()

            when (item.actionType) {
                "CALL_CONTACT" -> {
                    actionExecutor.openDialerWithNumber(finalTarget)
                    repository.logAction(
                        actionType = "CALL_CONTACT",
                        title = "Confirmed Call to ${item.contactName}",
                        targetSummary = finalTarget.ifBlank { item.contactName },
                        status = "CONFIRMED"
                    )
                    repository.addChatMessage(
                        role = "SYSTEM",
                        spokenText = "Confirmed: Opening phone dialer for ${item.contactName} (${finalTarget.ifBlank { "number selection" }}).",
                        actionSummary = "CALL CONFIRMED"
                    )
                }

                "SEND_SMS" -> {
                    actionExecutor.launchSmsComposer(finalTarget, finalMessage)
                    repository.logAction(
                        actionType = "SEND_SMS",
                        title = "Confirmed SMS to ${item.contactName}",
                        targetSummary = "\"$finalMessage\"",
                        status = "CONFIRMED"
                    )
                    repository.addChatMessage(
                        role = "SYSTEM",
                        spokenText = "Confirmed: Prepared SMS to ${item.contactName}: \"$finalMessage\"",
                        actionSummary = "SMS CONFIRMED"
                    )
                }

                "SEND_WHATSAPP" -> {
                    actionExecutor.launchWhatsAppMessage(finalTarget, finalMessage)
                    repository.logAction(
                        actionType = "SEND_WHATSAPP",
                        title = "Confirmed WhatsApp to ${item.contactName}",
                        targetSummary = "\"$finalMessage\"",
                        status = "CONFIRMED"
                    )
                    repository.addChatMessage(
                        role = "SYSTEM",
                        spokenText = "Confirmed: Launching WhatsApp message for ${item.contactName}: \"$finalMessage\"",
                        actionSummary = "WHATSAPP CONFIRMED"
                    )
                }

                "NAVIGATE_MAPS" -> {
                    actionExecutor.openGoogleMapsNavigation(finalTarget.ifBlank { item.contactName })
                    repository.logAction(
                        actionType = "NAVIGATE_MAPS",
                        title = "Confirmed Maps Navigation",
                        targetSummary = finalTarget.ifBlank { item.contactName },
                        status = "CONFIRMED"
                    )
                }
            }
        }
    }

    fun cancelPendingAction(item: PendingConfirmationItem) {
        viewModelScope.launch {
            _pendingConfirmations.value = _pendingConfirmations.value.filterNot { it.id == item.id }
            repository.logAction(
                actionType = item.actionType,
                title = "Cancelled ${item.title}",
                targetSummary = item.contactName,
                status = "CANCELLED"
            )
            repository.addChatMessage(
                role = "SYSTEM",
                spokenText = "Action cancelled by user: ${item.title} (${item.contactName}).",
                actionSummary = "CANCELLED"
            )
        }
    }

    // ------------------------------------------------------------------------
    // INTELLIGENT TASK AUTOMATION: "JARVIS, PREPARE MY DAY" & ROUTINES
    // ------------------------------------------------------------------------
    fun runPrepareMyDayAutomation() {
        viewModelScope.launch {
            voiceManager.setProcessingState()
            val initialSteps = listOf(
                AutomationStepStatus("Check Device Calendar", "Scanning today's schedule...", StepProgressStatus.RUNNING),
                AutomationStepStatus("Load Pending Reminders & To-Dos", "Waiting...", StepProgressStatus.PENDING),
                AutomationStepStatus("Fetch Live Weather Telemetry", "Waiting...", StepProgressStatus.PENDING),
                AutomationStepStatus("Synthesize Executive Daily Summary", "Waiting...", StepProgressStatus.PENDING)
            )
            _automationPipeline.value = AutomationPipelineState(
                routineName = "PREPARE MY DAY PROTOCOL",
                isRunning = true,
                steps = initialSteps
            )

            // Step 1: Calendar
            delay(350L)
            val calendarEvents = actionExecutor.readTodayCalendarEvents()
            val calendarSummary = if (calendarEvents.isEmpty()) {
                "Clear calendar schedule today (or calendar permission not yet granted)"
            } else {
                calendarEvents.joinToString("; ") { "${it.title} at ${it.startTimeFormatted}" }
            }

            _automationPipeline.value = _automationPipeline.value?.copy(
                steps = listOf(
                    AutomationStepStatus("Check Device Calendar", calendarSummary, StepProgressStatus.COMPLETED),
                    AutomationStepStatus("Load Pending Reminders & To-Dos", "Reading local Room vault...", StepProgressStatus.RUNNING),
                    AutomationStepStatus("Fetch Live Weather Telemetry", "Waiting...", StepProgressStatus.PENDING),
                    AutomationStepStatus("Synthesize Executive Daily Summary", "Waiting...", StepProgressStatus.PENDING)
                )
            )

            // Step 2: Pending Reminders & To-Dos
            delay(300L)
            val pendingTodos = repository.getPendingTodosSnapshot()
            val todosSummary = if (pendingTodos.isEmpty()) {
                "All tasks completed! Zero pending reminders."
            } else {
                "${pendingTodos.size} pending item(s): " + pendingTodos.take(5).joinToString("; ") { "${it.task} (${it.dueLabel})" }
            }

            _automationPipeline.value = _automationPipeline.value?.copy(
                steps = listOf(
                    AutomationStepStatus("Check Device Calendar", calendarSummary, StepProgressStatus.COMPLETED),
                    AutomationStepStatus("Load Pending Reminders & To-Dos", todosSummary, StepProgressStatus.COMPLETED),
                    AutomationStepStatus("Fetch Live Weather Telemetry", "Querying live meteorological feed...", StepProgressStatus.RUNNING),
                    AutomationStepStatus("Synthesize Executive Daily Summary", "Waiting...", StepProgressStatus.PENDING)
                )
            )

            // Step 3: Live Weather
            val city = _preferences.value.defaultCity
            val weatherRes = weatherService.fetchWeatherForCity(city)
            val weatherSummary = weatherRes.fold(
                onSuccess = { report ->
                    _liveWeather.value = report
                    "${report.locationName}: ${report.temperatureCelsius}°C, ${report.conditionDescription} (High ${report.highCelsius}°C / Low ${report.lowCelsius}°C, Wind ${report.windSpeedKmh} km/h)"
                },
                onFailure = {
                    val cached = _liveWeather.value
                    if (cached != null) {
                        "${cached.locationName}: ${cached.temperatureCelsius}°C, ${cached.conditionDescription}"
                    } else {
                        "$city: 26°C, Clear Sky"
                    }
                }
            )

            _automationPipeline.value = _automationPipeline.value?.copy(
                steps = listOf(
                    AutomationStepStatus("Check Device Calendar", calendarSummary, StepProgressStatus.COMPLETED),
                    AutomationStepStatus("Load Pending Reminders & To-Dos", todosSummary, StepProgressStatus.COMPLETED),
                    AutomationStepStatus("Fetch Live Weather Telemetry", weatherSummary, StepProgressStatus.COMPLETED),
                    AutomationStepStatus("Synthesize Executive Daily Summary", "Generating briefing with Gemini AI...", StepProgressStatus.RUNNING)
                )
            )

            // Step 4: Synthesize Daily Summary with Gemini
            val t = actionExecutor.readLiveTelemetry()
            val sysSummary = "Battery ${t.batteryPercent}%, Network: ${t.networkType}"
            val (spokenBriefing, detailedBriefing) = brainService.synthesizeDailyBriefing(
                calendarSummary = calendarSummary,
                todosSummary = todosSummary,
                weatherSummary = weatherSummary,
                batteryAndNetwork = sysSummary,
                preferredLanguage = _preferences.value.preferredLanguage
            ).getOrDefault(
                "Good morning! In $weatherSummary. You have ${pendingTodos.size} pending tasks today." to
                    "### Executive Daily Summary\n- **Calendar**: $calendarSummary\n- **Tasks**: $todosSummary\n- **Weather**: $weatherSummary\n- **Device**: $sysSummary"
            )

            _automationPipeline.value = _automationPipeline.value?.copy(
                isRunning = false,
                steps = listOf(
                    AutomationStepStatus("Check Device Calendar", calendarSummary, StepProgressStatus.COMPLETED),
                    AutomationStepStatus("Load Pending Reminders & To-Dos", todosSummary, StepProgressStatus.COMPLETED),
                    AutomationStepStatus("Fetch Live Weather Telemetry", weatherSummary, StepProgressStatus.COMPLETED),
                    AutomationStepStatus("Synthesize Executive Daily Summary", "Briefing ready & spoken", StepProgressStatus.COMPLETED)
                )
            )

            repository.logAction(
                actionType = "PREPARE_MY_DAY",
                title = "Executed Prepare My Day Routine",
                targetSummary = "${pendingTodos.size} tasks • $weatherSummary",
                status = "EXECUTED"
            )

            repository.addChatMessage(
                role = "JARVIS",
                spokenText = spokenBriefing,
                detailedText = detailedBriefing,
                language = "en",
                actionSummary = "DAILY BRIEFING COMPLETE"
            )

            voiceManager.speakResponse(spokenBriefing, "en")
        }
    }

    fun dismissAutomationPipeline() {
        _automationPipeline.value = null
    }

    fun executeCustomRoutine(routine: RoutineEntity, activity: Activity?) {
        if (routine.title.equals("Prepare My Day", ignoreCase = true)) {
            runPrepareMyDayAutomation()
            return
        }
        val steps = routine.stepsJoined.split("|").map { it.trim() }.filter { it.isNotBlank() }
        if (steps.isEmpty()) return

        viewModelScope.launch {
            repository.updateRoutine(routine.copy(lastRunAt = System.currentTimeMillis()))
            val combinedCommand = "Execute routine \"${routine.title}\": ${steps.joinToString(", then ")}"
            submitUserCommand(combinedCommand, fromVoice = false, activity = activity)
        }
    }

    // ------------------------------------------------------------------------
    // MULTIMODAL CAMERA VISION ANALYSIS
    // ------------------------------------------------------------------------
    fun analyzeCameraSnapshot(bitmap: Bitmap, prompt: String = "") {
        viewModelScope.launch {
            _isBrainThinking.value = true
            voiceManager.setProcessingState()
            repository.addChatMessage(
                role = "USER",
                spokenText = if (prompt.isNotBlank()) "📷 [Camera Snapshot] $prompt" else "📷 [Camera Snapshot] Pagi, analyze what my camera sees.",
                actionSummary = "CAMERA VISION INPUT"
            )

            val res = brainService.analyzeCapturedPhoto(bitmap, prompt)
            res.fold(
                onSuccess = { analysisText ->
                    val spoken = analysisText.lineSequence().firstOrNull { it.isNotBlank() }?.take(220)
                        ?: "Here is what I found in the captured image."
                    repository.addChatMessage(
                        role = "JARVIS",
                        spokenText = spoken,
                        detailedText = analysisText,
                        actionSummary = "VISION ANALYSIS COMPLETE"
                    )
                    repository.logAction("CAMERA_VISION", "Analyzed Camera Photo", spoken.take(60), "EXECUTED")
                    voiceManager.speakResponse(spoken, "en")
                },
                onFailure = { err ->
                    val errMsg = err.localizedMessage ?: "Vision analysis failed."
                    repository.addChatMessage(
                        role = "JARVIS",
                        spokenText = "I could not analyze the captured image: $errMsg",
                        actionSummary = "VISION ERROR"
                    )
                }
            )
            _isBrainThinking.value = false
        }
    }

    // ------------------------------------------------------------------------
    // DIRECT HARDWARE & MEMORY CRUD ACTIONS FOR UI TABS
    // ------------------------------------------------------------------------
    fun toggleFlashlightDirect() {
        viewModelScope.launch {
            val nextState = !_telemetry.value.isFlashlightOn
            val (_, msg) = actionExecutor.setFlashlight(nextState)
            repository.logAction(
                actionType = if (nextState) "FLASHLIGHT_ON" else "FLASHLIGHT_OFF",
                title = msg,
                targetSummary = "Quick Toggle",
                status = "EXECUTED"
            )
            _telemetry.value = actionExecutor.readLiveTelemetry()
            _statusBannerMessage.value = msg
        }
    }

    fun updateBrightnessDirect(activity: Activity?, percent: Int) {
        val msg = actionExecutor.setScreenBrightness(activity, percent)
        _telemetry.value = actionExecutor.readLiveTelemetry()
        viewModelScope.launch {
            repository.logAction("SET_BRIGHTNESS", msg, "$percent%", "EXECUTED")
        }
    }

    fun updateMediaVolumeDirect(percent: Int) {
        val msg = actionExecutor.setMediaVolumePercent(percent)
        _telemetry.value = actionExecutor.readLiveTelemetry()
        viewModelScope.launch {
            repository.logAction("SET_VOLUME", msg, "$percent%", "EXECUTED")
        }
    }

    fun saveMemoryFact(category: String, key: String, value: String) {
        if (key.isBlank() || value.isBlank()) return
        viewModelScope.launch {
            repository.saveOrUpdateMemory(category, key.trim(), value.trim())
            repository.logAction("SAVE_MEMORY", "Updated Personal Memory", "${key.trim()}: ${value.trim()}", "EXECUTED")
        }
    }

    fun deleteMemoryFact(id: Long) {
        viewModelScope.launch {
            repository.deleteMemory(id)
        }
    }

    fun saveNoteItem(title: String, content: String, isPinned: Boolean) {
        if (title.isBlank() && content.isBlank()) return
        viewModelScope.launch {
            repository.saveNote(
                title = title.trim().ifBlank { "Untitled Note" },
                content = content.trim(),
                isPinned = isPinned
            )
        }
    }

    fun toggleNotePinned(note: NoteEntity) {
        viewModelScope.launch {
            repository.updateNote(note.copy(isPinned = !note.isPinned))
        }
    }

    fun deleteNoteItem(id: Long) {
        viewModelScope.launch {
            repository.deleteNote(id)
        }
    }

    fun addTodoItem(task: String, dueLabel: String, priority: String) {
        if (task.isBlank()) return
        viewModelScope.launch {
            repository.addTodo(task.trim(), dueLabel.trim().ifBlank { "Today" }, priority)
        }
    }

    fun toggleTodoCompleted(todo: TodoItemEntity) {
        viewModelScope.launch {
            repository.toggleTodo(todo)
        }
    }

    fun deleteTodoItem(id: Long) {
        viewModelScope.launch {
            repository.deleteTodo(id)
        }
    }

    fun createCustomRoutine(title: String, triggerPhrase: String, steps: List<String>) {
        if (title.isBlank() || steps.isEmpty()) return
        viewModelScope.launch {
            repository.saveRoutine(title.trim(), triggerPhrase.trim().ifBlank { "Pagi, ${title.trim()}" }, steps)
        }
    }

    fun toggleRoutineEnabled(routine: RoutineEntity) {
        viewModelScope.launch {
            repository.updateRoutine(routine.copy(isEnabled = !routine.isEnabled))
        }
    }

    fun deleteRoutineItem(id: Long) {
        viewModelScope.launch {
            repository.deleteRoutine(id)
        }
    }

    fun clearConversation() {
        viewModelScope.launch {
            repository.clearChatHistory()
            repository.addChatMessage(
                role = "JARVIS",
                spokenText = "Conversation log cleared. Ready for your next command.",
                actionSummary = "MEMORY_PURGED"
            )
        }
    }

    fun clearExecutionLogs() {
        viewModelScope.launch {
            repository.clearActionLogs()
        }
    }

    override fun onCleared() {
        super.onCleared()
        voiceManager.shutdown()
        actionExecutor.cleanup()
    }
}

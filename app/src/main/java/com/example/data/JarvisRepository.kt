package com.example.data

import android.content.Context
import com.example.data.local.ActionLogEntity
import com.example.data.local.ChatMessageEntity
import com.example.data.local.JarvisDao
import com.example.data.local.MemoryFactEntity
import com.example.data.local.NoteEntity
import com.example.data.local.RoutineEntity
import com.example.data.local.TodoItemEntity
import kotlinx.coroutines.flow.Flow

data class JarvisPreferences(
    val preferredLanguage: String = "Hinglish & English", // "English", "Hindi", "Hinglish & English"
    val speechLocaleTag: String = "en-IN", // "en-IN", "hi-IN", "en-US"
    val speechRate: Float = 1.05f,
    val speechPitch: Float = 0.95f,
    val wakeWordEnabled: Boolean = true,
    val continuousConversation: Boolean = false,
    val voiceOutputEnabled: Boolean = true,
    val voiceListeningDisabled: Boolean = false,
    val defaultCity: String = "New Delhi",
    val darkTheme: Boolean = true
)

class JarvisRepository(
    context: Context,
    private val dao: JarvisDao
) {
    private val prefs = context.getSharedPreferences("jarvis_user_prefs", Context.MODE_PRIVATE)

    val messages: Flow<List<ChatMessageEntity>> = dao.getAllMessages()
    val memories: Flow<List<MemoryFactEntity>> = dao.getAllMemories()
    val notes: Flow<List<NoteEntity>> = dao.getAllNotes()
    val todos: Flow<List<TodoItemEntity>> = dao.getAllTodos()
    val routines: Flow<List<RoutineEntity>> = dao.getAllRoutines()
    val actionLogs: Flow<List<ActionLogEntity>> = dao.getAllActionLogs()

    fun loadPreferences(): JarvisPreferences {
        return JarvisPreferences(
            preferredLanguage = prefs.getString("preferred_language", "Hinglish & English") ?: "Hinglish & English",
            speechLocaleTag = prefs.getString("speech_locale", "en-IN") ?: "en-IN",
            speechRate = prefs.getFloat("speech_rate", 1.05f),
            speechPitch = prefs.getFloat("speech_pitch", 0.95f),
            wakeWordEnabled = prefs.getBoolean("wake_word_enabled", true),
            continuousConversation = prefs.getBoolean("continuous_conversation", false),
            voiceOutputEnabled = prefs.getBoolean("voice_output_enabled", true),
            voiceListeningDisabled = prefs.getBoolean("voice_listening_disabled", false),
            defaultCity = prefs.getString("default_city", "New Delhi") ?: "New Delhi",
            darkTheme = prefs.getBoolean("dark_theme", true)
        )
    }

    fun savePreferences(updated: JarvisPreferences) {
        prefs.edit()
            .putString("preferred_language", updated.preferredLanguage)
            .putString("speech_locale", updated.speechLocaleTag)
            .putFloat("speech_rate", updated.speechRate)
            .putFloat("speech_pitch", updated.speechPitch)
            .putBoolean("wake_word_enabled", updated.wakeWordEnabled)
            .putBoolean("continuous_conversation", updated.continuousConversation)
            .putBoolean("voice_output_enabled", updated.voiceOutputEnabled)
            .putBoolean("voice_listening_disabled", updated.voiceListeningDisabled)
            .putString("default_city", updated.defaultCity)
            .putBoolean("dark_theme", updated.darkTheme)
            .apply()
    }

    suspend fun ensureSeeded() {
        val alreadySeeded = prefs.getBoolean("initial_seeded_pagi_v1", false)
        if (alreadySeeded) return

        dao.clearAllMessages()

        // Welcome message
        dao.insertMessage(
            ChatMessageEntity(
                role = "JARVIS",
                spokenText = "Systems online. Good to see you! I am Pagi, your personal AI assistant. I understand English, Hindi, and Hinglish. Try saying \"Pagi, prepare my day\", \"Send Rahul a message that I will arrive in 15 minutes\", or \"Turn on flashlight and check battery\".",
                detailedText = "### Active Capabilities\n- **Voice & Wake Word**: Tap the AI Core or say *\"Hey Pagi\"*\n- **Phone Control**: Calls, SMS, WhatsApp, App Launcher, Flashlight, Brightness, Media, Settings\n- **Productivity & Automation**: Alarms, Timers, Calendar, Notes, To-Dos, Live Weather, and Multi-step Routines",
                language = "en",
                actionSummary = "BOOT_SEQUENCE_READY"
            )
        )

        // Default Personal Memories
        dao.insertMemory(
            MemoryFactEntity(
                category = "PREFERENCE",
                memoryKey = "Preferred Languages",
                memoryValue = "English, Hindi, and Hinglish"
            )
        )
        dao.insertMemory(
            MemoryFactEntity(
                category = "PREFERENCE",
                memoryKey = "Response Style",
                memoryValue = "Concise for device commands, detailed for knowledge & coding"
            )
        )
        dao.insertMemory(
            MemoryFactEntity(
                category = "PERSONAL_FACT",
                memoryKey = "Primary City",
                memoryValue = "New Delhi"
            )
        )
        dao.insertMemory(
            MemoryFactEntity(
                category = "FREQUENT_COMMAND",
                memoryKey = "Morning Briefing",
                memoryValue = "Pagi, prepare my day"
            )
        )

        // Default Notes
        dao.insertNote(
            NoteEntity(
                title = "Project Architecture Ideas",
                content = "Review voice latency benchmarks, test multilingual Hinglish wake prompts, and finalize dark HUD telemetry widgets.",
                isPinned = true
            )
        )
        dao.insertNote(
            NoteEntity(
                title = "Quick Voice Commands Reference",
                content = "• \"Pagi, prepare my day\"\n• \"Send Rahul a message that I will arrive in 15 minutes\"\n• \"Torch on karo aur battery check karo\"\n• \"Set an alarm for 6:30 AM\"",
                isPinned = false
            )
        )

        // Default To-Dos & Reminders
        dao.insertTodo(
            TodoItemEntity(
                task = "Review daily schedule & sprint deliverables",
                dueLabel = "Today, 10:00 AM",
                priority = "HIGH"
            )
        )
        dao.insertTodo(
            TodoItemEntity(
                task = "Call Rahul regarding arrival ETA",
                dueLabel = "Today, 5:30 PM",
                priority = "NORMAL"
            )
        )
        dao.insertTodo(
            TodoItemEntity(
                task = "Evening workout & hydration check",
                dueLabel = "Today, 7:00 PM",
                priority = "LOW"
            )
        )

        // Default Routines
        dao.insertRoutine(
            RoutineEntity(
                title = "Prepare My Day",
                triggerPhrase = "Pagi, prepare my day",
                stepsJoined = "Check today's device calendar|Load pending reminders & to-dos|Fetch live local weather|Generate executive daily summary",
                isEnabled = true
            )
        )
        dao.insertRoutine(
            RoutineEntity(
                title = "Commute Arrival Notice",
                triggerPhrase = "Pagi, I am on my way",
                stepsJoined = "Check battery & network status|Draft arrival message to contact|Open Google Maps navigation",
                isEnabled = true
            )
        )
        dao.insertRoutine(
            RoutineEntity(
                title = "Deep Focus Mode",
                triggerPhrase = "Pagi, activate focus mode",
                stepsJoined = "Set screen brightness to 40%|Set a 45-minute focus timer|Create focus session note",
                isEnabled = true
            )
        )

        prefs.edit().putBoolean("initial_seeded_pagi_v1", true).apply()
    }

    suspend fun addChatMessage(
        role: String,
        spokenText: String,
        detailedText: String = "",
        language: String = "en",
        actionSummary: String = ""
    ) {
        dao.insertMessage(
            ChatMessageEntity(
                role = role,
                spokenText = spokenText,
                detailedText = detailedText,
                language = language,
                actionSummary = actionSummary
            )
        )
    }

    suspend fun clearChatHistory() = dao.clearAllMessages()

    suspend fun getMemoriesSnapshot(): List<MemoryFactEntity> = dao.getMemoriesSnapshot()
    suspend fun getNotesSnapshot(): List<NoteEntity> = dao.getNotesSnapshot()
    suspend fun getPendingTodosSnapshot(): List<TodoItemEntity> = dao.getPendingTodosSnapshot()
    suspend fun getRoutinesSnapshot(): List<RoutineEntity> = dao.getRoutinesSnapshot()

    suspend fun saveOrUpdateMemory(category: String, key: String, value: String) {
        val existing = dao.findMemoryByKey(key)
        if (existing != null) {
            dao.updateMemory(
                existing.copy(
                    category = category,
                    memoryValue = value,
                    updatedAt = System.currentTimeMillis()
                )
            )
        } else {
            dao.insertMemory(
                MemoryFactEntity(
                    category = category,
                    memoryKey = key,
                    memoryValue = value
                )
            )
        }
    }

    suspend fun updateMemory(memory: MemoryFactEntity) = dao.updateMemory(memory)
    suspend fun deleteMemory(id: Long) = dao.deleteMemoryById(id)

    suspend fun saveNote(title: String, content: String, isPinned: Boolean = false) {
        dao.insertNote(NoteEntity(title = title, content = content, isPinned = isPinned))
    }

    suspend fun updateNote(note: NoteEntity) = dao.updateNote(note)
    suspend fun deleteNote(id: Long) = dao.deleteNoteById(id)

    suspend fun addTodo(task: String, dueLabel: String = "Today", priority: String = "NORMAL") {
        dao.insertTodo(TodoItemEntity(task = task, dueLabel = dueLabel, priority = priority))
    }

    suspend fun toggleTodo(todo: TodoItemEntity) {
        dao.updateTodo(todo.copy(isCompleted = !todo.isCompleted))
    }

    suspend fun deleteTodo(id: Long) = dao.deleteTodoById(id)

    suspend fun saveRoutine(title: String, triggerPhrase: String, steps: List<String>) {
        dao.insertRoutine(
            RoutineEntity(
                title = title,
                triggerPhrase = triggerPhrase,
                stepsJoined = steps.joinToString("|"),
                isEnabled = true
            )
        )
    }

    suspend fun updateRoutine(routine: RoutineEntity) = dao.updateRoutine(routine)
    suspend fun deleteRoutine(id: Long) = dao.deleteRoutineById(id)

    suspend fun logAction(
        actionType: String,
        title: String,
        targetSummary: String,
        status: String
    ) {
        dao.insertActionLog(
            ActionLogEntity(
                actionType = actionType,
                title = title,
                targetSummary = targetSummary,
                status = status
            )
        )
    }

    suspend fun clearActionLogs() = dao.clearActionLogs()
}

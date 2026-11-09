package com.example.ai

import android.graphics.Bitmap
import android.util.Log
import com.example.data.local.ChatMessageEntity
import com.example.data.local.MemoryFactEntity
import com.example.data.local.NoteEntity
import com.example.data.local.RoutineEntity
import com.example.data.local.TodoItemEntity
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject

private const val TAG = "JarvisBrainService"

data class ParsedCommandAction(
    val actionType: String,
    val targetName: String = "",
    val targetNumberOrQuery: String = "",
    val messageBody: String = "",
    val numericValue: Int = -1,
    val extraMode: String = "",
    val requiresConfirmation: Boolean = false
)

data class JarvisBrainResult(
    val spokenReply: String,
    val detailedReply: String = "",
    val detectedLanguage: String = "en",
    val needsClarification: Boolean = false,
    val actions: List<ParsedCommandAction> = emptyList(),
    val memoryKeyToSave: String = "",
    val memoryValueToSave: String = ""
)

class JarvisBrainService {

    private val actionItemSchema = Schema.obj(
        properties = mapOf(
            "actionType" to Schema.enumeration(
                values = listOf(
                    "PREPARE_MY_DAY",
                    "CALL_CONTACT",
                    "OPEN_DIALER",
                    "SEND_SMS",
                    "SEND_WHATSAPP",
                    "OPEN_APP",
                    "FLASHLIGHT_ON",
                    "FLASHLIGHT_OFF",
                    "SET_BRIGHTNESS",
                    "OPEN_WIFI_SETTINGS",
                    "OPEN_BLUETOOTH_SETTINGS",
                    "OPEN_SOUND_SETTINGS",
                    "OPEN_DISPLAY_SETTINGS",
                    "MEDIA_PLAY_PAUSE",
                    "MEDIA_NEXT",
                    "MEDIA_PREVIOUS",
                    "SET_VOLUME",
                    "CHECK_BATTERY",
                    "CHECK_NETWORK",
                    "SET_ALARM",
                    "SET_TIMER",
                    "ADD_REMINDER",
                    "CHECK_CALENDAR",
                    "ADD_CALENDAR_EVENT",
                    "CREATE_NOTE",
                    "ADD_TODO",
                    "NAVIGATE_MAPS",
                    "SEARCH_MAPS",
                    "OPEN_REAR_CAMERA",
                    "OPEN_FRONT_CAMERA",
                    "WEATHER_LOOKUP",
                    "WEB_SEARCH",
                    "SAVE_MEMORY"
                ),
                description = "Exact Android or assistant action type to execute"
            ),
            "targetName" to Schema.string(description = "Contact name, app name, note title, routine name, or location name"),
            "targetNumberOrQuery" to Schema.string(description = "Phone number, search query, destination address, or city name"),
            "messageBody" to Schema.string(description = "Drafted SMS/WhatsApp message text, note content, or reminder/to-do text"),
            "numericValue" to Schema.integer(description = "Numeric parameter: brightness (0-100), volume (0-100), alarm hour (0-23), or timer seconds"),
            "extraMode" to Schema.string(description = "Extra parameter such as alarm minute (0-59), priority (HIGH/NORMAL/LOW), or time label"),
            "requiresConfirmation" to Schema.boolean(description = "True for CALL_CONTACT, SEND_SMS, SEND_WHATSAPP, or NAVIGATE_MAPS; false for safe utilities")
        ),
        optionalProperties = listOf(
            "targetName",
            "targetNumberOrQuery",
            "messageBody",
            "numericValue",
            "extraMode"
        )
    )

    private val jarvisResponseSchema = Schema.obj(
        properties = mapOf(
            "spokenReply" to Schema.string(
                description = "Natural voice reply from PAGI in the same language (English, Hindi, or Hinglish) as the user. Concise for simple commands."
            ),
            "detailedReply" to Schema.string(
                description = "Optional detailed markdown answer for coding, math, translations, news summaries, educational help, or multi-step breakdowns."
            ),
            "detectedLanguage" to Schema.enumeration(
                values = listOf("en", "hi", "hinglish"),
                description = "Language used in the user prompt and reply"
            ),
            "needsClarification" to Schema.boolean(
                description = "True if the user's command is missing critical information (e.g., who to call or what message to send)"
            ),
            "actions" to Schema.array(
                items = actionItemSchema,
                description = "Ordered list of one or more actions to execute. Supports multiple commands in a single sentence."
            ),
            "memoryKeyToSave" to Schema.string(
                description = "If the user asked PAGI to remember a preference or personal fact, the concise key name; otherwise empty string"
            ),
            "memoryValueToSave" to Schema.string(
                description = "If the user asked PAGI to remember a preference or personal fact, the value to store; otherwise empty string"
            )
        ),
        optionalProperties = listOf("detailedReply", "memoryKeyToSave", "memoryValueToSave")
    )

    private val structuredModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = jarvisResponseSchema
            }
        )
    }

    private val generalModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash"
        )
    }

    suspend fun processUserCommand(
        userPrompt: String,
        recentHistory: List<ChatMessageEntity>,
        memories: List<MemoryFactEntity>,
        pendingTodos: List<TodoItemEntity>,
        notes: List<NoteEntity>,
        routines: List<RoutineEntity>,
        preferredLanguage: String,
        deviceTelemetrySummary: String
    ): Result<JarvisBrainResult> {
        val memoryContext = if (memories.isEmpty()) "None" else {
            memories.take(12).joinToString("; ") { "${it.memoryKey}: ${it.memoryValue}" }
        }
        val todoContext = if (pendingTodos.isEmpty()) "None" else {
            pendingTodos.take(8).joinToString("; ") { "${it.task} (${it.dueLabel})" }
        }
        val noteContext = if (notes.isEmpty()) "None" else {
            notes.take(5).joinToString("; ") { "${it.title}: ${it.content.take(80)}" }
        }
        val routineContext = if (routines.isEmpty()) "None" else {
            routines.joinToString("; ") { "\"${it.triggerPhrase}\" -> ${it.stepsJoined}" }
        }
        val historyContext = recentHistory.takeLast(6).joinToString("\n") { msg ->
            "${msg.role}: ${msg.spokenText}"
        }

        val fullPrompt = """
            You are PAGI AI (Pagi), an advanced, futuristic, loyal, and intelligent personal Android assistant.
            
            CAPABILITIES & RULES:
            1. Languages: Understand and respond fluently in English, Hindi (Devanagari or Romanized), and Hinglish. Match the user's language or preferred language ($preferredLanguage).
            2. Multi-Command Execution: If the user gives multiple commands in one sentence (e.g., "Turn on flashlight, set an alarm for 7 AM, and add a todo to buy milk"), include ALL corresponding actions in the `actions` array in order.
            3. Confirmation Rules:
               - Set `requiresConfirmation = true` for `CALL_CONTACT`, `SEND_SMS`, `SEND_WHATSAPP`, and `NAVIGATE_MAPS`.
               - Set `requiresConfirmation = false` for device utilities (`FLASHLIGHT_ON`, `FLASHLIGHT_OFF`, `SET_BRIGHTNESS`, `CHECK_BATTERY`, `CHECK_NETWORK`, `SET_ALARM`, `SET_TIMER`, `CREATE_NOTE`, `ADD_TODO`, `ADD_REMINDER`, `OPEN_APP`, `WEATHER_LOOKUP`, `PREPARE_MY_DAY`, etc.).
            4. Ambiguous Commands: If a command is unclear (e.g., "Send a message" without recipient or text), set `needsClarification = true`, leave `actions` empty, and ask a helpful clarifying question in `spokenReply`.
            5. Knowledge, Math, Coding, Translation & News:
               - Keep `spokenReply` concise and natural (1-3 sentences) so it sounds great when spoken aloud via Text-to-Speech.
               - When the user asks for detailed explanations, coding help, math step-by-step solutions, translations, or educational/general knowledge, put the complete markdown-formatted answer in `detailedReply`.
            6. Parameter Mapping for Actions:
               - `SET_ALARM`: put hour (0-23) in `numericValue` and minute (0-59) as string in `extraMode`, and label in `targetName`.
               - `SET_TIMER`: put duration in seconds in `numericValue` and timer label in `targetName`.
               - `SEND_SMS` / `SEND_WHATSAPP`: put recipient contact name in `targetName`, phone number (if provided) in `targetNumberOrQuery`, and drafted message in `messageBody`.
               - `CALL_CONTACT`: put contact name in `targetName` and phone number (if provided) in `targetNumberOrQuery`.
               - `OPEN_APP`: put application name (e.g. "YouTube", "WhatsApp", "Chrome", "Calculator", "Spotify", "Settings", "Maps", "Gmail") in `targetName`.
               - `NAVIGATE_MAPS` / `SEARCH_MAPS`: put destination or place query in `targetNumberOrQuery` and place name in `targetName`.
               - `WEATHER_LOOKUP`: put city name in `targetNumberOrQuery` (or leave empty for current city).
               - `PREPARE_MY_DAY`: use when the user says "Pagi, prepare my day", "Morning briefing", "Din ki taiyari karo", or similar daily routine commands.

            USER CONTEXT & MEMORY:
            - Saved Personal Memory: $memoryContext
            - Pending To-Dos & Reminders: $todoContext
            - Saved Notes: $noteContext
            - User Routines: $routineContext
            - Live Device Telemetry: $deviceTelemetrySummary

            RECENT CONVERSATION HISTORY:
            $historyContext

            CURRENT USER COMMAND:
            "$userPrompt"
        """.trimIndent()

        return try {
            val response = withTimeout(60_000L) {
                structuredModel.generateContent(fullPrompt)
            }
            val jsonText = response.text ?: throw IllegalStateException("Empty AI response")
            val parsed = parseStructuredJson(jsonText)
            Result.success(parsed)
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Gemini command processing timed out", e)
            Result.failure(IllegalStateException("AI request timed out after 60 seconds. Executing offline fallback if applicable."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Gemini command processing failed", e)
            Result.failure(e)
        }
    }

    suspend fun synthesizeDailyBriefing(
        calendarSummary: String,
        todosSummary: String,
        weatherSummary: String,
        batteryAndNetwork: String,
        preferredLanguage: String
    ): Result<Pair<String, String>> {
        val prompt = """
            You are PAGI AI preparing the user's daily executive briefing ("Prepare My Day").
            Preferred language style: $preferredLanguage.
            
            Here is the live data gathered from the user's Android device:
            1. Today's Calendar Schedule: $calendarSummary
            2. Pending Reminders & To-Do List: $todosSummary
            3. Live Weather Report: $weatherSummary
            4. Device Status: $batteryAndNetwork
            
            Provide:
            - First paragraph: A warm, crisp 3-4 sentence spoken executive summary suitable for Text-to-Speech.
            - Followed by "---DETAIL---" and a structured markdown breakdown with sections for Schedule, Priority Tasks, Weather & Commute Advisory, and System Readiness.
        """.trimIndent()

        return try {
            val response = withTimeout(60_000L) {
                generalModel.generateContent(prompt)
            }
            val fullText = response.text.orEmpty().trim()
            if (fullText.contains("---DETAIL---")) {
                val parts = fullText.split("---DETAIL---", limit = 2)
                Result.success(parts[0].trim() to parts[1].trim())
            } else {
                val firstSentences = fullText.lineSequence().firstOrNull { it.isNotBlank() } ?: fullText.take(220)
                Result.success(firstSentences to fullText)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Daily briefing synthesis failed", e)
            val fallbackSpoken = "Good day! Here is your briefing: $weatherSummary. You have $todosSummary."
            val fallbackDetail = "### Daily Briefing\n- **Calendar**: $calendarSummary\n- **Tasks**: $todosSummary\n- **Weather**: $weatherSummary\n- **System**: $batteryAndNetwork"
            Result.success(fallbackSpoken to fallbackDetail)
        }
    }

    suspend fun analyzeCapturedPhoto(bitmap: Bitmap, userQuestion: String): Result<String> {
        return try {
            val response = withTimeout(60_000L) {
                generalModel.generateContent(
                    content {
                        image(bitmap)
                        text(
                            "You are PAGI AI analyzing an image captured by the user's camera. " +
                                "User request: ${userQuestion.ifBlank { "Describe what you see, extract any key text or actionable details, and advise how you can help." }}"
                        )
                    }
                )
            }
            val text = response.text
            if (!text.isNullOrBlank()) {
                Result.success(text.trim())
            } else {
                Result.failure(IllegalStateException("Model returned an empty vision analysis."))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Vision analysis failed", e)
            Result.failure(e)
        }
    }

    private fun parseStructuredJson(jsonString: String): JarvisBrainResult {
        val json = JSONObject(jsonString)
        val spokenReply = json.optString("spokenReply", "Right away.")
        val detailedReply = json.optString("detailedReply", "")
        val detectedLanguage = json.optString("detectedLanguage", "en")
        val needsClarification = json.optBoolean("needsClarification", false)
        val memoryKey = json.optString("memoryKeyToSave", "")
        val memoryValue = json.optString("memoryValueToSave", "")

        val actionsList = mutableListOf<ParsedCommandAction>()
        val actionsArray: JSONArray? = json.optJSONArray("actions")
        if (actionsArray != null) {
            for (i in 0 until actionsArray.length()) {
                val item = actionsArray.optJSONObject(i) ?: continue
                val type = item.optString("actionType", "")
                if (type.isNotBlank()) {
                    actionsList.add(
                        ParsedCommandAction(
                            actionType = type,
                            targetName = item.optString("targetName", ""),
                            targetNumberOrQuery = item.optString("targetNumberOrQuery", ""),
                            messageBody = item.optString("messageBody", ""),
                            numericValue = item.optInt("numericValue", -1),
                            extraMode = item.optString("extraMode", ""),
                            requiresConfirmation = item.optBoolean(
                                "requiresConfirmation",
                                type in setOf("CALL_CONTACT", "SEND_SMS", "SEND_WHATSAPP", "NAVIGATE_MAPS")
                            )
                        )
                    )
                }
            }
        }

        return JarvisBrainResult(
            spokenReply = spokenReply,
            detailedReply = detailedReply,
            detectedLanguage = detectedLanguage,
            needsClarification = needsClarification,
            actions = actionsList,
            memoryKeyToSave = memoryKey,
            memoryValueToSave = memoryValue
        )
    }

    /**
     * Local resilient intent parser so hardware & phone controls still work if offline or if AI times out.
     */
    fun buildOfflineFallbackResult(prompt: String): JarvisBrainResult {
        val lower = prompt.lowercase().trim()
        val actions = mutableListOf<ParsedCommandAction>()

        if (lower.contains("prepare my day") || lower.contains("din ki taiyari") || lower.contains("morning briefing")) {
            actions.add(ParsedCommandAction(actionType = "PREPARE_MY_DAY"))
            return JarvisBrainResult(
                spokenReply = "Initiating your daily preparation protocol now.",
                actions = actions
            )
        }

        if (lower.contains("flashlight on") || lower.contains("torch on") || lower.contains("light jalao") || lower.contains("turn on flashlight")) {
            actions.add(ParsedCommandAction(actionType = "FLASHLIGHT_ON"))
        } else if (lower.contains("flashlight off") || lower.contains("torch off") || lower.contains("light band") || lower.contains("turn off flashlight")) {
            actions.add(ParsedCommandAction(actionType = "FLASHLIGHT_OFF"))
        }

        if (lower.contains("battery")) {
            actions.add(ParsedCommandAction(actionType = "CHECK_BATTERY"))
        }
        if (lower.contains("wifi") || lower.contains("wi-fi")) {
            actions.add(ParsedCommandAction(actionType = "OPEN_WIFI_SETTINGS"))
        }
        if (lower.contains("bluetooth")) {
            actions.add(ParsedCommandAction(actionType = "OPEN_BLUETOOTH_SETTINGS"))
        }
        if (lower.contains("selfie") || lower.contains("front camera")) {
            actions.add(ParsedCommandAction(actionType = "OPEN_FRONT_CAMERA"))
        } else if (lower.contains("camera") || lower.contains("photo")) {
            actions.add(ParsedCommandAction(actionType = "OPEN_REAR_CAMERA"))
        }

        // Send message pattern: "send <name> a message that <text>"
        val sendRegex = Regex("send\\s+([a-zA-Z]+)\\s+(?:a\\s+)?(?:whatsapp\\s+|sms\\s+)?message\\s+(?:that\\s+|saying\\s+)?(.+)", RegexOption.IGNORE_CASE)
        sendRegex.find(prompt)?.let { match ->
            val recipient = match.groupValues[1]
            val body = match.groupValues[2]
            val isWhatsApp = lower.contains("whatsapp")
            actions.add(
                ParsedCommandAction(
                    actionType = if (isWhatsApp) "SEND_WHATSAPP" else "SEND_SMS",
                    targetName = recipient,
                    messageBody = body,
                    requiresConfirmation = true
                )
            )
        }

        if (actions.isNotEmpty()) {
            return JarvisBrainResult(
                spokenReply = "Executing ${actions.size} command(s) via local tactical engine.",
                actions = actions
            )
        }

        return JarvisBrainResult(
            spokenReply = "I encountered a network issue reaching the Gemini cloud core, but local device controls (flashlight, alarms, calls, messages, notes, and routines) remain operational.",
            actions = emptyList()
        )
    }
}

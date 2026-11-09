package com.example.ai

import android.content.Context
import android.graphics.Bitmap
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Base64
import android.util.Log
import com.example.BuildConfig
import com.example.core.FirebaseSafeInitializer
import com.example.data.local.ChatMessageEntity
import com.example.data.local.MemoryFactEntity
import com.example.data.local.NoteEntity
import com.example.data.local.RoutineEntity
import com.example.data.local.TodoItemEntity
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLException

private const val TAG = "JarvisBrainService"

enum class GeminiErrorCategory {
    NETWORK_CONNECTION,
    TIMEOUT,
    INVALID_API_KEY_OR_AUTH,
    QUOTA_EXCEEDED,
    SERVER_ERROR,
    FIREBASE_CONFIG_MISSING,
    UNKNOWN
}

class GeminiServiceException(
    val category: GeminiErrorCategory,
    val userFriendlyMessage: String,
    cause: Throwable? = null
) : Exception(userFriendlyMessage, cause)

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

class JarvisBrainService(private val context: Context? = null) {

    private val httpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(45, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .build()
    }

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

    /**
     * Safely resolves an optional Gemini API key injected into BuildConfig via .env / Secrets
     * without requiring a hardcoded BuildConfig field when using Firebase AI Logic.
     */
    private fun resolveOptionalApiKey(): String? {
        return try {
            val field = BuildConfig::class.java.declaredFields.firstOrNull {
                it.name.equals("GEMINI_API_KEY", ignoreCase = true)
            } ?: return null
            field.isAccessible = true
            val value = (field.get(null) as? String)?.trim().orEmpty()
            if (value.isNotBlank() &&
                !value.equals("MY_GEMINI_API_KEY", ignoreCase = true) &&
                !value.equals("YOUR_API_KEY", ignoreCase = true) &&
                !value.startsWith("PLACEHOLDER", ignoreCase = true)
            ) {
                value
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun ensureInternetConnected() {
        val ctx = context ?: return
        val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val activeNet = cm?.activeNetwork
        val caps = activeNet?.let { cm.getNetworkCapabilities(it) }
        val hasInternet = caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        if (!hasInternet) {
            throw GeminiServiceException(
                category = GeminiErrorCategory.NETWORK_CONNECTION,
                userFriendlyMessage = "No internet connection detected. Please connect to Wi-Fi or mobile data and try again."
            )
        }
    }

    private fun isFirebaseReady(): Boolean {
        val ctx = context ?: return false
        return FirebaseSafeInitializer.ensureInitialized(ctx) && FirebaseApp.getApps(ctx).isNotEmpty()
    }

    fun classifyException(error: Throwable): GeminiServiceException {
        if (error is GeminiServiceException) return error

        val msg = error.message.orEmpty()
        val lower = msg.lowercase()

        return when {
            error is TimeoutCancellationException || error is SocketTimeoutException || lower.contains("timed out") || lower.contains("timeout") -> {
                GeminiServiceException(
                    category = GeminiErrorCategory.TIMEOUT,
                    userFriendlyMessage = "Gemini AI request timed out. Please check your connection speed and try again.",
                    cause = error
                )
            }

            error is UnknownHostException || error is ConnectException || error is SSLException || error is IOException ||
                lower.contains("unable to resolve host") || lower.contains("failed to connect") || lower.contains("network is unreachable") -> {
                GeminiServiceException(
                    category = GeminiErrorCategory.NETWORK_CONNECTION,
                    userFriendlyMessage = "Network connection error: Unable to reach Google Gemini servers. Please check your Wi-Fi or mobile data connection.",
                    cause = error
                )
            }

            lower.contains("default firebaseapp is not initialized") -> {
                GeminiServiceException(
                    category = GeminiErrorCategory.FIREBASE_CONFIG_MISSING,
                    userFriendlyMessage = "Firebase is not initialized because app/google-services.json was missing when the APK was built. Add app/google-services.json or configure GEMINI_API_KEY in Secrets.",
                    cause = error
                )
            }

            lower.contains("429") || lower.contains("quota") || lower.contains("resource_exhausted") || lower.contains("rate limit") -> {
                GeminiServiceException(
                    category = GeminiErrorCategory.QUOTA_EXCEEDED,
                    userFriendlyMessage = "Gemini API quota or rate limit exceeded (HTTP 429). Please wait a moment and try again.",
                    cause = error
                )
            }

            lower.contains("401") || lower.contains("403") || lower.contains("api_key_invalid") ||
                lower.contains("api key not valid") || lower.contains("permission_denied") ||
                lower.contains("app check") || lower.contains("attestation") || lower.contains("unauthenticated") -> {
                GeminiServiceException(
                    category = GeminiErrorCategory.INVALID_API_KEY_OR_AUTH,
                    userFriendlyMessage = "Authentication / App Check error (HTTP 401/403): Ensure your APK signing key (SHA-1/SHA-256) and app/google-services.json match your Firebase project, or configure a valid GEMINI_API_KEY in Secrets.",
                    cause = error
                )
            }

            lower.contains("500") || lower.contains("502") || lower.contains("503") || lower.contains("504") ||
                lower.contains("internal") || lower.contains("unavailable") || lower.contains("overloaded") -> {
                GeminiServiceException(
                    category = GeminiErrorCategory.SERVER_ERROR,
                    userFriendlyMessage = "Google Gemini server error (HTTP 5xx): The AI service is temporarily unavailable. Please try again shortly.",
                    cause = error
                )
            }

            else -> {
                GeminiServiceException(
                    category = GeminiErrorCategory.UNKNOWN,
                    userFriendlyMessage = "Gemini AI error: ${error.localizedMessage ?: "Unexpected response from cloud service."}",
                    cause = error
                )
            }
        }
    }

    /**
     * Direct REST call via OkHttp when a GEMINI_API_KEY is configured in .env / BuildConfig
     * and Firebase AI Logic is either unconfigured in a standalone CI build or rejected by App Check on an external device.
     */
    private suspend fun callGeminiRestApi(
        prompt: String,
        apiKey: String,
        expectJson: Boolean = false,
        inlineBitmap: Bitmap? = null
    ): String = withContext(Dispatchers.IO) {
        val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=$apiKey"

        val partsArray = JSONArray()
        if (inlineBitmap != null) {
            val stream = ByteArrayOutputStream()
            inlineBitmap.compress(Bitmap.CompressFormat.JPEG, 85, stream)
            val base64Image = Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
            partsArray.put(
                JSONObject().put(
                    "inline_data",
                    JSONObject()
                        .put("mime_type", "image/jpeg")
                        .put("data", base64Image)
                )
            )
        }
        partsArray.put(JSONObject().put("text", prompt))

        val payload = JSONObject().apply {
            put(
                "contents",
                JSONArray().put(
                    JSONObject().put("parts", partsArray)
                )
            )
            if (expectJson) {
                put(
                    "generationConfig",
                    JSONObject().put("responseMimeType", "application/json")
                )
            }
        }

        val request = Request.Builder()
            .url(url)
            .post(payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()

        httpClient.newCall(request).execute().use { response ->
            val responseBody = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val apiErrorMsg = try {
                    JSONObject(responseBody).optJSONObject("error")?.optString("message").orEmpty()
                } catch (_: Exception) {
                    ""
                }
                val detail = apiErrorMsg.ifBlank { "HTTP ${response.code}" }
                throw when (response.code) {
                    400 -> GeminiServiceException(
                        GeminiErrorCategory.INVALID_API_KEY_OR_AUTH,
                        "Invalid Gemini API request or key (HTTP 400): $detail"
                    )
                    401, 403 -> GeminiServiceException(
                        GeminiErrorCategory.INVALID_API_KEY_OR_AUTH,
                        "Invalid or unauthorized Gemini API key (HTTP ${response.code}): $detail"
                    )
                    429 -> GeminiServiceException(
                        GeminiErrorCategory.QUOTA_EXCEEDED,
                        "Gemini API quota or rate limit exceeded (HTTP 429): $detail"
                    )
                    in 500..599 -> GeminiServiceException(
                        GeminiErrorCategory.SERVER_ERROR,
                        "Gemini server error (HTTP ${response.code}): $detail"
                    )
                    else -> GeminiServiceException(
                        GeminiErrorCategory.UNKNOWN,
                        "Gemini API HTTP ${response.code}: $detail"
                    )
                }
            }

            val root = JSONObject(responseBody)
            val candidates = root.optJSONArray("candidates")
            val firstCandidate = candidates?.optJSONObject(0)
            val contentObj = firstCandidate?.optJSONObject("content")
            val parts = contentObj?.optJSONArray("parts")
            val text = parts?.optJSONObject(0)?.optString("text").orEmpty()
            if (text.isBlank()) {
                throw GeminiServiceException(
                    GeminiErrorCategory.UNKNOWN,
                    "Gemini API returned an empty response payload."
                )
            }
            text
        }
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
            7. JSON Schema Format: Return a JSON object with keys: "spokenReply" (string), "detailedReply" (string), "detectedLanguage" ("en"|"hi"|"hinglish"), "needsClarification" (boolean), "actions" (array of objects with "actionType", "targetName", "targetNumberOrQuery", "messageBody", "numericValue", "extraMode", "requiresConfirmation"), "memoryKeyToSave" (string), "memoryValueToSave" (string).

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
            ensureInternetConnected()
            val firebaseReady = isFirebaseReady()
            val optionalApiKey = resolveOptionalApiKey()

            val jsonText = if (firebaseReady) {
                try {
                    val response = withTimeout(60_000L) {
                        structuredModel.generateContent(fullPrompt)
                    }
                    response.text ?: throw IllegalStateException("Gemini returned an empty response.")
                } catch (firebaseErr: Exception) {
                    if (firebaseErr is CancellationException && firebaseErr !is TimeoutCancellationException) {
                        throw firebaseErr
                    }
                    if (!optionalApiKey.isNullOrBlank()) {
                        Log.w(TAG, "Firebase AI Logic call failed; falling back to configured GEMINI_API_KEY REST endpoint.", firebaseErr)
                        callGeminiRestApi(fullPrompt, optionalApiKey, expectJson = true)
                    } else {
                        throw firebaseErr
                    }
                }
            } else if (!optionalApiKey.isNullOrBlank()) {
                callGeminiRestApi(fullPrompt, optionalApiKey, expectJson = true)
            } else {
                throw GeminiServiceException(
                    category = GeminiErrorCategory.FIREBASE_CONFIG_MISSING,
                    userFriendlyMessage = "Firebase configuration (app/google-services.json) was not bundled when this APK was compiled. Include app/google-services.json in your repository or configure GEMINI_API_KEY in Secrets."
                )
            }

            val parsed = parseStructuredJson(jsonText)
            Result.success(parsed)
        } catch (e: CancellationException) {
            if (e is TimeoutCancellationException) {
                val classified = classifyException(e)
                Log.e(TAG, "Gemini command processing timed out: ${classified.userFriendlyMessage}", e)
                Result.failure(classified)
            } else {
                throw e
            }
        } catch (e: Exception) {
            val classified = classifyException(e)
            Log.e(TAG, "Gemini command processing failed [${classified.category}]: ${classified.userFriendlyMessage}", e)
            Result.failure(classified)
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
            ensureInternetConnected()
            val firebaseReady = isFirebaseReady()
            val optionalApiKey = resolveOptionalApiKey()

            val fullText = if (firebaseReady) {
                try {
                    val response = withTimeout(60_000L) {
                        generalModel.generateContent(prompt)
                    }
                    response.text.orEmpty().trim()
                } catch (firebaseErr: Exception) {
                    if (firebaseErr is CancellationException && firebaseErr !is TimeoutCancellationException) {
                        throw firebaseErr
                    }
                    if (!optionalApiKey.isNullOrBlank()) {
                        callGeminiRestApi(prompt, optionalApiKey, expectJson = false).trim()
                    } else {
                        throw firebaseErr
                    }
                }
            } else if (!optionalApiKey.isNullOrBlank()) {
                callGeminiRestApi(prompt, optionalApiKey, expectJson = false).trim()
            } else {
                throw GeminiServiceException(
                    category = GeminiErrorCategory.FIREBASE_CONFIG_MISSING,
                    userFriendlyMessage = "Firebase configuration missing."
                )
            }

            if (fullText.contains("---DETAIL---")) {
                val parts = fullText.split("---DETAIL---", limit = 2)
                Result.success(parts[0].trim() to parts[1].trim())
            } else {
                val firstSentences = fullText.lineSequence().firstOrNull { it.isNotBlank() } ?: fullText.take(220)
                Result.success(firstSentences to fullText)
            }
        } catch (e: CancellationException) {
            if (e !is TimeoutCancellationException) throw e
            val fallbackSpoken = "Good day! Here is your briefing: $weatherSummary. You have $todosSummary."
            val fallbackDetail = "### Daily Briefing\n- **Calendar**: $calendarSummary\n- **Tasks**: $todosSummary\n- **Weather**: $weatherSummary\n- **System**: $batteryAndNetwork"
            Result.success(fallbackSpoken to fallbackDetail)
        } catch (e: Exception) {
            Log.e(TAG, "Daily briefing synthesis fallback used", e)
            val fallbackSpoken = "Good day! Here is your briefing: $weatherSummary. You have $todosSummary."
            val fallbackDetail = "### Daily Briefing\n- **Calendar**: $calendarSummary\n- **Tasks**: $todosSummary\n- **Weather**: $weatherSummary\n- **System**: $batteryAndNetwork"
            Result.success(fallbackSpoken to fallbackDetail)
        }
    }

    suspend fun analyzeCapturedPhoto(bitmap: Bitmap, userQuestion: String): Result<String> {
        val visionPrompt = "You are PAGI AI analyzing an image captured by the user's camera. " +
            "User request: ${userQuestion.ifBlank { "Describe what you see, extract any key text or actionable details, and advise how you can help." }}"
        return try {
            ensureInternetConnected()
            val firebaseReady = isFirebaseReady()
            val optionalApiKey = resolveOptionalApiKey()

            val text = if (firebaseReady) {
                try {
                    val response = withTimeout(60_000L) {
                        generalModel.generateContent(
                            content {
                                image(bitmap)
                                text(visionPrompt)
                            }
                        )
                    }
                    response.text.orEmpty()
                } catch (firebaseErr: Exception) {
                    if (firebaseErr is CancellationException && firebaseErr !is TimeoutCancellationException) {
                        throw firebaseErr
                    }
                    if (!optionalApiKey.isNullOrBlank()) {
                        callGeminiRestApi(visionPrompt, optionalApiKey, expectJson = false, inlineBitmap = bitmap)
                    } else {
                        throw firebaseErr
                    }
                }
            } else if (!optionalApiKey.isNullOrBlank()) {
                callGeminiRestApi(visionPrompt, optionalApiKey, expectJson = false, inlineBitmap = bitmap)
            } else {
                throw GeminiServiceException(
                    category = GeminiErrorCategory.FIREBASE_CONFIG_MISSING,
                    userFriendlyMessage = "Firebase configuration (app/google-services.json) or GEMINI_API_KEY is required for camera vision analysis."
                )
            }

            if (text.isNotBlank()) {
                Result.success(text.trim())
            } else {
                Result.failure(
                    GeminiServiceException(
                        GeminiErrorCategory.UNKNOWN,
                        "Gemini Vision returned an empty response."
                    )
                )
            }
        } catch (e: CancellationException) {
            if (e is TimeoutCancellationException) {
                Result.failure(classifyException(e))
            } else {
                throw e
            }
        } catch (e: Exception) {
            val classified = classifyException(e)
            Log.e(TAG, "Vision analysis failed: ${classified.userFriendlyMessage}", e)
            Result.failure(classified)
        }
    }

    private fun parseStructuredJson(jsonString: String): JarvisBrainResult {
        val cleanedJson = jsonString
            .trim()
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()
        val json = JSONObject(cleanedJson)
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
     * Local resilient intent parser so hardware & phone controls still work if offline or if AI times out,
     * while surfacing specific diagnostic error messages for cloud queries.
     */
    fun buildOfflineFallbackResult(
        prompt: String,
        diagnosticError: String? = null
    ): JarvisBrainResult {
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

        val specificReason = diagnosticError?.takeIf { it.isNotBlank() }
            ?: "Unable to reach the Gemini AI service. Please verify your internet connection and Firebase configuration."

        return JarvisBrainResult(
            spokenReply = specificReason,
            detailedReply = "### Diagnostic Details\n- **Issue**: $specificReason\n- **Local Controls Available**: Flashlight, Battery & Network checks, Calls, SMS/WhatsApp, Alarms, Timers, Notes, To-Dos, and Routines remain fully operational.",
            actions = emptyList()
        )
    }
}

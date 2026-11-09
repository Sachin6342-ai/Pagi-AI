package com.example.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import kotlin.math.abs
import kotlin.math.sin

private const val TAG = "JarvisVoiceManager"

enum class VoiceCoreState {
    IDLE,
    WAKE_WORD_STANDBY,
    LISTENING_COMMAND,
    PROCESSING,
    SPEAKING,
    MUTED_PRIVACY
}

class JarvisVoiceManager(
    private val context: Context,
    private val onCommandRecognized: (String) -> Unit,
    private val onWakeWordTriggered: () -> Unit,
    private val onSpeechStatusMessage: (String) -> Unit
) : TextToSpeech.OnInitListener {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var speechRecognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var isTtsInitialized = false

    private val _coreState = MutableStateFlow(VoiceCoreState.IDLE)
    val coreState: StateFlow<VoiceCoreState> = _coreState.asStateFlow()

    private val _partialTranscript = MutableStateFlow("")
    val partialTranscript: StateFlow<String> = _partialTranscript.asStateFlow()

    private val _waveformLevels = MutableStateFlow(List(24) { 0.12f })
    val waveformLevels: StateFlow<List<Float>> = _waveformLevels.asStateFlow()

    private var speechLocaleTag: String = "en-IN"
    private var speechRate: Float = 1.05f
    private var speechPitch: Float = 0.95f
    private var wakeWordEnabled: Boolean = true
    private var continuousMode: Boolean = false
    private var voiceOutputEnabled: Boolean = true
    private var voiceListeningDisabled: Boolean = false
    private var hasRecordAudioPermission: Boolean = false

    private var isAwaitingDirectCommand = false
    private var speakingWaveTick = 0f

    private val speakingWaveRunnable = object : Runnable {
        override fun run() {
            if (_coreState.value == VoiceCoreState.SPEAKING) {
                speakingWaveTick += 0.35f
                val bars = List(24) { idx ->
                    val wave1 = abs(sin(speakingWaveTick + idx * 0.45f))
                    val wave2 = abs(sin(speakingWaveTick * 0.7f - idx * 0.25f))
                    (0.18f + (wave1 * 0.55f + wave2 * 0.27f)).coerceIn(0.12f, 1.0f)
                }
                _waveformLevels.value = bars
                mainHandler.postDelayed(this, 60L)
            }
        }
    }

    init {
        try {
            tts = TextToSpeech(context.applicationContext, this)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize TextToSpeech", e)
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            isTtsInitialized = true
            applyTtsSettings()
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    mainHandler.post {
                        _coreState.value = VoiceCoreState.SPEAKING
                        mainHandler.removeCallbacks(speakingWaveRunnable)
                        mainHandler.post(speakingWaveRunnable)
                    }
                }

                override fun onDone(utteranceId: String?) {
                    mainHandler.post {
                        mainHandler.removeCallbacks(speakingWaveRunnable)
                        resetWaveform()
                        if (voiceListeningDisabled) {
                            _coreState.value = VoiceCoreState.MUTED_PRIVACY
                        } else if (continuousMode && hasRecordAudioPermission) {
                            mainHandler.postDelayed({ startDirectCommandListening() }, 450L)
                        } else if (wakeWordEnabled && hasRecordAudioPermission) {
                            mainHandler.postDelayed({ startWakeWordStandby() }, 450L)
                        } else {
                            _coreState.value = VoiceCoreState.IDLE
                        }
                    }
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    mainHandler.post {
                        mainHandler.removeCallbacks(speakingWaveRunnable)
                        resetWaveform()
                        _coreState.value = if (voiceListeningDisabled) VoiceCoreState.MUTED_PRIVACY else VoiceCoreState.IDLE
                    }
                }
            })
        }
    }

    fun updateConfiguration(
        localeTag: String,
        rate: Float,
        pitch: Float,
        wakeEnabled: Boolean,
        continuous: Boolean,
        outputEnabled: Boolean,
        listeningDisabled: Boolean,
        audioPermGranted: Boolean
    ) {
        speechLocaleTag = localeTag
        speechRate = rate
        speechPitch = pitch
        wakeWordEnabled = wakeEnabled
        continuousMode = continuous
        voiceOutputEnabled = outputEnabled
        voiceListeningDisabled = listeningDisabled
        hasRecordAudioPermission = audioPermGranted

        applyTtsSettings()

        if (voiceListeningDisabled) {
            stopListening()
            _coreState.value = VoiceCoreState.MUTED_PRIVACY
        } else if (_coreState.value == VoiceCoreState.MUTED_PRIVACY) {
            _coreState.value = VoiceCoreState.IDLE
        }
    }

    private fun applyTtsSettings() {
        if (!isTtsInitialized) return
        val locale = Locale.forLanguageTag(speechLocaleTag)
        tts?.language = locale
        tts?.setSpeechRate(speechRate.coerceIn(0.5f, 2.0f))
        tts?.setPitch(speechPitch.coerceIn(0.5f, 1.5f))
    }

    /**
     * Barge-in support: stops any ongoing TTS output immediately so the user can interrupt JARVIS.
     */
    fun interruptSpeaking() {
        mainHandler.removeCallbacks(speakingWaveRunnable)
        if (tts?.isSpeaking == true) {
            tts?.stop()
        }
        resetWaveform()
        if (_coreState.value == VoiceCoreState.SPEAKING) {
            _coreState.value = if (voiceListeningDisabled) VoiceCoreState.MUTED_PRIVACY else VoiceCoreState.IDLE
        }
    }

    fun speakResponse(text: String, detectedLanguage: String = "en") {
        if (!voiceOutputEnabled || !isTtsInitialized || text.isBlank()) {
            if (!voiceListeningDisabled && continuousMode && hasRecordAudioPermission) {
                mainHandler.postDelayed({ startDirectCommandListening() }, 400L)
            } else if (!voiceListeningDisabled && wakeWordEnabled && hasRecordAudioPermission) {
                mainHandler.postDelayed({ startWakeWordStandby() }, 400L)
            } else {
                _coreState.value = if (voiceListeningDisabled) VoiceCoreState.MUTED_PRIVACY else VoiceCoreState.IDLE
            }
            return
        }

        // Stop active mic before speaking to avoid acoustic feedback loop
        destroyRecognizerOnMainThread()

        val targetLocale = when (detectedLanguage) {
            "hi" -> Locale.forLanguageTag("hi-IN")
            "hinglish" -> Locale.forLanguageTag("en-IN")
            else -> Locale.forLanguageTag(speechLocaleTag)
        }
        tts?.language = targetLocale
        tts?.setSpeechRate(speechRate.coerceIn(0.5f, 2.0f))
        tts?.setPitch(speechPitch.coerceIn(0.5f, 1.5f))

        val cleanSpeech = text
            .replace(Regex("[*#_`]"), "")
            .replace(Regex("\\s+"), " ")
            .trim()

        _coreState.value = VoiceCoreState.SPEAKING
        tts?.speak(cleanSpeech, TextToSpeech.QUEUE_FLUSH, null, "jarvis_utt_${System.currentTimeMillis()}")
    }

    fun setProcessingState() {
        interruptSpeaking()
        destroyRecognizerOnMainThread()
        _coreState.value = VoiceCoreState.PROCESSING
    }

    fun startDirectCommandListening() {
        if (voiceListeningDisabled) {
            _coreState.value = VoiceCoreState.MUTED_PRIVACY
            onSpeechStatusMessage("Voice listening is muted in privacy settings.")
            return
        }
        if (!hasRecordAudioPermission) {
            onSpeechStatusMessage("Microphone permission is required for voice commands.")
            return
        }
        interruptSpeaking()
        isAwaitingDirectCommand = true
        _partialTranscript.value = ""
        _coreState.value = VoiceCoreState.LISTENING_COMMAND
        launchRecognizer()
    }

    fun startWakeWordStandby() {
        if (voiceListeningDisabled) {
            _coreState.value = VoiceCoreState.MUTED_PRIVACY
            return
        }
        if (!hasRecordAudioPermission || !wakeWordEnabled) {
            _coreState.value = VoiceCoreState.IDLE
            return
        }
        if (_coreState.value == VoiceCoreState.SPEAKING || _coreState.value == VoiceCoreState.PROCESSING) {
            return
        }
        isAwaitingDirectCommand = false
        _partialTranscript.value = ""
        _coreState.value = VoiceCoreState.WAKE_WORD_STANDBY
        launchRecognizer()
    }

    fun stopListening() {
        mainHandler.removeCallbacksAndMessages(null)
        destroyRecognizerOnMainThread()
        resetWaveform()
        _partialTranscript.value = ""
        _coreState.value = if (voiceListeningDisabled) VoiceCoreState.MUTED_PRIVACY else VoiceCoreState.IDLE
    }

    private fun launchRecognizer() {
        mainHandler.post {
            try {
                destroyRecognizerOnMainThread()
                if (!SpeechRecognizer.isRecognitionAvailable(context)) {
                    onSpeechStatusMessage("Speech recognition service is unavailable on this device/emulator. You can still use the command bar or Quick Actions.")
                    _coreState.value = VoiceCoreState.IDLE
                    return@post
                }

                val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
                speechRecognizer = recognizer

                recognizer.setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        _coreState.value = if (isAwaitingDirectCommand) {
                            VoiceCoreState.LISTENING_COMMAND
                        } else {
                            VoiceCoreState.WAKE_WORD_STANDBY
                        }
                    }

                    override fun onBeginningOfSpeech() {}

                    override fun onRmsChanged(rmsdB: Float) {
                        val normalized = ((rmsdB + 2f) / 12f).coerceIn(0.08f, 1.0f)
                        val current = _waveformLevels.value.toMutableList()
                        if (current.isNotEmpty()) {
                            current.removeAt(0)
                            current.add(normalized)
                            _waveformLevels.value = current
                        }
                    }

                    override fun onBufferReceived(buffer: ByteArray?) {}

                    override fun onEndOfSpeech() {
                        resetWaveform()
                    }

                    override fun onError(error: Int) {
                        resetWaveform()
                        // If in wake-word standby, quietly restart standby after a short backoff
                        if (!isAwaitingDirectCommand && wakeWordEnabled && !voiceListeningDisabled && hasRecordAudioPermission) {
                            mainHandler.postDelayed({
                                if (_coreState.value == VoiceCoreState.WAKE_WORD_STANDBY) {
                                    launchRecognizer()
                                }
                            }, 1200L)
                        } else {
                            _coreState.value = if (voiceListeningDisabled) VoiceCoreState.MUTED_PRIVACY else VoiceCoreState.IDLE
                        }
                    }

                    override fun onResults(results: Bundle?) {
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val transcript = matches?.firstOrNull()?.trim().orEmpty()
                        _partialTranscript.value = ""
                        handleRecognizedSpeech(transcript)
                    }

                    override fun onPartialResults(partialResults: Bundle?) {
                        val partial = partialResults
                            ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            ?.firstOrNull()
                            ?.trim()
                            .orEmpty()
                        if (partial.isNotBlank()) {
                            _partialTranscript.value = partial
                            if (!isAwaitingDirectCommand && containsWakeWord(partial)) {
                                isAwaitingDirectCommand = true
                                _coreState.value = VoiceCoreState.LISTENING_COMMAND
                                onWakeWordTriggered()
                            }
                        }
                    }

                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })

                val recognizerIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, speechLocaleTag)
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                }
                recognizer.startListening(recognizerIntent)
            } catch (e: Exception) {
                Log.e(TAG, "Error starting SpeechRecognizer", e)
                _coreState.value = VoiceCoreState.IDLE
            }
        }
    }

    private fun handleRecognizedSpeech(transcript: String) {
        if (transcript.isBlank()) {
            if (!isAwaitingDirectCommand && wakeWordEnabled && !voiceListeningDisabled) {
                startWakeWordStandby()
            } else {
                _coreState.value = VoiceCoreState.IDLE
            }
            return
        }

        if (isAwaitingDirectCommand) {
            val cleaned = stripWakeWordPrefix(transcript).ifBlank { transcript }
            onCommandRecognized(cleaned)
        } else if (containsWakeWord(transcript)) {
            onWakeWordTriggered()
            val remainder = stripWakeWordPrefix(transcript)
            if (remainder.length > 2) {
                onCommandRecognized(remainder)
            } else {
                // User just said "Hey Pagi" -> switch to direct command listening!
                startDirectCommandListening()
            }
        } else if (wakeWordEnabled && !voiceListeningDisabled) {
            startWakeWordStandby()
        } else {
            _coreState.value = VoiceCoreState.IDLE
        }
    }

    private fun containsWakeWord(text: String): Boolean {
        val lower = text.lowercase()
        return lower.contains("hey pagi") ||
            lower.contains("hi pagi") ||
            lower.contains("ok pagi") ||
            lower.startsWith("pagi") ||
            lower.contains("पागी") ||
            lower.contains("hey jarvis") ||
            lower.startsWith("jarvis")
    }

    private fun stripWakeWordPrefix(text: String): String {
        return text.replace(
            Regex("^(hey\\s+pagi|hi\\s+pagi|ok\\s+pagi|pagi|हे\\s+पागी|पागी|hey\\s+jarvis|jarvis)[,.:!\\s]*", RegexOption.IGNORE_CASE),
            ""
        ).trim()
    }

    private fun resetWaveform() {
        _waveformLevels.value = List(24) { 0.12f }
    }

    private fun destroyRecognizerOnMainThread() {
        try {
            speechRecognizer?.stopListening()
            speechRecognizer?.cancel()
            speechRecognizer?.destroy()
            speechRecognizer = null
        } catch (e: Exception) {
            Log.w(TAG, "Recognizer cleanup warning", e)
        }
    }

    fun shutdown() {
        mainHandler.removeCallbacksAndMessages(null)
        destroyRecognizerOnMainThread()
        try {
            tts?.stop()
            tts?.shutdown()
        } catch (e: Exception) {
            Log.w(TAG, "TTS shutdown warning", e)
        }
    }
}

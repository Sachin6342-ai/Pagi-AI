# JARVIS AI – Advanced Personal Android AI Assistant

JARVIS AI is a voice-first, futuristic personal Android assistant powered by Google Gemini via Firebase AI Logic. It understands natural English, Hindi, and Hinglish commands, conducts real-time voice conversations, automates multi-step daily workflows, and executes native Android device controls, communications, and productivity actions with transparent user confirmation and privacy safeguards.

## User Review & Critical Decisions

> [!IMPORTANT]
> Because the initial clarification questions were skipped, we have selected the recommended defaults below so you get an immediately responsive, privacy-first assistant without mandatory login walls. Please review these choices before we begin building:

- **Confirmed Default — Local Personal Memory (Room Database)**: All personal memory (user preferences, saved notes, to-do lists, custom routines, and task execution history) is stored locally on the device using Android Room SQLite with full view, edit, and delete controls—no external user account sign-in is required.
- **Confirmed Default — Active Foreground Wake-Word & Hands-Free Mode**: Voice listening supports both a tap-to-speak AI Core button and a toggleable **Continuous / "Hey Jarvis" Wake-Word Mode** while the app is open, paired with an always-visible microphone status badge and instant voice interruption support.
- **Confirmed Default — Tiered Action Confirmation**: High-impact actions (placing phone calls, sending SMS or WhatsApp messages, launching turn-by-turn navigation, and deleting personal data) present an interactive confirmation card in the assistant stream before launching, whereas low-risk utilities (flashlight toggle, battery/network readout, setting alarms/timers, saving notes/to-dos, and opening settings panels) execute directly with immediate visual and spoken feedback.
- **Firebase AI Logic Provisioning Gate**: Once you approve this plan, the first build step will display the AI Studio Firebase setup prompt to provision **Firebase AI Logic (`gemini-3.8-flash`)** and **App Check** so live Gemini inference works out of the box.

---

## 1. Overview & Core Concept

- **What It Does**: Combines an intelligent conversational brain (`gemini-3.8-flash`) with an Android Intent & Hardware Execution Engine. Users can speak or type natural commands—ranging from single utilities (*"Torch on karo"*, *"Set an alarm for 6:30 AM"*) to multi-step routines (*"Jarvis, prepare my day"* or *"Send Rahul a message that I will arrive in 15 minutes"*)—and watch JARVIS parse, confirm, and execute each step in real time.
- **Target Audience / Persona**: Power users, productivity enthusiasts, and bilingual (English / Hindi / Hinglish) Android users who want a responsive, hands-free command center that unifies voice AI, device hardware controls, and personal memory.
- **Key Value**: Bridges conversational AI with real Android system capabilities—turning natural speech into structured, verifiable phone actions, live weather/knowledge briefings, and customizable multi-step automations.

---

## 2. User Experience & Visual Design

### Key User Flows

1. **Voice & Chat Command Hub (Home HUD)**:
   - Opening the app reveals the central **Animated Circular AI Core** surrounded by real-time telemetry badges (Battery %, Network status, Mic state, Active Language mode).
   - Tapping the AI Core—or speaking *"Hey Jarvis"* when hands-free wake mode is toggled on—transitions the core into a **Listening State** with a live multi-bar audio waveform reactive to microphone RMS decibels.
   - As JARVIS processes and replies, the core shifts to a **Speaking / Synthesizing State**, streaming the response text while speaking aloud via Android `TextToSpeech` (with support for instant barge-in interruption if the user taps or speaks again).
2. **Multi-Step Automation & Confirmation Cards**:
   - When a user issues a compound command (e.g., *"Jarvis, prepare my day"*), an interactive **Task Execution Pipeline Card** appears in the conversation feed, displaying real-time checkmarks as each step runs:
     1. Reading today's device calendar events (with permission check).
     2. Loading pending local reminders, notes, and to-do items.
     3. Fetching live local weather conditions and forecast.
     4. Synthesizing a spoken and visual executive daily briefing.
   - When a command involves calling a contact, sending an SMS/WhatsApp message, or starting Google Maps navigation, JARVIS resolves the contact/destination, drafts the payload, and presents a **Confirmation Card** with **Confirm & Launch**, **Edit**, and **Cancel** buttons.
3. **Quick Action Dock & Device Control Deck**:
   - A horizontal quick-action rail and expandable **Systems Control Sheet** allow one-tap or voice control over Flashlight (`CameraManager` torch), Screen Brightness (interactive slider + system write-settings permission flow), Wi-Fi & Bluetooth settings panels, Media Playback controls (`AudioManager` play/pause/next/previous & volume), Sound/Display settings, and Front/Rear Camera launch.
4. **Personal Memory, Productivity & Routines Vault**:
   - Dedicated tabs let users inspect, add, edit, and delete what JARVIS remembers:
     - **Memory & Preferences**: User name, preferred language (English, Hindi, Hinglish), preferred response style, and learned facts.
     - **Productivity Hub**: Local Notes, To-Do checklists, Reminders, and quick launchers for System Alarms & Timers.
     - **Custom Routines**: User-defined trigger phrases mapped to multi-step command sequences (pre-loaded with *"Prepare my day"*, *"Focus mode"*, and *"Commute briefing"*).
5. **Task Execution History & Settings Dashboard**:
   - **Audit Log**: Chronological log of every device action and intent executed by JARVIS with status badges (`Executed`, `Confirmed`, `Cancelled`, `Permission Needed`).
   - **Settings Dashboard**: Voice pitch and speaking speed sliders, language selector (`en-IN`, `hi-IN`, `en-US`), Continuous Conversation & Wake-Word toggles, Voice Mute switch, and Theme selector (Futuristic Dark HUD vs. High-Contrast Daylight HUD).

### Visual Identity & Theme

- **Aesthetic Direction**: **Tactical Holographic HUD**—inspired by high-precision aerospace telemetry and futuristic command consoles. Uses crisp geometric framing, subtle radial core glow, and high-contrast data typography rather than generic chat bubbles.
- **Color Palette & Mood**:
  - **Dark HUD Theme (Default)**:
    - Background Surface: Deep Abyssal Navy (`#060B16`) with elevated HUD card surfaces (`#0D1627` and `#132038`).
    - Primary Accent: Arc Reactor Cyan (`#00E5FF`) for the AI core, active waveforms, and primary interactive controls.
    - Secondary Accent: Neural Violet (`#7C4DFF`) for AI reasoning indicators, routine pipelines, and secondary badges.
    - Semantic Tokens: Emerald Matrix (`#00E676`) for verified task steps/online telemetry, Amber Alert (`#FFAB00`) for pending user confirmations/permissions, and Crimson (`#FF5252`) for active mic recording warnings or errors.
  - **Light HUD Theme (Daylight Lab)**:
    - Background Surface: Crisp Titanium White (`#F4F7FB`) with frosted slate cards (`#FFFFFF` and `#E8EEF7`), paired with Deep Cobalt (`#0066CC`) and Electric Indigo (`#5E35B1`) accents for effortless outdoor readability.
- **Typography & Hierarchy**:
  - Bundled locally via `font-util`:
    - **Display / HUD Headers**: `Orbitron` or `Rajdhani` for futuristic tactical headers, core status indicators, and telemetry readouts.
    - **Body & Multilingual Chat**: `Plus Jakarta Sans` for clean, legible English, Hindi (Devanagari), and Hinglish conversation text.
    - **Technical / Telemetry Readouts**: `JetBrains Mono` for timestamps, battery voltage/percentage, coordinates, and action execution logs.
- **Interactive Feedback & Motion**:
  - Multi-ring rotating canvas animation for the **AI Core** with smooth spring transitions (`spring()`) between *Idle* (slow breathing pulse), *Listening* (audio-reactive concentric ripples + frequency bars), *Thinking* (high-speed counter-rotating orbital arcs), and *Speaking* (harmonic sine wave modulation).

---

## 3. Key Product Decisions & Trade-Offs

- **Decision 1: Structured JSON Action Router + Conversational Response via Gemini (`gemini-3.8-flash`)**
  - *Chosen Approach*: Configure a structured output schema (`Schema.obj`) with `gemini-3.8-flash` via Firebase AI Logic so every user turn returns both a natural human reply (in the user's chosen or detected language: English, Hindi, or Hinglish) **and** a structured array of executable `JarvisAction` items (`CALL_CONTACT`, `SEND_SMS`, `SEND_WHATSAPP`, `OPEN_APP`, `TOGGLE_FLASHLIGHT`, `SET_BRIGHTNESS`, `OPEN_SETTINGS`, `MEDIA_CONTROL`, `BATTERY_STATUS`, `NETWORK_STATUS`, `SET_ALARM`, `SET_TIMER`, `ADD_REMINDER`, `CHECK_CALENDAR`, `CREATE_NOTE`, `ADD_TODO`, `NAVIGATE_MAPS`, `OPEN_CAMERA`, `WEATHER_LOOKUP`, `SAVE_MEMORY`, `RUN_ROUTINE`), plus an optional `clarifyingQuestion` flag.
  - *Why*: Guarantees deterministic, multi-command execution from a single natural sentence (e.g., *"Turn on the flashlight, set a timer for 10 minutes, and remind me to call Mom"*) without brittle regex parsing, while supporting phrasing variations in English, Hindi, and Hinglish.
  - *Alternatives Considered*: Separate intent-classification and chat calls (doubles latency) or client-side keyword matching (fails on natural phrasing and Hinglish).
- **Decision 2: Native Android SpeechRecognizer + TextToSpeech with Wake-Word Loop**
  - *Chosen Approach*: Combine Android's native `SpeechRecognizer` (`RecognizerIntent.LANGUAGE_MODEL_FREE_FORM` with multilingual support for `en-IN` / `hi-IN`) and `TextToSpeech` (with real-time pitch/rate controls and `UtteranceProgressListener`) with a foreground continuous-listening loop that listens for *"Hey Jarvis"* when hands-free mode is active.
  - *Why*: Works reliably across Android devices with zero extra audio streaming billing, supports offline/on-device speech engines where installed, provides real-time RMS audio levels (`onRmsChanged`) to drive the 60fps waveform animation, and respects Android's foreground microphone privacy indicators.
- **Decision 3: Policy-Compliant Android Intents & Hardware APIs**
  - *Chosen Approach*: Use real Android hardware managers (`CameraManager` for torch, `AudioManager` for media/volume, `BatteryManager` for battery telemetry, `ConnectivityManager` for network diagnostics) alongside standard system Intents (`AlarmClock` for alarms/timers, `ContactsContract` for contact lookup with runtime `READ_CONTACTS` permission, `Intent.ACTION_DIAL` / `ACTION_CALL` after confirmation, `Intent.ACTION_SENDTO` for SMS and WhatsApp package intents, `geo:` / Google Maps navigation intents, and `MediaStore.ACTION_IMAGE_CAPTURE` with front/rear camera extras). For installed app launching, declare explicit `<queries>` in `AndroidManifest.xml` so `PackageManager` can search and launch installed apps while complying with Google Play policy.
  - *Why*: Ensures every button and voice command performs a real, verifiable operation on the device without violating Android security sandboxes or Google Play permission policies.
- **Decision 4: Live Weather & Web Knowledge Integration**
  - *Chosen Approach*: Integrate live weather and geocoding lookups via the free, key-less Open-Meteo REST API (using Retrofit + Moshi and optional coarse location permission or city name), combined with Gemini's synthesis for news summaries, math calculations, coding assistance, translation, and general knowledge.
  - *Why*: Delivers real-time, accurate meteorological data (temperature, humidity, wind speed, daily forecast) for *"Jarvis, prepare my day"* and weather queries without requiring the user to configure third-party weather API keys.

---

## 4. Technical Architecture & Data Strategy *(Technical Reference)*

### Architecture & Component Diagram

```
┌──────────────────────────────────────────────────────────────────────────────┐
│                         UI LAYER (Jetpack Compose M3)                        │
│                                                                              │
│  ┌──────────────────────┐  ┌──────────────────────┐  ┌────────────────────┐  │
│  │   JarvisHudScreen    │  │  Memory & Routines   │  │ Systems & History  │  │
│  │  - Animated AI Core  │  │  - Notes & To-Dos    │  │ - Hardware Deck    │  │
│  │  - Live Waveform     │  │  - Saved Memories    │  │ - Action Audit Log │  │
│  │  - Chat & Action     │  │  - Custom Routines   │  │ - Voice & Theme    │  │
│  │    Confirmation Card │  │    Builder           │  │   Settings         │  │
│  └──────────┬───────────┘  └──────────┬───────────┘  └─────────┬──────────┘  │
└─────────────┼─────────────────────────┼────────────────────────┼─────────────┘
              │                         │                        │
              ▼                         ▼                        ▼
┌──────────────────────────────────────────────────────────────────────────────┐
│                     STATE & ORCHESTRATION (JarvisViewModel)                  │
│  - Conversation StateFlow, VoiceState (Idle / WakeListening / Active / TTS)  │
│  - Multi-Step Task Automation Runner & Pending Confirmation Queue            │
│  - Dynamic Runtime Permission Coordinator                                    │
└───────┬──────────────────┬────────────────────┬────────────────────┬─────────┘
        │                  │                    │                    │
        ▼                  ▼                    ▼                    ▼
┌───────────────┐  ┌───────────────┐  ┌──────────────────┐  ┌──────────────────┐
│ GeminiBrain   │  │ VoiceEngine   │  │ AndroidAction    │  │ LocalMemory &    │
│ Service       │  │ Manager       │  │ Executor         │  │ Weather Repo     │
│ (Firebase AI) │  │               │  │                  │  │                  │
│ - gemini-3.8  │  │ - SpeechRecog │  │ - Torch/Audio/   │  │ - Room Database  │
│   -flash      │  │ - Wake-word   │  │   Battery/Net    │  │   (Notes, ToDos, │
│ - Structured  │  │   "Hey Jarvis"│  │ - Contacts/Calls │  │   Memories, Logs,│
│   JSON Action │  │ - Android TTS │  │ - SMS / WhatsApp │  │   Routines)      │
│   Schema      │  │ - RMS Audio   │  │ - Alarms/Timers/ │  │ - Open-Meteo     │
│ - Context &   │  │   Level Flow  │  │   Calendar/Maps/ │  │   Live Weather   │
│   Memory      │  │ - Barge-in    │  │   Camera/Apps    │  │   Retrofit API   │
└───────────────┘  └───────────────┘  └──────────────────┘  └──────────────────┘
```

### Data Model & State

1. **Room Database Entities (`JarvisDatabase`)**:
   - `ChatMessageEntity`: `id`, `role` (`USER`, `JARVIS`, `SYSTEM`), `text`, `language`, `attachedActionsJson`, `timestamp`.
   - `MemoryFactEntity`: `id`, `category` (`PREFERENCE`, `PERSONAL_FACT`, `FREQUENT_COMMAND`), `key`, `value`, `updatedAt`.
   - `NoteEntity`: `id`, `title`, `content`, `createdAt`, `isPinned`.
   - `TodoItemEntity`: `id`, `task`, `dueTimeLabel`, `isCompleted`, `priority`, `createdAt`.
   - `RoutineEntity`: `id`, `name`, `triggerPhrase`, `stepsJson` (list of natural or structured commands), `isEnabled`.
   - `ActionLogEntity`: `id`, `actionType`, `title`, `targetSummary`, `status` (`EXECUTED`, `CONFIRMED`, `CANCELLED`, `FAILED`), `timestamp`.
2. **Structured AI Output Model (`JarvisBrainResponse`)**:
   - `spokenReply`: Concise, natural response optimized for Text-to-Speech in the user's language (English, Hindi, or Hinglish).
   - `detailedReply`: Optional rich markdown/formatted explanation for coding, math, news, or educational queries.
   - `detectedLanguage`: `"en"`, `"hi"`, or `"hinglish"`.
   - `needsClarification`: Boolean indicating whether JARVIS is asking a follow-up question before acting.
   - `actions`: Array of `ParsedCommandAction` objects (`actionType`, `targetName`, `phoneNumberOrQuery`, `messageBody`, `numericValue`, `requiresConfirmation`).
   - `memoryToSave`: Optional key-value preference learned from the user.

### Interactive Component & State Mapping

- **AI Core & Voice State Machine**:
  - Tapping the AI Core toggles listening immediately; if JARVIS is currently speaking via TTS, tapping or speaking triggers `tts.stop()` (barge-in interruption) and begins capturing the new voice command.
  - When **Continuous Mode** is enabled, completion of a TTS utterance automatically restarts `SpeechRecognizer` after a 400ms acoustic echo guard.
  - When **"Hey Jarvis" Wake-Word Mode** is enabled in the foreground, the recognizer monitors partial and final transcripts for *"hey jarvis"* / *"jarvis"* and automatically routes the remainder of the utterance (or the next prompt) to the Gemini Brain.
- **Confirmation Gate Workflow**:
  - Actions flagged with `requiresConfirmation = true` (Calls, SMS, WhatsApp, Maps Navigation, Data Deletion) are queued in `pendingConfirmations`. The UI renders an interactive card showing the resolved contact name/number or message text. Users can edit the draft inline, tap **Confirm** to launch the Android Intent and log `CONFIRMED` in `ActionLogEntity`, or tap **Cancel**.
- **"Prepare My Day" & Multi-Step Routine Runner**:
  - Invoking *"Prepare my day"* (by voice, chat, or Quick Action pill) executes a sequential coroutine pipeline that updates a live `AutomationProgressState` in the UI:
    1. Queries `CalendarContract.Events` for today's events (requesting `READ_CALENDAR` if needed, or falling back gracefully to local reminders/to-dos if declined).
    2. Queries `TodoItemEntity` and `NoteEntity` from Room.
    3. Fetches live current weather for the user's location or default city via Open-Meteo.
    4. Passes the aggregated context to `gemini-3.8-flash` to synthesize a personalized morning/daily briefing and speaks it aloud.

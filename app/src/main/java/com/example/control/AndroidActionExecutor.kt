package com.example.control

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.provider.ContactsContract
import android.provider.MediaStore
import android.provider.Settings
import android.util.Log
import android.view.KeyEvent
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private const val TAG = "AndroidActionExecutor"

data class ContactMatch(
    val id: String,
    val displayName: String,
    val phoneNumber: String
)

data class InstalledAppInfo(
    val appName: String,
    val packageName: String
)

data class CalendarEventSummary(
    val title: String,
    val startTimeFormatted: String,
    val location: String
)

data class DeviceTelemetry(
    val batteryPercent: Int = 100,
    val isCharging: Boolean = false,
    val batteryTempCelsius: Float = 28.0f,
    val networkType: String = "Wi-Fi",
    val isOnline: Boolean = true,
    val isFlashlightOn: Boolean = false,
    val brightnessPercent: Int = 75,
    val mediaVolumePercent: Int = 60
)

class AndroidActionExecutor(private val context: Context) {

    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

    private var torchCameraId: String? = null

    private val _isTorchOn = MutableStateFlow(false)
    val isTorchOn: StateFlow<Boolean> = _isTorchOn.asStateFlow()

    private val _brightnessPercent = MutableStateFlow(75)
    val brightnessPercent: StateFlow<Int> = _brightnessPercent.asStateFlow()

    private val torchCallback = object : CameraManager.TorchCallback() {
        override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
            if (cameraId == torchCameraId) {
                _isTorchOn.value = enabled
            }
        }
    }

    init {
        try {
            torchCameraId = cameraManager?.cameraIdList?.firstOrNull { id ->
                cameraManager.getCameraCharacteristics(id)
                    .get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }
            cameraManager?.registerTorchCallback(torchCallback, null)
        } catch (e: Exception) {
            Log.w(TAG, "Torch initialization note: ${e.message}")
        }
    }

    fun cleanup() {
        try {
            cameraManager?.unregisterTorchCallback(torchCallback)
        } catch (_: Exception) {
        }
    }

    // ------------------------------------------------------------------------
    // DEVICE TELEMETRY (Battery, Network, Volume, Brightness, Torch)
    // ------------------------------------------------------------------------
    fun readLiveTelemetry(): DeviceTelemetry {
        val batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: 100
        val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: 100
        val batteryPct = if (level >= 0 && scale > 0) (level * 100 / scale) else 100
        val status = batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL
        val tempTenths = batteryIntent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 280) ?: 280
        val tempC = tempTenths / 10f

        var netType = "Offline"
        var online = false
        try {
            val activeNet = connectivityManager?.activeNetwork
            val caps = activeNet?.let { connectivityManager?.getNetworkCapabilities(it) }
            if (caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
                online = true
                netType = when {
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi Connected"
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "5G / Cellular"
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
                    else -> "Online"
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Network check warning", e)
        }

        val maxVol = audioManager?.getStreamMaxVolume(AudioManager.STREAM_MUSIC)?.coerceAtLeast(1) ?: 15
        val curVol = audioManager?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: 9
        val volPct = ((curVol.toFloat() / maxVol.toFloat()) * 100f).toInt().coerceIn(0, 100)

        return DeviceTelemetry(
            batteryPercent = batteryPct,
            isCharging = isCharging,
            batteryTempCelsius = tempC,
            networkType = netType,
            isOnline = online,
            isFlashlightOn = _isTorchOn.value,
            brightnessPercent = _brightnessPercent.value,
            mediaVolumePercent = volPct
        )
    }

    // ------------------------------------------------------------------------
    // A. CALLING & CONTACTS
    // ------------------------------------------------------------------------
    suspend fun searchContacts(nameQuery: String): List<ContactMatch> = withContext(Dispatchers.IO) {
        val hasPerm = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasPerm || nameQuery.isBlank()) return@withContext emptyList()

        val matches = mutableListOf<ContactMatch>()
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        )
        val selection = "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?"
        val selectionArgs = arrayOf("%${nameQuery.trim()}%")

        try {
            context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC"
            )?.use { cursor ->
                val idIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
                val nameIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                while (cursor.moveToNext() && matches.size < 5) {
                    val id = if (idIdx >= 0) cursor.getString(idIdx).orEmpty() else ""
                    val name = if (nameIdx >= 0) cursor.getString(nameIdx).orEmpty() else ""
                    val number = if (numIdx >= 0) cursor.getString(numIdx).orEmpty() else ""
                    if (name.isNotBlank() && number.isNotBlank()) {
                        matches.add(ContactMatch(id = id, displayName = name, phoneNumber = number))
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error querying contacts", e)
        }
        matches
    }

    fun openDialerWithNumber(phoneNumber: String): Boolean {
        return try {
            val cleanUri = if (phoneNumber.isNotBlank()) {
                Uri.parse("tel:${Uri.encode(phoneNumber)}")
            } else {
                Uri.parse("tel:")
            }
            val intent = Intent(Intent.ACTION_DIAL, cleanUri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open dialer", e)
            false
        }
    }

    // ------------------------------------------------------------------------
    // B. MESSAGING (SMS & WHATSAPP)
    // ------------------------------------------------------------------------
    fun launchSmsComposer(phoneNumber: String, messageBody: String): Boolean {
        return try {
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("smsto:${Uri.encode(phoneNumber)}")
                putExtra("sms_body", messageBody)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch SMS composer", e)
            false
        }
    }

    fun launchWhatsAppMessage(phoneNumber: String, messageBody: String): Boolean {
        return try {
            val cleanDigits = phoneNumber.replace(Regex("[^0-9+]"), "")
            val encodedText = Uri.encode(messageBody)
            val uri = if (cleanDigits.isNotBlank()) {
                Uri.parse("https://api.whatsapp.com/send?phone=$cleanDigits&text=$encodedText")
            } else {
                Uri.parse("https://api.whatsapp.com/send?text=$encodedText")
            }
            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch WhatsApp intent, falling back to share", e)
            try {
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, messageBody)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(
                    Intent.createChooser(shareIntent, "Send Message").apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                )
                true
            } catch (_: Exception) {
                false
            }
        }
    }

    // ------------------------------------------------------------------------
    // C. APPLICATIONS (Installed App Discovery & Launching)
    // ------------------------------------------------------------------------
    suspend fun getInstalledApps(): List<InstalledAppInfo> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        try {
            val resolveInfos = pm.queryIntentActivities(mainIntent, 0)
            resolveInfos
                .mapNotNull { info ->
                    val pkg = info.activityInfo?.packageName ?: return@mapNotNull null
                    val label = info.loadLabel(pm)?.toString()?.trim().orEmpty()
                    if (label.isNotBlank() && pkg != context.packageName) {
                        InstalledAppInfo(appName = label, packageName = pkg)
                    } else {
                        null
                    }
                }
                .distinctBy { it.packageName }
                .sortedBy { it.appName.lowercase() }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to query installed apps", e)
            emptyList()
        }
    }

    suspend fun launchAppByNameOrQuery(appQuery: String): Pair<Boolean, String> {
        val clean = appQuery.trim().lowercase()
        if (clean.isBlank()) return false to "No application name specified."

        // Built-in system app shortcuts that always work on emulators & devices
        when {
            clean.contains("setting") -> {
                openSystemSettings(Settings.ACTION_SETTINGS)
                return true to "Opened Android System Settings."
            }
            clean.contains("wifi") || clean.contains("wi-fi") -> {
                openWifiSettings()
                return true to "Opened Wi-Fi Settings."
            }
            clean.contains("bluetooth") -> {
                openBluetoothSettings()
                return true to "Opened Bluetooth Settings."
            }
            clean.contains("camera") -> {
                openCamera(frontCamera = false)
                return true to "Opened Camera."
            }
            clean.contains("map") -> {
                openGoogleMapsSearch("Nearby")
                return true to "Opened Maps."
            }
            clean.contains("phone") || clean.contains("dialer") -> {
                openDialerWithNumber("")
                return true to "Opened Phone Dialer."
            }
        }

        val installed = getInstalledApps()
        val match = installed.firstOrNull { it.appName.lowercase() == clean }
            ?: installed.firstOrNull { it.appName.lowercase().contains(clean) || clean.contains(it.appName.lowercase()) }

        if (match != null) {
            return try {
                val launchIntent = context.packageManager.getLaunchIntentForPackage(match.packageName)
                if (launchIntent != null) {
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(launchIntent)
                    true to "Launched ${match.appName}."
                } else {
                    false to "Could not obtain launch intent for ${match.appName}."
                }
            } catch (e: Exception) {
                false to "Error launching ${match.appName}: ${e.localizedMessage}"
            }
        }

        // Web fallback for popular services (YouTube, Spotify, Gmail, etc.) if app isn't installed on emulator
        val webFallbacks = mapOf(
            "youtube" to "https://m.youtube.com",
            "whatsapp" to "https://web.whatsapp.com",
            "gmail" to "https://mail.google.com",
            "spotify" to "https://open.spotify.com",
            "chrome" to "https://www.google.com",
            "browser" to "https://www.google.com"
        )
        for ((key, url) in webFallbacks) {
            if (clean.contains(key)) {
                openWebUrl(url)
                return true to "Opened $appQuery via browser."
            }
        }

        return false to "Application \"$appQuery\" was not found on this device."
    }

    fun launchPackage(packageName: String): Boolean {
        return try {
            val intent = context.packageManager.getLaunchIntentForPackage(packageName) ?: return false
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error launching package $packageName", e)
            false
        }
    }

    // ------------------------------------------------------------------------
    // D. DEVICE CONTROLS (Flashlight, Brightness, Wi-Fi/BT/Sound/Display, Media)
    // ------------------------------------------------------------------------
    fun setFlashlight(enabled: Boolean): Pair<Boolean, String> {
        val id = torchCameraId
        if (id == null || cameraManager == null) {
            _isTorchOn.value = enabled
            return true to if (enabled) {
                "Flashlight state set to ON (Simulated on hardware without rear flash LED)."
            } else {
                "Flashlight state set to OFF."
            }
        }
        return try {
            cameraManager.setTorchMode(id, enabled)
            _isTorchOn.value = enabled
            true to if (enabled) "Flashlight turned ON." else "Flashlight turned OFF."
        } catch (e: Exception) {
            Log.e(TAG, "Torch control failed", e)
            _isTorchOn.value = enabled
            false to "Flashlight hardware unavailable (${e.localizedMessage ?: "No flash unit"})."
        }
    }

    fun setScreenBrightness(activity: Activity?, percent: Int): String {
        val clamped = percent.coerceIn(5, 100)
        _brightnessPercent.value = clamped

        // Apply immediately to current Activity window (always permitted without special system permissions)
        activity?.runOnUiThread {
            val window = activity.window
            val params = window.attributes
            params.screenBrightness = clamped / 100f
            window.attributes = params
        }

        // Also write system-wide brightness if WRITE_SETTINGS is granted
        return try {
            if (Settings.System.canWrite(context)) {
                val sysValue = ((clamped / 100f) * 255).toInt().coerceIn(10, 255)
                Settings.System.putInt(
                    context.contentResolver,
                    Settings.System.SCREEN_BRIGHTNESS,
                    sysValue
                )
                "Screen brightness set to $clamped% (System + HUD Window)."
            } else {
                "HUD screen brightness set to $clamped%."
            }
        } catch (_: Exception) {
            "HUD screen brightness set to $clamped%."
        }
    }

    fun openWifiSettings(): Boolean {
        val action = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            Settings.Panel.ACTION_WIFI
        } else {
            Settings.ACTION_WIFI_SETTINGS
        }
        return openSystemSettings(action) || openSystemSettings(Settings.ACTION_WIFI_SETTINGS)
    }

    fun openBluetoothSettings(): Boolean = openSystemSettings(Settings.ACTION_BLUETOOTH_SETTINGS)
    fun openSoundSettings(): Boolean = openSystemSettings(Settings.ACTION_SOUND_SETTINGS)
    fun openDisplaySettings(): Boolean = openSystemSettings(Settings.ACTION_DISPLAY_SETTINGS)

    fun openSystemSettings(action: String): Boolean {
        return try {
            val intent = Intent(action).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Could not open settings action $action", e)
            false
        }
    }

    fun sendMediaKey(keyCode: Int): String {
        return try {
            val downEvent = KeyEvent(KeyEvent.ACTION_DOWN, keyCode)
            val upEvent = KeyEvent(KeyEvent.ACTION_UP, keyCode)
            audioManager?.dispatchMediaKeyEvent(downEvent)
            audioManager?.dispatchMediaKeyEvent(upEvent)
            when (keyCode) {
                KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> "Toggled Media Play/Pause."
                KeyEvent.KEYCODE_MEDIA_NEXT -> "Skipped to Next Media Track."
                KeyEvent.KEYCODE_MEDIA_PREVIOUS -> "Returned to Previous Media Track."
                else -> "Sent Media Command."
            }
        } catch (e: Exception) {
            "Media command failed: ${e.localizedMessage}"
        }
    }

    fun setMediaVolumePercent(percent: Int): String {
        val clamped = percent.coerceIn(0, 100)
        return try {
            val max = audioManager?.getStreamMaxVolume(AudioManager.STREAM_MUSIC)?.coerceAtLeast(1) ?: 15
            val target = ((clamped / 100f) * max).toInt().coerceIn(0, max)
            audioManager?.setStreamVolume(AudioManager.STREAM_MUSIC, target, AudioManager.FLAG_SHOW_UI)
            "Media volume set to $clamped%."
        } catch (e: Exception) {
            "Could not adjust media volume: ${e.localizedMessage}"
        }
    }

    // ------------------------------------------------------------------------
    // E. PRODUCTIVITY (Alarms, Timers, Calendar)
    // ------------------------------------------------------------------------
    fun setSystemAlarm(hour: Int, minute: Int, label: String): Pair<Boolean, String> {
        val validHour = if (hour in 0..23) hour else 7
        val validMinute = if (minute in 0..59) minute else 0
        return try {
            val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                putExtra(AlarmClock.EXTRA_HOUR, validHour)
                putExtra(AlarmClock.EXTRA_MINUTES, validMinute)
                putExtra(AlarmClock.EXTRA_MESSAGE, label.ifBlank { "Pagi AI Alarm" })
                putExtra(AlarmClock.EXTRA_SKIP_UI, false)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            val formatted = String.format(Locale.US, "%02d:%02d", validHour, validMinute)
            true to "Alarm configured for $formatted (${label.ifBlank { "Pagi AI" }})."
        } catch (e: Exception) {
            Log.e(TAG, "Alarm intent failed", e)
            false to "System Clock app unavailable to set alarm."
        }
    }

    fun setSystemTimer(seconds: Int, label: String): Pair<Boolean, String> {
        val validSeconds = if (seconds > 0) seconds else 300
        return try {
            val intent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
                putExtra(AlarmClock.EXTRA_LENGTH, validSeconds)
                putExtra(AlarmClock.EXTRA_MESSAGE, label.ifBlank { "Pagi AI Timer" })
                putExtra(AlarmClock.EXTRA_SKIP_UI, false)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            val mins = validSeconds / 60
            val secs = validSeconds % 60
            val durationLabel = if (mins > 0) "${mins}m ${secs}s" else "${secs}s"
            true to "Timer started for $durationLabel."
        } catch (e: Exception) {
            Log.e(TAG, "Timer intent failed", e)
            false to "System Timer app unavailable."
        }
    }

    suspend fun readTodayCalendarEvents(): List<CalendarEventSummary> = withContext(Dispatchers.IO) {
        val hasPerm = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.READ_CALENDAR
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasPerm) return@withContext emptyList()

        val events = mutableListOf<CalendarEventSummary>()
        val startOfDay = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
        }.timeInMillis
        val endOfDay = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
        }.timeInMillis

        val projection = arrayOf(
            CalendarContract.Events.TITLE,
            CalendarContract.Events.DTSTART,
            CalendarContract.Events.EVENT_LOCATION
        )
        val selection = "${CalendarContract.Events.DTSTART} >= ? AND ${CalendarContract.Events.DTSTART} <= ?"
        val selectionArgs = arrayOf(startOfDay.toString(), endOfDay.toString())
        val formatter = SimpleDateFormat("hh:mm a", Locale.getDefault())

        try {
            context.contentResolver.query(
                CalendarContract.Events.CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                "${CalendarContract.Events.DTSTART} ASC"
            )?.use { cursor ->
                val titleIdx = cursor.getColumnIndex(CalendarContract.Events.TITLE)
                val startIdx = cursor.getColumnIndex(CalendarContract.Events.DTSTART)
                val locIdx = cursor.getColumnIndex(CalendarContract.Events.EVENT_LOCATION)
                while (cursor.moveToNext() && events.size < 10) {
                    val title = if (titleIdx >= 0) cursor.getString(titleIdx).orEmpty() else "Scheduled Event"
                    val startMillis = if (startIdx >= 0) cursor.getLong(startIdx) else startOfDay
                    val location = if (locIdx >= 0) cursor.getString(locIdx).orEmpty() else ""
                    events.add(
                        CalendarEventSummary(
                            title = title.ifBlank { "Untitled Event" },
                            startTimeFormatted = formatter.format(Date(startMillis)),
                            location = location
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error querying calendar events", e)
        }
        events
    }

    fun launchCalendarEventCreator(title: String, description: String): Boolean {
        return try {
            val startMillis = System.currentTimeMillis() + 3600_000L
            val endMillis = startMillis + 3600_000L
            val intent = Intent(Intent.ACTION_INSERT).apply {
                data = CalendarContract.Events.CONTENT_URI
                putExtra(CalendarContract.Events.TITLE, title.ifBlank { "Pagi Scheduled Event" })
                putExtra(CalendarContract.Events.DESCRIPTION, description)
                putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, startMillis)
                putExtra(CalendarContract.EXTRA_EVENT_END_TIME, endMillis)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Calendar insert intent failed", e)
            false
        }
    }

    // ------------------------------------------------------------------------
    // F. NAVIGATION (Google Maps Search & Turn-by-Turn Navigation)
    // ------------------------------------------------------------------------
    fun openGoogleMapsNavigation(destinationQuery: String): Boolean {
        val clean = destinationQuery.trim().ifBlank { "City Center" }
        return try {
            val navUri = Uri.parse("google.navigation:q=${Uri.encode(clean)}")
            val navIntent = Intent(Intent.ACTION_VIEW, navUri).apply {
                setPackage("com.google.android.apps.maps")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (navIntent.resolveActivity(context.packageManager) != null) {
                context.startActivity(navIntent)
            } else {
                openGoogleMapsSearch(clean)
            }
            true
        } catch (e: Exception) {
            openGoogleMapsSearch(clean)
        }
    }

    fun openGoogleMapsSearch(query: String): Boolean {
        val clean = query.trim().ifBlank { "Restaurants nearby" }
        return try {
            val geoUri = Uri.parse("geo:0,0?q=${Uri.encode(clean)}")
            val intent = Intent(Intent.ACTION_VIEW, geoUri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
                true
            } else {
                openWebUrl("https://www.google.com/maps/search/?api=1&query=${Uri.encode(clean)}")
            }
        } catch (e: Exception) {
            openWebUrl("https://www.google.com/maps/search/?api=1&query=${Uri.encode(clean)}")
        }
    }

    fun openWebSearch(query: String): Boolean {
        val clean = query.trim().ifBlank { "latest technology news" }
        return openWebUrl("https://www.google.com/search?q=${Uri.encode(clean)}")
    }

    fun openWebUrl(url: String): Boolean {
        return try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open web URL $url", e)
            false
        }
    }

    // ------------------------------------------------------------------------
    // G. CAMERA (Front & Rear Camera Launch)
    // ------------------------------------------------------------------------
    fun openCamera(frontCamera: Boolean): Boolean {
        return try {
            val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
                if (frontCamera) {
                    putExtra("android.intent.extras.CAMERA_FACING", 1)
                    putExtra("android.intent.extras.LENS_FACING_FRONT", 1)
                    putExtra("android.intent.extra.USE_FRONT_CAMERA", true)
                } else {
                    putExtra("android.intent.extras.CAMERA_FACING", 0)
                    putExtra("android.intent.extras.LENS_FACING_BACK", 1)
                    putExtra("android.intent.extra.USE_FRONT_CAMERA", false)
                }
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch camera", e)
            false
        }
    }
}

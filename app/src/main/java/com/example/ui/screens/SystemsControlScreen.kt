package com.example.ui.screens

import android.app.Activity
import android.view.KeyEvent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BrightnessHigh
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CameraFront
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.FlashlightOff
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.Launch
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Message
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.control.AndroidActionExecutor
import com.example.control.DeviceTelemetry
import com.example.control.InstalledAppInfo
import com.example.ui.theme.ArcCyan
import com.example.ui.theme.MatrixEmerald
import com.example.ui.theme.NeuralViolet

@Composable
fun SystemsControlScreen(
    telemetry: DeviceTelemetry,
    installedApps: List<InstalledAppInfo>,
    actionExecutor: AndroidActionExecutor,
    onToggleFlashlight: () -> Unit,
    onBrightnessChange: (Activity?, Int) -> Unit,
    onVolumeChange: (Int) -> Unit,
    onRefreshTelemetry: () -> Unit,
    onQueueActionViaBrain: (String, Activity?) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = context as? Activity

    var brightnessSlider by remember(telemetry.brightnessPercent) {
        mutableFloatStateOf(telemetry.brightnessPercent.toFloat())
    }
    var volumeSlider by remember(telemetry.mediaVolumePercent) {
        mutableFloatStateOf(telemetry.mediaVolumePercent.toFloat())
    }
    var appSearchQuery by rememberSaveable { mutableStateOf("") }
    var destinationInput by rememberSaveable { mutableStateOf("Connaught Place, New Delhi") }
    var quickRecipient by rememberSaveable { mutableStateOf("Rahul") }
    var quickMessage by rememberSaveable { mutableStateOf("I will arrive in 15 minutes.") }

    val filteredApps = remember(installedApps, appSearchQuery) {
        if (appSearchQuery.isBlank()) {
            installedApps.take(20)
        } else {
            installedApps.filter {
                it.appName.contains(appSearchQuery, ignoreCase = true) ||
                    it.packageName.contains(appSearchQuery, ignoreCase = true)
            }
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("systems_control_screen"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Futuristic HUD Banner + Live Telemetry Header
        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(18.dp))
            ) {
                Box(modifier = Modifier.height(148.dp)) {
                    Image(
                        painter = painterResource(id = R.drawable.img_jarvis_hud_banner_1794200243075),
                        contentDescription = "Pagi HUD Systems Telemetry Banner",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color(0x88060B16),
                                        Color(0xE6060B16)
                                    )
                                )
                            )
                            .padding(14.dp)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "ANDROID PHONE CONTROL DECK",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = ArcCyan
                                )
                                IconButton(
                                    onClick = onRefreshTelemetry,
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = "Refresh telemetry",
                                        tint = Color.White
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                TelemetryMetricPill(
                                    icon = Icons.Default.BatteryChargingFull,
                                    label = "BATTERY",
                                    value = "${telemetry.batteryPercent}% (${telemetry.batteryTempCelsius}°C)",
                                    accent = MatrixEmerald,
                                    modifier = Modifier.weight(1f)
                                )
                                TelemetryMetricPill(
                                    icon = Icons.Default.Wifi,
                                    label = "NETWORK",
                                    value = telemetry.networkType,
                                    accent = ArcCyan,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }
        }

        // D. Device Hardware Controls (Flashlight, Wi-Fi, Bluetooth, Sound, Display)
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "HARDWARE & SYSTEM PANELS",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        HardwareQuickTile(
                            title = if (telemetry.isFlashlightOn) "Torch ON" else "Torch OFF",
                            icon = if (telemetry.isFlashlightOn) Icons.Default.FlashlightOn else Icons.Default.FlashlightOff,
                            active = telemetry.isFlashlightOn,
                            onClick = onToggleFlashlight,
                            tag = "tile_flashlight",
                            modifier = Modifier.weight(1f)
                        )
                        HardwareQuickTile(
                            title = "Wi-Fi Panel",
                            icon = Icons.Default.Wifi,
                            active = telemetry.isOnline,
                            onClick = { actionExecutor.openWifiSettings() },
                            tag = "tile_wifi",
                            modifier = Modifier.weight(1f)
                        )
                        HardwareQuickTile(
                            title = "Bluetooth",
                            icon = Icons.Default.Bluetooth,
                            active = false,
                            onClick = { actionExecutor.openBluetoothSettings() },
                            tag = "tile_bluetooth",
                            modifier = Modifier.weight(1f)
                        )
                        HardwareQuickTile(
                            title = "Sound & Display",
                            icon = Icons.Default.Settings,
                            active = false,
                            onClick = { actionExecutor.openSoundSettings() },
                            tag = "tile_sound",
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Brightness Control Slider
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.BrightnessHigh,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Screen Brightness",
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                        Text(
                            text = "${brightnessSlider.toInt()}%",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Slider(
                        value = brightnessSlider,
                        onValueChange = { brightnessSlider = it },
                        onValueChangeFinished = {
                            onBrightnessChange(activity, brightnessSlider.toInt())
                        },
                        valueRange = 5f..100f
                    )

                    // Media Volume & Playback Transport
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.VolumeUp,
                                contentDescription = null,
                                tint = NeuralViolet,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Media Volume & Playback",
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                        Text(
                            text = "${volumeSlider.toInt()}%",
                            style = MaterialTheme.typography.labelLarge,
                            color = NeuralViolet
                        )
                    }
                    Slider(
                        value = volumeSlider,
                        onValueChange = { volumeSlider = it },
                        onValueChangeFinished = {
                            onVolumeChange(volumeSlider.toInt())
                        },
                        valueRange = 0f..100f
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        OutlinedButton(
                            onClick = { actionExecutor.sendMediaKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS) }
                        ) {
                            Icon(Icons.Default.SkipPrevious, contentDescription = "Previous track")
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Prev")
                        }
                        Button(
                            onClick = { actionExecutor.sendMediaKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) }
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = "Play or Pause")
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Play / Pause")
                        }
                        OutlinedButton(
                            onClick = { actionExecutor.sendMediaKey(KeyEvent.KEYCODE_MEDIA_NEXT) }
                        ) {
                            Text("Next")
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(Icons.Default.SkipNext, contentDescription = "Next track")
                        }
                    }
                }
            }
        }

        // A, B, E, F, G: Communications, Alarms, Camera & Maps Quick Deck
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "COMMUNICATION, CAMERA & NAVIGATION",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = quickRecipient,
                            onValueChange = { quickRecipient = it },
                            label = { Text("Contact / Number") },
                            singleLine = true,
                            modifier = Modifier.weight(0.42f)
                        )
                        OutlinedTextField(
                            value = quickMessage,
                            onValueChange = { quickMessage = it },
                            label = { Text("Message Draft") },
                            singleLine = true,
                            modifier = Modifier.weight(0.58f)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                onQueueActionViaBrain("Call $quickRecipient", activity)
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Call, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Call")
                        }
                        OutlinedButton(
                            onClick = {
                                onQueueActionViaBrain("Send an SMS message to $quickRecipient saying $quickMessage", activity)
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Message, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("SMS")
                        }
                        Button(
                            onClick = {
                                onQueueActionViaBrain("Send a WhatsApp message to $quickRecipient saying $quickMessage", activity)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MatrixEmerald, contentColor = Color.Black),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("WhatsApp", fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Productivity & Camera Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { actionExecutor.setSystemAlarm(7, 0, "Pagi Morning Alarm") },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Alarm, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Alarm")
                        }
                        OutlinedButton(
                            onClick = { actionExecutor.setSystemTimer(300, "Pagi 5m Timer") },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Timer, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Timer")
                        }
                        OutlinedButton(
                            onClick = { actionExecutor.launchCalendarEventCreator("Pagi Strategy Sync", "Scheduled via Pagi AI") },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Event, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Calendar")
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { actionExecutor.openCamera(frontCamera = false) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Rear Camera")
                        }
                        OutlinedButton(
                            onClick = { actionExecutor.openCamera(frontCamera = true) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.CameraFront, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Front Selfie")
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Google Maps Navigation Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = destinationInput,
                            onValueChange = { destinationInput = it },
                            label = { Text("Google Maps Destination") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        Button(
                            onClick = {
                                onQueueActionViaBrain("Navigate to $destinationInput on Google Maps", activity)
                            }
                        ) {
                            Icon(Icons.Default.Map, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Navigate")
                        }
                    }
                }
            }
        }

        // C. Installed Applications Search & Launcher
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Apps,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "INSTALLED APP SEARCH & LAUNCHER (${installedApps.size})",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = appSearchQuery,
                        onValueChange = { appSearchQuery = it },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        placeholder = { Text("Search installed apps or type e.g. YouTube, Settings...") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("app_search_input")
                    )
                }
            }
        }

        items(filteredApps, key = { it.packageName }) { appInfo ->
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                    .clickable { actionExecutor.launchPackage(appInfo.packageName) }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = appInfo.appName,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = appInfo.packageName,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.Launch,
                        contentDescription = "Launch ${appInfo.appName}",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
private fun TelemetryMetricPill(
    icon: ImageVector,
    label: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        color = Color(0xAA0D1627),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier.border(1.dp, accent.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(18.dp)
            )
            Column {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF8AB4D8)
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White
                )
            }
        }
    }
}

@Composable
private fun HardwareQuickTile(
    title: String,
    icon: ImageVector,
    active: Boolean,
    onClick: () -> Unit,
    tag: String,
    modifier: Modifier = Modifier
) {
    val bg = if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
    val borderCol = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(bg)
            .border(1.dp, borderCol, RoundedCornerShape(14.dp))
            .clickable { onClick() }
            .padding(vertical = 12.dp, horizontal = 6.dp)
            .testTag(tag)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(
                    if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface
                )
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1
        )
    }
}

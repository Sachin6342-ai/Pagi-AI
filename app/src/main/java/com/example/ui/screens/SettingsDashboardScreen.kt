package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.JarvisPreferences
import com.example.ui.theme.AmberAlert
import com.example.ui.theme.MatrixEmerald
import java.util.Locale

data class PermissionStatusEntry(
    val label: String,
    val permissionName: String,
    val isGranted: Boolean
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsDashboardScreen(
    preferences: JarvisPreferences,
    permissionStatuses: List<PermissionStatusEntry>,
    onUpdatePreferences: ((JarvisPreferences) -> JarvisPreferences) -> Unit,
    onRefreshWeatherCity: (String) -> Unit,
    onTestVoiceSample: () -> Unit,
    onRequestPermission: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var cityDraft by rememberSaveable(preferences.defaultCity) {
        mutableStateOf(preferences.defaultCity)
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("settings_dashboard_screen"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Voice, Language & Wake-Word Configuration
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.RecordVoiceOver,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "VOICE ENGINE & MULTILINGUAL AI",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "Preferred AI Language Mode",
                        style = MaterialTheme.typography.titleMedium
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(vertical = 4.dp)
                    ) {
                        listOf("Hinglish & English", "English Only", "Hindi (हिंदी)").forEach { langMode ->
                            FilterChip(
                                selected = preferences.preferredLanguage == langMode,
                                onClick = {
                                    onUpdatePreferences { it.copy(preferredLanguage = langMode) }
                                },
                                label = { Text(langMode) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "Speech Recognition & TTS Voice Locale",
                        style = MaterialTheme.typography.titleMedium
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(vertical = 4.dp)
                    ) {
                        listOf(
                            "en-IN" to "English (India)",
                            "hi-IN" to "Hindi (भारत)",
                            "en-US" to "English (US)"
                        ).forEach { (tag, label) ->
                            FilterChip(
                                selected = preferences.speechLocaleTag == tag,
                                onClick = {
                                    onUpdatePreferences { it.copy(speechLocaleTag = tag) }
                                },
                                label = { Text(label) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Speaking Speed Slider
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Speaking Speed", style = MaterialTheme.typography.titleMedium)
                        Text(
                            String.format(Locale.US, "%.2fx", preferences.speechRate),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Slider(
                        value = preferences.speechRate,
                        onValueChange = { rate ->
                            onUpdatePreferences { it.copy(speechRate = rate) }
                        },
                        valueRange = 0.6f..1.8f
                    )

                    // Voice Pitch Slider
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Voice Pitch", style = MaterialTheme.typography.titleMedium)
                        Text(
                            String.format(Locale.US, "%.2fx", preferences.speechPitch),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Slider(
                        value = preferences.speechPitch,
                        onValueChange = { pitch ->
                            onUpdatePreferences { it.copy(speechPitch = pitch) }
                        },
                        valueRange = 0.6f..1.4f
                    )

                    OutlinedButton(
                        onClick = onTestVoiceSample,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("test_voice_button")
                    ) {
                        Icon(Icons.Default.VolumeUp, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Test Pagi Voice Output")
                    }
                }
            }
        }

        // 2. Wake-Word, Continuous Mode & Privacy Controls
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "WAKE WORD & PRIVACY SAFEGUARDS",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    SettingsSwitchRow(
                        title = "Wake Word Activation (\"Hey Pagi\")",
                        subtitle = "Listen for \"Hey Pagi\" while the HUD is open",
                        checked = preferences.wakeWordEnabled && !preferences.voiceListeningDisabled,
                        onCheckedChange = { enabled ->
                            onUpdatePreferences { it.copy(wakeWordEnabled = enabled) }
                        }
                    )

                    SettingsSwitchRow(
                        title = "Continuous Conversation Mode",
                        subtitle = "Automatically listen for follow-up commands after Pagi finishes speaking",
                        checked = preferences.continuousConversation && !preferences.voiceListeningDisabled,
                        onCheckedChange = { enabled ->
                            onUpdatePreferences { it.copy(continuousConversation = enabled) }
                        }
                    )

                    SettingsSwitchRow(
                        title = "Spoken Voice Responses (TTS)",
                        subtitle = "Speak Pagi replies aloud using natural speech synthesis",
                        checked = preferences.voiceOutputEnabled,
                        onCheckedChange = { enabled ->
                            onUpdatePreferences { it.copy(voiceOutputEnabled = enabled) }
                        }
                    )

                    SettingsSwitchRow(
                        title = "Privacy Hardware Lock: Disable Voice Listening",
                        subtitle = "Completely block all microphone recognition until unlocked",
                        checked = preferences.voiceListeningDisabled,
                        onCheckedChange = { disabled ->
                            onUpdatePreferences { it.copy(voiceListeningDisabled = disabled) }
                        }
                    )
                }
            }
        }

        // 3. Appearance & Location Preferences
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Palette,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "HUD THEME & WEATHER LOCATION",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    SettingsSwitchRow(
                        title = "Futuristic Dark HUD Theme",
                        subtitle = "Switch between Abyssal Dark HUD and High-Contrast Daylight HUD",
                        checked = preferences.darkTheme,
                        onCheckedChange = { dark ->
                            onUpdatePreferences { it.copy(darkTheme = dark) }
                        }
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = cityDraft,
                            onValueChange = { cityDraft = it },
                            label = { Text("Default City for Weather & Briefings") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        Button(
                            onClick = {
                                if (cityDraft.isNotBlank()) {
                                    onUpdatePreferences { it.copy(defaultCity = cityDraft.trim()) }
                                    onRefreshWeatherCity(cityDraft.trim())
                                }
                            }
                        ) {
                            Icon(Icons.Default.Language, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Update")
                        }
                    }
                }
            }
        }

        // 4. Runtime Android Permissions Status Center
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
                        text = "ANDROID RUNTIME PERMISSIONS",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    permissionStatuses.forEach { entry ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = entry.label,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = if (entry.isGranted) "Authorized" else "Not Yet Granted",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (entry.isGranted) MatrixEmerald else AmberAlert
                                )
                            }
                            if (entry.isGranted) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "Granted",
                                    tint = MatrixEmerald
                                )
                            } else {
                                OutlinedButton(
                                    onClick = { onRequestPermission(entry.permissionName) }
                                ) {
                                    Text("Grant")
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
private fun SettingsSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

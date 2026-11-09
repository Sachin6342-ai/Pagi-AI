package com.example.ui.screens

import android.app.Activity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.Message
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.control.DeviceTelemetry
import com.example.data.local.ChatMessageEntity
import com.example.data.remote.LiveWeatherReport
import com.example.ui.AutomationPipelineState
import com.example.ui.PendingConfirmationItem
import com.example.ui.StepProgressStatus
import com.example.ui.components.JarvisAiCoreVisualizer
import com.example.ui.theme.AmberAlert
import com.example.ui.theme.ArcCyan
import com.example.ui.theme.CrimsonPulse
import com.example.ui.theme.MatrixEmerald
import com.example.ui.theme.NeuralViolet
import com.example.voice.VoiceCoreState

data class QuickCommandChip(
    val label: String,
    val command: String,
    val tag: String
)

@Composable
fun CommandHudScreen(
    messages: List<ChatMessageEntity>,
    voiceCoreState: VoiceCoreState,
    isBrainThinking: Boolean,
    waveformLevels: List<Float>,
    partialTranscript: String,
    telemetry: DeviceTelemetry,
    liveWeather: LiveWeatherReport?,
    pendingConfirmations: List<PendingConfirmationItem>,
    automationPipeline: AutomationPipelineState?,
    hasMicPermission: Boolean,
    onRequestMicPermission: () -> Unit,
    onAiCoreTap: () -> Unit,
    onStopVoiceTap: () -> Unit,
    onSendCommand: (String, Activity?) -> Unit,
    onPrepareMyDay: () -> Unit,
    onToggleFlashlight: () -> Unit,
    onCaptureVisionPhoto: () -> Unit,
    onConfirmAction: (PendingConfirmationItem, String, String) -> Unit,
    onCancelAction: (PendingConfirmationItem) -> Unit,
    onDismissAutomation: () -> Unit,
    onSpeakMessageAloud: (String, String) -> Unit,
    onClearConversation: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val focusManager = LocalFocusManager.current
    val listState = rememberLazyListState()

    var inputPrompt by rememberSaveable { mutableStateOf("") }
    var compactCore by rememberSaveable { mutableStateOf(false) }

    val quickChips = remember {
        listOf(
            QuickCommandChip("Prepare My Day", "Pagi, prepare my day", "chip_prepare_day"),
            QuickCommandChip("Arrival Message", "Send Rahul a message that I will arrive in 15 minutes", "chip_arrival_msg"),
            QuickCommandChip("Torch Toggle", "Turn on the flashlight", "chip_flashlight"),
            QuickCommandChip("Set 7 AM Alarm", "Set an alarm for 7:00 AM", "chip_alarm"),
            QuickCommandChip("5m Timer", "Set a timer for 5 minutes", "chip_timer"),
            QuickCommandChip("Weather Report", "What is the live weather today?", "chip_weather"),
            QuickCommandChip("Hinglish Demo", "Pagi, mera battery status batao aur ek note save karo", "chip_hinglish")
        )
    }

    LaunchedEffect(messages.size, pendingConfirmations.size, automationPipeline) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem((messages.size - 1).coerceAtLeast(0))
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Top HUD Telemetry Bar
        TopHudTelemetryBar(
            telemetry = telemetry,
            liveWeather = liveWeather,
            compactCore = compactCore,
            onToggleCompactCore = { compactCore = !compactCore },
            onCaptureVisionPhoto = onCaptureVisionPhoto,
            onClearConversation = onClearConversation
        )

        // Central AI Core & Live Waveform
        JarvisAiCoreVisualizer(
            coreState = voiceCoreState,
            isBrainThinking = isBrainThinking,
            waveformLevels = waveformLevels,
            partialTranscript = partialTranscript,
            compactMode = compactCore,
            onCoreTap = onAiCoreTap,
            onStopVoiceTap = onStopVoiceTap,
            modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
        )

        // Quick Action Command Rail
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(quickChips) { chip ->
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(50),
                    modifier = Modifier
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(50))
                        .clickable {
                            if (chip.tag == "chip_prepare_day") {
                                onPrepareMyDay()
                            } else if (chip.tag == "chip_flashlight") {
                                onToggleFlashlight()
                            } else {
                                onSendCommand(chip.command, activity)
                            }
                        }
                        .testTag(chip.tag)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = when (chip.tag) {
                                "chip_prepare_day" -> Icons.Default.AutoAwesome
                                "chip_flashlight" -> Icons.Default.FlashlightOn
                                "chip_arrival_msg" -> Icons.Default.Message
                                "chip_alarm" -> Icons.Default.Alarm
                                "chip_timer" -> Icons.Default.Schedule
                                "chip_weather" -> Icons.Default.Cloud
                                else -> Icons.Default.AutoAwesome
                            },
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = chip.label,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }

        // Conversation Feed + Automation Progress + Confirmation Cards
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .testTag("conversation_list"),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Multi-step Automation Pipeline Card ("Jarvis, prepare my day")
            if (automationPipeline != null) {
                item(key = "automation_pipeline_card") {
                    AutomationPipelineCard(
                        pipeline = automationPipeline,
                        onDismiss = onDismissAutomation
                    )
                }
            }

            // Pending Confirmation Cards (Calls, SMS, WhatsApp, Maps)
            items(pendingConfirmations, key = { "confirm_${it.id}" }) { pendingItem ->
                ActionConfirmationCard(
                    item = pendingItem,
                    onConfirm = { num, msg -> onConfirmAction(pendingItem, num, msg) },
                    onCancel = { onCancelAction(pendingItem) }
                )
            }

            // Chat Messages
            items(messages, key = { "msg_${it.id}" }) { message ->
                ChatMessageBubble(
                    message = message,
                    onSpeakAloud = { onSpeakMessageAloud(message.spokenText, message.language) }
                )
            }
        }

        // Bottom Multilingual Voice & Text Command Input Dock
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Camera Vision Analysis Button
                IconButton(
                    onClick = onCaptureVisionPhoto,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .testTag("camera_vision_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.CameraAlt,
                        contentDescription = "Capture photo for AI vision analysis",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                OutlinedTextField(
                    value = inputPrompt,
                    onValueChange = { inputPrompt = it },
                    placeholder = {
                        Text(
                            text = "Ask Pagi (English, हिंदी, Hinglish)...",
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(
                        onSend = {
                            if (inputPrompt.isNotBlank()) {
                                val textToSend = inputPrompt
                                inputPrompt = ""
                                focusManager.clearFocus()
                                onSendCommand(textToSend, activity)
                            }
                        }
                    ),
                    shape = RoundedCornerShape(24.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("command_input_field")
                )

                // Voice Mic Trigger Button
                IconButton(
                    onClick = {
                        if (!hasMicPermission) {
                            onRequestMicPermission()
                        } else {
                            onAiCoreTap()
                        }
                    },
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(
                            if (voiceCoreState == VoiceCoreState.LISTENING_COMMAND) MatrixEmerald else MaterialTheme.colorScheme.secondaryContainer
                        )
                        .testTag("bottom_mic_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = "Start voice input",
                        tint = if (voiceCoreState == VoiceCoreState.LISTENING_COMMAND) Color.Black else MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }

                // Send Command Button
                IconButton(
                    onClick = {
                        if (inputPrompt.isNotBlank()) {
                            val textToSend = inputPrompt
                            inputPrompt = ""
                            focusManager.clearFocus()
                            onSendCommand(textToSend, activity)
                        }
                    },
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                        .testTag("send_command_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send command",
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        }
    }
}

@Composable
private fun TopHudTelemetryBar(
    telemetry: DeviceTelemetry,
    liveWeather: LiveWeatherReport?,
    compactCore: Boolean,
    onToggleCompactCore: () -> Unit,
    onCaptureVisionPhoto: () -> Unit,
    onClearConversation: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "PAGI AI",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = "v3.8 ONLINE",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (telemetry.isCharging) Icons.Default.BatteryChargingFull else Icons.Default.BatteryFull,
                        contentDescription = null,
                        tint = MatrixEmerald,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    Text(
                        text = "${telemetry.batteryPercent}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Wifi,
                        contentDescription = null,
                        tint = if (telemetry.isOnline) ArcCyan else CrimsonPulse,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = telemetry.networkType,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (liveWeather != null) {
                    Text(
                        text = "• ${liveWeather.temperatureCelsius.toInt()}°C ${liveWeather.locationName.substringBefore(",")}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            IconButton(
                onClick = onToggleCompactCore,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = if (compactCore) Icons.Default.ExpandMore else Icons.Default.ExpandLess,
                    contentDescription = "Toggle AI Core size",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(
                onClick = onClearConversation,
                modifier = Modifier
                    .size(40.dp)
                    .testTag("clear_chat_button")
            ) {
                Icon(
                    imageVector = Icons.Default.DeleteSweep,
                    contentDescription = "Clear conversation history",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun AutomationPipelineCard(
    pipeline: AutomationPipelineState,
    onDismiss: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, NeuralViolet, RoundedCornerShape(16.dp))
            .testTag("automation_pipeline_card")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = NeuralViolet
                    )
                    Text(
                        text = pipeline.routineName,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Dismiss pipeline card",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            pipeline.steps.forEach { step ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    when (step.status) {
                        StepProgressStatus.COMPLETED -> Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Completed",
                            tint = MatrixEmerald,
                            modifier = Modifier.size(18.dp)
                        )
                        StepProgressStatus.RUNNING -> CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = ArcCyan
                        )
                        StepProgressStatus.PENDING -> Icon(
                            imageVector = Icons.Default.RadioButtonUnchecked,
                            contentDescription = "Pending",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Column {
                        Text(
                            text = step.stepTitle,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = step.detail,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ActionConfirmationCard(
    item: PendingConfirmationItem,
    onConfirm: (String, String) -> Unit,
    onCancel: () -> Unit
) {
    var numberOrQuery by rememberSaveable(item.id) { mutableStateOf(item.phoneNumberOrQuery) }
    var messageDraft by rememberSaveable(item.id) { mutableStateOf(item.messageDraft) }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.5.dp, AmberAlert, RoundedCornerShape(16.dp))
            .testTag("confirmation_card_${item.id}")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = when (item.actionType) {
                        "CALL_CONTACT" -> Icons.Default.Call
                        "NAVIGATE_MAPS" -> Icons.Default.Navigation
                        else -> Icons.Default.Message
                    },
                    contentDescription = null,
                    tint = AmberAlert
                )
                Column {
                    Text(
                        text = "SECURITY GATE • ${item.title.uppercase()}",
                        style = MaterialTheme.typography.labelLarge,
                        color = AmberAlert
                    )
                    Text(
                        text = "Target: ${item.contactName}" +
                            if (item.contactResolvedFromPhonebook) " (Matched in Contacts)" else "",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedTextField(
                value = numberOrQuery,
                onValueChange = { numberOrQuery = it },
                label = {
                    Text(
                        if (item.actionType == "NAVIGATE_MAPS") "Destination Address / Place"
                        else "Phone Number (Optional for WhatsApp chooser)"
                    )
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            if (item.actionType == "SEND_SMS" || item.actionType == "SEND_WHATSAPP") {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = messageDraft,
                    onValueChange = { messageDraft = it },
                    label = { Text("AI Drafted Message (Editable)") },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("cancel_action_button")
                ) {
                    Text("Cancel")
                }
                Button(
                    onClick = { onConfirm(numberOrQuery, messageDraft) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MatrixEmerald,
                        contentColor = Color.Black
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("confirm_action_button")
                ) {
                    Text("Confirm & Execute", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChatMessageBubble(
    message: ChatMessageEntity,
    onSpeakAloud: () -> Unit
) {
    val isUser = message.role == "USER"
    val isSystem = message.role == "SYSTEM"
    var showDetails by rememberSaveable(message.id) { mutableStateOf(true) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = when {
            isUser -> Alignment.End
            isSystem -> Alignment.CenterHorizontally
            else -> Alignment.Start
        }
    ) {
        Surface(
            color = when {
                isUser -> MaterialTheme.colorScheme.primaryContainer
                isSystem -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f)
                else -> MaterialTheme.colorScheme.surface
            },
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isUser) 16.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 16.dp
            ),
            modifier = Modifier
                .fillMaxWidth(if (isSystem) 0.96f else 0.90f)
                .border(
                    width = 1.dp,
                    color = when {
                        isUser -> MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                        isSystem -> NeuralViolet.copy(alpha = 0.5f)
                        else -> MaterialTheme.colorScheme.outline
                    },
                    shape = RoundedCornerShape(
                        topStart = 16.dp,
                        topEnd = 16.dp,
                        bottomStart = if (isUser) 16.dp else 4.dp,
                        bottomEnd = if (isUser) 4.dp else 16.dp
                    )
                )
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = when (message.role) {
                            "USER" -> "YOU"
                            "SYSTEM" -> "SYSTEM AUDIT"
                            else -> "PAGI AI"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isUser) MaterialTheme.colorScheme.primary else NeuralViolet
                    )
                    if (!isUser) {
                        IconButton(
                            onClick = onSpeakAloud,
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.VolumeUp,
                                contentDescription = "Speak response aloud",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = message.spokenText,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )

                if (message.detailedText.isNotBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .clickable { showDetails = !showDetails }
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (showDetails) "Hide Detailed Breakdown" else "Show Detailed Breakdown",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Icon(
                            imageVector = if (showDetails) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    AnimatedVisibility(visible = showDetails) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 6.dp)
                        ) {
                            Text(
                                text = message.detailedText,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }
                }

                if (message.actionSummary.isNotBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Surface(
                            color = MatrixEmerald.copy(alpha = 0.16f),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.border(1.dp, MatrixEmerald.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                        ) {
                            Text(
                                text = "⚡ ${message.actionSummary}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MatrixEmerald,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

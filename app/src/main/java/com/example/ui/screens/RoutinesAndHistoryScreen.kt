package com.example.ui.screens

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.local.ActionLogEntity
import com.example.data.local.RoutineEntity
import com.example.ui.theme.AmberAlert
import com.example.ui.theme.ArcCyan
import com.example.ui.theme.CrimsonPulse
import com.example.ui.theme.MatrixEmerald
import com.example.ui.theme.NeuralViolet
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun RoutinesAndHistoryScreen(
    routines: List<RoutineEntity>,
    actionLogs: List<ActionLogEntity>,
    onRunRoutine: (RoutineEntity, Activity?) -> Unit,
    onCreateRoutine: (String, String, List<String>) -> Unit,
    onToggleRoutine: (RoutineEntity) -> Unit,
    onDeleteRoutine: (Long) -> Unit,
    onClearActionLogs: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val tabs = listOf(
        "Routines (${routines.size})",
        "Execution History (${actionLogs.size})"
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("routines_history_screen")
    ) {
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            tabs.forEachIndexed { idx, title ->
                Tab(
                    selected = selectedTab == idx,
                    onClick = { selectedTab = idx },
                    text = { Text(title, style = MaterialTheme.typography.labelLarge) }
                )
            }
        }

        when (selectedTab) {
            0 -> CustomRoutinesTab(
                routines = routines,
                onRunRoutine = onRunRoutine,
                onCreateRoutine = onCreateRoutine,
                onToggleRoutine = onToggleRoutine,
                onDeleteRoutine = onDeleteRoutine
            )
            1 -> TaskExecutionHistoryTab(
                actionLogs = actionLogs,
                onClearActionLogs = onClearActionLogs
            )
        }
    }
}

@Composable
private fun CustomRoutinesTab(
    routines: List<RoutineEntity>,
    onRunRoutine: (RoutineEntity, Activity?) -> Unit,
    onCreateRoutine: (String, String, List<String>) -> Unit,
    onToggleRoutine: (RoutineEntity) -> Unit,
    onDeleteRoutine: (Long) -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity

    var routineTitle by rememberSaveable { mutableStateOf("") }
    var triggerPhrase by rememberSaveable { mutableStateOf("") }
    var stepsInput by rememberSaveable { mutableStateOf("") }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
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
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = NeuralViolet
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "CREATE MULTI-STEP AUTOMATION ROUTINE",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = routineTitle,
                        onValueChange = { routineTitle = it },
                        label = { Text("Routine Name (e.g., Workout Mode)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = triggerPhrase,
                        onValueChange = { triggerPhrase = it },
                        label = { Text("Voice Trigger (e.g., Pagi, start workout)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = stepsInput,
                        onValueChange = { stepsInput = it },
                        label = { Text("Steps separated by commas (e.g., Set volume to 80%, Set timer for 30 minutes)") },
                        minLines = 2,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = {
                            if (routineTitle.isNotBlank() && stepsInput.isNotBlank()) {
                                val parsedSteps = stepsInput.split(",", "\n").map { it.trim() }.filter { it.isNotBlank() }
                                onCreateRoutine(routineTitle, triggerPhrase, parsedSteps)
                                routineTitle = ""
                                triggerPhrase = ""
                                stepsInput = ""
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("create_routine_button")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Save Automation Routine")
                    }
                }
            }
        }

        items(routines, key = { it.id }) { routine ->
            val steps = routine.stepsJoined.split("|").filter { it.isNotBlank() }
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = routine.title,
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Voice Trigger: “${routine.triggerPhrase}”",
                                style = MaterialTheme.typography.labelLarge,
                                color = ArcCyan
                            )
                        }
                        Switch(
                            checked = routine.isEnabled,
                            onCheckedChange = { onToggleRoutine(routine) }
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    steps.forEachIndexed { index, stepText ->
                        Text(
                            text = "${index + 1}. $stepText",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 2.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { onDeleteRoutine(routine.id) }) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete routine",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                        Button(
                            onClick = { onRunRoutine(routine, activity) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MatrixEmerald,
                                contentColor = Color.Black
                            ),
                            modifier = Modifier.testTag("run_routine_${routine.id}")
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Execute Routine Now", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TaskExecutionHistoryTab(
    actionLogs: List<ActionLogEntity>,
    onClearActionLogs: () -> Unit
) {
    val timeFormatter = SimpleDateFormat("MMM dd, hh:mm:ss a", Locale.getDefault())

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "ACTION AUDIT LOG",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                if (actionLogs.isNotEmpty()) {
                    OutlinedButton(onClick = onClearActionLogs) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Clear")
                    }
                }
            }
        }

        if (actionLogs.isEmpty()) {
            item {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                ) {
                    Text(
                        text = "No device actions executed yet. Issue a voice or text command to see real-time audit logs here.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(18.dp)
                    )
                }
            }
        }

        items(actionLogs, key = { it.id }) { log ->
            val statusColor = when (log.status) {
                "EXECUTED", "CONFIRMED" -> MatrixEmerald
                "CANCELLED" -> AmberAlert
                else -> CrimsonPulse
            }
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = log.actionType,
                                style = MaterialTheme.typography.labelSmall,
                                color = ArcCyan
                            )
                            Text(
                                text = "• ${timeFormatter.format(Date(log.timestamp))}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = log.title,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (log.targetSummary.isNotBlank()) {
                            Text(
                                text = log.targetSummary,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Surface(
                        color = statusColor.copy(alpha = 0.16f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.border(1.dp, statusColor.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                    ) {
                        Text(
                            text = log.status,
                            style = MaterialTheme.typography.labelSmall,
                            color = statusColor,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }
    }
}

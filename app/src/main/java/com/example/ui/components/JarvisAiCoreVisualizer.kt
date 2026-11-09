package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.ui.theme.AmberAlert
import com.example.ui.theme.ArcCyan
import com.example.ui.theme.CrimsonPulse
import com.example.ui.theme.MatrixEmerald
import com.example.ui.theme.NeuralViolet
import com.example.voice.VoiceCoreState
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun JarvisAiCoreVisualizer(
    coreState: VoiceCoreState,
    isBrainThinking: Boolean,
    waveformLevels: List<Float>,
    partialTranscript: String,
    compactMode: Boolean = false,
    onCoreTap: () -> Unit,
    onStopVoiceTap: () -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "jarvis_core_transition")

    val outerRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (isBrainThinking || coreState == VoiceCoreState.LISTENING_COMMAND) 3500 else 10000,
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "outer_ring_rotation"
    )

    val innerRotation by infiniteTransition.animateFloat(
        initialValue = 360f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (isBrainThinking || coreState == VoiceCoreState.SPEAKING) 2600 else 7500,
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "inner_ring_rotation"
    )

    val corePulse by infiniteTransition.animateFloat(
        initialValue = 0.88f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = when {
                    coreState == VoiceCoreState.LISTENING_COMMAND -> 550
                    coreState == VoiceCoreState.SPEAKING -> 450
                    isBrainThinking -> 400
                    else -> 1600
                }
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "core_pulse"
    )

    val primaryCoreColor by animateColorAsState(
        targetValue = when {
            coreState == VoiceCoreState.MUTED_PRIVACY -> CrimsonPulse
            isBrainThinking || coreState == VoiceCoreState.PROCESSING -> NeuralViolet
            coreState == VoiceCoreState.LISTENING_COMMAND -> MatrixEmerald
            coreState == VoiceCoreState.SPEAKING -> ArcCyan
            coreState == VoiceCoreState.WAKE_WORD_STANDBY -> ArcCyan
            else -> ArcCyan
        },
        label = "core_color"
    )

    val secondaryCoreColor by animateColorAsState(
        targetValue = when {
            coreState == VoiceCoreState.MUTED_PRIVACY -> AmberAlert
            coreState == VoiceCoreState.LISTENING_COMMAND -> ArcCyan
            else -> NeuralViolet
        },
        label = "secondary_core_color"
    )

    val coreDiameter = if (compactMode) 116.dp else 168.dp

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Microphone & Core Status Pill
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f),
            shape = RoundedCornerShape(50),
            modifier = Modifier
                .border(1.dp, primaryCoreColor.copy(alpha = 0.5f), RoundedCornerShape(50))
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(primaryCoreColor)
                )
                val statusLabel = when {
                    isBrainThinking || coreState == VoiceCoreState.PROCESSING -> "ANALYZING NEURAL INTENT..."
                    coreState == VoiceCoreState.LISTENING_COMMAND -> "MIC ACTIVE • LISTENING FOR COMMAND"
                    coreState == VoiceCoreState.SPEAKING -> "PAGI SPEAKING • TAP TO INTERRUPT"
                    coreState == VoiceCoreState.WAKE_WORD_STANDBY -> "STANDBY • SAY \"HEY PAGI\" OR TAP CORE"
                    coreState == VoiceCoreState.MUTED_PRIVACY -> "PRIVACY LOCK • VOICE MIC DISABLED"
                    else -> "TAP AI CORE TO SPEAK"
                }
                Text(
                    text = statusLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (coreState == VoiceCoreState.SPEAKING || coreState == VoiceCoreState.LISTENING_COMMAND) {
                    Icon(
                        imageVector = Icons.Default.Stop,
                        contentDescription = "Stop voice activity",
                        tint = CrimsonPulse,
                        modifier = Modifier
                            .size(16.dp)
                            .clickable { onStopVoiceTap() }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(if (compactMode) 8.dp else 14.dp))

        // Animated Circular AI Core
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(coreDiameter)
                .clip(CircleShape)
                .clickable { onCoreTap() }
                .semantics { contentDescription = "Pagi AI Core Voice Button" }
                .testTag("jarvis_ai_core_button")
        ) {
            val avgAudioLevel = waveformLevels.average().toFloat().coerceIn(0.1f, 1.0f)

            Canvas(modifier = Modifier.size(coreDiameter)) {
                val center = Offset(size.width / 2f, size.height / 2f)
                val maxRadius = size.minDimension / 2f

                // Outer radial glow
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            primaryCoreColor.copy(alpha = 0.30f * corePulse),
                            secondaryCoreColor.copy(alpha = 0.14f),
                            Color.Transparent
                        ),
                        center = center,
                        radius = maxRadius
                    ),
                    radius = maxRadius,
                    center = center
                )

                // Outer segmented HUD telemetry ring
                rotate(degrees = outerRotation, pivot = center) {
                    val outerRingRadius = maxRadius * 0.88f
                    val topLeft = Offset(center.x - outerRingRadius, center.y - outerRingRadius)
                    val arcSize = Size(outerRingRadius * 2f, outerRingRadius * 2f)
                    for (angle in 0 until 360 step 45) {
                        drawArc(
                            color = primaryCoreColor.copy(alpha = 0.85f),
                            startAngle = angle.toFloat(),
                            sweepAngle = 26f,
                            useCenter = false,
                            topLeft = topLeft,
                            size = arcSize,
                            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                        )
                    }
                }

                // Counter-rotating inner neural violet ring
                rotate(degrees = innerRotation, pivot = center) {
                    val midRingRadius = maxRadius * 0.70f
                    val topLeft = Offset(center.x - midRingRadius, center.y - midRingRadius)
                    val arcSize = Size(midRingRadius * 2f, midRingRadius * 2f)
                    for (angle in 0 until 360 step 60) {
                        drawArc(
                            color = secondaryCoreColor.copy(alpha = 0.9f),
                            startAngle = angle.toFloat(),
                            sweepAngle = 36f,
                            useCenter = false,
                            topLeft = topLeft,
                            size = arcSize,
                            style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
                        )
                    }
                }

                // Audio-reactive tick spokes
                val spokeInner = maxRadius * 0.48f
                val spokeOuterBase = maxRadius * (0.56f + avgAudioLevel * 0.14f)
                for (i in 0 until 24) {
                    val angleRad = Math.toRadians((i * 15).toDouble())
                    val barBoost = waveformLevels.getOrElse(i) { 0.2f } * (maxRadius * 0.12f)
                    val start = Offset(
                        x = center.x + (spokeInner * cos(angleRad)).toFloat(),
                        y = center.y + (spokeInner * sin(angleRad)).toFloat()
                    )
                    val end = Offset(
                        x = center.x + ((spokeOuterBase + barBoost) * cos(angleRad)).toFloat(),
                        y = center.y + ((spokeOuterBase + barBoost) * sin(angleRad)).toFloat()
                    )
                    drawLine(
                        color = primaryCoreColor.copy(alpha = 0.7f),
                        start = start,
                        end = end,
                        strokeWidth = 2.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }

                // Central glowing energy nucleus
                val nucleusRadius = maxRadius * 0.36f * corePulse.coerceIn(0.9f, 1.08f)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            primaryCoreColor.copy(alpha = 0.95f),
                            secondaryCoreColor.copy(alpha = 0.75f),
                            Color(0xFF060B16)
                        ),
                        center = center,
                        radius = nucleusRadius * 1.25f
                    ),
                    radius = nucleusRadius,
                    center = center
                )

                drawCircle(
                    color = Color.White.copy(alpha = 0.9f),
                    radius = nucleusRadius * 0.85f,
                    center = center,
                    style = Stroke(width = 1.5.dp.toPx())
                )
            }

            // Center state icon
            Icon(
                imageVector = when {
                    coreState == VoiceCoreState.MUTED_PRIVACY -> Icons.Default.MicOff
                    coreState == VoiceCoreState.SPEAKING -> Icons.Default.VolumeUp
                    coreState == VoiceCoreState.LISTENING_COMMAND -> Icons.Default.GraphicEq
                    else -> Icons.Default.Mic
                },
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(if (compactMode) 26.dp else 34.dp)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Real-Time 24-Bar Audio Waveform Visualizer
        Row(
            modifier = Modifier
                .height(if (compactMode) 24.dp else 32.dp)
                .padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            waveformLevels.forEachIndexed { index, level ->
                val activeWave = coreState == VoiceCoreState.LISTENING_COMMAND ||
                    coreState == VoiceCoreState.SPEAKING ||
                    isBrainThinking
                val barFraction = if (activeWave) level.coerceIn(0.15f, 1.0f) else 0.18f
                val barColor = if (index % 2 == 0) primaryCoreColor else secondaryCoreColor
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .height((if (compactMode) 22.dp else 30.dp) * barFraction)
                        .clip(RoundedCornerShape(50))
                        .background(barColor.copy(alpha = if (activeWave) 0.9f else 0.4f))
                )
            }
        }

        // Live Partial Speech-to-Text Transcript
        if (partialTranscript.isNotBlank()) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "“$partialTranscript”",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = primaryCoreColor,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 20.dp)
            )
        }
    }
}

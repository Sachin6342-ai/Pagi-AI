package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val JarvisDarkColorScheme = darkColorScheme(
    primary = ArcCyan,
    onPrimary = AbyssalNavy,
    primaryContainer = Color(0xFF003B4A),
    onPrimaryContainer = ArcCyan,
    secondary = NeuralViolet,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF281952),
    onSecondaryContainer = NeuralVioletLight,
    tertiary = MatrixEmerald,
    onTertiary = AbyssalNavy,
    background = AbyssalNavy,
    onBackground = TextPrimaryDark,
    surface = HudSurfaceDark,
    onSurface = TextPrimaryDark,
    surfaceVariant = HudSurfaceElevated,
    onSurfaceVariant = TextSecondaryDark,
    outline = HudBorderCyan,
    error = CrimsonPulse,
    onError = Color.White
)

private val JarvisLightColorScheme = lightColorScheme(
    primary = CobaltPrimary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD6E8FF),
    onPrimaryContainer = Color(0xFF002B5C),
    secondary = ElectricIndigo,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFEADDFF),
    onSecondaryContainer = Color(0xFF21005D),
    tertiary = Color(0xFF008744),
    onTertiary = Color.White,
    background = TitaniumWhite,
    onBackground = TextPrimaryLight,
    surface = HudSurfaceLight,
    onSurface = TextPrimaryLight,
    surfaceVariant = HudSurfaceElevatedLight,
    onSurfaceVariant = TextSecondaryLight,
    outline = Color(0xFFB8CCE4),
    error = Color(0xFFD32F2F),
    onError = Color.White
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) JarvisDarkColorScheme else JarvisLightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

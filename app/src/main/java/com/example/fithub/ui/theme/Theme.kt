package com.example.fithub.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = FitGreen,
    onPrimary = Color.White,
    primaryContainer = FitMint,
    onPrimaryContainer = FitGreenDark,
    secondary = FitGreenDark,
    onSecondary = Color.White,
    tertiary = FitOrange,
    background = FitBackground,
    onBackground = Color(0xFF1A1C19),
    surface = Color.White,
    onSurface = Color(0xFF1A1C19),
    surfaceVariant = FitMint,
    onSurfaceVariant = Color(0xFF424940),
    error = FitRed
)

private val DarkColors = darkColorScheme(
    primary = FitGreenLight,
    onPrimary = Color(0xFF00390A),
    primaryContainer = FitGreenDark,
    onPrimaryContainer = Color(0xFFC8E6C9),
    secondary = FitLime,
    onSecondary = Color(0xFF1A1C19),
    tertiary = Color(0xFFFFB74D),
    background = DarkBackground,
    onBackground = Color(0xFFE1E3DD),
    surface = DarkSurface,
    onSurface = Color(0xFFE1E3DD),
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = Color(0xFFC2C9BD),
    error = Color(0xFFEF9A9A)
)

@Composable
fun FitHubTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = Typography,
        content = content
    )
}

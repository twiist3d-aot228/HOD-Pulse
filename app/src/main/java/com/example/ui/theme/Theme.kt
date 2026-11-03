package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val MilitaryColorScheme = darkColorScheme(
    primary = MilitaryPrimary,
    onPrimary = MilitaryBackground,
    primaryContainer = MilitarySurfaceVariant,
    onPrimaryContainer = MilitaryPrimary,
    secondary = MilitarySecondary,
    onSecondary = MilitaryBackground,
    tertiary = MilitaryTertiary,
    onTertiary = MilitaryBackground,
    background = MilitaryBackground,
    onBackground = MilitaryTextPrimary,
    surface = MilitarySurface,
    onSurface = MilitaryTextPrimary,
    surfaceVariant = MilitarySurfaceVariant,
    onSurfaceVariant = MilitaryTextSecondary,
    outline = MilitaryOutline,
    error = MilitaryError,
    onError = MilitaryBackground
)

@Composable
fun DayZServerTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = MilitaryColorScheme,
        typography = Typography,
        content = content
    )
}

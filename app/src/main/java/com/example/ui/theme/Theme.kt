package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val CosmicColorScheme = darkColorScheme(
    primary = CosmicPrimary,
    secondary = CosmicSecondary,
    tertiary = CosmicTertiary,
    error = CosmicError,
    background = CosmicBackground,
    surface = CosmicSurface,
    onBackground = CosmicOnBackground,
    onSurface = CosmicOnSurface,
    surfaceVariant = CosmicSurfaceVariant,
    onSurfaceVariant = CosmicOnSurfaceVariant
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true, // Force dark theme for the academic dashboard
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = CosmicColorScheme,
        typography = Typography,
        content = content
    )
}

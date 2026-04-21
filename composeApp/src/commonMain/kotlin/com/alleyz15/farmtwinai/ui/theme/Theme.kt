package com.alleyz15.farmtwinai.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Forest500,
    onPrimary = Fog50,
    primaryContainer = Mint200,
    onPrimaryContainer = Forest900,
    secondary = WaterBlue,
    background = Sand100,
    onBackground = Forest900,
    surface = Fog50,
    onSurface = Forest900,
    surfaceVariant = Color(0xFFE3EBDD),
    onSurfaceVariant = Slate600,
    error = AlertRed,
)

private val DarkColors = darkColorScheme(
    primary = Leaf400,
    onPrimary = Forest900,
    primaryContainer = Forest700,
    onPrimaryContainer = Fog50,
    secondary = UpdateCyan,
    background = Forest900,
    onBackground = Fog50,
    surface = CardDark,
    onSurface = Fog50,
    surfaceVariant = Forest700,
    onSurfaceVariant = Mint200,
    error = AlertRed,
)

@Composable
fun FarmTwinTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = FarmTwinTypography,
        content = content,
    )
}

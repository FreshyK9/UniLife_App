package com.opelm.unilife.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = InkBlue,
    onPrimary = Cream50,
    primaryContainer = Color(0xFFD6E3F2),
    onPrimaryContainer = Slate900,
    secondary = Brass600,
    onSecondary = Cream50,
    secondaryContainer = Color(0xFFEFDFC5),
    onSecondaryContainer = Color(0xFF3A2A10),
    tertiary = Sage600,
    onTertiary = Cream50,
    tertiaryContainer = Color(0xFFD4E8E1),
    onTertiaryContainer = Color(0xFF18332E),
    background = Cream100,
    onBackground = Slate900,
    surface = Cream50,
    onSurface = Slate900,
    surfaceVariant = Cream200,
    onSurfaceVariant = Charcoal700,
    outline = Charcoal200,
    error = Danger
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFB5CBE6),
    onPrimary = Color(0xFF0E1B29),
    primaryContainer = Slate700,
    onPrimaryContainer = Color(0xFFE4EEF8),
    secondary = Color(0xFFE2C793),
    onSecondary = Color(0xFF2B1E09),
    secondaryContainer = Color(0xFF5C4420),
    onSecondaryContainer = Color(0xFFF8E6BE),
    tertiary = Color(0xFFA9D0C8),
    onTertiary = Color(0xFF0E2621),
    tertiaryContainer = Color(0xFF2C514A),
    onTertiaryContainer = Color(0xFFD8F0EA),
    background = Color(0xFF10161D),
    onBackground = Color(0xFFE3E8EE),
    surface = Color(0xFF151D25),
    onSurface = Color(0xFFE3E8EE),
    surfaceVariant = Color(0xFF2B343E),
    onSurfaceVariant = Color(0xFFC0C8D1),
    outline = Color(0xFF89939E),
    error = Color(0xFFF2B8B5)
)

@Composable
fun UniLifeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = AppTypography,
        content = content
    )
}

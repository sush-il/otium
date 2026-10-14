package com.focusblock.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/*
 * A curated soft-pastel periwinkle palette. Used directly (not wallpaper-based dynamic
 * colour) so the look stays consistent and gently tinted. Backgrounds are tinted rather
 * than pure black/white, and the surfaceContainer roles give cards quiet layered depth.
 */

private val PastelLight = lightColorScheme(
    primary = Color(0xFF5A5FC7),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE2DFFF),
    onPrimaryContainer = Color(0xFF14134A),
    secondary = Color(0xFF5D5C72),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE3E0F9),
    onSecondaryContainer = Color(0xFF191A2C),
    tertiary = Color(0xFF79536A),
    tertiaryContainer = Color(0xFFFFD8EC),
    onTertiaryContainer = Color(0xFF2E1125),
    background = Color(0xFFFBF8FF),
    onBackground = Color(0xFF1B1B21),
    surface = Color(0xFFFBF8FF),
    onSurface = Color(0xFF1B1B21),
    surfaceVariant = Color(0xFFE4E1EC),
    onSurfaceVariant = Color(0xFF47464F),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF5F2FC),
    surfaceContainer = Color(0xFFEFECF7),
    surfaceContainerHigh = Color(0xFFEAE6F1),
    surfaceContainerHighest = Color(0xFFE4E1EC),
    outline = Color(0xFF78767F),
    outlineVariant = Color(0xFFC8C5D0),
    error = Color(0xFFBA1A1A),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
)

private val PastelDark = darkColorScheme(
    primary = Color(0xFFC3C4FF),
    onPrimary = Color(0xFF282A60),
    primaryContainer = Color(0xFF3F4178),
    onPrimaryContainer = Color(0xFFE2DFFF),
    secondary = Color(0xFFC6C4DD),
    onSecondary = Color(0xFF2E2F42),
    secondaryContainer = Color(0xFF454559),
    onSecondaryContainer = Color(0xFFE3E0F9),
    tertiary = Color(0xFFE9B9D3),
    tertiaryContainer = Color(0xFF5F3B50),
    onTertiaryContainer = Color(0xFFFFD8EC),
    background = Color(0xFF131318),
    onBackground = Color(0xFFE5E1E9),
    surface = Color(0xFF131318),
    onSurface = Color(0xFFE5E1E9),
    surfaceVariant = Color(0xFF47464F),
    onSurfaceVariant = Color(0xFFC9C5D0),
    surfaceContainerLowest = Color(0xFF0E0E13),
    surfaceContainerLow = Color(0xFF1B1B21),
    surfaceContainer = Color(0xFF1F1F25),
    surfaceContainerHigh = Color(0xFF2A2930),
    surfaceContainerHighest = Color(0xFF35343B),
    outline = Color(0xFF938F99),
    outlineVariant = Color(0xFF48464F),
    error = Color(0xFFFFB4AB),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
)

// Soft, rounded shapes for a friendly Material 3 feel (cards default to `medium`)
private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

@Composable
fun FocusBlockTheme(content: @Composable () -> Unit) {
    val colors = if (isSystemInDarkTheme()) PastelDark else PastelLight
    MaterialTheme(colorScheme = colors, shapes = AppShapes, content = content)
}

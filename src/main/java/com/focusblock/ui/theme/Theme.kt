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
 * Catppuccin palette mapped onto Material 3 roles: Latte for light, Mocha for dark,
 * with mauve as the accent. Used directly (not wallpaper-based) so the soft, gently
 * tinted look stays consistent; the surfaceContainer roles give cards quiet depth.
 */

// Latte
private val CatppuccinLight = lightColorScheme(
    primary = Color(0xFF8839EF), // mauve
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFEAD9FF),
    onPrimaryContainer = Color(0xFF2B0A52),
    secondary = Color(0xFF1E66F5), // blue
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFDCE4FF),
    onSecondaryContainer = Color(0xFF0A1C47),
    tertiary = Color(0xFFEA76CB), // pink
    tertiaryContainer = Color(0xFFFFD9F0),
    onTertiaryContainer = Color(0xFF43062F),
    background = Color(0xFFEFF1F5), // base
    onBackground = Color(0xFF4C4F69), // text
    surface = Color(0xFFEFF1F5),
    onSurface = Color(0xFF4C4F69),
    surfaceVariant = Color(0xFFCCD0DA), // surface0
    onSurfaceVariant = Color(0xFF6C6F85), // subtext0
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFE6E9EF), // mantle
    surfaceContainer = Color(0xFFE2E5EC),
    surfaceContainerHigh = Color(0xFFDCE0E8), // crust-ish
    surfaceContainerHighest = Color(0xFFD5D9E3),
    outline = Color(0xFF8C8FA1),
    outlineVariant = Color(0xFFBCC0CC),
    error = Color(0xFFD20F39), // red
    errorContainer = Color(0xFFFFDAD9),
    onErrorContainer = Color(0xFF410009),
)

// Mocha
private val CatppuccinDark = darkColorScheme(
    primary = Color(0xFFCBA6F7), // mauve
    onPrimary = Color(0xFF2A1A4A),
    primaryContainer = Color(0xFF45386B),
    onPrimaryContainer = Color(0xFFEAD9FF),
    secondary = Color(0xFF89B4FA), // blue
    onSecondary = Color(0xFF0A2347),
    secondaryContainer = Color(0xFF2C3C5E),
    onSecondaryContainer = Color(0xFFDCE4FF),
    tertiary = Color(0xFFF5C2E7), // pink
    tertiaryContainer = Color(0xFF5A3350),
    onTertiaryContainer = Color(0xFFFFD9F0),
    background = Color(0xFF1E1E2E), // base
    onBackground = Color(0xFFCDD6F4), // text
    surface = Color(0xFF1E1E2E),
    onSurface = Color(0xFFCDD6F4),
    surfaceVariant = Color(0xFF313244), // surface0
    onSurfaceVariant = Color(0xFFA6ADC8), // subtext0
    surfaceContainerLowest = Color(0xFF11111B), // crust
    surfaceContainerLow = Color(0xFF181825), // mantle
    surfaceContainer = Color(0xFF232334),
    surfaceContainerHigh = Color(0xFF313244), // surface0
    surfaceContainerHighest = Color(0xFF45475A), // surface1
    outline = Color(0xFF6C7086), // overlay0
    outlineVariant = Color(0xFF45475A),
    error = Color(0xFFF38BA8), // red
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
    val colors = if (isSystemInDarkTheme()) CatppuccinDark else CatppuccinLight
    MaterialTheme(colorScheme = colors, shapes = AppShapes, content = content)
}

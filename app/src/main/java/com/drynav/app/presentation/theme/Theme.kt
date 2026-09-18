package com.drynav.app.presentation.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val LightScheme = lightColorScheme(
    primary = TealPrimary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD2ECF1),
    onPrimaryContainer = TealDark,
    secondary = NavBlue,
    onSecondary = Color.White,
    tertiary = AccentGreen,
    error = FloodRed,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color.White,
    onBackground = TextDark,
    surface = Color.White,
    onSurface = TextDark,
    surfaceVariant = Color(0xFFF3F6F9),
    onSurfaceVariant = TextGray,
    outline = TealPrimary
)

private val DarkScheme = darkColorScheme(
    primary = TealLight,
    onPrimary = Color(0xFF00363F),
    primaryContainer = TealDark,
    onPrimaryContainer = Color(0xFFB7E7F0),
    secondary = Color(0xFF8FB8F2),
    tertiary = Color(0xFF7BC67E),
    error = Color(0xFFFFB4AB),
    background = DarkBackground,
    onBackground = Color(0xFFE3E8ED),
    surface = DarkSurface,
    onSurface = Color(0xFFE3E8ED),
    surfaceVariant = DarkCard,
    onSurfaceVariant = Color(0xFF9AA7B4),
    outline = TealLight
)

/** Big friendly radii to match the pill-and-card look of the mockups. */
private val DryNavShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp)
)

@Composable
fun DryNavTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkScheme else LightScheme,
        typography = DryNavTypography,
        shapes = DryNavShapes,
        content = content
    )
}

package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val ColorContainerCyan = androidx.compose.ui.graphics.Color(0xFF0E3A47)
private val ColorContainerCoral = androidx.compose.ui.graphics.Color(0xFF4A2018)

private val StudioDarkColorScheme = darkColorScheme(
    primary = ElectricCyan,
    onPrimary = StudioObsidian,
    primaryContainer = ColorContainerCyan,
    onPrimaryContainer = TextPrimary,
    secondary = CoralAmber,
    onSecondary = StudioObsidian,
    secondaryContainer = ColorContainerCoral,
    onSecondaryContainer = TextPrimary,
    tertiary = VioletTrack,
    onTertiary = StudioObsidian,
    background = StudioObsidian,
    onBackground = TextPrimary,
    surface = StudioSurface,
    onSurface = TextPrimary,
    surfaceVariant = StudioSurfaceElevated,
    onSurfaceVariant = TextSecondary,
    surfaceContainerLow = StudioSurface,
    surfaceContainer = StudioSurfaceElevated,
    surfaceContainerHigh = StudioSurfaceHighest,
    outline = StudioOutline,
    outlineVariant = StudioOutline,
    error = CrimsonError,
    onError = TextPrimary
)

val StudioShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(26.dp)
)

@Composable
fun AIVideoStudioTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = StudioDarkColorScheme,
        typography = Typography,
        shapes = StudioShapes,
        content = content
    )
}

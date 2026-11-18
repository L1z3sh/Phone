package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val AetherDarkColorScheme = darkColorScheme(
    primary = CyanPulse,
    onPrimary = ObsidianBg,
    primaryContainer = CyanDeep,
    onPrimaryContainer = CyanOnContainer,
    secondary = EmeraldAfk,
    onSecondary = ObsidianBg,
    secondaryContainer = EmeraldDeep,
    onSecondaryContainer = EmeraldOnContainer,
    tertiary = AmberBatch,
    onTertiary = ObsidianBg,
    tertiaryContainer = AmberDeep,
    onTertiaryContainer = AmberOnContainer,
    error = RoseAlert,
    onError = Color.White,
    errorContainer = RoseDeep,
    onErrorContainer = RoseOnContainer,
    background = ObsidianBg,
    onBackground = TextPrimary,
    surface = ObsidianSurface,
    onSurface = TextPrimary,
    surfaceVariant = ObsidianSurfaceVariant,
    onSurfaceVariant = TextSecondary,
    surfaceContainerHigh = ObsidianSurfaceHigh,
    outline = ObsidianOutline,
    outlineVariant = ObsidianSurfaceHigh
)

private val AetherLightColorScheme = lightColorScheme(
    primary = LightPrimary,
    onPrimary = Color.White,
    secondary = LightSecondary,
    onSecondary = Color.White,
    tertiary = LightTertiary,
    onTertiary = Color.White,
    background = LightBg,
    onBackground = ObsidianBg,
    surface = LightSurface,
    onSurface = ObsidianBg,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = Color(0xFF475569)
)

val AetherShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun AetherPhoneTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) AetherDarkColorScheme else AetherLightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        shapes = AetherShapes,
        content = content
    )
}

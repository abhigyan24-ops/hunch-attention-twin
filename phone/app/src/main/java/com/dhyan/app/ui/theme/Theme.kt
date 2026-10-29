package com.dhyan.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable

private fun scheme(p: DhyanPalette, dark: Boolean): ColorScheme {
    val base = if (dark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = p.primary, onPrimary = p.onPrimary,
        primaryContainer = p.primaryContainer, onPrimaryContainer = p.onPrimaryContainer,
        secondary = p.secondary, onSecondary = p.onSecondary,
        secondaryContainer = p.secondaryContainer, onSecondaryContainer = p.onSecondaryContainer,
        tertiary = p.tertiary, onTertiary = p.onTertiary,
        tertiaryContainer = p.tertiaryContainer, onTertiaryContainer = p.onTertiaryContainer,
        error = p.error, onError = p.onError,
        errorContainer = p.errorContainer, onErrorContainer = p.onErrorContainer,
        background = p.background, onBackground = p.text,
        surface = p.surface, onSurface = p.text,
        surfaceVariant = p.variant, onSurfaceVariant = p.muted,
        surfaceDim = p.background, surfaceBright = p.high,
        surfaceContainerLowest = p.background, surfaceContainerLow = p.low,
        surfaceContainer = p.low, surfaceContainerHigh = p.high, surfaceContainerHighest = p.variant,
        outline = p.outline, outlineVariant = p.outlineVariant, surfaceTint = p.primary
    )
}

@Composable
fun DhyanTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = scheme(if (darkTheme) DarkPalette else LightPalette, darkTheme),
        typography = DhyanTypography,
        shapes = DhyanShapes,
        content = content
    )
}

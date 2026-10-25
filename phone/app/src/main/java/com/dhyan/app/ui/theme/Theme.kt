package com.dhyan.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DhyanColorScheme = darkColorScheme(
    primary = DhyanPrimary,
    onPrimary = TextInverse,
    primaryContainer = StateWorkingBg,
    onPrimaryContainer = DhyanPrimaryGlow,
    secondary = DhyanSecondary,
    onSecondary = TextInverse,
    background = VoidBlack,
    onBackground = TextPrimary,
    surface = SurfaceBase,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceElevated,
    onSurfaceVariant = TextSecondary,
    outline = BorderSubtle,
    outlineVariant = SurfaceCard
)

@Composable
fun DhyanTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DhyanColorScheme,
        typography = DhyanTypography,
        content = content
    )
}

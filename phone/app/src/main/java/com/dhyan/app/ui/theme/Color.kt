package com.dhyan.app.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.Composable
import androidx.compose.material3.MaterialTheme

/** Opaque design tokens: usable by the theme and host-side contrast tests. */
data class DhyanPalette(
    val background: Color, val surface: Color, val low: Color, val high: Color, val variant: Color,
    val text: Color, val muted: Color, val primary: Color, val onPrimary: Color,
    val primaryContainer: Color, val onPrimaryContainer: Color,
    val secondary: Color, val onSecondary: Color, val secondaryContainer: Color, val onSecondaryContainer: Color,
    val tertiary: Color, val onTertiary: Color, val tertiaryContainer: Color, val onTertiaryContainer: Color,
    val error: Color, val onError: Color, val errorContainer: Color, val onErrorContainer: Color,
    val outline: Color, val outlineVariant: Color
)

val LightPalette = DhyanPalette(
    background = Color(0xFFF7F8FA), surface = Color.White, low = Color(0xFFF1F3F7),
    high = Color(0xFFE9EEF4), variant = Color(0xFFEAEEF4), text = Color(0xFF121417),
    muted = Color(0xFF56606E), primary = Color(0xFF121417), onPrimary = Color.White,
    primaryContainer = Color(0xFFE8F3E8), onPrimaryContainer = Color(0xFF2E6F40),
    secondary = Color(0xFF8D6E63), onSecondary = Color.White,
    secondaryContainer = Color(0xFFEFEBE4), onSecondaryContainer = Color(0xFF59473F),
    tertiary = Color(0xFF5558D6), onTertiary = Color.White,
    tertiaryContainer = Color(0xFFE9EAF8), onTertiaryContainer = Color(0xFF3B3D88),
    error = Color(0xFFBA1A1A), onError = Color.White,
    errorContainer = Color(0xFFFFDAD6), onErrorContainer = Color(0xFF410002),
    outline = Color(0xFF7B8491), outlineVariant = Color(0xFFEAEEF4)
)
val DarkPalette = DhyanPalette(
    background = Color(0xFF11151B), surface = Color(0xFF1B2028), low = Color(0xFF212832),
    high = Color(0xFF2B3440), variant = Color(0xFF303C4B), text = Color(0xFFF1F4F8),
    muted = Color(0xFFC1CBD7), primary = Color(0xFFF1F4F8), onPrimary = Color(0xFF121417),
    primaryContainer = Color(0xFF243F2D), onPrimaryContainer = Color(0xFFB9E9BE),
    secondary = Color(0xFFD9C2B5), onSecondary = Color(0xFF31231D),
    secondaryContainer = Color(0xFF42362E), onSecondaryContainer = Color(0xFFF1DFCF),
    tertiary = Color(0xFFBFC1FF), onTertiary = Color(0xFF28295F),
    tertiaryContainer = Color(0xFF313359), onTertiaryContainer = Color(0xFFE5E4FF),
    error = Color(0xFFFFB4AB), onError = Color(0xFF690005),
    errorContainer = Color(0xFF542420), onErrorContainer = Color(0xFFFFDAD6),
    outline = Color(0xFF8B98AA), outlineVariant = Color(0xFF364151)
)


val VoidBlack @Composable get() = MaterialTheme.colorScheme.background
val SurfaceBase @Composable get() = MaterialTheme.colorScheme.surface
val SurfaceElevated @Composable get() = MaterialTheme.colorScheme.surfaceContainerHigh
val SurfaceCard @Composable get() = MaterialTheme.colorScheme.surfaceContainerLow
val SurfaceCardHover @Composable get() = MaterialTheme.colorScheme.surfaceContainerHigh
val BorderSubtle @Composable get() = MaterialTheme.colorScheme.outlineVariant
val BorderHighlight @Composable get() = MaterialTheme.colorScheme.primary
val TextPrimary @Composable get() = MaterialTheme.colorScheme.onSurface
val TextSecondary @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant
val TextMuted @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant
val TextInverse @Composable get() = MaterialTheme.colorScheme.onPrimary
val DhyanPrimary @Composable get() = MaterialTheme.colorScheme.primary
val DhyanPrimaryGlow @Composable get() = MaterialTheme.colorScheme.primary
val DhyanSecondary @Composable get() = MaterialTheme.colorScheme.secondary
val DhyanTeal @Composable get() = MaterialTheme.colorScheme.primary
val StateDeepFocus @Composable get() = MaterialTheme.colorScheme.primary
val StateDeepFocusGlow @Composable get() = MaterialTheme.colorScheme.primary
val StateDeepFocusBg @Composable get() = MaterialTheme.colorScheme.primaryContainer
val StateWorking @Composable get() = MaterialTheme.colorScheme.tertiary
val StateWorkingGlow @Composable get() = MaterialTheme.colorScheme.tertiary
val StateWorkingBg @Composable get() = MaterialTheme.colorScheme.tertiaryContainer
val StateDrifting @Composable get() = MaterialTheme.colorScheme.secondary
val StateDriftingGlow @Composable get() = MaterialTheme.colorScheme.secondary
val StateDriftingBg @Composable get() = MaterialTheme.colorScheme.secondaryContainer
val StateDistracted @Composable get() = MaterialTheme.colorScheme.error
val StateDistractedGlow @Composable get() = MaterialTheme.colorScheme.error
val StateDistractedBg @Composable get() = MaterialTheme.colorScheme.errorContainer
val StateRecovering @Composable get() = MaterialTheme.colorScheme.tertiary
val StateRecoveringGlow @Composable get() = MaterialTheme.colorScheme.tertiary
val StateRecoveringBg @Composable get() = MaterialTheme.colorScheme.tertiaryContainer
val StateFatigued @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant
val StateFatiguedBg @Composable get() = MaterialTheme.colorScheme.surfaceVariant
val VerdictAllow @Composable get() = MaterialTheme.colorScheme.primary
val VerdictDigest @Composable get() = MaterialTheme.colorScheme.tertiary
val VerdictDelay @Composable get() = MaterialTheme.colorScheme.secondary
val VerdictSilent @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant

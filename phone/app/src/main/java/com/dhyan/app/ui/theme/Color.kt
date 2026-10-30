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
    background = Color(0xFFF6F7F2), surface = Color(0xFFFFFFFF), low = Color(0xFFEFF2EA),
    high = Color(0xFFE3E8DF), variant = Color(0xFFE0E7DC), text = Color(0xFF1B231D),
    muted = Color(0xFF526157), primary = Color(0xFF426347), onPrimary = Color.White,
    primaryContainer = Color(0xFFD7EBD2), onPrimaryContainer = Color(0xFF203D25),
    secondary = Color(0xFF805B30), onSecondary = Color.White,
    secondaryContainer = Color(0xFFF6E4C5), onSecondaryContainer = Color(0xFF493214),
    tertiary = Color(0xFF356176), onTertiary = Color.White,
    tertiaryContainer = Color(0xFFD4E8F2), onTertiaryContainer = Color(0xFF173E50),
    error = Color(0xFFBA1A1A), onError = Color.White,
    errorContainer = Color(0xFFFFDAD6), onErrorContainer = Color(0xFF410002),
    outline = Color(0xFF727D72), outlineVariant = Color(0xFFD5DDD1)
)
val DarkPalette = DhyanPalette(
    background = Color(0xFF0F1511), surface = Color(0xFF151C17), low = Color(0xFF1B241E),
    high = Color(0xFF263229), variant = Color(0xFF303C33), text = Color(0xFFE3E9DF),
    muted = Color(0xFFB9C6B8), primary = Color(0xFFA6CBA3), onPrimary = Color(0xFF17351F),
    primaryContainer = Color(0xFF2D4732), onPrimaryContainer = Color(0xFFD3EACD),
    secondary = Color(0xFFE4C088), onSecondary = Color(0xFF422D0A),
    secondaryContainer = Color(0xFF49391F), onSecondaryContainer = Color(0xFFF5DFBC),
    tertiary = Color(0xFFA4CDDF), onTertiary = Color(0xFF123544),
    tertiaryContainer = Color(0xFF294652), onTertiaryContainer = Color(0xFFD1E8F2),
    error = Color(0xFFFFB4AB), onError = Color(0xFF690005),
    errorContainer = Color(0xFF542420), onErrorContainer = Color(0xFFFFDAD6),
    outline = Color(0xFF879385), outlineVariant = Color(0xFF354338)
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

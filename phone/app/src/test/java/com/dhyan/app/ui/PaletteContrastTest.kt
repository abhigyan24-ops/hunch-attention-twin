package com.dhyan.app.ui

import androidx.compose.ui.graphics.Color
import com.dhyan.app.ui.theme.DarkPalette
import com.dhyan.app.ui.theme.LightPalette
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

class PaletteContrastTest {
    private fun luminance(color: Color): Double {
        fun linear(c: Float): Double = if (c <= 0.04045f) c / 12.92 else ((c + .055) / 1.055).pow(2.4)
        return .2126 * linear(color.red) + .7152 * linear(color.green) + .0722 * linear(color.blue)
    }
    private fun assertReadable(foreground: Color, background: Color) {
        val a = luminance(foreground)
        val b = luminance(background)
        val ratio = (maxOf(a, b) + .05) / (minOf(a, b) + .05)
        assertTrue("Normal text must have contrast >= 4.5:1; actual $ratio", ratio >= 4.5)
    }

    @Test fun lightAndDarkTextAndActionsHaveAccessibleContrast() {
        listOf(LightPalette, DarkPalette).forEach { p ->
            listOf(p.background, p.surface, p.low, p.high).forEach { surface ->
                assertReadable(p.text, surface)
                assertReadable(p.muted, surface)
            }
            assertReadable(p.onPrimary, p.primary)
            assertReadable(p.onPrimaryContainer, p.primaryContainer)
            assertReadable(p.onSecondaryContainer, p.secondaryContainer)
            assertReadable(p.onTertiaryContainer, p.tertiaryContainer)
            assertReadable(p.onErrorContainer, p.errorContainer)
        }
    }
}

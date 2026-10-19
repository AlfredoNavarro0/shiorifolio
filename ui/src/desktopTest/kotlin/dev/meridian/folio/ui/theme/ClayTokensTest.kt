package dev.meridian.folio.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.meridian.folio.core.HighlightTone
import dev.meridian.folio.core.ReaderTheme
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ClayTokensTest {

    @Test
    fun paletteUsesQuietChromeAndOpaqueCards() {
        val palette = ClayPalette()
        assertEquals(Color(0xFFF5F4F8), palette.canvas)
        assertEquals(Color(0xFFFCFBFE), palette.surface)
        assertEquals(Color(0xFF7354C8), palette.primary)
        assertEquals(Color(0xFF332F3A), palette.onSurface)
        assertEquals(Color(0xFF635F69), palette.textMuted)
        assertEquals(Color(0xFFDED9E7), palette.outlineVariant)
        assertEquals(Color(0xFF10B981), palette.successMint)
        assertEquals(Color(0xFFBA1A1A), palette.error)
        assertEquals(1f, palette.cardSurface.alpha)
        assertFalse(palette.isDarkReading)
    }

    @Test
    fun spacingAndRadiusScalesKeepCompactSoftCorners() {
        assertEquals(8.dp, ClaySpacing.xs)
        assertEquals(12.dp, ClaySpacing.sm)
        assertEquals(16.dp, ClaySpacing.md)
        assertEquals(24.dp, ClaySpacing.lg)
        assertEquals(36.dp, ClaySpacing.xl)
        assertEquals(16.dp, ClaySpacing.gutter)
        assertEquals(32.dp, ClaySpacing.gutterDesktop)
        assertEquals(48.dp, ClaySpacing.marginDesktop)
        assertEquals(8.dp, ClayRadius.sm)
        assertEquals(12.dp, ClayRadius.base)
        assertEquals(20.dp, ClayRadius.md)
        assertEquals(24.dp, ClayRadius.lg)
        assertEquals(32.dp, ClayRadius.xl)
    }

    @Test
    fun typeScaleMatchesDesignMd() {
        assertEquals(ClayTypeSpec(40f, 48f, FontWeight.Black, -0.02f), ClayTypeScale.displayLarge)
        assertEquals(ClayTypeSpec(26f, 34f, FontWeight.ExtraBold, -0.01f), ClayTypeScale.headlineLargeMobile)
        assertEquals(ClayTypeSpec(18f, 24f, FontWeight.Bold, 0f), ClayTypeScale.titleMd)
        assertEquals(ClayTypeSpec(14f, 20f, FontWeight.Normal, 0f), ClayTypeScale.bodyMd)
        assertEquals(ClayTypeSpec(12f, 16f, FontWeight.Bold, 0.02f), ClayTypeScale.labelMd)
        assertEquals(ClayTypeSpec(10f, 14f, FontWeight.Bold, 0.04f), ClayTypeScale.labelSm)
    }

    @Test
    fun shadowsStaySubtleAndPressedSurfacesHaveNoDropShadow() {
        for (stack in listOf(ClayShadows.card, ClayShadows.button, ClayShadows.floating)) {
            assertTrue(stack.outer.isNotEmpty())
            for (layer in stack.outer) {
                assertTrue(layer.color.alpha <= 0.15f, "Drop shadows should not compete with content")
                assertTrue(layer.dy.value in 0f..6f, "Avoid detached offset blocks")
                assertTrue(layer.blur.value > layer.dy.value, "Depth must have a feathered edge")
            }
        }
        assertTrue(ClayShadows.pressed.outer.isEmpty())
        assertTrue(ClayShadows.pressed.inner.isNotEmpty())
        assertTrue(ClayShadows.floating.inner.isEmpty())
    }

    @Test
    fun readingTonesMatchDesignMd() {
        val white = ClayTokens.readingTone(ReaderTheme.WHITE)
        assertFalse(white.isDark)
        assertEquals(Color(0xFFFCF9F3), white.sheet)
        assertEquals(Color(0xFF2B2520), white.ink)
        assertEquals(Color(0xFFF5EDDB), ClayTokens.readingTone(ReaderTheme.SEPIA).sheet)
        assertEquals(Color(0xFFEEF3EE), ClayTokens.readingTone(ReaderTheme.SAGE).sheet)
        val dark = ClayTokens.readingTone(ReaderTheme.DARK)
        assertTrue(dark.isDark)
        assertEquals(Color(0xFF221E2A), dark.sheet)
        // Dark sheet ink must be light.
        assertTrue(relativeLuminance(dark.ink) > 0.7f, "DARK ink luminance should be > 0.7")
    }

    @Test
    fun highContrastRaisesChromeAndReadingContrast() {
        val chrome = ClayTokens.chrome(highContrast = true)
        assertEquals(Color(0xFF0A0A08), chrome.onSurface)
        assertEquals(Color(0xFF1F241F), chrome.onSurfaceVariant)
        assertEquals(Color(0xFF3E443E), chrome.textMuted)
        assertEquals(Color(0xFFB5A592), chrome.outlineVariant)
        assertTrue(relativeLuminance(chrome.onSurface) < relativeLuminance(ClayTokens.chrome().onSurface))

        // Light sheets: high contrast darkens the ink.
        val white = ClayTokens.readingTone(ReaderTheme.WHITE)
        val whiteHc = ClayTokens.readingTone(ReaderTheme.WHITE, highContrast = true)
        assertTrue(relativeLuminance(whiteHc.ink) < relativeLuminance(white.ink))
        // DARK sheet: high contrast lightens the ink.
        val dark = ClayTokens.readingTone(ReaderTheme.DARK)
        val darkHc = ClayTokens.readingTone(ReaderTheme.DARK, highContrast = true)
        assertTrue(relativeLuminance(darkHc.ink) > relativeLuminance(dark.ink))
    }

    @Test
    fun highlightColorRespectsDarkReading() {
        val light = ClayPalette()
        assertEquals(Color(HighlightTone.SAGE.hexLight), light.highlightColor(HighlightTone.SAGE))
        val dark = ClayPalette(isDarkReading = true)
        assertEquals(Color(HighlightTone.SAGE.hexDark), dark.highlightColor(HighlightTone.SAGE))
    }

    @Test
    fun keyColorPairsMeetWcagContrastRatio() {
        for (theme in ReaderTheme.entries) {
            for (highContrast in listOf(false, true)) {
                val palette = ClayTokens.chrome(highContrast, theme)
                val pairs = listOf(
                    palette.onSurface to palette.canvas,
                    palette.onSurfaceVariant to palette.surfaceContainerLow,
                    palette.textMuted to palette.canvas,
                    palette.onSurface to palette.cardSurface,
                    palette.onSurfaceVariant to palette.surfaceContainerHigh,
                    palette.onPrimaryContainer to palette.primaryContainer,
                    palette.onPrimary to palette.primary,
                    palette.onWarningAmber to palette.warningAmber,
                    palette.onSuccessMint to palette.successMint,
                )
                for ((foreground, background) in pairs) {
                    val ratio = contrastRatio(foreground, background)
                    assertTrue(ratio >= 4.5, "$theme contrast $ratio between $foreground and $background should be >= 4.5")
                }
            }
        }
    }

    private fun relativeLuminance(color: Color): Double {
        fun linearize(channel: Float): Double {
            val c = channel.toDouble()
            return if (c <= 0.04045) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * linearize(color.red) +
            0.7152 * linearize(color.green) +
            0.0722 * linearize(color.blue)
    }

    private fun contrastRatio(foreground: Color, background: Color): Double {
        val l1 = relativeLuminance(foreground)
        val l2 = relativeLuminance(background)
        return (max(l1, l2) + 0.05) / (min(l1, l2) + 0.05)
    }
}

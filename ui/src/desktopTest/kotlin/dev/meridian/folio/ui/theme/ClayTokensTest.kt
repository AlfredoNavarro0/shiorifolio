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
    fun paletteHasExactDesignMdHexValues() {
        val palette = ClayPalette()
        assertEquals(Color(0xFFF4F1FA), palette.canvas)
        assertEquals(Color(0xFFFEF7FF), palette.surface)
        assertEquals(Color(0xFF7C3AED), palette.primary)
        assertEquals(Color(0xFF332F3A), palette.onSurface)
        assertEquals(Color(0xFF635F69), palette.textMuted)
        assertEquals(Color(0xFFCCC3D8), palette.outlineVariant)
        assertEquals(Color(0xFF10B981), palette.successMint)
        assertEquals(Color(0xFFBA1A1A), palette.error)
        // Compose packs alpha as 8-bit: 0.75 -> 191/255 = 0.7490196.
        assertEquals(0.75f, palette.cardSurface.alpha, 1f / 255f)
        assertFalse(palette.isDarkReading)
    }

    @Test
    fun spacingAndRadiusScalesMatchDesignMd() {
        assertEquals(8.dp, ClaySpacing.xs)
        assertEquals(12.dp, ClaySpacing.sm)
        assertEquals(16.dp, ClaySpacing.md)
        assertEquals(24.dp, ClaySpacing.lg)
        assertEquals(36.dp, ClaySpacing.xl)
        assertEquals(16.dp, ClaySpacing.gutter)
        assertEquals(32.dp, ClaySpacing.gutterDesktop)
        assertEquals(48.dp, ClaySpacing.marginDesktop)
        assertEquals(8.dp, ClayRadius.sm)
        assertEquals(16.dp, ClayRadius.base)
        assertEquals(24.dp, ClayRadius.md)
        assertEquals(32.dp, ClayRadius.lg)
        assertEquals(48.dp, ClayRadius.xl)
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
    fun cardShadowStackHasLiteralDesignMdLayers() {
        val card = ClayShadows.card
        assertEquals(2, card.outer.size)
        assertEquals(2, card.inner.size)
        with(card.outer[0]) {
            assertEquals(16.dp, dx)
            assertEquals(16.dp, dy)
            assertEquals(32.dp, blur)
            assertEquals(0.dp, spread)
            assertEquals(Color(0x38A096B4), color)
        }
        with(card.outer[1]) {
            assertEquals((-10).dp, dx)
            assertEquals((-10).dp, dy)
            assertEquals(24.dp, blur)
            assertEquals(0.dp, spread)
            assertEquals(Color(0xF2FFFFFF), color)
        }
        with(card.inner[0]) {
            assertEquals(6.dp, dx)
            assertEquals(6.dp, dy)
            assertEquals(12.dp, blur)
            assertEquals(0.dp, spread)
            assertEquals(Color(0x0A8B5CF6), color)
        }
        with(card.inner[1]) {
            assertEquals((-6).dp, dx)
            assertEquals((-6).dp, dy)
            assertEquals(12.dp, blur)
            assertEquals(0.dp, spread)
            assertEquals(Color(0xFFFFFFFF), color)
        }
        // Pressed is purely recessed: no outer drop layers.
        assertTrue(ClayShadows.pressed.outer.isEmpty())
        assertEquals(2, ClayShadows.pressed.inner.size)
    }

    @Test
    fun buttonShadowStackHasLiteralDesignMdLayers() {
        val button = ClayShadows.button
        assertEquals(2, button.outer.size)
        assertEquals(2, button.inner.size)
        with(button.outer[0]) {
            assertEquals(10.dp, dx)
            assertEquals(10.dp, dy)
            assertEquals(20.dp, blur)
            assertEquals(0.dp, spread)
            assertEquals(Color(0x477C3AED), color)
        }
        with(button.outer[1]) {
            assertEquals((-6).dp, dx)
            assertEquals((-6).dp, dy)
            assertEquals(14.dp, blur)
            assertEquals(0.dp, spread)
            assertEquals(Color(0xB3FFFFFF), color)
        }
        with(button.inner[0]) {
            assertEquals(3.dp, dx)
            assertEquals(3.dp, dy)
            assertEquals(6.dp, blur)
            assertEquals(0.dp, spread)
            assertEquals(Color(0x66FFFFFF), color)
        }
        with(button.inner[1]) {
            assertEquals((-3).dp, dx)
            assertEquals((-3).dp, dy)
            assertEquals(6.dp, blur)
            assertEquals(0.dp, spread)
            assertEquals(Color(0x1F000000), color)
        }
    }

    @Test
    fun floatingShadowStackCarriesLiteralDesignMdLayersAndSpread() {
        val floating = ClayShadows.floating
        assertTrue(floating.inner.isEmpty())
        assertEquals(2, floating.outer.size)
        // 0 20px 35px -8px rgba(124,58,237,0.2)
        with(floating.outer[0]) {
            assertEquals(0.dp, dx)
            assertEquals(20.dp, dy)
            assertEquals(35.dp, blur)
            assertEquals((-8).dp, spread)
            assertEquals(Color(0x337C3AED), color)
        }
        // 0 10px 15px -5px rgba(0,0,0,0.04)
        with(floating.outer[1]) {
            assertEquals(0.dp, dx)
            assertEquals(10.dp, dy)
            assertEquals(15.dp, blur)
            assertEquals((-5).dp, spread)
            assertEquals(Color(0x0A000000), color)
        }
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
        val palette = ClayPalette()
        val pairs = listOf(
            palette.onSurface to palette.canvas,
            palette.onSurfaceVariant to palette.surfaceContainerLow,
            palette.textMuted to palette.canvas,
            palette.onPrimary to palette.primary,
            palette.onWarningAmber to palette.warningAmber,
            palette.onSuccessMint to palette.successMint,
        )
        for ((foreground, background) in pairs) {
            val ratio = contrastRatio(foreground, background)
            assertTrue(ratio >= 4.5, "Contrast ${ratio} between $foreground and $background should be >= 4.5")
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

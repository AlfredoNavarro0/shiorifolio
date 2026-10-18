package dev.meridian.folio.ui.theme

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.meridian.folio.core.BookFontFamilyOption
import dev.meridian.folio.core.BookTextAlignment
import dev.meridian.folio.core.KeyBindingSpec
import dev.meridian.folio.core.UiFontFamilyOption
import dev.meridian.folio.core.UiFontWeightOption
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class ClayHelpersTest {

    @Test
    fun canonicalKeyNameCoversNavigationEditingAndUnknownKeys() {
        assertEquals("PageDown", canonicalKeyName(Key.PageDown))
        assertEquals("PageUp", canonicalKeyName(Key.PageUp))
        assertEquals("DirectionLeft", canonicalKeyName(Key.DirectionLeft))
        assertEquals("DirectionRight", canonicalKeyName(Key.DirectionRight))
        assertEquals("Tab", canonicalKeyName(Key.Tab))
        assertEquals("Space", canonicalKeyName(Key.Spacebar))
        assertEquals("Home", canonicalKeyName(Key.MoveHome))
        assertEquals("F11", canonicalKeyName(Key.F11))
        assertEquals("A", canonicalKeyName(Key.A))
        assertEquals("0", canonicalKeyName(Key.Zero))
        assertNull(canonicalKeyName(Key.Escape))
    }

    @Test
    fun keyBindingSpecRoundtripCapturesCtrlShiftAndAlt() = runComposeUiTest {
        val captured = captureKeyEvents()
        val sink = onNodeWithTag("key_sink")
        sink.requestFocus()

        sink.performKeyInput {
            keyDown(Key.CtrlLeft)
            pressKey(Key.PageDown)
            keyUp(Key.CtrlLeft)
        }
        var event = captured.lastKeyDown()
        var spec = event.toKeyBindingSpecOrNull()
        assertEquals(KeyBindingSpec("PageDown", ctrl = true), spec)
        assertTrue(event.matches(spec!!))
        assertFalse(event.matches(KeyBindingSpec("PageDown")))
        assertFalse(event.matches(KeyBindingSpec("PageDown", ctrl = true, shift = true)))

        sink.performKeyInput {
            keyDown(Key.ShiftLeft)
            keyDown(Key.AltLeft)
            pressKey(Key.PageDown)
            keyUp(Key.AltLeft)
            keyUp(Key.ShiftLeft)
        }
        event = captured.lastKeyDown()
        spec = event.toKeyBindingSpecOrNull()
        assertEquals(KeyBindingSpec("PageDown", shift = true, alt = true), spec)
        assertTrue(event.matches(spec!!))
        assertFalse(event.matches(KeyBindingSpec("PageDown", shift = true)))
    }

    @Test
    fun unknownKeyProducesNoBindingSpec() = runComposeUiTest {
        val captured = captureKeyEvents()
        val sink = onNodeWithTag("key_sink")
        sink.requestFocus()
        sink.performKeyInput { pressKey(Key.Escape) }
        val event = captured.lastKeyDown()
        assertNull(event.toKeyBindingSpecOrNull())
        assertFalse(event.matches(KeyBindingSpec("Escape")))
    }

    @Test
    fun fontWeightOptionMapsToComposeWeights() {
        assertEquals(FontWeight.Normal, UiFontWeightOption.REGULAR.toComposeFontWeight())
        assertEquals(FontWeight.Medium, UiFontWeightOption.MEDIUM.toComposeFontWeight())
        assertEquals(FontWeight.SemiBold, UiFontWeightOption.SEMIBOLD.toComposeFontWeight())
    }

    @Test
    fun bookTextAlignmentMapsToComposeTextAlign() {
        assertEquals(TextAlign.Justify, BookTextAlignment.JUSTIFY.toComposeTextAlign())
        assertEquals(TextAlign.Start, BookTextAlignment.START.toComposeTextAlign())
        assertEquals(TextAlign.Center, BookTextAlignment.CENTER.toComposeTextAlign())
    }

    @Test
    fun bookFontFamilyOptionResolvesToSystemFamily() {
        for (option in BookFontFamilyOption.entries) {
            assertNotNull(option.toComposeFontFamily(), "Family for $option should not be null")
        }
    }

    @Test
    fun uiFontFamilyOptionReadsThemeFontFamilies() = runComposeUiTest {
        var sans: FontFamily? = null
        var serif: FontFamily? = null
        var mono: FontFamily? = null
        setContent {
            // Default LocalClayUiFontFamilies: system families until the theme provides its own.
            sans = UiFontFamilyOption.SANS_SERIF.toComposeFontFamily()
            serif = UiFontFamilyOption.SERIF.toComposeFontFamily()
            mono = UiFontFamilyOption.MONOSPACE.toComposeFontFamily()
        }
        waitForIdle()
        assertEquals(FontFamily.SansSerif, sans)
        assertEquals(FontFamily.Serif, serif)
        assertEquals(FontFamily.Monospace, mono)
    }

    @Test
    fun clayTextFieldColorsUsesClayPalette() = runComposeUiTest {
        val palette = ClayTokens.chrome()
        var focusedBorder: Color? = null
        var unfocusedBorder: Color? = null
        var cursor: Color? = null
        setContent {
            val colors = clayTextFieldColors(palette)
            focusedBorder = colors.focusedIndicatorColor
            unfocusedBorder = colors.unfocusedIndicatorColor
            cursor = colors.cursorColor
        }
        waitForIdle()
        assertEquals(palette.primary, focusedBorder)
        assertEquals(palette.outlineVariant, unfocusedBorder)
        assertEquals(palette.primary, cursor)
    }

    /** Installs a focusable sink that records every key-down event it receives. */
    private fun androidx.compose.ui.test.ComposeUiTest.captureKeyEvents(): KeyEventRecorder {
        val recorder = KeyEventRecorder()
        setContent {
            Box(
                Modifier
                    .testTag("key_sink")
                    .size(48.dp)
                    .focusable()
                    .onKeyEvent {
                        if (it.type == KeyEventType.KeyDown) recorder.events += it
                        false
                    },
            )
        }
        return recorder
    }

    private class KeyEventRecorder {
        val events = mutableListOf<KeyEvent>()
        fun lastKeyDown(): KeyEvent = events.last()
    }
}

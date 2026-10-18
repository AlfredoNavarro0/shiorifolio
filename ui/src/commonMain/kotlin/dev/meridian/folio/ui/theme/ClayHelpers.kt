package dev.meridian.folio.ui.theme

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.meridian.folio.core.BookFontFamilyOption
import dev.meridian.folio.core.BookTextAlignment
import dev.meridian.folio.core.KeyBindingSpec
import dev.meridian.folio.core.UiFontFamilyOption
import dev.meridian.folio.core.UiFontWeightOption

/**
 * UI font families resolved for the clay chrome. The theme layer (ClayTheme) provides the
 * bundled Nunito Sans / DM Sans families; the defaults are the system families.
 */
@Immutable
data class ClayUiFontFamilies(
    val sans: FontFamily,
    val serif: FontFamily,
    val mono: FontFamily,
)

/** Resolved UI font families; defaults to system families until the theme provides its own. */
val LocalClayUiFontFamilies = staticCompositionLocalOf {
    ClayUiFontFamilies(FontFamily.SansSerif, FontFamily.Serif, FontFamily.Monospace)
}

/** UI font family for this option; SANS_SERIF resolves to the theme's bundled sans (DM Sans). */
@Composable
fun UiFontFamilyOption.toComposeFontFamily(): FontFamily =
    when (this) {
        UiFontFamilyOption.SANS_SERIF -> LocalClayUiFontFamilies.current.sans
        UiFontFamilyOption.SERIF -> LocalClayUiFontFamilies.current.serif
        UiFontFamilyOption.MONOSPACE -> LocalClayUiFontFamilies.current.mono
    }

fun UiFontWeightOption.toComposeFontWeight(): FontWeight =
    when (this) {
        UiFontWeightOption.REGULAR -> FontWeight.Normal
        UiFontWeightOption.MEDIUM -> FontWeight.Medium
        UiFontWeightOption.SEMIBOLD -> FontWeight.SemiBold
    }

fun BookFontFamilyOption.toComposeFontFamily(): FontFamily =
    when (this) {
        BookFontFamilyOption.SERIF -> FontFamily.Serif
        BookFontFamilyOption.SANS_SERIF -> FontFamily.SansSerif
        BookFontFamilyOption.MONOSPACE -> FontFamily.Monospace
    }

fun BookTextAlignment.toComposeTextAlign(): TextAlign =
    when (this) {
        BookTextAlignment.JUSTIFY -> TextAlign.Justify
        BookTextAlignment.START -> TextAlign.Start
        BookTextAlignment.CENTER -> TextAlign.Center
    }

fun canonicalKeyName(key: Key): String? = when (key) {
    Key.PageDown -> "PageDown"
    Key.PageUp -> "PageUp"
    Key.DirectionLeft -> "DirectionLeft"
    Key.DirectionRight -> "DirectionRight"
    Key.DirectionUp -> "DirectionUp"
    Key.DirectionDown -> "DirectionDown"
    Key.Tab -> "Tab"
    Key.Spacebar -> "Space"
    Key.MoveHome -> "Home"
    Key.MoveEnd -> "End"
    Key.F1 -> "F1"
    Key.F2 -> "F2"
    Key.F3 -> "F3"
    Key.F4 -> "F4"
    Key.F5 -> "F5"
    Key.F6 -> "F6"
    Key.F7 -> "F7"
    Key.F8 -> "F8"
    Key.F9 -> "F9"
    Key.F10 -> "F10"
    Key.F11 -> "F11"
    Key.F12 -> "F12"
    Key.A -> "A"
    Key.B -> "B"
    Key.C -> "C"
    Key.D -> "D"
    Key.E -> "E"
    Key.F -> "F"
    Key.G -> "G"
    Key.H -> "H"
    Key.I -> "I"
    Key.J -> "J"
    Key.K -> "K"
    Key.L -> "L"
    Key.M -> "M"
    Key.N -> "N"
    Key.O -> "O"
    Key.P -> "P"
    Key.Q -> "Q"
    Key.R -> "R"
    Key.S -> "S"
    Key.T -> "T"
    Key.U -> "U"
    Key.V -> "V"
    Key.W -> "W"
    Key.X -> "X"
    Key.Y -> "Y"
    Key.Z -> "Z"
    Key.Zero -> "0"
    Key.One -> "1"
    Key.Two -> "2"
    Key.Three -> "3"
    Key.Four -> "4"
    Key.Five -> "5"
    Key.Six -> "6"
    Key.Seven -> "7"
    Key.Eight -> "8"
    Key.Nine -> "9"
    else -> null
}

fun KeyEvent.matches(spec: KeyBindingSpec): Boolean {
    val name = canonicalKeyName(key) ?: return false
    return name.equals(spec.keyName, ignoreCase = true) &&
        isCtrlPressed == spec.ctrl &&
        isShiftPressed == spec.shift &&
        isAltPressed == spec.alt
}

fun KeyEvent.toKeyBindingSpecOrNull(): KeyBindingSpec? {
    val name = canonicalKeyName(key) ?: return null
    return KeyBindingSpec(
        keyName = name,
        ctrl = isCtrlPressed,
        shift = isShiftPressed,
        alt = isAltPressed,
    )
}

/**
 * Keyboard-accessible clickable modifier with a visible 2.dp focus ring (`palette.focusRing`)
 * when traversed via `Tab` / `Shift + Tab` on Windows & Linux.
 */
@Composable
fun Modifier.clayFocusableClickable(
    palette: ClayPalette,
    shape: Shape = RoundedCornerShape(6.dp),
    enabled: Boolean = true,
    onClick: () -> Unit,
): Modifier {
    var isFocused by remember { mutableStateOf(false) }
    return this
        .onFocusChanged { isFocused = it.isFocused }
        .then(
            if (isFocused && enabled) {
                Modifier.border(2.dp, palette.focusRing, shape)
            } else {
                Modifier
            },
        )
        .clickable(enabled = enabled, onClick = onClick)
}

/**
 * Outlined text field colors mapped from the clay palette: sunken well container
 * (`surfaceSecondary`), `onSurface` text, `primary` cursor and focused border,
 * `outlineVariant` unfocused border, `textMuted` placeholder.
 */
@Composable
fun clayTextFieldColors(palette: ClayPalette): TextFieldColors =
    OutlinedTextFieldDefaults.colors(
        focusedTextColor = palette.onSurface,
        unfocusedTextColor = palette.onSurface,
        disabledTextColor = palette.disabledContent,
        cursorColor = palette.primary,
        focusedContainerColor = palette.surfaceSecondary,
        unfocusedContainerColor = palette.surfaceSecondary,
        disabledContainerColor = palette.disabledContainer,
        focusedBorderColor = palette.primary,
        unfocusedBorderColor = palette.outlineVariant,
        disabledBorderColor = palette.outlineVariant.copy(alpha = 0.5f),
        focusedLabelColor = palette.primary,
        unfocusedLabelColor = palette.onSurfaceVariant,
        disabledLabelColor = palette.disabledContent,
        focusedPlaceholderColor = palette.textMuted,
        unfocusedPlaceholderColor = palette.textMuted,
        focusedLeadingIconColor = palette.onSurfaceVariant,
        unfocusedLeadingIconColor = palette.textMuted,
        focusedTrailingIconColor = palette.onSurfaceVariant,
        unfocusedTrailingIconColor = palette.textMuted,
    )

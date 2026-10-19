package dev.meridian.folio.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.meridian.folio.core.HighlightTone
import dev.meridian.folio.core.ReaderTheme

/**
 * Color tokens of the Stitch "Tactile Claymorphism" design system (DESIGN.md).
 * Constructor defaults are the app chrome palette, so `ClayPalette()` is the chrome.
 */
@Immutable
data class ClayPalette(
    val canvas: Color = Color(0xFFF5F4F8),
    val surface: Color = Color(0xFFFCFBFE),
    val surfaceContainerLowest: Color = Color(0xFFFFFFFF),
    val surfaceContainerLow: Color = Color(0xFFF9F8FC),
    val surfaceContainer: Color = Color(0xFFF3F1F7),
    val surfaceContainerHigh: Color = Color(0xFFEEEBF3),
    val surfaceContainerHighest: Color = Color(0xFFE6E2ED),
    val surfaceSecondary: Color = Color(0xFFF0EEF5),
    val cardSurface: Color = Color.White,
    val onSurface: Color = Color(0xFF332F3A),
    val onSurfaceVariant: Color = Color(0xFF4A4455),
    val textMuted: Color = Color(0xFF635F69),
    val outline: Color = Color(0xFF7B7487),
    val outlineVariant: Color = Color(0xFFDED9E7),
    val primary: Color = Color(0xFF7354C8),
    val primaryDeep: Color = Color(0xFF6040B1),
    val onPrimary: Color = Color(0xFFFFFFFF),
    val primaryContainer: Color = Color(0xFFEDE0FF),
    val onPrimaryContainer: Color = Color(0xFF25005A),
    val secondary: Color = Color(0xFFDB2777),
    val onSecondary: Color = Color(0xFFFFFFFF),
    val secondaryContainer: Color = Color(0xFFFFD9E2),
    val onSecondaryContainer: Color = Color(0xFF3F001C),
    val tertiary: Color = Color(0xFF0EA5E9),
    val onTertiary: Color = Color(0xFFFFFFFF),
    val successMint: Color = Color(0xFF10B981),
    val onSuccessMint: Color = Color(0xFF06341F),
    val warningAmber: Color = Color(0xFFF59E0B),
    val onWarningAmber: Color = Color(0xFF3D2800),
    val error: Color = Color(0xFFBA1A1A),
    val onError: Color = Color(0xFFFFFFFF),
    val errorContainer: Color = Color(0xFFFFDAD6),
    val onErrorContainer: Color = Color(0xFF93000A),
    val disabledContent: Color = Color(0xFF9A93A6),
    val disabledContainer: Color = Color(0xFFE9E3F0),
    val focusRing: Color = Color(0xFF630ED4),
    val spineShadow: Color = Color(0xFF332F3A),
    val isDarkReading: Boolean = false,
) {
    /** Highlight color for [tone]: dark variant when reading on a dark sheet. */
    fun highlightColor(tone: HighlightTone): Color =
        Color(if (isDarkReading) tone.hexDark else tone.hexLight)

    /** Native menus, dialogs and controls share the global app palette. */
    fun toMaterialColorScheme(): ColorScheme =
        (if (isDarkReading) darkColorScheme() else lightColorScheme()).copy(
            primary = primary,
            onPrimary = onPrimary,
            primaryContainer = primaryContainer,
            onPrimaryContainer = onPrimaryContainer,
            secondary = secondary,
            onSecondary = onSecondary,
            secondaryContainer = secondaryContainer,
            onSecondaryContainer = onSecondaryContainer,
            tertiary = tertiary,
            onTertiary = onTertiary,
            error = error,
            onError = onError,
            errorContainer = errorContainer,
            onErrorContainer = onErrorContainer,
            background = canvas,
            onBackground = onSurface,
            surface = surface,
            onSurface = onSurface,
            surfaceVariant = surfaceSecondary,
            surfaceTint = primary,
            surfaceContainerLowest = surfaceContainerLowest,
            surfaceContainerLow = surfaceContainerLow,
            surfaceContainer = surfaceContainer,
            surfaceContainerHigh = surfaceContainerHigh,
            surfaceContainerHighest = surfaceContainerHighest,
            onSurfaceVariant = onSurfaceVariant,
            outline = outline,
            outlineVariant = outlineVariant,
        )
}

/** Reading content colors, kept separate from the surrounding application surfaces. */
@Immutable
data class ReadingTone(
    val isDark: Boolean,
    val sheet: Color,
    val ink: Color,
    val inkSoft: Color,
    val muted: Color,
    val border: Color,
    /**
     * Legible accent on [sheet] (≥3:1): the `primary` violet on the light sheets, a light violet
     * on DARK, where `primary` only reaches ~2.9:1. Used by in-sheet accents such as `+ Frase`.
     */
    val accent: Color,
)

/** Token accessors for the Stitch "Tactile Claymorphism" design system. */
object ClayTokens {
    /** Global palette: navigation, panels, controls and dialogs follow the selected theme. */
    fun chrome(highContrast: Boolean = false, theme: ReaderTheme = ReaderTheme.WHITE): ClayPalette {
        val base = when (theme) {
            ReaderTheme.WHITE -> ClayPalette()
            ReaderTheme.SEPIA -> ClayPalette().copy(
                canvas = Color(0xFFEEE7DA), surface = Color(0xFFF8F1E5),
                surfaceContainerLowest = Color(0xFFFFFCF6), surfaceContainerLow = Color(0xFFF9F3E8),
                surfaceContainer = Color(0xFFF1E8D9), surfaceContainerHigh = Color(0xFFE9DECC),
                surfaceContainerHighest = Color(0xFFE0D2BD), surfaceSecondary = Color(0xFFF1E8D9),
                cardSurface = Color(0xFFFFFBF3), onSurface = Color(0xFF382D22),
                onSurfaceVariant = Color(0xFF554634), textMuted = Color(0xFF6A5A48),
                outline = Color(0xFF8C785E), outlineVariant = Color(0xFFD5C8B4),
                primary = Color(0xFF795638), primaryDeep = Color(0xFF67472E),
                primaryContainer = Color(0xFFEFDFC2), onPrimaryContainer = Color(0xFF3A2814),
                focusRing = Color(0xFF795638),
            )
            ReaderTheme.SAGE -> ClayPalette().copy(
                canvas = Color(0xFFE8EFE9), surface = Color(0xFFF5F9F5),
                surfaceContainerLowest = Color(0xFFFAFDFA), surfaceContainerLow = Color(0xFFF1F6F1),
                surfaceContainer = Color(0xFFE7EFE8), surfaceContainerHigh = Color(0xFFDFE9E0),
                surfaceContainerHighest = Color(0xFFD4E1D6), surfaceSecondary = Color(0xFFE2EBE3),
                cardSurface = Color(0xFFF8FCF8), onSurface = Color(0xFF25372B),
                onSurfaceVariant = Color(0xFF3F5446), textMuted = Color(0xFF536658),
                outline = Color(0xFF738B79), outlineVariant = Color(0xFFBDCFBF),
                primary = Color(0xFF365F49), primaryDeep = Color(0xFF274D38),
                primaryContainer = Color(0xFFCFE2D2), onPrimaryContainer = Color(0xFF1C3B28),
                focusRing = Color(0xFF365F49),
            )
            ReaderTheme.DARK -> ClayPalette().copy(
                canvas = Color(0xFF18171E), surface = Color(0xFF211F29),
                surfaceContainerLowest = Color(0xFF16151B), surfaceContainerLow = Color(0xFF24222C),
                surfaceContainer = Color(0xFF2A2733), surfaceContainerHigh = Color(0xFF302D3A),
                surfaceContainerHighest = Color(0xFF3B3646), surfaceSecondary = Color(0xFF2A2733),
                cardSurface = Color(0xFF25222D), onSurface = Color(0xFFF2EEF8),
                onSurfaceVariant = Color(0xFFD4CDDF), textMuted = Color(0xFFB6ADC5),
                outline = Color(0xFF91869F), outlineVariant = Color(0xFF4C445B),
                primary = Color(0xFFBDA4FF), primaryDeep = Color(0xFFA88BED), onPrimary = Color(0xFF251742),
                primaryContainer = Color(0xFF3D3059), onPrimaryContainer = Color(0xFFEADDFF),
                secondary = Color(0xFFF1A2C6), onSecondary = Color(0xFF45112B),
                secondaryContainer = Color(0xFF4B283D), onSecondaryContainer = Color(0xFFFFD9E2),
                tertiary = Color(0xFF8CCFFF), onTertiary = Color(0xFF102F40),
                successMint = Color(0xFF73D9AE), warningAmber = Color(0xFFFFC875),
                error = Color(0xFFFFB4AB), onError = Color(0xFF690005),
                errorContainer = Color(0xFF93000A), onErrorContainer = Color(0xFFFFDAD6),
                disabledContent = Color(0xFF8C829C), disabledContainer = Color(0xFF302B3B),
                focusRing = Color(0xFFD2BBFF), isDarkReading = true,
            )
        }
        if (!highContrast) return base
        if (base.isDarkReading) return base.copy(
            onSurface = Color.White, onSurfaceVariant = Color(0xFFF1EDF8),
            textMuted = Color(0xFFD4CDDF), outlineVariant = Color(0xFFA89DB8),
        )
        return base.copy(
            onSurface = Color(0xFF0A0A08),
            onSurfaceVariant = Color(0xFF1F241F),
            textMuted = Color(0xFF3E443E),
            outlineVariant = Color(0xFFB5A592),
        )
    }

    /** Reading sheet tone for [theme]; [highContrast] darkens ink on light sheets and lightens it on DARK. */
    fun readingTone(theme: ReaderTheme, highContrast: Boolean = false): ReadingTone {
        val base = when (theme) {
            ReaderTheme.WHITE -> ReadingTone(
                isDark = false,
                sheet = Color(0xFFFCF9F3),
                ink = Color(0xFF2B2520),
                inkSoft = Color(0xFF4A4238),
                muted = Color(0xFF6E655A),
                border = Color(0xFFE4DCD0),
                accent = Color(0xFF7C3AED),
            )
            ReaderTheme.SEPIA -> ReadingTone(
                isDark = false,
                sheet = Color(0xFFF5EDDB),
                ink = Color(0xFF261D14),
                inkSoft = Color(0xFF47392B),
                muted = Color(0xFF6E5C49),
                border = Color(0xFFDECBB3),
                accent = Color(0xFF7C3AED),
            )
            ReaderTheme.SAGE -> ReadingTone(
                isDark = false,
                sheet = Color(0xFFEEF3EE),
                ink = Color(0xFF1B2B21),
                inkSoft = Color(0xFF36433A),
                muted = Color(0xFF5A695D),
                border = Color(0xFFC8D6C9),
                accent = Color(0xFF7C3AED),
            )
            ReaderTheme.DARK -> ReadingTone(
                isDark = true,
                sheet = Color(0xFF221E2A),
                ink = Color(0xFFF4EFFC),
                inkSoft = Color(0xFFD9D2E6),
                muted = Color(0xFFB0A8BF),
                border = Color(0xFF4A4455),
                accent = Color(0xFFD2BBFF),
            )
        }
        if (!highContrast) return base
        return if (base.isDark) {
            // Lighten the text on the dark sheet to raise contrast.
            base.copy(
                ink = Color(0xFFFFFFFF),
                inkSoft = Color(0xFFEDE8F5),
                muted = Color(0xFFCFC8DE),
                border = Color(0xFF6E6880),
            )
        } else {
            // Darken the text on light sheets to raise contrast.
            base.copy(
                ink = Color(0xFF0A0A08),
                inkSoft = Color(0xFF1F241F),
                muted = Color(0xFF3E443E),
                border = Color(0xFFB5A592),
            )
        }
    }
}

/** Spacing scale (dp) from the design system (1rem = 16dp). */
object ClaySpacing {
    val xs: Dp = 8.dp
    val sm: Dp = 12.dp
    val md: Dp = 16.dp
    val lg: Dp = 24.dp
    val xl: Dp = 36.dp
    val gutter: Dp = 16.dp
    val gutterDesktop: Dp = 32.dp
    val marginDesktop: Dp = 48.dp
}

/** Corner radii (dp) from the design system. */
object ClayRadius {
    val sm: Dp = 8.dp
    val base: Dp = 12.dp
    val md: Dp = 20.dp
    val lg: Dp = 24.dp
    val xl: Dp = 32.dp
}

/** One step of the clay type scale (converted into TextStyles by the theme layer). */
@Immutable
data class ClayTypeSpec(
    val fontSizeSp: Float,
    val lineHeightSp: Float,
    val weight: FontWeight,
    val letterSpacingEm: Float,
)

/** Type scale from the DESIGN.md (Nunito Sans display/headline/title, DM Sans body/label). */
object ClayTypeScale {
    val displayLarge = ClayTypeSpec(fontSizeSp = 40f, lineHeightSp = 48f, weight = FontWeight.Black, letterSpacingEm = -0.02f)
    val headlineLarge = ClayTypeSpec(fontSizeSp = 32f, lineHeightSp = 40f, weight = FontWeight.ExtraBold, letterSpacingEm = -0.01f)
    val headlineLargeMobile = ClayTypeSpec(fontSizeSp = 26f, lineHeightSp = 34f, weight = FontWeight.ExtraBold, letterSpacingEm = -0.01f)
    val headlineMd = ClayTypeSpec(fontSizeSp = 24f, lineHeightSp = 32f, weight = FontWeight.Bold, letterSpacingEm = 0f)
    val headlineSm = ClayTypeSpec(fontSizeSp = 20f, lineHeightSp = 28f, weight = FontWeight.Bold, letterSpacingEm = 0f)
    val titleMd = ClayTypeSpec(fontSizeSp = 18f, lineHeightSp = 24f, weight = FontWeight.Bold, letterSpacingEm = 0f)
    val bodyLg = ClayTypeSpec(fontSizeSp = 16f, lineHeightSp = 24f, weight = FontWeight.Medium, letterSpacingEm = 0f)
    val bodyMd = ClayTypeSpec(fontSizeSp = 14f, lineHeightSp = 20f, weight = FontWeight.Normal, letterSpacingEm = 0f)
    val bodySm = ClayTypeSpec(fontSizeSp = 12f, lineHeightSp = 16f, weight = FontWeight.Normal, letterSpacingEm = 0f)
    val labelLg = ClayTypeSpec(fontSizeSp = 14f, lineHeightSp = 20f, weight = FontWeight.Bold, letterSpacingEm = 0f)
    val labelMd = ClayTypeSpec(fontSizeSp = 12f, lineHeightSp = 16f, weight = FontWeight.Bold, letterSpacingEm = 0.02f)
    val labelSm = ClayTypeSpec(fontSizeSp = 10f, lineHeightSp = 14f, weight = FontWeight.Bold, letterSpacingEm = 0.04f)
}

/** One layer of a clay shadow stack. `spread` insets/expands the layer (negative shrinks it). */
@Immutable
data class ClayShadowLayer(
    val dx: Dp,
    val dy: Dp,
    val blur: Dp,
    val color: Color,
    val spread: Dp = 0.dp,
)

/** Multi-layer clay shadow: outer (drop) layers plus inner (inset) layers. */
@Immutable
data class ClayShadowStack(
    val outer: List<ClayShadowLayer>,
    val inner: List<ClayShadowLayer>,
)

/** Restrained clay depth: short offsets, neutral shadows and a fine light rim. */
object ClayShadows {
    val card = ClayShadowStack(
        outer = listOf(
            ClayShadowLayer(0.dp, 3.dp, 12.dp, Color(0x143B304F)),
            ClayShadowLayer(0.dp, 1.dp, 3.dp, Color(0x083B304F)),
        ),
        inner = listOf(
            ClayShadowLayer(0.dp, 1.dp, 2.dp, Color(0x08FFFFFF)),
            ClayShadowLayer(0.dp, (-1).dp, 2.dp, Color(0x28FFFFFF)),
        ),
    )
    val button = ClayShadowStack(
        outer = listOf(
            ClayShadowLayer(0.dp, 2.dp, 6.dp, Color(0x247354C8)),
            ClayShadowLayer(0.dp, 1.dp, 2.dp, Color(0x103B304F)),
        ),
        inner = listOf(
            ClayShadowLayer(0.dp, 1.dp, 2.dp, Color(0x38FFFFFF)),
            ClayShadowLayer(0.dp, (-1).dp, 2.dp, Color(0x10000000)),
        ),
    )
    val pressed = ClayShadowStack(
        outer = emptyList(),
        inner = listOf(
            ClayShadowLayer(0.dp, 1.dp, 3.dp, Color(0x183B304F)),
            ClayShadowLayer(0.dp, (-1).dp, 2.dp, Color(0x60FFFFFF)),
        ),
    )
    val floating = ClayShadowStack(
        outer = listOf(
            ClayShadowLayer(0.dp, 5.dp, 16.dp, Color(0x183B304F), spread = (-2).dp),
            ClayShadowLayer(0.dp, 1.dp, 4.dp, Color(0x0C3B304F)),
        ),
        inner = emptyList(),
    )
}

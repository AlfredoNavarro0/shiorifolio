package dev.meridian.folio.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.lightColorScheme
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
    val canvas: Color = Color(0xFFF4F1FA),
    val surface: Color = Color(0xFFFEF7FF),
    val surfaceContainerLowest: Color = Color(0xFFFFFFFF),
    val surfaceContainerLow: Color = Color(0xFFF9F1FF),
    val surfaceContainer: Color = Color(0xFFF3EBFA),
    val surfaceContainerHigh: Color = Color(0xFFEDE5F4),
    val surfaceContainerHighest: Color = Color(0xFFE7E0EE),
    val surfaceSecondary: Color = Color(0xFFEFEBF5),
    val cardSurface: Color = Color.White.copy(alpha = 0.75f),
    val onSurface: Color = Color(0xFF332F3A),
    val onSurfaceVariant: Color = Color(0xFF4A4455),
    val textMuted: Color = Color(0xFF635F69),
    val outline: Color = Color(0xFF7B7487),
    val outlineVariant: Color = Color(0xFFCCC3D8),
    val primary: Color = Color(0xFF7C3AED),
    val primaryDeep: Color = Color(0xFF630ED4),
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

    /** Material 3 color scheme mapped from the clay tokens (light chrome). */
    fun toMaterialColorScheme(): ColorScheme =
        lightColorScheme(
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
            onSurfaceVariant = onSurfaceVariant,
            outline = outline,
            outlineVariant = outlineVariant,
        )
}

/** Reading sheet tone: only the reading surface is tinted; the app chrome stays clay. */
@Immutable
data class ReadingTone(
    val isDark: Boolean,
    val sheet: Color,
    val ink: Color,
    val inkSoft: Color,
    val muted: Color,
    val border: Color,
)

/** Token accessors for the Stitch "Tactile Claymorphism" design system. */
object ClayTokens {
    /** App chrome palette (always the light lavender canvas). */
    fun chrome(highContrast: Boolean = false): ClayPalette {
        val base = ClayPalette()
        if (!highContrast) return base
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
            )
            ReaderTheme.SEPIA -> ReadingTone(
                isDark = false,
                sheet = Color(0xFFF5EDDB),
                ink = Color(0xFF261D14),
                inkSoft = Color(0xFF47392B),
                muted = Color(0xFF6E5C49),
                border = Color(0xFFDECBB3),
            )
            ReaderTheme.SAGE -> ReadingTone(
                isDark = false,
                sheet = Color(0xFFEEF3EE),
                ink = Color(0xFF1B2B21),
                inkSoft = Color(0xFF36433A),
                muted = Color(0xFF5A695D),
                border = Color(0xFFC8D6C9),
            )
            ReaderTheme.DARK -> ReadingTone(
                isDark = true,
                sheet = Color(0xFF221E2A),
                ink = Color(0xFFF4EFFC),
                inkSoft = Color(0xFFD9D2E6),
                muted = Color(0xFFB0A8BF),
                border = Color(0xFF4A4455),
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
    val base: Dp = 16.dp
    val md: Dp = 24.dp
    val lg: Dp = 32.dp
    val xl: Dp = 48.dp
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

/** One layer of a clay shadow stack. */
@Immutable
data class ClayShadowLayer(
    val dx: Dp,
    val dy: Dp,
    val blur: Dp,
    val color: Color,
)

/** Multi-layer clay shadow: outer (drop) layers plus inner (inset) layers. */
@Immutable
data class ClayShadowStack(
    val outer: List<ClayShadowLayer>,
    val inner: List<ClayShadowLayer>,
)

/** Clay shadow stacks with the literal values from the DESIGN.md. */
object ClayShadows {
    val card = ClayShadowStack(
        outer = listOf(
            ClayShadowLayer(16.dp, 16.dp, 32.dp, Color(0x38A096B4)),
            ClayShadowLayer((-10).dp, (-10).dp, 24.dp, Color(0xF2FFFFFF)),
        ),
        inner = listOf(
            ClayShadowLayer(6.dp, 6.dp, 12.dp, Color(0x0A8B5CF6)),
            ClayShadowLayer((-6).dp, (-6).dp, 12.dp, Color(0xFFFFFFFF)),
        ),
    )
    val button = ClayShadowStack(
        outer = listOf(
            ClayShadowLayer(10.dp, 10.dp, 20.dp, Color(0x477C3AED)),
            ClayShadowLayer((-6).dp, (-6).dp, 14.dp, Color(0xB3FFFFFF)),
        ),
        inner = listOf(
            ClayShadowLayer(3.dp, 3.dp, 6.dp, Color(0x66FFFFFF)),
            ClayShadowLayer((-3).dp, (-3).dp, 6.dp, Color(0x1F000000)),
        ),
    )
    val pressed = ClayShadowStack(
        outer = emptyList(),
        inner = listOf(
            ClayShadowLayer(8.dp, 8.dp, 16.dp, Color(0xFFD9D4E3)),
            ClayShadowLayer((-8).dp, (-8).dp, 16.dp, Color(0xFFFFFFFF)),
        ),
    )
    val floating = ClayShadowStack(
        outer = listOf(
            ClayShadowLayer(0.dp, 20.dp, 35.dp, Color(0x337C3AED)),
            ClayShadowLayer(0.dp, 10.dp, 15.dp, Color(0x0A000000)),
        ),
        inner = emptyList(),
    )
}

package dev.meridian.folio.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import dev.meridian.folio.core.UiAppearanceSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

@OptIn(ExperimentalTestApi::class)
class ClayThemeTest {

    @Test
    fun typeScaleFallbackUsesSystemFamiliesAndDesignMdStyles() {
        val fallback = ClayTypographyData.fromTypeScale(
            displayFamily = FontFamily.SansSerif,
            bodyFamily = FontFamily.Default,
        )
        assertEquals(FontFamily.SansSerif, fallback.displayFamily)
        assertEquals(FontFamily.Default, fallback.bodyFamily)
        // Titular scales take the display family, body/label scales the body family.
        assertEquals(FontFamily.SansSerif, fallback.headlineLarge.fontFamily)
        assertEquals(FontFamily.SansSerif, fallback.titleMd.fontFamily)
        assertEquals(FontFamily.Default, fallback.bodyMd.fontFamily)
        assertEquals(FontFamily.Default, fallback.labelSm.fontFamily)
        // Styles carry the exact DESIGN.md scale values.
        with(ClayTypeScale.headlineLarge) {
            assertEquals(fontSizeSp.sp, fallback.headlineLarge.fontSize)
            assertEquals(lineHeightSp.sp, fallback.headlineLarge.lineHeight)
            assertEquals(weight, fallback.headlineLarge.fontWeight)
            assertEquals(letterSpacingEm.em, fallback.headlineLarge.letterSpacing)
        }
        with(ClayTypeScale.bodyMd) {
            assertEquals(fontSizeSp.sp, fallback.bodyMd.fontSize)
            assertEquals(lineHeightSp.sp, fallback.bodyMd.lineHeight)
            assertEquals(weight, fallback.bodyMd.fontWeight)
            assertEquals(letterSpacingEm.em, fallback.bodyMd.letterSpacing)
        }
    }

    @Test
    fun clayThemeProvidesClayLocalsAndRendersHeadlineLarge() = runComposeUiTest {
        val palette = ClayTokens.chrome()
        var providedPalette: ClayPalette? = null
        var providedTypography: ClayTypographyData? = null
        var providedAppearance: UiAppearanceSettings? = null
        var providedSans: FontFamily? = null
        setContent {
            ClayTheme(palette = palette) {
                providedPalette = LocalClayPalette.current
                providedTypography = LocalClayTypography.current
                providedAppearance = LocalClayUiAppearance.current
                providedSans = LocalClayUiFontFamilies.current.sans
                Text(
                    "Titular de muestra",
                    style = LocalClayTypography.current.headlineLarge,
                    modifier = Modifier.testTag("headline_sample"),
                )
            }
        }
        waitForIdle()
        onNodeWithTag("headline_sample").assertExists()
        assertEquals(palette, providedPalette)
        assertEquals(UiAppearanceSettings(), providedAppearance)
        val typography = providedTypography!!
        // Bundled families, not the system fallbacks: Nunito Sans display, DM Sans body.
        assertNotEquals(FontFamily.SansSerif, typography.displayFamily)
        assertNotEquals(FontFamily.Default, typography.bodyFamily)
        assertEquals(typography.bodyFamily, providedSans)
        // The 12 styles carry the exact DESIGN.md scale values.
        assertEquals(32.sp, typography.headlineLarge.fontSize)
        assertEquals(40.sp, typography.headlineLarge.lineHeight)
        assertEquals(FontWeight.ExtraBold, typography.headlineLarge.fontWeight)
        assertEquals((-0.01f).em, typography.headlineLarge.letterSpacing)
        assertEquals(typography.displayFamily, typography.headlineLarge.fontFamily)
        assertEquals(typography.bodyFamily, typography.bodyMd.fontFamily)
    }

    @Test
    fun clayThemeBridgesClayTokensIntoMaterialColorScheme() = runComposeUiTest {
        val palette = ClayTokens.chrome()
        var scheme: ColorScheme? = null
        var m3Typography: Typography? = null
        var clayTypography: ClayTypographyData? = null
        setContent {
            ClayTheme(palette = palette) {
                scheme = MaterialTheme.colorScheme
                m3Typography = MaterialTheme.typography
                clayTypography = LocalClayTypography.current
                Text("m3", modifier = Modifier.testTag("m3_sample"))
            }
        }
        waitForIdle()
        onNodeWithTag("m3_sample").assertExists()
        val m3 = scheme!!
        assertEquals(palette.primary, m3.primary)
        assertEquals(palette.canvas, m3.background)
        assertEquals(palette.surface, m3.surface)
        assertEquals(palette.surfaceContainerLowest, m3.surfaceContainerLowest)
        assertEquals(palette.onSurface, m3.onSurface)
        assertEquals(palette.secondary, m3.secondary)
        assertEquals(palette.outlineVariant, m3.outlineVariant)
        // The M3 typography bridge carries the clay families (DM Sans body, Nunito titular).
        val clay = clayTypography!!
        assertEquals(clay.bodyFamily, m3Typography!!.bodyMedium.fontFamily)
        assertEquals(clay.displayFamily, m3Typography!!.headlineLarge.fontFamily)
    }
}

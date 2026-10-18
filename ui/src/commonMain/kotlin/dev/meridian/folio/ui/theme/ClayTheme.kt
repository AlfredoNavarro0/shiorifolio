package dev.meridian.folio.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import dev.meridian.folio.core.UiAppearanceSettings
import meridian_folio.ui.generated.resources.Res
import meridian_folio.ui.generated.resources.dm_sans_bold
import meridian_folio.ui.generated.resources.dm_sans_medium
import meridian_folio.ui.generated.resources.dm_sans_regular
import meridian_folio.ui.generated.resources.nunito_sans_black
import meridian_folio.ui.generated.resources.nunito_sans_bold
import meridian_folio.ui.generated.resources.nunito_sans_extrabold
import org.jetbrains.compose.resources.Font

/** Clay color palette; defaults to the chrome palette until [ClayTheme] provides one. */
val LocalClayPalette = staticCompositionLocalOf { ClayPalette() }

/**
 * Resolved clay typography; defaults to the system families with the styles built from
 * [ClayTypeScale] until [ClayTheme] provides the bundled Nunito Sans / DM Sans families.
 */
val LocalClayTypography = staticCompositionLocalOf {
    ClayTypographyData.fromTypeScale(displayFamily = FontFamily.SansSerif, bodyFamily = FontFamily.Default)
}

/** UI appearance settings (font family/size/weight, scale, high contrast). */
val LocalClayUiAppearance = staticCompositionLocalOf { UiAppearanceSettings() }

/**
 * Resolved clay typography: the 12 [TextStyle]s of the type scale plus the two bundled
 * families — Nunito Sans for the display/headline/title scales, DM Sans for body/label.
 */
@Immutable
data class ClayTypographyData(
    val displayLarge: TextStyle,
    val headlineLarge: TextStyle,
    val headlineLargeMobile: TextStyle,
    val headlineMd: TextStyle,
    val headlineSm: TextStyle,
    val titleMd: TextStyle,
    val bodyLg: TextStyle,
    val bodyMd: TextStyle,
    val bodySm: TextStyle,
    val labelLg: TextStyle,
    val labelMd: TextStyle,
    val labelSm: TextStyle,
    val displayFamily: FontFamily,
    val bodyFamily: FontFamily,
) {
    companion object {
        /**
         * Builds the 12 styles from [ClayTypeScale]: titular scales (display/headline/title)
         * use [displayFamily], body/label scales use [bodyFamily].
         */
        fun fromTypeScale(displayFamily: FontFamily, bodyFamily: FontFamily): ClayTypographyData {
            fun ClayTypeSpec.toTextStyle(family: FontFamily): TextStyle = TextStyle(
                fontFamily = family,
                fontSize = fontSizeSp.sp,
                lineHeight = lineHeightSp.sp,
                fontWeight = weight,
                letterSpacing = letterSpacingEm.em,
            )
            return ClayTypographyData(
                displayLarge = ClayTypeScale.displayLarge.toTextStyle(displayFamily),
                headlineLarge = ClayTypeScale.headlineLarge.toTextStyle(displayFamily),
                headlineLargeMobile = ClayTypeScale.headlineLargeMobile.toTextStyle(displayFamily),
                headlineMd = ClayTypeScale.headlineMd.toTextStyle(displayFamily),
                headlineSm = ClayTypeScale.headlineSm.toTextStyle(displayFamily),
                titleMd = ClayTypeScale.titleMd.toTextStyle(displayFamily),
                bodyLg = ClayTypeScale.bodyLg.toTextStyle(bodyFamily),
                bodyMd = ClayTypeScale.bodyMd.toTextStyle(bodyFamily),
                bodySm = ClayTypeScale.bodySm.toTextStyle(bodyFamily),
                labelLg = ClayTypeScale.labelLg.toTextStyle(bodyFamily),
                labelMd = ClayTypeScale.labelMd.toTextStyle(bodyFamily),
                labelSm = ClayTypeScale.labelSm.toTextStyle(bodyFamily),
                displayFamily = displayFamily,
                bodyFamily = bodyFamily,
            )
        }
    }
}

/**
 * Typography of the clay design system, with the bundled resources (Task 1): Nunito Sans
 * (Black/ExtraBold/Bold) for the titular scales and DM Sans (Regular/Medium/Bold) for
 * body/label.
 */
@Composable
fun rememberClayTypography(): ClayTypographyData {
    val displayFamily = FontFamily(
        Font(Res.font.nunito_sans_black, FontWeight.Black),
        Font(Res.font.nunito_sans_extrabold, FontWeight.ExtraBold),
        Font(Res.font.nunito_sans_bold, FontWeight.Bold),
    )
    val bodyFamily = FontFamily(
        Font(Res.font.dm_sans_regular, FontWeight.Normal),
        Font(Res.font.dm_sans_medium, FontWeight.Medium),
        Font(Res.font.dm_sans_bold, FontWeight.Bold),
    )
    return remember(displayFamily, bodyFamily) {
        ClayTypographyData.fromTypeScale(displayFamily = displayFamily, bodyFamily = bodyFamily)
    }
}

/**
 * Bridges the clay typography into the Material 3 [Typography]. The pinned Material 3
 * version has no `Typography(defaultFontFamily = …)` factory, so the clay styles — which
 * already carry the Nunito Sans / DM Sans families — are mapped onto the M3 roles.
 */
private fun clayMaterialTypography(typography: ClayTypographyData): Typography = Typography(
    displayLarge = typography.displayLarge,
    displayMedium = typography.headlineLarge,
    displaySmall = typography.headlineMd,
    headlineLarge = typography.headlineLarge,
    headlineMedium = typography.headlineMd,
    headlineSmall = typography.headlineSm,
    titleLarge = typography.titleMd,
    titleMedium = typography.titleMd,
    titleSmall = typography.titleMd,
    bodyLarge = typography.bodyLg,
    bodyMedium = typography.bodyMd,
    bodySmall = typography.bodySm,
    labelLarge = typography.labelLg,
    labelMedium = typography.labelMd,
    labelSmall = typography.labelSm,
)

/**
 * Theme provider of the clay design system. Provides the clay composition locals
 * (palette, typography, UI appearance and UI font families — `SANS_SERIF` resolves to the
 * bundled DM Sans) and bridges into Material 3 so standard controls (menus, sliders,
 * dialogs) pick up the clay tokens.
 */
@Composable
fun ClayTheme(
    palette: ClayPalette = ClayTokens.chrome(),
    uiAppearance: UiAppearanceSettings = UiAppearanceSettings(),
    content: @Composable () -> Unit,
) {
    val typography = rememberClayTypography()
    val fontFamilies = remember(typography) {
        ClayUiFontFamilies(
            sans = typography.bodyFamily,
            serif = FontFamily.Serif,
            mono = FontFamily.Monospace,
        )
    }
    CompositionLocalProvider(
        LocalClayPalette provides palette,
        LocalClayTypography provides typography,
        LocalClayUiAppearance provides uiAppearance,
        LocalClayUiFontFamilies provides fontFamilies,
    ) {
        MaterialTheme(
            colorScheme = palette.toMaterialColorScheme(),
            typography = clayMaterialTypography(typography),
            content = content,
        )
    }
}

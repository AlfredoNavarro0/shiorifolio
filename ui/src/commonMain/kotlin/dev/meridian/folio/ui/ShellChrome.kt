package dev.meridian.folio.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.meridian.folio.core.ReaderTheme
import dev.meridian.folio.ui.theme.ClayCard
import dev.meridian.folio.ui.theme.ClayHeaderAction
import dev.meridian.folio.ui.theme.ClayPalette
import dev.meridian.folio.ui.theme.ClayRadius
import dev.meridian.folio.ui.theme.ClaySpacing
import dev.meridian.folio.ui.theme.LocalClayPalette
import dev.meridian.folio.ui.theme.LocalClayTypography
import dev.meridian.folio.ui.theme.clayFocusableClickable

/** The app emblem is shared with the desktop window icon, without theme tinting. */
@Composable
fun ClayLogoMark(size: Dp = 34.dp, modifier: Modifier = Modifier) {
    Image(
        painter = shioriFolioIconPainter(),
        contentDescription = "Logo de ShioriFolio",
        modifier = modifier.size(size).testTag("app_logo"),
    )
}

/**
 * Clay top navigation: a floating [ClayCard] (multi-layer clay card shadow over a frosted
 * `cardSurface`) holding the logo mark, the wordmark, the four section tabs
 * (`section_tab_<NAME>`) and the global theme toggle (`theme_toggle_button`).
 *
 * The card is explicitly sized (`fillMaxWidth()` + a minimum height): [ClayCard] mirrors its
 * shadow and frosted layers with `matchParentSize` children, so it cannot size itself. The
 * tabs row takes the remaining width and scrolls horizontally when the four labels no longer
 * fit (narrow windows or a raised UI scale). Container tag: `clay_top_nav`.
 */
@Composable
fun ClayTopNav(
    activeSection: MeridianSection,
    onSelectSection: (MeridianSection) -> Unit,
    theme: ReaderTheme,
    onToggleTheme: () -> Unit,
    modifier: Modifier = Modifier,
) {
    MinimalTopNavigation(
        activeSection, onSelectSection,
        themeLabel = "Tema ${theme.stitchLabel}",
        themeGlyph = if (theme == ReaderTheme.DARK) "☾" else "☼",
        onToggleTheme = onToggleTheme,
        modifier = modifier,
    )
}
/** Card height of [ClayBottomNav]: one [ClayChromeAction] pill row plus its own padding. */
val ClayBottomNavCardHeight: Dp = 60.dp

/**
 * Vertical space [ClayBottomNav] occupies on screen: its card ([ClayBottomNavCardHeight])
 * plus the padding the component adds above and below it. Hosts that reserve room for the
 * compact chrome (the mobile Kindle reader's reading canvases) use exactly this value, so the
 * last line of a page never sits under the bar. `ClayShellTest` pins the rendered bounds.
 */
val ClayBottomNavHeight: Dp = ClayBottomNavCardHeight + ClaySpacing.xs * 2

/**
 * Compact clay bottom navigation for the mobile / Kindle layout. Shares the
 * `section_tab_<NAME>` tags with [ClayTopNav]; both are never rendered at the same time.
 *
 * The tab row fills the bar when the four tabs fit and scrolls horizontally when they do
 * not (narrow compact widths or a raised UI scale), so every section stays reachable.
 * Container tag: `clay_bottom_nav`; scrollable tab row: `clay_bottom_nav_tabs`.
 */
@Composable
fun ClayBottomNav(
    activeSection: MeridianSection,
    onSelectSection: (MeridianSection) -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalClayPalette.current
    Box(modifier.fillMaxWidth().padding(horizontal = ClaySpacing.gutter, vertical = ClaySpacing.xs)) {
        ClayCard(
            Modifier
                .fillMaxWidth()
                .heightIn(min = ClayBottomNavCardHeight)
                .testTag("clay_bottom_nav"),
        ) {
            BoxWithConstraints(
                modifier = Modifier.fillMaxWidth().padding(ClaySpacing.xs),
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    modifier = Modifier
                        // Widen to the bar so the tabs spread evenly while they fit; beyond
                        // that the row keeps its intrinsic width and scrolls.
                        .widthIn(min = maxWidth)
                        .horizontalScroll(rememberScrollState())
                        .testTag("clay_bottom_nav_tabs"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    MainNavigationSections.forEach { section ->
                        ClayHeaderAction(
                            section.stitchTabTitle,
                            palette,
                            "section_tab_${section.name}",
                            section == activeSection,
                            quiet = true,
                        ) {
                            onSelectSection(section)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Thin clay footer of the desktop shell: brand line on the left, library tally on the
 * right. Hidden together with the rest of the chrome in Zen Mode / fullscreen.
 */
@Composable
fun ClayFooter(bookCount: Int, modifier: Modifier = Modifier) {
    val palette = LocalClayPalette.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(palette.surfaceContainerLow.copy(alpha = 0.6f))
            .heightIn(min = 40.dp)
            .padding(horizontal = ClaySpacing.gutterDesktop, vertical = ClaySpacing.sm)
            .testTag("clay_footer"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = "ShioriFolio · Lector y editor de PDF & EPUB",
            color = palette.onSurfaceVariant,
            style = LocalClayTypography.current.bodySm,
            maxLines = 1,
        )
        Text(
            text = "$bookCount títulos en tu biblioteca",
            color = palette.textMuted,
            style = LocalClayTypography.current.labelSm,
            maxLines = 1,
        )
    }
}

/**
 * Clay bookmark ribbon (Shiori 栞): a rounded ribbon with a swallowtail tip in [tint],
 * a soft clay drop shadow and a stitched emblem. Replaces the retired
 * `SwallowtailBookmarkRibbon`.
 */
@Composable
fun ClayBookmarkRibbon(
    isActive: Boolean,
    tint: Color,
    width: Dp,
    height: Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalClayPalette.current
    val ribbonColor = if (isActive) tint else tint.copy(alpha = 0.5f)
    Canvas(
        modifier = modifier
            .size(width = width, height = height)
            .clayFocusableClickable(palette, shape = RoundedCornerShape(ClayRadius.sm), onClick = onClick)
            .testTag("clay_bookmark_ribbon")
            .semantics {
                contentDescription = if (isActive) "Quitar marcapáginas" else "Marcar esta página"
            },
    ) {
        val w = size.width
        val h = size.height
        val bodyBottom = h * 0.82f
        val bodyCorner = CornerRadius(w * 0.3f, w * 0.3f)
        // Soft clay drop shadow behind the ribbon.
        drawRoundRect(
            color = palette.spineShadow.copy(alpha = 0.16f),
            topLeft = Offset(w * 0.08f, h * 0.04f),
            size = Size(w, bodyBottom),
            cornerRadius = bodyCorner,
        )
        // Swallowtail tip.
        val tip = Path().apply {
            moveTo(0f, bodyBottom - h * 0.02f)
            lineTo(0f, h)
            lineTo(w * 0.5f, h * 0.84f)
            lineTo(w, h)
            lineTo(w, bodyBottom - h * 0.02f)
            close()
        }
        drawPath(tip, color = ribbonColor)
        // Rounded body.
        drawRoundRect(
            color = ribbonColor,
            topLeft = Offset.Zero,
            size = Size(w, bodyBottom),
            cornerRadius = bodyCorner,
        )
        // Stitched inner line and emblem.
        val inset = w * 0.16f
        val stitch = Path().apply {
            moveTo(inset, h * 0.16f)
            lineTo(inset, bodyBottom - h * 0.1f)
            lineTo(w * 0.5f, bodyBottom - h * 0.02f)
            lineTo(w - inset, bodyBottom - h * 0.1f)
            lineTo(w - inset, h * 0.16f)
        }
        drawPath(stitch, color = Color.White.copy(alpha = 0.32f), style = Stroke(width = 1.1f))
        drawCircle(
            color = Color.White.copy(alpha = 0.88f),
            radius = w * 0.11f,
            center = Offset(w * 0.5f, h * 0.34f),
        )
    }
}

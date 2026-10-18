package dev.meridian.folio.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.meridian.folio.core.BookDocument
import dev.meridian.folio.core.DocumentFormat
import dev.meridian.folio.core.NavigationHistoryEntry
import dev.meridian.folio.core.PageFitMode
import dev.meridian.folio.core.PdfPageColorMode
import dev.meridian.folio.core.ReaderTheme
import dev.meridian.folio.ui.theme.ClayCard
import dev.meridian.folio.ui.theme.ClayChromeAction
import dev.meridian.folio.ui.theme.ClayPalette
import dev.meridian.folio.ui.theme.ClayRadius
import dev.meridian.folio.ui.theme.ClaySpacing
import dev.meridian.folio.ui.theme.LocalClayPalette
import dev.meridian.folio.ui.theme.LocalClayTypography
import dev.meridian.folio.ui.theme.clayFocusableClickable

/** Brand wordmark of the clay shell; the pre-migration tests pin this exact text. */
private const val CLAY_WORDMARK = "shiorifolio"

/**
 * Clay logo mark: a rounded `primaryContainer` tile with a simple open book and a
 * swallowtail bookmark ribbon drawn in `primary`. Replaces the retired
 * `MeridianCompassMark`; the tile is sized by [size] so call sites keep their own scale.
 */
@Composable
fun ClayLogoMark(size: Dp = 34.dp, modifier: Modifier = Modifier) {
    val palette = LocalClayPalette.current
    Canvas(modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        drawRoundRect(
            color = palette.primaryContainer,
            cornerRadius = CornerRadius(w * 0.3f, w * 0.3f),
        )
        val spineX = w * 0.5f
        val pageTop = h * 0.31f
        val pageBottom = h * 0.62f
        val pageCorner = CornerRadius(w * 0.06f, w * 0.06f)
        // Left and right leaves of the open book.
        drawRoundRect(
            color = palette.primary,
            topLeft = Offset(w * 0.17f, pageTop),
            size = Size(w * 0.30f, pageBottom - pageTop),
            cornerRadius = pageCorner,
        )
        drawRoundRect(
            color = palette.primary,
            topLeft = Offset(w * 0.53f, pageTop),
            size = Size(w * 0.30f, pageBottom - pageTop),
            cornerRadius = pageCorner,
        )
        // Shiori (栞) swallowtail ribbon hanging from the spine.
        val ribbon = Path().apply {
            moveTo(spineX - w * 0.07f, pageTop - h * 0.03f)
            lineTo(spineX + w * 0.07f, pageTop - h * 0.03f)
            lineTo(spineX + w * 0.07f, h * 0.84f)
            lineTo(spineX, h * 0.74f)
            lineTo(spineX - w * 0.07f, h * 0.84f)
            close()
        }
        drawPath(ribbon, color = palette.primary)
    }
}

/**
 * Clay top navigation: a floating [ClayCard] (multi-layer clay card shadow over a frosted
 * `cardSurface`) holding the logo mark, the wordmark, the four section tabs
 * (`section_tab_<NAME>`) and the theme toggle (`theme_toggle_button`). Only the light chrome
 * palette is used: the shell never follows the reading sheet tone.
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
    val palette = LocalClayPalette.current
    Box(modifier.fillMaxWidth().padding(horizontal = ClaySpacing.gutterDesktop, vertical = ClaySpacing.sm)) {
        ClayCard(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .testTag("clay_top_nav"),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = ClaySpacing.md, vertical = ClaySpacing.xs),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(ClaySpacing.md),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(ClaySpacing.xs),
                ) {
                    ClayLogoMark(size = 34.dp)
                    Text(
                        text = CLAY_WORDMARK,
                        color = palette.onSurface,
                        style = LocalClayTypography.current.headlineSm,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = (-0.5).sp,
                        maxLines = 1,
                    )
                }
                Row(
                    modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(ClaySpacing.xs),
                ) {
                    MeridianSection.entries.forEach { section ->
                        ClayChromeAction(
                            section.stitchTabTitle,
                            palette,
                            "section_tab_${section.name}",
                            section == activeSection,
                        ) {
                            onSelectSection(section)
                        }
                    }
                }
                ClayChromeAction(
                    if (theme == ReaderTheme.DARK) "☾  Oscuro" else "☀  Claro",
                    palette,
                    "theme_toggle_button",
                    onClick = onToggleTheme,
                )
            }
        }
    }
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
                    MeridianSection.entries.forEach { section ->
                        ClayChromeAction(
                            section.stitchTabTitle,
                            palette,
                            "section_tab_${section.name}",
                            section == activeSection,
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

/**
 * Clay reader toolbar: history controls, book title/author, chapter jump, fit & zoom strip
 * and the book tools menu. Mirrors the retired `ModernReaderToolbar` surface (same
 * parameters, tags and menu entries), restyled with clay primitives.
 */
@Composable
fun ClayReaderToolbar(
    palette: ClayPalette,
    book: BookDocument,
    back: NavigationHistoryEntry?,
    forward: NavigationHistoryEntry?,
    showToc: Boolean,
    phraseCount: Int,
    fitMode: PageFitMode,
    zoomScale: Float,
    pdfColorMode: PdfPageColorMode,
    onBack: () -> Unit,
    onForward: () -> Unit,
    onToc: () -> Unit,
    onNotebook: () -> Unit,
    onFit: (PageFitMode) -> Unit,
    onZoom: (Float) -> Unit,
    onPdfColor: (PdfPageColorMode) -> Unit,
) {
    var showTools by remember { mutableStateOf(false) }
    val navShape = RoundedCornerShape(ClayRadius.base)
    Row(
        modifier = Modifier
            .widthIn(max = 1440.dp)
            .fillMaxWidth()
            .padding(bottom = ClaySpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ClaySpacing.xs),
    ) {
        Row(
            modifier = Modifier
                .clip(navShape)
                .background(palette.surfaceContainerHigh)
                .padding(2.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            ClayChromeAction(
                "←",
                palette,
                "nav_history_back_btn",
                enabled = back != null,
                description = "Volver en el historial",
                onClick = onBack,
            )
            ClayChromeAction(
                "→",
                palette,
                "nav_history_forward_btn",
                enabled = forward != null,
                description = "Avanzar en el historial",
                onClick = onForward,
            )
        }
        Column(Modifier.weight(1f).padding(start = ClaySpacing.xs)) {
            Text(
                book.title,
                color = palette.onSurface,
                style = LocalClayTypography.current.titleMd,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                book.author,
                color = palette.onSurfaceVariant,
                style = LocalClayTypography.current.bodySm,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (book.tableOfContents.size > 1) {
            ClayChromeAction("☰  Capítulos", palette, "toc_toggle_btn", showToc, onClick = onToc)
        }
        ZoomAndFitControlStrip(palette, fitMode, zoomScale, onFit, onZoom)
        Box {
            ClayChromeAction(
                "⋯",
                palette,
                "reader_tools_menu",
                description = "Herramientas del libro",
            ) { showTools = true }
            DropdownMenu(showTools, { showTools = false }) {
                DropdownMenuItem(
                    text = { Text("$phraseCount anotaciones") },
                    onClick = { showTools = false; onNotebook() },
                    modifier = Modifier.testTag("reader_notebook"),
                )
                if (book.format == DocumentFormat.PDF) {
                    HorizontalDivider()
                    PdfPageColorMode.entries.forEach { mode ->
                        DropdownMenuItem(
                            text = { Text((if (mode == pdfColorMode) "✓  " else "") + mode.shortLabel) },
                            onClick = { onPdfColor(mode); showTools = false },
                            modifier = Modifier.testTag("reader_pdf_color_${mode.name}"),
                        )
                    }
                }
            }
        }
    }
}

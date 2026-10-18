package dev.meridian.folio.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.meridian.folio.core.BlockStyle
import dev.meridian.folio.core.BookDocument
import dev.meridian.folio.core.BookPage
import dev.meridian.folio.core.BookTypographySettings
import dev.meridian.folio.core.DocumentFormat
import dev.meridian.folio.core.HighlightTone
import dev.meridian.folio.core.PageFitMode
import dev.meridian.folio.core.PdfPageColorMode
import dev.meridian.folio.core.PdfReflowEngine
import dev.meridian.folio.core.ReflowedBlockItem
import dev.meridian.folio.core.SavedPhrase
import kotlin.math.roundToInt

/**
 * Computes the effective maximum width of the reading container according to [PageFitMode] and [zoomScale].
 */
internal fun computeScaledMaxSpreadWidth(
    baseMaxWidth: Dp,
    fitMode: PageFitMode,
    zoomScale: Float,
): Dp = when (fitMode) {
    PageFitMode.FIT_PAGE -> baseMaxWidth
    PageFitMode.FIT_WIDTH -> 1920.dp
    PageFitMode.MANUAL_ZOOM -> (baseMaxWidth.value * zoomScale.coerceIn(0.50f, 2.50f)).dp
}

/**
 * Compact Fit-to-Page, Fit-to-Width, and Manual Zoom control strip (Iteración 4 · Punto 4).
 */
@Composable
internal fun ZoomAndFitControlStrip(
    palette: MeridianPalette,
    fitMode: PageFitMode,
    zoomScale: Float,
    onSelectFitMode: (PageFitMode) -> Unit,
    onChangeZoomScale: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(palette.pillBg)
            .border(1.dp, palette.border, RoundedCornerShape(8.dp))
            .padding(horizontal = 6.dp, vertical = 3.dp)
            .testTag("zoom_fit_control_strip"),
    ) {
        val fitPageSelected = fitMode == PageFitMode.FIT_PAGE
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(5.dp))
                .background(if (fitPageSelected) palette.accentGreen else androidx.compose.ui.graphics.Color.Transparent)
                .shioriFocusableClickable(palette, RoundedCornerShape(5.dp)) {
                    onSelectFitMode(PageFitMode.FIT_PAGE)
                }
                .padding(horizontal = 6.dp, vertical = 2.dp)
                .testTag("fit_mode_FIT_PAGE"),
        ) {
            Text(
                text = "Ajustar Pág.",
                color = if (fitPageSelected) palette.onAccent else palette.inkSoft,
                fontSize = 10.sp,
                fontWeight = if (fitPageSelected) FontWeight.SemiBold else FontWeight.Medium,
            )
        }

        val fitWidthSelected = fitMode == PageFitMode.FIT_WIDTH
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(5.dp))
                .background(if (fitWidthSelected) palette.accentGreen else androidx.compose.ui.graphics.Color.Transparent)
                .shioriFocusableClickable(palette, RoundedCornerShape(5.dp)) {
                    onSelectFitMode(PageFitMode.FIT_WIDTH)
                }
                .padding(horizontal = 6.dp, vertical = 2.dp)
                .testTag("fit_mode_FIT_WIDTH"),
        ) {
            Text(
                text = "Ajustar Ancho",
                color = if (fitWidthSelected) palette.onAccent else palette.inkSoft,
                fontSize = 10.sp,
                fontWeight = if (fitWidthSelected) FontWeight.SemiBold else FontWeight.Medium,
            )
        }

        Text("|", color = palette.border, fontSize = 10.sp)

        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(5.dp))
                .shioriFocusableClickable(palette, RoundedCornerShape(5.dp)) {
                    onChangeZoomScale((zoomScale - 0.10f).coerceIn(0.50f, 2.50f))
                }
                .padding(horizontal = 5.dp, vertical = 2.dp)
                .testTag("zoom_decrease_btn"),
        ) {
            Text("−", color = palette.ink, fontWeight = FontWeight.Bold, fontSize = 11.sp)
        }

        Text(
            text = "${(zoomScale * 100).roundToInt()}%",
            color = if (fitMode == PageFitMode.MANUAL_ZOOM) palette.accentGreen else palette.ink,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.SemiBold,
            fontSize = 10.sp,
            modifier = Modifier
                .clickable { onSelectFitMode(PageFitMode.FIT_PAGE) }
                .padding(horizontal = 3.dp)
                .testTag("zoom_level_label"),
        )

        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(5.dp))
                .shioriFocusableClickable(palette, RoundedCornerShape(5.dp)) {
                    onChangeZoomScale((zoomScale + 0.10f).coerceIn(0.50f, 2.50f))
                }
                .padding(horizontal = 5.dp, vertical = 2.dp)
                .testTag("zoom_increase_btn"),
        ) {
            Text("+", color = palette.ink, fontWeight = FontWeight.Bold, fontSize = 11.sp)
        }
    }
}

/**
 * Mode 3: VERTICAL_PAGINATED ("Desplazamiento vertical por páginas" — Punto 4).
 * Displays each page as a distinct paper sheet separated vertically, synchronized with [book.currentPage].
 */
@Composable
internal fun VerticalPaginatedReaderCanvas(
    palette: MeridianPalette,
    pdfPageColorMode: PdfPageColorMode,
    book: BookDocument,
    savedPhrases: List<SavedPhrase>,
    fontSizePt: Float,
    lineSpacingMultiplier: Float,
    bookTypography: BookTypographySettings,
    zoomScale: Float,
    onPageChange: (Int) -> Unit,
    onQuickSavePhrase: (String, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val page = book.pages.firstOrNull { it.pageNumber == book.currentPage } ?: book.pages.first()
    val effectiveFontSize = (fontSizePt * zoomScale.coerceIn(0.70f, 1.80f)).coerceIn(12f, 34f)
    val pagination = LocalReaderPagination.current
    Surface(color = palette.pageSurface, shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, palette.border),
        modifier = modifier.fillMaxSize().testTag("vertical_paginated_canvas")
            .readerPageGestures(book.id) { pagination.turn(book, 1, it, onPageChange) }) {
        StitchBookSpreadLeaf(palette, pdfPageColorMode, book, page, savedPhrases, effectiveFontSize,
            effectiveFontSize * lineSpacingMultiplier, bookTypography, isLeftLeaf = false,
            onQuickSavePhrase = onQuickSavePhrase, modifier = Modifier.fillMaxSize().testTag("vertical_page_sheet_${page.pageNumber}"))
    }
}
/**
 * Mode 4: VERTICAL_CONTINUOUS ("Desplazamiento vertical continuo" — Puntos 4 y 5).
 * Presents a continuous reading stream across all pages, joining paragraphs split across page breaks
 * while preserving semantic scene breaks (* * *) and chapter headings.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun VerticalContinuousReaderCanvas(
    palette: MeridianPalette,
    pdfPageColorMode: PdfPageColorMode,
    book: BookDocument,
    savedPhrases: List<SavedPhrase>,
    fontSizePt: Float,
    lineSpacingMultiplier: Float,
    bookTypography: BookTypographySettings,
    zoomScale: Float,
    onPageChange: (Int) -> Unit,
    onQuickSavePhrase: (String, String) -> Unit,
    onReturnToOriginalView: () -> Unit,
    onRunOcrOnPage: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val reconstruction = remember(book.id, book.pages, bookTypography.normalizeExcessiveSpaces) {
        PdfReflowEngine.buildContinuousFlow(
            book = book,
            normalizeSpaces = bookTypography.normalizeExcessiveSpaces,
        )
    }

    val listState = rememberLazyListState()
    val effectiveFontSize = (fontSizePt * zoomScale.coerceIn(0.70f, 1.80f)).coerceIn(12f, 34f)
    val effectiveLineHeight = effectiveFontSize * lineSpacingMultiplier
    val contentFontFamily = bookTypography.fontFamily.toComposeFontFamily()
    val contentTextAlign = bookTypography.textAlignment.toComposeTextAlign()
    val horizPad = bookTypography.horizontalMarginDp.coerceIn(16f, 64f).dp
    val paraSpacing = bookTypography.paragraphSpacingDp.coerceIn(4f, 32f).dp

    Surface(
        color = palette.pageSurface,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, palette.border),
        modifier = modifier
            .fillMaxSize()
            .shadow(elevation = 5.dp, shape = RoundedCornerShape(14.dp))
            .testTag("vertical_continuous_canvas"),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = horizPad, vertical = 16.dp),
        ) {
            ReflowStatusAndReliabilityBanner(
                palette = palette,
                book = book,
                currentPage = book.currentPage,
                isReliable = reconstruction.isReliable,
                reliabilityWarning = reconstruction.reliabilityWarning,
                scannedPageNumbers = reconstruction.scannedPageNumbers,
                mergedPageBreaks = reconstruction.mergedPageBreakCount,
                dehyphenatedWords = reconstruction.dehyphenatedWordCount,
                onReturnToOriginalView = onReturnToOriginalView,
                onRunOcrOnPage = onRunOcrOnPage,
            )

            SelectionContainer(modifier = Modifier.weight(1f).fillMaxWidth()) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(paraSpacing),
                    contentPadding = PaddingValues(vertical = 8.dp),
                ) {
                    items(reconstruction.items, key = { "${book.id}_cont_${it.id}_${it.sourcePageNumber}" }) { item ->
                        ReflowBlockItemView(
                            palette = palette,
                            pdfPageColorMode = pdfPageColorMode,
                            book = book,
                            item = item,
                            savedPhrases = savedPhrases,
                            contentFontFamily = contentFontFamily,
                            contentTextAlign = contentTextAlign,
                            fontSizeSp = effectiveFontSize,
                            lineHeightSp = effectiveLineHeight,
                            showMarginalPageBadge = true,
                            onSelectSourcePage = { onPageChange(item.sourcePageNumber) },
                            onQuickSavePhrase = onQuickSavePhrase,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Mode 5: REFLOW_TEXT ("Texto adaptable o reflujo" — Puntos 4 y 5).
 * Reconstructs continuous reading order for the active page (joining cross-page split sentences,
 * de-hyphenating line breaks, stripping headers/footers, and offering OCR or instant return to Original View).
 */
@Composable
internal fun AdaptiveReflowReaderCanvas(
    palette: MeridianPalette,
    pdfPageColorMode: PdfPageColorMode,
    book: BookDocument,
    page: BookPage,
    savedPhrases: List<SavedPhrase>,
    fontSizePt: Float,
    lineSpacingMultiplier: Float,
    bookTypography: BookTypographySettings,
    zoomScale: Float,
    onQuickSavePhrase: (String, String) -> Unit,
    onReturnToOriginalView: () -> Unit,
    onRunOcrOnPage: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val reconstruction = remember(book.id, page.pageNumber, page.blocks, bookTypography.normalizeExcessiveSpaces) {
        PdfReflowEngine.buildReflowForPage(
            book = book,
            page = page,
            normalizeSpaces = bookTypography.normalizeExcessiveSpaces,
        )
    }

    val effectiveFontSize = (fontSizePt * zoomScale.coerceIn(0.70f, 1.80f)).coerceIn(12f, 34f)
    val effectiveLineHeight = effectiveFontSize * lineSpacingMultiplier
    val contentFontFamily = bookTypography.fontFamily.toComposeFontFamily()
    val contentTextAlign = bookTypography.textAlignment.toComposeTextAlign()
    val horizPad = bookTypography.horizontalMarginDp.coerceIn(16f, 64f).dp
    val vertPad = bookTypography.verticalMarginDp.coerceIn(8f, 44f).dp
    val paraSpacing = bookTypography.paragraphSpacingDp.coerceIn(4f, 32f).dp

    Surface(
        color = palette.pageSurface,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, palette.border),
        modifier = modifier
            .fillMaxSize()
            .shadow(elevation = 5.dp, shape = RoundedCornerShape(14.dp))
            .testTag("adaptive_reflow_canvas"),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = horizPad, end = horizPad, top = vertPad, bottom = 14.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            // Top Header reserving right space for SwallowtailBookmarkRibbon
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(end = 44.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "${page.chapterTitle.uppercase()} · MODO TEXTO ADAPTABLE",
                        color = palette.accentGreen,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 1.4.sp,
                        maxLines = 1,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "FOLIO ${page.pageNumber}",
                        color = palette.muted,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 10.sp,
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                HorizontalDivider(color = palette.border.copy(alpha = 0.7f), thickness = 1.dp)
            }

            // Reliability & OCR Status Banner
            ReflowStatusAndReliabilityBanner(
                palette = palette,
                book = book,
                currentPage = page.pageNumber,
                isReliable = reconstruction.isReliable,
                reliabilityWarning = reconstruction.reliabilityWarning,
                scannedPageNumbers = reconstruction.scannedPageNumbers,
                mergedPageBreaks = reconstruction.mergedPageBreakCount,
                dehyphenatedWords = reconstruction.dehyphenatedWordCount,
                onReturnToOriginalView = onReturnToOriginalView,
                onRunOcrOnPage = onRunOcrOnPage,
            )

            if (reconstruction.items.isEmpty()) {
                Column(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Esta página no contiene texto extraíble. Puedes reconocer el texto o volver al documento original.", color = palette.inkSoft)
                    ModernChromeAction("Reconocer texto", palette, "pdf_ocr_empty_page_btn") { onRunOcrOnPage(page.pageNumber) }
                    ModernChromeAction("Volver a Vista Original", palette, "reflow_empty_original_btn", onClick = onReturnToOriginalView)
                }
            } else {
                val reflowPage = remember(page, reconstruction.items) {
                    page.copy(blocks = reconstruction.items.map { dev.meridian.folio.core.ContentBlock(it.id, it.style, it.text, it.imagePath) })
                }
                PagedBookBody(palette, pdfPageColorMode, book, reflowPage,
                    savedPhrases.filter { it.pageNumber == page.pageNumber }, effectiveFontSize, effectiveLineHeight,
                    bookTypography, onQuickSavePhrase, Modifier.weight(1f).fillMaxWidth().padding(vertical = 8.dp))
            }
            // Bottom Footer
            Column(modifier = Modifier.fillMaxWidth()) {
                HorizontalDivider(color = palette.border.copy(alpha = 0.65f), thickness = 1.dp)
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Texto Adaptable • Archivo original intacto (${page.wordCount} palabras)",
                        color = palette.muted,
                        fontStyle = FontStyle.Italic,
                        fontSize = 10.5.sp,
                    )
                    Text(
                        text = "${page.pageNumber} / ${book.totalPages}",
                        color = palette.ink,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Medium,
                        fontSize = 11.5.sp,
                    )
                }
            }
        }
    }
}

@Composable
internal fun ReflowStatusAndReliabilityBanner(
    palette: MeridianPalette,
    book: BookDocument,
    currentPage: Int,
    isReliable: Boolean,
    reliabilityWarning: String?,
    scannedPageNumbers: List<Int>,
    mergedPageBreaks: Int,
    dehyphenatedWords: Int,
    onReturnToOriginalView: () -> Unit,
    onRunOcrOnPage: (Int) -> Unit,
) {
    if (book.format != DocumentFormat.PDF && reliabilityWarning == null && mergedPageBreaks == 0) return

    Surface(
        color = if (!isReliable) palette.warningAmber.copy(alpha = 0.14f) else palette.elevatedSurface,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, if (!isReliable) palette.warningAmber else palette.border),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp, bottom = 4.dp)
            .testTag("reflow_status_banner"),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = reliabilityWarning
                        ?: "Flujo continuo reconstruido (saltos de página unidos: $mergedPageBreaks · guiones unidos: $dehyphenatedWords · archivo original preservado).",
                    color = palette.ink,
                    fontSize = 11.5.sp,
                    fontWeight = if (!isReliable) FontWeight.SemiBold else FontWeight.Normal,
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (book.format == DocumentFormat.PDF) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(palette.pillBg)
                            .border(1.dp, palette.border, RoundedCornerShape(6.dp))
                            .shioriFocusableClickable(palette, RoundedCornerShape(6.dp)) {
                                onRunOcrOnPage(currentPage)
                            }
                            .padding(horizontal = 9.dp, vertical = 4.dp)
                            .testTag("pdf_ocr_action_btn"),
                    ) {
                        Text(
                            text = if (scannedPageNumbers.isNotEmpty()) "🔍 OCR Pág. $currentPage" else "OCR",
                            color = palette.accentGreen,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(palette.accentGreen)
                            .shioriFocusableClickable(palette, RoundedCornerShape(6.dp)) {
                                onReturnToOriginalView()
                            }
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                            .testTag("reflow_fallback_original_btn"),
                    ) {
                        Text(
                            text = "Volver a Vista Original",
                            color = palette.onAccent,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReflowBlockItemView(
    palette: MeridianPalette,
    pdfPageColorMode: PdfPageColorMode,
    book: BookDocument,
    item: ReflowedBlockItem,
    savedPhrases: List<SavedPhrase>,
    contentFontFamily: FontFamily,
    contentTextAlign: TextAlign,
    fontSizeSp: Float,
    lineHeightSp: Float,
    showMarginalPageBadge: Boolean,
    onSelectSourcePage: () -> Unit,
    onQuickSavePhrase: (String, String) -> Unit,
) {
    val pagePhrases = remember(savedPhrases, item.sourcePageNumber) {
        savedPhrases.filter { it.pageNumber == item.sourcePageNumber }
    }

    if (item.isSceneBreak) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(modifier = Modifier.width(36.dp).height(1.dp).background(palette.border))
            Text(
                text = "  ✦  ✦  ✦  ",
                color = palette.warningAmber,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
            )
            Box(modifier = Modifier.width(36.dp).height(1.dp).background(palette.border))
        }
        return
    }

    if (item.style == BlockStyle.IMAGE && !item.imagePath.isNullOrBlank()) {
        val bmp = rememberLoadedImageBitmap(
            path = item.imagePath,
            bookFilePath = book.filePath,
            bookId = book.id,
            pageNumber = item.sourcePageNumber,
        )
        if (bmp != null) {
            val pdfFilter = pdfPageImageColorFilter(
                palette = palette,
                mode = pdfPageColorMode,
                isPdfDocument = book.format == DocumentFormat.PDF,
                isFullPageArtwork = true,
            )
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Image(
                    bitmap = bmp,
                    contentDescription = item.text.ifBlank { "Folio ${item.sourcePageNumber}" },
                    contentScale = ContentScale.Fit,
                    colorFilter = pdfFilter,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 260.dp, max = 680.dp)
                        .clip(RoundedCornerShape(8.dp)),
                )
            }
        }
        return
    }

    val annotated = remember(item.text, pagePhrases, palette.isDark) {
        buildHighlightedAnnotatedString(item.text, pagePhrases, palette)
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        when (item.style) {
            BlockStyle.HEADING -> {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp, bottom = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "CAPÍTULO · FOLIO ${item.sourcePageNumber}",
                            color = palette.warningAmber,
                            fontSize = 9.5.sp,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 1.8.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = annotated,
                            color = palette.ink,
                            fontFamily = contentFontFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = (fontSizeSp + 4f).sp,
                            lineHeight = (lineHeightSp + 4f).sp,
                        )
                    }
                    if (showMarginalPageBadge) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(5.dp))
                                .background(palette.pillBg)
                                .border(1.dp, palette.border, RoundedCornerShape(5.dp))
                                .clickable { onSelectSourcePage() }
                                .padding(horizontal = 7.dp, vertical = 2.dp),
                        ) {
                            Text(
                                text = "p.${item.sourcePageNumber}",
                                color = palette.muted,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                            )
                        }
                    }
                }
            }

            BlockStyle.QUOTE -> {
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Box(modifier = Modifier.width(2.5.dp).height(44.dp).background(palette.warningAmber))
                    Spacer(modifier = Modifier.width(14.dp))
                    Text(
                        text = annotated,
                        color = palette.inkSoft,
                        fontFamily = contentFontFamily,
                        fontStyle = FontStyle.Italic,
                        fontSize = fontSizeSp.sp,
                        lineHeight = lineHeightSp.sp,
                        textAlign = contentTextAlign,
                    )
                }
            }

            BlockStyle.CALLOUT -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(palette.elevatedSurface)
                        .border(1.dp, palette.border, RoundedCornerShape(8.dp))
                        .padding(12.dp),
                ) {
                    Text(
                        text = annotated,
                        color = palette.ink,
                        fontFamily = contentFontFamily,
                        fontSize = (fontSizeSp - 1f).sp,
                        lineHeight = (lineHeightSp - 2f).sp,
                        textAlign = contentTextAlign,
                    )
                }
            }

            BlockStyle.PARAGRAPH, BlockStyle.IMAGE -> {
                Text(
                    text = annotated,
                    color = palette.ink,
                    fontFamily = contentFontFamily,
                    fontSize = fontSizeSp.sp,
                    lineHeight = lineHeightSp.sp,
                    textAlign = contentTextAlign,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        val candidates = remember(item.text) { extractCandidatePhrases(item.text) }
        if (candidates.isNotEmpty() && item.style != BlockStyle.HEADING && item.text.length >= 60) {
            Spacer(modifier = Modifier.height(3.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                candidates.forEach { candidate ->
                    val alreadySaved = pagePhrases.any { it.phrase.equals(candidate, ignoreCase = true) }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(
                                if (alreadySaved) palette.highlightColor(HighlightTone.SAGE) else palette.pillBg.copy(alpha = 0.7f),
                            )
                            .clickable { onQuickSavePhrase(candidate, item.text) }
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    ) {
                        Text(
                            text = if (alreadySaved) "✓ $candidate" else "+ $candidate",
                            color = palette.inkSoft,
                            fontSize = 9.5.sp,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Non-interfering lateral touch edge zones for Android / Mobile reading (Punto 4).
 * Only occupies thin 24.dp outer margins in paged mode so text selection (long press)
 * and vertical scrolling in the central reading canvas are never intercepted.
 */
@Composable
internal fun BoxScope.MobileEdgePageTurnZones(
    enabled: Boolean,
    canPrev: Boolean,
    canNext: Boolean,
    onPrevPage: () -> Unit,
    onNextPage: () -> Unit,
) {
    if (!enabled) return
    // Left edge tap zone (24.dp wide)
    Box(
        modifier = Modifier
            .align(Alignment.CenterStart)
            .width(24.dp)
            .fillMaxHeight(0.68f)
            .clickable(enabled = canPrev) { onPrevPage() }
            .testTag("mobile_edge_prev_zone"),
    )
    // Right edge tap zone (24.dp wide)
    Box(
        modifier = Modifier
            .align(Alignment.CenterEnd)
            .width(24.dp)
            .fillMaxHeight(0.68f)
            .clickable(enabled = canNext) { onNextPage() }
            .testTag("mobile_edge_next_zone"),
    )
}

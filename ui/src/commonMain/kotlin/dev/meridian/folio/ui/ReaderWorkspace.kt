package dev.meridian.folio.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.meridian.folio.core.BlockStyle
import dev.meridian.folio.core.BookDocument
import dev.meridian.folio.core.BookPage
import dev.meridian.folio.core.BookTypographySettings
import dev.meridian.folio.core.DocumentFormat
import dev.meridian.folio.core.FinishTimePrediction
import dev.meridian.folio.core.HighlightTone
import dev.meridian.folio.core.NavigationHistoryEntry
import dev.meridian.folio.core.PageFitMode
import dev.meridian.folio.core.PageReminder
import dev.meridian.folio.core.PageViewMode
import dev.meridian.folio.core.PdfPageColorMode
import dev.meridian.folio.core.ReaderTheme
import dev.meridian.folio.core.SavedPhrase
import dev.meridian.folio.core.UiAppearanceSettings
import dev.meridian.folio.ui.theme.*
import kotlinx.coroutines.flow.first
import kotlin.math.max
import kotlin.math.roundToInt

// =========================================================================================
// 1. STITCH DESKTOP TOP NAVIGATION BAR ("Serene Vintage Reader — Lector Inmersivo Escritorio")
// =========================================================================================
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun StitchDesktopTopNavigationBar(
    palette: ClayPalette,
    theme: ReaderTheme,
    pdfPageColorMode: PdfPageColorMode,
    viewMode: PageViewMode,
    activeSection: MeridianSection,
    currentBook: BookDocument,
    activeReminder: PageReminder?,
    fontSizePt: Float,
    lineSpacingMultiplier: Float,
    uiAppearance: UiAppearanceSettings,
    bookTypography: BookTypographySettings,
    isMobileOverride: Boolean,
    isZenControlsHidden: Boolean,
    isFullscreen: Boolean,
    onSelectSection: (MeridianSection) -> Unit,
    onSelectViewMode: (PageViewMode) -> Unit,
    onSelectTheme: (ReaderTheme) -> Unit,
    onToggleTheme: () -> Unit,
    onSelectPdfColorMode: (PdfPageColorMode) -> Unit,
    onFontSizeChange: (Float) -> Unit,
    onLineSpacingChange: (Float) -> Unit,
    onUpdateBookTypography: ((BookTypographySettings) -> BookTypographySettings) -> Unit,
    onToggleReminderEditor: () -> Unit,
    onToggleQuickMark: () -> Unit,
    onToggleMobilePreview: () -> Unit,
    onToggleZenControls: () -> Unit,
    onToggleFullscreen: () -> Unit,
    onOpenFileDialog: (() -> Unit)?,
    onOpenSearch: () -> Unit,
    onOpenPageEditor: () -> Unit,
    onOpenChapterManager: () -> Unit,
    onOpenMetadataEditor: () -> Unit,
    canUndoEdit: Boolean,
    canRedoEdit: Boolean,
    onUndoEdit: () -> Unit,
    onRedoEdit: () -> Unit,
) {
    var showTypographyPopover by remember { mutableStateOf(false) }
    val uiFontFamily = uiAppearance.fontFamily.toComposeFontFamily()
    val isPdfOriginalMode = currentBook.format == DocumentFormat.PDF && !bookTypography.pdfPreferReflowText
    // Real chapter position of the current page (1-based index into the book TOC); 0 when the
    // book has no chapter metadata, in which case the strip omits the chapter badge.
    val chapterNumber = remember(currentBook.id, currentBook.pages, currentBook.currentPage) {
        val entries = currentBook.tableOfContents
        entries.indexOfLast { it.startPage <= currentBook.currentPage } + 1
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(palette.canvas.copy(alpha = 0.96f)),
    ) {
        if (isMobileOverride) {
            ClayBottomNav(activeSection, onSelectSection)
        } else {
            ClayTopNav(activeSection, onSelectSection, theme, onToggleTheme)
        }
        if (activeSection == MeridianSection.READER) {
            // 1A. BOOK STRIP (Stitch ribbon): back to library, real format, title and chapter.
            ReaderBookStrip(
                palette = palette,
                book = currentBook,
                chapterNumber = chapterNumber,
                onBackToLibrary = { onSelectSection(MeridianSection.LIBRARY) },
            )
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                ClayChromeAction("Aa  Apariencia", palette, "top_typography_popover_btn", showTypographyPopover) {
                    showTypographyPopover = !showTypographyPopover
                }
                ClayChromeAction("Marcapáginas", palette, "open_reminder_btn", activeReminder != null, onClick = onToggleReminderEditor)
                ClayChromeAction("+ Frase", palette, "open_mark_phrase_btn", onClick = onToggleQuickMark)
                ClayChromeAction("Buscar", palette, "reader_search_btn", description = "Buscar y reemplazar en el libro", onClick = onOpenSearch)
                ClayChromeAction("Editar página", palette, "open_page_editor_btn", description = "Corregir palabras y párrafos de la página actual", onClick = onOpenPageEditor)
                ClayChromeAction("Capítulos", palette, "open_chapter_manager_btn", description = "Renombrar, reordenar y reparar capítulos", onClick = onOpenChapterManager)
                ClayChromeAction("Metadatos", palette, "open_metadata_btn", description = "Editar título, autor, idioma y portada", onClick = onOpenMetadataEditor)
                ClayChromeAction("Deshacer", palette, "reader_undo_btn", enabled = canUndoEdit, description = "Deshacer el último cambio", onClick = onUndoEdit)
                ClayChromeAction("Rehacer", palette, "reader_redo_btn", enabled = canRedoEdit, description = "Rehacer el último cambio deshecho", onClick = onRedoEdit)
                ClayChromeAction("Enfoque", palette, "zen_mode_toggle_btn", onClick = onToggleZenControls)
                ClayChromeAction("⛶  Pantalla completa", palette, "fullscreen_toggle_btn", isFullscreen, onClick = onToggleFullscreen)
                ClayChromeAction("Vista móvil", palette, "mobile_preview_toggle", isMobileOverride, onClick = onToggleMobilePreview)
                if (onOpenFileDialog != null) ClayChromeAction("Abrir archivo", palette, "reader_open_file", onClick = onOpenFileDialog)
            }
        }
        // Expandable Quick Typography & Screen Tone Strip (when "Aa" button clicked in header).
        // Clay popover (Task 10): the former M3 `Surface` band is now a content-sized clay card
        // inset with the desktop gutters; every control keeps its handler, label and tag.
        AnimatedVisibility(visible = showTypographyPopover) {
            ClayContentCard(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = ClaySpacing.gutterDesktop, vertical = ClaySpacing.xs),
            ) {
                FlowRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(ClaySpacing.md),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalArrangement = Arrangement.Center,
                ) {
                    // 4 Screen Tone Presets. A labeled clay pill per tone: `ClayChip` cannot carry
                    // the real tone swatch dot, so the pill is hand-rolled from the same tokens.
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text("Tono de Papel:", color = palette.textMuted, style = LocalClayTypography.current.labelSm)
                        ReaderTheme.entries.forEach { t ->
                            val sel = t == theme
                            val toneShape = RoundedCornerShape(percent = 50)
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp),
                                modifier = Modifier
                                    .heightIn(min = 36.dp)
                                    .clip(toneShape)
                                    .background(if (sel) palette.onSurface else palette.surfaceSecondary)
                                    .border(1.dp, palette.outlineVariant.copy(alpha = 0.6f), toneShape)
                                    .clayFocusableClickable(palette, toneShape) { onSelectTheme(t) }
                                    .padding(horizontal = ClaySpacing.sm),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(12.dp)
                                        .clip(CircleShape)
                                        .background(Color(t.swatchHex))
                                        .border(1.dp, palette.textMuted, CircleShape),
                                )
                                Text(
                                    text = t.stitchLabel,
                                    color = if (sel) palette.canvas else palette.onSurface,
                                    style = LocalClayTypography.current.labelMd,
                                )
                            }
                        }
                    }

                    // Independent PDF Page Color Transformation Selector.
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text("Color Pág. PDF:", color = palette.textMuted, style = LocalClayTypography.current.labelSm)
                        PdfPageColorMode.entries.forEach { pdfMode ->
                            ClayChip(
                                label = pdfMode.shortLabel,
                                selected = pdfMode == pdfPageColorMode,
                                onClick = { onSelectPdfColorMode(pdfMode) },
                                tag = "pdf_color_mode_${pdfMode.name}",
                                palette = palette,
                            )
                        }
                    }

                    // View Mode Pills (also accessible when in Editor/Archive/Notebook).
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text("Vista:", color = palette.textMuted, style = LocalClayTypography.current.labelSm)
                        PageViewMode.entries.forEach { m ->
                            ClayChip(
                                label = m.stitchLabel,
                                selected = m == viewMode,
                                onClick = { onSelectViewMode(m) },
                                palette = palette,
                            )
                        }
                    }

                    // Content Typography Controls (Distinguishes PDF Original View vs EPUB / Reflow Mode)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (isPdfOriginalMode) {
                            Text(
                                text = "PDF en Vista Original (fiel a maquetación):",
                                color = palette.textMuted,
                                style = LocalClayTypography.current.labelSm,
                            )
                            ClayChip(
                                label = "Activar Texto Adaptable (Reflujo)",
                                selected = true,
                                onClick = { onUpdateBookTypography { it.copy(pdfPreferReflowText = true) } },
                                palette = palette,
                            )
                        } else {
                            if (currentBook.format == DocumentFormat.PDF) {
                                ClayChip(
                                    label = "Vista Original PDF",
                                    selected = false,
                                    onClick = { onUpdateBookTypography { it.copy(pdfPreferReflowText = false) } },
                                    palette = palette,
                                )
                            }
                            Text("Cuerpo:", color = palette.textMuted, style = LocalClayTypography.current.labelSm)
                            ClayChip(
                                label = "A-",
                                selected = false,
                                onClick = { onFontSizeChange(fontSizePt - 1f) },
                                palette = palette,
                            )
                            Text(
                                text = "${fontSizePt.toInt()}pt",
                                color = palette.onSurface,
                                fontFamily = FontFamily.Monospace,
                                style = LocalClayTypography.current.labelMd,
                            )
                            ClayChip(
                                label = "A+",
                                selected = false,
                                onClick = { onFontSizeChange(fontSizePt + 1f) },
                                palette = palette,
                            )

                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Interlineado:", color = palette.textMuted, style = LocalClayTypography.current.labelSm)
                            listOf(1.5f, 1.8f, 2.1f).forEach { mult ->
                                ClayChip(
                                    label = "$mult",
                                    selected = kotlin.math.abs(lineSpacingMultiplier - mult) < 0.1f,
                                    onClick = { onLineSpacingChange(mult) },
                                    palette = palette,
                                )
                            }
                        }
                    }
                }
            }
        }

        HorizontalDivider(color = palette.outlineVariant, thickness = 1.dp)
    }
}

/**
 * Book strip of the desktop reader: the Stitch "top reader ribbon". A frosted clay card that
 * carries a circular back-to-library button, the real document format breadcrumb, the book
 * title and the current chapter badge (`Cap. N`, derived from the TOC). The tool row below
 * stays a separate scrollable row so every chrome action keeps its reachability.
 */
@Composable
private fun ReaderBookStrip(
    palette: ClayPalette,
    book: BookDocument,
    chapterNumber: Int,
    onBackToLibrary: () -> Unit,
) {
    val shape = RoundedCornerShape(ClayRadius.lg)
    val pillShape = RoundedCornerShape(percent = 50)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = ClaySpacing.gutterDesktop, vertical = ClaySpacing.xs),
    ) {
        ClayShadowBox(
            shadow = ClayShadows.card,
            shape = shape,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .testTag("reader_book_strip"),
        ) {
            Box(Modifier.matchParentSize().clayFrosted(shape, surface = palette.cardSurface))
            // Centring the row vertically removes the dead space left by the 64.dp minimum
            // height when the content is shorter than the bar.
            Row(
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth()
                    .padding(horizontal = ClaySpacing.md, vertical = ClaySpacing.xs),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(ClaySpacing.sm),
            ) {
                Row(
                    modifier = Modifier
                        .clip(pillShape)
                        .background(palette.surfaceSecondary)
                        .border(1.dp, palette.outlineVariant.copy(alpha = 0.6f), pillShape)
                        .clayFocusableClickable(palette, shape = pillShape, onClick = onBackToLibrary)
                        .testTag("reader_back_to_library_btn")
                        .padding(horizontal = ClaySpacing.sm, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text("←", color = palette.onSurfaceVariant, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Text(
                        text = "Biblioteca",
                        color = palette.onSurfaceVariant,
                        style = LocalClayTypography.current.labelLg,
                        maxLines = 1,
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = "BIBLIOTECA",
                            color = palette.textMuted,
                            style = LocalClayTypography.current.labelSm,
                            maxLines = 1,
                        )
                        Text("/", color = palette.outlineVariant, style = LocalClayTypography.current.labelSm)
                        Text(
                            text = book.format.label.uppercase(),
                            color = palette.primary,
                            style = LocalClayTypography.current.labelSm,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(ClaySpacing.xs),
                    ) {
                        Text(
                            text = book.title,
                            color = palette.onSurface,
                            style = LocalClayTypography.current.titleMd,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        if (chapterNumber > 0) {
                            Box(
                                modifier = Modifier
                                    .clip(pillShape)
                                    .background(palette.primaryContainer)
                                    .padding(horizontal = ClaySpacing.xs, vertical = 2.dp),
                            ) {
                                Text(
                                    text = "Cap. $chapterNumber",
                                    color = palette.onPrimaryContainer,
                                    style = LocalClayTypography.current.labelSm,
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// =========================================================================================
// 2. STITCH DESKTOP READER WORKSPACE: BOOK SPREAD + SWALLOWTAIL RIBBON + COMMAND DOCK
// =========================================================================================
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun StitchDesktopReaderSpreadWorkspace(
    palette: ClayPalette,
    theme: ReaderTheme,
    pdfPageColorMode: PdfPageColorMode,
    book: BookDocument,
    viewMode: PageViewMode,
    fitMode: PageFitMode,
    zoomScale: Float,
    fontSizePt: Float,
    lineSpacingMultiplier: Float,
    bookTypography: BookTypographySettings,
    prediction: FinishTimePrediction,
    activeReminder: PageReminder?,
    savedPhrases: List<SavedPhrase>,
    isTimerRunning: Boolean,
    isZenControlsHidden: Boolean,
    isFullscreen: Boolean,
    backHistoryTop: NavigationHistoryEntry?,
    forwardHistoryTop: NavigationHistoryEntry?,
    onPageChange: (Int) -> Unit,
    onJumpToChapter: (Int, String) -> Unit,
    onNavigateBack: () -> Unit,
    onNavigateForward: () -> Unit,
    onSelectViewMode: (PageViewMode) -> Unit,
    onSelectFitMode: (PageFitMode) -> Unit,
    onChangeZoomScale: (Float) -> Unit,
    onSelectTheme: (ReaderTheme) -> Unit,
    onSelectPdfColorMode: (PdfPageColorMode) -> Unit,
    onFontSizeChange: (Float) -> Unit,
    onLineSpacingChange: (Float) -> Unit,
    onUpdateBookTypography: ((BookTypographySettings) -> BookTypographySettings) -> Unit,
    onRunOcrOnPage: (Int) -> Unit,
    onToggleTimer: () -> Unit,
    onExitZenControls: () -> Unit,
    onToggleFullscreen: () -> Unit,
    onQuickSavePhrase: (phrase: String, context: String) -> Unit,
    onSetReminderHere: () -> Unit,
    onOpenNotebook: () -> Unit,
) {
    val pagination = LocalReaderPagination.current
    val currentPageObj = book.pages.find { it.pageNumber == book.currentPage } ?: book.pages.first()
    val secondPageObj = if (viewMode == PageViewMode.TWO_COLUMNS && book.currentPage < book.totalPages) {
        book.pages.find { it.pageNumber == book.currentPage + 1 }
    } else {
        null
    }
    val tocEntries = remember(book.id, book.pages.size) { book.tableOfContents }
    var showTocBar by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.canvas)
            .padding(
                horizontal = if (isZenControlsHidden) 14.dp else 24.dp,
                vertical = if (isZenControlsHidden) 10.dp else 16.dp,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // 2A. AUXILIARY ARCHIVAL UTILITY BAR (Hidden in Zen Mode so pages fill the workspace)
        if (!isZenControlsHidden) {
            ClayReaderToolbar(
                palette, book, backHistoryTop, forwardHistoryTop, showTocBar, savedPhrases.size,
                fitMode, zoomScale, pdfPageColorMode, onNavigateBack, onNavigateForward,
                { showTocBar = !showTocBar }, onOpenNotebook,
                onSelectFitMode, onChangeZoomScale, onSelectPdfColorMode,
            )
        }
        // Optional Expandable Chapter TOC Jump Strip (records jump in Navigation History)
        if (!isZenControlsHidden && showTocBar && tocEntries.isNotEmpty()) {
            val stripShape = RoundedCornerShape(ClayRadius.md)
            Row(
                modifier = Modifier
                    .widthIn(max = 1240.dp)
                    .fillMaxWidth()
                    .padding(bottom = ClaySpacing.xs)
                    .clip(stripShape)
                    .background(palette.surfaceSecondary)
                    .clayPressed(stripShape)
                    // `clayPressed` paints its inset band 4..12dp inside the edge; padding the
                    // chips/`Ir al capítulo` label by `sm` keeps them clear of it.
                    .padding(horizontal = ClaySpacing.sm, vertical = ClaySpacing.sm)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(ClaySpacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Ir al capítulo:",
                    color = palette.textMuted,
                    style = LocalClayTypography.current.labelSm,
                    maxLines = 1,
                )
                tocEntries.forEach { entry ->
                    val isCurrentChap = currentPageObj.chapterTitle.startsWith(entry.title)
                    val chipShape = RoundedCornerShape(percent = 50)
                    Box(
                        modifier = Modifier
                            .clip(chipShape)
                            .background(if (isCurrentChap) palette.primary else palette.surfaceContainerLowest)
                            .border(1.dp, palette.outlineVariant, chipShape)
                            .clayFocusableClickable(palette, chipShape) {
                                onJumpToChapter(entry.startPage, entry.title)
                            }
                            .padding(horizontal = ClaySpacing.sm, vertical = 6.dp)
                            .testTag("toc_chapter_${entry.startPage}"),
                    ) {
                        Text(
                            text = "${entry.title.take(36)} (p.${entry.startPage})",
                            color = if (isCurrentChap) palette.onPrimary else palette.onSurface,
                            fontSize = 11.sp,
                            fontWeight = if (isCurrentChap) FontWeight.SemiBold else FontWeight.Normal,
                            maxLines = 1,
                        )
                    }
                }
            }
        }

        // 2B. BOOK OPEN SPREAD SHELL WITH DRAPED SWALLOWTAIL RIBBON BOOKMARK
        val baseSpreadWidth = when {
            isZenControlsHidden && viewMode == PageViewMode.ONE_COLUMN -> 980.dp
            isZenControlsHidden -> 1600.dp
            viewMode == PageViewMode.ONE_COLUMN ||
                viewMode == PageViewMode.VERTICAL_PAGINATED ||
                viewMode == PageViewMode.VERTICAL_CONTINUOUS ||
                viewMode == PageViewMode.REFLOW_TEXT -> 880.dp
            else -> 1240.dp
        }
        val maxSpreadWidth = when (fitMode) {
            PageFitMode.FIT_WIDTH -> 1680.dp
            PageFitMode.MANUAL_ZOOM -> (baseSpreadWidth.value * zoomScale.coerceIn(0.6f, 2.2f)).dp
            PageFitMode.FIT_PAGE -> baseSpreadWidth
        }
        val effectiveFontSizePt = if (fitMode == PageFitMode.MANUAL_ZOOM) {
            (fontSizePt * zoomScale).coerceIn(11f, 34f)
        } else {
            fontSizePt
        }

        val tone = ClayTokens.readingTone(theme, LocalClayUiAppearance.current.highContrastText)
        val step = if (viewMode == PageViewMode.TWO_COLUMNS) 2 else 1
        val canGoPrev = book.currentPage > 1 || pagination.canTurn(book.currentPage, step, -1)
        val canGoNext = book.currentPage < book.totalPages || pagination.canTurn(book.currentPage, step, 1)

        Box(
            modifier = Modifier
                .weight(1f)
                .widthIn(max = maxSpreadWidth)
                .fillMaxWidth()
                .then(if (viewMode == PageViewMode.VERTICAL_CONTINUOUS) Modifier else Modifier.readerPageGestures(book.id to viewMode) { direction ->
                    pagination.turn(book, step, direction, onPageChange)
                }),
            contentAlignment = Alignment.TopCenter,
        ) {
            // The sheet keeps a gutter for the lateral page chevrons (as in the Stitch mock)
            // so they never cover the running header or the first glyphs of a line.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = if (isZenControlsHidden) 0.dp else 20.dp),
            ) {
                when (viewMode) {
                PageViewMode.TWO_COLUMNS -> {
                    // Dual-Page Open Book Spread: one clay sheet with the two leaves and the spine crease.
                    ReaderSheetCard(tone = tone, modifier = Modifier.fillMaxSize()) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            Row(modifier = Modifier.fillMaxSize()) {
                                // Left Page (Verso)
                                StitchBookSpreadLeaf(
                                    palette = palette,
                                    tone = tone,
                                    pdfPageColorMode = pdfPageColorMode,
                                    book = book,
                                    page = currentPageObj,
                                    savedPhrases = savedPhrases,
                                    fontSizeSp = (effectiveFontSizePt - 1.2f).coerceAtLeast(13f),
                                    lineHeightSp = (effectiveFontSizePt - 1.2f).coerceAtLeast(13f) * lineSpacingMultiplier,
                                    bookTypography = bookTypography,
                                    isLeftLeaf = true,
                                    reserveRightBookmarkSpace = false,
                                    onQuickSavePhrase = onQuickSavePhrase,
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight(),
                                )

                                // Right Page (Recto)
                                if (secondPageObj != null) {
                                    StitchBookSpreadLeaf(
                                        palette = palette,
                                        tone = tone,
                                        pdfPageColorMode = pdfPageColorMode,
                                        book = book,
                                        page = secondPageObj,
                                        savedPhrases = savedPhrases,
                                        fontSizeSp = (effectiveFontSizePt - 1.2f).coerceAtLeast(13f),
                                        lineHeightSp = (effectiveFontSizePt - 1.2f).coerceAtLeast(13f) * lineSpacingMultiplier,
                                        bookTypography = bookTypography,
                                        isLeftLeaf = false,
                                        reserveRightBookmarkSpace = true,
                                        onQuickSavePhrase = onQuickSavePhrase,
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxHeight(),
                                    )
                                } else {
                                    // End of Book Colophon Leaf (follows the reading tone)
                                    Column(
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxHeight()
                                            .background(tone.sheet)
                                            .padding(36.dp),
                                        verticalArrangement = Arrangement.Center,
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                    ) {
                                        ClayLogoMark(size = 52.dp)
                                        Spacer(modifier = Modifier.height(14.dp))
                                        Text(
                                            text = "EXPLICIT LIBER · FIN DEL VOLUMEN",
                                            color = tone.muted,
                                            fontSize = 10.sp,
                                            fontFamily = FontFamily.Monospace,
                                            letterSpacing = 2.sp,
                                        )
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = book.title,
                                            color = tone.ink,
                                            fontFamily = bookTypography.fontFamily.toComposeFontFamily(),
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 20.sp,
                                            textAlign = TextAlign.Center,
                                        )
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = "Has alcanzado el último folio (${book.totalPages} de ${book.totalPages}).",
                                            color = tone.inkSoft,
                                            fontSize = 13.sp,
                                            textAlign = TextAlign.Center,
                                        )
                                    }
                                }
                            }

                            // Center Book Crease & Spine Shadow Gradient — alphas follow the sheet tone.
                            Box(
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .width(16.dp)
                                    .fillMaxHeight()
                                    .background(
                                        Brush.horizontalGradient(
                                            colors = listOf(
                                                Color.Transparent,
                                                palette.spineShadow.copy(alpha = if (tone.isDark) 0.12f else 0.02f),
                                                palette.spineShadow.copy(alpha = if (tone.isDark) 0.18f else 0.04f),
                                                palette.spineShadow.copy(alpha = if (tone.isDark) 0.12f else 0.02f),
                                                Color.Transparent,
                                            ),
                                        ),
                                    ),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.Center)
                                        .width(1.dp)
                                        .fillMaxHeight()
                                        .background(tone.border),
                                )
                            }
                        }
                    }
                }

                PageViewMode.ONE_COLUMN -> {
                    ReaderSheetCard(tone = tone, modifier = Modifier.fillMaxSize()) {
                        StitchBookSpreadLeaf(
                            palette = palette,
                            tone = tone,
                            pdfPageColorMode = pdfPageColorMode,
                            book = book,
                            page = currentPageObj,
                            savedPhrases = savedPhrases,
                            fontSizeSp = effectiveFontSizePt,
                            lineHeightSp = effectiveFontSizePt * lineSpacingMultiplier,
                            bookTypography = bookTypography,
                            isLeftLeaf = false,
                            reserveRightBookmarkSpace = true,
                            onQuickSavePhrase = onQuickSavePhrase,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }

                PageViewMode.VERTICAL_PAGINATED -> {
                    VerticalPaginatedReaderCanvas(
                        palette = palette,
                        tone = tone,
                        pdfPageColorMode = pdfPageColorMode,
                        book = book,
                        savedPhrases = savedPhrases,
                        fontSizePt = effectiveFontSizePt,
                        lineSpacingMultiplier = lineSpacingMultiplier,
                        bookTypography = bookTypography,
                        zoomScale = zoomScale,
                        onPageChange = onPageChange,
                        onQuickSavePhrase = onQuickSavePhrase,
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                PageViewMode.VERTICAL_CONTINUOUS -> {
                    VerticalContinuousReaderCanvas(
                        palette = palette,
                        tone = tone,
                        pdfPageColorMode = pdfPageColorMode,
                        book = book,
                        savedPhrases = savedPhrases,
                        fontSizePt = effectiveFontSizePt,
                        lineSpacingMultiplier = lineSpacingMultiplier,
                        bookTypography = bookTypography,
                        zoomScale = zoomScale,
                        onPageChange = onPageChange,
                        onQuickSavePhrase = onQuickSavePhrase,
                        onReturnToOriginalView = {
                            onUpdateBookTypography { it.copy(pdfPreferReflowText = false) }
                            onSelectViewMode(PageViewMode.ONE_COLUMN)
                        },
                        onRunOcrOnPage = onRunOcrOnPage,
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                PageViewMode.REFLOW_TEXT -> {
                    AdaptiveReflowReaderCanvas(
                        palette = palette,
                        tone = tone,
                        pdfPageColorMode = pdfPageColorMode,
                        book = book,
                        page = currentPageObj,
                        savedPhrases = savedPhrases,
                        fontSizePt = effectiveFontSizePt,
                        lineSpacingMultiplier = lineSpacingMultiplier,
                        bookTypography = bookTypography,
                        zoomScale = zoomScale,
                        onQuickSavePhrase = onQuickSavePhrase,
                        onReturnToOriginalView = {
                            onUpdateBookTypography { it.copy(pdfPreferReflowText = false) }
                            onSelectViewMode(PageViewMode.ONE_COLUMN)
                        },
                        onRunOcrOnPage = onRunOcrOnPage,
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                PageViewMode.STRETCH -> {
                    ReaderSheetCard(tone = tone, modifier = Modifier.fillMaxSize()) {
                        StitchBookSpreadLeaf(
                            palette = palette,
                            tone = tone,
                            pdfPageColorMode = pdfPageColorMode,
                            book = book,
                            page = currentPageObj,
                            savedPhrases = savedPhrases,
                            fontSizeSp = effectiveFontSizePt + 2f,
                            lineHeightSp = (effectiveFontSizePt + 2f) * lineSpacingMultiplier,
                            bookTypography = bookTypography,
                            isLeftLeaf = false,
                            reserveRightBookmarkSpace = true,
                            stretchVertically = true,
                            onQuickSavePhrase = onQuickSavePhrase,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }

                PageViewMode.WIDE -> {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        ReaderSheetCard(tone = tone, modifier = Modifier.weight(0.72f).fillMaxHeight()) {
                            StitchBookSpreadLeaf(
                                palette = palette,
                                tone = tone,
                                pdfPageColorMode = pdfPageColorMode,
                                book = book,
                                page = currentPageObj,
                                savedPhrases = savedPhrases,
                                fontSizeSp = effectiveFontSizePt + 0.5f,
                                lineHeightSp = (effectiveFontSizePt + 0.5f) * lineSpacingMultiplier,
                                bookTypography = bookTypography,
                                isLeftLeaf = false,
                                reserveRightBookmarkSpace = false,
                                onQuickSavePhrase = onQuickSavePhrase,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }

                        // Side Marginalia & Telemetry Card in Wide Mode (part of the sheet, so it
                        // follows the reading tone instead of the light chrome).
                        ReaderSheetCard(tone = tone, modifier = Modifier.weight(0.28f).fillMaxHeight()) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(top = 20.dp, start = 20.dp, end = 48.dp, bottom = 20.dp)
                                    .verticalScroll(rememberScrollState()),
                            ) {
                                Text(
                                    text = "MARGINALIA & RITMO",
                                    color = tone.muted,
                                    fontSize = 10.5.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.4.sp,
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Restante: ${prediction.formattedRemainingTime()}",
                                    color = tone.ink,
                                    fontFamily = FontFamily.Serif,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 16.sp,
                                )
                                Text(
                                    text = "Cadencia: ${prediction.formattedPacePerPage()} (${prediction.formattedWpm()})",
                                    color = tone.inkSoft,
                                    fontSize = 12.sp,
                                )
                                Spacer(modifier = Modifier.height(14.dp))
                                HorizontalDivider(color = tone.border)
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "NOTAS EN ESTE FOLIO",
                                    color = tone.muted,
                                    fontSize = 10.5.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.2.sp,
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                val pagePhrases = savedPhrases.filter { it.pageNumber == currentPageObj.pageNumber }
                                if (pagePhrases.isEmpty()) {
                                    Text(
                                        text = "Haz clic en cualquier frase sugerida debajo de un párrafo para subrayarla y guardarla en la página ${currentPageObj.pageNumber}.",
                                        color = tone.muted,
                                        fontSize = 12.sp,
                                    )
                                } else {
                                    pagePhrases.forEach { sp ->
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(bottom = 8.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(readingHighlight(tone, sp.tone))
                                                .padding(10.dp),
                                        ) {
                                            Column {
                                                Text(
                                                    text = "«${sp.phrase}»",
                                                    color = tone.ink,
                                                    fontFamily = FontFamily.Serif,
                                                    fontWeight = FontWeight.SemiBold,
                                                    fontSize = 12.5.sp,
                                                )
                                                if (sp.note.isNotBlank()) {
                                                    Text(
                                                        text = sp.note,
                                                        color = tone.inkSoft,
                                                        fontSize = 11.sp,
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                }
            }

            // Lateral clay page chevrons (Stitch side navigation): the existing prev/next tags.
            // Hidden in Zen Mode, where the floating overlay carries the same two controls.
            if (!isZenControlsHidden) {
                ReaderPageChevron(
                    palette = palette,
                    tag = "prev_page_button",
                    glyph = "‹",
                    description = "Página anterior",
                    enabled = canGoPrev,
                    onClick = { pagination.turn(book, step, -1, onPageChange) },
                    modifier = Modifier.align(Alignment.CenterStart),
                )
                ReaderPageChevron(
                    palette = palette,
                    tag = "next_page_button",
                    glyph = "›",
                    description = "Página siguiente",
                    enabled = canGoNext,
                    onClick = { pagination.turn(book, step, 1, onPageChange) },
                    modifier = Modifier.align(Alignment.CenterEnd),
                )
            }

            // Draped Top-Right Swallowtail Bookmark Ribbon (Shiori 栞) — positioned in reserved right margin so it NEVER overlaps "FOLIO N"
            val isThisPageBookmarked = activeReminder?.pageNumber == currentPageObj.pageNumber ||
                (secondPageObj != null && activeReminder?.pageNumber == secondPageObj.pageNumber)
            ClayBookmarkRibbon(
                isActive = isThisPageBookmarked,
                tint = palette.warningAmber,
                width = 28.dp,
                height = 56.dp,
                onClick = onSetReminderHere,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = 14.dp)
                    .offset(y = (-2).dp),
            )

            // Floating Zen Mode Overlay Controls (when Zen Mode hides standard bars)
            if (isZenControlsHidden) {
                val zenShape = RoundedCornerShape(ClayRadius.md)
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = ClaySpacing.xs),
                ) {
                    // Clay chrome bar: kit radius plus the floating shadow stack of the kit.
                    ClayShadowBox(shadow = ClayShadows.floating, shape = zenShape, modifier = Modifier.matchParentSize()) {
                        Box(
                            Modifier
                                .matchParentSize()
                                .clip(zenShape)
                                .background(palette.surfaceSecondary.copy(alpha = 0.94f))
                                .border(1.dp, palette.outlineVariant, zenShape),
                        )
                    }
                    Row(
                        modifier = Modifier.padding(horizontal = ClaySpacing.sm, vertical = ClaySpacing.xs),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(ClaySpacing.xs),
                    ) {
                        Text(
                            text = "shiorifolio",
                            color = palette.textMuted,
                            fontFamily = FontFamily.Serif,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 11.5.sp,
                        )
                        val zenPillShape = RoundedCornerShape(percent = 50)
                        Box(
                            modifier = Modifier
                                .clip(zenPillShape)
                                .background(if (book.currentPage > 1 || pagination.canTurn(book.currentPage, step, -1)) palette.surfaceContainerHigh else palette.disabledContainer)
                                .clickable(enabled = book.currentPage > 1 || pagination.canTurn(book.currentPage, step, -1)) {
                                    pagination.turn(book, step, -1, onPageChange)
                                }
                                .padding(horizontal = ClaySpacing.xs, vertical = 3.dp)
                                .testTag("prev_page_button"),
                        ) {
                            Text("‹ Ant.", color = if (book.currentPage > 1 || pagination.canTurn(book.currentPage, step, -1)) palette.onSurface else palette.disabledContent, fontSize = 11.sp)
                        }
                        Text(
                            text = "Pág. ${book.currentPage}${if (secondPageObj != null) "-${secondPageObj.pageNumber}" else ""} / ${book.totalPages}",
                            color = palette.onSurface,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Box(
                            modifier = Modifier
                                .clip(zenPillShape)
                                .background(if (book.currentPage < book.totalPages || pagination.canTurn(book.currentPage, step, 1)) palette.surfaceContainerHigh else palette.disabledContainer)
                                .clickable(enabled = book.currentPage < book.totalPages || pagination.canTurn(book.currentPage, step, 1)) {
                                    pagination.turn(book, step, 1, onPageChange)
                                }
                                .padding(horizontal = ClaySpacing.xs, vertical = 3.dp)
                                .testTag("next_page_button"),
                        ) {
                            Text("Sig. ›", color = if (book.currentPage < book.totalPages || pagination.canTurn(book.currentPage, step, 1)) palette.onSurface else palette.disabledContent, fontSize = 11.sp)
                        }
                        Box(
                            modifier = Modifier
                                .clip(zenPillShape)
                                .background(palette.primary)
                                .clickable { onExitZenControls() }
                                .padding(horizontal = ClaySpacing.sm, vertical = 4.dp)
                                .testTag("exit_zen_mode_btn"),
                        ) {
                            Text(
                                text = "Mostrar Controles (Esc)",
                                color = palette.onPrimary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                        Box(
                            modifier = Modifier
                                .clip(zenPillShape)
                                .background(palette.surfaceContainerHigh)
                                .border(1.dp, palette.outlineVariant, zenPillShape)
                                .clickable { onToggleFullscreen() }
                                .padding(horizontal = ClaySpacing.xs, vertical = 4.dp),
                        ) {
                            Text(
                                text = if (isFullscreen) "Salir Pantalla Completa" else "Pantalla Completa ⛶",
                                color = palette.onSurface,
                                fontSize = 10.5.sp,
                            )
                        }
                    }
                }
            }
        }

        if (!isZenControlsHidden) {
            Spacer(modifier = Modifier.height(10.dp))

            // 2C. FLOATING READER COMMAND DOCK (Stitch Bottom Command Bar)
            StitchFloatingCommandDock(
                palette = palette,
                theme = theme,
                viewMode = viewMode,
                fontSizePt = fontSizePt,
                lineSpacingMultiplier = lineSpacingMultiplier,
                bookTypography = bookTypography,
                book = book,
                secondPageNumber = secondPageObj?.pageNumber,
                prediction = prediction,
                isTimerRunning = isTimerRunning,
                onPageChange = onPageChange,
                onSelectViewMode = onSelectViewMode,
                onSelectTheme = onSelectTheme,
                onFontSizeChange = onFontSizeChange,
                onLineSpacingChange = onLineSpacingChange,
                onUpdateBookTypography = onUpdateBookTypography,
                onToggleTimer = onToggleTimer,
            )
        }
    }
}

private fun currentBookAuthorSubtitle(book: BookDocument): String =
    if (book.subtitle.isNotBlank()) "${book.author} · ${book.subtitle}" else book.author

/** Highlight fill for [tone] inside the sheet: dark variant only on a dark sheet. */
internal fun readingHighlight(tone: ReadingTone, highlight: HighlightTone): Color =
    Color(if (tone.isDark) highlight.hexDark else highlight.hexLight)

/**
 * Clay sheet card: a [ClayShadows.card] card tinted with [ReadingTone.sheet] and outlined
 * with [ReadingTone.border]. Used by the open spread and every single-sheet view mode so
 * the reading surface — and only it — follows the reader theme.
 *
 * The outline is applied to the host box, so it is painted after the sheet fill *and* the
 * sheet content: an opaque leaf that repaints its own `tone.sheet` (e.g. the spread leaf)
 * can no longer hide the card edge, while the outer drop shadow layers stay behind.
 */
@Composable
internal fun ReaderSheetCard(
    tone: ReadingTone,
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = RoundedCornerShape(ClayRadius.lg),
    content: @Composable BoxScope.() -> Unit,
) {
    ClayShadowBox(
        shadow = ClayShadows.card,
        shape = shape,
        modifier = modifier.border(1.dp, tone.border, shape),
    ) {
        Box(Modifier.matchParentSize().background(tone.sheet, shape))
        content()
    }
}

/**
 * Circular clay page-turn chevron flanking the spread. Carries the existing
 * `prev_page_button` / `next_page_button` tags; drawn last so it stays above the sheet.
 */
@Composable
private fun ReaderPageChevron(
    palette: ClayPalette,
    tag: String,
    glyph: String,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ClayShadowBox(shadow = ClayShadows.floating, shape = CircleShape, modifier = modifier.size(44.dp)) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .clip(CircleShape)
                .background(if (enabled) palette.cardSurface else palette.disabledContainer)
                .border(1.dp, palette.outlineVariant.copy(alpha = 0.6f), CircleShape)
                .clayFocusableClickable(palette, shape = CircleShape, enabled = enabled, onClick = onClick)
                .testTag(tag)
                .semantics { contentDescription = description },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = glyph,
                color = if (enabled) palette.onSurfaceVariant else palette.disabledContent,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

/**
 * Computes a ColorFilter for PDF rendered page images according to the user's selected
 * PdfPageColorMode and the current reading [tone].
 * Preserves natural colors on full-page artwork/illustrations in THEME_TINT dark mode so
 * covers and illustrations never turn into photo-negatives unless SMART_DARK is chosen.
 */
internal fun pdfPageImageColorFilter(
    tone: ReadingTone,
    mode: PdfPageColorMode,
    isPdfDocument: Boolean,
    isFullPageArtwork: Boolean = false,
): ColorFilter? {
    if (!isPdfDocument) return null
    return when (mode) {
        PdfPageColorMode.ORIGINAL -> null
        PdfPageColorMode.THEME_TINT -> {
            if (tone.isDark) {
                if (isFullPageArtwork) {
                    null
                } else {
                    val m = ColorMatrix(
                        floatArrayOf(
                            -0.80f, 0f, 0f, 0f, 238f,
                            0f, -0.79f, 0f, 0f, 232f,
                            0f, 0f, -0.77f, 0f, 222f,
                            0f, 0f, 0f, 1f, 0f,
                        ),
                    )
                    ColorFilter.colorMatrix(m)
                }
            } else {
                ColorFilter.tint(tone.sheet, androidx.compose.ui.graphics.BlendMode.Multiply)
            }
        }
        PdfPageColorMode.SMART_DARK -> {
            val m = ColorMatrix(
                floatArrayOf(
                    -0.86f, 0f, 0f, 0f, 245f,
                    0f, -0.84f, 0f, 0f, 239f,
                    0f, 0f, -0.81f, 0f, 228f,
                    0f, 0f, 0f, 1f, 0f,
                ),
            )
            ColorFilter.colorMatrix(m)
        }
    }
}

/**
 * Individual Book Leaf inside the Stitch Open Book Spread.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun StitchBookSpreadLeaf(
    palette: ClayPalette,
    tone: ReadingTone,
    pdfPageColorMode: PdfPageColorMode,
    book: BookDocument,
    page: BookPage,
    savedPhrases: List<SavedPhrase>,
    fontSizeSp: Float,
    lineHeightSp: Float,
    bookTypography: BookTypographySettings,
    isLeftLeaf: Boolean,
    reserveRightBookmarkSpace: Boolean = !isLeftLeaf,
    stretchVertically: Boolean = false,
    onQuickSavePhrase: (phrase: String, context: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val pagePhrases = remember(savedPhrases, page.pageNumber) {
        savedPhrases.filter { it.pageNumber == page.pageNumber }
    }
    val imageBlocks = remember(page.blocks) {
        page.blocks.filter { it.style == BlockStyle.IMAGE && !it.imagePath.isNullOrBlank() }
    }
    val nonEmptyTextBlocks = remember(page.blocks) {
        page.blocks.filter { it.style != BlockStyle.IMAGE && it.text.isNotBlank() }
    }
    val isSingleImageLeaf = imageBlocks.size == 1 && nonEmptyTextBlocks.isEmpty()

    val pdfColorFilter = remember(tone, pdfPageColorMode, book.format, isSingleImageLeaf) {
        pdfPageImageColorFilter(
            tone = tone,
            mode = pdfPageColorMode,
            isPdfDocument = book.format == DocumentFormat.PDF,
            isFullPageArtwork = isSingleImageLeaf,
        )
    }

    val contentFontFamily = bookTypography.fontFamily.toComposeFontFamily()
    val contentTextAlign = bookTypography.textAlignment.toComposeTextAlign()
    val horizPad = if (isSingleImageLeaf) {
        14.dp
    } else {
        bookTypography.horizontalMarginDp.coerceIn(16f, 64f).dp
    }
    val vertPad = if (isSingleImageLeaf) {
        12.dp
    } else {
        bookTypography.verticalMarginDp.coerceIn(8f, 44f).dp
    }

    Column(
        modifier = modifier
            .background(tone.sheet)
            .padding(
                start = horizPad,
                end = horizPad,
                top = vertPad,
                bottom = 14.dp,
            ),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        // 1. Running Archival Header — reserves right space when the SwallowtailBookmarkRibbon is anchored on this leaf so it NEVER overlaps "FOLIO N"
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(end = if (reserveRightBookmarkSpace) 44.dp else 0.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (isLeftLeaf) book.title else page.chapterTitle,
                    color = tone.muted,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 0.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "${page.pageNumber}",
                    color = tone.muted,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 10.sp,
                    maxLines = 1,
                    softWrap = false,
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            HorizontalDivider(color = tone.border, thickness = 1.dp)
        }

        // 2. Leaf Body: Full-Page Illustration/PDF Canvas vs. Scrollable Literary Body
        val singleImagePath = if (isSingleImageLeaf) imageBlocks.first().imagePath else null
        val fullLeafBitmap = if (singleImagePath != null) {
            rememberLoadedImageBitmap(
                path = singleImagePath,
                bookFilePath = book.filePath,
                bookId = book.id,
                pageNumber = page.pageNumber,
            )
        } else {
            null
        }

        if (isSingleImageLeaf && fullLeafBitmap != null) {
            // Full-page PDF/EPUB illustration leaf: fills 100% of the available leaf height & width without any 520.dp cap or inner border frame!
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    bitmap = fullLeafBitmap,
                    contentDescription = "Folio ${page.pageNumber}",
                    contentScale = ContentScale.Fit,
                    colorFilter = pdfColorFilter,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(6.dp))
                        .testTag("leaf_full_page_image_${page.pageNumber}"),
                )
            }
        } else {
            PagedBookBody(
                palette, tone, pdfPageColorMode, book, page, pagePhrases, fontSizeSp, lineHeightSp,
                bookTypography, onQuickSavePhrase,
                Modifier.weight(1f).fillMaxWidth().padding(vertical = 10.dp),
            )
        }
        // 3. Archival Folio Footer ("Folio • M-01" and page number)
        Column(modifier = Modifier.fillMaxWidth()) {
            HorizontalDivider(color = tone.border, thickness = 1.dp)
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (isLeftLeaf) {
                    Text(
                        text = "${page.pageNumber}",
                        color = tone.ink,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Medium,
                        fontSize = 11.5.sp,
                    )
                    Text(
                        text = "Folio • SF-${page.pageNumber.toString().padStart(2, '0')} (${page.wordCount} palabras)",
                        color = tone.muted,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                    )
                } else {
                    Text(
                        text = if (pagePhrases.isNotEmpty()) {
                            "Notas de lectura activas (${pagePhrases.size})"
                        } else {
                            "ShioriFolio • Edición Crítica (${page.wordCount} palabras)"
                        },
                        color = tone.muted,
                        fontStyle = FontStyle.Italic,
                        fontSize = 10.5.sp,
                    )
                    Text(
                        text = "${page.pageNumber}",
                        color = tone.ink,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Medium,
                        fontSize = 11.5.sp,
                    )
                }
            }
        }
    }
}

/**
 * Floating Reader Command Dock (Stitch Desktop Bottom Control Dock), a frosted clay card:
 * - Top Row: `Pág. 1-2 / 237` indicator, `0% — [Interactive Scrubber] — 100%` (the slider only
 *   commits through [onPageChange] when the gesture ends), the live `Ritmo` telemetry pill and
 *   the reflow typography controls.
 * - Bottom Row: View Mode Switcher (`Doble página`, `Simple`, `Ancho`, `Stretch`), the four clay
 *   `Tono Ambiental` swatches (`reader_tone_<THEME>`) and the PDF original/reflow controls.
 *
 * Page turning lives on the lateral clay chevrons, not here, so the dock never duplicates the
 * `prev_page_button` / `next_page_button` tags.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StitchFloatingCommandDock(
    palette: ClayPalette,
    theme: ReaderTheme,
    viewMode: PageViewMode,
    fontSizePt: Float,
    lineSpacingMultiplier: Float,
    bookTypography: BookTypographySettings,
    book: BookDocument,
    secondPageNumber: Int?,
    prediction: FinishTimePrediction,
    isTimerRunning: Boolean,
    onPageChange: (Int) -> Unit,
    onSelectViewMode: (PageViewMode) -> Unit,
    onSelectTheme: (ReaderTheme) -> Unit,
    onFontSizeChange: (Float) -> Unit,
    onLineSpacingChange: (Float) -> Unit,
    onUpdateBookTypography: ((BookTypographySettings) -> BookTypographySettings) -> Unit,
    onToggleTimer: () -> Unit,
) {
    val isPdfOriginalMode = book.format == DocumentFormat.PDF && !bookTypography.pdfPreferReflowText
    // Drag preview only: the repository is updated once, when the drag ends.
    var sliderPage by remember(book.currentPage) { mutableStateOf(book.currentPage.toFloat()) }
    val dockShape = RoundedCornerShape(ClayRadius.md)
    val pageRangeLabel = if (secondPageNumber != null) {
        "${book.currentPage}-${secondPageNumber} / ${book.totalPages}"
    } else {
        "${book.currentPage} / ${book.totalPages}"
    }

    // ClayShadowBox only paints matchParentSize layers, so the dock's own content sizes the
    // outer Box and the frosted surface is drawn underneath it.
    Box(
        modifier = Modifier
            .widthIn(max = 1240.dp)
            .fillMaxWidth()
            .testTag("reader_dock"),
    ) {
        ClayShadowBox(shadow = ClayShadows.floating, shape = dockShape, modifier = Modifier.matchParentSize()) {
            Box(Modifier.matchParentSize().clayFrosted(dockShape, surface = palette.cardSurface))
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Top Row: Page Stepper + Interactive Scrubber + Live Reading Pace Telemetry Pill
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                // Page indicator pill (the lateral clay chevrons own page turning)
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(ClayRadius.sm))
                        .background(palette.surfaceSecondary)
                        .border(1.dp, palette.outlineVariant.copy(alpha = 0.7f), RoundedCornerShape(ClayRadius.sm))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                        .testTag("reader_page_indicator"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Text(
                        text = "Pág.",
                        color = palette.textMuted,
                        style = LocalClayTypography.current.labelSm,
                    )
                    Text(
                        text = pageRangeLabel,
                        color = palette.onSurface,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp,
                    )
                }

                // Interactive Scrubber Slider (0% — Slider — 100%). A single-page document has
                // no page 2 to announce, so the scrubber is hidden rather than offered as a
                // seek target that does not exist.
                if (book.totalPages > 1) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = "${(prediction.completionFraction * 100).roundToInt()}%",
                            color = palette.textMuted,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                        )
                        Slider(
                            value = sliderPage,
                            onValueChange = { sliderPage = it },
                            onValueChangeFinished = {
                                onPageChange(sliderPage.roundToInt().coerceIn(1, book.totalPages))
                            },
                            valueRange = 1f..book.totalPages.coerceAtLeast(2).toFloat(),
                            colors = SliderDefaults.colors(
                                thumbColor = palette.primary,
                                activeTrackColor = palette.primary,
                                inactiveTrackColor = palette.outlineVariant,
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .height(22.dp)
                                .testTag("reader_progress_slider"),
                        )
                        Text(
                            text = "100%",
                            color = palette.textMuted,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.weight(1f))
                }

                // Live Telemetry Pill ("Ritmo: 7h 4m • 33s/pág (80 WPM)")
                val telemetryShape = RoundedCornerShape(percent = 50)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .clip(telemetryShape)
                        .background(palette.surfaceContainerHigh)
                        .border(1.dp, palette.outlineVariant, telemetryShape)
                        .clayFocusableClickable(palette = palette, shape = telemetryShape) { onToggleTimer() }
                        .padding(horizontal = ClaySpacing.sm, vertical = 6.dp)
                        .testTag("prediction_badge"),
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(if (isTimerRunning) palette.primary else palette.textMuted)
                            .testTag("reading_timer_toggle"),
                    )
                    Text(
                        text = "${prediction.formattedRemainingTime()} restantes",
                        color = palette.onSurface,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                    )
                }

                // Reflow typography micro-controls (hidden in PDF original view, where the glyph
                // size is baked into the scanned page).
                if (!isPdfOriginalMode) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(ClayRadius.sm))
                            .background(palette.surfaceContainerHigh)
                            .border(1.dp, palette.outlineVariant.copy(alpha = 0.7f), RoundedCornerShape(ClayRadius.sm))
                            .testTag("dock_reflow_font_controls"),
                    ) {
                        Box(
                            modifier = Modifier
                                .clayFocusableClickable(palette = palette, shape = RoundedCornerShape(ClayRadius.sm)) {
                                    onFontSizeChange(fontSizePt - 1f)
                                }
                                .padding(horizontal = 9.dp, vertical = 5.dp)
                                .testTag("dock_font_decrease_btn"),
                        ) {
                            Text("A-", color = palette.onSurface, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, fontSize = 11.5.sp)
                        }
                        Text(
                            text = "${fontSizePt.toInt()}pt",
                            color = palette.textMuted,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(horizontal = 3.dp),
                        )
                        Box(
                            modifier = Modifier
                                .clayFocusableClickable(palette = palette, shape = RoundedCornerShape(ClayRadius.sm)) {
                                    onFontSizeChange(fontSizePt + 1f)
                                }
                                .padding(horizontal = 9.dp, vertical = 5.dp)
                                .testTag("dock_font_increase_btn"),
                        ) {
                            Text("A+", color = palette.onSurface, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, fontSize = 11.5.sp)
                        }
                    }
                }
            }

            HorizontalDivider(color = palette.outlineVariant.copy(alpha = 0.6f), thickness = 1.dp)

            // Bottom Row: View Mode Switcher + 4 Circular Screen Tone Swatches + Typography / PDF Mode Micro-Controls
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalArrangement = Arrangement.Center,
            ) {
                // View Mode Switcher (All 7 modes: Doble página, Simple, Págs. Vertical, Continuo, Texto Adaptable, Ancho, Stretch) — preserves testTag("view_mode_${mode.name}")
                // Clay restyle: sunken `surfaceSecondary` pill well holding fully-rounded item pills.
                val viewModeWellShape = RoundedCornerShape(ClayRadius.sm)
                val viewModeItemShape = RoundedCornerShape(percent = 50)
                FlowRow(
                    verticalArrangement = Arrangement.Center,
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                    modifier = Modifier
                        .clip(viewModeWellShape)
                        .background(palette.surfaceSecondary)
                        .padding(3.dp),
                ) {
                    val orderedModes = listOf(
                        PageViewMode.TWO_COLUMNS,
                        PageViewMode.ONE_COLUMN,
                        PageViewMode.VERTICAL_PAGINATED,
                        PageViewMode.VERTICAL_CONTINUOUS,
                        PageViewMode.REFLOW_TEXT,
                        PageViewMode.WIDE,
                        PageViewMode.STRETCH,
                    )
                    orderedModes.forEach { mode ->
                        val selected = mode == viewMode
                        Box(
                            modifier = Modifier
                                .clip(viewModeItemShape)
                                .background(if (selected) palette.primary else Color.Transparent)
                                .clayFocusableClickable(palette = palette, shape = viewModeItemShape) {
                                    onSelectViewMode(mode)
                                }
                                .padding(horizontal = 7.dp, vertical = 4.dp)
                                .testTag("view_mode_${mode.name}"),
                        ) {
                            Text(
                                text = mode.stitchLabel,
                                color = if (selected) palette.onPrimary else palette.onSurfaceVariant,
                                style = LocalClayTypography.current.labelSm,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                            )
                        }
                    }
                }

                // Screen tone presets: the same clay swatches the shell uses, tagged per theme.
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(ClaySpacing.xs),
                ) {
                    Text(
                        text = "Tono Ambiental",
                        color = palette.textMuted,
                        style = LocalClayTypography.current.labelSm,
                    )
                    ClayToneSwatches(
                        selected = theme,
                        onSelect = onSelectTheme,
                        palette = palette,
                        tagPrefix = "reader_tone_",
                    )
                }

                // PDF mode controls plus the line-spacing cycle (the reflow font controls sit in
                // the top row so they stay visible in every non-PDF-original layout).
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (isPdfOriginalMode) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(palette.surfaceContainerHigh)
                                .border(1.dp, palette.outlineVariant, RoundedCornerShape(8.dp))
                                .padding(horizontal = 9.dp, vertical = 4.dp)
                                .testTag("pdf_original_mode_badge"),
                        ) {
                            Text(
                                text = "PDF: Vista Original",
                                color = palette.textMuted,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(palette.primary)
                                .clayFocusableClickable(palette = palette, shape = RoundedCornerShape(8.dp)) {
                                    onUpdateBookTypography { it.copy(pdfPreferReflowText = true) }
                                }
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                                .testTag("dock_pdf_reflow_toggle"),
                        ) {
                            Text(
                                text = "Modo Texto Adaptable",
                                color = palette.onPrimary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    } else {
                        if (book.format == DocumentFormat.PDF) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(palette.surfaceContainerHigh)
                                    .border(1.dp, palette.outlineVariant, RoundedCornerShape(8.dp))
                                    .clayFocusableClickable(palette = palette, shape = RoundedCornerShape(8.dp)) {
                                        onUpdateBookTypography { it.copy(pdfPreferReflowText = false) }
                                    }
                                    .padding(horizontal = 9.dp, vertical = 4.dp)
                                    .testTag("dock_pdf_reflow_toggle"),
                            ) {
                                Text(
                                    text = "Vista Original PDF",
                                    color = palette.onSurface,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }

                        // Line Spacing cycle button
                        val nextSpacing = when {
                            lineSpacingMultiplier < 1.65f -> 1.8f
                            lineSpacingMultiplier < 1.95f -> 2.1f
                            else -> 1.5f
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(palette.surfaceContainerHigh)
                                .border(1.dp, palette.outlineVariant, RoundedCornerShape(8.dp))
                                .clayFocusableClickable(palette = palette, shape = RoundedCornerShape(8.dp)) {
                                    onLineSpacingChange(nextSpacing)
                                }
                                .padding(horizontal = 9.dp, vertical = 4.dp),
                        ) {
                            Text(
                                text = "↕ ${lineSpacingMultiplier}×",
                                color = palette.onSurface,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun PageReminderBanner(
    palette: ClayPalette,
    currentBook: BookDocument,
    reminder: PageReminder?,
    showEditor: Boolean,
    onJumpToReminder: (Int) -> Unit,
    onSaveReminder: (pageNumber: Int, note: String, targetLabel: String) -> Unit,
    onClearReminder: () -> Unit,
    onToggleEditor: () -> Unit,
    onCloseEditor: () -> Unit,
) {
    val defaultPageStr = (reminder?.pageNumber ?: currentBook.currentPage).toString()
    val defaultNoteStr = reminder?.note
        ?: "Continuar en Pág. ${currentBook.currentPage} (${
            currentBook.pages.find { it.pageNumber == currentBook.currentPage }?.chapterTitle ?: ""
        })"
    val defaultTargetStr = reminder?.targetLabel ?: "Próxima Sesión"

    var pageInput by remember(currentBook.currentPage, reminder, showEditor) {
        mutableStateOf(defaultPageStr)
    }
    var noteInput by remember(currentBook.currentPage, reminder, showEditor) {
        mutableStateOf(defaultNoteStr)
    }
    var targetLabelInput by remember(reminder, showEditor) {
        mutableStateOf(defaultTargetStr)
    }

    val cancelAndDiscardChanges: () -> Unit = {
        pageInput = defaultPageStr
        noteInput = defaultNoteStr
        targetLabelInput = defaultTargetStr
        onCloseEditor()
    }

    val textFieldColors = clayTextFieldColors(palette)
    // Clay restyle: token radii (well/card `ClayRadius.base`, pills fully rounded).
    val reminderShape = RoundedCornerShape(ClayRadius.base)
    val reminderPillShape = RoundedCornerShape(percent = 50)

    // Clay restyle: the reminder banner is a frosted [ClayShadows.card] surface instead of an
    // M3 Surface. The card content sizes the outer Box and the shadow/frosted layers ride
    // along as matchParentSize siblings (ClayShadowBox cannot size itself).
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 3.dp)
            .onPreviewKeyEvent { event ->
                if (showEditor && event.type == KeyEventType.KeyDown && event.key == Key.Escape) {
                    cancelAndDiscardChanges()
                    true
                } else {
                    false
                }
            }
            .testTag("page_reminder_banner"),
    ) {
        ClayShadowBox(
            shadow = ClayShadows.card,
            shape = reminderShape,
            modifier = Modifier
                .matchParentSize()
                .border(1.dp, palette.outlineVariant, reminderShape),
        ) {
            Box(Modifier.matchParentSize().clayFrosted(reminderShape, surface = palette.surfaceContainerLowest))
        }
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)) {
            if (reminder != null && !showEditor) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(reminderPillShape)
                                .background(palette.warningAmber)
                                .padding(horizontal = 7.dp, vertical = 2.dp),
                        ) {
                            Text(
                                text = "Guardado · pág. ${reminder.pageNumber}",
                                color = palette.onWarningAmber,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.sp,
                            )
                        }
                        Text(
                            text = "${reminder.targetLabel}: ${reminder.note}",
                            color = palette.onSurfaceVariant,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ClayPrimaryButton(
                            label = "Continuar en Pág. ${reminder.pageNumber}",
                            onClick = { onJumpToReminder(reminder.pageNumber) },
                            palette = palette,
                        )
                        ClaySecondaryButton(
                            label = "Editar",
                            onClick = onToggleEditor,
                            palette = palette,
                        )
                    }
                }
            }

            if (showEditor) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Configurar Recordatorio de Página (栞 Shiori)",
                        color = palette.onSurface,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                    )
                    Box(
                        modifier = Modifier
                            .clip(reminderPillShape)
                            .background(palette.surfaceContainerHigh)
                            .border(1.dp, palette.outlineVariant, reminderPillShape)
                            .clickable { cancelAndDiscardChanges() }
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                    ) {
                        Text(
                            text = "✕ Cerrar (Esc)",
                            color = palette.onSurfaceVariant,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = pageInput,
                        onValueChange = { pageInput = it.filter { ch -> ch.isDigit() } },
                        label = { Text("Pág. # (1-${currentBook.totalPages})", fontSize = 11.sp) },
                        singleLine = true,
                        shape = RoundedCornerShape(ClayRadius.sm),
                        colors = textFieldColors,
                        modifier = Modifier
                            .width(120.dp)
                            .shioriTextFieldFocusGuard(),
                    )
                    OutlinedTextField(
                        value = targetLabelInput,
                        onValueChange = { targetLabelInput = it },
                        label = { Text("Cuándo (ej. Esta noche)", fontSize = 11.sp) },
                        singleLine = true,
                        shape = RoundedCornerShape(ClayRadius.sm),
                        colors = textFieldColors,
                        modifier = Modifier
                            .width(180.dp)
                            .shioriTextFieldFocusGuard(),
                    )
                    OutlinedTextField(
                        value = noteInput,
                        onValueChange = { noteInput = it },
                        label = { Text("Nota de lectura", fontSize = 11.sp) },
                        singleLine = true,
                        shape = RoundedCornerShape(ClayRadius.sm),
                        colors = textFieldColors,
                        modifier = Modifier
                            .weight(1f)
                            .shioriTextFieldFocusGuard(),
                    )
                    ClayPrimaryButton(
                        label = "Guardar",
                        onClick = {
                            val parsedPage = pageInput.toIntOrNull() ?: currentBook.currentPage
                            onSaveReminder(parsedPage, noteInput, targetLabelInput)
                        },
                        palette = palette,
                    )
                    ClaySecondaryButton(
                        label = "Cancelar",
                        onClick = cancelAndDiscardChanges,
                        tag = "close_reminder_editor_btn",
                        palette = palette,
                    )
                    if (reminder != null) {
                        ClaySecondaryButton(
                            label = "Borrar",
                            onClick = onClearReminder,
                            palette = palette,
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun QuickMarkPhraseBar(
    palette: ClayPalette,
    currentBook: BookDocument,
    phrase: String,
    note: String,
    tone: HighlightTone,
    onPhraseChange: (String) -> Unit,
    onNoteChange: (String) -> Unit,
    onToneChange: (HighlightTone) -> Unit,
    onSave: () -> Unit,
    onClose: () -> Unit,
) {
    val textFieldColors = clayTextFieldColors(palette)
    val barShape = RoundedCornerShape(ClayRadius.sm)

    // Clay restyle: frosted clay card with a `primary` outline marking the active editor,
    // replacing the former M3 Surface (same tags/behavior, clay look).
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 4.dp),
    ) {
        ClayShadowBox(
            shadow = ClayShadows.card,
            shape = barShape,
            modifier = Modifier
                .matchParentSize()
                .border(1.dp, palette.primary, barShape),
        ) {
            Box(Modifier.matchParentSize().clayFrosted(barShape, surface = palette.surfaceContainerLowest))
        }
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Marcar y Guardar Palabra o Frase (Pág. ${currentBook.currentPage})",
                    color = palette.onSurface,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    HighlightTone.entries.forEach { t ->
                        val isSelected = t == tone
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(palette.highlightColor(t))
                                .border(
                                    width = if (isSelected) 2.dp else 1.dp,
                                    color = if (isSelected) palette.onSurface else palette.outlineVariant,
                                    shape = RoundedCornerShape(4.dp),
                                )
                                .clickable { onToneChange(t) }
                                .padding(horizontal = 8.dp, vertical = 3.dp),
                        ) {
                            Text(t.label, color = palette.onSurface, fontSize = 11.sp)
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = phrase,
                    onValueChange = onPhraseChange,
                    label = { Text("Palabra o frase para subrayar y guardar", fontSize = 11.sp) },
                    singleLine = true,
                    shape = RoundedCornerShape(ClayRadius.sm),
                    colors = textFieldColors,
                    modifier = Modifier
                        .weight(1f)
                        .shioriTextFieldFocusGuard(),
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = onNoteChange,
                    label = { Text("Nota / Definición", fontSize = 11.sp) },
                    singleLine = true,
                    shape = RoundedCornerShape(ClayRadius.sm),
                    colors = textFieldColors,
                    modifier = Modifier
                        .weight(1f)
                        .shioriTextFieldFocusGuard(),
                )
                ClayPrimaryButton(
                    label = "Guardar Frase",
                    onClick = onSave,
                    palette = palette,
                )
                ClaySecondaryButton(
                    label = "Cancelar",
                    onClick = onClose,
                    palette = palette,
                )
            }
        }
    }
}

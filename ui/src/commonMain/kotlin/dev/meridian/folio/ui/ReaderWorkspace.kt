package dev.meridian.folio.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
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
        // Expandable Quick Typography & Screen Tone Strip (when "Aa" button clicked in header)
        AnimatedVisibility(visible = showTypographyPopover) {
            Surface(
                color = palette.surfaceSecondary,
                border = BorderStroke(1.dp, palette.outlineVariant),
                modifier = Modifier.fillMaxWidth(),
            ) {
                FlowRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalArrangement = Arrangement.Center,
                ) {
                    // 4 Screen Tone Presets
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text("Tono de Papel:", color = palette.textMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        ReaderTheme.entries.forEach { t ->
                            val sel = t == theme
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (sel) palette.onSurface else palette.surfaceContainerLowest)
                                    .border(1.dp, palette.outlineVariant, RoundedCornerShape(6.dp))
                                    .clickable { onSelectTheme(t) }
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
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
                                    fontSize = 11.sp,
                                    fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal,
                                )
                            }
                        }
                    }

                    // Independent PDF Page Color Transformation Selector
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text("Color Pág. PDF:", color = palette.textMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        PdfPageColorMode.entries.forEach { pdfMode ->
                            val sel = pdfMode == pdfPageColorMode
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(5.dp))
                                    .background(if (sel) palette.primary else palette.surfaceContainerLowest)
                                    .border(1.dp, palette.outlineVariant, RoundedCornerShape(5.dp))
                                    .clickable { onSelectPdfColorMode(pdfMode) }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                                    .testTag("pdf_color_mode_${pdfMode.name}"),
                            ) {
                                Text(
                                    text = pdfMode.shortLabel,
                                    color = if (sel) palette.onPrimary else palette.onSurface,
                                    fontSize = 11.sp,
                                    fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal,
                                )
                            }
                        }
                    }

                    // View Mode Pills (also accessible when in Editor/Archive/Notebook)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text("Vista:", color = palette.textMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        PageViewMode.entries.forEach { m ->
                            val sel = m == viewMode
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(5.dp))
                                    .background(if (sel) palette.primary else palette.surfaceContainerLowest)
                                    .border(1.dp, palette.outlineVariant, RoundedCornerShape(5.dp))
                                    .clickable { onSelectViewMode(m) }
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                            ) {
                                Text(
                                    text = m.stitchLabel,
                                    color = if (sel) palette.onPrimary else palette.onSurface,
                                    fontSize = 11.sp,
                                    fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal,
                                )
                            }
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
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(5.dp))
                                    .background(palette.primary)
                                    .clickable {
                                        onUpdateBookTypography { it.copy(pdfPreferReflowText = true) }
                                    }
                                    .padding(horizontal = 9.dp, vertical = 4.dp),
                            ) {
                                Text(
                                    text = "Activar Texto Adaptable (Reflujo)",
                                    color = palette.onPrimary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        } else {
                            if (currentBook.format == DocumentFormat.PDF) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(5.dp))
                                        .background(palette.surfaceContainerLowest)
                                        .border(1.dp, palette.outlineVariant, RoundedCornerShape(5.dp))
                                        .clickable {
                                            onUpdateBookTypography { it.copy(pdfPreferReflowText = false) }
                                        }
                                        .padding(horizontal = 8.dp, vertical = 4.dp),
                                ) {
                                    Text(
                                        text = "Vista Original PDF",
                                        color = palette.onSurface,
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                }
                            }
                            Text("Cuerpo:", color = palette.textMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(palette.surfaceContainerLowest)
                                    .border(1.dp, palette.outlineVariant, RoundedCornerShape(4.dp))
                                    .clickable { onFontSizeChange(fontSizePt - 1f) }
                                    .padding(horizontal = 8.dp, vertical = 3.dp),
                            ) {
                                Text("A-", color = palette.onSurface, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                            Text("${fontSizePt.toInt()}pt", color = palette.onSurface, fontFamily = FontFamily.Monospace, fontSize = 11.5.sp)
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(palette.surfaceContainerLowest)
                                    .border(1.dp, palette.outlineVariant, RoundedCornerShape(4.dp))
                                    .clickable { onFontSizeChange(fontSizePt + 1f) }
                                    .padding(horizontal = 8.dp, vertical = 3.dp),
                            ) {
                                Text("A+", color = palette.onSurface, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }

                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Interlineado:", color = palette.textMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            listOf(1.5f, 1.8f, 2.1f).forEach { mult ->
                                val sel = kotlin.math.abs(lineSpacingMultiplier - mult) < 0.1f
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(if (sel) palette.primary else palette.surfaceContainerLowest)
                                        .border(1.dp, palette.outlineVariant, RoundedCornerShape(4.dp))
                                        .clickable { onLineSpacingChange(mult) }
                                        .padding(horizontal = 7.dp, vertical = 3.dp),
                                ) {
                                    Text(
                                        text = "$mult",
                                        color = if (sel) palette.onPrimary else palette.onSurface,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 10.5.sp,
                                        fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        HorizontalDivider(color = palette.outlineVariant, thickness = 1.dp)
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
            Row(
                modifier = Modifier
                    .widthIn(max = 1240.dp)
                    .fillMaxWidth()
                    .padding(bottom = 6.dp)
                    .horizontalScroll(rememberScrollState())
                    .background(palette.surfaceSecondary, RoundedCornerShape(8.dp))
                    .border(1.dp, palette.outlineVariant, RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Ir al capítulo:", color = palette.textMuted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                tocEntries.forEach { entry ->
                    val isCurrentChap = currentPageObj.chapterTitle.startsWith(entry.title)
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(5.dp))
                            .background(if (isCurrentChap) palette.primary else palette.surfaceContainerLowest)
                            .border(1.dp, palette.outlineVariant, RoundedCornerShape(5.dp))
                            .clayFocusableClickable(palette, RoundedCornerShape(5.dp)) {
                                onJumpToChapter(entry.startPage, entry.title)
                            }
                            .padding(horizontal = 9.dp, vertical = 4.dp)
                            .testTag("toc_chapter_${entry.startPage}"),
                    ) {
                        Text(
                            text = "${entry.title.take(36)} (p.${entry.startPage})",
                            color = if (isCurrentChap) palette.onPrimary else palette.onSurface,
                            fontSize = 11.sp,
                            fontWeight = if (isCurrentChap) FontWeight.SemiBold else FontWeight.Normal,
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

        Box(
            modifier = Modifier
                .weight(1f)
                .widthIn(max = maxSpreadWidth)
                .fillMaxWidth()
                .then(if (viewMode == PageViewMode.VERTICAL_CONTINUOUS) Modifier else Modifier.readerPageGestures(book.id to viewMode) { direction ->
                    pagination.turn(book, if (viewMode == PageViewMode.TWO_COLUMNS) 2 else 1, direction, onPageChange)
                }),
            contentAlignment = Alignment.TopCenter,
        ) {
            when (viewMode) {
                PageViewMode.TWO_COLUMNS -> {
                    // Dual-Page Open Book Spread inside a unified rounded-xl card with theme-consistent center spine crease
                    Surface(
                        color = palette.surfaceContainerLowest,
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, palette.outlineVariant),
                        modifier = Modifier
                            .fillMaxSize()
                            .shadow(elevation = 1.dp, shape = RoundedCornerShape(14.dp)),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(palette.surfaceContainerLowest),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(palette.surfaceContainerLowest),
                            ) {
                                // Left Page (Verso)
                                StitchBookSpreadLeaf(
                                    palette = palette,
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
                                        .fillMaxHeight()
                                        .background(palette.surfaceContainerLowest),
                                )

                                // Right Page (Recto)
                                if (secondPageObj != null) {
                                    StitchBookSpreadLeaf(
                                        palette = palette,
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
                                            .fillMaxHeight()
                                            .background(palette.surfaceContainerLowest),
                                    )
                                } else {
                                    // End of Book Colophon Leaf (theme-consistent dark/light surface)
                                    Column(
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxHeight()
                                            .background(palette.surfaceContainerLowest)
                                            .padding(36.dp),
                                        verticalArrangement = Arrangement.Center,
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                    ) {
                                        ClayLogoMark(size = 52.dp)
                                        Spacer(modifier = Modifier.height(14.dp))
                                        Text(
                                            text = "EXPLICIT LIBER · FIN DEL VOLUMEN",
                                            color = palette.textMuted,
                                            fontSize = 10.sp,
                                            fontFamily = FontFamily.Monospace,
                                            letterSpacing = 2.sp,
                                        )
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = book.title,
                                            color = palette.onSurface,
                                            fontFamily = bookTypography.fontFamily.toComposeFontFamily(),
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 20.sp,
                                            textAlign = TextAlign.Center,
                                        )
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = "Has alcanzado el último folio (${book.totalPages} de ${book.totalPages}).",
                                            color = palette.onSurfaceVariant,
                                            fontSize = 13.sp,
                                            textAlign = TextAlign.Center,
                                        )
                                    }
                                }
                            }

                            // Center Book Crease & Spine Shadow Gradient — uses palette.spineShadow (dark shadow in both light and dark modes, never light ink in dark mode!)
                            Box(
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .width(16.dp)
                                    .fillMaxHeight()
                                    .background(
                                        Brush.horizontalGradient(
                                            colors = listOf(
                                                Color.Transparent,
                                                palette.spineShadow.copy(alpha = if (palette.isDarkReading) 0.12f else 0.02f),
                                                palette.spineShadow.copy(alpha = if (palette.isDarkReading) 0.18f else 0.04f),
                                                palette.spineShadow.copy(alpha = if (palette.isDarkReading) 0.12f else 0.02f),
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
                                        .background(palette.outlineVariant),
                                )
                            }
                        }
                    }
                }

                PageViewMode.ONE_COLUMN -> {
                    Surface(
                        color = palette.surfaceContainerLowest,
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, palette.outlineVariant),
                        modifier = Modifier
                            .fillMaxSize()
                            .shadow(elevation = 5.dp, shape = RoundedCornerShape(14.dp)),
                    ) {
                        StitchBookSpreadLeaf(
                            palette = palette,
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
                    Surface(
                        color = palette.surfaceContainerLowest,
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, palette.outlineVariant),
                        modifier = Modifier
                            .fillMaxSize()
                            .shadow(elevation = 5.dp, shape = RoundedCornerShape(14.dp)),
                    ) {
                        StitchBookSpreadLeaf(
                            palette = palette,
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
                        Surface(
                            color = palette.surfaceContainerLowest,
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, palette.outlineVariant),
                            modifier = Modifier
                                .weight(0.72f)
                                .fillMaxHeight()
                                .shadow(elevation = 5.dp, shape = RoundedCornerShape(14.dp)),
                        ) {
                            StitchBookSpreadLeaf(
                                palette = palette,
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

                        // Side Marginalia & Telemetry Card in Wide Mode
                        Surface(
                            color = palette.surfaceContainerLowest,
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, palette.outlineVariant),
                            modifier = Modifier
                                .weight(0.28f)
                                .fillMaxHeight()
                                .shadow(elevation = 4.dp, shape = RoundedCornerShape(14.dp)),
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(top = 20.dp, start = 20.dp, end = 48.dp, bottom = 20.dp)
                                    .verticalScroll(rememberScrollState()),
                            ) {
                                Text(
                                    text = "MARGINALIA & RITMO",
                                    color = palette.textMuted,
                                    fontSize = 10.5.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.4.sp,
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Restante: ${prediction.formattedRemainingTime()}",
                                    color = palette.onSurface,
                                    fontFamily = FontFamily.Serif,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 16.sp,
                                )
                                Text(
                                    text = "Cadencia: ${prediction.formattedPacePerPage()} (${prediction.formattedWpm()})",
                                    color = palette.onSurfaceVariant,
                                    fontSize = 12.sp,
                                )
                                Spacer(modifier = Modifier.height(14.dp))
                                HorizontalDivider(color = palette.outlineVariant)
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "NOTAS EN ESTE FOLIO",
                                    color = palette.textMuted,
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
                                        color = palette.textMuted,
                                        fontSize = 12.sp,
                                    )
                                } else {
                                    pagePhrases.forEach { sp ->
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(bottom = 8.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(palette.highlightColor(sp.tone))
                                                .padding(10.dp),
                                        ) {
                                            Column {
                                                Text(
                                                    text = "«${sp.phrase}»",
                                                    color = palette.onSurface,
                                                    fontFamily = FontFamily.Serif,
                                                    fontWeight = FontWeight.SemiBold,
                                                    fontSize = 12.5.sp,
                                                )
                                                if (sp.note.isNotBlank()) {
                                                    Text(
                                                        text = sp.note,
                                                        color = palette.onSurfaceVariant,
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
                val step = if (viewMode == PageViewMode.TWO_COLUMNS) 2 else 1
                Surface(
                    color = palette.surfaceSecondary.copy(alpha = 0.94f),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, palette.outlineVariant),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 10.dp)
                        .shadow(elevation = 6.dp, shape = RoundedCornerShape(10.dp)),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            text = "shiorifolio",
                            color = palette.textMuted,
                            fontFamily = FontFamily.Serif,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 11.5.sp,
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (book.currentPage > 1 || pagination.canTurn(book.currentPage, step, -1)) palette.surfaceContainerHigh else palette.disabledContainer)
                                .clickable(enabled = book.currentPage > 1 || pagination.canTurn(book.currentPage, step, -1)) {
                                    pagination.turn(book, step, -1, onPageChange)
                                }
                                .padding(horizontal = 8.dp, vertical = 3.dp)
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
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (book.currentPage < book.totalPages || pagination.canTurn(book.currentPage, step, 1)) palette.surfaceContainerHigh else palette.disabledContainer)
                                .clickable(enabled = book.currentPage < book.totalPages || pagination.canTurn(book.currentPage, step, 1)) {
                                    pagination.turn(book, step, 1, onPageChange)
                                }
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                                .testTag("next_page_button"),
                        ) {
                            Text("Sig. ›", color = if (book.currentPage < book.totalPages || pagination.canTurn(book.currentPage, step, 1)) palette.onSurface else palette.disabledContent, fontSize = 11.sp)
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(palette.primary)
                                .clickable { onExitZenControls() }
                                .padding(horizontal = 10.dp, vertical = 4.dp)
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
                                .clip(RoundedCornerShape(6.dp))
                                .background(palette.surfaceContainerHigh)
                                .border(1.dp, palette.outlineVariant, RoundedCornerShape(6.dp))
                                .clickable { onToggleFullscreen() }
                                .padding(horizontal = 8.dp, vertical = 4.dp),
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

/**
 * Computes a ColorFilter for PDF rendered page images according to the user's selected
 * PdfPageColorMode and current ClayPalette.
 * Preserves natural colors on full-page artwork/illustrations in THEME_TINT dark mode so
 * covers and illustrations never turn into photo-negatives unless SMART_DARK is chosen.
 */
internal fun pdfPageImageColorFilter(
    palette: ClayPalette,
    mode: PdfPageColorMode,
    isPdfDocument: Boolean,
    isFullPageArtwork: Boolean = false,
): ColorFilter? {
    if (!isPdfDocument) return null
    return when (mode) {
        PdfPageColorMode.ORIGINAL -> null
        PdfPageColorMode.THEME_TINT -> {
            if (palette.isDarkReading) {
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
                ColorFilter.tint(palette.surfaceContainerLowest, androidx.compose.ui.graphics.BlendMode.Multiply)
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

    val pdfColorFilter = remember(palette, pdfPageColorMode, book.format, isSingleImageLeaf) {
        pdfPageImageColorFilter(
            palette = palette,
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
            .background(palette.surfaceContainerLowest)
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
                    color = palette.textMuted,
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
                    color = palette.textMuted,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 10.sp,
                    maxLines = 1,
                    softWrap = false,
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            HorizontalDivider(color = palette.outlineVariant.copy(alpha = 0.7f), thickness = 1.dp)
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
                palette, pdfPageColorMode, book, page, pagePhrases, fontSizeSp, lineHeightSp,
                bookTypography, onQuickSavePhrase,
                Modifier.weight(1f).fillMaxWidth().padding(vertical = 10.dp),
            )
        }
        // 3. Archival Folio Footer ("Folio • M-01" and page number)
        Column(modifier = Modifier.fillMaxWidth()) {
            HorizontalDivider(color = palette.outlineVariant.copy(alpha = 0.65f), thickness = 1.dp)
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (isLeftLeaf) {
                    Text(
                        text = "${page.pageNumber}",
                        color = palette.onSurface,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Medium,
                        fontSize = 11.5.sp,
                    )
                    Text(
                        text = "Folio • SF-${page.pageNumber.toString().padStart(2, '0')} (${page.wordCount} palabras)",
                        color = palette.textMuted,
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
                        color = palette.textMuted,
                        fontStyle = FontStyle.Italic,
                        fontSize = 10.5.sp,
                    )
                    Text(
                        text = "${page.pageNumber}",
                        color = palette.onSurface,
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
 * Floating Reader Command Dock (Stitch Desktop Bottom Control Dock):
 * - Top Row: Prev/Next + `1-2 / 237` counter, `0% — [Interactive Scrubber] — 100%`, and Live `Ritmo` Telemetry Pill
 * - Bottom Row: View Mode Switcher (`Doble página`, `Simple`, `Ancho`, `Stretch`), 4 Circular Tone Swatches, and Typography / PDF Mode controls.
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
    val pagination = LocalReaderPagination.current
    val step = if (viewMode == PageViewMode.TWO_COLUMNS) 2 else 1
    val isPdfOriginalMode = book.format == DocumentFormat.PDF && !bookTypography.pdfPreferReflowText

    Surface(
        color = palette.surfaceContainerLowest,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, palette.outlineVariant),
        modifier = Modifier
            .widthIn(max = 1240.dp)
            .fillMaxWidth()
            .shadow(elevation = 2.dp, shape = RoundedCornerShape(20.dp)),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Top Row: Page Stepper + Interactive Scrubber + Live Reading Pace Telemetry Pill
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                // Prev / Counter / Next
                val canGoPrev = book.currentPage > 1 || pagination.canTurn(book.currentPage, step, -1)
                val canGoNext = book.currentPage < book.totalPages || pagination.canTurn(book.currentPage, step, 1)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (canGoPrev) palette.surfaceContainerHigh else palette.disabledContainer)
                            .border(1.dp, palette.outlineVariant, RoundedCornerShape(8.dp))
                            .clayFocusableClickable(palette = palette, shape = RoundedCornerShape(8.dp), enabled = canGoPrev) {
                                pagination.turn(book, step, -1, onPageChange)
                            }
                            .testTag("prev_page_button"),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "‹",
                            color = if (canGoPrev) palette.onSurface else palette.disabledContent,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }

                    val pageRangeLabel = if (secondPageNumber != null) {
                        "${book.currentPage}-${secondPageNumber} / ${book.totalPages}"
                    } else {
                        "${book.currentPage} / ${book.totalPages}"
                    }
                    Text(
                        text = pageRangeLabel,
                        color = palette.onSurface,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )

                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (canGoNext) palette.surfaceContainerHigh else palette.disabledContainer)
                            .border(1.dp, palette.outlineVariant, RoundedCornerShape(8.dp))
                            .clayFocusableClickable(palette = palette, shape = RoundedCornerShape(8.dp), enabled = canGoNext) {
                                pagination.turn(book, step, 1, onPageChange)
                            }
                            .testTag("next_page_button"),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "›",
                            color = if (canGoNext) palette.onSurface else palette.disabledContent,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }

                // Interactive Scrubber Slider (0% — Slider — 100%)
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
                        value = book.currentPage.toFloat(),
                        onValueChange = { newVal ->
                            onPageChange(newVal.roundToInt().coerceIn(1, book.totalPages))
                        },
                        valueRange = 1f..book.totalPages.coerceAtLeast(2).toFloat(),
                        colors = SliderDefaults.colors(
                            thumbColor = palette.primary,
                            activeTrackColor = palette.primary,
                            inactiveTrackColor = palette.outlineVariant,
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(22.dp),
                    )
                    Text(
                        text = "100%",
                        color = palette.textMuted,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                    )
                }

                // Live Telemetry Pill ("Ritmo: 7h 4m • 33s/pág (80 WPM)")
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(palette.surfaceContainerHigh)
                        .border(1.dp, palette.outlineVariant, RoundedCornerShape(8.dp))
                        .clayFocusableClickable(palette = palette, shape = RoundedCornerShape(8.dp)) { onToggleTimer() }
                        .padding(horizontal = 10.dp, vertical = 5.dp)
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
            }

            HorizontalDivider(color = palette.outlineVariant.copy(alpha = 0.6f), thickness = 1.dp)

            // Bottom Row: View Mode Switcher + 4 Circular Screen Tone Swatches + Typography / PDF Mode Micro-Controls
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalArrangement = Arrangement.Center,
            ) {
                // View Mode Switcher (All 7 modes: Doble página, Simple, Págs. Vertical, Continuo, Texto Adaptable, Ancho, Stretch) — preserves testTag("view_mode_${mode.name}")
                FlowRow(
                    verticalArrangement = Arrangement.Center,
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(palette.surfaceContainerHigh)
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
                                .clip(RoundedCornerShape(7.dp))
                                .background(if (selected) palette.primary else Color.Transparent)
                                .clayFocusableClickable(palette = palette, shape = RoundedCornerShape(7.dp)) {
                                    onSelectViewMode(mode)
                                }
                                .padding(horizontal = 7.dp, vertical = 4.dp)
                                .testTag("view_mode_${mode.name}"),
                        ) {
                            Text(
                                text = mode.stitchLabel,
                                color = if (selected) palette.onPrimary else palette.onSurfaceVariant,
                                fontSize = 11.sp,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                            )
                        }
                    }
                }

                // 4 Circular Screen Tone Swatches (Kindle Cálido #FCF9F3, Sepia #F5EDDB, Salvia #EEF3EE, Oscuro #1E1B18)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "TONO",
                        color = palette.textMuted,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.2.sp,
                    )
                    ReaderTheme.entries.forEach { t ->
                        val isSel = t == theme
                        Box(
                            modifier = Modifier
                                .size(22.dp)
                                .clip(CircleShape)
                                .background(Color(t.swatchHex))
                                .border(
                                    width = if (isSel) 2.2.dp else 1.dp,
                                    color = if (isSel) palette.primary else palette.textMuted.copy(alpha = 0.5f),
                                    shape = CircleShape,
                                )
                                .clayFocusableClickable(palette = palette, shape = CircleShape) { onSelectTheme(t) },
                        )
                    }
                }

                // Typography Micro-Controls (In PDF Original View, do NOT offer reflow font size controls; offer PDF mode switch instead)
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
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(palette.surfaceContainerHigh)
                                .border(1.dp, palette.outlineVariant, RoundedCornerShape(8.dp))
                                .testTag("dock_reflow_font_controls"),
                        ) {
                            Box(
                                modifier = Modifier
                                    .clayFocusableClickable(palette = palette, shape = RoundedCornerShape(8.dp)) {
                                        onFontSizeChange(fontSizePt - 1f)
                                    }
                                    .padding(horizontal = 9.dp, vertical = 4.dp)
                                    .testTag("dock_font_decrease_btn"),
                            ) {
                                Text("A-", color = palette.onSurface, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, fontSize = 11.5.sp)
                            }
                            Text(
                                text = "${fontSizePt.toInt()}pt",
                                color = palette.textMuted,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(horizontal = 4.dp),
                            )
                            Box(
                                modifier = Modifier
                                    .clayFocusableClickable(palette = palette, shape = RoundedCornerShape(8.dp)) {
                                        onFontSizeChange(fontSizePt + 1f)
                                    }
                                    .padding(horizontal = 9.dp, vertical = 4.dp)
                                    .testTag("dock_font_increase_btn"),
                            ) {
                                Text("A+", color = palette.onSurface, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, fontSize = 11.5.sp)
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

    Surface(
        color = palette.surfaceContainerLowest,
        border = BorderStroke(1.dp, palette.outlineVariant),
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
        shape = RoundedCornerShape(14.dp),
    ) {
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
                                .clip(RoundedCornerShape(4.dp))
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
                        Button(
                            onClick = { onJumpToReminder(reminder.pageNumber) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = palette.primary,
                                contentColor = palette.onPrimary,
                            ),
                        ) {
                            Text("Continuar en Pág. ${reminder.pageNumber}", fontSize = 11.sp)
                        }
                        OutlinedButton(
                            onClick = onToggleEditor,
                            border = BorderStroke(1.dp, palette.outlineVariant),
                        ) {
                            Text("Editar", color = palette.onSurface, fontSize = 11.sp)
                        }
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
                            .clip(RoundedCornerShape(6.dp))
                            .background(palette.surfaceContainerHigh)
                            .border(1.dp, palette.outlineVariant, RoundedCornerShape(6.dp))
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
                        colors = textFieldColors,
                        modifier = Modifier
                            .weight(1f)
                            .shioriTextFieldFocusGuard(),
                    )
                    Button(
                        onClick = {
                            val parsedPage = pageInput.toIntOrNull() ?: currentBook.currentPage
                            onSaveReminder(parsedPage, noteInput, targetLabelInput)
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = palette.primary,
                            contentColor = palette.onPrimary,
                        ),
                    ) {
                        Text("Guardar", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                    OutlinedButton(
                        onClick = cancelAndDiscardChanges,
                        border = BorderStroke(1.dp, palette.outlineVariant),
                        modifier = Modifier.testTag("close_reminder_editor_btn"),
                    ) {
                        Text("Cancelar", color = palette.onSurface, fontSize = 12.sp)
                    }
                    if (reminder != null) {
                        OutlinedButton(
                            onClick = onClearReminder,
                            border = BorderStroke(1.dp, palette.outlineVariant),
                        ) {
                            Text("Borrar", color = palette.onSurfaceVariant, fontSize = 12.sp)
                        }
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

    Surface(
        color = palette.surfaceContainerLowest,
        border = BorderStroke(1.dp, palette.primary),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 4.dp),
    ) {
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
                    colors = textFieldColors,
                    modifier = Modifier
                        .weight(1f)
                        .shioriTextFieldFocusGuard(),
                )
                Button(
                    onClick = onSave,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = palette.primary,
                        contentColor = palette.onPrimary,
                    ),
                ) {
                    Text("Guardar Frase", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
                OutlinedButton(
                    onClick = onClose,
                    border = BorderStroke(1.dp, palette.outlineVariant),
                ) {
                    Text("Cancelar", color = palette.onSurface, fontSize = 12.sp)
                }
            }
        }
    }
}

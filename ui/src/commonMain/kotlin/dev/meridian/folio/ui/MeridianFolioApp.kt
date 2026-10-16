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
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.meridian.folio.core.BlockStyle
import dev.meridian.folio.core.BookDocument
import dev.meridian.folio.core.BookPage
import dev.meridian.folio.core.BookReadingTelemetry
import dev.meridian.folio.core.ContentBlock
import dev.meridian.folio.core.DocumentFormat
import dev.meridian.folio.core.FinishTimePrediction
import dev.meridian.folio.core.FolioRepository
import dev.meridian.folio.core.HighlightTone
import dev.meridian.folio.core.PageReminder
import dev.meridian.folio.core.PageViewMode
import dev.meridian.folio.core.ReaderTheme
import dev.meridian.folio.core.ReadingPredictionEngine
import dev.meridian.folio.core.SavedPhrase
import kotlinx.coroutines.delay
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

enum class MeridianSection(val title: String, val stitchTabTitle: String) {
    READER("Reader", "Reader"),
    LIBRARY("Library & Export", "Archive"),
    LEXICON("Saved Words & Phrases", "Notebook"),
    EDITOR("PDF / EPUB Editor", "Typography & Editor"),
    PREDICTION("Reading Pace & Prediction", "Telemetry"),
}

@Composable
fun MeridianFolioApp(
    repository: FolioRepository = remember { FolioRepository() },
    onPickFileToImport: ((onPathSelected: (String) -> Unit) -> Unit)? = null,
    onPickFileToExport: ((defaultFileName: String, onPathSelected: (String) -> Unit) -> Unit)? = null,
) {
    val state by repository.state.collectAsState()
    val palette = MeridianDesign.paletteFor(state.theme)
    val currentBook = state.documents.find { it.id == state.selectedBookId } ?: state.documents.firstOrNull()

    var activeSection by remember { mutableStateOf(MeridianSection.READER) }
    var isTimerRunning by remember { mutableStateOf(true) }
    var showReminderEditor by remember { mutableStateOf(false) }
    var quickMarkPhrase by remember { mutableStateOf("") }
    var quickMarkNote by remember { mutableStateOf("") }
    var quickMarkTone by remember { mutableStateOf(HighlightTone.SAGE) }
    var showQuickMarkBar by remember { mutableStateOf(false) }
    var statusBannerMessage by remember { mutableStateOf<String?>(null) }

    // User can also manually preview the Mobile Kindle View vs Desktop Spread View on any screen size
    var forceMobileKindleLayout by remember { mutableStateOf<Boolean?>(null) }
    var isZenFullscreen by remember { mutableStateOf(false) }

    // Continuous 1-second reading timer that tracks reading time and recalculates finish-time prediction
    LaunchedEffect(isTimerRunning, currentBook?.id, currentBook?.currentPage, activeSection) {
        while (isTimerRunning && currentBook != null && activeSection == MeridianSection.READER) {
            delay(1000L)
            repository.recordReadingTimeTick(currentBook.id, 1.0)
        }
    }

    if (currentBook == null) {
        Box(
            modifier = Modifier.fillMaxSize().background(palette.paper),
            contentAlignment = Alignment.Center,
        ) {
            Text("No documents in library.", color = palette.ink)
        }
        return
    }

    val bookTelemetry = state.telemetry[currentBook.id]
    val prediction = remember(currentBook, bookTelemetry) {
        ReadingPredictionEngine.computePrediction(currentBook, bookTelemetry)
    }
    val activeReminder = state.reminders[currentBook.id]
    val bookSavedPhrases = remember(state.savedPhrases, currentBook.id) {
        state.savedPhrases.filter { it.bookId == currentBook.id }
    }

    val onOpenFileDialog: (() -> Unit)? = if (onPickFileToImport != null) {
        {
            onPickFileToImport { picked ->
                val imported = repository.importDocumentFromFile(picked)
                if (imported != null) {
                    activeSection = MeridianSection.READER
                    statusBannerMessage = "Opened \"${imported.title}\" (${imported.totalPages} pages)."
                } else {
                    statusBannerMessage = "Could not load file: $picked"
                }
            }
        }
    } else {
        null
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.paper)
            .testTag("meridian_root"),
    ) {
        val isMobileKindleView = forceMobileKindleLayout ?: (maxWidth < 760.dp)

        if (isMobileKindleView && activeSection == MeridianSection.READER) {
            // =========================================================================
            // STITCH MOBILE DESIGN: "Lector Kindle Inmersivo EPUB & PDF"
            // =========================================================================
            StitchMobileKindleReaderLayout(
                palette = palette,
                theme = state.theme,
                viewMode = state.viewMode,
                fontSizePt = state.fontSizePt,
                lineSpacingMultiplier = state.lineSpacingMultiplier,
                book = currentBook,
                prediction = prediction,
                activeReminder = activeReminder,
                savedPhrases = bookSavedPhrases,
                isTimerRunning = isTimerRunning,
                statusBannerMessage = statusBannerMessage,
                onDismissStatus = { statusBannerMessage = null },
                onPageChange = { repository.goToPage(currentBook.id, it) },
                onSelectTheme = { repository.setTheme(it) },
                onToggleTheme = { repository.toggleTheme() },
                onSelectViewMode = { repository.setViewMode(it) },
                onFontSizeChange = { repository.setFontSizePt(it) },
                onLineSpacingChange = { repository.setLineSpacingMultiplier(it) },
                onToggleTimer = { isTimerRunning = !isTimerRunning },
                onToggleBookmarkHere = {
                    val existing = state.reminders[currentBook.id]
                    if (existing != null && existing.pageNumber == currentBook.currentPage) {
                        repository.clearPageReminder(currentBook.id)
                        statusBannerMessage = "Marcador eliminado en pág. ${currentBook.currentPage}."
                    } else {
                        repository.setPageReminder(
                            bookId = currentBook.id,
                            pageNumber = currentBook.currentPage,
                            note = "Continuar lectura en Pág. ${currentBook.currentPage}: ${
                                currentBook.pages.find { it.pageNumber == currentBook.currentPage }?.chapterTitle ?: ""
                            }",
                            targetLabel = "Próxima Sesión",
                        )
                        statusBannerMessage = "Pág. ${currentBook.currentPage} guardada en tu cinta marcapáginas (栞)."
                    }
                },
                onQuickSavePhrase = { phrase, contextBlock ->
                    val saved = repository.saveWordOrPhrase(
                        bookId = currentBook.id,
                        pageNumber = currentBook.currentPage,
                        phrase = phrase,
                        contextSentence = contextBlock,
                        note = "Marcado en ${currentBook.title} (Pág. ${currentBook.currentPage})",
                        tone = quickMarkTone,
                    )
                    if (saved != null) {
                        statusBannerMessage = "Frase guardada: \"${saved.phrase}\" (Pág. ${saved.pageNumber})."
                    }
                },
                onSelectSection = { activeSection = it },
                onOpenFileDialog = onOpenFileDialog,
                onSwitchToDesktopView = { forceMobileKindleLayout = false },
            )
        } else {
            // =========================================================================
            // STITCH DESKTOP DESIGN: "Lector Inmersivo Escritorio (Serene Vintage Reader)"
            // =========================================================================
            Column(modifier = Modifier.fillMaxSize()) {
                // 1. TOP STITCH HEADER BAR (Hidden only when Zen Fullscreen is active)
                if (!isZenFullscreen) {
                    StitchDesktopTopNavigationBar(
                        palette = palette,
                        theme = state.theme,
                        viewMode = state.viewMode,
                        activeSection = activeSection,
                        currentBook = currentBook,
                        activeReminder = activeReminder,
                        fontSizePt = state.fontSizePt,
                        lineSpacingMultiplier = state.lineSpacingMultiplier,
                        isMobileOverride = forceMobileKindleLayout == true,
                        onSelectSection = { activeSection = it },
                        onSelectViewMode = { repository.setViewMode(it) },
                        onSelectTheme = { repository.setTheme(it) },
                        onToggleTheme = { repository.toggleTheme() },
                        onFontSizeChange = { repository.setFontSizePt(it) },
                        onLineSpacingChange = { repository.setLineSpacingMultiplier(it) },
                        onToggleReminderEditor = { showReminderEditor = !showReminderEditor },
                        onToggleQuickMark = { showQuickMarkBar = !showQuickMarkBar },
                        onToggleMobilePreview = {
                            forceMobileKindleLayout = if (forceMobileKindleLayout == true) false else true
                            activeSection = MeridianSection.READER
                        },
                        onToggleZenFullscreen = { isZenFullscreen = !isZenFullscreen },
                        onOpenFileDialog = onOpenFileDialog,
                    )
                } else {
                    // Minimal Zen exit strip at top right
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(palette.paper)
                            .padding(horizontal = 24.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "shiorifolio · ${currentBook.title} (p. ${currentBook.currentPage} / ${currentBook.totalPages})",
                            color = palette.muted,
                            fontSize = 11.5.sp,
                            fontFamily = FontFamily.Serif,
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(palette.pillBg)
                                .clickable { isZenFullscreen = false }
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                        ) {
                            Text("Salir de Pantalla Completa (Esc)", color = palette.ink, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }

                // Optional status feedback banner
                if (statusBannerMessage != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(palette.pillBg)
                            .padding(horizontal = 24.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = statusBannerMessage ?: "",
                            color = palette.ink,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                        )
                        Text(
                            text = "Cerrar",
                            color = palette.muted,
                            fontSize = 11.5.sp,
                            modifier = Modifier.clickable { statusBannerMessage = null },
                        )
                    }
                }

                // 2. PAGE REMINDER BANNER ("Continue Where You Left Off")
                if (!isZenFullscreen && (activeReminder != null || showReminderEditor)) {
                    PageReminderBanner(
                        palette = palette,
                        currentBook = currentBook,
                        reminder = activeReminder,
                        showEditor = showReminderEditor,
                        onJumpToReminder = { pageNum ->
                            repository.goToPage(currentBook.id, pageNum)
                            activeSection = MeridianSection.READER
                            statusBannerMessage = "Saltaste a la página $pageNum desde tu recordatorio."
                        },
                        onSaveReminder = { pageNum, note, targetLabel ->
                            repository.setPageReminder(currentBook.id, pageNum, note, targetLabel)
                            showReminderEditor = false
                            statusBannerMessage = "Recordatorio guardado para la página $pageNum ($targetLabel)."
                        },
                        onClearReminder = {
                            repository.clearPageReminder(currentBook.id)
                            showReminderEditor = false
                        },
                        onToggleEditor = { showReminderEditor = !showReminderEditor },
                    )
                }

                // 3. QUICK MARK & SAVE WORD / PHRASE BAR
                if (showQuickMarkBar) {
                    QuickMarkPhraseBar(
                        palette = palette,
                        currentBook = currentBook,
                        phrase = quickMarkPhrase,
                        note = quickMarkNote,
                        tone = quickMarkTone,
                        onPhraseChange = { quickMarkPhrase = it },
                        onNoteChange = { quickMarkNote = it },
                        onToneChange = { quickMarkTone = it },
                        onSave = {
                            val pageObj = currentBook.pages.find { it.pageNumber == currentBook.currentPage }
                            val context = pageObj?.blocks?.firstOrNull {
                                it.text.contains(quickMarkPhrase.trim(), ignoreCase = true)
                            }?.text ?: pageObj?.blocks?.firstOrNull()?.text ?: quickMarkPhrase
                            val saved = repository.saveWordOrPhrase(
                                bookId = currentBook.id,
                                pageNumber = currentBook.currentPage,
                                phrase = quickMarkPhrase,
                                contextSentence = context,
                                note = quickMarkNote,
                                tone = quickMarkTone,
                            )
                            if (saved != null) {
                                statusBannerMessage = "Marcado y guardado: \"${saved.phrase}\" en pág. ${saved.pageNumber}."
                                quickMarkPhrase = ""
                                quickMarkNote = ""
                                showQuickMarkBar = false
                            }
                        },
                        onClose = { showQuickMarkBar = false },
                    )
                }

                // 4. MAIN CONTENT WORKSPACE
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                ) {
                    when (activeSection) {
                        MeridianSection.READER -> StitchDesktopReaderSpreadWorkspace(
                            palette = palette,
                            theme = state.theme,
                            book = currentBook,
                            viewMode = state.viewMode,
                            fontSizePt = state.fontSizePt,
                            lineSpacingMultiplier = state.lineSpacingMultiplier,
                            prediction = prediction,
                            activeReminder = activeReminder,
                            savedPhrases = bookSavedPhrases,
                            isTimerRunning = isTimerRunning,
                            onPageChange = { targetPage -> repository.goToPage(currentBook.id, targetPage) },
                            onSelectViewMode = { repository.setViewMode(it) },
                            onSelectTheme = { repository.setTheme(it) },
                            onFontSizeChange = { repository.setFontSizePt(it) },
                            onLineSpacingChange = { repository.setLineSpacingMultiplier(it) },
                            onToggleTimer = { isTimerRunning = !isTimerRunning },
                            onQuickSavePhrase = { wordOrSentence, contextBlock ->
                                quickMarkPhrase = wordOrSentence
                                quickMarkNote = "Marcado en ${currentBook.title} (Pág. ${currentBook.currentPage})"
                                val saved = repository.saveWordOrPhrase(
                                    bookId = currentBook.id,
                                    pageNumber = currentBook.currentPage,
                                    phrase = wordOrSentence,
                                    contextSentence = contextBlock,
                                    note = quickMarkNote,
                                    tone = quickMarkTone,
                                )
                                if (saved != null) {
                                    statusBannerMessage = "Guardado en tu cuaderno: \"${saved.phrase}\"."
                                }
                            },
                            onSetReminderHere = {
                                repository.setPageReminder(
                                    bookId = currentBook.id,
                                    pageNumber = currentBook.currentPage,
                                    note = "Continuar en Pág. ${currentBook.currentPage}: ${
                                        currentBook.pages.find { it.pageNumber == currentBook.currentPage }?.chapterTitle ?: ""
                                    }",
                                    targetLabel = "Próxima Sesión",
                                )
                                statusBannerMessage = "Página ${currentBook.currentPage} marcada con la cinta Shiori (栞)."
                            },
                            onSwitchToEditor = { activeSection = MeridianSection.EDITOR },
                            onOpenNotebook = { activeSection = MeridianSection.LEXICON },
                        )

                        MeridianSection.EDITOR -> DocumentEditorWorkspaceView(
                            palette = palette,
                            book = currentBook,
                            onUpdatePage = { pageNo, chapterTitle, blocks ->
                                repository.updatePageContent(currentBook.id, pageNo, chapterTitle, blocks)
                                statusBannerMessage = "Cambios guardados en pág. $pageNo · Predicción recalculada."
                            },
                            onUpdateMetadata = { title, subtitle, author, format ->
                                repository.updateDocumentMetadata(currentBook.id, title, subtitle, author, format)
                                statusBannerMessage = "Metadatos actualizados ($title · ${format.label})."
                            },
                            onAddPage = { afterPage, title ->
                                repository.addNewPage(currentBook.id, afterPage, title)
                                statusBannerMessage = "Nueva página insertada después de pág. $afterPage."
                            },
                            onDeletePage = { pageNo ->
                                repository.deletePage(currentBook.id, pageNo)
                                statusBannerMessage = "Página $pageNo eliminada."
                            },
                            onPageChange = { repository.goToPage(currentBook.id, it) },
                        )

                        MeridianSection.LEXICON -> SavedPhrasesVaultView(
                            palette = palette,
                            allPhrases = state.savedPhrases,
                            currentBook = currentBook,
                            onSaveNewPhrase = { phrase, note, tone ->
                                val pageObj = currentBook.pages.find { it.pageNumber == currentBook.currentPage }
                                repository.saveWordOrPhrase(
                                    bookId = currentBook.id,
                                    pageNumber = currentBook.currentPage,
                                    phrase = phrase,
                                    contextSentence = pageObj?.blocks?.firstOrNull()?.text ?: phrase,
                                    note = note,
                                    tone = tone,
                                )
                                statusBannerMessage = "Añadido \"$phrase\" a Palabras y Frases Guardadas."
                            },
                            onJumpToPhrase = { phrase ->
                                repository.selectBook(phrase.bookId)
                                repository.goToPage(phrase.bookId, phrase.pageNumber)
                                activeSection = MeridianSection.READER
                            },
                            onDeletePhrase = { repository.deleteSavedPhrase(it) },
                        )

                        MeridianSection.PREDICTION -> ReadingAnalyticsAndPredictionView(
                            palette = palette,
                            book = currentBook,
                            telemetry = bookTelemetry,
                            prediction = prediction,
                            onSimulateReadingBurst = { seconds ->
                                repository.recordReadingTimeTick(currentBook.id, seconds)
                                statusBannerMessage = "Muestra de +${seconds.toInt()}s registrada — predicción recalculada."
                            },
                            onJumpToPage = { pageNo ->
                                repository.goToPage(currentBook.id, pageNo)
                                activeSection = MeridianSection.READER
                            },
                        )

                        MeridianSection.LIBRARY -> LibraryAndFileIoView(
                            palette = palette,
                            documents = state.documents,
                            selectedBookId = currentBook.id,
                            reminders = state.reminders,
                            telemetryMap = state.telemetry,
                            onSelectBook = { bookId ->
                                repository.selectBook(bookId)
                                activeSection = MeridianSection.READER
                            },
                            onCreateDocument = { title, author, format ->
                                val created = repository.createNewDocument(title, author, format)
                                statusBannerMessage = "Nuevo ${format.label} creado: \"${created.title}\"."
                                activeSection = MeridianSection.EDITOR
                            },
                            onImportFromPath = { path ->
                                val imported = repository.importDocumentFromFile(path)
                                if (imported != null) {
                                    statusBannerMessage = "Importado \"${imported.title}\" (${imported.totalPages} páginas)."
                                    activeSection = MeridianSection.READER
                                } else {
                                    statusBannerMessage = "No se pudo leer el archivo: $path"
                                }
                            },
                            onExportToPath = { bookId, targetPath ->
                                val ok = repository.exportDocumentToFile(bookId, targetPath)
                                statusBannerMessage = if (ok) {
                                    "Documento exportado a $targetPath"
                                } else {
                                    "Error al exportar a $targetPath"
                                }
                            },
                            onPickFileToImport = onPickFileToImport,
                            onPickFileToExport = onPickFileToExport,
                        )
                    }
                }

                // 5. STITCH BOTTOM STATUS STREAM BAR + MERIDIAN VIADUCT FOOTER
                if (!isZenFullscreen) {
                    val currentPageObj = currentBook.pages.find { it.pageNumber == currentBook.currentPage } ?: currentBook.pages.first()
                    StitchDesktopStatusStreamBar(
                        palette = palette,
                        book = currentBook,
                        currentChapter = currentPageObj.chapterTitle,
                        prediction = prediction,
                        isTimerRunning = isTimerRunning,
                        onPrevPage = {
                            val step = if (state.viewMode == PageViewMode.TWO_COLUMNS) 2 else 1
                            repository.goToPage(currentBook.id, (currentBook.currentPage - step).coerceAtLeast(1))
                        },
                        onNextPage = {
                            val step = if (state.viewMode == PageViewMode.TWO_COLUMNS) 2 else 1
                            repository.goToPage(currentBook.id, (currentBook.currentPage + step).coerceAtMost(currentBook.totalPages))
                        },
                    )
                }
            }
        }
    }
}

// =========================================================================================
// 1. STITCH DESKTOP TOP NAVIGATION BAR ("Serene Vintage Reader — Lector Inmersivo Escritorio")
// =========================================================================================
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StitchDesktopTopNavigationBar(
    palette: MeridianPalette,
    theme: ReaderTheme,
    viewMode: PageViewMode,
    activeSection: MeridianSection,
    currentBook: BookDocument,
    activeReminder: PageReminder?,
    fontSizePt: Float,
    lineSpacingMultiplier: Float,
    isMobileOverride: Boolean,
    onSelectSection: (MeridianSection) -> Unit,
    onSelectViewMode: (PageViewMode) -> Unit,
    onSelectTheme: (ReaderTheme) -> Unit,
    onToggleTheme: () -> Unit,
    onFontSizeChange: (Float) -> Unit,
    onLineSpacingChange: (Float) -> Unit,
    onToggleReminderEditor: () -> Unit,
    onToggleQuickMark: () -> Unit,
    onToggleMobilePreview: () -> Unit,
    onToggleZenFullscreen: () -> Unit,
    onOpenFileDialog: (() -> Unit)?,
) {
    var showTypographyPopover by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(palette.paper.copy(alpha = 0.96f)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Left: Brand Logo + "shiorifolio" + "Archive & Press" + Active Book Pill
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f, fill = false),
            ) {
                MeridianCompassMark(
                    inkColor = palette.accentGreen,
                    paperColor = palette.paper,
                    size = 36.dp,
                )
                Column {
                    Text(
                        text = "shiorifolio",
                        color = palette.ink,
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 20.sp,
                        letterSpacing = (-0.4).sp,
                    )
                    Text(
                        text = "ARCHIVE & PRESS · 栞",
                        color = palette.muted,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 1.6.sp,
                    )
                }

                Box(
                    modifier = Modifier
                        .height(26.dp)
                        .width(1.dp)
                        .background(palette.border),
                )

                // Current Book Format Badge + Title + Page Counter
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(palette.pillBg)
                            .border(1.dp, palette.border, RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    ) {
                        Text(
                            text = currentBook.format.name,
                            color = palette.accentGreen,
                            fontSize = 9.5.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Text(
                        text = currentBook.title,
                        color = palette.ink,
                        fontFamily = FontFamily.Serif,
                        fontStyle = FontStyle.Italic,
                        fontSize = 13.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 250.dp),
                    )
                    Text(
                        text = "p. ${currentBook.currentPage} / ${currentBook.totalPages}",
                        color = palette.muted,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.5.sp,
                    )
                }
            }

            // Center: Section Navigation Tabs (Reader, Archive, Notebook, Typography & Editor, Telemetry)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(18.dp),
                modifier = Modifier.padding(horizontal = 12.dp),
            ) {
                MeridianSection.entries.forEach { section ->
                    val isSelected = section == activeSection
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .clickable { onSelectSection(section) }
                            .padding(vertical = 4.dp, horizontal = 2.dp),
                    ) {
                        Text(
                            text = section.stitchTabTitle,
                            color = if (isSelected) palette.accentGreen else palette.inkSoft,
                            fontFamily = FontFamily.Serif,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            fontSize = 13.5.sp,
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Box(
                            modifier = Modifier
                                .height(2.dp)
                                .width(if (isSelected) 32.dp else 0.dp)
                                .background(palette.accentGreen),
                        )
                    }
                }
            }

            // Right: Action Controls (Open File, Typography A±, Bookmark Ribbon, Mark Phrase, Theme, Kindle Mobile Switch, Fullscreen)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (onOpenFileDialog != null) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(7.dp))
                            .background(palette.accentGreen)
                            .clickable { onOpenFileDialog() }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                    ) {
                        Text(
                            text = "Abrir .PDF/.EPUB",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }

                // Format Size / Typography Quick Popover Trigger
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(7.dp))
                        .background(if (showTypographyPopover) palette.ink else palette.pillBg)
                        .border(1.dp, palette.border, RoundedCornerShape(7.dp))
                        .clickable { showTypographyPopover = !showTypographyPopover }
                        .padding(horizontal = 9.dp, vertical = 5.dp),
                ) {
                    Text(
                        text = "Aa ${fontSizePt.toInt()}pt",
                        color = if (showTypographyPopover) palette.paper else palette.ink,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }

                // Bookmark Reminder Toggle
                val hasReminder = activeReminder != null
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(7.dp))
                        .background(if (hasReminder) palette.warningAmber.copy(alpha = 0.16f) else palette.pillBg)
                        .border(1.dp, if (hasReminder) palette.warningAmber else palette.border, RoundedCornerShape(7.dp))
                        .clickable { onToggleReminderEditor() }
                        .padding(horizontal = 9.dp, vertical = 5.dp)
                        .testTag("open_reminder_btn"),
                ) {
                    Text(
                        text = "栞 Marcapáginas",
                        color = if (hasReminder) palette.warningAmber else palette.ink,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }

                // Mark Phrase Quick Bar Toggle
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(7.dp))
                        .background(palette.pillBg)
                        .border(1.dp, palette.border, RoundedCornerShape(7.dp))
                        .clickable { onToggleQuickMark() }
                        .padding(horizontal = 9.dp, vertical = 5.dp)
                        .testTag("open_mark_phrase_btn"),
                ) {
                    Text(
                        text = "+ Anotar Frase",
                        color = palette.ink,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }

                // White / Dark Theme Toggle (preserves testTag("theme_toggle_button"))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(7.dp))
                        .background(palette.elevatedSurface)
                        .border(1.dp, palette.border, RoundedCornerShape(7.dp))
                        .clickable { onToggleTheme() }
                        .padding(horizontal = 9.dp, vertical = 5.dp)
                        .testTag("theme_toggle_button"),
                ) {
                    Text(
                        text = "◐ ${theme.stitchLabel}",
                        color = palette.ink,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }

                // Mobile Kindle View Preview Toggle
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(7.dp))
                        .background(if (isMobileOverride) palette.accentGreen else palette.pillBg)
                        .border(1.dp, palette.border, RoundedCornerShape(7.dp))
                        .clickable { onToggleMobilePreview() }
                        .padding(horizontal = 9.dp, vertical = 5.dp),
                ) {
                    Text(
                        text = "📱 Kindle Móvil",
                        color = if (isMobileOverride) Color.White else palette.ink,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }

                // Fullscreen Zen Toggle
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(7.dp))
                        .background(palette.pillBg)
                        .border(1.dp, palette.border, RoundedCornerShape(7.dp))
                        .clickable { onToggleZenFullscreen() }
                        .padding(horizontal = 8.dp, vertical = 5.dp),
                ) {
                    Text("⛶", color = palette.ink, fontSize = 12.sp)
                }
            }
        }

        // Expandable Quick Typography & Screen Tone Strip (when "Aa" button clicked in header)
        AnimatedVisibility(visible = showTypographyPopover) {
            Surface(
                color = palette.elevatedSurface,
                border = BorderStroke(1.dp, palette.border),
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
                        Text("Tono de Papel:", color = palette.muted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        ReaderTheme.entries.forEach { t ->
                            val sel = t == theme
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (sel) palette.ink else palette.pageSurface)
                                    .border(1.dp, palette.border, RoundedCornerShape(6.dp))
                                    .clickable { onSelectTheme(t) }
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(12.dp)
                                        .clip(CircleShape)
                                        .background(Color(t.swatchHex))
                                        .border(1.dp, palette.muted, CircleShape),
                                )
                                Text(
                                    text = t.stitchLabel,
                                    color = if (sel) palette.paper else palette.ink,
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
                        Text("Vista:", color = palette.muted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        PageViewMode.entries.forEach { m ->
                            val sel = m == viewMode
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(5.dp))
                                    .background(if (sel) palette.accentGreen else palette.pageSurface)
                                    .border(1.dp, palette.border, RoundedCornerShape(5.dp))
                                    .clickable { onSelectViewMode(m) }
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                            ) {
                                Text(
                                    text = m.stitchLabel,
                                    color = if (sel) Color.White else palette.ink,
                                    fontSize = 11.sp,
                                )
                            }
                        }
                    }

                    // Font Size & Line Spacing Controls
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text("Cuerpo:", color = palette.muted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(palette.pageSurface)
                                .border(1.dp, palette.border, RoundedCornerShape(4.dp))
                                .clickable { onFontSizeChange(fontSizePt - 1f) }
                                .padding(horizontal = 8.dp, vertical = 3.dp),
                        ) {
                            Text("A-", color = palette.ink, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                        Text("${fontSizePt.toInt()}pt", color = palette.ink, fontFamily = FontFamily.Monospace, fontSize = 11.5.sp)
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(palette.pageSurface)
                                .border(1.dp, palette.border, RoundedCornerShape(4.dp))
                                .clickable { onFontSizeChange(fontSizePt + 1f) }
                                .padding(horizontal = 8.dp, vertical = 3.dp),
                        ) {
                            Text("A+", color = palette.ink, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Interlineado:", color = palette.muted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        listOf(1.5f, 1.8f, 2.1f).forEach { mult ->
                            val sel = kotlin.math.abs(lineSpacingMultiplier - mult) < 0.1f
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(if (sel) palette.accentGreen else palette.pageSurface)
                                    .border(1.dp, palette.border, RoundedCornerShape(4.dp))
                                    .clickable { onLineSpacingChange(mult) }
                                    .padding(horizontal = 7.dp, vertical = 3.dp),
                            ) {
                                Text(
                                    text = "$mult",
                                    color = if (sel) Color.White else palette.ink,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 10.5.sp,
                                )
                            }
                        }
                    }
                }
            }
        }

        HorizontalDivider(color = palette.border, thickness = 1.dp)
    }
}

// =========================================================================================
// 2. STITCH DESKTOP READER WORKSPACE: BOOK SPREAD + SWALLOWTAIL RIBBON + COMMAND DOCK
// =========================================================================================
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StitchDesktopReaderSpreadWorkspace(
    palette: MeridianPalette,
    theme: ReaderTheme,
    book: BookDocument,
    viewMode: PageViewMode,
    fontSizePt: Float,
    lineSpacingMultiplier: Float,
    prediction: FinishTimePrediction,
    activeReminder: PageReminder?,
    savedPhrases: List<SavedPhrase>,
    isTimerRunning: Boolean,
    onPageChange: (Int) -> Unit,
    onSelectViewMode: (PageViewMode) -> Unit,
    onSelectTheme: (ReaderTheme) -> Unit,
    onFontSizeChange: (Float) -> Unit,
    onLineSpacingChange: (Float) -> Unit,
    onToggleTimer: () -> Unit,
    onQuickSavePhrase: (phrase: String, context: String) -> Unit,
    onSetReminderHere: () -> Unit,
    onSwitchToEditor: () -> Unit,
    onOpenNotebook: () -> Unit,
) {
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
            .padding(horizontal = 28.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // 2A. AUXILIARY ARCHIVAL UTILITY BAR ("Edición Crítica Folio", Annotated Passages, Total Pages)
        Row(
            modifier = Modifier
                .widthIn(max = 1240.dp)
                .fillMaxWidth()
                .padding(bottom = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = "EDICIÓN CRÍTICA FOLIO",
                    color = palette.muted,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.6.sp,
                )
                Text("•", color = palette.border, fontSize = 10.sp)
                Text(
                    text = currentBookAuthorSubtitle(book),
                    color = palette.inkSoft,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Serif,
                    fontStyle = FontStyle.Italic,
                )
                if (tocEntries.size > 1) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(5.dp))
                            .background(if (showTocBar) palette.accentGreen else palette.pillBg)
                            .border(1.dp, palette.border, RoundedCornerShape(5.dp))
                            .clickable { showTocBar = !showTocBar }
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                    ) {
                        Text(
                            text = if (showTocBar) "Ocultar Capítulos (${tocEntries.size})" else "Capítulos (${tocEntries.size})",
                            color = if (showTocBar) Color.White else palette.ink,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // Annotated passages pill
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(palette.pillBg)
                        .border(1.dp, palette.border, RoundedCornerShape(6.dp))
                        .clickable { onOpenNotebook() }
                        .padding(horizontal = 10.dp, vertical = 3.dp),
                ) {
                    Text(
                        text = "✎ ${savedPhrases.size} pasajes anotados",
                        color = palette.inkSoft,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }

                // Total pages pill
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(palette.pillBg)
                        .border(1.dp, palette.border, RoundedCornerShape(6.dp))
                        .padding(horizontal = 10.dp, vertical = 3.dp),
                ) {
                    Text(
                        text = "▤ ${book.totalPages} páginas totales",
                        color = palette.inkSoft,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                    )
                }

                // Quick Edit Page trigger
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(palette.pillBg)
                        .border(1.dp, palette.border, RoundedCornerShape(6.dp))
                        .clickable { onSwitchToEditor() }
                        .padding(horizontal = 10.dp, vertical = 3.dp),
                ) {
                    Text(
                        text = "Editar Folio",
                        color = palette.accentGreen,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }

        // Optional Expandable Chapter TOC Jump Strip
        if (showTocBar && tocEntries.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .widthIn(max = 1240.dp)
                    .fillMaxWidth()
                    .padding(bottom = 6.dp)
                    .horizontalScroll(rememberScrollState())
                    .background(palette.elevatedSurface, RoundedCornerShape(8.dp))
                    .border(1.dp, palette.border, RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Ir al capítulo:", color = palette.muted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                tocEntries.forEach { entry ->
                    val isCurrentChap = currentPageObj.chapterTitle.startsWith(entry.title)
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(5.dp))
                            .background(if (isCurrentChap) palette.accentGreen else palette.pageSurface)
                            .border(1.dp, palette.border, RoundedCornerShape(5.dp))
                            .clickable { onPageChange(entry.startPage) }
                            .padding(horizontal = 9.dp, vertical = 4.dp),
                    ) {
                        Text(
                            text = "${entry.title.take(36)} (p.${entry.startPage})",
                            color = if (isCurrentChap) Color.White else palette.ink,
                            fontSize = 11.sp,
                        )
                    }
                }
            }
        }

        // 2B. BOOK OPEN SPREAD SHELL WITH DRAPED SWALLOWTAIL RIBBON BOOKMARK
        Box(
            modifier = Modifier
                .weight(1f)
                .widthIn(max = if (viewMode == PageViewMode.ONE_COLUMN) 820.dp else 1240.dp)
                .fillMaxWidth(),
            contentAlignment = Alignment.TopCenter,
        ) {
            when (viewMode) {
                PageViewMode.TWO_COLUMNS -> {
                    // Dual-Page Open Book Spread inside a unified rounded-xl card with center spine crease
                    Surface(
                        color = palette.pageSurface,
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, palette.border),
                        modifier = Modifier
                            .fillMaxSize()
                            .shadow(elevation = 6.dp, shape = RoundedCornerShape(14.dp)),
                    ) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            Row(modifier = Modifier.fillMaxSize()) {
                                // Left Page (Verso)
                                StitchBookSpreadLeaf(
                                    palette = palette,
                                    book = book,
                                    page = currentPageObj,
                                    savedPhrases = savedPhrases,
                                    fontSizeSp = (fontSizePt - 1.2f).coerceAtLeast(13f),
                                    lineHeightSp = (fontSizePt - 1.2f).coerceAtLeast(13f) * lineSpacingMultiplier,
                                    isLeftLeaf = true,
                                    onQuickSavePhrase = onQuickSavePhrase,
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight(),
                                )

                                //Right Page (Recto)
                                if (secondPageObj != null) {
                                    StitchBookSpreadLeaf(
                                        palette = palette,
                                        book = book,
                                        page = secondPageObj,
                                        savedPhrases = savedPhrases,
                                        fontSizeSp = (fontSizePt - 1.2f).coerceAtLeast(13f),
                                        lineHeightSp = (fontSizePt - 1.2f).coerceAtLeast(13f) * lineSpacingMultiplier,
                                        isLeftLeaf = false,
                                        onQuickSavePhrase = onQuickSavePhrase,
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxHeight(),
                                    )
                                } else {
                                    // End of Book Colophon Leaf
                                    Column(
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxHeight()
                                            .padding(36.dp),
                                        verticalArrangement = Arrangement.Center,
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                    ) {
                                        MeridianCompassMark(
                                            inkColor = palette.accentGreen,
                                            paperColor = palette.pageSurface,
                                            size = 52.dp,
                                        )
                                        Spacer(modifier = Modifier.height(14.dp))
                                        Text(
                                            text = "EXPLICIT LIBER · FIN DEL VOLUMEN",
                                            color = palette.muted,
                                            fontSize = 10.sp,
                                            fontFamily = FontFamily.Monospace,
                                            letterSpacing = 2.sp,
                                        )
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = book.title,
                                            color = palette.ink,
                                            fontFamily = FontFamily.Serif,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 20.sp,
                                            textAlign = TextAlign.Center,
                                        )
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = "Has alcanzado el último folio (${book.totalPages} de ${book.totalPages}).",
                                            color = palette.inkSoft,
                                            fontSize = 13.sp,
                                            textAlign = TextAlign.Center,
                                        )
                                    }
                                }
                            }

                            // Center Book Crease & Spine Shadow Gradient (exact Stitch book-crease-left / book-crease-right)
                            Box(
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .width(44.dp)
                                    .fillMaxHeight()
                                    .background(
                                        Brush.horizontalGradient(
                                            colors = listOf(
                                                Color.Transparent,
                                                palette.ink.copy(alpha = if (palette.isDark) 0.22f else 0.06f),
                                                palette.ink.copy(alpha = if (palette.isDark) 0.34f else 0.12f),
                                                palette.ink.copy(alpha = if (palette.isDark) 0.22f else 0.06f),
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
                                        .background(palette.border),
                                )
                            }
                        }
                    }
                }

                PageViewMode.ONE_COLUMN -> {
                    Surface(
                        color = palette.pageSurface,
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, palette.border),
                        modifier = Modifier
                            .fillMaxSize()
                            .shadow(elevation = 5.dp, shape = RoundedCornerShape(14.dp)),
                    ) {
                        StitchBookSpreadLeaf(
                            palette = palette,
                            book = book,
                            page = currentPageObj,
                            savedPhrases = savedPhrases,
                            fontSizeSp = fontSizePt,
                            lineHeightSp = fontSizePt * lineSpacingMultiplier,
                            isLeftLeaf = false,
                            onQuickSavePhrase = onQuickSavePhrase,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }

                PageViewMode.STRETCH -> {
                    Surface(
                        color = palette.pageSurface,
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, palette.border),
                        modifier = Modifier
                            .fillMaxSize()
                            .shadow(elevation = 5.dp, shape = RoundedCornerShape(14.dp)),
                    ) {
                        StitchBookSpreadLeaf(
                            palette = palette,
                            book = book,
                            page = currentPageObj,
                            savedPhrases = savedPhrases,
                            fontSizeSp = fontSizePt + 2f,
                            lineHeightSp = (fontSizePt + 2f) * lineSpacingMultiplier,
                            isLeftLeaf = false,
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
                            color = palette.pageSurface,
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, palette.border),
                            modifier = Modifier
                                .weight(0.72f)
                                .fillMaxHeight()
                                .shadow(elevation = 5.dp, shape = RoundedCornerShape(14.dp)),
                        ) {
                            StitchBookSpreadLeaf(
                                palette = palette,
                                book = book,
                                page = currentPageObj,
                                savedPhrases = savedPhrases,
                                fontSizeSp = fontSizePt + 0.5f,
                                lineHeightSp = (fontSizePt + 0.5f) * lineSpacingMultiplier,
                                isLeftLeaf = false,
                                onQuickSavePhrase = onQuickSavePhrase,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }

                        // Side Marginalia & Telemetry Card in Wide Mode
                        Surface(
                            color = palette.pageSurface,
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, palette.border),
                            modifier = Modifier
                                .weight(0.28f)
                                .fillMaxHeight()
                                .shadow(elevation = 4.dp, shape = RoundedCornerShape(14.dp)),
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(20.dp)
                                    .verticalScroll(rememberScrollState()),
                            ) {
                                Text(
                                    text = "MARGINALIA & RITMO",
                                    color = palette.muted,
                                    fontSize = 10.5.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.4.sp,
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Restante: ${prediction.formattedRemainingTime()}",
                                    color = palette.ink,
                                    fontFamily = FontFamily.Serif,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 16.sp,
                                )
                                Text(
                                    text = "Cadencia: ${prediction.formattedPacePerPage()} (${prediction.formattedWpm()})",
                                    color = palette.inkSoft,
                                    fontSize = 12.sp,
                                )
                                Spacer(modifier = Modifier.height(14.dp))
                                HorizontalDivider(color = palette.border)
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "NOTAS EN ESTE FOLIO",
                                    color = palette.muted,
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
                                        color = palette.muted,
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
                                                    color = palette.ink,
                                                    fontFamily = FontFamily.Serif,
                                                    fontWeight = FontWeight.SemiBold,
                                                    fontSize = 12.5.sp,
                                                )
                                                if (sp.note.isNotBlank()) {
                                                    Text(
                                                        text = sp.note,
                                                        color = palette.inkSoft,
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

            // Draped Top-Right Swallowtail Bookmark Ribbon (Shiori 栞)
            val isThisPageBookmarked = activeReminder?.pageNumber == currentPageObj.pageNumber ||
                (secondPageObj != null && activeReminder?.pageNumber == secondPageObj.pageNumber)
            SwallowtailBookmarkRibbon(
                isActive = isThisPageBookmarked,
                ribbonColor = palette.warningAmber,
                width = 30.dp,
                height = 60.dp,
                onClick = onSetReminderHere,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = 36.dp)
                    .offset(y = (-4).dp),
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // 2C. FLOATING READER COMMAND DOCK (Stitch Bottom Command Bar)
        StitchFloatingCommandDock(
            palette = palette,
            theme = theme,
            viewMode = viewMode,
            fontSizePt = fontSizePt,
            lineSpacingMultiplier = lineSpacingMultiplier,
            book = book,
            secondPageNumber = secondPageObj?.pageNumber,
            prediction = prediction,
            isTimerRunning = isTimerRunning,
            onPageChange = onPageChange,
            onSelectViewMode = onSelectViewMode,
            onSelectTheme = onSelectTheme,
            onFontSizeChange = onFontSizeChange,
            onLineSpacingChange = onLineSpacingChange,
            onToggleTimer = onToggleTimer,
        )
    }
}

private fun currentBookAuthorSubtitle(book: BookDocument): String =
    if (book.subtitle.isNotBlank()) "${book.author} · ${book.subtitle}" else book.author

/**
 * Individual Book Leaf inside the Stitch Open Book Spread.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StitchBookSpreadLeaf(
    palette: MeridianPalette,
    book: BookDocument,
    page: BookPage,
    savedPhrases: List<SavedPhrase>,
    fontSizeSp: Float,
    lineHeightSp: Float,
    isLeftLeaf: Boolean,
    stretchVertically: Boolean = false,
    onQuickSavePhrase: (phrase: String, context: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val pagePhrases = remember(savedPhrases, page.pageNumber) {
        savedPhrases.filter { it.pageNumber == page.pageNumber }
    }

    Column(
        modifier = modifier
            .padding(
                start = if (isLeftLeaf) 36.dp else 32.dp,
                end = if (isLeftLeaf) 32.dp else 36.dp,
                top = 22.dp,
                bottom = 16.dp,
            ),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        // 1. Running Archival Header
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (isLeftLeaf) book.title.uppercase() else page.chapterTitle.uppercase(),
                    color = palette.muted,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 1.6.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "FOLIO ${page.pageNumber}",
                    color = palette.muted,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(color = palette.border.copy(alpha = 0.7f), thickness = 1.dp)
        }

        // 2. Scrollable Literary Body & Illustrations
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(vertical = 12.dp),
        ) {
            SelectionContainer {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = if (stretchVertically) Arrangement.SpaceBetween else Arrangement.spacedBy(14.dp),
                ) {
                    page.blocks.forEachIndexed { idx, block ->
                        val imagePath = block.imagePath
                        if (block.style == BlockStyle.IMAGE && imagePath != null) {
                            val bmp = rememberLoadedImageBitmap(
                                path = imagePath,
                                bookFilePath = book.filePath,
                                bookId = book.id,
                                pageNumber = page.pageNumber,
                            )
                            if (bmp != null) {
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    Image(
                                        bitmap = bmp,
                                        contentDescription = block.text.ifBlank { "Book Illustration" },
                                        contentScale = ContentScale.Fit,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(min = 200.dp, max = 520.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .border(1.dp, palette.border, RoundedCornerShape(8.dp)),
                                    )
                                    if (block.text.isNotBlank()) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = block.text,
                                            color = palette.muted,
                                            fontSize = 11.sp,
                                            fontStyle = FontStyle.Italic,
                                        )
                                    }
                                }
                            }
                        } else {
                            val annotated = remember(block.text, pagePhrases, palette.isDark) {
                                buildHighlightedAnnotatedString(block.text, pagePhrases, palette)
                            }

                            Column {
                                when (block.style) {
                                    BlockStyle.HEADING -> {
                                        Column(modifier = Modifier.padding(bottom = 4.dp)) {
                                            Text(
                                                text = "CAPÍTULO · FOLIO ${page.pageNumber}",
                                                color = palette.warningAmber,
                                                fontSize = 9.5.sp,
                                                fontFamily = FontFamily.Monospace,
                                                letterSpacing = 2.sp,
                                                fontWeight = FontWeight.SemiBold,
                                            )
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = annotated,
                                                color = palette.ink,
                                                fontFamily = FontFamily.Serif,
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = (fontSizeSp + 4.5f).sp,
                                                lineHeight = (lineHeightSp + 4f).sp,
                                            )
                                        }
                                    }

                                    BlockStyle.QUOTE -> {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 4.dp),
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .width(2.5.dp)
                                                    .height(46.dp)
                                                    .background(palette.warningAmber),
                                            )
                                            Spacer(modifier = Modifier.width(14.dp))
                                            Text(
                                                text = annotated,
                                                color = palette.inkSoft,
                                                fontFamily = FontFamily.Serif,
                                                fontStyle = FontStyle.Italic,
                                                fontSize = fontSizeSp.sp,
                                                lineHeight = lineHeightSp.sp,
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
                                                fontSize = (fontSizeSp - 1.5f).sp,
                                                lineHeight = (lineHeightSp - 3f).sp,
                                            )
                                        }
                                    }

                                    BlockStyle.PARAGRAPH, BlockStyle.IMAGE -> {
                                        // Render subtle editorial drop-cap styling on first paragraph of the leaf
                                        Text(
                                            text = annotated,
                                            color = palette.ink,
                                            fontFamily = FontFamily.Serif,
                                            fontSize = fontSizeSp.sp,
                                            lineHeight = lineHeightSp.sp,
                                        )
                                    }
                                }

                                // Interactive Notable Phrase Chips for one-click marking
                                val candidatePhrases = remember(block.text) {
                                    extractCandidatePhrases(block.text)
                                }
                                if (candidatePhrases.isNotEmpty() && block.style != BlockStyle.HEADING && block.text.length >= 50 && idx < 4) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp),
                                    ) {
                                        candidatePhrases.forEach { candidate ->
                                            val alreadySaved = pagePhrases.any {
                                                it.phrase.equals(candidate, ignoreCase = true)
                                            }
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(4.dp))
                                                    .background(
                                                        if (alreadySaved) {
                                                            palette.highlightColor(HighlightTone.SAGE)
                                                        } else {
                                                            palette.pillBg.copy(alpha = 0.7f)
                                                        },
                                                    )
                                                    .clickable { onQuickSavePhrase(candidate, block.text) }
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
                    }
                }
            }
        }

        // 3. Archival Folio Footer ("Folio • M-01" and page number)
        Column(modifier = Modifier.fillMaxWidth()) {
            HorizontalDivider(color = palette.border.copy(alpha = 0.65f), thickness = 1.dp)
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (isLeftLeaf) {
                    Text(
                        text = "${page.pageNumber}",
                        color = palette.ink,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Medium,
                        fontSize = 11.5.sp,
                    )
                    Text(
                        text = "Folio • SF-${page.pageNumber.toString().padStart(2, '0')} (${page.wordCount} palabras)",
                        color = palette.muted,
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
                        color = palette.muted,
                        fontStyle = FontStyle.Italic,
                        fontSize = 10.5.sp,
                    )
                    Text(
                        text = "${page.pageNumber}",
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

/**
 * Floating Reader Command Dock (Stitch Desktop Bottom Control Dock):
 * - Top Row: Prev/Next + `1-2 / 237` counter, `0% — [Interactive Scrubber] — 100%`, and Live `Ritmo` Telemetry Pill
 * - Bottom Row: View Mode Switcher (`Doble página`, `Simple`, `Ancho`, `Stretch`), 4 Circular Tone Swatches, and `A- / 17pt / A+` + Line Spacing controls.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StitchFloatingCommandDock(
    palette: MeridianPalette,
    theme: ReaderTheme,
    viewMode: PageViewMode,
    fontSizePt: Float,
    lineSpacingMultiplier: Float,
    book: BookDocument,
    secondPageNumber: Int?,
    prediction: FinishTimePrediction,
    isTimerRunning: Boolean,
    onPageChange: (Int) -> Unit,
    onSelectViewMode: (PageViewMode) -> Unit,
    onSelectTheme: (ReaderTheme) -> Unit,
    onFontSizeChange: (Float) -> Unit,
    onLineSpacingChange: (Float) -> Unit,
    onToggleTimer: () -> Unit,
) {
    val step = if (viewMode == PageViewMode.TWO_COLUMNS) 2 else 1

    Surface(
        color = palette.paper.copy(alpha = 0.96f),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, palette.border),
        modifier = Modifier
            .widthIn(max = 980.dp)
            .fillMaxWidth()
            .shadow(elevation = 8.dp, shape = RoundedCornerShape(16.dp)),
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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(palette.pillBg)
                            .border(1.dp, palette.border, RoundedCornerShape(8.dp))
                            .clickable(enabled = book.currentPage > 1) {
                                onPageChange((book.currentPage - step).coerceAtLeast(1))
                            }
                            .testTag("prev_page_button"),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("‹", color = palette.ink, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }

                    val pageRangeLabel = if (secondPageNumber != null) {
                        "${book.currentPage}-${secondPageNumber} / ${book.totalPages}"
                    } else {
                        "${book.currentPage} / ${book.totalPages}"
                    }
                    Text(
                        text = pageRangeLabel,
                        color = palette.ink,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )

                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(palette.pillBg)
                            .border(1.dp, palette.border, RoundedCornerShape(8.dp))
                            .clickable(enabled = book.currentPage < book.totalPages) {
                                onPageChange((book.currentPage + step).coerceAtMost(book.totalPages))
                            }
                            .testTag("next_page_button"),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("›", color = palette.ink, fontSize = 16.sp, fontWeight = FontWeight.Bold)
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
                        color = palette.muted,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.5.sp,
                    )
                    Slider(
                        value = book.currentPage.toFloat(),
                        onValueChange = { newVal ->
                            onPageChange(newVal.roundToInt().coerceIn(1, book.totalPages))
                        },
                        valueRange = 1f..book.totalPages.coerceAtLeast(2).toFloat(),
                        colors = SliderDefaults.colors(
                            thumbColor = palette.accentGreen,
                            activeTrackColor = palette.accentGreen,
                            inactiveTrackColor = palette.border,
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(22.dp),
                    )
                    Text(
                        text = "100%",
                        color = palette.muted,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.5.sp,
                    )
                }

                // Live Telemetry Pill ("Ritmo: 7h 4m • 33s/pág (80 WPM)")
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(palette.pillBg)
                        .border(1.dp, palette.border, RoundedCornerShape(8.dp))
                        .clickable { onToggleTimer() }
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                        .testTag("prediction_badge"),
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(if (isTimerRunning) Color(0xFF10B981) else palette.muted)
                            .testTag("reading_timer_toggle"),
                    )
                    Text(
                        text = "Ritmo: ${prediction.formattedRemainingTime()} • ${prediction.estimatedSecondsPerPage.toInt()}s/pág (${prediction.estimatedWordsPerMinute.toInt()} WPM)",
                        color = palette.ink,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                    )
                }
            }

            HorizontalDivider(color = palette.border.copy(alpha = 0.6f), thickness = 1.dp)

            // Bottom Row: View Mode Switcher + 4 Circular Screen Tone Swatches + Typography Micro-Controls
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalArrangement = Arrangement.Center,
            ) {
                // View Mode Switcher (Doble página, Simple, Ancho, Stretch) — preserves testTag("view_mode_${mode.name}")
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(palette.pillBg)
                        .padding(3.dp),
                ) {
                    // Order with Doble página & Simple prominent, all 4 modes present
                    val orderedModes = listOf(
                        PageViewMode.TWO_COLUMNS,
                        PageViewMode.ONE_COLUMN,
                        PageViewMode.WIDE,
                        PageViewMode.STRETCH,
                    )
                    orderedModes.forEach { mode ->
                        val selected = mode == viewMode
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(7.dp))
                                .background(if (selected) palette.accentGreen else Color.Transparent)
                                .clickable { onSelectViewMode(mode) }
                                .padding(horizontal = 10.dp, vertical = 5.dp)
                                .testTag("view_mode_${mode.name}"),
                        ) {
                            Text(
                                text = mode.stitchLabel,
                                color = if (selected) Color.White else palette.inkSoft,
                                fontSize = 11.5.sp,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                            )
                        }
                    }
                }

                // 4 Circular Screen Tone Swatches (Kindle Cálido #FCF9F3, Sepia #F5EDDB, Salvia #EEF3EE, Oscuro #231F1C)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "TONO",
                        color = palette.muted,
                        fontSize = 9.5.sp,
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
                                    color = if (isSel) palette.accentGreen else palette.muted.copy(alpha = 0.5f),
                                    shape = CircleShape,
                                )
                                .clickable { onSelectTheme(t) },
                        )
                    }
                }

                // Typography Micro-Controls (A- / 17pt / A+ and Line Spacing 1.5 / 1.8 / 2.1)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(palette.pillBg)
                            .border(1.dp, palette.border, RoundedCornerShape(8.dp)),
                    ) {
                        Box(
                            modifier = Modifier
                                .clickable { onFontSizeChange(fontSizePt - 1f) }
                                .padding(horizontal = 9.dp, vertical = 4.dp),
                        ) {
                            Text("A-", color = palette.ink, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, fontSize = 11.5.sp)
                        }
                        Text(
                            text = "${fontSizePt.toInt()}pt",
                            color = palette.muted,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.5.sp,
                            modifier = Modifier.padding(horizontal = 4.dp),
                        )
                        Box(
                            modifier = Modifier
                                .clickable { onFontSizeChange(fontSizePt + 1f) }
                                .padding(horizontal = 9.dp, vertical = 4.dp),
                        ) {
                            Text("A+", color = palette.ink, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, fontSize = 11.5.sp)
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
                            .background(palette.pillBg)
                            .border(1.dp, palette.border, RoundedCornerShape(8.dp))
                            .clickable { onLineSpacingChange(nextSpacing) }
                            .padding(horizontal = 9.dp, vertical = 4.dp),
                    ) {
                        Text(
                            text = "↕ ${lineSpacingMultiplier}×",
                            color = palette.ink,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Stitch Desktop Status Stream Bar at the very bottom of the screen.
 */
@Composable
private fun StitchDesktopStatusStreamBar(
    palette: MeridianPalette,
    book: BookDocument,
    currentChapter: String,
    prediction: FinishTimePrediction,
    isTimerRunning: Boolean,
    onPrevPage: () -> Unit,
    onNextPage: () -> Unit,
) {
    Surface(
        color = palette.elevatedSurface,
        border = BorderStroke(1.dp, palette.border),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(if (isTimerRunning) palette.accentGreen else palette.muted),
                    )
                    Text(
                        text = "SHIORIFOLIO READING STREAM",
                        color = palette.muted,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        letterSpacing = 1.2.sp,
                    )
                }
                Text("|", color = palette.border, fontSize = 11.sp)
                Text(
                    text = "Cap.: $currentChapter",
                    color = palette.inkSoft,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 320.dp),
                )
                Text("•", color = palette.border, fontSize = 11.sp)
                Text(
                    text = "Restante: ${prediction.formattedRemainingTime()} (Activo: ${prediction.formattedActiveTime()})",
                    color = palette.inkSoft,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.5.sp,
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(
                    text = "Cadencia: ${prediction.formattedWpm()} (Recálculos: #${prediction.recalculationCount})",
                    color = palette.muted,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.5.sp,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = "‹",
                        color = palette.ink,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable { onPrevPage() }.padding(horizontal = 4.dp),
                    )
                    Text(
                        text = "Pág. ${book.currentPage} de ${book.totalPages}",
                        color = palette.ink,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 10.5.sp,
                    )
                    Text(
                        text = "›",
                        color = palette.ink,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable { onNextPage() }.padding(horizontal = 4.dp),
                    )
                }
            }
        }
    }
}

// =========================================================================================
// 3. STITCH MOBILE KINDLE DESIGN: "Lector Kindle Inmersivo EPUB & PDF"
// =========================================================================================
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StitchMobileKindleReaderLayout(
    palette: MeridianPalette,
    theme: ReaderTheme,
    viewMode: PageViewMode,
    fontSizePt: Float,
    lineSpacingMultiplier: Float,
    book: BookDocument,
    prediction: FinishTimePrediction,
    activeReminder: PageReminder?,
    savedPhrases: List<SavedPhrase>,
    isTimerRunning: Boolean,
    statusBannerMessage: String?,
    onDismissStatus: () -> Unit,
    onPageChange: (Int) -> Unit,
    onSelectTheme: (ReaderTheme) -> Unit,
    onToggleTheme: () -> Unit,
    onSelectViewMode: (PageViewMode) -> Unit,
    onFontSizeChange: (Float) -> Unit,
    onLineSpacingChange: (Float) -> Unit,
    onToggleTimer: () -> Unit,
    onToggleBookmarkHere: () -> Unit,
    onQuickSavePhrase: (phrase: String, context: String) -> Unit,
    onSelectSection: (MeridianSection) -> Unit,
    onOpenFileDialog: (() -> Unit)?,
    onSwitchToDesktopView: () -> Unit,
) {
    val currentPageObj = book.pages.find { it.pageNumber == book.currentPage } ?: book.pages.first()
    val pagePhrases = remember(savedPhrases, currentPageObj.pageNumber) {
        savedPhrases.filter { it.pageNumber == currentPageObj.pageNumber }
    }
    val tocEntries = remember(book.id, book.pages.size) { book.tableOfContents }

    var hudVisible by remember { mutableStateOf(true) }
    var showKindleSettingsSheet by remember { mutableStateOf(false) }
    var showMobileTocSheet by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.paper),
    ) {
        // 1. FULL-BLEED E-INK READING CANVAS
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    top = if (hudVisible) 76.dp else 20.dp,
                    bottom = if (hudVisible) 110.dp else 28.dp,
                    start = 22.dp,
                    end = 22.dp,
                )
                .verticalScroll(rememberScrollState()),
        ) {
            // Ornamental Chapter Header (Stitch Kindle style)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "CAPÍTULO · FOLIO ${currentPageObj.pageNumber}",
                    color = palette.accentGreen,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    letterSpacing = 2.2.sp,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = currentPageObj.chapterTitle,
                    color = palette.ink,
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 19.sp,
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(modifier = Modifier.width(28.dp).height(1.dp).background(palette.border))
                    Box(modifier = Modifier.size(5.dp).background(palette.warningAmber))
                    Box(modifier = Modifier.width(28.dp).height(1.dp).background(palette.border))
                }
            }

            // Book Content Blocks
            SelectionContainer {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    currentPageObj.blocks.forEachIndexed { idx, block ->
                        val imagePath = block.imagePath
                        if (block.style == BlockStyle.IMAGE && imagePath != null) {
                            val bmp = rememberLoadedImageBitmap(
                                path = imagePath,
                                bookFilePath = book.filePath,
                                bookId = book.id,
                                pageNumber = currentPageObj.pageNumber,
                            )
                            if (bmp != null) {
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    Image(
                                        bitmap = bmp,
                                        contentDescription = block.text.ifBlank { "Ilustración" },
                                        contentScale = ContentScale.Fit,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(min = 200.dp, max = 460.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .border(1.dp, palette.border, RoundedCornerShape(8.dp)),
                                    )
                                }
                            }
                        } else {
                            val annotated = remember(block.text, pagePhrases, palette.isDark) {
                                buildHighlightedAnnotatedString(block.text, pagePhrases, palette)
                            }
                            Text(
                                text = annotated,
                                color = palette.ink,
                                fontFamily = FontFamily.Serif,
                                fontSize = fontSizePt.sp,
                                lineHeight = (fontSizePt * lineSpacingMultiplier).sp,
                            )

                            val candidatePhrases = remember(block.text) {
                                extractCandidatePhrases(block.text)
                            }
                            if (candidatePhrases.isNotEmpty() && idx < 3) {
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                    modifier = Modifier.padding(top = 4.dp),
                                ) {
                                    candidatePhrases.forEach { candidate ->
                                        val alreadySaved = pagePhrases.any { it.phrase.equals(candidate, ignoreCase = true) }
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(if (alreadySaved) palette.highlightColor(HighlightTone.SAGE) else palette.pillBg)
                                                .clickable { onQuickSavePhrase(candidate, block.text) }
                                                .padding(horizontal = 6.dp, vertical = 2.dp),
                                        ) {
                                            Text(
                                                text = if (alreadySaved) "✓ $candidate" else "+ $candidate",
                                                color = palette.inkSoft,
                                                fontSize = 10.sp,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }

        // Invisible Center Tap Zone to toggle HUD when HUD is hidden
        if (!hudVisible) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable { hudVisible = true },
            )
        }

        // 2. FULL-BLEED TOP 3DP PROGRESS LINE
        LinearProgressIndicator(
            progress = { prediction.completionFraction },
            color = palette.accentGreen,
            trackColor = palette.border.copy(alpha = 0.6f),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(3.dp),
        )

        // 3. FLOATING TRANSLUCENT TOP HUD (Stitch Kindle Mobile Header)
        AnimatedVisibility(
            visible = hudVisible,
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Surface(
                    color = palette.paper.copy(alpha = 0.94f),
                    border = BorderStroke(1.dp, palette.border),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            // Left: Brand Logo + Format Badge + Book Title + Page/Chapter info
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.weight(1f),
                            ) {
                                MeridianCompassMark(
                                    inkColor = palette.accentGreen,
                                    paperColor = palette.paper,
                                    size = 30.dp,
                                    modifier = Modifier.clickable { onSelectSection(MeridianSection.LIBRARY) },
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    ) {
                                        Text(
                                            text = "shiorifolio",
                                            color = palette.muted,
                                            fontFamily = FontFamily.Serif,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 11.sp,
                                        )
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(palette.accentGreen)
                                                .padding(horizontal = 5.dp, vertical = 1.dp),
                                        ) {
                                            Text(
                                                text = book.format.name,
                                                color = Color.White,
                                                fontSize = 8.5.sp,
                                                fontFamily = FontFamily.Monospace,
                                                fontWeight = FontWeight.Bold,
                                            )
                                        }
                                        Text(
                                            text = book.title,
                                            color = palette.ink,
                                            fontFamily = FontFamily.Serif,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 13.5.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    ) {
                                        Text(
                                            text = "Pág. ${book.currentPage} de ${book.totalPages} · ${(prediction.completionFraction * 100).roundToInt()}%",
                                            color = palette.inkSoft,
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 10.sp,
                                        )
                                        Text("·", color = palette.muted, fontSize = 10.sp)
                                        Text(
                                            text = "Capítulos (${tocEntries.size})",
                                            color = palette.accentGreen,
                                            fontSize = 10.5.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            textDecoration = TextDecoration.Underline,
                                            modifier = Modifier.clickable { showMobileTocSheet = !showMobileTocSheet },
                                        )
                                    }
                                }
                            }

                            // Right: Bookmark Ribbon + Settings Gear + Desktop Switch
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                val isBookmarked = activeReminder?.pageNumber == book.currentPage
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isBookmarked) palette.warningAmber.copy(alpha = 0.18f) else palette.pillBg)
                                        .border(1.dp, if (isBookmarked) palette.warningAmber else palette.border, RoundedCornerShape(8.dp))
                                        .clickable { onToggleBookmarkHere() }
                                        .padding(horizontal = 8.dp, vertical = 5.dp)
                                        .testTag("open_reminder_btn"),
                                ) {
                                    Text(
                                        text = "栞",
                                        color = if (isBookmarked) palette.warningAmber else palette.ink,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }

                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (showKindleSettingsSheet) palette.accentGreen else palette.pillBg)
                                        .border(1.dp, palette.border, RoundedCornerShape(8.dp))
                                        .clickable { showKindleSettingsSheet = !showKindleSettingsSheet }
                                        .padding(horizontal = 9.dp, vertical = 5.dp),
                                ) {
                                    Text(
                                        text = "⚙ Aa",
                                        color = if (showKindleSettingsSheet) Color.White else palette.ink,
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                }

                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(palette.pillBg)
                                        .border(1.dp, palette.border, RoundedCornerShape(8.dp))
                                        .clickable { onSwitchToDesktopView() }
                                        .padding(horizontal = 8.dp, vertical = 5.dp),
                                ) {
                                    Text("🖥", color = palette.ink, fontSize = 11.5.sp)
                                }
                            }
                        }

                        // Optional status message or active reminder strip inside Mobile HUD
                        if (statusBannerMessage != null) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(palette.pillBg)
                                    .padding(horizontal = 10.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(statusBannerMessage, color = palette.ink, fontSize = 11.sp)
                                Text("✕", color = palette.muted, fontSize = 11.sp, modifier = Modifier.clickable { onDismissStatus() })
                            }
                        }

                        // Hidden testTag node so UI tests always find page_reminder_banner & theme_toggle_button
                        if (activeReminder != null) {
                            Box(modifier = Modifier.testTag("page_reminder_banner"))
                        }
                    }
                }

                // Expandable Mobile Chapter TOC Drawer
                if (showMobileTocSheet && tocEntries.isNotEmpty()) {
                    Surface(
                        color = palette.elevatedSurface,
                        border = BorderStroke(1.dp, palette.border),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            tocEntries.forEach { entry ->
                                val isCurr = currentPageObj.chapterTitle.startsWith(entry.title)
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (isCurr) palette.accentGreen else palette.pageSurface)
                                        .border(1.dp, palette.border, RoundedCornerShape(6.dp))
                                        .clickable {
                                            onPageChange(entry.startPage)
                                            showMobileTocSheet = false
                                        }
                                        .padding(horizontal = 10.dp, vertical = 5.dp),
                                ) {
                                    Text(
                                        text = "${entry.title.take(28)} (p.${entry.startPage})",
                                        color = if (isCurr) Color.White else palette.ink,
                                        fontSize = 11.sp,
                                    )
                                }
                            }
                        }
                    }
                }

                // Expandable "Ajustes de Lectura Kindle" Sheet
                if (showKindleSettingsSheet) {
                    Surface(
                        color = palette.paper.copy(alpha = 0.98f),
                        border = BorderStroke(1.dp, palette.border),
                        modifier = Modifier
                            .fillMaxWidth()
                            .shadow(elevation = 8.dp),
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = "AJUSTES DE LECTURA KINDLE",
                                    color = palette.muted,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.4.sp,
                                )
                                Text(
                                    text = "Cerrar",
                                    color = palette.accentGreen,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.clickable { showKindleSettingsSheet = false },
                                )
                            }

                            // 4 Screen Tone Cards (Kindle Cálido, Sepia, Salvia, Oscuro)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                ReaderTheme.entries.forEach { t ->
                                    val isSel = t == theme
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(Color(t.swatchHex))
                                            .border(
                                                width = if (isSel) 2.dp else 1.dp,
                                                color = if (isSel) palette.accentGreen else palette.border,
                                                shape = RoundedCornerShape(10.dp),
                                            )
                                            .clickable { onSelectTheme(t) }
                                            .padding(vertical = 8.dp),
                                    ) {
                                        Text(
                                            text = t.stitchLabel,
                                            color = if (t == ReaderTheme.DARK) Color(0xFFE8E2D9) else Color(0xFF1C1C18),
                                            fontSize = 10.5.sp,
                                            fontWeight = FontWeight.SemiBold,
                                        )
                                    }
                                }
                            }

                            // Font Size (A-, A, A+) & Line Spacing (1.5, 1.8, 2.1)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Row(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(palette.pillBg)
                                        .border(1.dp, palette.border, RoundedCornerShape(8.dp))
                                        .padding(4.dp),
                                    horizontalArrangement = Arrangement.SpaceEvenly,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = "A-",
                                        color = palette.ink,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        modifier = Modifier.clickable { onFontSizeChange(fontSizePt - 1.5f) }.padding(6.dp),
                                    )
                                    Text(
                                        text = "${fontSizePt.toInt()}pt",
                                        color = palette.accentGreen,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.5.sp,
                                    )
                                    Text(
                                        text = "A+",
                                        color = palette.ink,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        modifier = Modifier.clickable { onFontSizeChange(fontSizePt + 1.5f) }.padding(6.dp),
                                    )
                                }

                                Row(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(palette.pillBg)
                                        .border(1.dp, palette.border, RoundedCornerShape(8.dp))
                                        .padding(4.dp),
                                    horizontalArrangement = Arrangement.SpaceEvenly,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    listOf(1.5f, 1.8f, 2.1f).forEach { mult ->
                                        val sel = kotlin.math.abs(lineSpacingMultiplier - mult) < 0.1f
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(if (sel) palette.accentGreen else Color.Transparent)
                                                .clickable { onLineSpacingChange(mult) }
                                                .padding(horizontal = 8.dp, vertical = 4.dp),
                                        ) {
                                            Text(
                                                text = "$mult",
                                                color = if (sel) Color.White else palette.ink,
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = 11.sp,
                                            )
                                        }
                                    }
                                }
                            }

                            // View Mode & Workspace Section Switcher
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    PageViewMode.entries.forEach { m ->
                                        val sel = m == viewMode
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(if (sel) palette.accentGreen else palette.pillBg)
                                                .clickable { onSelectViewMode(m) }
                                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                                .testTag("view_mode_${m.name}"),
                                        ) {
                                            Text(
                                                text = m.shortTag,
                                                color = if (sel) Color.White else palette.ink,
                                                fontSize = 10.5.sp,
                                            )
                                        }
                                    }
                                }

                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    if (onOpenFileDialog != null) {
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(palette.accentGreen)
                                                .clickable { onOpenFileDialog() }
                                                .padding(horizontal = 8.dp, vertical = 4.dp),
                                        ) {
                                            Text("Abrir Archivo", color = Color.White, fontSize = 10.5.sp)
                                        }
                                    }
                                    MeridianSection.entries.filter { it != MeridianSection.READER }.forEach { sec ->
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(palette.pillBg)
                                                .border(1.dp, palette.border, RoundedCornerShape(6.dp))
                                                .clickable { onSelectSection(sec) }
                                                .padding(horizontal = 8.dp, vertical = 4.dp),
                                        ) {
                                            Text(sec.stitchTabTitle, color = palette.ink, fontSize = 10.5.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 4. FLOATING BOTTOM NAVIGATION DOCK + MINIMAL READING PACE PILL (Stitch Kindle Mobile Footer)
        AnimatedVisibility(
            visible = hudVisible,
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Floating Minimal Reading Pace Pill ("Ritmo: 7h 4m restantes · 33s/pág (80 WPM)")
                Surface(
                    color = palette.paper.copy(alpha = 0.94f),
                    shape = CircleShape,
                    border = BorderStroke(1.dp, palette.border),
                    modifier = Modifier
                        .padding(bottom = 6.dp)
                        .shadow(elevation = 4.dp, shape = CircleShape)
                        .clickable { onToggleTimer() }
                        .testTag("prediction_badge"),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(if (isTimerRunning) Color(0xFF10B981) else palette.muted)
                                .testTag("reading_timer_toggle"),
                        )
                        Text(
                            text = "Ritmo: ${prediction.formattedRemainingTime()} restantes · ${prediction.estimatedSecondsPerPage.toInt()}s/pág (${prediction.estimatedWordsPerMinute.toInt()} WPM)",
                            color = palette.inkSoft,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                        )
                    }
                }

                // Bottom Navigation Dock
                Surface(
                    color = palette.paper.copy(alpha = 0.96f),
                    border = BorderStroke(1.dp, palette.border),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        // Prev Page Button
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(palette.pillBg)
                                .border(1.dp, palette.border, RoundedCornerShape(10.dp))
                                .clickable(enabled = book.currentPage > 1) {
                                    onPageChange((book.currentPage - 1).coerceAtLeast(1))
                                }
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                                .testTag("prev_page_button"),
                        ) {
                            Text("‹ Ant.", color = palette.ink, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        }

                        // Center Page Pill + Interactive Scrubber Slider
                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Text(
                                    text = "${book.currentPage} / ${book.totalPages}",
                                    color = palette.ink,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.5.sp,
                                )
                                Text(
                                    text = "· Modo Inmersivo",
                                    color = palette.muted,
                                    fontSize = 10.sp,
                                    modifier = Modifier.clickable { hudVisible = false },
                                )
                            }
                            Slider(
                                value = book.currentPage.toFloat(),
                                onValueChange = { onPageChange(it.roundToInt().coerceIn(1, book.totalPages)) },
                                valueRange = 1f..book.totalPages.coerceAtLeast(2).toFloat(),
                                colors = SliderDefaults.colors(
                                    thumbColor = palette.accentGreen,
                                    activeTrackColor = palette.accentGreen,
                                    inactiveTrackColor = palette.border,
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(20.dp),
                            )
                        }

                        // Next Page Button
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(palette.accentGreen)
                                .clickable(enabled = book.currentPage < book.totalPages) {
                                    onPageChange((book.currentPage + 1).coerceAtMost(book.totalPages))
                                }
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                                .testTag("next_page_button"),
                        ) {
                            Text("Sig. ›", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PageReminderBanner(
    palette: MeridianPalette,
    currentBook: BookDocument,
    reminder: PageReminder?,
    showEditor: Boolean,
    onJumpToReminder: (Int) -> Unit,
    onSaveReminder: (pageNumber: Int, note: String, targetLabel: String) -> Unit,
    onClearReminder: () -> Unit,
    onToggleEditor: () -> Unit,
) {
    var pageInput by remember(currentBook.currentPage, reminder) {
        mutableStateOf((reminder?.pageNumber ?: currentBook.currentPage).toString())
    }
    var noteInput by remember(currentBook.currentPage, reminder) {
        mutableStateOf(
            reminder?.note
                ?: "Continuar en Pág. ${currentBook.currentPage} (${
                    currentBook.pages.find { it.pageNumber == currentBook.currentPage }?.chapterTitle ?: ""
                })",
        )
    }
    var targetLabelInput by remember(reminder) {
        mutableStateOf(reminder?.targetLabel ?: "Próxima Sesión")
    }

    Surface(
        color = palette.elevatedSurface,
        border = BorderStroke(1.dp, palette.border),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 3.dp)
            .testTag("page_reminder_banner"),
        shape = RoundedCornerShape(8.dp),
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
                                text = "栞 MARCAPÁGINAS · PÁG. ${reminder.pageNumber}",
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.8.sp,
                            )
                        }
                        Text(
                            text = "${reminder.targetLabel}: ${reminder.note}",
                            color = palette.inkSoft,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { onJumpToReminder(reminder.pageNumber) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = palette.accentGreen,
                                contentColor = Color.White,
                            ),
                        ) {
                            Text("Continuar en Pág. ${reminder.pageNumber}", fontSize = 11.sp)
                        }
                        OutlinedButton(
                            onClick = onToggleEditor,
                            border = BorderStroke(1.dp, palette.border),
                        ) {
                            Text("Editar", color = palette.ink, fontSize = 11.sp)
                        }
                    }
                }
            }

            if (showEditor) {
                Text(
                    text = "Configurar Recordatorio de Página (栞 Shiori)",
                    color = palette.ink,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                )
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
                        modifier = Modifier.width(120.dp),
                    )
                    OutlinedTextField(
                        value = targetLabelInput,
                        onValueChange = { targetLabelInput = it },
                        label = { Text("Cuándo (ej. Esta noche)", fontSize = 11.sp) },
                        singleLine = true,
                        modifier = Modifier.width(180.dp),
                    )
                    OutlinedTextField(
                        value = noteInput,
                        onValueChange = { noteInput = it },
                        label = { Text("Nota de lectura", fontSize = 11.sp) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    Button(
                        onClick = {
                            val parsedPage = pageInput.toIntOrNull() ?: currentBook.currentPage
                            onSaveReminder(parsedPage, noteInput, targetLabelInput)
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = palette.accentGreen,
                            contentColor = Color.White,
                        ),
                    ) {
                        Text("Guardar", fontSize = 12.sp)
                    }
                    if (reminder != null) {
                        OutlinedButton(onClick = onClearReminder) {
                            Text("Borrar", color = palette.ink, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickMarkPhraseBar(
    palette: MeridianPalette,
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
    Surface(
        color = palette.pageSurface,
        border = BorderStroke(1.dp, palette.accentGreen),
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
                    color = palette.ink,
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
                                    color = if (isSelected) palette.ink else palette.border,
                                    shape = RoundedCornerShape(4.dp),
                                )
                                .clickable { onToneChange(t) }
                                .padding(horizontal = 8.dp, vertical = 3.dp),
                        ) {
                            Text(t.label, color = palette.ink, fontSize = 11.sp)
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
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = onNoteChange,
                    label = { Text("Nota / Definición", fontSize = 11.sp) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                Button(
                    onClick = onSave,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = palette.accentGreen,
                        contentColor = Color.White,
                    ),
                ) {
                    Text("Guardar Frase", fontSize = 12.sp)
                }
                OutlinedButton(onClick = onClose) {
                    Text("Cancelar", color = palette.ink, fontSize = 12.sp)
                }
            }
        }
    }
}

private fun buildHighlightedAnnotatedString(
    text: String,
    phrases: List<SavedPhrase>,
    palette: MeridianPalette,
): AnnotatedString = buildAnnotatedString {
    append(text)
    val lower = text.lowercase()
    for (saved in phrases) {
        val target = saved.phrase.trim().lowercase()
        if (target.isEmpty()) continue
        var startIdx = lower.indexOf(target)
        while (startIdx >= 0) {
            val endIdx = (startIdx + target.length).coerceAtMost(text.length)
            addStyle(
                style = SpanStyle(
                    background = palette.highlightColor(saved.tone),
                    color = palette.ink,
                    fontWeight = FontWeight.SemiBold,
                ),
                start = startIdx,
                end = endIdx,
            )
            startIdx = lower.indexOf(target, startIdx + target.length)
        }
    }
}

private fun extractCandidatePhrases(text: String): List<String> {
    val words = text
        .replace(Regex("[^A-Za-z0-9#\\-\\s]"), " ")
        .trim()
        .split(Regex("\\s+"))
        .filter { it.length >= 4 }
    if (words.size < 3) return emptyList()
    val firstChunk = words.take(3).joinToString(" ")
    val midStart = (words.size / 2).coerceAtLeast(1)
    val secondChunk = words.drop(midStart).take(3).joinToString(" ")
    return listOf(firstChunk, secondChunk).distinct().filter { it.length >= 8 }
}

/**
 * PDF / EPUB DOCUMENT EDITOR WORKSPACE
 */
@Composable
private fun DocumentEditorWorkspaceView(
    palette: MeridianPalette,
    book: BookDocument,
    onUpdatePage: (pageNumber: Int, chapterTitle: String, blocks: List<ContentBlock>) -> Unit,
    onUpdateMetadata: (title: String, subtitle: String, author: String, format: DocumentFormat) -> Unit,
    onAddPage: (afterPage: Int, chapterTitle: String) -> Unit,
    onDeletePage: (pageNumber: Int) -> Unit,
    onPageChange: (Int) -> Unit,
) {
    val currentPageObj = book.pages.find { it.pageNumber == book.currentPage } ?: book.pages.first()

    var docTitle by remember(book.id, book.title) { mutableStateOf(book.title) }
    var docSubtitle by remember(book.id, book.subtitle) { mutableStateOf(book.subtitle) }
    var docAuthor by remember(book.id, book.author) { mutableStateOf(book.author) }
    var docFormat by remember(book.id, book.format) { mutableStateOf(book.format) }

    var chapterTitle by remember(book.id, currentPageObj.pageNumber, currentPageObj.chapterTitle) {
        mutableStateOf(currentPageObj.chapterTitle)
    }
    var editableBlocks by remember(book.id, currentPageObj.pageNumber, currentPageObj.blocks) {
        mutableStateOf(currentPageObj.blocks)
    }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Surface(
            color = palette.pageSurface,
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(1.dp, palette.border),
            modifier = Modifier
                .width(310.dp)
                .fillMaxHeight(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(
                    text = "DOCUMENT METADATA",
                    color = palette.ink,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = docTitle,
                    onValueChange = { docTitle = it },
                    label = { Text("Book / Folio Title", fontSize = 11.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = docAuthor,
                    onValueChange = { docAuthor = it },
                    label = { Text("Author / Press", fontSize = 11.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    DocumentFormat.entries.forEach { fmt ->
                        val selected = fmt == docFormat
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (selected) palette.ink else palette.pillBg)
                                .clickable { docFormat = fmt }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                        ) {
                            Text(
                                text = fmt.label,
                                color = if (selected) palette.paper else palette.ink,
                                fontSize = 11.5.sp,
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = { onUpdateMetadata(docTitle, docSubtitle, docAuthor, docFormat) },
                    colors = ButtonDefaults.buttonColors(containerColor = palette.ink, contentColor = palette.paper),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Save Metadata", fontSize = 12.sp)
                }

                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(color = palette.border)
                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "PAGES (${book.totalPages})",
                        color = palette.ink,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "+ Insert Page",
                        color = palette.ink,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clickable {
                            onAddPage(book.currentPage, "Chapter ${book.totalPages + 1}")
                        },
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                book.pages.forEach { p ->
                    val isCurr = p.pageNumber == book.currentPage
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 6.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isCurr) palette.ink else palette.elevatedSurface)
                            .clickable { onPageChange(p.pageNumber) }
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Page ${p.pageNumber} · ${p.chapterTitle}",
                                color = if (isCurr) palette.paper else palette.ink,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = "${p.wordCount} words · ${p.blocks.size} blocks",
                                color = if (isCurr) palette.paper.copy(alpha = 0.75f) else palette.muted,
                                fontSize = 10.5.sp,
                            )
                        }
                    }
                }
            }
        }

        Surface(
            color = palette.pageSurface,
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(1.dp, palette.border),
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(18.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "EDITING PAGE ${currentPageObj.pageNumber} (${book.format.label})",
                        color = palette.ink,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = {
                                editableBlocks = editableBlocks + ContentBlock(
                                    id = "blk_${editableBlocks.size + 1}",
                                    style = BlockStyle.PARAGRAPH,
                                    text = "New paragraph added in the Meridian Editor.",
                                )
                            },
                        ) {
                            Text("+ Add Paragraph", color = palette.ink, fontSize = 12.sp)
                        }
                        if (book.totalPages > 1) {
                            OutlinedButton(onClick = { onDeletePage(currentPageObj.pageNumber) }) {
                                Text("Delete Page", color = palette.ink, fontSize = 12.sp)
                            }
                        }
                        Button(
                            onClick = {
                                onUpdatePage(currentPageObj.pageNumber, chapterTitle, editableBlocks)
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = palette.ink,
                                contentColor = palette.paper,
                            ),
                        ) {
                            Text("Save Page Changes", fontSize = 12.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = chapterTitle,
                    onValueChange = { chapterTitle = it },
                    label = { Text("Chapter / Running Header Title", fontSize = 11.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(modifier = Modifier.height(12.dp))

                editableBlocks.forEachIndexed { idx, block ->
                    Surface(
                        color = palette.elevatedSurface,
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, palette.border),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 10.dp),
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    BlockStyle.entries.forEach { st ->
                                        val sel = st == block.style
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(if (sel) palette.ink else palette.pillBg)
                                                .clickable {
                                                    editableBlocks = editableBlocks.toMutableList().also {
                                                        it[idx] = block.copy(style = st)
                                                    }
                                                }
                                                .padding(horizontal = 8.dp, vertical = 3.dp),
                                        ) {
                                            Text(
                                                text = st.label,
                                                color = if (sel) palette.paper else palette.ink,
                                                fontSize = 10.5.sp,
                                            )
                                        }
                                    }
                                }
                                if (editableBlocks.size > 1) {
                                    Text(
                                        text = "Remove Block",
                                        color = palette.muted,
                                        fontSize = 11.sp,
                                        modifier = Modifier.clickable {
                                            editableBlocks = editableBlocks.filterIndexed { i, _ -> i != idx }
                                        },
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            OutlinedTextField(
                                value = block.text,
                                onValueChange = { updatedText ->
                                    editableBlocks = editableBlocks.toMutableList().also {
                                        it[idx] = block.copy(text = updatedText)
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * SAVED WORDS & PHRASES VAULT (LEXICON)
 */
@Composable
private fun SavedPhrasesVaultView(
    palette: MeridianPalette,
    allPhrases: List<SavedPhrase>,
    currentBook: BookDocument,
    onSaveNewPhrase: (phrase: String, note: String, tone: HighlightTone) -> Unit,
    onJumpToPhrase: (SavedPhrase) -> Unit,
    onDeletePhrase: (String) -> Unit,
) {
    var searchQuery by remember { mutableStateOf("") }
    var newPhraseText by remember { mutableStateOf("") }
    var newPhraseNote by remember { mutableStateOf("") }
    var selectedTone by remember { mutableStateOf(HighlightTone.SAGE) }

    val filtered = remember(allPhrases, searchQuery) {
        if (searchQuery.isBlank()) {
            allPhrases
        } else {
            val q = searchQuery.trim().lowercase()
            allPhrases.filter {
                it.phrase.lowercase().contains(q) ||
                    it.note.lowercase().contains(q) ||
                    it.bookTitle.lowercase().contains(q)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 10.dp),
    ) {
        Surface(
            color = palette.pageSurface,
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(1.dp, palette.border),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = "MARK & SAVE NEW WORD OR PHRASE FROM ${currentBook.title.uppercase()} (PAGE ${currentBook.currentPage})",
                    color = palette.ink,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = newPhraseText,
                        onValueChange = { newPhraseText = it },
                        label = { Text("Word or Phrase", fontSize = 11.sp) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = newPhraseNote,
                        onValueChange = { newPhraseNote = it },
                        label = { Text("Definition / Note", fontSize = 11.sp) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    HighlightTone.entries.forEach { t ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(palette.highlightColor(t))
                                .border(
                                    width = if (t == selectedTone) 2.dp else 1.dp,
                                    color = if (t == selectedTone) palette.ink else palette.border,
                                    shape = RoundedCornerShape(4.dp),
                                )
                                .clickable { selectedTone = t }
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                        ) {
                            Text(t.label, color = palette.ink, fontSize = 11.sp)
                        }
                    }
                    Button(
                        onClick = {
                            if (newPhraseText.isNotBlank()) {
                                onSaveNewPhrase(newPhraseText, newPhraseNote, selectedTone)
                                newPhraseText = ""
                                newPhraseNote = ""
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = palette.ink, contentColor = palette.paper),
                    ) {
                        Text("Save", fontSize = 12.sp)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Saved Words & Phrases (${filtered.size})",
                color = palette.ink,
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.SemiBold,
                fontSize = 20.sp,
            )
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = { Text("Search saved words & phrases...", fontSize = 11.sp) },
                singleLine = true,
                modifier = Modifier.width(280.dp),
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            filtered.forEach { item ->
                Surface(
                    color = palette.pageSurface,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, palette.border),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(palette.highlightColor(item.tone))
                                        .padding(horizontal = 8.dp, vertical = 3.dp),
                                ) {
                                    Text(
                                        text = "\"${item.phrase}\"",
                                        color = palette.ink,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                    )
                                }
                                Text(
                                    text = "${item.bookTitle} · Page ${item.pageNumber}",
                                    color = palette.muted,
                                    fontSize = 12.sp,
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Context: \"${item.contextSentence}\"",
                                color = palette.inkSoft,
                                fontStyle = FontStyle.Italic,
                                fontSize = 12.5.sp,
                            )
                            if (item.note.isNotBlank()) {
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Note: ${item.note}",
                                    color = palette.ink,
                                    fontSize = 12.5.sp,
                                )
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { onJumpToPhrase(item) }) {
                                Text("Open Page ${item.pageNumber}", color = palette.ink, fontSize = 12.sp)
                            }
                            OutlinedButton(onClick = { onDeletePhrase(item.id) }) {
                                Text("Delete", color = palette.muted, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * READING ANALYTICS & DYNAMIC FINISH-TIME PREDICTION VIEW
 */
@Composable
private fun ReadingAnalyticsAndPredictionView(
    palette: MeridianPalette,
    book: BookDocument,
    telemetry: BookReadingTelemetry?,
    prediction: FinishTimePrediction,
    onSimulateReadingBurst: (Double) -> Unit,
    onJumpToPage: (Int) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 10.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Surface(
            color = palette.pageSurface,
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(1.dp, palette.border),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Text(
                    text = "ADAPTIVE READING TELEMETRY & DYNAMIC FINISH PREDICTION",
                    color = palette.muted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Estimated Time Remaining: ${prediction.formattedRemainingTime()}",
                    color = palette.ink,
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 28.sp,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Active Time Tracked: ${prediction.formattedActiveTime()}  ·  Current Pace: ${prediction.formattedPacePerPage()} (${prediction.formattedWpm()})  ·  Confidence: ${prediction.confidenceLabel} (Recalculated ${prediction.recalculationCount} times)",
                    color = palette.inkSoft,
                    fontSize = 13.5.sp,
                )
                Spacer(modifier = Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { onSimulateReadingBurst(15.0) },
                        colors = ButtonDefaults.buttonColors(containerColor = palette.ink, contentColor = palette.paper),
                    ) {
                        Text("Simulate +15s Reading Sample (Recalculate)", fontSize = 12.sp)
                    }
                    OutlinedButton(onClick = { onSimulateReadingBurst(45.0) }) {
                        Text("Simulate +45s Deep Study Sample", color = palette.ink, fontSize = 12.sp)
                    }
                }
            }
        }

        Surface(
            color = palette.pageSurface,
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(1.dp, palette.border),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Text(
                    text = "PAGE-BY-PAGE TRACKING LEDGER (${book.title})",
                    color = palette.ink,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.height(10.dp))
                book.pages.take(60).forEach { p ->
                    val stat = telemetry?.pagesVisited?.get(p.pageNumber)
                    val spent = stat?.secondsSpent ?: 0.0
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Page ${p.pageNumber}: ${p.chapterTitle}",
                                color = palette.ink,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.5.sp,
                            )
                            Text(
                                text = "${p.wordCount} words · Visited ${stat?.visitCount ?: 0} time(s) · Time tracked: ${
                                    FinishTimePrediction.formatDurationSeconds(spent)
                                }",
                                color = palette.inkSoft,
                                fontSize = 12.sp,
                            )
                        }
                        OutlinedButton(onClick = { onJumpToPage(p.pageNumber) }) {
                            Text("Read Page ${p.pageNumber}", color = palette.ink, fontSize = 12.sp)
                        }
                    }
                    HorizontalDivider(color = palette.border)
                }
            }
        }
    }
}

/**
 * LIBRARY & FILE IMPORT / EXPORT VIEW
 */
@Composable
private fun LibraryAndFileIoView(
    palette: MeridianPalette,
    documents: List<BookDocument>,
    selectedBookId: String,
    reminders: Map<String, PageReminder>,
    telemetryMap: Map<String, BookReadingTelemetry>,
    onSelectBook: (String) -> Unit,
    onCreateDocument: (title: String, author: String, format: DocumentFormat) -> Unit,
    onImportFromPath: (String) -> Unit,
    onExportToPath: (bookId: String, targetPath: String) -> Unit,
    onPickFileToImport: ((onPathSelected: (String) -> Unit) -> Unit)?,
    onPickFileToExport: ((defaultFileName: String, onPathSelected: (String) -> Unit) -> Unit)?,
) {
    var newDocTitle by remember { mutableStateOf("") }
    var newDocAuthor by remember { mutableStateOf("Meridian Archive") }
    var newDocFormat by remember { mutableStateOf(DocumentFormat.EPUB) }
    var filePathInput by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 10.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Surface(
                color = palette.pageSurface,
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, palette.border),
                modifier = Modifier.weight(1f),
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "CREATE NEW PDF / EPUB FOLIO",
                        color = palette.ink,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OutlinedTextField(
                            value = newDocTitle,
                            onValueChange = { newDocTitle = it },
                            label = { Text("Title", fontSize = 11.sp) },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        DocumentFormat.entries.forEach { fmt ->
                            val sel = fmt == newDocFormat
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (sel) palette.ink else palette.pillBg)
                                    .clickable { newDocFormat = fmt }
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                            ) {
                                Text(
                                    fmt.name,
                                    color = if (sel) palette.paper else palette.ink,
                                    fontSize = 11.5.sp,
                                )
                            }
                        }
                        Button(
                            onClick = {
                                onCreateDocument(newDocTitle, newDocAuthor, newDocFormat)
                                newDocTitle = ""
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = palette.ink, contentColor = palette.paper),
                        ) {
                            Text("Create", fontSize = 12.sp)
                        }
                    }
                }
            }

            Surface(
                color = palette.pageSurface,
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, palette.border),
                modifier = Modifier.weight(1f),
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "OPEN OR EXPORT .PDF / .EPUB FILE ON DISK",
                        color = palette.ink,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OutlinedTextField(
                            value = filePathInput,
                            onValueChange = { filePathInput = it },
                            label = { Text("File path (.pdf or .epub)", fontSize = 11.sp) },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        if (onPickFileToImport != null) {
                            Button(
                                onClick = {
                                    onPickFileToImport { picked ->
                                        filePathInput = picked
                                        onImportFromPath(picked)
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = palette.ink, contentColor = palette.paper),
                            ) {
                                Text("Browse...", fontSize = 12.sp)
                            }
                        }
                        OutlinedButton(
                            onClick = {
                                if (filePathInput.isNotBlank()) onImportFromPath(filePathInput.trim())
                            },
                        ) {
                            Text("Import", color = palette.ink, fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        documents.forEach { doc ->
            val isSelected = doc.id == selectedBookId
            val pred = ReadingPredictionEngine.computePrediction(doc, telemetryMap[doc.id])
            val rem = reminders[doc.id]
            Surface(
                color = palette.pageSurface,
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(if (isSelected) 2.dp else 1.dp, if (isSelected) palette.ink else palette.border),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(palette.ink)
                                    .padding(horizontal = 7.dp, vertical = 2.dp),
                            ) {
                                Text(doc.format.name, color = palette.paper, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                            Text(
                                text = doc.title,
                                color = palette.ink,
                                fontFamily = FontFamily.Serif,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 18.sp,
                            )
                        }
                        Text(
                            text = "By ${doc.author} · Page ${doc.currentPage} of ${doc.totalPages} (${doc.totalWords} words) · Read Time: ${pred.formattedActiveTime()} · Est. Remaining: ${pred.formattedRemainingTime()}",
                            color = palette.inkSoft,
                            fontSize = 12.5.sp,
                        )
                        if (rem != null) {
                            Text(
                                text = "Reminder set for Page ${rem.pageNumber}: \"${rem.note}\"",
                                color = palette.ink,
                                fontWeight = FontWeight.Medium,
                                fontSize = 12.sp,
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val existingFilePath = doc.filePath
                        if (existingFilePath != null) {
                            OutlinedButton(
                                onClick = { onImportFromPath(existingFilePath) },
                            ) {
                                Text("Reload File", color = palette.ink, fontSize = 12.sp)
                            }
                        }
                        Button(
                            onClick = { onSelectBook(doc.id) },
                            colors = ButtonDefaults.buttonColors(containerColor = palette.ink, contentColor = palette.paper),
                        ) {
                            Text(if (isSelected) "Reading Now" else "Open in Reader", fontSize = 12.sp)
                        }
                        OutlinedButton(
                            onClick = {
                                val defaultName = "${doc.title.replace(Regex("[^A-Za-z0-9_\\-]"), "_")}.${doc.format.extension}"
                                if (onPickFileToExport != null) {
                                    onPickFileToExport(defaultName) { target ->
                                        onExportToPath(doc.id, target)
                                    }
                                } else {
                                    val fallback = if (filePathInput.isNotBlank()) filePathInput.trim() else defaultName
                                    onExportToPath(doc.id, fallback)
                                }
                            },
                        ) {
                            Text("Export .${doc.format.extension.uppercase()}", color = palette.ink, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

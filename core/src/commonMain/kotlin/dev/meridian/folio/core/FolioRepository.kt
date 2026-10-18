package dev.meridian.folio.core

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.json.Json

class FolioRepository(
    private val storageFilePath: String = PlatformFileAndZipIo.defaultStateFilePath(),
    private val persistToDisk: Boolean = true,
) {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val _state = MutableStateFlow(loadInitialState())
    val state: StateFlow<FolioPersistedState> = _state.asStateFlow()
    val library = LibraryManager(this)

    private val editHistory = EditHistory()
    private val _editHistoryState = MutableStateFlow(EditHistoryState())
    val editHistoryState: StateFlow<EditHistoryState> = _editHistoryState.asStateFlow()

    internal fun updateLibrary(transform: (FolioPersistedState) -> FolioPersistedState) {
        _state.update(transform)
        saveCurrentState()
    }

    private fun refreshEditHistoryState() {
        _editHistoryState.value = EditHistoryState(
            undoBooks = editHistory.undoBooks,
            redoBooks = editHistory.redoBooks,
        )
    }

    internal fun clearEditHistoryFor(bookIds: Collection<String>) {
        editHistory.purge(bookIds)
        refreshEditHistoryState()
    }

    /**
     * Applies a mutation on [bookId] recording an Undo/Redo entry with snapshots
     * of the document and its annotations (non-destructive editing).
     */
    private inline fun mutateWithHistory(
        bookId: String,
        label: String,
        mutation: (FolioPersistedState) -> FolioPersistedState,
    ) {
        val beforeState = _state.value
        val beforeDoc = beforeState.documents.find { it.id == bookId } ?: return
        val beforePhrases = beforeState.savedPhrases.filter { it.bookId == bookId }
        val beforeReminder = beforeState.reminders[bookId]
        _state.update(mutation)
        val afterState = _state.value
        val afterDoc = afterState.documents.find { it.id == bookId } ?: return
        val afterPhrases = afterState.savedPhrases.filter { it.bookId == bookId }
        val afterReminder = afterState.reminders[bookId]
        if (beforeDoc == afterDoc && beforePhrases == afterPhrases && beforeReminder == afterReminder) return
        editHistory.record(
            EditHistory.Entry(bookId, label, beforeDoc, afterDoc, beforePhrases, afterPhrases, beforeReminder, afterReminder),
        )
        refreshEditHistoryState()
    }

    private fun applySnapshot(
        bookId: String,
        document: BookDocument,
        phrases: List<SavedPhrase>,
        reminder: PageReminder?,
    ) {
        _state.update { current ->
            val knownIds = current.savedPhrases.filter { it.bookId == bookId }.map { it.id }.toSet()
            val byId = phrases.associateBy { it.id }
            val rebuiltPhrases = current.savedPhrases.mapNotNull { existing ->
                if (existing.bookId != bookId) existing else byId[existing.id]
            } + phrases.filterNot { it.id in knownIds }
            current.copy(
                documents = if (current.documents.any { it.id == bookId }) {
                    current.documents.map { if (it.id == bookId) document else it }
                } else {
                    listOf(document) + current.documents
                },
                savedPhrases = rebuiltPhrases,
                reminders = if (reminder == null) current.reminders - bookId else current.reminders + (bookId to reminder),
            )
        }
    }

    private fun loadInitialState(): FolioPersistedState {
        if (persistToDisk) {
            val bytes = PlatformFileAndZipIo.readFileBytes(storageFilePath)
            if (bytes != null && bytes.isNotEmpty()) {
                val decoded = runCatching {
                    json.decodeFromString<FolioPersistedState>(bytes.decodeToString())
                }.getOrNull()
                if (decoded != null) {
                    var anyUpgraded = false
                    val upgradedDocs = decoded.documents.map { doc ->
                        if (doc.parserVersion < DocumentCodec.CURRENT_PARSER_VERSION) {
                            anyUpgraded = true
                            val rawBytes = doc.filePath?.let { PlatformFileAndZipIo.readFileBytes(it) }
                            if (rawBytes != null && doc.filePath != null) {
                                val fileName = doc.filePath.substringAfterLast('\\').substringAfterLast('/')
                                val reparsed = DocumentCodec.importFromBytes(fileName, rawBytes, doc.filePath)
                                reparsed.copy(
                                    id = doc.id,
                                    currentPage = doc.currentPage.coerceIn(1, reparsed.totalPages),
                                    parserVersion = DocumentCodec.CURRENT_PARSER_VERSION,
                                )
                            } else {
                                doc.copy(
                                    parserVersion = DocumentCodec.CURRENT_PARSER_VERSION,
                                    pages = doc.pages.map { page ->
                                        page.copy(
                                            blocks = page.blocks.map { blk ->
                                                if (blk.style == BlockStyle.IMAGE && blk.text.matches(Regex("""Page\s+\d+\s+Illustration""", RegexOption.IGNORE_CASE))) {
                                                    blk.copy(text = "")
                                                } else {
                                                    blk
                                                }
                                            },
                                        )
                                    },
                                )
                            }
                        } else {
                            doc
                        }
                    }
                    val upgradedState = decoded.copy(documents = upgradedDocs)
                    if (anyUpgraded) {
                        runCatching {
                            val encoded = json.encodeToString(FolioPersistedState.serializer(), upgradedState)
                            PlatformFileAndZipIo.writeFileBytes(storageFilePath, encoded.encodeToByteArray())
                        }
                    }
                    return upgradedState
                }
            }
        }
        val now = PlatformFileAndZipIo.currentEpochMillis()
        val books = SampleLibrary.defaultBooks(now)
        return FolioPersistedState(
            documents = books,
            selectedBookId = books.first().id,
            viewMode = PageViewMode.ONE_COLUMN,
            theme = ReaderTheme.WHITE,
            reminders = SampleLibrary.defaultReminders(now),
            savedPhrases = SampleLibrary.defaultSavedPhrases(now),
            telemetry = SampleLibrary.defaultTelemetry(now),
        )
    }

    private fun saveCurrentState() {
        if (!persistToDisk) return
        PlatformLibraryIo.stateWrite {
            runCatching {
                val encoded = json.encodeToString(FolioPersistedState.serializer(), _state.value)
                PlatformFileAndZipIo.writeFileBytes(storageFilePath, encoded.encodeToByteArray())
            }
        }
    }

    fun currentBook(): BookDocument? {
        val s = _state.value
        return s.documents.find { it.id == s.selectedBookId } ?: s.documents.firstOrNull()
    }

    fun selectBook(bookId: String) {
        _state.update { current ->
            val targetDoc = current.documents.find { it.id == bookId } ?: return@update current
            val savedPref = current.bookPreferences[bookId]
            val restoredPage = savedPref?.lastPage?.coerceIn(1, targetDoc.totalPages) ?: targetDoc.currentPage
            val updatedDocs = if (restoredPage != targetDoc.currentPage) {
                current.documents.map {
                    if (it.id == bookId) it.copy(currentPage = restoredPage) else it
                }
            } else {
                current.documents
            }
            val effectivePref = savedPref ?: BookViewPreference(
                bookId = bookId,
                viewMode = current.viewMode,
                fitMode = current.fitMode,
                zoomScale = current.zoomScale,
                lastPage = restoredPage,
                pdfPreferReflowText = current.bookTypography.pdfPreferReflowText,
            )
            current.copy(
                selectedBookId = bookId,
                documents = updatedDocs,
                viewMode = effectivePref.viewMode,
                fitMode = effectivePref.fitMode,
                zoomScale = effectivePref.zoomScale,
                bookTypography = current.bookTypography.copy(
                    pdfPreferReflowText = effectivePref.pdfPreferReflowText || effectivePref.viewMode == PageViewMode.REFLOW_TEXT,
                ),
                bookPreferences = current.bookPreferences + (bookId to effectivePref),
            )
        }
        saveCurrentState()
    }

    fun setViewMode(mode: PageViewMode) {
        _state.update { current ->
            val activeBookId = current.selectedBookId ?: current.documents.firstOrNull()?.id
            val updatedTypography = if (mode == PageViewMode.REFLOW_TEXT) {
                current.bookTypography.copy(pdfPreferReflowText = true)
            } else {
                current.bookTypography
            }
            val updatedPrefs = if (activeBookId != null) {
                val activeDoc = current.documents.find { it.id == activeBookId }
                val prevPref = current.bookPreferences[activeBookId] ?: BookViewPreference(
                    bookId = activeBookId,
                    fitMode = current.fitMode,
                    zoomScale = current.zoomScale,
                    lastPage = activeDoc?.currentPage ?: 1,
                )
                current.bookPreferences + (
                    activeBookId to prevPref.copy(
                        viewMode = mode,
                        pdfPreferReflowText = updatedTypography.pdfPreferReflowText,
                    )
                )
            } else {
                current.bookPreferences
            }
            current.copy(
                viewMode = mode,
                bookTypography = updatedTypography,
                bookPreferences = updatedPrefs,
            )
        }
        saveCurrentState()
    }

    fun cycleViewMode() {
        val modes = PageViewMode.entries
        val currentIdx = modes.indexOf(_state.value.viewMode).coerceAtLeast(0)
        val nextMode = modes[(currentIdx + 1) % modes.size]
        setViewMode(nextMode)
    }

    fun setPageFitMode(fitMode: PageFitMode) {
        _state.update { current ->
            val newZoom = if (fitMode == PageFitMode.MANUAL_ZOOM) current.zoomScale else 1.0f
            val activeBookId = current.selectedBookId ?: current.documents.firstOrNull()?.id
            val updatedPrefs = if (activeBookId != null) {
                val prevPref = current.bookPreferences[activeBookId] ?: BookViewPreference(
                    bookId = activeBookId,
                    viewMode = current.viewMode,
                )
                current.bookPreferences + (activeBookId to prevPref.copy(fitMode = fitMode, zoomScale = newZoom))
            } else {
                current.bookPreferences
            }
            current.copy(
                fitMode = fitMode,
                zoomScale = newZoom,
                bookPreferences = updatedPrefs,
            )
        }
        saveCurrentState()
    }

    fun setZoomScale(scale: Float) {
        val clamped = ((scale.coerceIn(0.50f, 2.50f) * 100f).toInt()) / 100f
        _state.update { current ->
            val nextFit = PageFitMode.MANUAL_ZOOM
            val activeBookId = current.selectedBookId ?: current.documents.firstOrNull()?.id
            val updatedPrefs = if (activeBookId != null) {
                val prevPref = current.bookPreferences[activeBookId] ?: BookViewPreference(
                    bookId = activeBookId,
                    viewMode = current.viewMode,
                )
                current.bookPreferences + (activeBookId to prevPref.copy(fitMode = nextFit, zoomScale = clamped))
            } else {
                current.bookPreferences
            }
            current.copy(
                fitMode = nextFit,
                zoomScale = clamped,
                bookPreferences = updatedPrefs,
            )
        }
        saveCurrentState()
    }

    fun setTheme(theme: ReaderTheme) {
        _state.update { it.copy(theme = theme) }
        saveCurrentState()
    }

    fun toggleTheme() {
        val next = if (_state.value.theme == ReaderTheme.DARK) ReaderTheme.WHITE else ReaderTheme.DARK
        setTheme(next)
    }

    fun setPdfPageColorMode(mode: PdfPageColorMode) {
        _state.update { it.copy(pdfPageColorMode = mode) }
        saveCurrentState()
    }

    fun cyclePdfPageColorMode() {
        val modes = PdfPageColorMode.entries
        val currentIdx = modes.indexOf(_state.value.pdfPageColorMode).coerceAtLeast(0)
        val nextMode = modes[(currentIdx + 1) % modes.size]
        setPdfPageColorMode(nextMode)
    }

    fun bindingFor(action: ReaderAction): KeyBindingSpec =
        _state.value.keyBindings[action] ?: action.defaultSpec

    fun updateKeyBinding(action: ReaderAction, spec: KeyBindingSpec) {
        _state.update { current ->
            current.copy(keyBindings = current.keyBindings + (action to spec))
        }
        saveCurrentState()
    }

    fun resetKeyBindings() {
        _state.update { current ->
            current.copy(keyBindings = KeyBindingSpec.defaultBindings())
        }
        saveCurrentState()
    }

    fun setFontSizePt(fontSizePt: Float) {
        val clamped = fontSizePt.coerceIn(12f, 28f)
        _state.update { it.copy(fontSizePt = clamped) }
        saveCurrentState()
    }

    fun setLineSpacingMultiplier(multiplier: Float) {
        val clamped = multiplier.coerceIn(1.2f, 2.4f)
        _state.update { it.copy(lineSpacingMultiplier = clamped) }
        saveCurrentState()
    }

    fun updateUiAppearance(transform: (UiAppearanceSettings) -> UiAppearanceSettings) {
        _state.update { current ->
            val updated = transform(current.uiAppearance)
            current.copy(
                uiAppearance = updated.copy(
                    fontSizeSp = updated.fontSizeSp.coerceIn(11f, 16f),
                    uiScale = updated.uiScale.coerceIn(0.85f, 1.35f),
                ),
            )
        }
        saveCurrentState()
    }

    fun updateBookTypography(transform: (BookTypographySettings) -> BookTypographySettings) {
        _state.update { current ->
            val updated = transform(current.bookTypography).copy(
                paragraphSpacingDp = transform(current.bookTypography).paragraphSpacingDp.coerceIn(0f, 36f),
                horizontalMarginDp = transform(current.bookTypography).horizontalMarginDp.coerceIn(8f, 72f),
                verticalMarginDp = transform(current.bookTypography).verticalMarginDp.coerceIn(8f, 56f),
            )
            val activeBookId = current.selectedBookId ?: current.documents.firstOrNull()?.id
            val updatedPrefs = if (activeBookId != null) {
                val prevPref = current.bookPreferences[activeBookId] ?: BookViewPreference(
                    bookId = activeBookId,
                    viewMode = current.viewMode,
                    fitMode = current.fitMode,
                    zoomScale = current.zoomScale,
                )
                current.bookPreferences + (activeBookId to prevPref.copy(pdfPreferReflowText = updated.pdfPreferReflowText))
            } else {
                current.bookPreferences
            }
            current.copy(
                bookTypography = updated,
                bookPreferences = updatedPrefs,
            )
        }
        saveCurrentState()
    }

    fun resetUiAppearance() {
        _state.update { current ->
            current.copy(uiAppearance = UiAppearanceSettings())
        }
        saveCurrentState()
    }

    fun resetBookTypography() {
        _state.update { current ->
            current.copy(
                fontSizePt = 17f,
                lineSpacingMultiplier = 1.8f,
                bookTypography = BookTypographySettings(),
            )
        }
        saveCurrentState()
    }

    fun resetAllAppearanceAndTypography() {
        _state.update { current ->
            current.copy(
                theme = ReaderTheme.WHITE,
                pdfPageColorMode = PdfPageColorMode.THEME_TINT,
                fontSizePt = 17f,
                lineSpacingMultiplier = 1.8f,
                uiAppearance = UiAppearanceSettings(),
                bookTypography = BookTypographySettings(),
            )
        }
        saveCurrentState()
    }

    /**
     * Runs non-destructive OCR on [pageNumber] of [bookId], adding the recognized text blocks
     * in memory while keeping the original PDF file on disk untouched.
     */
    fun runOcrOnPage(bookId: String, pageNumber: Int): Int {
        val now = PlatformFileAndZipIo.currentEpochMillis()
        var addedCount = 0
        _state.update { current ->
            val book = current.documents.find { it.id == bookId } ?: return@update current
            val targetPage = book.pages.find { it.pageNumber == pageNumber } ?: return@update current
            val ocrParagraphs = PlatformPdfEngine.extractOcrTextForPage(
                filePath = book.filePath,
                docId = book.id,
                pageNumber = pageNumber,
                renderedImagePath = targetPage.renderedPageImagePath,
            )
            if (ocrParagraphs.isEmpty()) return@update current

            val newBlocks = ocrParagraphs.mapIndexed { idx, para ->
                ContentBlock(
                    id = "ocr_${pageNumber}_$idx",
                    style = BlockStyle.PARAGRAPH,
                    text = para,
                )
            }
            addedCount = newBlocks.size
            val updatedPages = book.pages.map { page ->
                if (page.pageNumber == pageNumber) {
                    val withoutOldOcr = page.blocks.filterNot { it.id.startsWith("ocr_") }
                    page.copy(
                        blocks = withoutOldOcr + newBlocks,
                        lastEditedEpochMs = now,
                    )
                } else {
                    page
                }
            }
            val updatedDocs = current.documents.map {
                if (it.id == bookId) it.copy(pages = updatedPages, updatedAtMs = now) else it
            }
            current.copy(documents = updatedDocs)
        }
        if (addedCount > 0) saveCurrentState()
        return addedCount
    }

    /**
     * Sequential page turn (Arrows, PageUp/PageDown, Prev/Next buttons, Slider).
     * Does NOT pollute navigation back/forward history.
     */
    fun goToPage(bookId: String, targetPage: Int) {
        val now = PlatformFileAndZipIo.currentEpochMillis()
        _state.update { current ->
            val book = current.documents.find { it.id == bookId } ?: return@update current
            val clampedPage = targetPage.coerceIn(1, book.totalPages)
            val fromPage = book.currentPage
            val updatedDocs = current.documents.map {
                if (it.id == bookId) it.copy(currentPage = clampedPage, updatedAtMs = now) else it
            }
            val existingTel = current.telemetry[bookId] ?: BookReadingTelemetry(bookId = bookId)
            val updatedTel = if (fromPage != clampedPage) {
                ReadingPredictionEngine.recordPageTurn(existingTel, book, fromPage, clampedPage, now)
            } else {
                existingTel
            }
            val prevPref = current.bookPreferences[bookId] ?: BookViewPreference(
                bookId = bookId,
                viewMode = current.viewMode,
                fitMode = current.fitMode,
                zoomScale = current.zoomScale,
                pdfPreferReflowText = current.bookTypography.pdfPreferReflowText,
            )
            current.copy(
                documents = updatedDocs,
                telemetry = current.telemetry + (bookId to updatedTel),
                bookPreferences = current.bookPreferences + (bookId to prevPref.copy(lastPage = clampedPage)),
            )
        }
        saveCurrentState()
    }

    /**
     * Non-sequential jump (TOC chapter, bookmark reminder, saved phrase in notebook, search result, or book switch).
     * Pushes the previous reading position onto [FolioPersistedState.navigationBackStack] and clears forward history.
     */
    fun jumpToLocation(bookId: String, targetPage: Int, reason: String = "Salto") {
        val now = PlatformFileAndZipIo.currentEpochMillis()
        _state.update { current ->
            val targetBook = current.documents.find { it.id == bookId } ?: return@update current
            val clampedTargetPage = targetPage.coerceIn(1, targetBook.totalPages)
            val originBook = current.documents.find { it.id == current.selectedBookId } ?: current.documents.firstOrNull()

            val updatedBackStack = if (
                originBook != null &&
                (originBook.id != targetBook.id || originBook.currentPage != clampedTargetPage)
            ) {
                val entry = NavigationHistoryEntry(
                    bookId = originBook.id,
                    pageNumber = originBook.currentPage,
                    reason = reason,
                    timestampMs = now,
                )
                (current.navigationBackStack + entry).takeLast(40)
            } else {
                current.navigationBackStack
            }

            val fromPage = targetBook.currentPage
            val updatedDocs = current.documents.map {
                if (it.id == bookId) it.copy(currentPage = clampedTargetPage, updatedAtMs = now) else it
            }
            val existingTel = current.telemetry[bookId] ?: BookReadingTelemetry(bookId = bookId)
            val updatedTel = if (fromPage != clampedTargetPage) {
                ReadingPredictionEngine.recordPageTurn(existingTel, targetBook, fromPage, clampedTargetPage, now)
            } else {
                existingTel
            }

            val prevPref = current.bookPreferences[bookId] ?: BookViewPreference(
                bookId = bookId,
                viewMode = current.viewMode,
                fitMode = current.fitMode,
                zoomScale = current.zoomScale,
                lastPage = clampedTargetPage,
                pdfPreferReflowText = current.bookTypography.pdfPreferReflowText,
            )
            val updatedPref = prevPref.copy(lastPage = clampedTargetPage)

            current.copy(
                selectedBookId = bookId,
                documents = updatedDocs,
                viewMode = updatedPref.viewMode,
                fitMode = updatedPref.fitMode,
                zoomScale = updatedPref.zoomScale,
                bookTypography = current.bookTypography.copy(
                    pdfPreferReflowText = updatedPref.pdfPreferReflowText || updatedPref.viewMode == PageViewMode.REFLOW_TEXT,
                ),
                bookPreferences = current.bookPreferences + (bookId to updatedPref),
                telemetry = current.telemetry + (bookId to updatedTel),
                navigationBackStack = updatedBackStack,
                navigationForwardStack = emptyList(),
            )
        }
        saveCurrentState()
    }

    fun canNavigateBack(): Boolean = _state.value.navigationBackStack.isNotEmpty()

    fun canNavigateForward(): Boolean = _state.value.navigationForwardStack.isNotEmpty()

    fun navigateBack(): NavigationHistoryEntry? {
        val now = PlatformFileAndZipIo.currentEpochMillis()
        var popped: NavigationHistoryEntry? = null
        _state.update { current ->
            val targetEntry = current.navigationBackStack.lastOrNull() ?: return@update current
            popped = targetEntry
            val currentBook = current.documents.find { it.id == current.selectedBookId } ?: current.documents.firstOrNull()
            val forwardEntry = if (currentBook != null) {
                NavigationHistoryEntry(
                    bookId = currentBook.id,
                    pageNumber = currentBook.currentPage,
                    reason = targetEntry.reason,
                    timestampMs = now,
                )
            } else {
                null
            }
            val updatedDocs = current.documents.map { doc ->
                if (doc.id == targetEntry.bookId) {
                    doc.copy(currentPage = targetEntry.pageNumber.coerceIn(1, doc.totalPages), updatedAtMs = now)
                } else {
                    doc
                }
            }
            current.copy(
                selectedBookId = targetEntry.bookId,
                documents = updatedDocs,
                navigationBackStack = current.navigationBackStack.dropLast(1),
                navigationForwardStack = if (forwardEntry != null) {
                    (current.navigationForwardStack + forwardEntry).takeLast(40)
                } else {
                    current.navigationForwardStack
                },
            )
        }
        if (popped != null) saveCurrentState()
        return popped
    }

    fun navigateForward(): NavigationHistoryEntry? {
        val now = PlatformFileAndZipIo.currentEpochMillis()
        var popped: NavigationHistoryEntry? = null
        _state.update { current ->
            val targetEntry = current.navigationForwardStack.lastOrNull() ?: return@update current
            popped = targetEntry
            val currentBook = current.documents.find { it.id == current.selectedBookId } ?: current.documents.firstOrNull()
            val backEntry = if (currentBook != null) {
                NavigationHistoryEntry(
                    bookId = currentBook.id,
                    pageNumber = currentBook.currentPage,
                    reason = targetEntry.reason,
                    timestampMs = now,
                )
            } else {
                null
            }
            val updatedDocs = current.documents.map { doc ->
                if (doc.id == targetEntry.bookId) {
                    doc.copy(currentPage = targetEntry.pageNumber.coerceIn(1, doc.totalPages), updatedAtMs = now)
                } else {
                    doc
                }
            }
            current.copy(
                selectedBookId = targetEntry.bookId,
                documents = updatedDocs,
                navigationForwardStack = current.navigationForwardStack.dropLast(1),
                navigationBackStack = if (backEntry != null) {
                    (current.navigationBackStack + backEntry).takeLast(40)
                } else {
                    current.navigationBackStack
                },
            )
        }
        if (popped != null) saveCurrentState()
        return popped
    }

    fun recordReadingTimeTick(bookId: String, deltaSeconds: Double) {
        val now = PlatformFileAndZipIo.currentEpochMillis()
        _state.update { current ->
            val book = current.documents.find { it.id == bookId } ?: return@update current
            val existingTel = current.telemetry[bookId] ?: BookReadingTelemetry(bookId = bookId)
            val updatedTel = ReadingPredictionEngine.recordReadingTick(
                telemetry = existingTel,
                book = book,
                pageNumber = book.currentPage,
                deltaSeconds = deltaSeconds,
                nowMs = now,
            )
            current.copy(
                telemetry = current.telemetry + (bookId to updatedTel),
            )
        }
        if ((_state.value.telemetry[bookId]?.recalculationCount ?: 0) % 5 == 0) {
            saveCurrentState()
        }
    }

    fun setPageReminder(bookId: String, pageNumber: Int, note: String, targetLabel: String = "Next Session") {
        val now = PlatformFileAndZipIo.currentEpochMillis()
        _state.update { current ->
            val book = current.documents.find { it.id == bookId } ?: return@update current
            val reminder = PageReminder(
                bookId = bookId,
                bookTitle = book.title,
                pageNumber = pageNumber.coerceIn(1, book.totalPages),
                note = note.trim().ifBlank { "Continue reading from Page $pageNumber" },
                targetLabel = targetLabel.trim().ifBlank { "Next Session" },
                createdAtMs = now,
            )
            current.copy(reminders = current.reminders + (bookId to reminder))
        }
        saveCurrentState()
    }

    fun clearPageReminder(bookId: String) {
        _state.update { current ->
            current.copy(reminders = current.reminders - bookId)
        }
        saveCurrentState()
    }

    fun saveWordOrPhrase(
        bookId: String,
        pageNumber: Int,
        phrase: String,
        contextSentence: String,
        note: String = "",
        tone: HighlightTone = HighlightTone.SAGE,
    ): SavedPhrase? {
        val trimmed = phrase.trim()
        if (trimmed.isBlank()) return null
        val now = PlatformFileAndZipIo.currentEpochMillis()
        val book = _state.value.documents.find { it.id == bookId } ?: return null
        val page = book.pages.find { it.pageNumber == pageNumber }
        val anchor = page?.let { AnnotationAnchorer.anchorForPage(it, trimmed) }
        val entry = SavedPhrase(
            id = "ph_${now}_${trimmed.hashCode().toUInt()}",
            bookId = bookId,
            bookTitle = book.title,
            pageNumber = pageNumber,
            phrase = trimmed,
            contextSentence = contextSentence.trim().ifBlank { trimmed },
            note = note.trim(),
            tone = tone,
            createdAtMs = now,
            anchorPrefix = anchor?.prefix.orEmpty(),
            anchorSuffix = anchor?.suffix.orEmpty(),
            anchorChapterTitle = page?.chapterTitle.orEmpty(),
            anchorStatus = AnchorStatus.ACTIVE,
        )
        _state.update { current ->
            current.copy(savedPhrases = listOf(entry) + current.savedPhrases)
        }
        saveCurrentState()
        return entry
    }

    fun deleteSavedPhrase(phraseId: String) {
        _state.update { current ->
            current.copy(savedPhrases = current.savedPhrases.filterNot { it.id == phraseId })
        }
        saveCurrentState()
    }

    fun updatePageContent(
        bookId: String,
        pageNumber: Int,
        newChapterTitle: String,
        newBlocks: List<ContentBlock>,
    ) {
        val now = PlatformFileAndZipIo.currentEpochMillis()
        mutateWithHistory(bookId, "Editar página $pageNumber") { current ->
            val doc = current.documents.find { it.id == bookId } ?: return@mutateWithHistory current
            val updatedPages = doc.pages.map { p ->
                if (p.pageNumber == pageNumber) {
                    p.copy(
                        chapterTitle = newChapterTitle.trim().ifBlank { "Page $pageNumber" },
                        blocks = newBlocks,
                        lastEditedEpochMs = now,
                    )
                } else {
                    p
                }
            }
            val updatedDoc = doc.copy(pages = updatedPages, updatedAtMs = now)
            val reanchoredPhrases = AnnotationAnchorer.reanchorBook(current.savedPhrases, bookId, updatedPages)
            val existingTel = current.telemetry[bookId]
            val updatedTelMap = if (existingTel != null) {
                current.telemetry + (bookId to existingTel.copy(recalculationCount = existingTel.recalculationCount + 1))
            } else {
                current.telemetry
            }
            current.copy(
                documents = current.documents.map { if (it.id == bookId) updatedDoc else it },
                savedPhrases = reanchoredPhrases,
                telemetry = updatedTelMap,
            )
        }
        saveCurrentState()
    }

    /** Reemplaza todas las ocurrencias de [query] en el capítulo o libro indicado. */
    fun replaceAllText(
        bookId: String,
        query: String,
        replacement: String,
        chapterTitle: String? = null,
        ignoreCase: Boolean = true,
    ): Int {
        var replaced = 0
        val now = PlatformFileAndZipIo.currentEpochMillis()
        mutateWithHistory(bookId, "Reemplazar «${query.take(24)}»") { current ->
            val doc = current.documents.find { it.id == bookId } ?: return@mutateWithHistory current
            val outcome = BookTextReplace.replaceAll(doc.pages, query, replacement, chapterTitle, ignoreCase)
            replaced = outcome.replacedCount
            if (outcome.replacedCount == 0) return@mutateWithHistory current
            val stamped = stampChangedPages(doc.pages, outcome.pages, now)
            current.copy(
                documents = current.documents.map {
                    if (it.id == bookId) doc.copy(pages = stamped, updatedAtMs = now) else it
                },
                savedPhrases = AnnotationAnchorer.reanchorBook(current.savedPhrases, bookId, stamped),
            )
        }
        saveCurrentState()
        return replaced
    }

    /** Reemplaza una única ocurrencia localizada por una búsqueda previa. */
    fun replaceTextAt(
        bookId: String,
        pageNumber: Int,
        blockId: String,
        startIndex: Int,
        query: String,
        replacement: String,
        ignoreCase: Boolean = true,
    ): Boolean {
        var done = false
        val now = PlatformFileAndZipIo.currentEpochMillis()
        mutateWithHistory(bookId, "Reemplazar «${query.take(24)}»") { current ->
            val doc = current.documents.find { it.id == bookId } ?: return@mutateWithHistory current
            val outcome = BookTextReplace.replaceOne(doc.pages, pageNumber, blockId, startIndex, query, replacement, ignoreCase)
            if (outcome.replacedCount == 0) return@mutateWithHistory current
            done = true
            val stamped = stampChangedPages(doc.pages, outcome.pages, now)
            current.copy(
                documents = current.documents.map {
                    if (it.id == bookId) doc.copy(pages = stamped, updatedAtMs = now) else it
                },
                savedPhrases = AnnotationAnchorer.reanchorBook(current.savedPhrases, bookId, stamped),
            )
        }
        saveCurrentState()
        return done
    }

    fun renameChapter(bookId: String, startPage: Int, newTitle: String): Boolean {
        var changed = false
        val now = PlatformFileAndZipIo.currentEpochMillis()
        mutateWithHistory(bookId, "Renombrar capítulo") { current ->
            val doc = current.documents.find { it.id == bookId } ?: return@mutateWithHistory current
            val pages = ChapterEditing.renameChapter(doc.pages, startPage, newTitle)
            if (pages == doc.pages) return@mutateWithHistory current
            changed = true
            current.copy(
                documents = current.documents.map {
                    if (it.id == bookId) doc.copy(pages = pages, updatedAtMs = now) else it
                },
                savedPhrases = AnnotationAnchorer.reanchorBook(current.savedPhrases, bookId, pages),
            )
        }
        saveCurrentState()
        return changed
    }

    fun moveChapter(bookId: String, startPage: Int, upwards: Boolean): Boolean {
        var changed = false
        val now = PlatformFileAndZipIo.currentEpochMillis()
        mutateWithHistory(bookId, "Reordenar capítulos") { current ->
            val doc = current.documents.find { it.id == bookId } ?: return@mutateWithHistory current
            val pages = ChapterEditing.moveChapter(doc.pages, startPage, upwards) ?: return@mutateWithHistory current
            changed = true
            current.copy(
                documents = current.documents.map {
                    if (it.id == bookId) {
                        doc.copy(pages = pages, currentPage = doc.currentPage.coerceIn(1, pages.size), updatedAtMs = now)
                    } else {
                        it
                    }
                },
                savedPhrases = AnnotationAnchorer.reanchorBook(current.savedPhrases, bookId, pages),
            )
        }
        saveCurrentState()
        return changed
    }

    fun repairTableOfContents(bookId: String): Boolean {
        var changed = false
        val now = PlatformFileAndZipIo.currentEpochMillis()
        mutateWithHistory(bookId, "Reparar TOC") { current ->
            val doc = current.documents.find { it.id == bookId } ?: return@mutateWithHistory current
            val pages = ChapterEditing.repairTitles(doc.pages)
            if (pages == doc.pages) return@mutateWithHistory current
            changed = true
            current.copy(
                documents = current.documents.map {
                    if (it.id == bookId) doc.copy(pages = pages, updatedAtMs = now) else it
                },
                savedPhrases = AnnotationAnchorer.reanchorBook(current.savedPhrases, bookId, pages),
            )
        }
        saveCurrentState()
        return changed
    }

    private fun stampChangedPages(before: List<BookPage>, after: List<BookPage>, now: Long): List<BookPage> =
        after.mapIndexed { index, page ->
            val old = before.getOrNull(index)
            if (old != null && old.blocks != page.blocks) page.copy(lastEditedEpochMs = now) else page
        }

    fun canUndoEdit(bookId: String): Boolean = editHistory.canUndo(bookId)

    fun canRedoEdit(bookId: String): Boolean = editHistory.canRedo(bookId)

    fun undoEdit(bookId: String): Boolean {
        val entry = editHistory.undoEntry(bookId) ?: return false
        applySnapshot(entry.bookId, entry.beforeDocument, entry.beforePhrases, entry.beforeReminder)
        saveCurrentState()
        refreshEditHistoryState()
        return true
    }

    fun redoEdit(bookId: String): Boolean {
        val entry = editHistory.redoEntry(bookId) ?: return false
        applySnapshot(entry.bookId, entry.afterDocument, entry.afterPhrases, entry.afterReminder)
        saveCurrentState()
        refreshEditHistoryState()
        return true
    }

    /** Reancla manualmente una anotación huérfana sobre una ocurrencia elegida. */
    fun reattachPhrase(
        phraseId: String,
        pageNumber: Int,
        chapterTitle: String,
        anchorPrefix: String,
        anchorSuffix: String,
    ) {
        val phrase = _state.value.savedPhrases.find { it.id == phraseId } ?: return
        mutateWithHistory(phrase.bookId, "Reanclar anotación") { current ->
            current.copy(
                savedPhrases = current.savedPhrases.map {
                    if (it.id == phraseId) {
                        it.copy(
                            pageNumber = pageNumber,
                            anchorChapterTitle = chapterTitle,
                            anchorPrefix = anchorPrefix,
                            anchorSuffix = anchorSuffix,
                            anchorStatus = AnchorStatus.ACTIVE,
                        )
                    } else {
                        it
                    }
                },
            )
        }
        saveCurrentState()
    }

    fun updateDocumentMetadata(
        bookId: String,
        newTitle: String,
        newAuthor: String,
        newLanguage: String? = null,
        newCoverImagePath: String? = null,
    ) {
        val now = PlatformFileAndZipIo.currentEpochMillis()
        mutateWithHistory(bookId, "Editar metadatos") { current ->
            val doc = current.documents.find { it.id == bookId } ?: return@mutateWithHistory current
            val updatedDoc = doc.copy(
                title = newTitle.trim().ifBlank { doc.title },
                author = newAuthor.trim().ifBlank { doc.author },
                language = newLanguage?.trim() ?: doc.language,
                coverImagePath = when {
                    newCoverImagePath == null -> doc.coverImagePath
                    newCoverImagePath.isBlank() -> null
                    else -> newCoverImagePath
                },
                updatedAtMs = now,
            )
            current.copy(
                documents = current.documents.map { if (it.id == bookId) updatedDoc else it },
                savedPhrases = current.savedPhrases.map {
                    if (it.bookId == bookId) it.copy(bookTitle = updatedDoc.title) else it
                },
                reminders = current.reminders.mapValues { (id, reminder) ->
                    if (id == bookId) reminder.copy(bookTitle = updatedDoc.title) else reminder
                },
            )
        }
        saveCurrentState()
    }

    fun addNewPage(bookId: String, afterPageNumber: Int? = null, chapterTitle: String = "New Folio Leaf") {
        val now = PlatformFileAndZipIo.currentEpochMillis()
        mutateWithHistory(bookId, "Añadir página") { current ->
            val doc = current.documents.find { it.id == bookId } ?: return@mutateWithHistory current
            val insertIdx = (afterPageNumber ?: doc.totalPages).coerceIn(0, doc.pages.size)
            val mutablePages = doc.pages.toMutableList()
            val newPageNum = insertIdx + 1
            mutablePages.add(
                insertIdx,
                BookPage(
                    pageNumber = newPageNum,
                    chapterTitle = chapterTitle,
                    blocks = listOf(
                        ContentBlock(
                            id = "blk_${now}_0",
                            style = BlockStyle.HEADING,
                            text = chapterTitle,
                        ),
                        ContentBlock(
                            id = "blk_${now}_1",
                            style = BlockStyle.PARAGRAPH,
                            text = "Begin composing or editing this page in the Meridian PDF / EPUB Editor...",
                        ),
                    ),
                    lastEditedEpochMs = now,
                ),
            )
            val renumbered = mutablePages.mapIndexed { idx, p -> p.copy(pageNumber = idx + 1) }
            val updatedDoc = doc.copy(
                pages = renumbered,
                currentPage = newPageNum,
                updatedAtMs = now,
            )
            current.copy(
                documents = current.documents.map { if (it.id == bookId) updatedDoc else it },
                savedPhrases = AnnotationAnchorer.reanchorBook(current.savedPhrases, bookId, renumbered),
                reminders = current.reminders.mapValues { (id, reminder) ->
                    if (id == bookId && reminder.pageNumber >= newPageNum) {
                        reminder.copy(pageNumber = reminder.pageNumber + 1)
                    } else {
                        reminder
                    }
                },
            )
        }
        saveCurrentState()
    }

    fun deletePage(bookId: String, pageNumber: Int) {
        val now = PlatformFileAndZipIo.currentEpochMillis()
        mutateWithHistory(bookId, "Eliminar página") { current ->
            val doc = current.documents.find { it.id == bookId } ?: return@mutateWithHistory current
            if (doc.pages.size <= 1) return@mutateWithHistory current
            val filtered = doc.pages.filterNot { it.pageNumber == pageNumber }
                .mapIndexed { idx, p -> p.copy(pageNumber = idx + 1) }
            val nextCurr = doc.currentPage.coerceIn(1, filtered.size)
            val updatedDoc = doc.copy(
                pages = filtered,
                currentPage = nextCurr,
                updatedAtMs = now,
            )
            current.copy(
                documents = current.documents.map { if (it.id == bookId) updatedDoc else it },
                savedPhrases = AnnotationAnchorer.reanchorBook(current.savedPhrases, bookId, filtered),
                reminders = current.reminders.mapValues { (id, reminder) ->
                    if (id == bookId && reminder.pageNumber > pageNumber) {
                        reminder.copy(pageNumber = reminder.pageNumber - 1)
                    } else if (id == bookId) {
                        reminder.copy(pageNumber = reminder.pageNumber.coerceIn(1, filtered.size))
                    } else {
                        reminder
                    }
                },
            )
        }
        saveCurrentState()
    }

    fun createNewDocument(
        title: String,
        author: String,
        format: DocumentFormat,
    ): BookDocument {
        val now = PlatformFileAndZipIo.currentEpochMillis()
        val doc = BookDocument(
            id = "doc_${now}_${title.hashCode().toUInt()}",
            title = title.trim().ifBlank { "Untitled Meridian Folio" },
            subtitle = "Created in Meridian Studio",
            author = author.trim().ifBlank { "Meridian Author" },
            format = format,
            pages = listOf(
                BookPage(
                    pageNumber = 1,
                    chapterTitle = "Chapter I — Opening Leaf",
                    blocks = listOf(
                        ContentBlock("b_${now}_1", BlockStyle.HEADING, title.trim().ifBlank { "Opening Chapter" }),
                        ContentBlock(
                            "b_${now}_2",
                            BlockStyle.PARAGRAPH,
                            "This ${format.label} manuscript was created in Meridian Folio. Use the reader's editing tools (search, page editing, chapter manager) to refine paragraphs and chapters, or export directly to a .${format.extension} file.",
                        ),
                    ),
                    lastEditedEpochMs = now,
                ),
            ),
            currentPage = 1,
            parserVersion = 2,
            createdAtMs = now,
            updatedAtMs = now,
        )
        _state.update { current ->
            current.copy(
                documents = listOf(doc) + current.documents,
                selectedBookId = doc.id,
            )
        }
        saveCurrentState()
        return doc
    }

    fun importDocumentFromFile(path: String): BookDocument? {
        return runCatching { library.importFile(path, select = true) }.getOrNull()
    }

    fun exportDocumentToFile(bookId: String, targetPath: String): Boolean =
        exportDocumentWithReport(bookId, targetPath) != null

    /**
     * Exports [bookId] to [targetPath] and returns the structural validation report
     * (plus epubcheck output when the CLI tool is installed on the system).
     */
    fun exportDocumentWithReport(bookId: String, targetPath: String): ExportValidationReport? {
        val doc = _state.value.documents.find { it.id == bookId } ?: return null
        val bytes = DocumentCodec.exportToBytes(doc)
        if (!PlatformFileAndZipIo.writeFileBytes(targetPath, bytes)) return null
        val report = EpubExportValidator.validateStructure(bytes)
        val epubcheckOutput = if (doc.format == DocumentFormat.EPUB) {
            PlatformTooling.runEpubcheck(targetPath)
        } else {
            null
        }
        return report.copy(epubcheckOutput = epubcheckOutput)
    }

    // ---- Portable personal-data backup ----

    fun exportBackupToFile(targetPath: String): BackupExportSummary? {
        val now = PlatformFileAndZipIo.currentEpochMillis()
        val (bundle, summary) = BackupCodec.buildBundle(_state.value, now)
        val written = PlatformFileAndZipIo.writeFileBytes(targetPath, BackupCodec.encode(bundle).encodeToByteArray())
        return if (written) summary else null
    }

    /** Reads a backup file header for confirmation UI without applying anything. */
    fun peekBackupFile(path: String): BackupPeek? {
        val bytes = PlatformFileAndZipIo.readFileBytes(path) ?: return null
        val bundle = runCatching { BackupCodec.decode(bytes.decodeToString()) }.getOrNull() ?: return null
        return BackupPeek(
            exportedAtMs = bundle.exportedAtMs,
            documentCount = bundle.documents.size,
            phraseCount = bundle.savedPhrases.size,
            reminderCount = bundle.reminders.size,
            collectionCount = bundle.collections.size,
        )
    }

    /**
     * Restores a backup: books are matched by content fingerprint, personal data is
     * replaced for matched books, and unmatched entries are reported instead of dropped.
     */
    fun importBackupFromFile(path: String): BackupImportSummary? {
        val bytes = PlatformFileAndZipIo.readFileBytes(path) ?: return null
        val bundle = runCatching { BackupCodec.decode(bytes.decodeToString()) }.getOrNull() ?: return null
        val now = PlatformFileAndZipIo.currentEpochMillis()
        val (updated, summary) = BackupCodec.applyBundle(_state.value, bundle, now)
        _state.value = updated
        editHistory.clear()
        refreshEditHistoryState()
        saveCurrentState()
        return summary
    }
}

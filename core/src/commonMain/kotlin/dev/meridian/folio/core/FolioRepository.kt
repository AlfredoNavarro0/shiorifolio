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

    private fun loadInitialState(): FolioPersistedState {
        if (persistToDisk) {
            val bytes = PlatformFileAndZipIo.readFileBytes(storageFilePath)
            if (bytes != null && bytes.isNotEmpty()) {
                val decoded = runCatching {
                    json.decodeFromString<FolioPersistedState>(bytes.decodeToString())
                }.getOrNull()
                if (decoded != null && decoded.documents.isNotEmpty()) {
                    var anyUpgraded = false
                    val upgradedDocs = decoded.documents.map { doc ->
                        if (doc.parserVersion < 3 && doc.filePath != null) {
                            val rawBytes = PlatformFileAndZipIo.readFileBytes(doc.filePath)
                            if (rawBytes != null) {
                                anyUpgraded = true
                                val fileName = doc.filePath.substringAfterLast('\\').substringAfterLast('/')
                                val reparsed = DocumentCodec.importFromBytes(fileName, rawBytes, doc.filePath)
                                reparsed.copy(
                                    id = doc.id,
                                    currentPage = doc.currentPage.coerceIn(1, reparsed.totalPages),
                                    parserVersion = 3,
                                )
                            } else {
                                doc.copy(parserVersion = 3)
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
        val current = _state.value
        runCatching {
            val encoded = json.encodeToString(FolioPersistedState.serializer(), current)
            PlatformFileAndZipIo.writeFileBytes(storageFilePath, encoded.encodeToByteArray())
        }
    }

    fun currentBook(): BookDocument? {
        val s = _state.value
        return s.documents.find { it.id == s.selectedBookId } ?: s.documents.firstOrNull()
    }

    fun selectBook(bookId: String) {
        _state.update { it.copy(selectedBookId = bookId) }
        saveCurrentState()
    }

    fun setViewMode(mode: PageViewMode) {
        _state.update { it.copy(viewMode = mode) }
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

    fun setFontSizePt(fontSizePt: Float) {
        val clamped = fontSizePt.coerceIn(13f, 26f)
        _state.update { it.copy(fontSizePt = clamped) }
        saveCurrentState()
    }

    fun setLineSpacingMultiplier(multiplier: Float) {
        val clamped = multiplier.coerceIn(1.3f, 2.4f)
        _state.update { it.copy(lineSpacingMultiplier = clamped) }
        saveCurrentState()
    }

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
            current.copy(
                documents = updatedDocs,
                telemetry = current.telemetry + (bookId to updatedTel),
            )
        }
        saveCurrentState()
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
        _state.update { current ->
            val updatedDocs = current.documents.map { doc ->
                if (doc.id != bookId) {
                    doc
                } else {
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
                    doc.copy(pages = updatedPages, updatedAtMs = now)
                }
            }
            val existingTel = current.telemetry[bookId]
            val updatedTelMap = if (existingTel != null) {
                current.telemetry + (bookId to existingTel.copy(recalculationCount = existingTel.recalculationCount + 1))
            } else {
                current.telemetry
            }
            current.copy(documents = updatedDocs, telemetry = updatedTelMap)
        }
        saveCurrentState()
    }

    fun updateDocumentMetadata(
        bookId: String,
        newTitle: String,
        newSubtitle: String,
        newAuthor: String,
        newFormat: DocumentFormat,
    ) {
        val now = PlatformFileAndZipIo.currentEpochMillis()
        _state.update { current ->
            val updatedDocs = current.documents.map { doc ->
                if (doc.id == bookId) {
                    doc.copy(
                        title = newTitle.trim().ifBlank { doc.title },
                        subtitle = newSubtitle.trim(),
                        author = newAuthor.trim().ifBlank { doc.author },
                        format = newFormat,
                        updatedAtMs = now,
                    )
                } else {
                    doc
                }
            }
            current.copy(documents = updatedDocs)
        }
        saveCurrentState()
    }

    fun addNewPage(bookId: String, afterPageNumber: Int? = null, chapterTitle: String = "New Folio Leaf") {
        val now = PlatformFileAndZipIo.currentEpochMillis()
        _state.update { current ->
            val doc = current.documents.find { it.id == bookId } ?: return@update current
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
            )
        }
        saveCurrentState()
    }

    fun deletePage(bookId: String, pageNumber: Int) {
        val now = PlatformFileAndZipIo.currentEpochMillis()
        _state.update { current ->
            val doc = current.documents.find { it.id == bookId } ?: return@update current
            if (doc.pages.size <= 1) return@update current
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
                            "This ${format.label} manuscript was created in Meridian Folio. Switch to the Editor tab to add paragraphs, quotes, and additional pages, or export directly to a .${format.extension} file.",
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
        val bytes = PlatformFileAndZipIo.readFileBytes(path) ?: return null
        val fileName = path.substringAfterLast('\\').substringAfterLast('/')
        val doc = DocumentCodec.importFromBytes(
            fileName = fileName,
            bytes = bytes,
            filePath = path,
        )
        _state.update { current ->
            val filtered = current.documents.filterNot { it.filePath == path || it.id == doc.id }
            current.copy(
                documents = listOf(doc) + filtered,
                selectedBookId = doc.id,
            )
        }
        saveCurrentState()
        return doc
    }

    fun exportDocumentToFile(bookId: String, targetPath: String): Boolean {
        val doc = _state.value.documents.find { it.id == bookId } ?: return false
        val bytes = DocumentCodec.exportToBytes(doc)
        return PlatformFileAndZipIo.writeFileBytes(targetPath, bytes)
    }
}

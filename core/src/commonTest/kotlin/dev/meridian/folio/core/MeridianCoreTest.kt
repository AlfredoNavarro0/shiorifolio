package dev.meridian.folio.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class MeridianCoreTest {

    @Test
    fun finishPredictionRecalculatesOverTimeAsReadingVelocityChanges() {
        val repo = FolioRepository(persistToDisk = false)
        val book = repo.currentBook()!!
        repo.goToPage(book.id, 1)

        val initialPrediction = ReadingPredictionEngine.computePrediction(
            book = repo.currentBook()!!,
            telemetry = repo.state.value.telemetry[book.id],
        )
        assertTrue(initialPrediction.estimatedRemainingSeconds > 0.0)
        val initialRecalcCount = initialPrediction.recalculationCount

        repo.recordReadingTimeTick(book.id, 12.0)
        repo.recordReadingTimeTick(book.id, 10.0)
        repo.goToPage(book.id, 2)
        repo.recordReadingTimeTick(book.id, 8.0)

        val updatedPrediction = ReadingPredictionEngine.computePrediction(
            book = repo.currentBook()!!,
            telemetry = repo.state.value.telemetry[book.id],
        )

        assertTrue(updatedPrediction.recalculationCount > initialRecalcCount)
        assertTrue(updatedPrediction.activeReadingSeconds >= initialPrediction.activeReadingSeconds + 30.0)
        assertEquals(2, updatedPrediction.currentPage)
    }

    @Test
    fun supportsAllFourViewModesAndWhiteDarkThemes() {
        val repo = FolioRepository(persistToDisk = false)
        for (mode in PageViewMode.entries) {
            repo.setViewMode(mode)
            assertEquals(mode, repo.state.value.viewMode)
        }

        assertEquals(ReaderTheme.WHITE, repo.state.value.theme)
        repo.toggleTheme()
        assertEquals(ReaderTheme.DARK, repo.state.value.theme)
        repo.toggleTheme()
        assertEquals(ReaderTheme.WHITE, repo.state.value.theme)
    }

    @Test
    fun pageReminderAndSavedWordsPhrasesAndEditorPersistInRepository() {
        val repo = FolioRepository(persistToDisk = false)
        val book = repo.currentBook()!!

        repo.setPageReminder(
            bookId = book.id,
            pageNumber = 4,
            note = "Resume at Chapter IV — Lanterns on the High Span",
            targetLabel = "Tomorrow Morning",
        )
        val reminder = repo.state.value.reminders[book.id]
        assertNotNull(reminder)
        assertEquals(4, reminder.pageNumber)
        assertTrue(reminder.note.contains("Lanterns"))

        val saved = repo.saveWordOrPhrase(
            bookId = book.id,
            pageNumber = 3,
            phrase = "deep forest pigment of Meridian",
            contextSentence = "Unlike black lamp-soot ink, the deep forest pigment of Meridian (#2b3a30) is ground from verdigris.",
            note = "Color palette note",
            tone = HighlightTone.TERRACOTTA,
        )
        assertNotNull(saved)
        assertTrue(repo.state.value.savedPhrases.any { it.phrase == "deep forest pigment of Meridian" })

        repo.addNewPage(book.id, afterPageNumber = 2, chapterTitle = "Inserted Archival Leaf")
        val updatedBook = repo.currentBook()!!
        assertEquals(book.totalPages + 1, updatedBook.totalPages)

        val epubBytes = DocumentCodec.generateEpubBytes(updatedBook)
        assertTrue(epubBytes.isNotEmpty())
        val parsedEpub = DocumentCodec.parseEpubBytes("exported.epub", epubBytes)
        assertEquals(updatedBook.title, parsedEpub.title)
        assertTrue(parsedEpub.pages.isNotEmpty())

        val pdfBytes = DocumentCodec.generatePdfBytes(updatedBook.copy(format = DocumentFormat.PDF))
        assertTrue(pdfBytes.isNotEmpty())
        val parsedPdf = DocumentCodec.parsePdfBytes("exported.pdf", pdfBytes)
        assertTrue(parsedPdf.pages.isNotEmpty())
        val allText = parsedPdf.pages.joinToString("\n") { it.plainText }
        assertTrue(allText.contains("Meridian Viaduct"))
    }

    @Test
    fun parsesRealLightNovelEpubWithSpineOrderImagesAndDialogue() {
        val epubPath = """c:\Users\terra\Downloads\The Gal Is Sitting Behind Me, and Loves Me vol 1 - Fungus FTL.epub"""
        val bytes = PlatformFileAndZipIo.readFileBytes(epubPath) ?: return
        val doc = DocumentCodec.parseEpubBytes(
            fileName = "The Gal Is Sitting Behind Me, and Loves Me vol 1 - Fungus FTL.epub",
            bytes = bytes,
            filePath = epubPath,
        )
        assertTrue(doc.pages.size >= 20, "Expected full light novel pages, got ${doc.pages.size}")
        // Page 1 is Cover.xhtml with cover illustration
        val coverPage = doc.pages.first()
        assertTrue(coverPage.blocks.any { it.style == BlockStyle.IMAGE && !it.imagePath.isNullOrBlank() })

        // Verify Prologue and Chapter 1 text are extracted cleanly in order
        val fullBookText = doc.pages.joinToString("\n") { it.plainText }
        assertTrue(fullBookText.contains("Seeing you with your foot in the gutter is kinda eye-catching"))
        assertTrue(fullBookText.contains("Sandai lent his own clothes to Shino"))
    }

    @Test
    fun perBookViewPreferenceRemembersViewModeFitZoomAndPositionPerBook() {
        val repo = FolioRepository(persistToDisk = false)
        val docs = repo.state.value.documents
        assertTrue(docs.size >= 2)
        val bookA = docs[0]
        val bookB = docs[1]

        repo.selectBook(bookA.id)
        repo.setViewMode(PageViewMode.VERTICAL_CONTINUOUS)
        repo.setPageFitMode(PageFitMode.MANUAL_ZOOM)
        repo.setZoomScale(1.45f)
        repo.goToPage(bookA.id, 3)

        repo.selectBook(bookB.id)
        repo.setViewMode(PageViewMode.REFLOW_TEXT)
        repo.setPageFitMode(PageFitMode.FIT_WIDTH)
        repo.goToPage(bookB.id, 2)

        // Switch back to bookA and verify its viewMode, fitMode, zoomScale, and lastPage are restored
        repo.selectBook(bookA.id)
        assertEquals(PageViewMode.VERTICAL_CONTINUOUS, repo.state.value.viewMode)
        assertEquals(PageFitMode.MANUAL_ZOOM, repo.state.value.fitMode)
        assertEquals(1.45f, repo.state.value.zoomScale)
        assertEquals(3, repo.currentBook()!!.currentPage)

        // Switch back to bookB and verify its preferences are restored
        repo.selectBook(bookB.id)
        assertEquals(PageViewMode.REFLOW_TEXT, repo.state.value.viewMode)
        assertEquals(PageFitMode.FIT_WIDTH, repo.state.value.fitMode)
        assertEquals(2, repo.currentBook()!!.currentPage)
    }

    @Test
    fun pdfReflowEngineJoinsSplitParagraphsStripsHeadersAndDetectsHyphenationAndSceneBreaks() {
        val syntheticPdf = BookDocument(
            id = "test-pdf-reflow",
            title = "Treatise on Bridges",
            subtitle = "Edition I",
            author = "A. Verdigris",
            format = DocumentFormat.PDF,
            currentPage = 1,
            pages = listOf(
                BookPage(
                    pageNumber = 1,
                    chapterTitle = "Chapter I",
                    blocks = listOf(
                        ContentBlock("b1", BlockStyle.PARAGRAPH, "SHIORIFOLIO · FOLIO 1"),
                        ContentBlock(
                            "b2",
                            BlockStyle.PARAGRAPH,
                            "The archi-\ntectural span of the viaduct was designed to endure centuries, and it was built in",
                        ),
                        ContentBlock("b3", BlockStyle.PARAGRAPH, "1"),
                    ),
                ),
                BookPage(
                    pageNumber = 2,
                    chapterTitle = "Chapter I",
                    blocks = listOf(
                        ContentBlock("b4", BlockStyle.PARAGRAPH, "FOLIO 2"),
                        ContentBlock(
                            "b5",
                            BlockStyle.PARAGRAPH,
                            "the autumn of 1894 using hand-dressed limestone blocks from the northern quarry.",
                        ),
                        ContentBlock("b6", BlockStyle.PARAGRAPH, "* * *"),
                        ContentBlock("b7", BlockStyle.PARAGRAPH, "After the dedication ceremony, lanterns lit the river."),
                    ),
                ),
                BookPage(
                    pageNumber = 3,
                    chapterTitle = "Plate III",
                    blocks = listOf(
                        ContentBlock("b8", BlockStyle.IMAGE, "Scanned plate", imagePath = "__PDF_PAGE_RENDER__:3"),
                    ),
                ),
            ),
        )

        val result = PdfReflowEngine.buildContinuousFlow(syntheticPdf, normalizeSpaces = true)
        assertTrue(result.mergedPageBreakCount >= 1, "Should join paragraph split across page 1 -> 2")
        assertTrue(result.dehyphenatedWordCount >= 1, "Should dehyphenate archi-\\ntectural")
        assertTrue(result.strippedHeaderFooterCount >= 2, "Should strip running FOLIO headers and page numbers")
        assertTrue(result.scannedPageNumbers.contains(3), "Should detect page 3 as a scanned/image-only page")

        val combinedText = result.items.joinToString("\n") { it.text }
        assertTrue(combinedText.contains("architectural span of the viaduct"))
        assertTrue(combinedText.contains("built in the autumn of 1894"))
        assertTrue(result.items.any { it.isSceneBreak || PdfReflowEngine.isSemanticSceneBreak(it.text) })
    }
}

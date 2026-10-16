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
}

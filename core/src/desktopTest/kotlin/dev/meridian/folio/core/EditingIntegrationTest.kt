package dev.meridian.folio.core

import java.io.File
import java.nio.file.Files
import kotlin.test.*
import kotlinx.serialization.json.Json

class EditingIntegrationTest {
    private fun <T> temporaryLibrary(block: (File, FolioRepository) -> T): T {
        val root = Files.createTempDirectory("shiorifolio-editing-test-").toFile()
        try {
            return block(root, FolioRepository(persistToDisk = false))
        } finally {
            root.deleteRecursively()
        }
    }

    private fun samplePages(): List<BookPage> = listOf(
        BookPage(1, "Chapter One", listOf(ContentBlock("b1", BlockStyle.PARAGRAPH, "The quick brown fox jumps over the lazy dog."))),
        BookPage(2, "Chapter Two", listOf(ContentBlock("b2", BlockStyle.PARAGRAPH, "Quick thinking saved the fox."))),
    )

    @Test
    fun searchFindsMatchesByScopeAndCase() {
        val pages = samplePages()
        val all = BookTextSearch.findMatches(pages, "Quick")
        assertEquals(2, all.size)
        assertEquals(listOf(1, 2), all.map { it.pageNumber })
        assertEquals("The ", all.first().snippetBefore)
        assertTrue(all.first().snippetAfter.startsWith(" brown"))
        assertTrue(all.first().matchedText.equals("quick", ignoreCase = true))

        val caseSensitive = BookTextSearch.findMatches(pages, "Quick", ignoreCase = false)
        assertEquals(listOf(2), caseSensitive.map { it.pageNumber })

        val scoped = BookTextSearch.findMatches(pages, "fox", chapterTitle = "Chapter Two")
        assertEquals(listOf(2), scoped.map { it.pageNumber })

        assertTrue(BookTextSearch.findMatches(pages, " ").isEmpty())
        assertTrue(BookTextSearch.findMatches(pages, "zebra").isEmpty())
    }

    @Test
    fun replaceRewritesLiterallyByScopeAndSingleOccurrence() {
        val pages = samplePages()
        val outcome = BookTextReplace.replaceAll(pages, "fox", "cat")
        assertEquals(2, outcome.replacedCount)
        assertEquals(listOf(1, 2), outcome.affectedPageNumbers)
        assertTrue(outcome.pages[0].blocks[0].text.contains("cat"))
        assertFalse(outcome.pages[0].blocks[0].text.contains("fox"))

        val scoped = BookTextReplace.replaceAll(pages, "fox", "cat", chapterTitle = "Chapter One")
        assertEquals(1, scoped.replacedCount)
        assertFalse(scoped.pages[1].blocks[0].text.contains("cat"))

        val one = BookTextReplace.replaceOne(pages, 1, "b1", 4, "quick", "slow")
        assertEquals(1, one.replacedCount)
        assertEquals("The slow brown fox jumps over the lazy dog.", one.pages[0].blocks[0].text)

        // A stale index (text changed since the preview) must be a safe no-op.
        assertEquals(0, BookTextReplace.replaceOne(pages, 1, "b1", 5, "quick", "slow").replacedCount)
        // Replacement strings with regex metacharacters are literal.
        val dollar = BookTextReplace.replaceAll(pages, "fox", "\$1 \\d")
        assertEquals(2, dollar.replacedCount)
        assertTrue(dollar.pages[0].blocks[0].text.contains("\$1 \\d"))
    }

    @Test
    fun reanchoringMovesPhrasesWithContextAndFlagsOrphans() {
        val phrase = SavedPhrase(
            id = "p1",
            bookId = "book",
            bookTitle = "B",
            pageNumber = 1,
            phrase = "fox",
            contextSentence = "",
            createdAtMs = 1,
            anchorPrefix = "quick brown ",
            anchorSuffix = " jumps over",
            anchorChapterTitle = "One",
        )
        val moved = AnnotationAnchorer.reanchor(
            phrase,
            listOf(
                BookPage(1, "One", listOf(ContentBlock("a", BlockStyle.PARAGRAPH, "The lazy dog slept."))),
                BookPage(2, "Two", listOf(ContentBlock("b", BlockStyle.PARAGRAPH, "The quick brown fox jumps over the fence."))),
            ),
        )
        assertEquals(2, moved.pageNumber)
        assertEquals(AnchorStatus.ACTIVE, moved.anchorStatus)

        val orphan = AnnotationAnchorer.reanchor(
            phrase,
            listOf(BookPage(1, "One", listOf(ContentBlock("a", BlockStyle.PARAGRAPH, "Nothing here.")))),
        )
        assertEquals(AnchorStatus.ORPHANED, orphan.anchorStatus)
        assertEquals(1, orphan.pageNumber)

        // Duplicate phrase text: the anchor context disambiguates.
        val disambiguated = AnnotationAnchorer.reanchor(
            phrase,
            listOf(
                BookPage(1, "One", listOf(ContentBlock("a", BlockStyle.PARAGRAPH, "A fox appeared in the yard."))),
                BookPage(3, "Two", listOf(ContentBlock("b", BlockStyle.PARAGRAPH, "The quick brown fox jumps over the fence."))),
            ),
        )
        assertEquals(3, disambiguated.pageNumber)

        // Already-orphaned phrases stay unsigned for manual re-anchoring.
        val stays = AnnotationAnchorer.reanchor(orphan, listOf(BookPage(1, "One", listOf(ContentBlock("a", BlockStyle.PARAGRAPH, "Still nothing.")))))
        assertEquals(AnchorStatus.ORPHANED, stays.anchorStatus)

        // Text found only with a fully broken context: orphaned for manual re-anchoring,
        // so the reader picks the right occurrence instead of a silent move.
        val brokenContext = AnnotationAnchorer.reanchor(
            phrase,
            listOf(
                BookPage(1, "One", listOf(ContentBlock("a", BlockStyle.PARAGRAPH, "Nothing here."))),
                BookPage(5, "Five", listOf(ContentBlock("b", BlockStyle.PARAGRAPH, "A fox appeared in the yard."))),
            ),
        )
        assertEquals(AnchorStatus.ORPHANED, brokenContext.anchorStatus)
    }

    @Test
    fun chapterRenameMoveAndRepair() {
        val pages = listOf(
            BookPage(1, "Chapter One", listOf(ContentBlock("h1", BlockStyle.HEADING, "Chapter One"), ContentBlock("p1", BlockStyle.PARAGRAPH, "alpha"))),
            BookPage(2, "Chapter One (p. 2)", listOf(ContentBlock("p2", BlockStyle.PARAGRAPH, "beta"))),
            BookPage(3, "Chapter Two", listOf(ContentBlock("h2", BlockStyle.HEADING, "Chapter Two"), ContentBlock("p3", BlockStyle.PARAGRAPH, "gamma"))),
            BookPage(4, "Chapter Two (p. 2)", listOf(ContentBlock("p4", BlockStyle.PARAGRAPH, "delta"))),
        )
        val renamed = ChapterEditing.renameChapter(pages, 1, "Primer Capítulo")
        assertEquals("Primer Capítulo", renamed[0].chapterTitle)
        assertEquals("Primer Capítulo", renamed[1].chapterTitle)
        assertEquals("Primer Capítulo", renamed[0].blocks.first().text)
        assertEquals("Chapter Two", renamed[2].chapterTitle)

        val movedUp = ChapterEditing.moveChapter(pages, 3, upwards = true)!!
        assertEquals(
            listOf("Chapter Two", "Chapter Two", "Chapter One", "Chapter One"),
            movedUp.map { ChapterEditing.cleanTitle(it.chapterTitle) },
        )
        assertEquals(listOf(1, 2, 3, 4), movedUp.map { it.pageNumber })
        assertEquals("gamma", movedUp[0].blocks.last().text)
        assertEquals("alpha", movedUp[2].blocks.last().text)
        assertNull(ChapterEditing.moveChapter(pages, 1, upwards = true))

        val messy = listOf(
            BookPage(1, "PORTADA", listOf(ContentBlock("h", BlockStyle.HEADING, "Casa"))),
            BookPage(2, "Casa (p. 2)", listOf(ContentBlock("p", BlockStyle.PARAGRAPH, "x"))),
            BookPage(3, "Título raro (p. 3)", listOf(ContentBlock("h3", BlockStyle.HEADING, "Jardín"))),
            BookPage(4, "Jardín (p. 4)", listOf(ContentBlock("p4", BlockStyle.PARAGRAPH, "y"))),
        )
        val repaired = ChapterEditing.repairTitles(messy)
        assertEquals(listOf("Casa", "Casa", "Jardín", "Jardín"), repaired.map { it.chapterTitle })

        // Books without headings (PDF-style titles) keep their distinct titles.
        val pdfLike = listOf(
            BookPage(1, "Novela — Page 1", listOf(ContentBlock("a", BlockStyle.PARAGRAPH, "uno"))),
            BookPage(2, "Novela — Page 2", listOf(ContentBlock("b", BlockStyle.PARAGRAPH, "dos"))),
        )
        assertEquals(
            listOf("Novela — Page 1", "Novela — Page 2"),
            ChapterEditing.repairTitles(pdfLike).map { it.chapterTitle },
        )
    }

    @Test
    fun pageEditingReanchorsPhrasesAndUndoRedoAreSessionScoped() = temporaryLibrary { _, repo ->
        val book = repo.state.value.documents.first { it.format == DocumentFormat.EPUB }
        val page1 = book.pages.first()
        val block = page1.blocks.first { it.style != BlockStyle.IMAGE && it.text.length > 40 }
        val phraseText = block.text.substring(0, 24).trim()
        val saved = repo.saveWordOrPhrase(book.id, page1.pageNumber, phraseText, block.text)!!
        assertEquals(AnchorStatus.ACTIVE, saved.anchorStatus)
        assertTrue(saved.anchorPrefix.isNotEmpty() || saved.anchorSuffix.isNotEmpty())

        val page2 = book.pages[1]
        repo.updatePageContent(
            book.id,
            page1.pageNumber,
            page1.chapterTitle,
            page1.blocks.map { if (it.id == block.id) it.copy(text = it.text.replace(phraseText, "")) else it },
        )
        assertEquals(AnchorStatus.ORPHANED, repo.state.value.savedPhrases.first { it.id == saved.id }.anchorStatus)

        repo.updatePageContent(
            book.id,
            page2.pageNumber,
            page2.chapterTitle,
            page2.blocks + ContentBlock("moved_block", BlockStyle.PARAGRAPH, block.text),
        )
        val reanchored = repo.state.value.savedPhrases.first { it.id == saved.id }
        assertEquals(page2.pageNumber, reanchored.pageNumber)
        assertEquals(AnchorStatus.ACTIVE, reanchored.anchorStatus)

        assertTrue(book.id in repo.editHistoryState.value.undoBooks)
        assertTrue(repo.undoEdit(book.id))
        assertEquals(AnchorStatus.ORPHANED, repo.state.value.savedPhrases.first { it.id == saved.id }.anchorStatus)
        assertTrue(repo.undoEdit(book.id))
        val restored = repo.state.value.documents.first { it.id == book.id }
        assertEquals(block.text, restored.pages.first().blocks.find { it.id == block.id }!!.text)
        val original = repo.state.value.savedPhrases.first { it.id == saved.id }
        assertEquals(AnchorStatus.ACTIVE, original.anchorStatus)
        assertEquals(page1.pageNumber, original.pageNumber)
        assertFalse(repo.canUndoEdit(book.id))
        assertTrue(repo.canRedoEdit(book.id))
        assertTrue(repo.redoEdit(book.id))
        assertEquals(AnchorStatus.ORPHANED, repo.state.value.savedPhrases.first { it.id == saved.id }.anchorStatus)
        // A fresh edit discards the redo branch.
        repo.updatePageContent(book.id, page1.pageNumber, page1.chapterTitle, page1.blocks)
        assertFalse(repo.canRedoEdit(book.id))
    }

    @Test
    fun metadataEditingUpdatesDenormalizedCopiesAndUndoes() = temporaryLibrary { _, repo ->
        val book = repo.state.value.documents.first { it.format == DocumentFormat.EPUB }
        val saved = repo.saveWordOrPhrase(book.id, 1, "cosa", "una cosa cualquiera")!!
        repo.setPageReminder(book.id, 1, "nota")
        repo.updateDocumentMetadata(book.id, "Título Nuevo", "Autora Nueva", newLanguage = "es")
        val updated = repo.state.value.documents.first { it.id == book.id }
        assertEquals("Título Nuevo", updated.title)
        assertEquals("Autora Nueva", updated.author)
        assertEquals("es", updated.language)
        assertEquals("Título Nuevo", repo.state.value.savedPhrases.first { it.id == saved.id }.bookTitle)
        assertEquals("Título Nuevo", repo.state.value.reminders[book.id]?.bookTitle)

        assertTrue(repo.undoEdit(book.id))
        assertEquals(book.title, repo.state.value.documents.first { it.id == book.id }.title)
        assertEquals(book.title, repo.state.value.savedPhrases.first { it.id == saved.id }.bookTitle)
        assertEquals(book.title, repo.state.value.reminders[book.id]?.bookTitle)
    }

    @Test
    fun chapterOperationsAndReplaceThroughRepository() = temporaryLibrary { _, repo ->
        val doc = repo.createNewDocument("Mi Libro", "Autor", DocumentFormat.EPUB)
        repo.addNewPage(doc.id, afterPageNumber = 1, chapterTitle = "Capítulo Dos")

        val replaced = repo.replaceAllText(doc.id, "Meridian", "Shiori")
        assertTrue(replaced >= 1)
        assertTrue(repo.state.value.documents.first { it.id == doc.id }.pages.first().plainText.contains("Shiori"))
        assertTrue(repo.undoEdit(doc.id))
        assertTrue(repo.state.value.documents.first { it.id == doc.id }.pages.first().plainText.contains("Meridian"))

        assertTrue(repo.renameChapter(doc.id, 1, "Uno Renombrado"))
        val renamed = repo.state.value.documents.first { it.id == doc.id }
        assertEquals("Uno Renombrado", renamed.pages.first().chapterTitle)

        assertTrue(repo.moveChapter(doc.id, 2, upwards = true))
        val moved = repo.state.value.documents.first { it.id == doc.id }
        assertEquals("Capítulo Dos", moved.pages.first().chapterTitle)
        assertEquals(1, moved.pages.first().pageNumber)
        assertEquals("Uno Renombrado", moved.pages.last().chapterTitle)
        assertFalse(repo.moveChapter(doc.id, 1, upwards = true))

        repo.updatePageContent(
            doc.id,
            1,
            "Cosa (p. 9)",
            listOf(ContentBlock("c1", BlockStyle.HEADING, "Casa"), ContentBlock("c2", BlockStyle.PARAGRAPH, "texto")),
        )
        assertTrue(repo.repairTableOfContents(doc.id))
        assertEquals("Casa", repo.state.value.documents.first { it.id == doc.id }.pages.first().chapterTitle)

        // Everything above is one session of editing: undo walks it back.
        var guard = 0
        while (repo.canUndoEdit(doc.id) && guard++ < 20) repo.undoEdit(doc.id)
        val walkedBack = repo.state.value.documents.first { it.id == doc.id }
        assertFalse(repo.canUndoEdit(doc.id))
        assertEquals("Chapter I — Opening Leaf", walkedBack.pages.first().chapterTitle)
        assertEquals(1, walkedBack.pages.size)
    }

    @Test
    fun backupRoundTripRestoresPersonalDataAndReportsMissingBooks() = temporaryLibrary { root, repo ->
        val bytes = DocumentCodec.exportToBytes(repo.state.value.documents.first { it.format == DocumentFormat.EPUB })
        val file = File(root, "novela.epub").apply { writeBytes(bytes) }
        val imported = repo.library.importFile(file.path, select = true)
        repo.goToPage(imported.id, 2)
        repo.saveWordOrPhrase(imported.id, 2, "frase de prueba", "contexto de la frase de prueba")
        repo.setPageReminder(imported.id, 2, "Retomar capítulo")
        repo.library.createCollection("Viaje")
        repo.library.assignCollection(setOf(imported.id), repo.state.value.collections.single().id, true)
        repo.library.setStatus(setOf(imported.id), LibraryStatus.READING)
        repo.setTheme(ReaderTheme.DARK)

        val backupPath = File(root, "respaldo.json").path
        val export = repo.exportBackupToFile(backupPath)!!
        assertEquals(1, export.booksIncluded)
        assertEquals(1, export.phrasesIncluded)
        assertTrue(export.booksSkipped >= 1)

        val otherDir = File(root, "otro").apply { mkdirs() }
        val otherFile = File(otherDir, "novela.epub").apply { writeBytes(bytes) }
        val repo2 = FolioRepository(persistToDisk = false)
        repo2.library.importFile(otherFile.path)
        val peek = repo2.peekBackupFile(backupPath)!!
        assertEquals(1, peek.documentCount)
        assertEquals(1, peek.phraseCount)
        assertEquals(1, peek.reminderCount)

        val summary = repo2.importBackupFromFile(backupPath)!!
        assertEquals(1, summary.booksMatched)
        assertEquals(0, summary.booksMissing)
        assertEquals(1, summary.phrasesRestored)
        val restored = repo2.state.value.documents.first { it.id == imported.id }
        assertEquals(2, restored.currentPage)
        assertEquals(LibraryStatus.READING, restored.libraryStatus)
        assertEquals(setOf(repo2.state.value.collections.single().id), restored.collectionIds)
        assertEquals("Retomar capítulo", repo2.state.value.reminders[imported.id]?.note)
        assertEquals(ReaderTheme.DARK, repo2.state.value.theme)
        assertEquals(1, repo2.state.value.savedPhrases.count { it.bookId == imported.id })

        // Somewhere without the book file: nothing is applied but everything is reported.
        val repo3 = FolioRepository(persistToDisk = false)
        val summary3 = repo3.importBackupFromFile(backupPath)!!
        assertEquals(0, summary3.booksMatched)
        assertEquals(1, summary3.booksMissing)
        assertEquals(1, summary3.phrasesSkipped)
        assertEquals(1, summary3.remindersSkipped)

        // A non-backup file is rejected without touching the state.
        val bogus = File(root, "no-es-respaldo.json").apply { writeText("""{"hola":1}""") }
        assertNull(repo.peekBackupFile(bogus.path))
        assertNull(repo.importBackupFromFile(bogus.path))
    }

    @Test
    fun exportedEpubRegeneratesNavigationLanguageCoverAndValidates() = temporaryLibrary { root, repo ->
        val coverFile = File(root, "cover.png").apply { writeBytes(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 1, 2, 3)) }
        val base = repo.state.value.documents.first { it.format == DocumentFormat.EPUB }
        val doc = base.copy(language = "es", coverImagePath = coverFile.path, pages = base.pages.take(3))

        val bytes = DocumentCodec.exportToBytes(doc)
        val entries = PlatformFileAndZipIo.unzipEntries(bytes)
        assertTrue(entries.containsKey("OEBPS/nav.xhtml"))
        assertTrue(entries.containsKey("OEBPS/toc.ncx"))
        assertTrue(entries.containsKey("OEBPS/images/cover.png"))
        val opf = entries.getValue("OEBPS/content.opf").decodeToString()
        assertTrue(opf.contains("<dc:language>es</dc:language>"))
        assertTrue(opf.contains("properties=\"nav\""))
        assertTrue(opf.contains("cover-image"))
        assertTrue(opf.contains("dcterms:modified"))
        assertTrue(entries.getValue("OEBPS/nav.xhtml").decodeToString().contains("epub:type=\"toc\""))

        val report = EpubExportValidator.validateStructure(bytes)
        assertTrue(report.errors.isEmpty(), "Errores inesperados: ${report.errors}")

        // The exported EPUB parses back with its metadata intact.
        val reimported = DocumentCodec.importFromBytes("reexport.epub", bytes, null)
        assertEquals(3, reimported.pages.size)
        assertEquals("es", reimported.language)
        assertEquals(doc.title, reimported.title)

        // The validator actually detects structural breakage.
        val broken = PlatformFileAndZipIo.zipEntries(entries.filterKeys { it != "OEBPS/page_2.xhtml" })
        val brokenReport = EpubExportValidator.validateStructure(broken)
        assertTrue(brokenReport.errors.isNotEmpty())
    }

    @Test
    fun reattachPhraseRevivesOrphanThroughManualAnchor() = temporaryLibrary { _, repo ->
        val book = repo.state.value.documents.first { it.format == DocumentFormat.EPUB }
        val page1 = book.pages.first()
        val block = page1.blocks.first { it.style != BlockStyle.IMAGE && it.text.length > 40 }
        val phraseText = block.text.substring(0, 20).trim()
        val saved = repo.saveWordOrPhrase(book.id, 1, phraseText, block.text)!!
        val page2 = book.pages[1]
        repo.updatePageContent(
            book.id,
            page1.pageNumber,
            page1.chapterTitle,
            page1.blocks.map { if (it.id == block.id) it.copy(text = it.text.replace(phraseText, "")) else it },
        )
        assertEquals(AnchorStatus.ORPHANED, repo.state.value.savedPhrases.first { it.id == saved.id }.anchorStatus)

        val movedBlock = ContentBlock("moved", BlockStyle.PARAGRAPH, block.text)
        repo.updatePageContent(book.id, page2.pageNumber, page2.chapterTitle, page2.blocks + movedBlock)
        // Force it back to orphan by removing the text again, then re-attach manually.
        repo.updatePageContent(book.id, page2.pageNumber, page2.chapterTitle, page2.blocks)
        assertEquals(AnchorStatus.ORPHANED, repo.state.value.savedPhrases.first { it.id == saved.id }.anchorStatus)
        repo.reattachPhrase(saved.id, page1.pageNumber, page1.chapterTitle, "prefijo manual", "sufijo manual")
        val revived = repo.state.value.savedPhrases.first { it.id == saved.id }
        assertEquals(AnchorStatus.ACTIVE, revived.anchorStatus)
        assertEquals(page1.pageNumber, revived.pageNumber)
        assertEquals("prefijo manual", revived.anchorPrefix)
    }

    @Test
    fun legacyStateDecodesWithAnchorDefaults() {
        val json = Json { encodeDefaults = true }
        val legacyPhrase = json.decodeFromString<SavedPhrase>(
            """{"id":"p","bookId":"b","bookTitle":"t","pageNumber":3,"phrase":"x","contextSentence":"y","createdAtMs":1}""",
        )
        assertEquals(AnchorStatus.ACTIVE, legacyPhrase.anchorStatus)
        assertEquals("", legacyPhrase.anchorPrefix)
        assertEquals("", legacyPhrase.anchorSuffix)
        assertEquals("", legacyPhrase.anchorChapterTitle)

        val legacyDoc = json.decodeFromString<BookDocument>(
            """{"id":"d","title":"T","author":"A","format":"EPUB","pages":[]}""",
        )
        assertEquals("", legacyDoc.language)
        assertNull(legacyDoc.coverImagePath)
    }
}

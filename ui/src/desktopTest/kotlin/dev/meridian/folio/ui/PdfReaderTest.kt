package dev.meridian.folio.ui

import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.*
import dev.meridian.folio.core.*
import java.io.File
import org.jetbrains.skia.Image
import org.jetbrains.skia.EncodedImageFormat
import kotlin.test.*

@OptIn(ExperimentalTestApi::class)
class PdfReaderTest {
    @Test
    fun realLightNovelKeepsPageTwentyAndTurnsToTwentyOne() = runDesktopComposeUiTest(width = 1440, height = 900) {
        val file = File(System.getProperty("user.home"), "Downloads/The Gal Is Sitting Behind Me, and Loves Me vol 1 - Fungus FTL.pdf")
        org.junit.Assume.assumeTrue("Local reference PDF is optional outside the author's machine", file.isFile)
        val repo = FolioRepository(persistToDisk = false)
        val book = assertNotNull(repo.importDocumentFromFile(file.absolutePath))
        repo.setViewMode(PageViewMode.ONE_COLUMN)
        repo.goToPage(book.id, 20)
        setContent { MeridianFolioApp(repository = repo, initialSection = MeridianSection.READER) }
        waitUntil(timeoutMillis = 20_000) { onAllNodesWithTag("pdf_page_image_20").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("paged_body_20").assertDoesNotExist()
        val output = File("build/visual/pdf-real-page-20.png")
        output.parentFile.mkdirs()
        Image.makeFromBitmap(onNodeWithTag("meridian_root").captureToImage().asSkiaBitmap()).use { image ->
            image.encodeToData(EncodedImageFormat.PNG)!!.use { output.writeBytes(it.bytes) }
        }
        onNodeWithTag("next_page_button").performClick()
        assertEquals(21, repo.currentBook()!!.currentPage)
        onNodeWithTag("meridian_root").performKeyInput { pressKey(Key.PageUp) }
        assertEquals(20, repo.currentBook()!!.currentPage)
    }

    private fun importedPdf(): FolioRepository {
        val document = BookDocument("pdf-reader-test", "Lectura PDF", author = "ShioriFolio", format = DocumentFormat.PDF,
            pages = (1..4).map { page -> BookPage(page, "Capítulo $page", listOf(ContentBlock("body_$page",
                text = "Página $page. Este párrafo extenso permite comprobar la lectura de una hoja completa sin dividirla en fragmentos. ".repeat(30)))) })
        val file = File("build/fixtures/pdf-reader-rework.pdf")
        file.parentFile.mkdirs()
        file.writeBytes(DocumentCodec.exportToBytes(document))
        return FolioRepository(persistToDisk = false).also { repo ->
            assertNotNull(repo.importDocumentFromFile(file.absolutePath))
            repo.setViewMode(PageViewMode.ONE_COLUMN)
            repo.goToPage(repo.currentBook()!!.id, 1)
        }
    }

    @Test
    fun densePdfRendersAsOneOriginalPageAndNextAlwaysChangesSourcePage() = runDesktopComposeUiTest(width = 1440, height = 900) {
        val repo = importedPdf()
        val book = repo.currentBook()!!
        assertEquals(4, book.totalPages)
        assertTrue(book.pages.first().plainText.length > 1500)
        setContent { MeridianFolioApp(repository = repo, initialSection = MeridianSection.READER) }
        waitUntil(timeoutMillis = 20_000) { onAllNodesWithTag("pdf_page_image_1").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("paged_body_1").assertDoesNotExist()
        val imageBounds = onNodeWithTag("pdf_page_image_1").fetchSemanticsNode().boundsInRoot
        val canvasBounds = onNodeWithTag("reader_canvas").fetchSemanticsNode().boundsInRoot
        assertTrue(imageBounds.height <= canvasBounds.height && imageBounds.width < canvasBounds.width)
        val capture = onNodeWithTag("meridian_root").captureToImage().asSkiaBitmap()
        val output = File("build/visual/pdf-original-dense.png")
        output.parentFile.mkdirs()
        Image.makeFromBitmap(capture).use { image -> image.encodeToData(EncodedImageFormat.PNG)!!.use { output.writeBytes(it.bytes) } }
        onNodeWithTag("fit_mode_FIT_WIDTH").performClick()
        val scroll = onNodeWithTag("pdf_page_scroll_1")
        assertTrue(scroll.fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.VerticalScrollAxisRange].maxValue() > 0f)
        scroll.performMouseInput { moveTo(center); scroll(250f) }
        assertEquals(1, repo.currentBook()!!.currentPage, "Reading a zoomed page must not turn it")
        onNodeWithTag("next_page_button").performClick()
        assertEquals(2, repo.currentBook()!!.currentPage)
        onNodeWithTag("meridian_root").performKeyInput { pressKey(Key.PageDown) }
        assertEquals(3, repo.currentBook()!!.currentPage)
        onNodeWithTag("prev_page_button").performClick()
        assertEquals(2, repo.currentBook()!!.currentPage)
        repo.setViewMode(PageViewMode.TWO_COLUMNS)
        onNodeWithTag("next_page_button").performClick()
        assertEquals(4, repo.currentBook()!!.currentPage)
    }

    @Test
    fun narrowReaderUsesOriginalPdfPagesAndKeepsKeyboardNavigation() = runDesktopComposeUiTest(width = 640, height = 800) {
        val repo = importedPdf()
        setContent { MeridianFolioApp(repository = repo, initialSection = MeridianSection.READER) }
        waitUntil(timeoutMillis = 20_000) { onAllNodesWithTag("pdf_page_image_1").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("paged_body_1").assertDoesNotExist()
        onNodeWithTag("meridian_root").performKeyInput { pressKey(Key.PageDown) }
        assertEquals(2, repo.currentBook()!!.currentPage)
    }

    @Test
    fun continuousPdfTracksScrollAndRespondsToPageCommands() = runDesktopComposeUiTest(width = 1440, height = 900) {
        val repo = importedPdf()
        repo.setViewMode(PageViewMode.VERTICAL_CONTINUOUS)
        repo.goToPage(repo.currentBook()!!.id, 2)
        setContent { MeridianFolioApp(repository = repo, initialSection = MeridianSection.READER) }
        val list = onNodeWithTag("continuous_page_list")
        onNodeWithTag("continuous_pdf_page_2").assertIsDisplayed()
        list.performMouseInput { moveTo(center); scroll(1800f) }
        waitUntil(timeoutMillis = 10_000) { repo.currentBook()!!.currentPage >= 3 }
        val scrolledPage = repo.currentBook()!!.currentPage
        onNodeWithTag("prev_page_button").performClick()
        assertEquals(scrolledPage - 1, repo.currentBook()!!.currentPage)
        repo.goToPage(repo.currentBook()!!.id, 1)
        waitForIdle()
        onNodeWithTag("continuous_pdf_page_1").assertIsDisplayed()
        assertEquals(1, repo.currentBook()!!.currentPage)
        waitUntil(timeoutMillis = 20_000) { onAllNodesWithTag("continuous_pdf_image_1").fetchSemanticsNodes().isNotEmpty() }
        val capture = onNodeWithTag("meridian_root").captureToImage().asSkiaBitmap()
        val output = File("build/visual/pdf-continuous.png")
        output.parentFile.mkdirs()
        Image.makeFromBitmap(capture).use { image -> image.encodeToData(EncodedImageFormat.PNG)!!.use { output.writeBytes(it.bytes) } }
    }

    @Test
    fun pdfReflowKeepsWholeSourcePageScrollableWhileNextMovesToNextPage() = runDesktopComposeUiTest(width = 1440, height = 900) {
        val repo = importedPdf()
        repo.updateBookTypography { it.copy(pdfPreferReflowText = true) }
        repo.setViewMode(PageViewMode.REFLOW_TEXT)
        setContent { MeridianFolioApp(repository = repo, initialSection = MeridianSection.READER) }
        onNodeWithTag("paged_body_1").assertExists()
        onNodeWithTag("page_part_1").assertDoesNotExist()
        onNodeWithTag("paged_body_1").performMouseInput { moveTo(center); scroll(4f) }
        assertEquals(1, repo.currentBook()!!.currentPage)
        onNodeWithTag("next_page_button").performClick()
        assertEquals(2, repo.currentBook()!!.currentPage)
    }

    @Test
    fun continuousTextUpdatesCounterWithoutReturningToTopOfPage() = runDesktopComposeUiTest(width = 1440, height = 900) {
        val repo = FolioRepository(persistToDisk = false)
        val book = repo.currentBook()!!
        book.pages.forEach { page -> repo.updatePageContent(book.id, page.pageNumber, "Capítulo ${page.pageNumber}",
            (1..8).map { paragraph -> ContentBlock("${page.pageNumber}_$paragraph", text = "Página ${page.pageNumber}. Un párrafo de lectura para verificar la posición. ".repeat(3)) }) }
        repo.goToPage(book.id, 1)
        repo.setViewMode(PageViewMode.VERTICAL_CONTINUOUS)
        setContent { MeridianFolioApp(repository = repo, initialSection = MeridianSection.READER) }
        onNodeWithTag("continuous_page_list").performMouseInput { moveTo(center); scroll(1600f) }
        waitUntil(timeoutMillis = 10_000) { repo.currentBook()!!.currentPage > 1 }
        val page = repo.currentBook()!!.currentPage
        waitForIdle()
        assertEquals(page, repo.currentBook()!!.currentPage)
        repo.goToPage(book.id, 1)
        waitForIdle()
        assertEquals(1, repo.currentBook()!!.currentPage)
    }
}

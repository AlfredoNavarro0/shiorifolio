package dev.meridian.folio.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.*
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import dev.meridian.folio.core.*
import dev.meridian.folio.ui.theme.*
import java.io.File
import org.jetbrains.skia.Image
import org.jetbrains.skia.EncodedImageFormat
import androidx.compose.ui.graphics.asSkiaBitmap
import kotlin.test.*

@OptIn(ExperimentalTestApi::class)
class ReaderSelectionTest {
    @Test
    fun realTableOfContentsHasNoSuggestedPhraseButtons() = runDesktopComposeUiTest(width = 1440, height = 900) {
        val file = File(System.getProperty("user.home"), "Downloads/The Gal Is Sitting Behind Me, and Loves Me vol 1 - Fungus FTL.epub")
        org.junit.Assume.assumeTrue("Local reference EPUB is optional outside the author's machine", file.isFile)
        val repo = FolioRepository(persistToDisk = false)
        val book = assertNotNull(repo.importDocumentFromFile(file.absolutePath))
        repo.goToPage(book.id, 8)
        repo.setViewMode(PageViewMode.VERTICAL_CONTINUOUS)
        setContent { MeridianFolioApp(repository = repo, initialSection = MeridianSection.READER) }
        val suggestedPhrase = SemanticsMatcher("Generated phrase below paragraph") { node ->
            node.config.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.Text)?.any { it.text.startsWith("+ ") } == true
        }
        onAllNodes(suggestedPhrase and hasAnyAncestor(hasTestTag("continuous_page_list"))).assertCountEquals(0)
        onNodeWithTag("reader_selection_menu").assertDoesNotExist()
        val output = File("build/visual/reader-clean-continuous.png")
        output.parentFile.mkdirs()
        Image.makeFromBitmap(onNodeWithTag("meridian_root").captureToImage().asSkiaBitmap()).use { image ->
            image.encodeToData(EncodedImageFormat.PNG)!!.use { output.writeBytes(it.bytes) }
        }
    }

    private fun ComposeUiTest.selectText(text: String, start: Int, end: Int) {
        val node = onNodeWithText(text)
        val layouts = mutableListOf<TextLayoutResult>()
        node.performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) { it(layouts) }
        val layout = layouts.single()
        node.performMouseInput {
            moveTo(layout.getCursorRect(start).center)
            press()
            moveTo(layout.getCursorRect(end).center, delayMillis = 200)
            release()
        }
    }

    @Test
    fun selectedUnicodeFragmentsRetainTheSourcePageAndContext() {
        val original = AnnotatedString("Una selección exacta, 日本語.").withReaderSource(12, "Contexto completo")
        val fragment = original.subSequence(4, 20)
        val entry = assertNotNull(selectedNotebookEntry(listOf(fragment), fallbackPage = 10))
        assertEquals("selección exacta", entry.text)
        assertEquals(12, entry.page)
        assertEquals("Contexto completo", entry.context)
        assertNull(selectedNotebookEntry(listOf(AnnotatedString("  ")), 1))
    }

    @Test
    fun mouseSelectionSavesExactTextFromTheRightHandPage() = runDesktopComposeUiTest(width = 1440, height = 900) {
        val repo = FolioRepository(persistToDisk = false)
        val book = repo.currentBook()!!
        val text = "Una frase exacta merece guardarse en el cuaderno."
        repo.goToPage(book.id, 1)
        repo.updatePageContent(book.id, 2, "Página derecha", listOf(ContentBlock("selection_body", text = text)))
        repo.setViewMode(PageViewMode.TWO_COLUMNS)
        val before = repo.state.value.savedPhrases.size
        setContent { MeridianFolioApp(repository = repo, initialSection = MeridianSection.READER) }
        onNodeWithTag("reader_selection_menu").assertDoesNotExist()
        selectText(text, 4, 16)
        onNodeWithTag("reader_selection_menu").assertExists()
        val output = File("build/visual/reader-selection-menu.png")
        output.parentFile.mkdirs()
        Image.makeFromBitmap(onNodeWithTag("meridian_root").captureToImage().asSkiaBitmap()).use { image ->
            image.encodeToData(EncodedImageFormat.PNG)!!.use { output.writeBytes(it.bytes) }
        }
        onNodeWithTag("selection_save_notebook").performClick()
        assertEquals(before + 1, repo.state.value.savedPhrases.size)
        val saved = repo.state.value.savedPhrases.first()
        assertEquals("frase exacta", saved.phrase)
        assertEquals(2, saved.pageNumber)
        assertEquals(book.id, saved.bookId)
        onNodeWithTag("reader_selection_menu").assertDoesNotExist()
    }

    @Test
    fun longPressOffersNotebookActionAndDismissDoesNotSave() = runDesktopComposeUiTest(width = 640, height = 800) {
        val saved = mutableListOf<NotebookSelection>()
        val text = "Selecciona esta frase con el dedo."
        setContent {
            ClayTheme(palette = ClayTokens.chrome()) {
                CompositionLocalProvider(LocalSaveReaderSelection provides { saved += it }) {
                    ReaderTextSelection("touch", 7) { Column { Text(AnnotatedString(text).withReaderSource(7, text)) } }
                }
            }
        }
        onNodeWithText(text).performTouchInput { longClick(centerLeft + androidx.compose.ui.geometry.Offset(10f, 0f)) }
        onNodeWithTag("selection_close").performTouchInput { click() }
        assertTrue(saved.isEmpty())
        onNodeWithTag("reader_selection_menu").assertDoesNotExist()
        onNodeWithText(text).performTouchInput { longClick(centerLeft + androidx.compose.ui.geometry.Offset(10f, 0f)) }
        onNodeWithTag("selection_save_notebook").performTouchInput { click() }
        assertEquals(1, saved.size)
        assertEquals("Selecciona", saved.single().text)
        assertEquals(7, saved.single().page)
    }

    @Test
    fun changingPageDismissesTheSelectionWithoutSaving() = runDesktopComposeUiTest(width = 1440, height = 900) {
        val repo = FolioRepository(persistToDisk = false)
        val book = repo.currentBook()!!
        val text = "Esta frase puede seleccionarse."
        repo.updatePageContent(book.id, 1, "Inicio", listOf(ContentBlock("selection_body", text = text)))
        repo.goToPage(book.id, 1)
        repo.setViewMode(PageViewMode.ONE_COLUMN)
        val before = repo.state.value.savedPhrases.size
        setContent { MeridianFolioApp(repository = repo, initialSection = MeridianSection.READER) }
        selectText(text, 0, 10)
        onNodeWithTag("reader_selection_menu").assertExists()
        onNodeWithTag("meridian_root").performKeyInput { pressKey(Key.PageDown) }
        onNodeWithTag("reader_selection_menu").assertDoesNotExist()
        assertEquals(before, repo.state.value.savedPhrases.size)
    }
}

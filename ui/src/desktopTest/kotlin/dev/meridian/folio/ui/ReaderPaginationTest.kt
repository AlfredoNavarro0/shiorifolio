package dev.meridian.folio.ui

import androidx.compose.runtime.*
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.*
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.asSkiaBitmap
import dev.meridian.folio.core.*
import org.jetbrains.skia.Image
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlin.test.*

@OptIn(ExperimentalTestApi::class)
class ReaderPaginationTest {
    @Test
    fun linePaginationPreservesTextAtDifferentWidthsAndFontSizes() = runComposeUiTest {
        val source = AnnotatedString(("Párrafo con acentos, 日本語 y una frase larga para dividir entre hojas.\n\n").repeat(40))
        val results = mutableListOf<List<PagePiece>>()
        setContent {
            val measurer = rememberTextMeasurer()
            LaunchedEffect(Unit) {
                for (width in listOf(180, 500)) for (size in listOf(14, 26)) {
                    results += paginateText(source, TextStyle(fontSize = size.sp, lineHeight = (size * 1.8).sp), width, 200) { text, style, w ->
                        measurer.measure(text, style, constraints = Constraints.fixedWidth(w))
                    }
                }
            }
        }
        waitForIdle()
        assertEquals(4, results.size)
        for (pieces in results) {
            assertTrue(pieces.size > 1)
            assertEquals(source.text, pieces.joinToString("") { it.text.text })
            assertTrue(pieces.all { it.heightPx <= 200 }, "Every fragment fits its sheet")
        }
    }

    @Test
    fun wheelAndKeyboardReadAllContinuationsBeforeLeavingSourcePage() = runComposeUiTest {
        val repo = FolioRepository(persistToDisk = false)
        val book = repo.currentBook()!!
        repo.setViewMode(PageViewMode.ONE_COLUMN)
        repo.goToPage(book.id, 1)
        repo.updatePageContent(book.id, 1, "Prueba de paginación", listOf(ContentBlock("long", text = "Palabras de una página extensa. ".repeat(250))))
        setContent { MeridianFolioApp(repository = repo) }
        fun part() = onNodeWithTag("page_part_1").fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.Text].first().text
        val count = part().substringAfterLast('/').toInt()
        assertTrue(count > 1)
        onNodeWithTag("paged_body_1").performMouseInput { moveTo(center); scroll(5f) }
        assertTrue(part().contains("2/$count"))
        assertEquals(1, repo.currentBook()!!.currentPage)
        onNodeWithTag("paged_body_1").performMouseInput { moveTo(center); advanceEventTime(300); scroll(-5f) }
        assertTrue(part().contains("1/$count"))
        repeat(count - 1) { onNodeWithTag("meridian_root").performKeyInput { pressKey(Key.PageDown) } }
        assertEquals(1, repo.currentBook()!!.currentPage)
        assertTrue(part().contains("$count/$count"))
        onNodeWithTag("next_page_button").performClick()
        assertEquals(2, repo.currentBook()!!.currentPage)
        onNodeWithTag("prev_page_button").performClick()
        assertEquals(1, repo.currentBook()!!.currentPage)
        assertTrue(part().contains("$count/$count"), "Returning backwards opens the final continuation")
        onNodeWithTag("status_prev_page").assertDoesNotExist()
        onNodeWithTag("section_tab_EDITOR").assertDoesNotExist()
        onNodeWithTag("section_tab_PREDICTION").assertDoesNotExist()
    }

    @Test
    fun settingsShowOnlySelectedCategoryAndRemainUsableInBothThemes() = runComposeUiTest {
        val repo = FolioRepository(persistToDisk = false)
        setContent { MeridianFolioApp(repository = repo) }
        onNodeWithTag("section_tab_SETTINGS").performClick()
        for (theme in listOf(ReaderTheme.WHITE, ReaderTheme.DARK)) {
            repo.setTheme(theme)
            for ((category, card) in listOf("appearance" to "settings_ui_appearance_card", "reading" to "settings_book_typography_card", "shortcuts" to "settings_shortcuts_card")) {
                onNodeWithTag("settings_category_$category").performClick()
                onNodeWithTag(card).assertExists()
                if (category != "reading") onNodeWithTag("settings_book_typography_card").assertDoesNotExist()
                if (category != "shortcuts") onNodeWithTag("settings_shortcuts_card").assertDoesNotExist()
                val snapshot = onNodeWithTag("meridian_root").captureToImage()
                Image.makeFromBitmap(snapshot.asSkiaBitmap()).use { image ->
                    image.encodeToData(EncodedImageFormat.PNG)?.use { File("build/settings-$category-${theme.name}.png").writeBytes(it.bytes) }
                }
            }
        }
    }
}

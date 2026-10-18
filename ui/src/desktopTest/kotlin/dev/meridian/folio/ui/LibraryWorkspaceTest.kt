package dev.meridian.folio.ui

import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.test.*
import dev.meridian.folio.core.*
import java.io.File
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import kotlin.test.*

@OptIn(ExperimentalTestApi::class)
class LibraryWorkspaceTest {
    @Test
    fun libraryTabRendersFourVariantsAndSearchesByAuthor() = runComposeUiTest {
        val repo = FolioRepository(persistToDisk = false)
        repo.setTheme(ReaderTheme.DARK)
        val realEpub = File(System.getProperty("user.home"), "Downloads/The Gal Is Sitting Behind Me, and Loves Me vol 1 - Fungus FTL.epub")
        val imported = realEpub.takeIf { it.isFile }?.let { repo.library.importFile(it.path) }
        setContent { MeridianFolioApp(repository = repo) }
        onNodeWithTag("section_tab_LIBRARY").performClick()
        onNodeWithTag("library_workspace").assertExists()
        for (layout in LibraryLayout.entries) for (density in LibraryDensity.entries) {
            onNodeWithTag("library_layout_${layout.name}").performClick()
            onNodeWithTag("library_density_${density.name}").performClick()
            assertEquals(layout, repo.state.value.libraryPreferences.layout)
            assertEquals(density, repo.state.value.libraryPreferences.density)
            if (layout == LibraryLayout.CARDS && imported != null) {
                waitUntil(timeoutMillis = 5_000) {
                    onAllNodesWithContentDescription("Portada de ${imported.title}").fetchSemanticsNodes().isNotEmpty()
                }
            }
            val snapshot = onNodeWithTag("library_workspace").captureToImage()
            val output = File("build/library-${layout.name}-${density.name}-dark.png")
            output.parentFile.mkdirs()
            Image.makeFromBitmap(snapshot.asSkiaBitmap()).use { image ->
                image.encodeToData(EncodedImageFormat.PNG)?.use { output.writeBytes(it.bytes) }
            }
        }
        onNodeWithTag("library_layout_CARDS").performClick()
        repo.setTheme(ReaderTheme.WHITE)
        if (imported != null) waitUntil(timeoutMillis = 5_000) {
            onAllNodesWithContentDescription("Portada de ${imported.title}").fetchSemanticsNodes().isNotEmpty()
        }
        val lightSnapshot = onNodeWithTag("library_workspace").captureToImage()
        Image.makeFromBitmap(lightSnapshot.asSkiaBitmap()).use { image ->
            image.encodeToData(EncodedImageFormat.PNG)?.use { File("build/library-CARDS-DETAILED-light.png").writeBytes(it.bytes) }
        }
        onNodeWithTag("library_quick_format_PDF").performClick()
        onNodeWithTag("library_count").assertTextEquals("1 / ${repo.state.value.documents.size} volúmenes")
        onNodeWithTag("library_quick_format_PDF").performClick()
        val author = repo.state.value.documents.first().author
        onNodeWithTag("library_search").performTextInput(author)
        onNodeWithTag("library_select_all").performClick()
        onNodeWithTag("library_batch_status_ABANDONED").performScrollTo().performClick()
        assertTrue(repo.state.value.documents.filter { it.author.contains(author, true) }.all { it.libraryStatus == LibraryStatus.ABANDONED })
        assertTrue(repo.state.value.documents.filterNot { it.author.contains(author, true) }.all { it.libraryStatus == LibraryStatus.PENDING })
        onNodeWithTag("library_clear_selection").performScrollTo().performClick()
        onNodeWithTag("library_layout_CARDS").performClick()
        val book = repo.state.value.documents.first()
        onNodeWithTag("library_menu_${book.id}").performClick()
        onNodeWithTag("library_menu_select_${book.id}").performClick()
        onNodeWithTag("library_select_${book.id}").assertIsOn()
        onNodeWithTag("library_clear_selection").performScrollTo().performClick()
        onNodeWithTag("library_open_${book.id}").performClick()
        onNodeWithTag("prediction_badge").assertExists()
        assertEquals(book.id, repo.state.value.selectedBookId)
    }

    @Test
    fun deletionRequiresConfirmationAndEmptyLibraryCanStillImport() = runComposeUiTest {
        val repo = FolioRepository(persistToDisk = false)
        repo.setTheme(ReaderTheme.DARK)
        setContent { MeridianFolioApp(repository = repo) }
        onNodeWithTag("section_tab_LIBRARY").performClick()
        val count = repo.state.value.documents.size
        onNodeWithTag("library_select_all").performClick()
        onNodeWithTag("library_delete").performScrollTo().performClick()
        onNodeWithTag("library_cancel_delete").performClick()
        assertEquals(count, repo.state.value.documents.size)
        onNodeWithTag("library_delete").performScrollTo().performClick()
        onNodeWithTag("library_confirm_delete").performClick()
        waitUntil(timeoutMillis = 5_000) { repo.state.value.documents.isEmpty() }
        onNodeWithTag("meridian_root").assertExists()
        onNodeWithText("shiorifolio").assertExists()
        onNodeWithTag("library_tools").performClick()
        onNodeWithTag("library_file_path").assertExists()
        onNodeWithTag("library_new_title").assertExists()
    }

    @Test
    fun libraryHeaderStatusChipsFilterPanelAndCardProgressBehave() = runComposeUiTest {
        val repo = FolioRepository(persistToDisk = false)
        setContent { MeridianFolioApp(repository = repo) }
        onNodeWithTag("section_tab_LIBRARY").performClick()

        // Header: clay title, live volume counter and the (single) tagged import control.
        onNodeWithTag("library_header").assertExists()
        onNodeWithText("Tu biblioteca").assertExists()
        val total = repo.state.value.documents.size
        onNodeWithTag("library_count").assertTextEquals("$total / $total volúmenes")
        onNodeWithTag("library_tools").performClick()
        onNodeWithTag("library_file_path").assertExists()
        onNodeWithTag("library_new_title").assertExists()
        onNodeWithTag("library_tools").performClick()
        onAllNodesWithText("+ Importar libro").assertCountEquals(1)

        // Status chips filter the counter with the real statuses and can be cleared again.
        val book = repo.state.value.documents.first()
        repo.library.setStatus(setOf(book.id), LibraryStatus.READING)
        waitForIdle()
        val reading = repo.state.value.documents.count { it.libraryStatus == LibraryStatus.READING }
        onNodeWithTag("library_quick_status_READING").performClick()
        onNodeWithTag("library_count").assertTextEquals("$reading / $total volúmenes")
        onNodeWithTag("library_layout_CARDS").performClick()
        val progress = onNodeWithTag("library_progress_${book.id}")
        progress.assertExists()
        onNodeWithTag("library_quick_status_ALL").performClick()
        onNodeWithTag("library_count").assertTextEquals("$total / $total volúmenes")

        // The filters panel opens, applies and clears without breaking the workspace.
        onNodeWithTag("library_filters_btn").performClick()
        onNodeWithTag("library_filter_panel").assertExists()
        onNodeWithTag("library_filter_status_FINISHED").performClick()
        onNodeWithText("Aplicar").performClick()
        onNodeWithTag("library_filter_panel").assertDoesNotExist()
        onNodeWithTag("library_filters_btn").performClick()
        onNodeWithText("Limpiar").performClick()
        onNodeWithTag("library_count").assertTextEquals("$total / $total volúmenes")
        onNodeWithTag("library_workspace").assertExists()
    }
}

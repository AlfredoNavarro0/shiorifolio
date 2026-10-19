package dev.meridian.folio.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.runComposeUiTest
import dev.meridian.folio.core.AnchorStatus
import dev.meridian.folio.core.DocumentCodec
import dev.meridian.folio.core.DocumentFormat
import dev.meridian.folio.core.FolioRepository
import dev.meridian.folio.core.HighlightTone
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Iteration 6: non-destructive editing, re-anchoring and portable backup from the UI. */
@OptIn(ExperimentalTestApi::class)
class EditingUiTest {

    private fun newRepo() = FolioRepository(persistToDisk = false)

    @Test
    fun searchReplaceDialogPreviewsReplacesAndUndoRestores() = runComposeUiTest {
        val repo = newRepo()
        setContent { MeridianFolioApp(repository = repo) }
        val book = repo.currentBook()!!

        onNodeWithTag("reader_search_btn").performScrollTo().performClick()
        onNodeWithTag("search_replace_dialog").assertExists()

        onNodeWithTag("search_query_input").performTextReplacement("Viaduct")
        waitForIdle()
        onNodeWithTag("search_result_summary").assertExists()

        onNodeWithTag("search_replace_input").performTextReplacement("Bridge")
        onNodeWithTag("search_replace_all").performClick()
        waitForIdle()

        val replaced = repo.state.value.documents.first { it.id == book.id }
        assertTrue(replaced.pages.any { page -> page.blocks.any { it.text.contains("Bridge") } })
        assertFalse(replaced.pages.any { page -> page.blocks.any { it.text.contains("Viaduct") } })

        onNodeWithTag("search_close_btn").performClick()
        waitForIdle()

        onNodeWithTag("reader_undo_btn").performScrollTo().performClick()
        waitForIdle()

        val reverted = repo.state.value.documents.first { it.id == book.id }
        assertFalse(reverted.pages.any { page -> page.blocks.any { it.text.contains("Bridge") } })
        assertTrue(reverted.pages.any { page -> page.blocks.any { it.text.contains("Viaduct") } })

        // Redo returns the replacement in place.
        onNodeWithTag("reader_redo_btn").performScrollTo().performClick()
        waitForIdle()
        val redone = repo.state.value.documents.first { it.id == book.id }
        assertTrue(redone.pages.any { page -> page.blocks.any { it.text.contains("Bridge") } })
    }

    @Test
    fun pageEditorSavesWordAndParagraphCorrections() = runComposeUiTest {
        val repo = newRepo()
        setContent { MeridianFolioApp(repository = repo) }
        val book = repo.currentBook()!!
        val pageNumber = book.currentPage

        onNodeWithTag("open_page_editor_btn").performScrollTo().performClick()
        onNodeWithTag("page_editor_dialog").assertExists()

        onNodeWithTag("page_editor_block_1").performTextReplacement("Waybills revised by the reader.")
        onNodeWithTag("page_editor_save_btn").performClick()
        waitForIdle()

        val page = repo.state.value.documents.first { it.id == book.id }.pages.first { it.pageNumber == pageNumber }
        assertEquals("Waybills revised by the reader.", page.blocks[1].text)
    }

    @Test
    fun chapterManagerRenamesAndRepairsTableOfContents() = runComposeUiTest {
        val repo = newRepo()
        setContent { MeridianFolioApp(repository = repo) }
        val book = repo.currentBook()!!

        onNodeWithTag("open_chapter_manager_btn").performScrollTo().performClick()
        onNodeWithTag("chapter_manager_dialog").assertExists()

        onNodeWithTag("chapter_rename_3").performScrollTo().performClick()
        onNodeWithTag("chapter_rename_input_3").performTextReplacement("III. Renamed Chapter")
        onNodeWithTag("chapter_rename_save_3").performClick()
        waitForIdle()

        val renamed = repo.state.value.documents.first { it.id == book.id }
        assertEquals("III. Renamed Chapter", renamed.pages.first { it.pageNumber == 3 }.chapterTitle)

        // Repair rebuilds chapter titles from the real content headings.
        onNodeWithTag("chapter_repair_toc_btn").performClick()
        waitForIdle()

        val repaired = repo.state.value.documents.first { it.id == book.id }
        assertEquals(
            "Chapter III — Intaglio Plates and Paper Grain",
            repaired.tableOfContents.first { it.startPage == 3 }.title,
        )
    }

    @Test
    fun notebookFlagsOrphanedPhraseAndReattachFlowRestoresIt() = runComposeUiTest {
        val repo = newRepo()
        val book = repo.currentBook()!!
        val phrase = repo.saveWordOrPhrase(
            bookId = book.id,
            pageNumber = 1,
            phrase = "keystone load",
            contextSentence = "Every arch was laid by masons who measured span and keystone load by compass rose.",
            note = "carga de la clave",
            tone = HighlightTone.SAGE,
        )!!

        // Remove the anchored text from page 1, then re-add it on page 3 with a broken context:
        // the anchor can no longer be resolved silently and must be flagged as orphaned.
        val page1 = repo.state.value.documents.first { it.id == book.id }.pages.first { it.pageNumber == 1 }
        repo.updatePageContent(
            book.id, 1, page1.chapterTitle,
            page1.blocks.map { if (it.id == "mv_1_2") it.copy(text = it.text.replace("keystone load", "keystone mass")) else it },
        )
        val page3 = repo.state.value.documents.first { it.id == book.id }.pages.first { it.pageNumber == 3 }
        repo.updatePageContent(
            book.id, 3, page3.chapterTitle,
            page3.blocks.map { if (it.id == "mv_3_2") it.copy(text = it.text + " The keystone load was verified upstream.") else it },
        )
        val orphaned = repo.state.value.savedPhrases.first { it.id == phrase.id }
        assertEquals(AnchorStatus.ORPHANED, orphaned.anchorStatus)

        setContent { MeridianFolioApp(repository = repo) }
        onNodeWithTag("section_tab_LEXICON").performClick()
        waitForIdle()

        onNodeWithText("Huérfana").assertExists()
        onNodeWithTag("reattach_phrase_${phrase.id}").performScrollTo().performClick()
        waitForIdle()
        onNodeWithTag("reattach_dialog").assertExists()

        onNodeWithTag("reattach_match_0").performClick()
        waitForIdle()

        val reattached = repo.state.value.savedPhrases.first { it.id == phrase.id }
        assertEquals(AnchorStatus.ACTIVE, reattached.anchorStatus)
        assertEquals(3, reattached.pageNumber)
    }

    @Test
    fun notebookWorkspaceCapturesFiltersAndOpensEntries() = runComposeUiTest {
        val repo = newRepo()
        val book = repo.currentBook()!!
        val keystone = repo.saveWordOrPhrase(
            bookId = book.id,
            pageNumber = 1,
            phrase = "keystone load",
            contextSentence = "Every arch was laid by masons who measured span and keystone load by compass rose.",
            note = "carga de la clave",
            tone = HighlightTone.SAGE,
        )!!
        val copperplate = repo.saveWordOrPhrase(
            bookId = book.id,
            pageNumber = 2,
            phrase = "copperplate",
            contextSentence = "Copperplate engraving",
            note = "",
            tone = HighlightTone.AMBER,
        )!!

        setContent { MeridianFolioApp(repository = repo) }
        onNodeWithTag("section_tab_LEXICON").performClick()
        waitForIdle()

        onNodeWithTag("notebook_header").assertExists()
        onNodeWithTag("notebook_count").assertExists()
        onNodeWithTag("notebook_entry_${keystone.id}").assertExists()
        onNodeWithTag("notebook_entry_${copperplate.id}").assertExists()

        // The search keeps only the entries whose real fields match.
        onNodeWithTag("lexicon_search_input").performTextReplacement("keystone")
        waitForIdle()
        onNodeWithTag("notebook_entry_${keystone.id}").assertExists()
        onNodeWithTag("notebook_entry_${copperplate.id}").assertDoesNotExist()

        onNodeWithTag("lexicon_search_input").performTextReplacement("")
        waitForIdle()

        // Capture form: real book context, required text, note and a real highlight tone.
        onNodeWithTag("notebook_new_entry_btn").performClick()
        waitForIdle()
        onNodeWithTag("notebook_capture_book").assertExists()
        onNodeWithTag("notebook_capture_text").performTextReplacement("waybill ledger")
        onNodeWithTag("notebook_capture_note").performTextReplacement("registro de guías")
        onNodeWithTag("notebook_capture_tone_AMBER").performScrollTo().performClick()
        onNodeWithTag("notebook_capture_save").performScrollTo().performClick()
        waitForIdle()

        val saved = assertNotNull(repo.state.value.savedPhrases.firstOrNull { it.phrase == "waybill ledger" })
        assertEquals(HighlightTone.AMBER, saved.tone)
        assertEquals("registro de guías", saved.note)
        assertEquals(book.id, saved.bookId)
        onNodeWithTag("notebook_entry_${saved.id}").performScrollTo().assertExists()

        onNodeWithTag("phrase_delete_${saved.id}").performScrollTo().performClick()
        waitForIdle()
        assertTrue(repo.state.value.savedPhrases.none { it.id == saved.id })
        onNodeWithTag("notebook_entry_${saved.id}").assertDoesNotExist()
    }

    @Test
    fun settingsDataCategoryExportsAndRestoresPortableBackup() = runComposeUiTest {
        val root = Files.createTempDirectory("shiori-ui-backup-").toFile()
        try {
            val repo = newRepo()
            // A backup re-associates books by content fingerprint, so import a real file first.
            val bytes = DocumentCodec.exportToBytes(repo.state.value.documents.first { it.format == DocumentFormat.EPUB })
            val sourceFile = File(root, "novela.epub").apply { writeBytes(bytes) }
            val book = repo.library.importFile(sourceFile.path, select = true)
            repo.goToPage(book.id, 2)
            repo.saveWordOrPhrase(
                bookId = book.id,
                pageNumber = 2,
                phrase = "copperplate",
                contextSentence = "Copperplate engraving",
                note = "nota de prueba",
                tone = HighlightTone.SAGE,
            )

            val backupFile = File(root, "respaldo.json")

            setContent { MeridianFolioApp(repository = repo) }

            onNodeWithTag("section_tab_SETTINGS").performClick()
            waitForIdle()
            onNodeWithTag("settings_category_data").performClick()
            waitForIdle()

            onNodeWithTag("backup_export_btn").performScrollTo().performClick()
            waitForIdle()
            onNodeWithTag("backup_path_dialog").assertExists()
            onNodeWithTag("backup_path_input").performTextReplacement(backupFile.absolutePath)
            onNodeWithTag("backup_path_confirm").performClick()

            waitUntil(timeoutMillis = 5_000) { backupFile.exists() && backupFile.length() > 0 }
            waitUntil(timeoutMillis = 5_000) { onAllNodesWithTag("data_status_message").fetchSemanticsNodes().isNotEmpty() }

            // Move local progress, then restore the backup and confirm the page comes back.
            repo.goToPage(book.id, 1)
            onNodeWithTag("backup_import_btn").performScrollTo().performClick()
            waitForIdle()
            onNodeWithTag("backup_path_input").performTextReplacement(backupFile.absolutePath)
            onNodeWithTag("backup_path_confirm").performClick()

            waitUntil(timeoutMillis = 5_000) { onAllNodesWithTag("backup_import_confirm_dialog").fetchSemanticsNodes().isNotEmpty() }
            onNodeWithTag("backup_import_confirm").performClick()

            waitUntil(timeoutMillis = 5_000) { repo.state.value.documents.first { it.id == book.id }.currentPage == 2 }
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun mobileHudOffersEditingTools() = runComposeUiTest {
        val repo = newRepo()
        setContent { MeridianFolioApp(repository = repo) }

        onNodeWithTag("mobile_preview_toggle").performScrollTo().performClick()
        waitForIdle()
        onNodeWithTag("mobile_edit_menu_btn").performClick()
        onNodeWithTag("mobile_search_btn").performClick()
        waitForIdle()

        onNodeWithTag("search_replace_dialog").assertExists()
        onNodeWithTag("search_close_btn").performClick()
        waitForIdle()
    }
}

package dev.meridian.folio.ui

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.*
import dev.meridian.folio.core.*
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class ReaderKeyboardTest {
    @Test
    fun searchShortcutOpensTheSearchWithoutExposingAnExtraToolbarButton() = runComposeUiTest {
        val repo = FolioRepository(persistToDisk = false)
        setContent { MeridianFolioApp(repository = repo, initialSection = MeridianSection.READER) }
        onNodeWithTag("reader_search_btn").assertDoesNotExist()
        onNodeWithTag("meridian_root").performKeyInput {
            keyDown(Key.CtrlLeft); pressKey(Key.F); keyUp(Key.CtrlLeft)
        }
        onNodeWithTag("search_replace_dialog").assertExists()
    }

    @Test
    fun pageKeysWorkImmediatelyWithoutClickingReaderControls() = runComposeUiTest {
        val repo = FolioRepository(persistToDisk = false)
        repo.state.value.documents.forEach { book -> book.pages.forEach { page ->
            repo.updatePageContent(book.id, page.pageNumber, page.chapterTitle, listOf(ContentBlock("short", text = "Contenido breve de prueba.")))
        } }
        setContent { MeridianFolioApp(initialSection = MeridianSection.READER, repository = repo) }

        for (mode in PageViewMode.entries) {
            repo.setViewMode(mode)
            repo.goToPage(repo.currentBook()!!.id, 1)
            waitForIdle()
            val step = if (mode == PageViewMode.TWO_COLUMNS) 2 else 1
            for ((next, prev) in listOf(Key.PageDown to Key.PageUp, Key.DirectionRight to Key.DirectionLeft, Key.DirectionDown to Key.DirectionUp)) {
                onNodeWithTag("meridian_root").performKeyInput { pressKey(next) }
                assertEquals(1 + step, repo.currentBook()!!.currentPage, "First page turn in $mode using $next")
                onNodeWithTag("meridian_root").performKeyInput { pressKey(prev) }
                assertEquals(1, repo.currentBook()!!.currentPage)
            }
        }
    }

    @Test
    fun shortcutsResumeAfterLeavingSearchAndClosingReminderEditor() = runComposeUiTest {
        val repo = FolioRepository(persistToDisk = false)
        repo.state.value.documents.forEach { book -> book.pages.forEach { page ->
            repo.updatePageContent(book.id, page.pageNumber, page.chapterTitle, listOf(ContentBlock("short", text = "Contenido breve de prueba.")))
        } }
        repo.setViewMode(PageViewMode.ONE_COLUMN)
        repo.goToPage(repo.currentBook()!!.id, 1)
        setContent { MeridianFolioApp(initialSection = MeridianSection.READER, repository = repo) }

        onNodeWithTag("section_tab_LIBRARY").performClick()
        onNodeWithTag("library_search").performClick().performTextInput("Meridian")
        onNodeWithTag("meridian_root").performKeyInput { pressKey(Key.DirectionLeft) }
        assertEquals(1, repo.currentBook()!!.currentPage)
        onNodeWithTag("meridian_root").performKeyInput {
            keyDown(Key.CtrlLeft); pressKey(Key.T); keyUp(Key.CtrlLeft)
        }
        assertEquals(ReaderTheme.WHITE, repo.state.value.theme, "Global shortcuts must not hijack text input")
        onNodeWithTag("meridian_root").performKeyInput {
            keyDown(Key.CtrlLeft)
            pressKey(Key.Tab)
            keyUp(Key.CtrlLeft)
        }
        onNodeWithTag("meridian_root").performKeyInput { pressKey(Key.PageDown) }
        assertEquals(2, repo.currentBook()!!.currentPage)

        onNodeWithTag("open_reminder_btn").performClick()
        onAllNodes(hasSetTextAction())[0].performClick()
        onNodeWithTag("meridian_root").performKeyInput { pressKey(Key.PageDown) }
        assertEquals(2, repo.currentBook()!!.currentPage, "Typing must not turn the page")
        onNodeWithTag("meridian_root").performKeyInput { pressKey(Key.Escape) }
        onNodeWithTag("meridian_root").performKeyInput { pressKey(Key.PageDown) }
        assertEquals(3, repo.currentBook()!!.currentPage)
    }

    @Test
    fun customPageKeysSurviveBookChangesAndZenRemovingFocusedControls() = runComposeUiTest {
        val repo = FolioRepository(persistToDisk = false)
        repo.state.value.documents.forEach { book -> book.pages.forEach { page ->
            repo.updatePageContent(book.id, page.pageNumber, page.chapterTitle, listOf(ContentBlock("short", text = "Contenido breve de prueba.")))
        } }
        repo.setViewMode(PageViewMode.ONE_COLUMN)
        repo.updateKeyBinding(ReaderAction.NEXT_PAGE, KeyBindingSpec("N"))
        repo.updateKeyBinding(ReaderAction.PREV_PAGE, KeyBindingSpec("P"))
        setContent { MeridianFolioApp(initialSection = MeridianSection.READER, repository = repo) }

        val book = repo.state.value.documents.first { it.id != repo.currentBook()!!.id }
        repo.selectBook(book.id)
        repo.setViewMode(PageViewMode.ONE_COLUMN)
        repo.goToPage(book.id, 1)
        waitForIdle()
        onNodeWithTag("meridian_root").performKeyInput { pressKey(Key.N) }
        assertEquals(2, repo.currentBook()!!.currentPage)
        readerMenuItem("zen_mode_toggle_btn").performClick()
        onNodeWithTag("meridian_root").performKeyInput { pressKey(Key.P) }
        assertEquals(1, repo.currentBook()!!.currentPage)
        onNodeWithTag("meridian_root").performKeyInput { pressKey(Key.Escape) }
        onNodeWithTag("meridian_root").performKeyInput { pressKey(Key.N) }
        assertEquals(2, repo.currentBook()!!.currentPage)
    }

    @Test
    fun globalShortcutsAndRebindingWorkWithKeyboardFocus() = runComposeUiTest {
        val repo = FolioRepository(persistToDisk = false)
        repo.state.value.documents.forEach { book -> book.pages.forEach { page ->
            repo.updatePageContent(book.id, page.pageNumber, page.chapterTitle, listOf(ContentBlock("short", text = "Contenido breve de prueba.")))
        } }
        var fullscreenCount = 0
        setContent { MeridianFolioApp(initialSection = MeridianSection.READER, repository = repo, onToggleWindowFullscreen = { fullscreenCount++ }) }
        onNodeWithTag("meridian_root").performKeyInput {
            keyDown(Key.CtrlLeft); pressKey(Key.T); keyUp(Key.CtrlLeft)
        }
        assertEquals(ReaderTheme.DARK, repo.state.value.theme)
        val mode = repo.state.value.viewMode
        onNodeWithTag("meridian_root").performKeyInput {
            keyDown(Key.CtrlLeft); pressKey(Key.M); keyUp(Key.CtrlLeft)
        }
        assertEquals(PageViewMode.entries[(mode.ordinal + 1) % PageViewMode.entries.size], repo.state.value.viewMode)
        onNodeWithTag("meridian_root").performKeyInput { pressKey(Key.F11) }
        assertEquals(1, fullscreenCount)
        val book = repo.currentBook()!!
        val previousPage = book.currentPage
        repo.jumpToLocation(book.id, book.totalPages, "Test chapter")
        waitForIdle()
        onNodeWithTag("meridian_root").performKeyInput {
            keyDown(Key.AltLeft); pressKey(Key.DirectionLeft); keyUp(Key.AltLeft)
        }
        assertEquals(previousPage, repo.currentBook()!!.currentPage)
        onNodeWithTag("meridian_root").performKeyInput {
            keyDown(Key.AltLeft); pressKey(Key.DirectionRight); keyUp(Key.AltLeft)
        }
        assertEquals(book.totalPages, repo.currentBook()!!.currentPage)
        onNodeWithTag("meridian_root").performKeyInput {
            keyDown(Key.CtrlLeft); pressKey(Key.Tab); keyUp(Key.CtrlLeft)
        }
        onNodeWithTag("notebook_header").assertExists()
        onNodeWithTag("section_tab_SETTINGS").performClick()
        onNodeWithTag("settings_category_shortcuts").performClick()
        onNodeWithTag("rebind_action_NEXT_PAGE").performScrollTo().performClick()
        onNodeWithTag("meridian_root").performKeyInput { pressKey(Key.N) }
        assertEquals(KeyBindingSpec("N"), repo.bindingFor(ReaderAction.NEXT_PAGE))
        onNodeWithTag("rebind_action_PREV_PAGE").performScrollTo().performClick()
        onNodeWithTag("meridian_root").performKeyInput { pressKey(Key.Escape) }
        assertEquals(ReaderAction.PREV_PAGE.defaultSpec, repo.bindingFor(ReaderAction.PREV_PAGE))
        onNodeWithTag("rebind_prompt_banner").assertDoesNotExist()
    }
}

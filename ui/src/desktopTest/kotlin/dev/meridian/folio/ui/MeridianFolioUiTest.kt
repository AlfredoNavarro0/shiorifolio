package dev.meridian.folio.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.graphics.asSkiaBitmap
import org.jetbrains.skia.Image
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import androidx.compose.ui.test.runComposeUiTest
import dev.meridian.folio.core.FolioRepository
import dev.meridian.folio.core.PageViewMode
import dev.meridian.folio.core.PdfPageColorMode
import dev.meridian.folio.core.ReaderTheme
import dev.meridian.folio.ui.theme.ClayTokens
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class MeridianFolioUiTest {

    @Test
    fun shioriFolioRendersAndSwitchesViewModesAndThemes() = runComposeUiTest {
        val repo = FolioRepository(persistToDisk = false)
        setContent {
            MeridianFolioApp(initialSection = MeridianSection.READER, repository = repo)
        }

        // Verify root, brand wordmark "shiorifolio", prediction badge, and reminder banner exist
        onNodeWithTag("meridian_root").assertExists()
        onNodeWithText("shiorifolio").assertExists()
        onNodeWithTag("prediction_badge").assertExists()
        onNodeWithTag("page_reminder_banner").assertExists()

        // Switch through all 4 viewing modes: 1 Column, 2 Columns, Stretch, Wide
        for (mode in PageViewMode.entries) {
            onNodeWithTag("view_mode_${mode.name}").performClick()
            assertEquals(mode, repo.state.value.viewMode)
        }

        // Toggle theme between White (Sage Paper) and Dark (Nocturne)
        assertEquals(ReaderTheme.WHITE, repo.state.value.theme)
        onNodeWithTag("theme_toggle_button").performClick()
        assertEquals(ReaderTheme.DARK, repo.state.value.theme)
        onNodeWithTag("theme_toggle_button").performClick()
        assertEquals(ReaderTheme.WHITE, repo.state.value.theme)

        // Capture the native reader in every tone after exercising the shared controls.
        repo.setViewMode(PageViewMode.TWO_COLUMNS)
        for (theme in ReaderTheme.entries) {
            repo.setTheme(theme)
            waitForIdle()
            val snapshot = onNodeWithTag("meridian_root").captureToImage()
            Image.makeFromBitmap(snapshot.asSkiaBitmap()).use { image ->
                image.encodeToData(EncodedImageFormat.PNG)?.use {
                    File("build/reader-modern-${theme.name}.png").writeBytes(it.bytes)
                }
            }
        }
    }

    @Test
    fun reminderEditorClosesWithoutSavingOnCancel() = runComposeUiTest {
        val repo = FolioRepository(persistToDisk = false)
        val initialReminderCount = repo.state.value.reminders.size

        setContent {
            MeridianFolioApp(initialSection = MeridianSection.READER, repository = repo)
        }

        onNodeWithTag("open_reminder_btn").performClick()
        onNodeWithTag("close_reminder_editor_btn").assertExists()
        onNodeWithTag("close_reminder_editor_btn").performClick()

        // Editor should be closed and reminder count unchanged
        onNodeWithTag("close_reminder_editor_btn").assertDoesNotExist()
        assertEquals(initialReminderCount, repo.state.value.reminders.size)
    }

    @Test
    fun zenModeAndFullscreenOperateIndependently() = runComposeUiTest {
        val repo = FolioRepository(persistToDisk = false)
        var fullscreenToggledCount = 0

        setContent {
            MeridianFolioApp(initialSection = MeridianSection.READER,
                repository = repo,
                isWindowFullscreen = fullscreenToggledCount % 2 == 1,
                onToggleWindowFullscreen = { fullscreenToggledCount++ },
            )
        }

        // Toggle platform fullscreen without hiding Zen controls
        onNodeWithTag("fullscreen_toggle_btn").assertExists()
        onNodeWithTag("fullscreen_toggle_btn").performClick()
        assertEquals(1, fullscreenToggledCount)
        onNodeWithTag("prediction_badge").assertExists()

        // Enter Zen Mode (hides controls, shows floating exit button)
        readerMenuItem("zen_mode_toggle_btn").performClick()
        onNodeWithTag("exit_zen_mode_btn").assertExists()
        onNodeWithTag("prediction_badge").assertDoesNotExist()

        // Exit Zen Mode and verify controls return
        onNodeWithTag("exit_zen_mode_btn").performClick()
        onNodeWithTag("prediction_badge").assertExists()
    }

    @Test
    fun pdfPageColorModeCyclesIndependentlyOfTheme() {
        val repo = FolioRepository(persistToDisk = false)
        assertEquals(ReaderTheme.WHITE, repo.state.value.theme)
        assertEquals(PdfPageColorMode.THEME_TINT, repo.state.value.pdfPageColorMode)

        repo.cyclePdfPageColorMode()
        assertEquals(PdfPageColorMode.SMART_DARK, repo.state.value.pdfPageColorMode)
        assertEquals(ReaderTheme.WHITE, repo.state.value.theme)

        repo.setPdfPageColorMode(PdfPageColorMode.ORIGINAL)
        assertEquals(PdfPageColorMode.ORIGINAL, repo.state.value.pdfPageColorMode)
        assertTrue(ClayTokens.readingTone(ReaderTheme.DARK).ink.luminance > 0.7f)
    }

    @Test
    fun navigationHistoryDistinguishesPageTurnsFromJumpsAndRestoresPosition() = runComposeUiTest {
        val repo = FolioRepository(persistToDisk = false)
        val bookId = repo.currentBook()!!.id
        repo.currentBook()!!.pages.forEach { page -> repo.updatePageContent(bookId, page.pageNumber, "Historial", listOf(dev.meridian.folio.core.ContentBlock("short-${page.pageNumber}", text = "Una página breve para probar el historial."))) }

        setContent {
            MeridianFolioApp(initialSection = MeridianSection.READER, repository = repo)
        }

        // Sequential page turn via next_page_button does NOT create navigation history
        val initialPage = repo.currentBook()!!.currentPage
        onNodeWithTag("next_page_button").performClick()
        val pageAfterStep = repo.currentBook()!!.currentPage
        assertTrue(pageAfterStep > initialPage)
        assertEquals(0, repo.state.value.navigationBackStack.size)
        readerMenuItem("nav_history_back_btn").assertIsNotEnabled()

        // Semantic jump (e.g., jumping to a chapter or bookmark) records history
        val targetPage = repo.currentBook()!!.totalPages
        repo.jumpToLocation(bookId, targetPage, reason = "Capítulo final")
        waitForIdle()
        assertEquals(targetPage, repo.currentBook()!!.currentPage)
        assertEquals(1, repo.state.value.navigationBackStack.size)

        // Back button becomes enabled in the reader header and restores previous reading position
        readerMenuItem("nav_history_back_btn").assertIsEnabled()
        readerMenuItem("nav_history_back_btn").performClick()
        waitForIdle()
        assertEquals(pageAfterStep, repo.currentBook()!!.currentPage)

        // Forward button becomes enabled and returns to the jumped location
        readerMenuItem("nav_history_forward_btn").assertIsEnabled()
        readerMenuItem("nav_history_forward_btn").performClick()
        waitForIdle()
        assertEquals(targetPage, repo.currentBook()!!.currentPage)
    }

    @Test
    fun settingsWorkspaceAllowsCustomizingAndResettingKeyboardShortcuts() = runComposeUiTest {
        val repo = FolioRepository(persistToDisk = false)

        setContent {
            MeridianFolioApp(initialSection = MeridianSection.READER, repository = repo)
        }

        // Switch to Settings tab
        onNodeWithTag("section_tab_SETTINGS").assertExists()
        onNodeWithTag("section_tab_SETTINGS").performClick()
        waitForIdle()

        onNodeWithTag("settings_workspace_view").assertExists()
        onNodeWithTag("settings_category_shortcuts").performClick()
        onNodeWithTag("shortcut_badge_TOGGLE_ZEN_MODE").assertExists()

        // Customize a shortcut and verify persistence in repository state
        val customSpec = dev.meridian.folio.core.KeyBindingSpec(keyName = "Z", ctrl = true, alt = true)
        repo.updateKeyBinding(dev.meridian.folio.core.ReaderAction.TOGGLE_ZEN_MODE, customSpec)
        waitForIdle()
        assertEquals("Ctrl + Alt + Z", repo.bindingFor(dev.meridian.folio.core.ReaderAction.TOGGLE_ZEN_MODE).displayLabel())

        // Click "Restaurar Predeterminados" button in Settings UI and verify default binding returns
        onNodeWithTag("reset_shortcuts_btn").performScrollTo().performClick()
        waitForIdle()
        assertEquals(
            dev.meridian.folio.core.ReaderAction.TOGGLE_ZEN_MODE.defaultSpec,
            repo.bindingFor(dev.meridian.folio.core.ReaderAction.TOGGLE_ZEN_MODE),
        )
    }

    @Test
    fun appearanceAndBookTypographySettingsDistinguishUiFromBookAndPdfOriginalView() = runComposeUiTest {
        val repo = FolioRepository(persistToDisk = false)
        val pdfBook = repo.state.value.documents.first { it.format == dev.meridian.folio.core.DocumentFormat.PDF }
        repo.selectBook(pdfBook.id)

        setContent {
            MeridianFolioApp(initialSection = MeridianSection.READER, repository = repo)
        }

        // Selected book is a PDF in Original View: dock should show PDF Original mode toggle instead of reflow font-size A-/A+
        val initialBook = repo.currentBook()!!
        assertEquals(dev.meridian.folio.core.DocumentFormat.PDF, initialBook.format)
        onNodeWithTag("dock_pdf_reflow_toggle").assertExists()

        // Open Settings tab and verify UI Appearance & Book Typography cards
        onNodeWithTag("section_tab_SETTINGS").performClick()
        waitForIdle()

        onNodeWithTag("settings_ui_appearance_card").assertExists()
        onNodeWithTag("settings_category_reading").performClick()
        onNodeWithTag("settings_book_typography_card").assertExists()

        // In PDF Original View, the notice is displayed instead of reflow font pickers
        onNodeWithTag("pdf_original_typography_notice").performScrollTo().assertExists()

        // Switch PDF to Adaptive Reflow Text mode and verify reflow typography controls appear
        onNodeWithTag("settings_pdf_reflow_mode_btn").performScrollTo().performClick()
        waitForIdle()
        assertTrue(repo.state.value.bookTypography.pdfPreferReflowText)
        onNodeWithTag("book_font_family_MONOSPACE").performScrollTo().assertExists()
        onNodeWithTag("book_font_family_MONOSPACE").performScrollTo().performClick()
        waitForIdle()
        assertEquals(
            dev.meridian.folio.core.BookFontFamilyOption.MONOSPACE,
            repo.state.value.bookTypography.fontFamily,
        )

        // Modify UI scale and high contrast in UI Appearance card
        onNodeWithTag("settings_category_appearance").performClick()
        onNodeWithTag("ui_scale_inc_btn").performScrollTo().performClick()
        onNodeWithTag("ui_high_contrast_toggle").performScrollTo().performClick()
        waitForIdle()
        assertTrue(repo.state.value.uiAppearance.uiScale > 1.0f)
        assertTrue(repo.state.value.uiAppearance.highContrastText)

        // Reset all appearance and book typography settings to defaults
        onNodeWithTag("reset_all_appearance_btn").performScrollTo().performClick()
        waitForIdle()
        assertEquals(1.0f, repo.state.value.uiAppearance.uiScale)
        assertEquals(false, repo.state.value.uiAppearance.highContrastText)
        assertEquals(false, repo.state.value.bookTypography.pdfPreferReflowText)
        assertEquals(
            dev.meridian.folio.core.BookFontFamilyOption.SERIF,
            repo.state.value.bookTypography.fontFamily,
        )
    }

    @Test
    fun viewingModesZoomControlsAndPdfReflowOperateAndRememberPerBook() = runComposeUiTest {
        val repo = FolioRepository(persistToDisk = false)
        val pdfBook = repo.state.value.documents.first { it.format == dev.meridian.folio.core.DocumentFormat.PDF }
        repo.selectBook(pdfBook.id)

        setContent {
            MeridianFolioApp(initialSection = MeridianSection.READER, repository = repo)
        }

        // Verify Fit & Manual Zoom controls in reader utility bar
        onNodeWithTag("zoom_fit_control_strip").assertExists()
        onNodeWithTag("fit_mode_FIT_WIDTH").performClick()
        waitForIdle()
        assertEquals(dev.meridian.folio.core.PageFitMode.FIT_WIDTH, repo.state.value.fitMode)

        onNodeWithTag("zoom_increase_btn").performClick()
        waitForIdle()
        assertEquals(dev.meridian.folio.core.PageFitMode.MANUAL_ZOOM, repo.state.value.fitMode)
        assertTrue(repo.state.value.zoomScale > 1.0f)

        // Switch to Vertical Paginated mode and verify canvas renders
        onNodeWithTag("view_mode_VERTICAL_PAGINATED").performClick()
        waitForIdle()
        onNodeWithTag("vertical_paginated_canvas").assertExists()

        // Switch to Vertical Continuous mode and verify continuous flow canvas renders
        onNodeWithTag("view_mode_VERTICAL_CONTINUOUS").performClick()
        waitForIdle()
        onNodeWithTag("vertical_continuous_canvas").assertExists()

        // Switch to Adaptive Reflow Text mode and verify reflow canvas + return to Original PDF button
        onNodeWithTag("view_mode_REFLOW_TEXT").performClick()
        waitForIdle()
        onNodeWithTag("adaptive_reflow_canvas").assertExists()
        onNodeWithTag("reflow_fallback_original_btn").assertExists()
        onNodeWithTag("reflow_fallback_original_btn").performClick()
        waitForIdle()
        assertEquals(PageViewMode.ONE_COLUMN, repo.state.value.viewMode)
    }

    @Test
    fun settingsToneCardsAndLivePreviewBehave() = runComposeUiTest {
        val repo = FolioRepository(persistToDisk = false)

        setContent {
            MeridianFolioApp(initialSection = MeridianSection.READER, repository = repo)
        }

        onNodeWithTag("section_tab_SETTINGS").performClick()
        waitForIdle()

        // Clay header and the live preview built from the real current settings.
        onNodeWithTag("settings_workspace_view").assertExists()
        onNodeWithTag("settings_header").assertExists()
        onNodeWithTag("settings_preview_card").assertExists()

        // The four real tone cards exist; selecting one drives the repository theme.
        ReaderTheme.entries.forEach { value ->
            onNodeWithTag("settings_tone_card_${value.name}").assertExists()
        }
        onNodeWithTag("settings_theme_DARK").performScrollTo().performClick()
        waitForIdle()
        assertEquals(ReaderTheme.DARK, repo.state.value.theme)
        onNodeWithTag("settings_theme_WHITE").performScrollTo().performClick()
        waitForIdle()
        assertEquals(ReaderTheme.WHITE, repo.state.value.theme)

        // The immersive shortcut row opens the Reader through its real binding.
        onNodeWithTag("settings_category_shortcuts").performClick()
        waitForIdle()
        onNodeWithTag("settings_try_reader_btn").performScrollTo().performClick()
        waitForIdle()
        onNodeWithTag("settings_workspace_view").assertDoesNotExist()
        onNodeWithTag("reader_page_indicator").assertExists()
    }
}

private val androidx.compose.ui.graphics.Color.luminance: Float
    get() = 0.2126f * red + 0.7152f * green + 0.0722f * blue

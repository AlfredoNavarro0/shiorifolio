package dev.meridian.folio.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.runComposeUiTest
import dev.meridian.folio.core.FolioRepository
import dev.meridian.folio.core.ReaderTheme
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Contract of the claymorphic shell ([ShellChrome]): the wordmark, the four section tabs, the
 * theme toggle, the footer tally and the mobile bottom navigation that the pre-migration tests
 * relied on must keep their exact tags and text.
 */
@OptIn(ExperimentalTestApi::class)
class ClayShellTest {

    @Test
    fun clayTopNavShowsWordmarkAndEverySectionTab() = runComposeUiTest {
        val repo = FolioRepository(persistToDisk = false)
        setContent { MeridianFolioApp(repository = repo) }

        onNodeWithTag("clay_top_nav").assertExists()
        onNodeWithText("shiorifolio").assertExists()
        for (section in MeridianSection.entries) {
            onNodeWithTag("section_tab_${section.name}").assertExists()
        }
        // The desktop layout must not render the mobile chrome at the same time.
        onNodeWithTag("clay_bottom_nav").assertDoesNotExist()
    }

    @Test
    fun sectionTabClickUpdatesActiveSection() = runComposeUiTest {
        val repo = FolioRepository(persistToDisk = false)
        setContent { MeridianFolioApp(repository = repo) }

        onNodeWithTag("section_tab_LIBRARY").performClick()
        waitForIdle()
        onNodeWithTag("library_workspace").assertExists()

        onNodeWithTag("section_tab_READER").performClick()
        waitForIdle()
        onNodeWithTag("prediction_badge").assertExists()
    }

    @Test
    fun themeToggleButtonSwitchesBetweenWhiteAndDark() = runComposeUiTest {
        val repo = FolioRepository(persistToDisk = false)
        setContent { MeridianFolioApp(repository = repo) }

        assertEquals(ReaderTheme.WHITE, repo.state.value.theme)
        onNodeWithTag("theme_toggle_button").performClick()
        assertEquals(ReaderTheme.DARK, repo.state.value.theme)
        onNodeWithTag("theme_toggle_button").performClick()
        assertEquals(ReaderTheme.WHITE, repo.state.value.theme)
    }

    @Test
    fun clayFooterRendersBrandAndLibraryTally() = runComposeUiTest {
        val repo = FolioRepository(persistToDisk = false)
        val count = repo.state.value.documents.size
        setContent { MeridianFolioApp(repository = repo) }

        onNodeWithTag("clay_footer").assertExists()
        onNodeWithText("ShioriFolio · Lector y editor de PDF & EPUB").assertExists()
        onNodeWithText("$count títulos en tu biblioteca").assertExists()
    }

    @Test
    fun chromeStaysLightWhenReadingToneIsDark() = runComposeUiTest {
        val repo = FolioRepository(persistToDisk = false)
        repo.setTheme(ReaderTheme.DARK)
        setContent { MeridianFolioApp(repository = repo) }

        // The shell never adopts the reading sheet tone: only the readingTone tokens switch.
        onNodeWithTag("clay_top_nav").assertExists()
        onNodeWithText("shiorifolio").assertExists()
        onNodeWithTag("clay_footer").assertExists()
        assertEquals(ReaderTheme.DARK, repo.state.value.theme)
    }

    @Test
    fun mobilePreviewToggleRevealsBottomNavInsteadOfTopNav() = runComposeUiTest {
        val repo = FolioRepository(persistToDisk = false)
        setContent { MeridianFolioApp(repository = repo) }

        // The toggle lives at the end of the horizontally scrolling reader action bar.
        onNodeWithTag("mobile_preview_toggle").performScrollTo().performClick()
        waitForIdle()

        onNodeWithTag("clay_bottom_nav").assertExists()
        onNodeWithTag("clay_top_nav").assertDoesNotExist()
        onNodeWithText("shiorifolio").assertExists()
    }
}

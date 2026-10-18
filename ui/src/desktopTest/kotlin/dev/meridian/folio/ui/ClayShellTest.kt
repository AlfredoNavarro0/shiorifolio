package dev.meridian.folio.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import dev.meridian.folio.core.FolioRepository
import dev.meridian.folio.core.ReaderTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Contract of the claymorphic shell ([ShellChrome]): the wordmark, the four section tabs, the
 * theme toggle, the footer tally and the compact bottom navigation that the pre-migration tests
 * relied on must keep their exact tags and text, and the chrome must stay usable (and light)
 * whatever the reading tone is.
 */
@OptIn(ExperimentalTestApi::class)
class ClayShellTest {

    @Test
    fun clayTopNavShowsWordmarkAndEverySectionTab() = runComposeUiTest {
        val repo = FolioRepository(persistToDisk = false)
        setContent { MeridianFolioApp(repository = repo) }

        // The card must be explicitly sized: ClayCard cannot size itself (its shadow and
        // frosted layers are matchParentSize children), so a 0×0 pill would mean a regression.
        onNodeWithTag("clay_top_nav")
            .assertExists()
            .assertWidthIsAtLeast(900.dp)
            .assertHeightIsAtLeast(60.dp)
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
    fun clayChromeStaysLightWithDarkReadingTone() = runComposeUiTest {
        val repo = FolioRepository(persistToDisk = false)
        repo.setTheme(ReaderTheme.DARK)
        setContent { MeridianFolioApp(repository = repo) }

        // The shell never adopts the reading sheet tone: the theme state is reflected in the
        // toggle label, but the chrome itself keeps the light clay palette.
        onNodeWithTag("clay_top_nav").assertExists()
        onNodeWithText("☾  Oscuro").assertExists()
        onNodeWithText("shiorifolio").assertExists()
        onNodeWithTag("clay_footer").assertExists()
        assertEquals(ReaderTheme.DARK, repo.state.value.theme)

        // Sample a band just under the top edge of the pill (card surface, clear of the tab
        // labels) and require it to be light; a chrome that followed the dark reading tone
        // would sample at roughly a third of this luminance.
        val nav = onNodeWithTag("clay_top_nav").captureToImage()
        val pixels = nav.toPixelMap()
        val y = (nav.height * 0.05f).toInt().coerceAtLeast(2)
        for (fraction in listOf(0.05f, 0.25f, 0.5f, 0.75f, 0.95f)) {
            val x = (nav.width * fraction).toInt()
            val pixel = pixels[x, y]
            val luminance = 0.2126f * pixel.red + 0.7152f * pixel.green + 0.0722f * pixel.blue
            assertTrue(
                luminance > 0.45f,
                "clay chrome must stay light with a dark reading tone (luminance $luminance at $x,$y)",
            )
        }
    }

    @Test
    fun compactWidthRendersBottomNavAndKeepsEveryTabReachable() = runComposeUiTest {
        val repo = FolioRepository(persistToDisk = false)
        // The narrowest compact width: below the < 700.dp threshold the mobile Kindle layout
        // takes over, and the four tabs no longer fit in the bottom bar.
        setContent {
            Box(Modifier.size(300.dp, 640.dp)) {
                MeridianFolioApp(repository = repo)
            }
        }

        val nav = onNodeWithTag("clay_bottom_nav")
            .assertExists()
            .assertWidthIsAtLeast(250.dp)
        // The mobile reader reserves exactly ClayBottomNavHeight (this card plus the padding
        // around it) at the bottom of its reading canvases: the card must match it exactly,
        // otherwise the canvas clearance is wrong.
        nav.assertHeightIsEqualTo(ClayBottomNavCardHeight)
        // The bar is stacked under the page dock, so the reading canvas only has to clear
        // dock + pace pill + bar (the reservations add 96.dp/110.dp + ClayBottomNavHeight).
        val navTop = nav.fetchSemanticsNode().boundsInRoot.top
        val dockBottom = onNodeWithTag("next_page_button").fetchSemanticsNode().boundsInRoot.bottom
        assertTrue(
            navTop >= dockBottom,
            "bottom nav must sit below the page dock (nav top=$navTop, dock bottom=$dockBottom)",
        )
        onNodeWithTag("clay_top_nav").assertDoesNotExist()
        for (section in MeridianSection.entries) {
            onNodeWithTag("section_tab_${section.name}").assertExists()
        }
        // The tab row must be scrollable, otherwise the trailing tabs are clipped and
        // unreachable; performScrollTo fails when there is no scrollable ancestor.
        onNodeWithTag("section_tab_SETTINGS").performScrollTo().assertExists()
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

    @Test
    fun zenModeHidesFooterWithTheRestOfTheChrome() = runComposeUiTest {
        val repo = FolioRepository(persistToDisk = false)
        setContent { MeridianFolioApp(repository = repo) }

        onNodeWithTag("clay_footer").assertExists()
        onNodeWithTag("clay_top_nav").assertExists()

        onNodeWithTag("zen_mode_toggle_btn").performClick()
        waitForIdle()
        // Zen Mode hides the whole shell chrome, footer included.
        onNodeWithTag("clay_footer").assertDoesNotExist()
        onNodeWithTag("clay_top_nav").assertDoesNotExist()

        onNodeWithTag("exit_zen_mode_btn").performClick()
        waitForIdle()
        onNodeWithTag("clay_footer").assertExists()
        onNodeWithTag("clay_top_nav").assertExists()
    }
}

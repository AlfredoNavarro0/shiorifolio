package dev.meridian.folio.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import dev.meridian.folio.core.FolioRepository
import dev.meridian.folio.core.PageViewMode
import dev.meridian.folio.core.ReaderTheme
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class MeridianFolioUiTest {

    @Test
    fun shioriFolioRendersAndSwitchesViewModesAndThemes() = runComposeUiTest {
        val repo = FolioRepository(persistToDisk = false)
        setContent {
            MeridianFolioApp(repository = repo)
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
    }
}

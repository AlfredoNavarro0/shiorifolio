package dev.meridian.folio.ui.theme

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import dev.meridian.folio.core.ReaderTheme
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class ClayComponentsTest {

    @Test
    fun primaryButtonClickFiresCallbackAndCarriesTag() = runComposeUiTest {
        var clicks = 0
        setContent {
            ClayTheme {
                ClayPrimaryButton("Importar libro", onClick = { clicks++ }, tag = "library_import_btn")
            }
        }
        onNodeWithTag("library_import_btn").assertExists()
        onNodeWithTag("library_import_btn").performClick()
        waitForIdle()
        assertEquals(1, clicks)
    }

    @Test
    fun secondaryButtonDisabledDoesNotFireCallback() = runComposeUiTest {
        var clicks = 0
        setContent {
            ClayTheme {
                ClaySecondaryButton("Cancelar", onClick = { clicks++ }, tag = "cancel_btn", enabled = false)
            }
        }
        onNodeWithTag("cancel_btn").assertExists()
        onNodeWithTag("cancel_btn").performClick()
        waitForIdle()
        assertEquals(0, clicks)
    }

    @Test
    fun chipClickFiresCallbackAndCarriesTag() = runComposeUiTest {
        var clicked: String? = null
        setContent {
            ClayTheme {
                ClayChip("Todos", selected = true, onClick = { clicked = "Todos" }, tag = "library_filter_chip_ALL")
            }
        }
        onNodeWithTag("library_filter_chip_ALL").assertExists()
        onNodeWithTag("library_filter_chip_ALL").performClick()
        waitForIdle()
        assertEquals("Todos", clicked)
    }

    @Test
    fun segmentedTabsEmitSelectedIndex() = runComposeUiTest {
        var selectedIndex = -1
        setContent {
            ClayTheme {
                ClaySegmentedTabs(
                    items = listOf("Apariencia", "Lectura"),
                    selectedIndex = 0,
                    onSelect = { selectedIndex = it },
                    tagPrefix = "settings_tab_",
                )
            }
        }
        onNodeWithTag("settings_tab_Apariencia").assertExists()
        onNodeWithTag("settings_tab_Lectura").performClick()
        waitForIdle()
        assertEquals(1, selectedIndex)
    }

    @Test
    fun chromeActionClickFiresCallbackAndCarriesTag() = runComposeUiTest {
        var clicks = 0
        setContent {
            ClayTheme {
                ClayChromeAction(
                    "Buscar",
                    ClayTokens.chrome(),
                    "reader_search_btn",
                    description = "Buscar y reemplazar en el libro",
                ) {
                    clicks++
                }
            }
        }
        onNodeWithTag("reader_search_btn").assertExists()
        onNodeWithTag("reader_search_btn").performClick()
        waitForIdle()
        assertEquals(1, clicks)
    }

    @Test
    fun chromeActionDisabledDoesNotFireCallback() = runComposeUiTest {
        var clicks = 0
        setContent {
            ClayTheme {
                ClayChromeAction(
                    "Deshacer",
                    ClayTokens.chrome(),
                    "reader_undo_btn",
                    enabled = false,
                    onClick = { clicks++ },
                )
            }
        }
        onNodeWithTag("reader_undo_btn").performClick()
        waitForIdle()
        assertEquals(0, clicks)
    }

    @Test
    fun toneSwatchesEmitSelectedReaderTheme() = runComposeUiTest {
        var selected: ReaderTheme? = null
        setContent {
            ClayTheme {
                ClayToneSwatches(selected = ReaderTheme.WHITE, onSelect = { selected = it })
            }
        }
        onNodeWithTag("reader_tone_WHITE").assertExists()
        onNodeWithTag("reader_tone_DARK").assertExists()
        onNodeWithTag("reader_tone_SAGE").performClick()
        waitForIdle()
        assertEquals(ReaderTheme.SAGE, selected)
    }
}

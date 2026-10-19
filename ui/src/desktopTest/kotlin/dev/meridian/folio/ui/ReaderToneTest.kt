package dev.meridian.folio.ui

import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import dev.meridian.folio.core.FolioRepository
import dev.meridian.folio.core.ReaderTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Contract of the reading tone in the desktop reader workspace: the clay book strip and the
 * floating command dock carry the new tags, the dock's tone swatches drive [ReaderTheme], and
 * the sheet — and only the sheet — repaints with the tone while the chrome stays light clay.
 */
@OptIn(ExperimentalTestApi::class)
class ReaderToneTest {

    @Test
    fun readerWorkspaceExposesBookStripDockAndEveryToneSwatch() = runComposeUiTest {
        val repo = FolioRepository(persistToDisk = false)
        setContent { MeridianFolioApp(repository = repo) }

        onNodeWithTag("reader_book_strip").assertExists()
        onNodeWithTag("reader_back_to_library_btn").assertExists()
        onNodeWithTag("reader_dock").assertExists()
        onNodeWithTag("reader_page_indicator").assertExists()
        onNodeWithTag("reader_progress_slider").assertExists()
        for (theme in ReaderTheme.entries) {
            onNodeWithTag("reader_tone_${theme.name}").assertExists()
        }
    }

    @Test
    fun dockToneSwatchesDriveTheReaderTheme() = runComposeUiTest {
        val repo = FolioRepository(persistToDisk = false)
        setContent { MeridianFolioApp(repository = repo) }

        assertEquals(ReaderTheme.WHITE, repo.state.value.theme)
        for (theme in listOf(ReaderTheme.SAGE, ReaderTheme.DARK, ReaderTheme.WHITE)) {
            onNodeWithTag("reader_tone_${theme.name}").performClick()
            waitForIdle()
            assertEquals(theme, repo.state.value.theme)
        }
    }

    @Test
    fun sheetFollowsTheToneWhileTheChromeStaysLight() = runComposeUiTest {
        val repo = FolioRepository(persistToDisk = false)
        setContent { MeridianFolioApp(repository = repo) }

        onNodeWithTag("reader_tone_WHITE").performClick()
        waitForIdle()
        val lightSheet = dominantSheetLuminance()

        onNodeWithTag("reader_tone_DARK").performClick()
        waitForIdle()
        val darkSheet = dominantSheetLuminance()

        // The dominant colour of the reading band is the sheet background: near white on the
        // Kindle-Warm tone and near black on the nocturnal one. A chrome-coloured sheet would
        // keep the same luminance in both cases.
        assertTrue(
            lightSheet > 0.80f,
            "the WHITE tone must paint a light sheet (dominant luminance $lightSheet)",
        )
        assertTrue(
            darkSheet < 0.35f,
            "the DARK tone must paint a dark sheet (dominant luminance $darkSheet)",
        )

        // The chrome is tone independent: the dock stays the light clay surface.
        val dockPixels = onNodeWithTag("reader_dock").captureToImage().toPixelMap()
        val dockY = (dockPixels.height * 0.5f).toInt()
        val cornerLuminance = luminance(dockPixels[dockPixels.width / 20, dockY])
        assertTrue(
            cornerLuminance > 0.45f,
            "the dock must stay light clay with a dark reading tone (luminance $cornerLuminance)",
        )
    }

    @Test
    fun progressSliderCommitsThePageWhenTheGestureEnds() = runComposeUiTest {
        val repo = FolioRepository(persistToDisk = false)
        setContent { MeridianFolioApp(repository = repo) }

        val startPage = repo.currentBook()!!.currentPage
        val slider = onNodeWithTag("reader_progress_slider").assertExists()
        // The scrubber must be a real, sized control: a zero-size node would swallow every gesture.
        slider.assertWidthIsAtLeast(120.dp)

        // Seeking with the scrubber must commit through goToPage once the gesture ends.
        slider.performTouchInput { click(percentOffset(x = 0.85f, y = 0.5f)) }
        waitUntil(timeoutMillis = 5_000) { repo.currentBook()!!.currentPage > startPage }
    }

    /** Luminance of the most frequent colour inside the reading band, i.e. the sheet itself. */
    private fun ComposeUiTest.dominantSheetLuminance(): Float {
        val image = onNodeWithTag("meridian_root").captureToImage()
        val pixels = image.toPixelMap()
        val x0 = (image.width * 0.30f).toInt()
        val x1 = (image.width * 0.70f).toInt()
        val y0 = (image.height * 0.35f).toInt()
        val y1 = (image.height * 0.60f).toInt()
        val histogram = HashMap<Int, Int>()
        for (y in y0 until y1) {
            for (x in x0 until x1) {
                val pixel = pixels[x, y]
                val key = (pixel.red * 255).toInt() shl 16 or ((pixel.green * 255).toInt() shl 8) or (pixel.blue * 255).toInt()
                histogram[key] = (histogram[key] ?: 0) + 1
            }
        }
        val dominant = histogram.maxByOrNull { it.value }?.key ?: 0
        return luminance(
            androidx.compose.ui.graphics.Color(
                red = ((dominant shr 16) and 0xFF) / 255f,
                green = ((dominant shr 8) and 0xFF) / 255f,
                blue = (dominant and 0xFF) / 255f,
            ),
        )
    }

    private fun luminance(color: androidx.compose.ui.graphics.Color): Float =
        0.2126f * color.red + 0.7152f * color.green + 0.0722f * color.blue
}

package dev.meridian.folio.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
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
import dev.meridian.folio.ui.theme.ClayTokens
import dev.meridian.folio.ui.theme.ReadingTone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Contract of the reading tone in the desktop reader workspace: the clay book strip and the
 * floating command dock carry the new tags, the dock's tone swatches drive [ReaderTheme], and
 * both the sheet and app chrome repaint with the selected global theme.
 */
@OptIn(ExperimentalTestApi::class)
class ReaderToneTest {

    @Test
    fun readerWorkspaceExposesBookStripDockAndEveryToneSwatch() = runComposeUiTest {
        val repo = FolioRepository(persistToDisk = false)
        setContent { MeridianFolioApp(initialSection = MeridianSection.READER, repository = repo) }

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
        setContent { MeridianFolioApp(initialSection = MeridianSection.READER, repository = repo) }

        assertEquals(ReaderTheme.WHITE, repo.state.value.theme)
        for (theme in listOf(ReaderTheme.SAGE, ReaderTheme.DARK, ReaderTheme.WHITE)) {
            onNodeWithTag("reader_tone_${theme.name}").performClick()
            waitForIdle()
            assertEquals(theme, repo.state.value.theme)
        }
    }

    @Test
    fun sheetAndDockFollowTheGlobalTheme() = runComposeUiTest {
        val repo = FolioRepository(persistToDisk = false)
        setContent { MeridianFolioApp(initialSection = MeridianSection.READER, repository = repo) }

        onNodeWithTag("reader_tone_WHITE").performClick()
        waitForIdle()
        val lightSheet = dominantSheetLuminance()

        onNodeWithTag("reader_tone_DARK").performClick()
        waitForIdle()
        val darkSheet = dominantSheetLuminance()

        // The same band of the same screen must swing from a light reading surface to a dark
        // one. Asserting the swing (a difference) instead of absolute luminance bands keeps the
        // contract valid if the tone tokens are ever retuned; a chrome-coloured sheet would
        // keep the same luminance in both cases and fail.
        assertTrue(
            lightSheet - darkSheet > 0.45f,
            "the sheet must follow the tone (light $lightSheet vs dark $darkSheet)",
        )

        // The dock must turn dark as well; changing only the document is a regression.
        val dockPixels = onNodeWithTag("reader_dock").captureToImage().toPixelMap()
        val dockY = (dockPixels.height * 0.5f).toInt()
        val cornerLuminance = luminance(dockPixels[dockPixels.width / 20, dockY])
        assertTrue(
            cornerLuminance < 0.35f,
            "the dock must follow the global dark theme (dock $cornerLuminance)",
        )
    }

    @Test
    fun progressSliderCommitsThePageWhenTheGestureEnds() = runComposeUiTest {
        val repo = FolioRepository(persistToDisk = false)
        setContent { MeridianFolioApp(initialSection = MeridianSection.READER, repository = repo) }

        val startPage = repo.currentBook()!!.currentPage
        val slider = onNodeWithTag("reader_progress_slider").assertExists()
        // The scrubber must be a real, sized control: a zero-size node would swallow every gesture.
        slider.assertWidthIsAtLeast(120.dp)

        // Seeking with the scrubber must commit through goToPage once the gesture ends.
        slider.performTouchInput { click(percentOffset(x = 0.85f, y = 0.5f)) }
        waitUntil(timeoutMillis = 5_000) { repo.currentBook()!!.currentPage > startPage }
    }

    @Test
    fun sheetCardPaintsItsToneOutlineAboveOpaqueContent() = runComposeUiTest {
        val chrome = ClayTokens.chrome()
        val light = ClayTokens.readingTone(ReaderTheme.WHITE)
        val dark = ClayTokens.readingTone(ReaderTheme.DARK)
        setContent {
            Row {
                // Each card sits on a backdrop that contrasts its own sheet, so the outline is
                // measured against the tone border rather than a coincidentally similar colour.
                Box(Modifier.size(200.dp).background(chrome.onSurface)) {
                    ReaderSheetCard(tone = light, modifier = Modifier.fillMaxSize().testTag("sheet_probe_light")) {
                        // Opaque content repainting the fill, exactly like `StitchBookSpreadLeaf`.
                        Box(Modifier.matchParentSize().background(light.sheet))
                    }
                }
                Box(Modifier.size(200.dp).background(chrome.canvas)) {
                    ReaderSheetCard(tone = dark, modifier = Modifier.fillMaxSize().testTag("sheet_probe_dark")) {
                        Box(Modifier.matchParentSize().background(dark.sheet))
                    }
                }
            }
        }

        val lightEdge = edgeSample("sheet_probe_light", light)
        val darkEdge = edgeSample("sheet_probe_dark", dark)

        // The card must paint its `tone.border` outline on top of the opaque leaf, so the outermost
        // column of the card is the border colour — not the sheet fill. Without the border the edge
        // pixel *is* `tone.sheet` and both assertions below invert, which is precisely the
        // regression guard for the brief-mandated outline.
        assertEdgeIsTheBorder("WHITE", lightEdge, light)
        assertEdgeIsTheBorder("DARK", darkEdge, dark)
    }

    /** The card's left-edge pixel colour, with its centre asserted to be the sheet fill. */
    private fun ComposeUiTest.edgeSample(tag: String, tone: ReadingTone): Color {
        val pixels = onNodeWithTag(tag).captureToImage().toPixelMap()
        val y = pixels.height / 2
        val interior = luminance(pixels[pixels.width / 2, y])
        assertTrue(
            kotlin.math.abs(interior - luminance(tone.sheet)) < 0.02f,
            "the $tag card centre must be the sheet fill (interior $interior vs sheet ${luminance(tone.sheet)})",
        )
        return pixels[0, y]
    }

    private fun assertEdgeIsTheBorder(label: String, edge: Color, tone: ReadingTone) {
        val vsBorder = kotlin.math.abs(luminance(edge) - luminance(tone.border))
        val vsSheet = kotlin.math.abs(luminance(edge) - luminance(tone.sheet))
        assertTrue(
            vsBorder < 0.05f,
            "the $label sheet card edge must be tone.border (edge $vsBorder away from the border)",
        )
        assertTrue(
            vsSheet > 0.05f,
            "the $label sheet card edge must not be the sheet fill (edge $vsSheet away from the sheet)",
        )
    }

    private fun pixelKey(color: Color): Int =
        (color.red * 255).toInt() shl 16 or ((color.green * 255).toInt() shl 8) or (color.blue * 255).toInt()

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
                val key = pixelKey(pixel)
                histogram[key] = (histogram[key] ?: 0) + 1
            }
        }
        val dominant = histogram.maxByOrNull { it.value }?.key ?: 0
        return luminance(
            Color(
                red = ((dominant shr 16) and 0xFF) / 255f,
                green = ((dominant shr 8) and 0xFF) / 255f,
                blue = (dominant and 0xFF) / 255f,
            ),
        )
    }

    private fun luminance(color: Color): Float =
        0.2126f * color.red + 0.7152f * color.green + 0.0722f * color.blue
}

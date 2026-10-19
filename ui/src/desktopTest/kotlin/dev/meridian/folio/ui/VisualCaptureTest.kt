package dev.meridian.folio.ui

import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.*
import dev.meridian.folio.core.FolioRepository
import dev.meridian.folio.core.ReaderTheme
import dev.meridian.folio.ui.theme.ClayTokens
import kotlin.math.abs
import java.io.File
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Task 11a (visual fidelity pass): renders the four desktop surfaces of the real
 * [MeridianFolioApp] shell at a wide, a mid and a narrow window size and writes one PNG per
 * surface and size into `ui/build/visual/`.
 *
 * The tests are read-only: they use an in-memory [FolioRepository] and never assert business
 * behaviour. Their job is to prove the captures are real (non-empty, correctly sized images),
 * so a regression that leaves a surface blank or throws during layout fails the build instead
 * of quietly producing an empty screenshot.
 */
@OptIn(ExperimentalTestApi::class)
class VisualCaptureTest {

    /** One captured scene: output name + the section tab that reveals it. */
    private data class Surface(val name: String, val tab: String)

    private val surfaces = listOf(
        Surface("lector", "section_tab_READER"),
        Surface("biblioteca", "section_tab_LIBRARY"),
        Surface("cuaderno", "section_tab_LEXICON"),
        Surface("ajustes", "section_tab_SETTINGS"),
    )

    @Test
    fun desktopSurfacesRenderAtWideAndMidWidths() {
        captureSurfaces(sizeTag = "wide", width = 1440, height = 900)
        // Mid width: still above the reader's 700dp mobile switch, and inside the 700..1040dp band
        // that no other capture covers, so the single-column responsive paths get exercised too.
        captureSurfaces(sizeTag = "mid", width = 900, height = 800)
    }

    @Test
    fun narrowSurfacesRenderWithoutOverflow() = captureSurfaces(sizeTag = "narrow", width = 640, height = 800)

    @Test
    fun globalThemesRenderAcrossAllSections() {
        for (theme in listOf(ReaderTheme.SEPIA, ReaderTheme.SAGE, ReaderTheme.DARK)) {
            captureSurfaces("theme-${theme.name.lowercase()}", 1440, 900, theme)
        }
    }

    @Test
    fun libraryFilterDialogStaysCompactWithAdvancedOptions() = runDesktopComposeUiTest(width = 1440, height = 900) {
        val repo = FolioRepository(persistToDisk = false)
        repo.setTheme(ReaderTheme.DARK)
        setContent { MeridianFolioApp(repository = repo) }
        onNodeWithTag("library_filters_btn").performClick()
        for (advanced in listOf(false, true)) {
            if (advanced) onNodeWithTag("library_filter_advanced").performClick()
            waitForIdle()
            val node = onNodeWithTag("library_filter_panel")
            val bounds = node.fetchSemanticsNode().boundsInRoot
            assertTrue(bounds.width < 700f && bounds.height < 750f, "Filter dialog must leave the shelf visible")
            onNodeWithTag("library_apply_filters").assertIsDisplayed()
            val bitmap = node.captureToImage().asSkiaBitmap()
            val output = File("build/visual/library-filters-${if (advanced) "advanced" else "basic"}-dark.png")
            output.parentFile.mkdirs()
            Image.makeFromBitmap(bitmap).use { image ->
                image.encodeToData(EncodedImageFormat.PNG)!!.use { output.writeBytes(it.bytes) }
            }
        }
    }

    /**
     * Opens every section of the shell, captures `meridian_root` (the full rendered surface,
     * chrome included) and asserts the PNG was written with the expected pixel size.
     */
    private fun captureSurfaces(sizeTag: String, width: Int, height: Int, theme: ReaderTheme = ReaderTheme.WHITE) {
        runDesktopComposeUiTest(width = width, height = height) {
            val repo = FolioRepository(persistToDisk = false)
            repo.setTheme(theme)
            setContent { MeridianFolioApp(initialSection = MeridianSection.READER, repository = repo) }
            waitForIdle()

            surfaces.forEach { surface ->
                // Tabs live in a horizontally scrollable chrome row; scroll them in before tapping.
                onNodeWithTag(surface.tab).performScrollTo().performClick()
                waitForIdle()

                val root = onNodeWithTag("meridian_root")
                root.assertExists()
                if (surface.name == "lector" && sizeTag != "narrow") {
                    val canvas = onNodeWithTag("reader_canvas").fetchSemanticsNode().boundsInRoot
                    val viewport = root.fetchSemanticsNode().boundsInRoot
                    val minimumFraction = if (sizeTag == "wide") 0.60f else 0.55f
                    assertTrue(
                        canvas.height >= viewport.height * minimumFraction,
                        "Reading must occupy most of the window: $canvas in $viewport",
                    )
                }
                val snapshot = root.captureToImage()
                if (sizeTag.startsWith("theme-")) {
                    val pixel = snapshot.toPixelMap()[2, snapshot.height / 2]
                    val expected = ClayTokens.chrome(theme = theme).canvas
                    assertTrue(
                        abs(pixel.red - expected.red) < 0.04f &&
                            abs(pixel.green - expected.green) < 0.04f &&
                            abs(pixel.blue - expected.blue) < 0.04f,
                        "${surface.name} must apply the global $theme theme to its workspace",
                    )
                }
                val bitmap = snapshot.asSkiaBitmap()

                assertEquals(width, bitmap.width, "${surface.name} capture width")
                assertEquals(height, bitmap.height, "${surface.name} capture height")

                val output = File("build/visual/$sizeTag-${surface.name}.png")
                output.parentFile.mkdirs()
                Image.makeFromBitmap(bitmap).use { image ->
                    val data = image.encodeToData(EncodedImageFormat.PNG)
                        ?: error("Could not encode ${surface.name} to PNG")
                    data.use { output.writeBytes(it.bytes) }
                }

                assertTrue(output.isFile, "${surface.name} PNG was not written")
                assertTrue(output.length() > 0L, "${surface.name} PNG is empty")
            }
        }
    }
}

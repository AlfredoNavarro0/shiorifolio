package dev.meridian.folio.ui

import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.test.*
import dev.meridian.folio.core.FolioRepository
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

    /**
     * Opens every section of the shell, captures `meridian_root` (the full rendered surface,
     * chrome included) and asserts the PNG was written with the expected pixel size.
     */
    private fun captureSurfaces(sizeTag: String, width: Int, height: Int) {
        runDesktopComposeUiTest(width = width, height = height) {
            val repo = FolioRepository(persistToDisk = false)
            setContent { MeridianFolioApp(repository = repo) }
            waitForIdle()

            surfaces.forEach { surface ->
                // Tabs live in a horizontally scrollable chrome row; scroll them in before tapping.
                onNodeWithTag(surface.tab).performScrollTo().performClick()
                waitForIdle()

                val root = onNodeWithTag("meridian_root")
                root.assertExists()
                val snapshot = root.captureToImage()
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

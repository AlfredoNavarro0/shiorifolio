package dev.meridian.folio.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class ClayModifiersTest {

    @Test
    fun shadowFadesOutsideTheCardWithoutSolidOffsetBlocks() = runComposeUiTest {
        setContent {
            Box(Modifier.size(180.dp, 140.dp).background(Color.White).testTag("shadow_canvas")) {
                ClayShadowBox(
                    shadow = ClayShadowStack(
                        outer = listOf(ClayShadowLayer(0.dp, 0.dp, 16.dp, Color.Black.copy(alpha = 0.5f))),
                        inner = emptyList(),
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.align(Alignment.Center).size(100.dp, 80.dp),
                ) {
                    Box(Modifier.matchParentSize().background(Color.White))
                }
            }
        }
        val pixels = onNodeWithTag("shadow_canvas").captureToImage().toPixelMap()
        // Measure along a straight edge, clear of the rounded corners, at any test density.
        fun redAt(dpX: Float) = pixels[(pixels.width * dpX / 180f).toInt(), pixels.height / 2].red
        val near = redAt(142f)
        val middle = redAt(146f)
        val far = redAt(154f)
        assertTrue(near < middle && middle < far, "Shadow must fade smoothly: $near, $middle, $far")
        assertTrue(far > 0.98f, "Shadow must not become a detached background panel")
    }

    @Test
    fun outerLayerRectTranslatesWithoutSpread() {
        val rect = clayOuterLayerRect(
            baseWidthPx = 100f,
            baseHeightPx = 48f,
            dxPx = 10f,
            dyPx = 10f,
            spreadPx = 0f,
        )
        assertEquals(androidx.compose.ui.geometry.Rect(10f, 10f, 110f, 58f), rect)
    }

    @Test
    fun outerLayerRectGrowsWithPositiveSpread() {
        val base = clayOuterLayerRect(100f, 48f, dxPx = 10f, dyPx = 10f, spreadPx = 0f)
        val grown = clayOuterLayerRect(100f, 48f, dxPx = 10f, dyPx = 10f, spreadPx = 4f)
        assertEquals(androidx.compose.ui.geometry.Rect(6f, 6f, 114f, 62f), grown)
        assertTrue(grown.width > base.width)
        assertTrue(grown.height > base.height)
        assertTrue(grown.left < base.left)
        assertTrue(grown.top < base.top)
    }

    @Test
    fun outerLayerRectShrinksWithNegativeSpread() {
        // The floating stack's first layer: 0/20/35 with spread -8.
        val noSpread = clayOuterLayerRect(100f, 48f, dxPx = 0f, dyPx = 20f, spreadPx = 0f)
        val shrunk = clayOuterLayerRect(100f, 48f, dxPx = 0f, dyPx = 20f, spreadPx = -8f)
        assertEquals(androidx.compose.ui.geometry.Rect(8f, 28f, 92f, 60f), shrunk)
        assertTrue(shrunk.width < noSpread.width)
        assertTrue(shrunk.height < noSpread.height)
        assertTrue(shrunk.left > noSpread.left)
        assertTrue(shrunk.top > noSpread.top)
    }

    @Test
    fun degenerateLayerRectsNeverGoNegative() {
        val collapsed = clayOuterLayerRect(10f, 10f, dxPx = 0f, dyPx = 0f, spreadPx = -8f)
        assertEquals(0f, collapsed.width)
        assertEquals(0f, collapsed.height)
        assertTrue(collapsed.left <= collapsed.right)
        assertTrue(collapsed.top <= collapsed.bottom)

        val extreme = clayOuterLayerRect(10f, 10f, dxPx = 0f, dyPx = 0f, spreadPx = -100f)
        assertEquals(0f, extreme.width)
        assertEquals(0f, extreme.height)
        assertTrue(extreme.left <= extreme.right)
        assertTrue(extreme.top <= extreme.bottom)
    }

    @Test
    fun insetBandRectChangesWithSpread() {
        val band = clayInsetLayerRect(100f, 48f, dxPx = 8f, dyPx = 8f, spreadPx = 0f)
        assertEquals(androidx.compose.ui.geometry.Rect(8f, 8f, 108f, 56f), band)
        val shrunk = clayInsetLayerRect(100f, 48f, dxPx = 8f, dyPx = 8f, spreadPx = -8f)
        // 100x48 shrunk by 8 on every side, then translated by (8, 8): 84x32 at (16, 16).
        assertEquals(androidx.compose.ui.geometry.Rect(16f, 16f, 100f, 48f), shrunk)
        assertTrue(shrunk.width < band.width)
        assertTrue(shrunk.height < band.height)

        // Degenerate inset bands collapse instead of flipping.
        val collapsed = clayInsetLayerRect(10f, 10f, dxPx = 0f, dyPx = 0f, spreadPx = -8f)
        assertEquals(0f, collapsed.width)
        assertEquals(0f, collapsed.height)
    }

    @Test
    fun insetStrokeWidthIsHalfBlurWithOnePxFloor() {
        assertEquals(6f, clayInsetStrokeWidthPx(12f))
        assertEquals(1f, clayInsetStrokeWidthPx(0f))
        assertEquals(1f, clayInsetStrokeWidthPx(1f))
    }

    @Test
    fun shadowBoxRendersSpreadAndDegenerateLayersWithoutCrashing() = runComposeUiTest {
        setContent {
            ClayTheme {
                // The floating stack: outer layers carry spread -8/-5, inner layers
                // carry a negative spread, and the second box is fully degenerate.
                ClayShadowBox(
                    shadow = ClayShadows.floating.copy(
                        inner = listOf(ClayShadowLayer(8.dp, 8.dp, 16.dp, Color(0xFFD9D4E3), spread = (-4).dp)),
                    ),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.size(120.dp, 80.dp),
                ) {
                    Box(Modifier.fillMaxSize().background(Color.White).testTag("floating_card"))
                }
                ClayShadowBox(
                    shadow = ClayShadowStack(
                        outer = listOf(ClayShadowLayer(0.dp, 0.dp, 4.dp, Color(0x33000000), spread = (-200).dp)),
                        inner = listOf(ClayShadowLayer(0.dp, 0.dp, 4.dp, Color(0x33000000), spread = (-200).dp)),
                    ),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.size(120.dp, 80.dp),
                ) {
                    Box(Modifier.fillMaxSize().background(Color.White).testTag("degenerate_card"))
                }
            }
        }
        waitForIdle()
        onNodeWithTag("floating_card").assertExists()
        onNodeWithTag("degenerate_card").assertExists()
    }

    @Test
    fun insetOverlayRendersOverOpaqueSurfaceWithoutCrashing() = runComposeUiTest {
        setContent {
            ClayTheme {
                Box(
                    Modifier
                        .size(120.dp, 48.dp)
                        .background(Color(0xFF7C3AED), RoundedCornerShape(20.dp))
                        .clayInsetOverlay(RoundedCornerShape(20.dp), ClayShadows.button.inner)
                        .testTag("overlay_surface"),
                )
            }
        }
        waitForIdle()
        onNodeWithTag("overlay_surface").assertExists()
    }
}

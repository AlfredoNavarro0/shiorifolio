package dev.meridian.folio.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate

/** Soft clay depth drawn consistently on desktop and Android, without platform blur. */
@Composable
fun ClayShadowBox(
    shadow: ClayShadowStack,
    shape: Shape,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(modifier.drawWithContent {
        drawOuterLayers(shape, shadow.outer)
        drawContent()
    }) {
        // Base card clipped to the shape, with the inset shadow layers painted inside.
        Box(
            Modifier
                .matchParentSize()
                .clip(shape)
                .drawWithContent {
                    drawInsetLayers(shape, shadow.inner)
                    drawContent()
                },
        ) {
            content()
        }
    }
}

/**
 * Recessed ("pressed") clay surface: dark inset band on the top-left inner edge and a
 * white inset band on the bottom-right inner edge (values from [ClayShadows.pressed]).
 */
fun Modifier.clayPressed(shape: Shape): Modifier =
    clip(shape).drawWithContent {
        drawContent()
        drawInsetLayers(shape, ClayShadows.pressed.inner)
    }

/** Fine inset shading drawn above opaque surfaces such as primary buttons. */
fun Modifier.clayInsetOverlay(shape: Shape, layers: List<ClayShadowLayer>): Modifier =
    clip(shape).drawWithContent {
        drawContent()
        drawInsetLayers(shape, layers)
    }

/** Solid clay fill: depth comes from ClayShadowBox rather than blurring the fill. */
fun Modifier.clayFrosted(
    shape: Shape,
    surface: Color = Color.White,
): Modifier = clip(shape).background(surface, shape)

/** Spread-adjusted bounds, clamped so a collapsed shadow never has a negative size. */
fun clayOuterLayerRect(
    baseWidthPx: Float,
    baseHeightPx: Float,
    dxPx: Float,
    dyPx: Float,
    spreadPx: Float,
): Rect = Rect(
    offset = Offset(dxPx - spreadPx, dyPx - spreadPx),
    size = Size(
        (baseWidthPx + 2 * spreadPx).coerceAtLeast(0f),
        (baseHeightPx + 2 * spreadPx).coerceAtLeast(0f),
    ),
)

/** Inset bands use the same spread and translation geometry as outer shadows. */
fun clayInsetLayerRect(
    baseWidthPx: Float,
    baseHeightPx: Float,
    dxPx: Float,
    dyPx: Float,
    spreadPx: Float,
): Rect = clayOuterLayerRect(baseWidthPx, baseHeightPx, dxPx, dyPx, spreadPx)

/** Keep even a zero-blur inset visible at one physical pixel. */
fun clayInsetStrokeWidthPx(blurPx: Float): Float = (blurPx / 2f).coerceAtLeast(1f)

/** Feathered outer depth, independent of platform blur support. */
private fun DrawScope.drawOuterLayers(shape: Shape, layers: List<ClayShadowLayer>) {
    layers.forEach { layer ->
        // Concentric translucent outlines feather the edge even when Modifier.blur is
        // unsupported. Start outside and accumulate opacity towards the surface.
        val base = clayOuterLayerRect(size.width, size.height, layer.dx.toPx(), layer.dy.toPx(), layer.spread.toPx())
        if (base.width <= 0f || base.height <= 0f) return@forEach
        val steps = 16
        for (step in steps downTo 1) {
            val feather = layer.blur.toPx() * 0.5f * step / steps
            val rect = Rect(base.left - feather, base.top - feather, base.right + feather, base.bottom + feather)
            val outline = shape.createOutline(rect.size, layoutDirection, this)
            translate(rect.left, rect.top) {
                drawOutline(outline, layer.color.copy(alpha = layer.color.alpha / steps))
            }
        }
    }
}

/** Fine inset rim clipped by the host surface. */
private fun DrawScope.drawInsetLayers(shape: Shape, layers: List<ClayShadowLayer>) {
    if (layers.isEmpty()) return
    layers.forEach { layer ->
        val bandRect = clayInsetLayerRect(
            baseWidthPx = size.width,
            baseHeightPx = size.height,
            dxPx = layer.dx.toPx(),
            dyPx = layer.dy.toPx(),
            spreadPx = layer.spread.toPx(),
        )
        if (bandRect.width <= 0f || bandRect.height <= 0f) return@forEach
        val outline: Outline = shape.createOutline(
            Size(bandRect.width, bandRect.height),
            layoutDirection,
            this,
        )
        translate(bandRect.left, bandRect.top) {
            for (step in 8 downTo 1) {
                drawOutline(
                    outline,
                    color = layer.color.copy(alpha = layer.color.alpha / 8),
                    style = Stroke(width = clayInsetStrokeWidthPx(layer.blur.toPx()) * step / 8),
                )
            }
        }
    }
}

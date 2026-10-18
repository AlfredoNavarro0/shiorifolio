package dev.meridian.folio.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
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
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * Card container with a multi-layer clay shadow stack.
 *
 * The outer (drop) layers are stacked boxes behind the card: each one is offset by
 * (dx, dy), sized by [clayOuterLayerRect] (the base rect inflated by the layer's
 * `spread` — negative spread shrinks it) and blurred; on platforms without blur support
 * (Android < 12) `Modifier.blur` is a no-op and the translucent layer remains as a soft
 * fallback. The base card is clipped to [shape] and paints the inner (inset) layers
 * inside the clip via `drawWithContent`, behind the content so translucent clay cards
 * pick them up without covering opaque content.
 */
@Composable
fun ClayShadowBox(
    shadow: ClayShadowStack,
    shape: Shape,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(modifier) {
        // Outer (drop) shadow layers behind the card.
        shadow.outer.forEach { layer ->
            Box(
                Modifier
                    .matchParentSize()
                    .offset(x = layer.dx - layer.spread, y = layer.dy - layer.spread)
                    .shadowLayerSpread(layer.spread)
                    .blur(layer.blur)
                    .background(layer.color, shape),
            )
        }
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

/**
 * Paints the inset shadow [layers] as soft bands along the inner edges of [shape],
 * clipped to the shape and drawn OVER the element's own content — the overlay variant
 * of the inset painting inside [ClayShadowBox] (which paints behind the content, for
 * translucent surfaces like [ClayCard]). Use it on opaque surfaces (e.g. the gradient
 * of `ClayPrimaryButton`) where the bands would otherwise be hidden.
 */
fun Modifier.clayInsetOverlay(shape: Shape, layers: List<ClayShadowLayer>): Modifier =
    clip(shape).drawWithContent {
        drawContent()
        drawInsetLayers(shape, layers)
    }

/**
 * Frosted-glass clay surface: translucent card background (`cardSurface`) with a blur
 * applied where the platform supports it (Skia/desktop, Android 12+). `Modifier.blur`
 * blurs the element's own rendering rather than the backdrop, so this is meant for the
 * surface layer of a card (content goes in a sibling above it). Where blur is not
 * supported the translucent background remains as the fallback.
 */
fun Modifier.clayFrosted(
    shape: Shape,
    surface: Color = Color.White.copy(alpha = 0.75f),
    blurRadius: Dp = 16.dp,
): Modifier =
    blur(blurRadius)
        .clip(shape)
        .background(surface, shape)

/**
 * Rect covered by an outer (drop) shadow layer, relative to the base surface's top-left
 * corner: the base rect translated by (dx, dy) and inflated by [spreadPx] on every side
 * (negative spread shrinks it). Width/height are coerced to be non-negative so a
 * degenerate layer collapses to an empty rect instead of flipping.
 */
fun clayOuterLayerRect(
    baseWidthPx: Float,
    baseHeightPx: Float,
    dxPx: Float,
    dyPx: Float,
    spreadPx: Float,
): Rect {
    val width = (baseWidthPx + 2 * spreadPx).coerceAtLeast(0f)
    val height = (baseHeightPx + 2 * spreadPx).coerceAtLeast(0f)
    return Rect(
        offset = Offset(dxPx - spreadPx, dyPx - spreadPx),
        size = Size(width, height),
    )
}

/**
 * Rect stroked for an inner (inset) shadow layer: the base rect inflated by [spreadPx]
 * on every side and translated by (dx, dy). The stroke is clipped to the base shape, so
 * the band shows on the edges the layer is offset towards. Same coercion as
 * [clayOuterLayerRect].
 */
fun clayInsetLayerRect(
    baseWidthPx: Float,
    baseHeightPx: Float,
    dxPx: Float,
    dyPx: Float,
    spreadPx: Float,
): Rect = clayOuterLayerRect(baseWidthPx, baseHeightPx, dxPx, dyPx, spreadPx)

/** Stroke width used to paint an inset band: half the layer's blur, at least 1 px. */
fun clayInsetStrokeWidthPx(blurPx: Float): Float = (blurPx / 2f).coerceAtLeast(1f)

/**
 * Resizes the layer box to the rect computed by [clayOuterLayerRect] from the incoming
 * tight constraints (the base size) inflated by [spread] on every side. Negative spread
 * shrinks the box; degenerate sizes collapse to 0 instead of going negative.
 */
private fun Modifier.shadowLayerSpread(spread: Dp): Modifier =
    layout { measurable, constraints ->
        val spreadPx = spread.toPx()
        if (spreadPx == 0f || !constraints.hasBoundedWidth || !constraints.hasBoundedHeight) {
            val placeable = measurable.measure(constraints)
            return@layout layout(placeable.width, placeable.height) {
                placeable.place(0, 0)
            }
        }
        val rect = clayOuterLayerRect(
            baseWidthPx = constraints.maxWidth.toFloat(),
            baseHeightPx = constraints.maxHeight.toFloat(),
            dxPx = 0f,
            dyPx = 0f,
            spreadPx = spreadPx,
        )
        val placeable = measurable.measure(
            Constraints(
                minWidth = rect.width.roundToInt(),
                maxWidth = rect.width.roundToInt(),
                minHeight = rect.height.roundToInt(),
                maxHeight = rect.height.roundToInt(),
            ),
        )
        layout(placeable.width, placeable.height) {
            placeable.place(0, 0)
        }
    }

/**
 * Paints the inset shadow [layers] as soft bands along the inner edges of [shape]:
 * each layer strokes the shape outline of the band rect computed by
 * [clayInsetLayerRect] (base rect inflated by the layer's `spread` and translated by
 * (dx, dy)), so the band shows on the edges the layer is offset towards and is clipped
 * away on the opposite edges. Layers whose band collapses to a non-positive size paint
 * nothing.
 */
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
            drawOutline(
                outline,
                color = layer.color,
                style = Stroke(width = clayInsetStrokeWidthPx(layer.blur.toPx())),
            )
        }
    }
}

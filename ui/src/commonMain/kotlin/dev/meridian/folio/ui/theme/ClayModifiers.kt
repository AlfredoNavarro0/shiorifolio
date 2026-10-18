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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Card container with a multi-layer clay shadow stack.
 *
 * The outer (drop) layers are stacked offset + blurred boxes behind the card; on platforms
 * without blur support (Android < 12) `Modifier.blur` is a no-op and the translucent layer
 * remains as a soft fallback. The base card is clipped to [shape] and paints the inner
 * (inset) layers inside the clip via `drawWithContent`, behind the content so translucent
 * clay cards pick them up without covering opaque content.
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
                    .offset(x = layer.dx, y = layer.dy)
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
 * Paints the inset shadow [layers] as soft bands along the inner edges of [shape]:
 * each layer strokes the shape outline translated by (dx, dy), so the band shows on the
 * edges the layer is offset towards and is clipped away on the opposite edges.
 */
private fun DrawScope.drawInsetLayers(shape: Shape, layers: List<ClayShadowLayer>) {
    if (layers.isEmpty()) return
    val outline: Outline = shape.createOutline(size, layoutDirection, this)
    layers.forEach { layer ->
        translate(layer.dx.toPx(), layer.dy.toPx()) {
            drawOutline(
                outline,
                color = layer.color,
                style = Stroke(width = (layer.blur.toPx() / 2f).coerceAtLeast(1f)),
            )
        }
    }
}

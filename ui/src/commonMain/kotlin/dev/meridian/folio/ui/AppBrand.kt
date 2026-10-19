package dev.meridian.folio.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import meridian_folio.ui.generated.resources.Res
import meridian_folio.ui.generated.resources.shiorifolio_icon
import org.jetbrains.compose.resources.painterResource

/** One emblem shared by the window icon and the navigation on every platform. */
@Composable
fun shioriFolioIconPainter(): Painter = painterResource(Res.drawable.shiorifolio_icon)

package dev.meridian.folio.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.meridian.folio.core.HighlightTone
import dev.meridian.folio.core.ReaderTheme
import kotlin.math.sin

import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import dev.meridian.folio.core.KeyBindingSpec

@Immutable
data class MeridianPalette(
    val isDark: Boolean,
    val paper: Color,
    val pageSurface: Color,
    val elevatedSurface: Color,
    val ink: Color,
    val inkSoft: Color,
    val muted: Color,
    val border: Color,
    val pillBg: Color,
    val accentGreen: Color,
    val warningAmber: Color,
    val onAccent: Color = if (isDark) Color(0xFF121C15) else Color(0xFFFFFFFF),
    val onWarningAmber: Color = if (isDark) Color(0xFF1F120A) else Color(0xFFFFFFFF),
    val disabledInk: Color = if (isDark) Color(0xFF6E675E) else Color(0xFF9C968E),
    val disabledBg: Color = if (isDark) Color(0xFF2A2623) else Color(0xFFEAE5DC),
    val focusRing: Color = if (isDark) Color(0xFFA8D5B5) else Color(0xFF2E5A3C),
    val spineShadow: Color = if (isDark) Color(0xFF0B0908) else Color(0xFF1C1C18),
) {
    fun highlightColor(tone: HighlightTone): Color =
        Color(if (isDark) tone.hexDark else tone.hexLight)

    fun toMaterialColorScheme(): ColorScheme =
        if (isDark) {
            darkColorScheme(
                primary = accentGreen,
                onPrimary = onAccent,
                primaryContainer = pillBg,
                onPrimaryContainer = ink,
                secondary = warningAmber,
                onSecondary = onWarningAmber,
                secondaryContainer = elevatedSurface,
                onSecondaryContainer = ink,
                tertiary = focusRing,
                onTertiary = onAccent,
                background = paper,
                onBackground = ink,
                surface = pageSurface,
                onSurface = ink,
                surfaceVariant = elevatedSurface,
                onSurfaceVariant = inkSoft,
                outline = border,
                outlineVariant = muted,
            )
        } else {
            lightColorScheme(
                primary = accentGreen,
                onPrimary = onAccent,
                primaryContainer = pillBg,
                onPrimaryContainer = ink,
                secondary = warningAmber,
                onSecondary = onWarningAmber,
                secondaryContainer = elevatedSurface,
                onSecondaryContainer = ink,
                tertiary = focusRing,
                onTertiary = onAccent,
                background = paper,
                onBackground = ink,
                surface = pageSurface,
                onSurface = ink,
                surfaceVariant = elevatedSurface,
                onSurfaceVariant = inkSoft,
                outline = border,
                outlineVariant = muted,
            )
        }
}

@Composable
fun shioriOutlinedTextFieldColors(palette: MeridianPalette): TextFieldColors =
    OutlinedTextFieldDefaults.colors(
        focusedTextColor = palette.ink,
        unfocusedTextColor = palette.ink,
        disabledTextColor = palette.disabledInk,
        cursorColor = palette.accentGreen,
        focusedContainerColor = palette.pageSurface,
        unfocusedContainerColor = palette.pageSurface,
        disabledContainerColor = palette.disabledBg,
        focusedBorderColor = palette.accentGreen,
        unfocusedBorderColor = palette.border,
        disabledBorderColor = palette.border.copy(alpha = 0.5f),
        focusedLabelColor = palette.accentGreen,
        unfocusedLabelColor = palette.inkSoft,
        disabledLabelColor = palette.disabledInk,
        focusedPlaceholderColor = palette.muted,
        unfocusedPlaceholderColor = palette.muted,
        focusedLeadingIconColor = palette.inkSoft,
        unfocusedLeadingIconColor = palette.muted,
        focusedTrailingIconColor = palette.inkSoft,
        unfocusedTrailingIconColor = palette.muted,
    )

object MeridianDesign {
    // 1. Kindle Cálido / Hueso (#FCF9F3) — Stitch Default Warm Paper
    val WhitePalette = MeridianPalette(
        isDark = false,
        paper = Color(0xFFFCF9F3),       // Kindle warm e-ink paper surface (#fcf9f3)
        pageSurface = Color(0xFFFFFFFF), // Pure folio spread card (#ffffff)
        elevatedSurface = Color(0xFFF6F3ED),
        ink = Color(0xFF1C1C18),         // Deep carbon ink (#1c1c18)
        inkSoft = Color(0xFF424842),     // Editorial secondary ink (#424842)
        muted = Color(0xFF686E67),       // High-legibility archival caption & outline
        border = Color(0xFFEBDCCB),      // Warm parchment rule (#ebdccb)
        pillBg = Color(0xFFF0EDE7),      // Surface container (#f0ede7)
        accentGreen = Color(0xFF3C4E40), // Sage 700 primary (#3c4e40)
        warningAmber = Color(0xFF984629),// Terracotta / Amber Ribbon (#984629 / #b46b38)
        onAccent = Color(0xFFFFFFFF),
        onWarningAmber = Color(0xFFFFFFFF),
    )

    // 2. Sepia (#F5EDDB) — Stitch Warm Antiquarian Parchment
    val SepiaPalette = MeridianPalette(
        isDark = false,
        paper = Color(0xFFF5EDDB),
        pageSurface = Color(0xFFFAF3E3),
        elevatedSurface = Color(0xFFEBE1CC),
        ink = Color(0xFF261D14),
        inkSoft = Color(0xFF47392B),
        muted = Color(0xFF6E5C49),
        border = Color(0xFFDECBB3),
        pillBg = Color(0xFFE6DAC2),
        accentGreen = Color(0xFF445B49),
        warningAmber = Color(0xFFA65E2E),
        onAccent = Color(0xFFFFFFFF),
        onWarningAmber = Color(0xFFFFFFFF),
    )

    // 3. Salvia (#EEF3EE) — Stitch Meridian Botanical Sage
    val SagePalette = MeridianPalette(
        isDark = false,
        paper = Color(0xFFEEF3EE),
        pageSurface = Color(0xFFF7FAF6),
        elevatedSurface = Color(0xFFE2EAE1),
        ink = Color(0xFF1B2B21),
        inkSoft = Color(0xFF36433A),
        muted = Color(0xFF5A695D),
        border = Color(0xFFC8D6C9),
        pillBg = Color(0xFFDCE5DC),
        accentGreen = Color(0xFF2B3A30),
        warningAmber = Color(0xFF984629),
        onAccent = Color(0xFFFFFFFF),
        onWarningAmber = Color(0xFFFFFFFF),
    )

    // 4. Oscuro / Noche Atenuada (#1E1B18) — High-Contrast Nocturnal Reading Surface
    val DarkPalette = MeridianPalette(
        isDark = true,
        paper = Color(0xFF1E1B18),       // Deep warm obsidian canvas
        pageSurface = Color(0xFF272320), // Distinct dark folio leaf surface
        elevatedSurface = Color(0xFF312C28),
        ink = Color(0xFFF2ECE1),         // Crisp warm ivory ink (high contrast >= 12:1)
        inkSoft = Color(0xFFD5CDC0),     // Clear secondary ink (contrast >= 8.5:1)
        muted = Color(0xFFA89F92),       // Accessible muted captions & labels (contrast >= 5.2:1)
        border = Color(0xFF4A433D),      // Clear warm border rule
        pillBg = Color(0xFF36302B),      // Elevated pill container
        accentGreen = Color(0xFF94C2A0), // Luminous botanical sage for dark surfaces
        warningAmber = Color(0xFFE59866),// Warm readable amber
        onAccent = Color(0xFF111C14),    // Deep dark ink on luminous sage buttons/badges
        onWarningAmber = Color(0xFF1F120A),
        disabledInk = Color(0xFF70695F),
        disabledBg = Color(0xFF26221F),
        focusRing = Color(0xFFB5E2C1),
        spineShadow = Color(0xFF090807), // Deep shadow for 2-column center crease in dark mode
    )

    fun paletteFor(theme: ReaderTheme, highContrast: Boolean = false): MeridianPalette {
        val base = when (theme) {
            ReaderTheme.WHITE -> WhitePalette
            ReaderTheme.SEPIA -> SepiaPalette
            ReaderTheme.SAGE -> SagePalette
            ReaderTheme.DARK -> DarkPalette
        }
        if (!highContrast) return base
        return if (base.isDark) {
            base.copy(
                ink = Color(0xFFFFFFFF),
                inkSoft = Color(0xFFEDE8DF),
                muted = Color(0xFFC8C0B4),
                border = Color(0xFF6B6259),
            )
        } else {
            base.copy(
                ink = Color(0xFF0A0A08),
                inkSoft = Color(0xFF1F241F),
                muted = Color(0xFF3E443E),
                border = Color(0xFFB5A592),
            )
        }
    }
}

fun dev.meridian.folio.core.UiFontFamilyOption.toComposeFontFamily(): FontFamily =
    when (this) {
        dev.meridian.folio.core.UiFontFamilyOption.SANS_SERIF -> FontFamily.SansSerif
        dev.meridian.folio.core.UiFontFamilyOption.SERIF -> FontFamily.Serif
        dev.meridian.folio.core.UiFontFamilyOption.MONOSPACE -> FontFamily.Monospace
    }

fun dev.meridian.folio.core.UiFontWeightOption.toComposeFontWeight(): FontWeight =
    when (this) {
        dev.meridian.folio.core.UiFontWeightOption.REGULAR -> FontWeight.Normal
        dev.meridian.folio.core.UiFontWeightOption.MEDIUM -> FontWeight.Medium
        dev.meridian.folio.core.UiFontWeightOption.SEMIBOLD -> FontWeight.SemiBold
    }

fun dev.meridian.folio.core.BookFontFamilyOption.toComposeFontFamily(): FontFamily =
    when (this) {
        dev.meridian.folio.core.BookFontFamilyOption.SERIF -> FontFamily.Serif
        dev.meridian.folio.core.BookFontFamilyOption.SANS_SERIF -> FontFamily.SansSerif
        dev.meridian.folio.core.BookFontFamilyOption.MONOSPACE -> FontFamily.Monospace
    }

fun dev.meridian.folio.core.BookTextAlignment.toComposeTextAlign(): androidx.compose.ui.text.style.TextAlign =
    when (this) {
        dev.meridian.folio.core.BookTextAlignment.JUSTIFY -> androidx.compose.ui.text.style.TextAlign.Justify
        dev.meridian.folio.core.BookTextAlignment.START -> androidx.compose.ui.text.style.TextAlign.Start
        dev.meridian.folio.core.BookTextAlignment.CENTER -> androidx.compose.ui.text.style.TextAlign.Center
    }

/**
 * ShioriFolio (栞 · Folio) Engraved Book, Bookmark Ribbon & Fountain Nib Logo Mark (viewBox 0 0 64 64):
 * - Double engraved ring medallion
 * - Open archival book with curved left and right pages
 * - Hanging swallowtail bookmark ribbon (Shiori 栞) below the spine
 * - Rising fountain nib / light star above the open book
 */
@Composable
fun MeridianCompassMark(
    inkColor: Color,
    paperColor: Color,
    size: Dp = 46.dp,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.size(size)) {
        val scale = this.size.minDimension / 64f
        fun s(v: Float) = v * scale
        val center = Offset(s(32f), s(32f))
        val strokeMain = Stroke(width = s(1.9f), cap = StrokeCap.Round, join = StrokeJoin.Round)
        val strokeFine = Stroke(width = s(1.0f), cap = StrokeCap.Round, join = StrokeJoin.Round)

        // 1. Engraved outer & inner medallion rings
        drawCircle(
            color = inkColor,
            radius = s(30f),
            center = center,
            style = Stroke(width = s(2.1f)),
        )
        drawCircle(
            color = inkColor.copy(alpha = 0.45f),
            radius = s(26f),
            center = center,
            style = Stroke(width = s(0.8f)),
        )

        // 2. Rising reading light / nib star above the open pages
        val starPath = Path().apply {
            moveTo(s(32f), s(9.5f))
            lineTo(s(33.8f), s(15.2f))
            lineTo(s(39.5f), s(17.0f))
            lineTo(s(33.8f), s(18.8f))
            lineTo(s(32f), s(24.5f))
            lineTo(s(30.2f), s(18.8f))
            lineTo(s(24.5f), s(17.0f))
            lineTo(s(30.2f), s(15.2f))
            close()
        }
        drawPath(starPath, color = inkColor, style = Fill)

        // Subtle engraved rays around the star
        drawLine(inkColor.copy(alpha = 0.65f), Offset(s(23f), s(11.5f)), Offset(s(26.5f), s(14f)), strokeWidth = s(1.1f))
        drawLine(inkColor.copy(alpha = 0.65f), Offset(s(41f), s(11.5f)), Offset(s(37.5f), s(14f)), strokeWidth = s(1.1f))

        // 3. Open Folio Book — Left Page & Right Page
        val leftPage = Path().apply {
            moveTo(s(32f), s(26f))
            cubicTo(s(27f), s(23.2f), s(19.5f), s(22.8f), s(13.5f), s(24.8f))
            lineTo(s(13.5f), s(43.5f))
            cubicTo(s(19.5f), s(41.5f), s(27f), s(42.0f), s(32f), s(45.0f))
            close()
        }
        val rightPage = Path().apply {
            moveTo(s(32f), s(26f))
            cubicTo(s(37f), s(23.2f), s(44.5f), s(22.8f), s(50.5f), s(24.8f))
            lineTo(s(50.5f), s(43.5f))
            cubicTo(s(44.5f), s(41.5f), s(37f), s(42.0f), s(32f), s(45.0f))
            close()
        }

        // Half-shaded right page for intaglio engraving depth
        drawPath(leftPage, color = paperColor, style = Fill)
        drawPath(rightPage, color = inkColor.copy(alpha = 0.14f), style = Fill)
        drawPath(leftPage, color = inkColor, style = strokeMain)
        drawPath(rightPage, color = inkColor, style = strokeMain)

        // Engraved text lines inside left & right pages
        for (row in 0..2) {
            val yOff = row * 4.4f
            drawLine(
                color = inkColor.copy(alpha = 0.72f),
                start = Offset(s(17.2f), s(29.5f + yOff)),
                end = Offset(s(28.2f), s(30.5f + yOff)),
                strokeWidth = s(1.0f),
                cap = StrokeCap.Round,
            )
            drawLine(
                color = inkColor.copy(alpha = 0.72f),
                start = Offset(s(35.8f), s(30.5f + yOff)),
                end = Offset(s(46.8f), s(29.5f + yOff)),
                strokeWidth = s(1.0f),
                cap = StrokeCap.Round,
            )
        }

        // Outer book cover under-layer
        drawLine(inkColor, Offset(s(12f), s(45.5f)), Offset(s(29.5f), s(46.8f)), strokeWidth = s(1.5f), cap = StrokeCap.Round)
        drawLine(inkColor, Offset(s(52f), s(45.5f)), Offset(s(34.5f), s(46.8f)), strokeWidth = s(1.5f), cap = StrokeCap.Round)

        // 4. Shiori (栞) Swallowtail Bookmark Ribbon hanging from the book spine
        val ribbonPath = Path().apply {
            moveTo(s(29.4f), s(44.2f))
            lineTo(s(34.6f), s(44.2f))
            lineTo(s(34.6f), s(56.5f))
            lineTo(s(32.0f), s(53.6f))
            lineTo(s(29.4f), s(56.5f))
            close()
        }
        drawPath(ribbonPath, color = inkColor, style = Fill)
        drawPath(ribbonPath, color = inkColor, style = strokeFine)

        // Center spine eyelet dot
        drawCircle(color = paperColor, radius = s(1.5f), center = Offset(s(32f), s(35f)), style = Fill)
        drawCircle(color = inkColor, radius = s(1.5f), center = Offset(s(32f), s(35f)), style = strokeFine)
    }
}

/**
 * Pen-and-ink engraved stone arched viaduct bridge with animated stagecoach & three trotting horses,
 * bringing the Meridian engraved scene into ShioriFolio.
 */
@Composable
fun MeridianViaductFooterBanner(
    palette: MeridianPalette,
    readingCompletionFraction: Float,
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "stagecoach")
    val coachCycle by transition.animateFloat(
        initialValue = -0.15f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 16000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "coachProgress",
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(palette.paper)
            .padding(top = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp),
        ) {
            val w = size.width
            val h = size.height
            val deckY = h * 0.40f
            val groundY = h * 0.94f
            val ink = palette.ink
            val softInk = palette.ink.copy(alpha = 0.42f)

            // Double parapet lines of the viaduct bridge
            drawLine(ink, Offset(0f, deckY - 3f), Offset(w, deckY - 3f), strokeWidth = 1.4f)
            drawLine(ink, Offset(0f, deckY), Offset(w, deckY), strokeWidth = 2.0f)

            // Arched stone piers across the width
            val archCount = (w / 76f).toInt().coerceAtLeast(6)
            val span = w / archCount
            for (i in 0 until archCount) {
                val x0 = i * span
                val x1 = (i + 1) * span
                val pierW = span * 0.18f
                val archLeft = x0 + pierW / 2f
                val archRight = x1 - pierW / 2f
                val archWidth = archRight - archLeft

                drawRect(
                    color = ink.copy(alpha = 0.16f),
                    topLeft = Offset(x0 - pierW / 2f, deckY),
                    size = Size(pierW, groundY - deckY),
                )
                drawLine(ink, Offset(archLeft, deckY + archWidth * 0.42f), Offset(archLeft, groundY), strokeWidth = 1.5f)
                drawLine(ink, Offset(archRight, deckY + archWidth * 0.42f), Offset(archRight, groundY), strokeWidth = 1.5f)

                drawArc(
                    color = ink,
                    startAngle = 180f,
                    sweepAngle = 180f,
                    useCenter = false,
                    topLeft = Offset(archLeft, deckY + 2f),
                    size = Size(archWidth, archWidth * 0.78f),
                    style = Stroke(width = 1.6f),
                )
                drawArc(
                    color = softInk,
                    startAngle = 180f,
                    sweepAngle = 180f,
                    useCenter = false,
                    topLeft = Offset(archLeft + 3f, deckY + 5f),
                    size = Size((archWidth - 6f).coerceAtLeast(4f), (archWidth * 0.78f - 4f).coerceAtLeast(4f)),
                    style = Stroke(width = 0.8f),
                )
            }

            // Stagecoach + 3 trotting horses crossing left -> right along the bridge deck
            val coachX = coachCycle * w
            val carriageY = deckY - 12f
            for (d in 1..5) {
                val dx = coachX - d * 9f
                val dy = deckY - 4f - (d % 2) * 2.5f
                drawCircle(
                    color = ink.copy(alpha = (0.35f - d * 0.05f).coerceAtLeast(0.05f)),
                    radius = (1.5f + d * 0.7f),
                    center = Offset(dx, dy),
                )
            }
            drawRect(
                color = ink,
                topLeft = Offset(coachX - 18f, carriageY - 5f),
                size = Size(20f, 11f),
            )
            drawRect(
                color = palette.paper,
                topLeft = Offset(coachX - 13f, carriageY - 3f),
                size = Size(5f, 4.5f),
            )
            drawRect(
                color = palette.paper,
                topLeft = Offset(coachX - 6f, carriageY - 3f),
                size = Size(5f, 4.5f),
            )
            drawCircle(color = ink, radius = 5f, center = Offset(coachX - 14f, deckY - 5f), style = Stroke(width = 1.5f))
            drawCircle(color = ink, radius = 4f, center = Offset(coachX - 1f, deckY - 4f), style = Stroke(width = 1.4f))

            for (hIdx in 0..2) {
                val hx = coachX + 10f + hIdx * 13f
                val trotBounce = sin((coachCycle * 90f + hIdx * 1.4f).toDouble()).toFloat() * 1.4f
                val hy = deckY - 9f + trotBounce
                drawRect(color = ink, topLeft = Offset(hx, hy), size = Size(9f, 4.5f))
                drawLine(ink, Offset(hx + 8f, hy + 1f), Offset(hx + 11f, hy - 3f), strokeWidth = 2f)
                drawLine(ink, Offset(hx + 1f, hy + 4f), Offset(hx - 1f + trotBounce, deckY - 1f), strokeWidth = 1.2f)
                drawLine(ink, Offset(hx + 8f, hy + 4f), Offset(hx + 10f - trotBounce, deckY - 1f), strokeWidth = 1.2f)
                drawLine(softInk, Offset(coachX + 2f, carriageY + 2f), Offset(hx + 2f, hy + 2f), strokeWidth = 0.8f)
            }

            // Reading progress milestone flag along the viaduct
            val milestoneX = (readingCompletionFraction.coerceIn(0.02f, 0.98f)) * w
            drawLine(ink, Offset(milestoneX, deckY - 18f), Offset(milestoneX, deckY), strokeWidth = 1.5f)
            drawCircle(ink, radius = 3.2f, center = Offset(milestoneX, deckY - 18f))
        }

        Text(
            text = "© 2026 ShioriFolio (栞 · Folio) · Where every page leaves its mark — Read, track, and edit from folio to folio.",
            color = palette.muted,
            fontSize = 11.sp,
            fontFamily = FontFamily.SansSerif,
            modifier = Modifier.padding(bottom = 6.dp),
        )
    }
}

/**
 * Navigation strip with 4px round dot separators and animated left-to-right 1px underline.
 */
@Composable
fun <T> MeridianDotNavBar(
    items: List<T>,
    selectedItem: T,
    labelProvider: (T) -> String,
    onSelect: (T) -> Unit,
    palette: MeridianPalette,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        items.forEachIndexed { index, item ->
            if (index > 0) {
                Spacer(modifier = Modifier.width(14.dp))
                Box(
                    modifier = Modifier
                        .size(4.dp)
                        .clip(CircleShape)
                        .background(palette.ink),
                )
                Spacer(modifier = Modifier.width(14.dp))
            }

            val isSelected = item == selectedItem
            val underlineScale by animateFloatAsState(
                targetValue = if (isSelected) 1f else 0f,
                animationSpec = tween(durationMillis = 260),
                label = "navUnderline",
            )

            Column(
                modifier = Modifier
                    .clickable { onSelect(item) }
                    .padding(vertical = 4.dp, horizontal = 2.dp),
                horizontalAlignment = Alignment.Start,
            ) {
                Text(
                    text = labelProvider(item),
                    color = if (isSelected) palette.ink else palette.inkSoft,
                    fontSize = 14.sp,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    fontFamily = FontFamily.SansSerif,
                )
                Spacer(modifier = Modifier.height(3.dp))
                Box(
                    modifier = Modifier
                        .height(1.5.dp)
                        .width((labelProvider(item).length * 7.2f * underlineScale).dp)
                        .background(palette.ink),
                )
            }
        }
    }
}

@Composable
fun MeridianWordmarkTitle(
    palette: MeridianPalette,
    subtitle: String = "栞 Shiori (Marcapáginas) + Folio (Hoja) — Read, annotate, and edit from leaf to leaf.",
    compact: Boolean = false,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        MeridianCompassMark(
            inkColor = palette.ink,
            paperColor = palette.paper,
            size = if (compact) 38.dp else 46.dp,
        )
        Column {
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "shiorifolio",
                    color = palette.ink,
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = if (compact) 26.sp else 32.sp,
                    letterSpacing = (-0.5).sp,
                )
                Text(
                    text = "栞 · LECTOR & SCRIPTORIUM",
                    color = palette.muted,
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight.Medium,
                    fontSize = 10.sp,
                    letterSpacing = 1.4.sp,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }
            if (!compact) {
                Text(
                    text = subtitle,
                    color = palette.inkSoft,
                    fontFamily = FontFamily.SansSerif,
                    fontStyle = FontStyle.Normal,
                    fontSize = 12.5.sp,
                )
            }
        }
    }
}

/**
 * Draped Swallowtail Bookmark Ribbon (Shiori 栞) from the Stitch "Serene Vintage Reader"
 * Desktop Book Spread & Mobile HUD (#984629 / #B46B38).
 */
@Composable
fun SwallowtailBookmarkRibbon(
    isActive: Boolean,
    ribbonColor: Color = Color(0xFF984629),
    width: Dp = 32.dp,
    height: Dp = 64.dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Canvas(
        modifier = modifier
            .size(width = width, height = height)
            .clickable { onClick() },
    ) {
        val w = size.width
        val h = size.height
        val path = Path().apply {
            moveTo(0f, 0f)
            lineTo(w, 0f)
            lineTo(w, h)
            lineTo(w * 0.5f, h * 0.78f)
            lineTo(0f, h)
            close()
        }
        drawPath(
            path = path,
            color = if (isActive) ribbonColor else ribbonColor.copy(alpha = 0.55f),
            style = Fill,
        )
        // Subtle stitched inner border line
        val inset = w * 0.14f
        val innerPath = Path().apply {
            moveTo(inset, 0f)
            lineTo(inset, h * 0.84f)
            lineTo(w * 0.5f, h * 0.68f)
            lineTo(w - inset, h * 0.84f)
            lineTo(w - inset, 0f)
        }
        drawPath(
            path = innerPath,
            color = Color.White.copy(alpha = 0.32f),
            style = Stroke(width = 1.1f),
        )
        // Small star / bookmark emblem in the upper center of the ribbon
        drawCircle(
            color = Color.White.copy(alpha = 0.88f),
            radius = w * 0.11f,
            center = Offset(w * 0.5f, h * 0.34f),
        )
    }
}

/**
 * Keyboard-accessible clickable modifier with a visible 2.dp focus ring (`palette.focusRing`)
 * when traversed via `Tab` / `Shift + Tab` on Windows & Linux.
 */
@Composable
fun Modifier.shioriFocusableClickable(
    palette: MeridianPalette,
    shape: Shape = RoundedCornerShape(6.dp),
    enabled: Boolean = true,
    onClick: () -> Unit,
): Modifier {
    var isFocused by remember { mutableStateOf(false) }
    return this
        .onFocusChanged { isFocused = it.isFocused }
        .then(
            if (isFocused && enabled) {
                Modifier.border(2.dp, palette.focusRing, shape)
            } else {
                Modifier
            },
        )
        .clickable(enabled = enabled, onClick = onClick)
}

fun canonicalKeyName(key: Key): String? = when (key) {
    Key.PageDown -> "PageDown"
    Key.PageUp -> "PageUp"
    Key.DirectionLeft -> "DirectionLeft"
    Key.DirectionRight -> "DirectionRight"
    Key.DirectionUp -> "DirectionUp"
    Key.DirectionDown -> "DirectionDown"
    Key.Tab -> "Tab"
    Key.Spacebar -> "Space"
    Key.MoveHome -> "Home"
    Key.MoveEnd -> "End"
    Key.F1 -> "F1"
    Key.F2 -> "F2"
    Key.F3 -> "F3"
    Key.F4 -> "F4"
    Key.F5 -> "F5"
    Key.F6 -> "F6"
    Key.F7 -> "F7"
    Key.F8 -> "F8"
    Key.F9 -> "F9"
    Key.F10 -> "F10"
    Key.F11 -> "F11"
    Key.F12 -> "F12"
    Key.A -> "A"
    Key.B -> "B"
    Key.C -> "C"
    Key.D -> "D"
    Key.E -> "E"
    Key.F -> "F"
    Key.G -> "G"
    Key.H -> "H"
    Key.I -> "I"
    Key.J -> "J"
    Key.K -> "K"
    Key.L -> "L"
    Key.M -> "M"
    Key.N -> "N"
    Key.O -> "O"
    Key.P -> "P"
    Key.Q -> "Q"
    Key.R -> "R"
    Key.S -> "S"
    Key.T -> "T"
    Key.U -> "U"
    Key.V -> "V"
    Key.W -> "W"
    Key.X -> "X"
    Key.Y -> "Y"
    Key.Z -> "Z"
    Key.Zero -> "0"
    Key.One -> "1"
    Key.Two -> "2"
    Key.Three -> "3"
    Key.Four -> "4"
    Key.Five -> "5"
    Key.Six -> "6"
    Key.Seven -> "7"
    Key.Eight -> "8"
    Key.Nine -> "9"
    else -> null
}

fun KeyEvent.matches(spec: KeyBindingSpec): Boolean {
    val name = canonicalKeyName(key) ?: return false
    return name.equals(spec.keyName, ignoreCase = true) &&
        isCtrlPressed == spec.ctrl &&
        isShiftPressed == spec.shift &&
        isAltPressed == spec.alt
}

fun KeyEvent.toKeyBindingSpecOrNull(): KeyBindingSpec? {
    val name = canonicalKeyName(key) ?: return null
    return KeyBindingSpec(
        keyName = name,
        ctrl = isCtrlPressed,
        shift = isShiftPressed,
        alt = isAltPressed,
    )
}


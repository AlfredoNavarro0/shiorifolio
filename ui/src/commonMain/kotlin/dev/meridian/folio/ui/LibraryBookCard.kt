package dev.meridian.folio.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.meridian.folio.core.BookDocument
import dev.meridian.folio.core.BookReadingTelemetry
import dev.meridian.folio.core.DocumentFormat
import dev.meridian.folio.core.LibraryStatus
import dev.meridian.folio.core.PageReminder
import dev.meridian.folio.core.ReadingPredictionEngine
import dev.meridian.folio.core.libraryProgress
import dev.meridian.folio.ui.theme.ClayCard
import dev.meridian.folio.ui.theme.ClayChromeAction
import dev.meridian.folio.ui.theme.ClayPalette
import dev.meridian.folio.ui.theme.ClayProgressBar
import dev.meridian.folio.ui.theme.ClayRadius
import dev.meridian.folio.ui.theme.ClaySpacing
import dev.meridian.folio.ui.theme.LocalClayTypography
import kotlin.math.roundToInt

/** Poster ratio of the card cover: height = width × 1.5 (the Stitch 3:4-ish poster block). */
private const val BookCoverRatio = 1.5f

/**
 * Height of the card chrome below the poster: title (2 lines), author, the optional pace line and
 * the status band with its progress track. The card height is derived from the poster, so this
 * value only has to leave room for the text block.
 */
private val BookCardChromeHeight = 176.dp

/**
 * Cover-led shelf card: poster with the format badge and the page count, title/author, the reading
 * status band (`Leyendo 64%` / `Sin comenzar` / `Completado 100%`) with a real progress track and
 * the action menu.
 *
 * Kept contract tags: `library_book_<id>`, `library_open_<id>` (poster, opens or selects),
 * `library_select_<id>` (checkbox), `library_menu_<id>` -> `library_menu_select_<id>`,
 * `library_export_<id>`, `library_edit_metadata_<id>`, `library_reload_<id>`,
 * `library_title_<id>`, `library_missing_<id>`. New (additive): `library_progress_<id>`.
 */
@Composable
internal fun LibraryBookCard(
    book: BookDocument,
    telemetry: BookReadingTelemetry?,
    palette: ClayPalette,
    detailed: Boolean,
    selected: Boolean,
    selectionMode: Boolean,
    reminder: PageReminder?,
    onToggleSelection: () -> Unit,
    onOpen: () -> Unit,
    onExport: () -> Unit,
    onReload: () -> Unit,
    onEditMetadata: () -> Unit,
) {
    val typography = LocalClayTypography.current
    val interactions = remember { MutableInteractionSource() }
    val hovered by interactions.collectIsHoveredAsState()
    val focused by interactions.collectIsFocusedAsState()
    var menuExpanded by remember { mutableStateOf(false) }
    val prediction = remember(book, telemetry) { ReadingPredictionEngine.computePrediction(book, telemetry) }
    val percent = (book.libraryProgress() * 100).roundToInt()
    val coverShape = RoundedCornerShape(ClayRadius.base)
    val lift by animateFloatAsState(if (hovered || focused) -4f else 0f, tween(220))

    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val coverHeight = (maxWidth - ClaySpacing.md * 2).coerceAtLeast(ClaySpacing.xl) * BookCoverRatio
        ClayCard(
            Modifier.fillMaxWidth()
                .height(coverHeight + BookCardChromeHeight)
                .graphicsLayer { translationY = lift * density }
                .testTag("library_book_${book.id}"),
        ) {
            Column(
                Modifier.fillMaxSize().padding(ClaySpacing.md),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(Modifier.fillMaxWidth()) {
                    Box(
                        Modifier.fillMaxWidth().height(coverHeight).clip(coverShape)
                            .border(
                                width = if (selected) 2.dp else 1.dp,
                                color = when {
                                    focused -> palette.focusRing
                                    selected -> palette.primary
                                    else -> palette.outlineVariant.copy(alpha = 0.65f)
                                },
                                shape = coverShape,
                            )
                            .clickable(
                                interactionSource = interactions,
                                indication = null,
                                onClickLabel = if (selectionMode) "Seleccionar ${book.title}" else "Leer ${book.title}",
                            ) { if (selectionMode) onToggleSelection() else onOpen() }
                            .testTag("library_open_${book.id}"),
                    ) {
                        LibraryCover(book, palette)
                        if (selectionMode || hovered) {
                            Checkbox(
                                selected, { onToggleSelection() },
                                modifier = Modifier.align(Alignment.TopStart).padding(4.dp)
                                    .clip(RoundedCornerShape(ClayRadius.sm))
                                    .background(palette.surfaceContainerLowest.copy(alpha = 0.94f))
                                    .testTag("library_select_${book.id}")
                                    .semantics { contentDescription = "Seleccionar ${book.title}" },
                            )
                        } else {
                            LibraryFormatBadge(book, palette, Modifier.align(Alignment.TopStart).padding(ClaySpacing.xs))
                        }
                        LibraryPagePill(book, palette, Modifier.align(Alignment.TopEnd).padding(ClaySpacing.xs))
                        if (hovered && !selectionMode) Box(
                            Modifier.fillMaxSize().background(palette.spineShadow.copy(alpha = 0.25f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Box(
                                Modifier.clip(RoundedCornerShape(percent = 50)).background(palette.surfaceContainerLowest)
                                    .padding(horizontal = ClaySpacing.md, vertical = ClaySpacing.sm),
                            ) {
                                Text(
                                    if (percent > 0) "Continuar lectura" else "Abrir libro",
                                    color = palette.onSurface,
                                    style = typography.labelMd,
                                )
                            }
                        }
                    }
                    Row(
                        Modifier.fillMaxWidth().padding(top = ClaySpacing.xs),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(ClaySpacing.xs),
                    ) {
                        Text(
                            book.title,
                            color = if (hovered) palette.primary else palette.onSurface,
                            style = typography.titleMd,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                                .clickable { if (selectionMode) onToggleSelection() else onOpen() }
                                .testTag("library_title_${book.id}"),
                        )
                        Box {
                            ClayChromeAction(
                                "⋯",
                                palette,
                                "library_menu_${book.id}",
                                description = "Opciones de ${book.title}",
                            ) { menuExpanded = true }
                            DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                                DropdownMenuItem(text = { Text("Abrir libro") }, onClick = { menuExpanded = false; onOpen() })
                                DropdownMenuItem(
                                    text = { Text(if (selected) "Quitar de la selección" else "Seleccionar libro") },
                                    onClick = { menuExpanded = false; onToggleSelection() },
                                    modifier = Modifier.testTag("library_menu_select_${book.id}"),
                                )
                                DropdownMenuItem(
                                    text = { Text("Exportar") },
                                    onClick = { menuExpanded = false; onExport() },
                                    modifier = Modifier.testTag("library_export_${book.id}"),
                                )
                                DropdownMenuItem(
                                    text = { Text("Editar metadatos") },
                                    onClick = { menuExpanded = false; onEditMetadata() },
                                    modifier = Modifier.testTag("library_edit_metadata_${book.id}"),
                                )
                                if (book.filePath != null) DropdownMenuItem(
                                    text = { Text("Recargar desde el archivo") },
                                    enabled = !book.isMissingOnDisk,
                                    onClick = { menuExpanded = false; onReload() },
                                    modifier = Modifier.testTag("library_reload_${book.id}"),
                                )
                                if (reminder != null) {
                                    HorizontalDivider()
                                    Text(
                                        "Recordatorio · pág. ${reminder.pageNumber}\n${reminder.note}",
                                        color = palette.onSurfaceVariant,
                                        style = typography.bodySm,
                                        modifier = Modifier.widthIn(max = 260.dp).padding(ClaySpacing.md),
                                    )
                                }
                            }
                        }
                    }
                    Text(
                        book.author,
                        color = palette.onSurfaceVariant,
                        style = typography.bodySm,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (detailed) Text(
                        "${prediction.formattedRemainingTime()} restantes · " +
                            if ((telemetry?.totalActiveSeconds ?: 0.0) > 0) "Según tu ritmo" else "Estimado inicial",
                        color = palette.textMuted,
                        style = typography.bodySm,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (book.isMissingOnDisk) Text(
                        "No disponible en disco",
                        color = palette.warningAmber,
                        style = typography.bodySm,
                        modifier = Modifier.testTag("library_missing_${book.id}"),
                    )
                }
                LibraryCardProgressFooter(book, palette, percent)
            }
        }
    }
}

/**
 * Status band and, when there is real progress, the page hint and the progress track of the card.
 * The track (and its `library_progress_<id>` tag) only exists for READING/FINISHED books or once
 * the book has actually moved, so an untouched PENDING card shows neither a bar nor a stray `0%`.
 */
@Composable
private fun LibraryCardProgressFooter(book: BookDocument, palette: ClayPalette, percent: Int) {
    val status = book.libraryStatus
    val hasProgress = status == LibraryStatus.READING || status == LibraryStatus.FINISHED || book.libraryProgress() > 0f
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LibraryStatusBand(book, palette, percent)
            // The status band already carries the percentage for the non-reading states.
            if (status == LibraryStatus.READING) Text(
                "Pág ${book.currentPage}",
                color = palette.textMuted,
                style = LocalClayTypography.current.labelSm,
                maxLines = 1,
            )
        }
        if (hasProgress) ClayProgressBar(book.libraryProgress(), tag = "library_progress_${book.id}")
    }
}

/** Reading status band with the labels of the reference screen, fed by the real status/progress. */
@Composable
private fun LibraryStatusBand(book: BookDocument, palette: ClayPalette, percent: Int) {
    val container = when (book.libraryStatus) {
        LibraryStatus.PENDING -> palette.surfaceSecondary
        LibraryStatus.READING -> palette.successMint.copy(alpha = 0.15f)
        LibraryStatus.FINISHED -> palette.primaryContainer
        LibraryStatus.ABANDONED -> palette.warningAmber.copy(alpha = 0.18f)
    }
    val content = when (book.libraryStatus) {
        LibraryStatus.PENDING -> palette.textMuted
        LibraryStatus.READING -> palette.successMint
        LibraryStatus.FINISHED -> palette.onPrimaryContainer
        LibraryStatus.ABANDONED -> palette.onWarningAmber
    }
    val label = when (book.libraryStatus) {
        LibraryStatus.PENDING -> "Sin comenzar"
        LibraryStatus.FINISHED -> "Completado 100%"
        LibraryStatus.READING, LibraryStatus.ABANDONED -> "${book.libraryStatus.label} $percent%"
    }
    Box(Modifier.clip(RoundedCornerShape(percent = 50)).background(container).padding(horizontal = ClaySpacing.xs, vertical = 3.dp)) {
        Text(label, color = content, style = LocalClayTypography.current.labelSm, maxLines = 1)
    }
}

/** Format pill of the poster corner: real `DocumentFormat` name and accent dot. */
@Composable
private fun LibraryFormatBadge(book: BookDocument, palette: ClayPalette, modifier: Modifier = Modifier) {
    val dot = if (book.format == DocumentFormat.PDF) palette.secondary else palette.tertiary
    Box(modifier.clip(RoundedCornerShape(percent = 50)).background(palette.cardSurface).padding(horizontal = ClaySpacing.xs, vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(dot))
            Text(book.format.name, color = palette.onSurface, style = LocalClayTypography.current.labelSm, maxLines = 1)
        }
    }
}

/** Page-count pill of the poster corner; EPUB page counts are estimates. */
@Composable
private fun LibraryPagePill(book: BookDocument, palette: ClayPalette, modifier: Modifier = Modifier) {
    Box(
        modifier.clip(RoundedCornerShape(percent = 50)).background(palette.spineShadow.copy(alpha = 0.75f))
            .padding(horizontal = ClaySpacing.xs, vertical = 3.dp),
    ) {
        Text(
            if (book.format == DocumentFormat.EPUB) "${book.totalPages} págs. est." else "${book.totalPages} págs",
            color = palette.surfaceContainerLowest,
            style = LocalClayTypography.current.labelSm,
            maxLines = 1,
        )
    }
}

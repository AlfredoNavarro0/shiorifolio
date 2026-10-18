package dev.meridian.folio.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.meridian.folio.core.*
import kotlin.math.roundToInt

/** Cover-led shelf: actions live in the cover's menu, leaving the artwork and title visible. */
@Composable
internal fun LibraryBookCard(
    book: BookDocument,
    telemetry: BookReadingTelemetry?,
    palette: MeridianPalette,
    detailed: Boolean,
    selected: Boolean,
    selectionMode: Boolean,
    reminder: PageReminder?,
    onToggleSelection: () -> Unit,
    onOpen: () -> Unit,
    onExport: () -> Unit,
    onReload: () -> Unit,
) {
    val interactions = remember { MutableInteractionSource() }
    val hovered by interactions.collectIsHoveredAsState()
    val focused by interactions.collectIsFocusedAsState()
    var menuExpanded by remember { mutableStateOf(false) }
    val prediction = remember(book, telemetry) { ReadingPredictionEngine.computePrediction(book, telemetry) }
    val percent = (book.libraryProgress() * 100).roundToInt()
    val coverShape = RoundedCornerShape(16.dp)
    val lift by animateFloatAsState(if (hovered || focused) -4f else 0f, tween(220))
    val overlayInk = if (palette.isDark) palette.ink else palette.pageSurface

    Column(Modifier.fillMaxWidth().testTag("library_book_${book.id}"), verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Box(Modifier.graphicsLayer { translationY = lift * density }
            .shadow(if (hovered) 8.dp else 3.dp, coverShape, ambientColor = palette.spineShadow.copy(alpha = 0.12f), spotColor = palette.spineShadow.copy(alpha = 0.12f))) {
            Box(Modifier.fillMaxWidth().aspectRatio(2f / 3f).clip(coverShape)
                .border(if (selected || hovered || focused) 2.dp else 1.dp,
                    if (focused) palette.focusRing else if (selected || hovered) palette.accentGreen else palette.border.copy(alpha = 0.65f), coverShape)
                .clickable(interactionSource = interactions, indication = null,
                    onClickLabel = if (selectionMode) "Seleccionar ${book.title}" else "Leer ${book.title}") {
                    if (selectionMode) onToggleSelection() else onOpen()
                }.testTag("library_open_${book.id}")) {
                LibraryCover(book, palette)
                if (detailed || percent > 0) Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(palette.spineShadow.copy(alpha = 0f), palette.spineShadow.copy(alpha = 0.9f))))
                    .padding(start = 10.dp, end = 10.dp, top = 28.dp, bottom = 10.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Pág. ${book.currentPage} / ${book.totalPages}", color = overlayInk, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                        Text(if (book.libraryStatus == LibraryStatus.FINISHED) "Leído" else "$percent%", color = overlayInk,
                            fontSize = 10.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                    }
                    LinearProgressIndicator(progress = { book.libraryProgress() }, color = palette.warningAmber,
                        trackColor = overlayInk.copy(alpha = 0.25f), modifier = Modifier.fillMaxWidth().height(3.dp))
                }
                if ((hovered || focused) && !selectionMode) Box(Modifier.fillMaxSize().background(palette.spineShadow.copy(alpha = 0.25f)), contentAlignment = Alignment.Center) {
                    Surface(color = palette.pageSurface, contentColor = palette.ink, shape = RoundedCornerShape(8.dp)) {
                        Text(if (percent > 0) "Continuar lectura" else "Abrir libro", fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp))
                    }
                }
            }
            if (selectionMode || hovered) {
                Checkbox(selected, { onToggleSelection() }, modifier = Modifier.align(Alignment.TopStart).padding(4.dp)
                    .clip(RoundedCornerShape(12.dp)).background(palette.paper.copy(alpha = 0.94f))
                    .testTag("library_select_${book.id}").semantics { contentDescription = "Seleccionar ${book.title}" })
            } else {
                Text(book.format.name,
                    color = overlayInk, fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace,
                    modifier = Modifier.align(Alignment.TopStart).padding(8.dp).clip(RoundedCornerShape(12.dp))
                        .background(palette.spineShadow.copy(alpha = 0.85f)).padding(horizontal = 9.dp, vertical = 5.dp))
            }
            Box(Modifier.align(Alignment.TopEnd).padding(4.dp)) {
                IconButton(onClick = { menuExpanded = true }, modifier = Modifier.size(48.dp)
                    .testTag("library_menu_${book.id}").semantics { contentDescription = "Opciones de ${book.title}" }) {
                    Text("⋯", color = palette.ink, fontSize = 22.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(palette.paper.copy(alpha = 0.88f))
                            .padding(horizontal = 9.dp, vertical = 1.dp))
                }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    DropdownMenuItem(text = { Text("Abrir libro") }, onClick = { menuExpanded = false; onOpen() })
                    DropdownMenuItem(text = { Text(if (selected) "Quitar de la selección" else "Seleccionar libro") },
                        onClick = { menuExpanded = false; onToggleSelection() }, modifier = Modifier.testTag("library_menu_select_${book.id}"))
                    DropdownMenuItem(text = { Text("Exportar") }, onClick = { menuExpanded = false; onExport() }, modifier = Modifier.testTag("library_export_${book.id}"))
                    if (book.filePath != null) DropdownMenuItem(text = { Text("Recargar desde el archivo") },
                        enabled = !book.isMissingOnDisk, onClick = { menuExpanded = false; onReload() }, modifier = Modifier.testTag("library_reload_${book.id}"))
                    if (reminder != null) {
                        HorizontalDivider()
                        Text("Recordatorio · pág. ${reminder.pageNumber}\n${reminder.note}", color = palette.inkSoft,
                            fontSize = 12.sp, modifier = Modifier.widthIn(max = 260.dp).padding(16.dp))
                    }
                }
            }
        }
        Text(book.title, color = if (hovered) palette.accentGreen else palette.ink, fontFamily = FontFamily.SansSerif, fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold, lineHeight = 21.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.clickable { if (selectionMode) onToggleSelection() else onOpen() }.testTag("library_title_${book.id}"))
        Text(book.author, color = palette.inkSoft, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text("${book.format.name} · ${book.totalPages} " + if (book.format == DocumentFormat.EPUB) "págs. estimadas" else "págs.",
            color = palette.muted, fontFamily = FontFamily.SansSerif, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (detailed) {
            Text("${book.libraryStatus.label} · ${prediction.formattedRemainingTime()} restantes", color = palette.inkSoft,
                fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(if ((telemetry?.totalActiveSeconds ?: 0.0) > 0) "Según tu ritmo" else "Estimado inicial",
                color = palette.muted, fontSize = 11.sp)
        }
        if (book.isMissingOnDisk) Text("No disponible en disco", color = palette.warningAmber,
            fontSize = 12.sp, modifier = Modifier.testTag("library_missing_${book.id}"))
    }
}

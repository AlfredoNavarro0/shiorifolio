package dev.meridian.folio.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.meridian.folio.core.*
import dev.meridian.folio.ui.theme.*
import kotlinx.datetime.LocalDate

internal fun LibraryFilter.activeFacetCount(): Int = listOf(
    statuses.isNotEmpty(), formats.isNotEmpty(), collectionId != null,
    minProgress != 0 || maxProgress != 100, minPages != 0 || maxPages != Int.MAX_VALUE,
    addedAfterMs != null || addedBeforeMs != null, completedAfterMs != null || completedBeforeMs != null,
).count { it }

/** A bounded, dismissible filter editor; the shelf stays in place behind it. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun LibraryFilters(
    repository: FolioRepository,
    palette: ClayPalette,
    filter: LibraryFilter,
    onChange: (LibraryFilter) -> Unit,
    onClose: () -> Unit,
) {
    val state by repository.state.collectAsState()
    var draft by remember { mutableStateOf(filter) }
    var advanced by remember { mutableStateOf(false) }
    var collectionName by remember { mutableStateOf("") }
    var invalidDates by remember { mutableStateOf(emptySet<String>()) }
    var resetKey by remember { mutableIntStateOf(0) }
    val validRange = draft.minProgress <= draft.maxProgress && draft.minPages <= draft.maxPages &&
        (draft.addedAfterMs == null || draft.addedBeforeMs == null || draft.addedAfterMs!! <= draft.addedBeforeMs!!) &&
        (draft.completedAfterMs == null || draft.completedBeforeMs == null || draft.completedAfterMs!! <= draft.completedBeforeMs!!)
    val typography = LocalClayTypography.current
    AlertDialog(
        onDismissRequest = onClose,
        modifier = Modifier.widthIn(max = 600.dp).testTag("library_filter_panel"),
        shape = RoundedCornerShape(ClayRadius.md), containerColor = palette.cardSurface,
        title = { Text("Filtrar biblioteca", color = palette.onSurface, style = typography.titleMd) },
        text = {
          key(resetKey) {
            Column(
                Modifier.fillMaxWidth().heightIn(max = 400.dp).verticalScroll(rememberScrollState())
                    .testTag("library_filter_scroll"),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                FilterGroupLabel("Estado de lectura", palette)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    LibraryStatus.entries.forEach { status ->
                        ClayChip(status.label, status in draft.statuses, {
                            draft = draft.copy(statuses = if (status in draft.statuses) draft.statuses - status else draft.statuses + status)
                        }, tag = "library_filter_status_${status.name}")
                    }
                }
                FilterGroupLabel("Formato", palette)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DocumentFormat.entries.forEach { format ->
                        ClayChip(format.name, format in draft.formats, {
                            draft = draft.copy(formats = if (format in draft.formats) draft.formats - format else draft.formats + format)
                        }, tag = "library_filter_format_${format.name}")
                    }
                }
                if (state.collections.isNotEmpty()) {
                    FilterGroupLabel("Colección", palette)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        ClayChip("Todas", draft.collectionId == null, { draft = draft.copy(collectionId = null) }, tag = "library_collection_all")
                        state.collections.forEach { collection ->
                            ClayChip(collection.name, draft.collectionId == collection.id,
                                { draft = draft.copy(collectionId = collection.id) }, tag = "library_collection_${collection.id}")
                        }
                    }
                }
                ClayHeaderAction(if (advanced) "−  Menos opciones" else "+  Más opciones", palette,
                    "library_filter_advanced", advanced) { advanced = !advanced }
                AnimatedVisibility(advanced) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Desplázate para ver rangos, fechas y colecciones.",
                            color = palette.textMuted, style = typography.bodySm)
                        FilterGroupLabel("Progreso de lectura", palette)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            LibraryNumberField("Mínimo %", draft.minProgress, "library_min_progress", palette, Modifier.weight(1f)) {
                                draft = draft.copy(minProgress = it.coerceIn(0, 100))
                            }
                            LibraryNumberField("Máximo %", draft.maxProgress, "library_max_progress", palette, Modifier.weight(1f)) {
                                draft = draft.copy(maxProgress = it.coerceIn(0, 100))
                            }
                        }
                        FilterGroupLabel("Número de páginas", palette)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            LibraryNumberField("Desde", draft.minPages, "library_min_pages", palette, Modifier.weight(1f)) {
                                draft = draft.copy(minPages = it)
                            }
                            LibraryNumberField("Hasta", draft.maxPages, "library_max_pages", palette, Modifier.weight(1f)) {
                                draft = draft.copy(maxPages = it)
                            }
                        }
                        FilterGroupLabel("Fecha de incorporación", palette)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            LibraryDateField("Desde", "library_added_from", draft.addedAfterMs, palette, Modifier.weight(1f),
                                onValidity = { invalidDates = if (it) invalidDates - "added_from" else invalidDates + "added_from" }) {
                                draft = draft.copy(addedAfterMs = it)
                            }
                            LibraryDateField("Hasta", "library_added_to", draft.addedBeforeMs, palette, Modifier.weight(1f), true,
                                onValidity = { invalidDates = if (it) invalidDates - "added_to" else invalidDates + "added_to" }) {
                                draft = draft.copy(addedBeforeMs = it)
                            }
                        }
                        FilterGroupLabel("Fecha de finalización", palette)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            LibraryDateField("Desde", "library_finished_from", draft.completedAfterMs, palette, Modifier.weight(1f),
                                onValidity = { invalidDates = if (it) invalidDates - "finished_from" else invalidDates + "finished_from" }) {
                                draft = draft.copy(completedAfterMs = it)
                            }
                            LibraryDateField("Hasta", "library_finished_to", draft.completedBeforeMs, palette, Modifier.weight(1f), true,
                                onValidity = { invalidDates = if (it) invalidDates - "finished_to" else invalidDates + "finished_to" }) {
                                draft = draft.copy(completedBeforeMs = it)
                            }
                        }
                        FilterGroupLabel("Gestionar colecciones", palette)
                        state.collections.forEach { collection ->
                            ClayHeaderAction("Eliminar ${collection.name}", palette, "library_remove_collection_${collection.id}") {
                                repository.library.removeCollection(collection.id)
                                if (draft.collectionId == collection.id) draft = draft.copy(collectionId = null)
                            }
                        }
                        OutlinedTextField(collectionName, { collectionName = it }, singleLine = true,
                            label = { Text("Nueva colección") }, colors = clayTextFieldColors(palette),
                            modifier = Modifier.fillMaxWidth().shioriTextFieldFocusGuard().testTag("library_collection_name"))
                        ClaySecondaryButton("Crear colección", {
                            repository.library.createCollection(collectionName); collectionName = ""
                        }, tag = "library_create_collection", enabled = collectionName.isNotBlank())
                    }
                }
                if (!validRange) Text("El valor inicial no puede superar al final.", color = palette.error, style = typography.bodySm)
            }
          }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ClaySecondaryButton("Limpiar", {
                    draft = LibraryFilter(search = filter.search); invalidDates = emptySet(); resetKey++
                }, tag = "library_reset_filters")
                ClaySecondaryButton("Cancelar", onClose, tag = "library_cancel_filters")
            }
        },
        confirmButton = {
            ClayPrimaryButton("Aplicar", { onChange(draft); onClose() }, tag = "library_apply_filters",
                enabled = validRange && invalidDates.isEmpty())
        },
    )
}

@Composable
private fun FilterGroupLabel(text: String, palette: ClayPalette) {
    Text(text, color = palette.onSurfaceVariant, style = LocalClayTypography.current.labelMd)
}

@Composable
private fun LibraryNumberField(label: String, value: Int, tag: String, palette: ClayPalette,
    modifier: Modifier, onChange: (Int) -> Unit) {
    var text by remember(value) { mutableStateOf(if (value == Int.MAX_VALUE) "" else value.toString()) }
    OutlinedTextField(text, { input ->
        if (input.all { it.isDigit() }) {
            text = input
            onChange(input.toIntOrNull() ?: if (tag.contains("max_pages")) Int.MAX_VALUE else 0)
        }
    }, label = { Text(label) }, singleLine = true, colors = clayTextFieldColors(palette),
        modifier = modifier.shioriTextFieldFocusGuard().testTag(tag))
}

@Composable
private fun LibraryDateField(label: String, tag: String, value: Long?, palette: ClayPalette,
    modifier: Modifier, endOfDay: Boolean = false, onValidity: (Boolean) -> Unit, onChange: (Long?) -> Unit) {
    var text by remember(value) {
        mutableStateOf(value?.let { LocalDate.fromEpochDays((it / 86_400_000L).toInt()).toString() } ?: "")
    }
    val parsed = runCatching { LocalDate.parse(text).toEpochDays() * 86_400_000L }.getOrNull()
    OutlinedTextField(text, { input ->
        text = input
        val date = runCatching { LocalDate.parse(input).toEpochDays() * 86_400_000L }.getOrNull()
        onValidity(input.isBlank() || date != null)
        if (input.isBlank() || date != null) onChange(date?.let { it + if (endOfDay) 86_399_999L else 0L })
    }, label = { Text(label) }, placeholder = { Text("AAAA-MM-DD") }, singleLine = true,
        isError = text.isNotEmpty() && parsed == null, colors = clayTextFieldColors(palette),
        modifier = modifier.shioriTextFieldFocusGuard().testTag(tag))
}

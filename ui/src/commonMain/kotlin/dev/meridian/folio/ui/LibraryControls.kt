package dev.meridian.folio.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.meridian.folio.core.*
import dev.meridian.folio.ui.theme.*

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun LibraryControls(
    state: FolioPersistedState,
    palette: ClayPalette,
    filter: LibraryFilter,
    resultCount: Int,
    onFilter: (LibraryFilter) -> Unit,
    onPreferences: (LibraryPreferences) -> Unit,
    onTools: () -> Unit,
    onFolders: () -> Unit,
    onFilters: () -> Unit,
    onSelectAll: () -> Unit,
) {
    val prefs = state.libraryPreferences
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f).widthIn(min = 230.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Tu biblioteca", color = palette.onSurface, fontFamily = FontFamily.SansSerif, fontSize = 32.sp, fontWeight = FontWeight.Bold, letterSpacing = (-1).sp)
                    Surface(color = palette.primary.copy(alpha = 0.08f), shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, palette.primary.copy(alpha = 0.2f))) {
                        Text("$resultCount / ${state.documents.size} volúmenes", color = palette.primary,
                            fontFamily = FontFamily.SansSerif, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp).testTag("library_count"))
                    }
                }
                Text("Tu próxima lectura empieza aquí.", color = palette.onSurfaceVariant, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
                if (state.documents.isEmpty()) Text("shiorifolio", color = palette.onSurface, fontFamily = FontFamily.Serif)
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onTools, shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(
                    containerColor = palette.primary, contentColor = palette.onPrimary), modifier = Modifier.testTag("library_tools")) {
                    Text("+  Importar libro", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
                LibraryToolbarButton("Filtros y orden", "library_filters", palette, onClick = onFilters)
                LibraryToolbarButton("Seleccionar", "library_select_all", palette, onClick = onSelectAll)
            }
        }
        Surface(color = palette.canvas, shape = RoundedCornerShape(16.dp)) {
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val search: @Composable (Modifier) -> Unit = { modifier ->
                    OutlinedTextField(filter.search, { onFilter(filter.copy(search = it)) },
                        placeholder = { Text("Buscar por título o autor…", fontSize = 12.sp) }, singleLine = true,
                        leadingIcon = { Text("⌕", color = palette.onSurfaceVariant, fontSize = 24.sp) },
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(unfocusedContainerColor = palette.surfaceContainerLowest,
                            focusedContainerColor = palette.surfaceContainerLowest, unfocusedBorderColor = palette.outlineVariant, focusedBorderColor = palette.primary),
                        modifier = modifier.shioriTextFieldFocusGuard().testTag("library_search"))
                }
                val views: @Composable () -> Unit = {
                    Row(Modifier.horizontalScroll(rememberScrollState()).background(palette.surfaceSecondary, RoundedCornerShape(12.dp)).padding(3.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        LibraryLayout.entries.forEach { layout ->
                            LibraryToolbarButton(if (layout == LibraryLayout.CARDS) "Pósteres" else "Lista", "library_layout_${layout.name}", palette, prefs.layout == layout) {
                                onPreferences(prefs.copy(layout = layout))
                            }
                        }
                        LibraryDensity.entries.forEach { density ->
                            LibraryToolbarButton(density.label, "library_density_${density.name}", palette, prefs.density == density) {
                                onPreferences(prefs.copy(density = density))
                            }
                        }
                        LibraryToolbarButton("Carpetas", "library_folders", palette, onClick = onFolders)
                    }
                }
                if (maxWidth >= 900.dp) Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    search(Modifier.weight(1f))
                    views()
                } else Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    search(Modifier.fillMaxWidth())
                    views()
                }
            }
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LibraryToolbarButton("Todos (${state.documents.size})", "library_quick_all", palette, filter.statuses.isEmpty() && filter.formats.isEmpty() && filter.collectionId == null) {
                onFilter(filter.copy(statuses = emptySet(), formats = emptySet(), collectionId = null))
            }
            LibraryToolbarButton("Leyendo (${state.documents.count { it.libraryStatus == LibraryStatus.READING }})", "library_quick_reading", palette, LibraryStatus.READING in filter.statuses) {
                onFilter(filter.copy(statuses = if (LibraryStatus.READING in filter.statuses) emptySet() else setOf(LibraryStatus.READING)))
            }
            state.collections.forEach { collection ->
                LibraryToolbarButton(collection.name, "library_quick_collection_${collection.id}", palette, filter.collectionId == collection.id) {
                    onFilter(filter.copy(collectionId = collection.id.takeUnless { it == filter.collectionId }))
                }
            }
            DocumentFormat.entries.forEach { format ->
                LibraryToolbarButton("Formato ${format.name}", "library_quick_format_${format.name}", palette, format in filter.formats) {
                    onFilter(filter.copy(formats = if (format in filter.formats) filter.formats - format else filter.formats + format))
                }
            }
        }
    }
}

@Composable
private fun LibraryToolbarButton(text: String, tag: String, palette: ClayPalette, selected: Boolean = false, onClick: () -> Unit) {
    Surface(onClick = onClick, color = if (selected) palette.primary.copy(alpha = 0.12f) else palette.canvas.copy(alpha = 0f),
        contentColor = if (selected) palette.primary else palette.onSurfaceVariant,
        shape = RoundedCornerShape(12.dp), border = null,
        modifier = Modifier.heightIn(min = 48.dp).testTag(tag)) {
        Box(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), contentAlignment = Alignment.Center) {
            Text(text, fontSize = 12.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium)
        }
    }
}

package dev.meridian.folio.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.meridian.folio.core.DocumentFormat
import dev.meridian.folio.core.FolioPersistedState
import dev.meridian.folio.core.LibraryDensity
import dev.meridian.folio.core.LibraryFilter
import dev.meridian.folio.core.LibraryLayout
import dev.meridian.folio.core.LibraryPreferences
import dev.meridian.folio.core.LibraryStatus
import dev.meridian.folio.core.LibrarySort
import dev.meridian.folio.ui.theme.ClayCard
import dev.meridian.folio.ui.theme.ClayChip
import dev.meridian.folio.ui.theme.ClayHeaderAction
import dev.meridian.folio.ui.theme.ClayPalette
import dev.meridian.folio.ui.theme.ClayPrimaryButton
import dev.meridian.folio.ui.theme.ClaySpacing
import dev.meridian.folio.ui.theme.LocalClayTypography
import dev.meridian.folio.ui.theme.clayTextFieldColors

/**
 * Import entry point of the library. Literal label of the Stitch reference ("+ Importar libro");
 * it is rendered by the pre-existing tagged control (`library_tools`), never by a second button.
 */
private const val LibraryImportLabel = "+ Importar libro"

/** Placeholder of the search field: titles and authors are the only fields the repository backs. */
private const val LibrarySearchPlaceholder = "Buscar por título o autor…"

/** Width at which the header switches from one row (title, search, import) to two stacked rows. */
private val LibraryHeaderRowBreakpoint = 900.dp


/**
 * Content-sized clay card: the clay surface of the library panels.
 *
 * [ClayCard] mirrors its shadow and frosted layers with `matchParentSize` children, so it cannot
 * size itself and its caller must fix a height. Library panels, on the other hand, grow with
 * their content (chips that wrap, text fields, a variable number of collections), so this variant
 * measures [content] first and paints the very same [ClayCard] behind it at the resolved size.
 * Shadow, radius and card surface still come from the design kit; nothing else is painted here.
 */
@Composable
internal fun ClayContentCard(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(modifier) {
        Box(Modifier.matchParentSize()) { ClayCard(Modifier.fillMaxSize()) { } }
        content()
    }
}

/**
 * Library header and quick-filter toolbar, restyled on the clay design kit.
 *
 * Kept contract (pinned by `LibraryWorkspaceTest` and the migration plan): `library_count`
 * (`"$visibles / $total volúmenes"`), `library_tools` (the single import control), `library_search`,
 * `library_layout_<NAME>`, `library_density_<NAME>`, `library_quick_format_<FORMAT>`,
 * `library_select_all`, `library_filters_btn` and `library_folders`. The essential controls
 * (`library_select_all`, `library_filters_btn`, `library_folders`) are pinned outside the scrolling
 * chip row so they never scroll off-screen. New (additive): `library_header`, `library_toolbar`
 * and `library_quick_status_<STATUS|ALL>`.
 */
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
    filtersOpen: Boolean,
    onSelectAll: () -> Unit,
) {
    val prefs = state.libraryPreferences
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(ClaySpacing.md)) {
        LibraryHeader(state, palette, filter, resultCount, onFilter, onTools)
        LibraryQuickToolbar(state, palette, prefs, filter, onFilter, onPreferences, onFilters, filtersOpen, onFolders, onSelectAll)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LibraryHeader(
    state: FolioPersistedState,
    palette: ClayPalette,
    filter: LibraryFilter,
    resultCount: Int,
    onFilter: (LibraryFilter) -> Unit,
    onTools: () -> Unit,
) {
    val typography = LocalClayTypography.current
    val search: @Composable (Modifier) -> Unit = { modifier ->
        OutlinedTextField(
            filter.search,
            { onFilter(filter.copy(search = it)) },
            placeholder = { Text(LibrarySearchPlaceholder, color = palette.textMuted, style = typography.bodySm) },
            singleLine = true,
            leadingIcon = { Text("⌕", color = palette.onSurfaceVariant, fontSize = 22.sp) },
            shape = RoundedCornerShape(percent = 50),
            colors = clayTextFieldColors(palette),
            modifier = modifier.shioriTextFieldFocusGuard().testTag("library_search"),
        )
    }
    val title: @Composable (Modifier) -> Unit = { modifier ->
        Column(modifier) {
            FlowRow(verticalArrangement = Arrangement.spacedBy(6.dp), horizontalArrangement = Arrangement.spacedBy(ClaySpacing.sm)) {
                Text(
                    "Tu biblioteca",
                    color = palette.onSurface,
                    style = typography.headlineMd,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                LibraryCountCapsule(palette, resultCount, state.documents.size)
            }
            Text(
                "Tu próxima lectura empieza aquí.",
                color = palette.onSurfaceVariant,
                style = typography.bodySm,
                modifier = Modifier.padding(top = 4.dp),
            )
            // Brand wordmark of the empty library (the shell chrome is not rendered without a book).
            if (state.documents.isEmpty()) Text(
                "shiorifolio",
                color = palette.primary,
                style = typography.headlineSm,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
    BoxWithConstraints(Modifier.fillMaxWidth().testTag("library_header")) {
        if (maxWidth >= LibraryHeaderRowBreakpoint) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(ClaySpacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                title(Modifier.weight(1f))
                search(Modifier.widthIn(min = 320.dp, max = 360.dp))
                ClayPrimaryButton(LibraryImportLabel, onClick = onTools, tag = "library_tools")
            }
        } else {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(ClaySpacing.sm)) {
                title(Modifier.fillMaxWidth())
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(ClaySpacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    search(Modifier.weight(1f))
                    ClayPrimaryButton(LibraryImportLabel, onClick = onTools, tag = "library_tools")
                }
            }
        }
    }
}

/** Recessed counter capsule of the header. `library_count` keeps the exact volume tally text. */
@Composable
private fun LibraryCountCapsule(palette: ClayPalette, visible: Int, total: Int) {
    val shape = RoundedCornerShape(percent = 50)
    // A filled capsule with a hairline edge. It shares the notebook counter's *shape* (rounded
    // pill + hairline border), but not its recipe: this one is filled with `surfaceSecondary` and
    // outlined in `primary`, whereas the notebook counter is filled with `primaryContainer`. It
    // skips `clayPressed` because its inset bands are sized for large cards and, at 16.dp blur,
    // would paint an 8.dp band over the top/bottom of this short pill and hide the tally text.
    Box(
        Modifier
            .clip(shape)
            .background(palette.surfaceSecondary)
            .border(1.dp, palette.primary.copy(alpha = 0.35f), shape)
            .padding(horizontal = ClaySpacing.sm, vertical = 6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(palette.primary))
            Text(
                "$visible / $total volúmenes",
                color = palette.primary,
                style = LocalClayTypography.current.labelMd,
                maxLines = 1,
                modifier = Modifier.testTag("library_count"),
            )
        }
    }
}

/** One compact toolbar; view preferences and sorting live in small menus. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LibraryQuickToolbar(
    state: FolioPersistedState,
    palette: ClayPalette,
    prefs: LibraryPreferences,
    filter: LibraryFilter,
    onFilter: (LibraryFilter) -> Unit,
    onPreferences: (LibraryPreferences) -> Unit,
    onFilters: () -> Unit,
    filtersOpen: Boolean,
    onFolders: () -> Unit,
    onSelectAll: () -> Unit,
) {
    var showView by remember { mutableStateOf(false) }
    var showSort by remember { mutableStateOf(false) }
    var showMore by remember { mutableStateOf(false) }
    val active = filter.activeFacetCount()
    val counts = remember(state.documents) { state.documents.groupingBy { it.libraryStatus }.eachCount() }
    val quick: @Composable (Modifier) -> Unit = { modifier ->
        Row(modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically) {
            ClayHeaderAction("Todos (${state.documents.size})", palette, "library_quick_status_ALL", active == 0, quiet = true) {
                onFilter(LibraryFilter(search = filter.search))
            }
            LibraryStatus.entries.forEach { status ->
                val count = counts[status] ?: 0
                if (count > 0) ClayHeaderAction("${status.label} ($count)", palette, "library_quick_status_${status.name}",
                    status in filter.statuses, quiet = true) {
                    onFilter(filter.copy(statuses = if (status in filter.statuses) emptySet() else setOf(status)))
                }
            }
            DocumentFormat.entries.forEach { format ->
                ClayHeaderAction(format.name, palette, "library_quick_format_${format.name}", format in filter.formats, quiet = true) {
                    onFilter(filter.copy(formats = if (format in filter.formats) filter.formats - format else filter.formats + format))
                }
            }
            state.collections.forEach { collection ->
                ClayHeaderAction(collection.name, palette, "library_quick_collection_${collection.id}", filter.collectionId == collection.id, quiet = true) {
                    onFilter(filter.copy(collectionId = collection.id.takeUnless { it == filter.collectionId }))
                }
            }
        }
    }
    val actions: @Composable () -> Unit = {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            ClayHeaderAction(if (active == 0) "Filtros" else "Filtros · $active", palette, "library_filters_btn", filtersOpen || active > 0,
                onClick = onFilters)
            Box {
                ClayHeaderAction(if (prefs.layout == LibraryLayout.CARDS) "▦  Pósteres" else "☰  Lista", palette, "library_view_menu", showView) { showView = true }
                DropdownMenu(showView, { showView = false }) {
                    LibraryLayout.entries.forEach { layout ->
                        DropdownMenuItem(text = { Text((if (layout == prefs.layout) "✓  " else "") + if (layout == LibraryLayout.CARDS) "Pósteres" else "Lista") },
                            modifier = Modifier.testTag("library_layout_${layout.name}"),
                            onClick = { showView = false; onPreferences(prefs.copy(layout = layout)) })
                    }
                    HorizontalDivider()
                    LibraryDensity.entries.forEach { density ->
                        DropdownMenuItem(text = { Text((if (density == prefs.density) "✓  " else "") + density.label) },
                            modifier = Modifier.testTag("library_density_${density.name}"),
                            onClick = { showView = false; onPreferences(prefs.copy(density = density)) })
                    }
                }
            }
            Box {
                ClayHeaderAction("${prefs.sort.label} ${if (prefs.descending) "↓" else "↑"}", palette, "library_sort_menu", showSort,
                    description = "Ordenar biblioteca") { showSort = true }
                DropdownMenu(showSort, { showSort = false }) {
                    LibrarySort.entries.forEach { sort ->
                        DropdownMenuItem(text = { Text((if (sort == prefs.sort) "✓  " else "") + sort.label) },
                            modifier = Modifier.testTag("library_sort_${sort.name}"),
                            onClick = { showSort = false; onPreferences(prefs.copy(sort = sort)) })
                    }
                    HorizontalDivider()
                    DropdownMenuItem(text = { Text(if (prefs.descending) "Descendente ↓" else "Ascendente ↑") },
                        modifier = Modifier.testTag("library_sort_direction"),
                        onClick = { showSort = false; onPreferences(prefs.copy(descending = !prefs.descending)) })
                }
            }
            Box {
                ClayHeaderAction("⋯", palette, "library_more_menu", showMore, description = "Más opciones de biblioteca") { showMore = true }
                DropdownMenu(showMore, { showMore = false }) {
                    DropdownMenuItem(text = { Text("Carpetas vigiladas") }, modifier = Modifier.testTag("library_folders"),
                        onClick = { showMore = false; onFolders() })
                    DropdownMenuItem(text = { Text("Seleccionar todos") }, modifier = Modifier.testTag("library_select_all"),
                        onClick = { showMore = false; onSelectAll() })
                }
            }
        }
    }
    ClayContentCard(Modifier.fillMaxWidth().testTag("library_toolbar")) {
        BoxWithConstraints(Modifier.fillMaxWidth().padding(8.dp)) {
            if (maxWidth >= 760.dp) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    quick(Modifier.weight(1f))
                    actions()
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    quick(Modifier.fillMaxWidth())
                    actions()
                }
            }
        }
    }
}

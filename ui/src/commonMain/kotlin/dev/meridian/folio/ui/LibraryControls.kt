package dev.meridian.folio.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import dev.meridian.folio.ui.theme.ClayCard
import dev.meridian.folio.ui.theme.ClayChip
import dev.meridian.folio.ui.theme.ClayChromeAction
import dev.meridian.folio.ui.theme.ClayPalette
import dev.meridian.folio.ui.theme.ClayPrimaryButton
import dev.meridian.folio.ui.theme.ClaySpacing
import dev.meridian.folio.ui.theme.LocalClayTypography
import dev.meridian.folio.ui.theme.clayPressed
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

/** Height of the quick-filter toolbar card: two chip rows plus the card padding. */
private val LibraryToolbarCardHeight = 112.dp

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
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(ClaySpacing.sm)) {
                Text(
                    "Tu biblioteca",
                    color = palette.onSurface,
                    style = typography.headlineLarge,
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
                search(Modifier.widthIn(min = 240.dp, max = 360.dp))
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
    Box(Modifier.clip(shape).background(palette.surfaceSecondary).clayPressed(shape).padding(horizontal = ClaySpacing.sm, vertical = 6.dp)) {
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

/** Quick filters (chips), view switcher and the filters/folders/selection controls. */
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
    // The card is explicitly sized (its layers are matchParentSize children): two chip rows of
    // 40.dp plus the card padding. The rows never wrap past it; where a row can overflow it scrolls.
    ClayCard(Modifier.fillMaxWidth().heightIn(min = LibraryToolbarCardHeight).testTag("library_toolbar")) {
        Column(
            Modifier.fillMaxWidth().padding(ClaySpacing.sm),
            verticalArrangement = Arrangement.spacedBy(ClaySpacing.xs),
        ) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(ClaySpacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val allSelected = filter.statuses.isEmpty() && filter.formats.isEmpty() && filter.collectionId == null
                ClayChip(
                    "Todos (${state.documents.size})", allSelected,
                    onClick = { onFilter(filter.copy(statuses = emptySet(), formats = emptySet(), collectionId = null)) },
                    tag = "library_quick_status_ALL",
                )
                LibraryStatus.entries.forEach { status ->
                    val count = state.documents.count { it.libraryStatus == status }
                    if (count == 0) return@forEach
                    ClayChip(
                        "${status.label} ($count)",
                        selected = status in filter.statuses,
                        onClick = {
                            onFilter(filter.copy(statuses = if (status in filter.statuses) emptySet() else setOf(status)))
                        },
                        tag = "library_quick_status_${status.name}",
                    )
                }
                state.collections.forEach { collection ->
                    ClayChip(
                        collection.name,
                        selected = filter.collectionId == collection.id,
                        onClick = { onFilter(filter.copy(collectionId = collection.id.takeUnless { it == filter.collectionId })) },
                        tag = "library_quick_collection_${collection.id}",
                    )
                }
                DocumentFormat.entries.forEach { format ->
                    ClayChip(
                        "Formato ${format.name}",
                        selected = format in filter.formats,
                        onClick = { onFilter(filter.copy(formats = if (format in filter.formats) filter.formats - format else filter.formats + format)) },
                        tag = "library_quick_format_${format.name}",
                    )
                }
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(ClaySpacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Only the view switcher scrolls; the essential controls below stay pinned so they
                // remain visible at any width instead of scrolling off-screen.
                Row(
                    Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(ClaySpacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        Modifier.clip(RoundedCornerShape(percent = 50)).background(palette.surfaceSecondary).padding(3.dp),
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        LibraryLayout.entries.forEach { layout ->
                            ClayChip(
                                if (layout == LibraryLayout.CARDS) "Pósteres" else "Lista",
                                selected = prefs.layout == layout,
                                onClick = { onPreferences(prefs.copy(layout = layout)) },
                                tag = "library_layout_${layout.name}",
                            )
                        }
                        LibraryDensity.entries.forEach { density ->
                            ClayChip(
                                density.label,
                                selected = prefs.density == density,
                                onClick = { onPreferences(prefs.copy(density = density)) },
                                tag = "library_density_${density.name}",
                            )
                        }
                    }
                }
                ClayChromeAction(
                    "Filtros y orden " + if (filtersOpen) "▴" else "▾",
                    palette,
                    "library_filters_btn",
                    selected = filtersOpen,
                    onClick = onFilters,
                )
                ClayChromeAction("Carpetas", palette, "library_folders", onClick = onFolders)
                ClayChromeAction("Seleccionar", palette, "library_select_all", onClick = onSelectAll)
            }
        }
    }
}

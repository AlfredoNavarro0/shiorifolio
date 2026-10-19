package dev.meridian.folio.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.meridian.folio.core.*
import dev.meridian.folio.ui.theme.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate
import kotlin.math.roundToInt

@Composable
internal fun LibraryFolderEffects(repository: FolioRepository) {
    val state by repository.state.collectAsState()
    val scope = rememberCoroutineScope()
    // Sync timestamps do not restart observers or trigger another scan.
    val configuration = state.watchedFolders.map { Triple(it.id, it.location, it.includeSubfolders to it.observeChanges) }
    LaunchedEffect(configuration) {
        if (configuration.isNotEmpty()) repository.library.synchronizeFolders()
    }
    DisposableEffect(configuration) {
        var pending: Job? = null
        val observers = state.watchedFolders.filter { it.observeChanges }.map { folder ->
            PlatformLibraryIo.observe(folder) {
                scope.launch {
                    pending?.cancel()
                    pending = scope.launch { delay(800); repository.library.synchronizeFolders() }
                }
            }
        }
        onDispose { pending?.cancel(); observers.forEach { it.close() } }
    }
}

@Composable
internal fun LibraryWorkspace(
    repository: FolioRepository,
    palette: ClayPalette,
    onOpenBook: (String) -> Unit,
    onCreated: () -> Unit,
    onPickFolder: (((String) -> Unit) -> Unit)?,
    onPickFile: (((String) -> Unit) -> Unit)?,
    onPickExport: ((String, (String) -> Unit) -> Unit)?,
) {
    val state by repository.state.collectAsState()
    val progress by repository.library.progress.collectAsState()
    val typography = LocalClayTypography.current
    val scope = rememberCoroutineScope()
    var filter by remember { mutableStateOf(LibraryFilter()) }
    var selected by remember { mutableStateOf(setOf<String>()) }
    var showFilters by remember { mutableStateOf(false) }
    var showFolders by remember { mutableStateOf(false) }
    var showTools by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }
    var reloadBook by remember { mutableStateOf<BookDocument?>(null) }
    var metadataBook by remember { mutableStateOf<BookDocument?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val books = remember(state.documents, state.telemetry, state.libraryPreferences, filter) { queryLibrary(state, filter) }
    val prefs = state.libraryPreferences
    LaunchedEffect(state.documents) { selected = selected.intersect(state.documents.map { it.id }.toSet()) }

    fun importPath(path: String) {
        scope.launch {
            busy = true
            try {
                val book = withContext(PlatformLibraryIo.ioDispatcher) { repository.library.importFile(path, select = true) }
                message = "Importado: ${book.title}"
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                message = "No se pudo importar: ${e.message}"
            } finally { busy = false }
        }
    }

    fun exportBook(book: BookDocument) {
        val name = book.title.replace(Regex("[^A-Za-z0-9_-]"), "_") + ".${book.format.extension}"
        if (onPickExport == null) { message = "Abre Importar / Crear / Exportar e indica una ruta de destino."; return }
        onPickExport(name) { target -> scope.launch {
            val ok = withContext(PlatformLibraryIo.ioDispatcher) { repository.exportDocumentToFile(book.id, target) }
            message = if (ok) "Exportado a $target" else "No se pudo exportar a $target"
        } }
    }

    Column(
        Modifier.fillMaxSize().background(palette.canvas)
            .padding(horizontal = ClaySpacing.lg, vertical = ClaySpacing.md)
            .testTag("library_workspace"),
    ) {
        if (progress.running || progress.processed > 0 || progress.errors.isNotEmpty()) {
            ClayContentCard(Modifier.fillMaxWidth().padding(bottom = ClaySpacing.xs).testTag("library_import_progress")) {
                Column(Modifier.fillMaxWidth().padding(ClaySpacing.sm), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (progress.running) ClayProgressBar(
                        if (progress.total == 0) 0f else progress.processed.toFloat() / progress.total,
                    )
                    Text(
                        "${progress.processed}/${progress.total} · ${progress.imported} nuevos · ${progress.duplicates} existentes · ${progress.errors.size} errores" +
                            if (progress.running) " · ${progress.currentFile}" else "",
                        color = palette.onSurfaceVariant, style = typography.bodySm,
                    )
                }
            }
        }
        message?.let { text ->
            ClayContentCard(Modifier.fillMaxWidth().padding(bottom = ClaySpacing.xs)) {
                Text(
                    text,
                    color = palette.onSurface,
                    style = typography.bodyMd,
                    modifier = Modifier.padding(ClaySpacing.sm).testTag("library_message"),
                )
            }
        }
        if (selected.isNotEmpty()) {
            LibrarySelectionBar(repository, palette, selected,
                onClear = { selected = emptySet() }, onDelete = { showDelete = true })
        }
        LazyVerticalGrid(
            columns = if (prefs.layout == LibraryLayout.LIST) GridCells.Fixed(1)
            else GridCells.Adaptive(if (prefs.density == LibraryDensity.COMPACT) 140.dp else 165.dp),
            horizontalArrangement = Arrangement.spacedBy(ClaySpacing.md), verticalArrangement = Arrangement.spacedBy(ClaySpacing.lg),
            contentPadding = PaddingValues(horizontal = ClaySpacing.xs, vertical = ClaySpacing.xs),
            modifier = Modifier.weight(1f).fillMaxWidth().testTag("library_books"),
        ) {
            item(key = "library_controls", span = { GridItemSpan(maxLineSpan) }) {
                LibraryControls(state, palette, filter, books.size,
                    onFilter = { filter = it }, onPreferences = { repository.library.setPreferences(it) },
                    onTools = { showTools = !showTools }, onFolders = { showFolders = !showFolders },
                    onFilters = { showFilters = !showFilters }, filtersOpen = showFilters,
                    onSelectAll = { selected = selected + books.map { it.id } })
            }
            if (showFilters) item(span = { GridItemSpan(maxLineSpan) }) {
                LibraryFilters(repository, palette, filter, { filter = it }, onClose = { showFilters = false })
            }
            if (showFolders) item(span = { GridItemSpan(maxLineSpan) }) {
                LibraryFolders(repository, palette, onPickFolder)
            }
            if (showTools) item(span = { GridItemSpan(maxLineSpan) }) {
                LibraryFileTools(repository, palette, busy, onPickFile, onPickExport, ::importPath, onCreated) { message = it }
            }
            if (progress.errors.isNotEmpty()) item(span = { GridItemSpan(maxLineSpan) }) {
                ClayContentCard(Modifier.fillMaxWidth()) {
                    Text(
                        progress.errors.joinToString("\n"), color = palette.warningAmber, style = typography.bodySm,
                        modifier = Modifier.padding(ClaySpacing.sm).testTag("library_import_errors"),
                    )
                }
            }
            if (books.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) {
                ClayContentCard(Modifier.fillMaxWidth()) {
                    Column(
                        Modifier.fillMaxWidth().padding(ClaySpacing.lg),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(ClaySpacing.xs),
                    ) {
                        Text("⌕", color = palette.primary, style = typography.headlineMd)
                        Text(
                            "No hay libros que coincidan. Importa un archivo o añade una carpeta.",
                            color = palette.onSurfaceVariant, style = typography.bodyMd, textAlign = TextAlign.Center,
                        )
                    }
                }
            }
            items(books, key = { it.id }) { book ->
                LibraryBookItem(book, state.telemetry[book.id], palette, prefs, book.id in selected, selected.isNotEmpty(),
                    state.reminders[book.id],
                    onToggleSelection = { selected = if (book.id in selected) selected - book.id else selected + book.id },
                    onOpen = {
                        if (book.libraryStatus == LibraryStatus.PENDING) repository.library.setStatus(setOf(book.id), LibraryStatus.READING)
                        onOpenBook(book.id)
                    },
                    onExport = { exportBook(book) },
                    onReload = { reloadBook = book },
                    onEditMetadata = { metadataBook = book })
            }
        }
    }
    metadataBook?.let { book ->
        BookMetadataDialog(
            palette = palette,
            book = book,
            coverPicker = null,
            onSave = { title, author, language, coverImagePath ->
                repository.updateDocumentMetadata(book.id, title, author, language, coverImagePath)
                message = "Metadatos de \"$title\" actualizados."
            },
            onClose = { metadataBook = null },
        )
    }
    reloadBook?.let { book ->
        // Secondary-flow restyle (Task 10): clay dialog chrome, buttons already come from
        // `LibraryAction` (ClayPrimaryButton/ClaySecondaryButton). Tags and handlers untouched.
        AlertDialog(
            onDismissRequest = { reloadBook = null },
            shape = RoundedCornerShape(ClayRadius.md),
            containerColor = palette.surfaceContainerLow,
            title = { Text("Recargar ${book.title}", color = palette.onSurface, style = LocalClayTypography.current.titleMd) },
            text = { Text("Se sustituirán las modificaciones del contenido por el archivo vinculado. Se conservarán el progreso, las colecciones y las anotaciones.", color = palette.onSurfaceVariant) },
            dismissButton = { LibraryAction("Cancelar", "library_cancel_reload") { reloadBook = null } },
            confirmButton = { LibraryAction("Recargar contenido", "library_confirm_reload") {
                reloadBook = null
                scope.launch {
                    try {
                        withContext(PlatformLibraryIo.ioDispatcher) {
                            val source = book.sources.firstOrNull { PlatformLibraryIo.exists(it.path) }
                            val path = source?.path ?: book.filePath ?: error("Sin archivo vinculado")
                            repository.library.importFile(path, name = "${book.title}.${book.format.extension}", folderId = source?.folderId, refresh = true)
                        }
                        message = "Contenido recargado; se conservaron el progreso y las anotaciones."
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        message = "No se pudo recargar: ${e.message}"
                    }
                }
            } },
        )
    }
    if (showDelete) {
        var deleteFiles by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { if (!busy) showDelete = false },
            shape = RoundedCornerShape(ClayRadius.md),
            containerColor = palette.surfaceContainerLow,
            title = { Text("Eliminar ${selected.size} libros", color = palette.onSurface, style = LocalClayTypography.current.titleMd) },
            text = {
                Column(Modifier.heightIn(max = 350.dp).verticalScroll(rememberScrollState())) {
                    Text("Se quitarán el progreso, las notas y los recordatorios de los libros seleccionados.", color = palette.onSurfaceVariant)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(deleteFiles, { deleteFiles = it }, enabled = !busy, modifier = Modifier.testTag("library_delete_files"))
                        Text("Eliminar también los archivos del dispositivo", color = palette.onSurfaceVariant)
                    }
                    if (!deleteFiles) Text("Quitar de la biblioteca y conservar los archivos en el dispositivo.", color = palette.onSurfaceVariant)
                    else Text("Esta acción elimina permanentemente todas las copias vinculadas:", color = palette.onSurfaceVariant)
                    if (deleteFiles) state.documents.filter { it.id in selected }.forEach { book ->
                        Text((book.sources.map { it.path } + listOfNotNull(book.filePath)).distinct().joinToString("\n"), fontSize = 12.sp)
                    }
                }
            },
            confirmButton = {
                LibraryAction(if (deleteFiles) "Confirmar eliminación del disco" else "Quitar de la biblioteca", "library_confirm_delete", enabled = !busy) {
                    val ids = selected
                    scope.launch {
                        busy = true
                        try {
                            val failures = repository.library.removeBooks(ids, deleteFiles)
                            message = if (failures.isEmpty()) "Libros eliminados de la biblioteca." else "No se pudieron eliminar: ${failures.joinToString()}"
                            selected = emptySet()
                            showDelete = false
                        } finally { busy = false }
                    }
                }
            },
            dismissButton = { LibraryAction("Cancelar", "library_cancel_delete", enabled = !busy) { showDelete = false } },
        )
    }
}

/**
 * Action of the library controls, restyled on the clay button kit: [selected] renders the active
 * state (primary gradient), the resting state is the quiet secondary surface. [tag] is optional so
 * purely presentational actions (e.g. the panel's `Aplicar`) do not add new tags.
 */
@Composable
private fun LibraryAction(text: String, tag: String? = null, selected: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) {
    if (selected) ClayPrimaryButton(text, onClick = onClick, tag = tag, enabled = enabled)
    else ClaySecondaryButton(text, onClick = onClick, tag = tag, enabled = enabled)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LibrarySelectionBar(repository: FolioRepository, palette: ClayPalette, selected: Set<String>, onClear: () -> Unit, onDelete: () -> Unit) {
    val state by repository.state.collectAsState()
    ClayContentCard(Modifier.fillMaxWidth().padding(bottom = ClaySpacing.xs).testTag("library_selection_bar")) {
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(ClaySpacing.xs),
            horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "${selected.size} seleccionados", color = palette.onSurface,
                style = LocalClayTypography.current.labelMd, modifier = Modifier.padding(horizontal = ClaySpacing.sm),
            )
            LibraryStatus.entries.forEach { status ->
                LibraryAction(status.label, "library_batch_status_${status.name}") { repository.library.setStatus(selected, status) }
            }
            state.collections.forEach { collection ->
                LibraryAction("+ ${collection.name}", "library_assign_${collection.id}") { repository.library.assignCollection(selected, collection.id, true) }
                LibraryAction("− ${collection.name}", "library_unassign_${collection.id}") { repository.library.assignCollection(selected, collection.id, false) }
            }
            LibraryAction("Eliminar…", "library_delete") { onDelete() }
            LibraryAction("Cancelar selección", "library_clear_selection") { onClear() }
        }
    }
}

/** Group caption of the filter panel (uppercase tracking, muted ink). */
@Composable
private fun FilterGroupLabel(text: String, palette: ClayPalette) {
    Text(
        text.uppercase(), color = palette.textMuted, style = LocalClayTypography.current.labelSm,
        modifier = Modifier.padding(top = ClaySpacing.xs),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LibraryFilters(
    repository: FolioRepository,
    palette: ClayPalette,
    filter: LibraryFilter,
    onChange: (LibraryFilter) -> Unit,
    onClose: () -> Unit,
) {
    val state by repository.state.collectAsState()
    var collectionName by remember { mutableStateOf("") }
    val typography = LocalClayTypography.current
    ClayContentCard(Modifier.fillMaxWidth().testTag("library_filter_panel")) {
        Column(Modifier.fillMaxWidth().padding(ClaySpacing.md), verticalArrangement = Arrangement.spacedBy(ClaySpacing.sm)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Filtros combinables", color = palette.onSurface, style = typography.titleMd)
                Row(horizontalArrangement = Arrangement.spacedBy(ClaySpacing.xs), verticalAlignment = Alignment.CenterVertically) {
                    LibraryAction("Limpiar", "library_reset_filters") { onChange(LibraryFilter()) }
                    LibraryAction("Aplicar", selected = true) { onClose() }
                }
            }
            FilterGroupLabel("Estado de lectura", palette)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                LibraryStatus.entries.forEach { status ->
                    LibraryAction(status.label, "library_filter_status_${status.name}", status in filter.statuses) {
                        onChange(filter.copy(statuses = if (status in filter.statuses) filter.statuses - status else filter.statuses + status))
                    }
                }
            }
            FilterGroupLabel("Formato de archivo", palette)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                DocumentFormat.entries.forEach { format ->
                    LibraryAction(format.name, "library_filter_format_${format.name}", format in filter.formats) {
                        onChange(filter.copy(formats = if (format in filter.formats) filter.formats - format else filter.formats + format))
                    }
                }
            }
            FilterGroupLabel("Rango de porcentaje · ${filter.minProgress}% - ${filter.maxProgress}%", palette)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                LibraryNumberField("Progreso mínimo %", filter.minProgress, "library_min_progress", palette) { onChange(filter.copy(minProgress = it.coerceIn(0, 100))) }
                LibraryNumberField("Progreso máximo %", filter.maxProgress, "library_max_progress", palette) { onChange(filter.copy(maxProgress = it.coerceIn(0, 100))) }
            }
            FilterGroupLabel("Cantidad de páginas", palette)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                LibraryNumberField("Páginas mínimas", filter.minPages, "library_min_pages", palette) { onChange(filter.copy(minPages = it)) }
                LibraryNumberField("Páginas máximas", filter.maxPages, "library_max_pages", palette) { onChange(filter.copy(maxPages = it)) }
            }
            FilterGroupLabel("Fechas", palette)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                LibraryDateField("Añadido desde", "library_added_from", filter.addedAfterMs, palette) { onChange(filter.copy(addedAfterMs = it)) }
                LibraryDateField("Añadido hasta", "library_added_to", filter.addedBeforeMs, palette, endOfDay = true) { onChange(filter.copy(addedBeforeMs = it)) }
                LibraryDateField("Finalizado desde", "library_finished_from", filter.completedAfterMs, palette) { onChange(filter.copy(completedAfterMs = it)) }
                LibraryDateField("Finalizado hasta", "library_finished_to", filter.completedBeforeMs, palette, endOfDay = true) { onChange(filter.copy(completedBeforeMs = it)) }
            }
            FilterGroupLabel("Ordenar por", palette)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                LibrarySort.entries.forEach { sort ->
                    LibraryAction(sort.label, "library_sort_${sort.name}", state.libraryPreferences.sort == sort) {
                        repository.library.setPreferences(state.libraryPreferences.copy(sort = sort))
                    }
                }
                LibraryAction(if (state.libraryPreferences.descending) "Descendente ↓" else "Ascendente ↑", "library_sort_direction") {
                    repository.library.setPreferences(state.libraryPreferences.copy(descending = !state.libraryPreferences.descending))
                }
            }
            FilterGroupLabel("Colecciones", palette)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                LibraryAction("Todas", "library_collection_all", filter.collectionId == null) { onChange(filter.copy(collectionId = null)) }
                state.collections.forEach { collection ->
                    LibraryAction(collection.name, "library_collection_${collection.id}", filter.collectionId == collection.id) { onChange(filter.copy(collectionId = collection.id)) }
                    LibraryAction("Eliminar colección ${collection.name}", "library_remove_collection_${collection.id}") {
                        repository.library.removeCollection(collection.id)
                        if (filter.collectionId == collection.id) onChange(filter.copy(collectionId = null))
                    }
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    collectionName, { collectionName = it },
                    label = { Text("Nueva colección") }, singleLine = true,
                    colors = clayTextFieldColors(palette),
                    modifier = Modifier.width(260.dp).shioriTextFieldFocusGuard().testTag("library_collection_name"),
                )
                LibraryAction("Crear colección", "library_create_collection", enabled = collectionName.isNotBlank()) {
                    repository.library.createCollection(collectionName); collectionName = ""
                }
            }
        }
    }
}

@Composable
private fun LibraryNumberField(label: String, value: Int, tag: String, palette: ClayPalette, onChange: (Int) -> Unit) {
    var draft by remember(value) { mutableStateOf(if (value == Int.MAX_VALUE) "" else value.toString()) }
    OutlinedTextField(
        draft, { input ->
            if (input.all { it.isDigit() }) {
                draft = input
                onChange(input.toIntOrNull() ?: if (tag.contains("max_pages")) Int.MAX_VALUE else 0)
            }
        },
        label = { Text(label) }, singleLine = true, colors = clayTextFieldColors(palette),
        modifier = Modifier.width(190.dp).shioriTextFieldFocusGuard().testTag(tag),
    )
}

@Composable
private fun LibraryDateField(label: String, tag: String, value: Long?, palette: ClayPalette, endOfDay: Boolean = false, onChange: (Long?) -> Unit) {
    var draft by remember { mutableStateOf("") }
    LaunchedEffect(value) { if (value == null) draft = "" }
    val day = runCatching { LocalDate.parse(draft).toEpochDays() * 86_400_000L }.getOrNull()
    OutlinedTextField(
        draft, {
            draft = it
            val parsed = runCatching { LocalDate.parse(it).toEpochDays() * 86_400_000L }.getOrNull()
            if (it.isBlank() || parsed != null) onChange(parsed?.let { ms -> ms + if (endOfDay) 86_399_999 else 0 })
        },
        label = { Text("$label · AAAA-MM-DD") }, isError = draft.isNotEmpty() && day == null,
        singleLine = true, colors = clayTextFieldColors(palette),
        modifier = Modifier.width(250.dp).shioriTextFieldFocusGuard().testTag(tag),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LibraryFolders(repository: FolioRepository, palette: ClayPalette, onPickFolder: (((String) -> Unit) -> Unit)?) {
    val state by repository.state.collectAsState()
    val progress by repository.library.progress.collectAsState()
    val scope = rememberCoroutineScope()
    var path by remember { mutableStateOf("") }
    val typography = LocalClayTypography.current
    ClayContentCard(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(ClaySpacing.md), verticalArrangement = Arrangement.spacedBy(ClaySpacing.sm)) {
            Text("Carpetas de la biblioteca", color = palette.onSurface, style = typography.titleMd)
            Text(
                "Los libros ausentes conservan su progreso y anotaciones. Android actualiza al reanudar o al pulsar Actualizar.",
                color = palette.onSurfaceVariant, style = typography.bodySm,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (onPickFolder != null) LibraryAction("Elegir carpeta…", "library_pick_folder") { onPickFolder { repository.library.addFolder(it) } }
                OutlinedTextField(
                    path, { path = it }, label = { Text("Ruta o URI de carpeta") }, singleLine = true,
                    colors = clayTextFieldColors(palette),
                    modifier = Modifier.width(320.dp).shioriTextFieldFocusGuard().testTag("library_folder_path"),
                )
                LibraryAction("Añadir carpeta", "library_add_folder", enabled = path.isNotBlank()) {
                    runCatching { repository.library.addFolder(path) }; path = ""
                }
                LibraryAction("Actualizar", "library_sync", enabled = !progress.running) { scope.launch { repository.library.synchronizeFolders() } }
            }
            state.watchedFolders.forEach { folder ->
                Column(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(folder.location, color = palette.onSurface, style = typography.bodyMd)
                    Text(
                        "Última sincronización: " + (folder.lastSyncEpochMs?.let { kotlin.time.Instant.fromEpochMilliseconds(it).toString() } ?: "Nunca"),
                        color = palette.onSurfaceVariant, style = typography.bodySm,
                    )
                    folder.lastError?.let { Text(it, color = palette.warningAmber, style = typography.bodySm) }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        LibraryAction("Incluir subcarpetas", "library_recursive_${folder.id}", folder.includeSubfolders) { repository.library.updateFolder(folder.copy(includeSubfolders = !folder.includeSubfolders)) }
                        LibraryAction("Observar cambios (Desktop)", "library_watch_${folder.id}", folder.observeChanges) { repository.library.updateFolder(folder.copy(observeChanges = !folder.observeChanges)) }
                        LibraryAction("Dejar de sincronizar", "library_remove_folder_${folder.id}") { repository.library.removeFolder(folder.id) }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LibraryFileTools(repository: FolioRepository, palette: ClayPalette, busy: Boolean,
    onPickFile: (((String) -> Unit) -> Unit)?, onPickExport: ((String, (String) -> Unit) -> Unit)?,
    onImport: (String) -> Unit, onCreated: () -> Unit, onMessage: (String) -> Unit,
) {
    var path by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }
    var author by remember { mutableStateOf("") }
    var format by remember { mutableStateOf(DocumentFormat.EPUB) }
    val scope = rememberCoroutineScope()
    val typography = LocalClayTypography.current
    ClayContentCard(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(ClaySpacing.md), verticalArrangement = Arrangement.spacedBy(ClaySpacing.sm)) {
            Text("Importar / Crear / Exportar", color = palette.onSurface, style = typography.titleMd)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    path, { path = it }, label = { Text("Ruta de archivo PDF o EPUB") }, singleLine = true,
                    colors = clayTextFieldColors(palette),
                    modifier = Modifier.width(320.dp).shioriTextFieldFocusGuard().testTag("library_file_path"),
                )
                LibraryAction("Importar", "library_import", enabled = !busy && path.isNotBlank()) { onImport(path.trim()) }
                if (onPickFile != null) LibraryAction("Elegir archivo…", "library_pick_file", enabled = !busy) { onPickFile(onImport) }
                LibraryAction("Exportar libro activo", "library_export", enabled = repository.currentBook() != null) {
                    val book = repository.currentBook() ?: return@LibraryAction
                    val defaultName = book.title.replace(Regex("[^A-Za-z0-9_-]"), "_") + ".${book.format.extension}"
                    val export: (String) -> Unit = { target -> scope.launch {
                        val ok = withContext(PlatformLibraryIo.ioDispatcher) { repository.exportDocumentToFile(book.id, target) }
                        onMessage(if (ok) "Exportado a $target" else "No se pudo exportar a $target")
                    } }
                    if (onPickExport != null) onPickExport(defaultName, export) else if (path.isNotBlank()) export(path.trim())
                    else onMessage("Indica una ruta de destino para exportar.")
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    title, { title = it }, label = { Text("Título") }, singleLine = true,
                    colors = clayTextFieldColors(palette),
                    modifier = Modifier.width(260.dp).shioriTextFieldFocusGuard().testTag("library_new_title"),
                )
                OutlinedTextField(
                    author, { author = it }, label = { Text("Autor") }, singleLine = true,
                    colors = clayTextFieldColors(palette),
                    modifier = Modifier.width(260.dp).shioriTextFieldFocusGuard().testTag("library_new_author"),
                )
                DocumentFormat.entries.forEach { f -> LibraryAction(f.name, "library_new_format_${f.name}", f == format) { format = f } }
                LibraryAction("Crear", "library_create", enabled = title.isNotBlank()) { repository.createNewDocument(title, author, format); onCreated() }
            }
        }
    }
}

@Composable
private fun LibraryBookItem(book: BookDocument, telemetry: BookReadingTelemetry?, palette: ClayPalette,
    prefs: LibraryPreferences, selected: Boolean, selectionMode: Boolean, reminder: PageReminder?, onToggleSelection: () -> Unit, onOpen: () -> Unit,
    onExport: () -> Unit, onReload: () -> Unit, onEditMetadata: () -> Unit,
) {
    val detailed = prefs.density == LibraryDensity.DETAILED
    if (prefs.layout == LibraryLayout.CARDS) {
        LibraryBookCard(book, telemetry, palette, detailed, selected, selectionMode, reminder, onToggleSelection, onOpen, onExport, onReload, onEditMetadata)
        return
    }
    val typography = LocalClayTypography.current
    val prediction = remember(book, telemetry) { ReadingPredictionEngine.computePrediction(book, telemetry) }
    ClayContentCard(Modifier.fillMaxWidth().testTag("library_book_${book.id}")) {
        Column(Modifier.fillMaxWidth().padding(ClaySpacing.sm), verticalArrangement = Arrangement.spacedBy(ClaySpacing.xs)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(ClaySpacing.xs)) {
                Checkbox(
                    selected, { onToggleSelection() },
                    modifier = Modifier.testTag("library_select_${book.id}").semantics { contentDescription = "Seleccionar ${book.title}" },
                )
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(book.title, color = palette.onSurface, style = typography.titleMd, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(book.author, color = palette.onSurfaceVariant, style = typography.bodySm, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (book.isMissingOnDisk) Text(
                        "No disponible en disco", color = palette.warningAmber, style = typography.bodySm,
                        modifier = Modifier.testTag("library_missing_${book.id}"),
                    )
                }
            }
            if (detailed) LibraryBookMetrics(book, telemetry, prediction, palette, reminder)
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(ClaySpacing.xs),
            ) {
                LibraryAction("Abrir", "library_open_${book.id}") { onOpen() }
                LibraryAction("Metadatos", "library_edit_metadata_${book.id}") { onEditMetadata() }
                if (detailed) {
                    LibraryAction("Exportar", "library_export_${book.id}") { onExport() }
                    if (book.filePath != null) LibraryAction("Recargar", "library_reload_${book.id}", enabled = !book.isMissingOnDisk) { onReload() }
                }
            }
        }
    }
}

@Composable
private fun LibraryBookMetrics(book: BookDocument, telemetry: BookReadingTelemetry?, prediction: FinishTimePrediction,
    palette: ClayPalette, reminder: PageReminder?,
) {
    val typography = LocalClayTypography.current
    Text("${book.format.name} · ${book.libraryStatus.label}", color = palette.onSurfaceVariant, style = typography.labelMd)
    ClayProgressBar(book.libraryProgress())
    Text(
        "${(book.libraryProgress() * 100).roundToInt()}% · ${book.totalPages} " + if (book.format == DocumentFormat.EPUB) "págs. estimadas" else "páginas",
        color = palette.onSurfaceVariant, style = typography.bodySm,
    )
    Text(
        "${prediction.formattedRemainingTime()} restantes · " + if ((telemetry?.totalActiveSeconds ?: 0.0) > 0) "Según tu ritmo" else "Estimado inicial",
        color = palette.onSurfaceVariant, style = typography.bodySm,
    )
    reminder?.let { Text("Recordatorio · pág. ${it.pageNumber}: ${it.note}", color = palette.onSurfaceVariant, style = typography.bodySm) }
}

@Composable
internal fun LibraryCover(book: BookDocument, palette: ClayPalette) {
    val coverPath = remember(book.id, book.pages, book.coverImagePath) {
        book.coverImagePath?.takeIf { it.isNotBlank() }
            ?: book.pages.firstOrNull()?.blocks?.firstOrNull { it.style == BlockStyle.IMAGE }?.imagePath
    }
    val bitmap by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, coverPath) {
        value = withContext(Dispatchers.Default) {
            coverPath?.takeUnless { it.startsWith("lazy_pdf://") }?.let { path ->
                PlatformFileAndZipIo.readFileBytes(path)?.let { runCatching { decodeImageBitmapFromBytes(it) }.getOrNull() }
            }
        }
    }
    Box(Modifier.fillMaxSize().background(palette.surfaceSecondary), contentAlignment = Alignment.Center) {
        val image = bitmap
        if (image != null) Image(image, "Portada de ${book.title}", Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else Column(
            Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Brush.verticalGradient(
                listOf(palette.primary.copy(alpha = 0.15f), palette.surfaceContainerLowest, palette.surfaceSecondary)))
                .padding(horizontal = 22.dp, vertical = 48.dp),
            horizontalAlignment = Alignment.Start, verticalArrangement = Arrangement.Center,
        ) {
            Text(book.format.name + " /", color = palette.primary, fontFamily = FontFamily.Monospace,
                fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
            Spacer(Modifier.height(18.dp))
            Text(book.title, color = palette.onSurface, fontFamily = FontFamily.Serif, fontSize = 21.sp,
                lineHeight = 27.sp, fontWeight = FontWeight.Medium, maxLines = 5, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(18.dp))
            HorizontalDivider(Modifier.width(32.dp), color = palette.primary, thickness = 2.dp)
            Spacer(Modifier.height(12.dp))
            Text(book.author, color = palette.onSurfaceVariant, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

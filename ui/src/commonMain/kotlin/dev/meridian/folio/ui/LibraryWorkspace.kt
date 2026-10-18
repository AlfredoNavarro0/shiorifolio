package dev.meridian.folio.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.draw.clip
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun LibraryWorkspace(
    repository: FolioRepository,
    palette: MeridianPalette,
    onOpenBook: (String) -> Unit,
    onCreated: () -> Unit,
    onPickFolder: (((String) -> Unit) -> Unit)?,
    onPickFile: (((String) -> Unit) -> Unit)?,
    onPickExport: ((String, (String) -> Unit) -> Unit)?,
) {
    val state by repository.state.collectAsState()
    val progress by repository.library.progress.collectAsState()
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

    Column(Modifier.fillMaxSize().background(palette.paper).padding(horizontal = 24.dp, vertical = 20.dp).testTag("library_workspace")) {
        if (progress.running || progress.processed > 0 || progress.errors.isNotEmpty()) {
            Column(Modifier.fillMaxWidth().testTag("library_import_progress")) {
                if (progress.running) LinearProgressIndicator(
                    progress = { if (progress.total == 0) 0f else progress.processed.toFloat() / progress.total }, modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "${progress.processed}/${progress.total} · ${progress.imported} nuevos · ${progress.duplicates} existentes · ${progress.errors.size} errores" +
                        if (progress.running) " · ${progress.currentFile}" else "",
                    color = palette.inkSoft, fontSize = 12.sp,
                )
            }
        }
        message?.let { Text(it, color = palette.ink, modifier = Modifier.testTag("library_message")) }
        if (selected.isNotEmpty()) {
            LibrarySelectionBar(repository, palette, selected,
                onClear = { selected = emptySet() }, onDelete = { showDelete = true })
        }
        LazyVerticalGrid(
            columns = if (prefs.layout == LibraryLayout.LIST) GridCells.Fixed(1) else GridCells.Adaptive(if (prefs.density == LibraryDensity.COMPACT) 140.dp else 165.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp), verticalArrangement = Arrangement.spacedBy(24.dp),
            contentPadding = PaddingValues(vertical = 16.dp), modifier = Modifier.weight(1f).testTag("library_books"),
        ) {
            item(key = "library_controls", span = { GridItemSpan(maxLineSpan) }) {
                LibraryControls(state, palette, filter, books.size,
                    onFilter = { filter = it }, onPreferences = { repository.library.setPreferences(it) },
                    onTools = { showTools = !showTools }, onFolders = { showFolders = !showFolders },
                    onFilters = { showFilters = !showFilters }, onSelectAll = { selected = selected + books.map { it.id } })
            }
            if (showFilters) item(span = { GridItemSpan(maxLineSpan) }) {
                LibraryFilters(repository, palette, filter) { filter = it }
            }
            if (showFolders) item(span = { GridItemSpan(maxLineSpan) }) {
                LibraryFolders(repository, palette, onPickFolder)
            }
            if (showTools) item(span = { GridItemSpan(maxLineSpan) }) {
                LibraryFileTools(repository, palette, busy, onPickFile, onPickExport, ::importPath, onCreated) { message = it }
            }
            if (progress.errors.isNotEmpty()) item(span = { GridItemSpan(maxLineSpan) }) {
                Text(progress.errors.joinToString("\n"), color = palette.warningAmber, modifier = Modifier.testTag("library_import_errors"))
            }
            if (books.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) {
                Text("No hay libros que coincidan. Importa un archivo o añade una carpeta.", color = palette.inkSoft)
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
        AlertDialog(
            onDismissRequest = { reloadBook = null },
            title = { Text("Recargar ${book.title}") },
            text = { Text("Se sustituirán las modificaciones del contenido por el archivo vinculado. Se conservarán el progreso, las colecciones y las anotaciones.") },
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
            title = { Text("Eliminar ${selected.size} libros") },
            text = {
                Column(Modifier.heightIn(max = 350.dp).verticalScroll(rememberScrollState())) {
                    Text("Se quitarán el progreso, las notas y los recordatorios de los libros seleccionados.")
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(deleteFiles, { deleteFiles = it }, enabled = !busy, modifier = Modifier.testTag("library_delete_files"))
                        Text("Eliminar también los archivos del dispositivo")
                    }
                    if (!deleteFiles) Text("Quitar de la biblioteca y conservar los archivos en el dispositivo.")
                    else Text("Esta acción elimina permanentemente todas las copias vinculadas:")
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

@Composable
private fun LibraryAction(text: String, tag: String, selected: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) {
    if (selected) Button(onClick, enabled = enabled, modifier = Modifier.heightIn(min = 48.dp).testTag(tag)) { Text(text, fontSize = 13.sp, fontWeight = FontWeight.Medium) }
    else OutlinedButton(onClick, enabled = enabled, modifier = Modifier.heightIn(min = 48.dp).testTag(tag)) { Text(text, fontSize = 13.sp, fontWeight = FontWeight.Medium) }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LibrarySelectionBar(repository: FolioRepository, palette: MeridianPalette, selected: Set<String>, onClear: () -> Unit, onDelete: () -> Unit) {
    val state by repository.state.collectAsState()
    Row(Modifier.fillMaxWidth().background(palette.elevatedSurface).horizontalScroll(rememberScrollState()).padding(8.dp).testTag("library_selection_bar"), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("${selected.size} seleccionados", color = palette.ink, modifier = Modifier.padding(12.dp))
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LibraryFilters(repository: FolioRepository, palette: MeridianPalette, filter: LibraryFilter, onChange: (LibraryFilter) -> Unit) {
    val state by repository.state.collectAsState()
    var collectionName by remember { mutableStateOf("") }
    Column(Modifier.fillMaxWidth().background(palette.elevatedSurface).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Filtros combinables", color = palette.ink, fontWeight = FontWeight.SemiBold)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            LibraryStatus.entries.forEach { status ->
                LibraryAction(status.label, "library_filter_status_${status.name}", status in filter.statuses) {
                    onChange(filter.copy(statuses = if (status in filter.statuses) filter.statuses - status else filter.statuses + status))
                }
            }
            DocumentFormat.entries.forEach { format ->
                LibraryAction(format.name, "library_filter_format_${format.name}", format in filter.formats) {
                    onChange(filter.copy(formats = if (format in filter.formats) filter.formats - format else filter.formats + format))
                }
            }
            LibraryAction("Limpiar filtros", "library_reset_filters") { onChange(LibraryFilter()) }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LibraryNumberField("Progreso mínimo %", filter.minProgress, "library_min_progress") { onChange(filter.copy(minProgress = it.coerceIn(0, 100))) }
            LibraryNumberField("Progreso máximo %", filter.maxProgress, "library_max_progress") { onChange(filter.copy(maxProgress = it.coerceIn(0, 100))) }
            LibraryNumberField("Páginas mínimas", filter.minPages, "library_min_pages") { onChange(filter.copy(minPages = it)) }
            LibraryNumberField("Páginas máximas", filter.maxPages, "library_max_pages") { onChange(filter.copy(maxPages = it)) }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LibraryDateField("Añadido desde", "library_added_from", filter.addedAfterMs) { onChange(filter.copy(addedAfterMs = it)) }
            LibraryDateField("Añadido hasta", "library_added_to", filter.addedBeforeMs, endOfDay = true) { onChange(filter.copy(addedBeforeMs = it)) }
            LibraryDateField("Finalizado desde", "library_finished_from", filter.completedAfterMs) { onChange(filter.copy(completedAfterMs = it)) }
            LibraryDateField("Finalizado hasta", "library_finished_to", filter.completedBeforeMs, endOfDay = true) { onChange(filter.copy(completedBeforeMs = it)) }
        }
        Text("Ordenar por", color = palette.inkSoft)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            LibrarySort.entries.forEach { sort ->
                LibraryAction(sort.label, "library_sort_${sort.name}", state.libraryPreferences.sort == sort) {
                    repository.library.setPreferences(state.libraryPreferences.copy(sort = sort))
                }
            }
            LibraryAction(if (state.libraryPreferences.descending) "Descendente ↓" else "Ascendente ↑", "library_sort_direction") {
                repository.library.setPreferences(state.libraryPreferences.copy(descending = !state.libraryPreferences.descending))
            }
        }
        Text("Colecciones", color = palette.ink, fontWeight = FontWeight.SemiBold)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            LibraryAction("Todas", "library_collection_all", filter.collectionId == null) { onChange(filter.copy(collectionId = null)) }
            state.collections.forEach { collection ->
                LibraryAction(collection.name, "library_collection_${collection.id}", filter.collectionId == collection.id) { onChange(filter.copy(collectionId = collection.id)) }
                LibraryAction("Eliminar colección ${collection.name}", "library_remove_collection_${collection.id}") {
                    repository.library.removeCollection(collection.id)
                    if (filter.collectionId == collection.id) onChange(filter.copy(collectionId = null))
                }
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(collectionName, { collectionName = it }, label = { Text("Nueva colección") }, singleLine = true,
                modifier = Modifier.width(240.dp).shioriTextFieldFocusGuard().testTag("library_collection_name"))
            LibraryAction("Crear colección", "library_create_collection", enabled = collectionName.isNotBlank()) {
                repository.library.createCollection(collectionName); collectionName = ""
            }
        }
    }
}

@Composable
private fun LibraryNumberField(label: String, value: Int, tag: String, onChange: (Int) -> Unit) {
    var draft by remember(value) { mutableStateOf(if (value == Int.MAX_VALUE) "" else value.toString()) }
    OutlinedTextField(draft, { input ->
        if (input.all { it.isDigit() }) {
            draft = input
            onChange(input.toIntOrNull() ?: if (tag.contains("max_pages")) Int.MAX_VALUE else 0)
        }
    }, label = { Text(label) }, singleLine = true, modifier = Modifier.width(180.dp).shioriTextFieldFocusGuard().testTag(tag))
}

@Composable
private fun LibraryDateField(label: String, tag: String, value: Long?, endOfDay: Boolean = false, onChange: (Long?) -> Unit) {
    var draft by remember { mutableStateOf("") }
    LaunchedEffect(value) { if (value == null) draft = "" }
    val day = runCatching { LocalDate.parse(draft).toEpochDays() * 86_400_000L }.getOrNull()
    OutlinedTextField(draft, {
        draft = it
        val parsed = runCatching { LocalDate.parse(it).toEpochDays() * 86_400_000L }.getOrNull()
        if (it.isBlank() || parsed != null) onChange(parsed?.let { ms -> ms + if (endOfDay) 86_399_999 else 0 })
    }, label = { Text("$label · AAAA-MM-DD") }, isError = draft.isNotEmpty() && day == null,
        singleLine = true, modifier = Modifier.width(240.dp).shioriTextFieldFocusGuard().testTag(tag))
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LibraryFolders(repository: FolioRepository, palette: MeridianPalette, onPickFolder: (((String) -> Unit) -> Unit)?) {
    val state by repository.state.collectAsState()
    val progress by repository.library.progress.collectAsState()
    val scope = rememberCoroutineScope()
    var path by remember { mutableStateOf("") }
    Column(Modifier.fillMaxWidth().background(palette.elevatedSurface).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Carpetas de la biblioteca", color = palette.ink, fontWeight = FontWeight.SemiBold)
        Text("Los libros ausentes conservan su progreso y anotaciones. Android actualiza al reanudar o al pulsar Actualizar.", color = palette.inkSoft)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (onPickFolder != null) LibraryAction("Elegir carpeta…", "library_pick_folder") { onPickFolder { repository.library.addFolder(it) } }
            OutlinedTextField(path, { path = it }, label = { Text("Ruta o URI de carpeta") }, singleLine = true,
                modifier = Modifier.width(300.dp).shioriTextFieldFocusGuard().testTag("library_folder_path"))
            LibraryAction("Añadir carpeta", "library_add_folder", enabled = path.isNotBlank()) {
                runCatching { repository.library.addFolder(path) }; path = ""
            }
            LibraryAction("Actualizar", "library_sync", enabled = !progress.running) { scope.launch { repository.library.synchronizeFolders() } }
        }
        state.watchedFolders.forEach { folder ->
            Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                Text(folder.location, color = palette.ink)
                Text("Última sincronización: " + (folder.lastSyncEpochMs?.let { kotlin.time.Instant.fromEpochMilliseconds(it).toString() } ?: "Nunca"), color = palette.inkSoft, fontSize = 12.sp)
                folder.lastError?.let { Text(it, color = palette.warningAmber) }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    LibraryAction("Incluir subcarpetas", "library_recursive_${folder.id}", folder.includeSubfolders) { repository.library.updateFolder(folder.copy(includeSubfolders = !folder.includeSubfolders)) }
                    LibraryAction("Observar cambios (Desktop)", "library_watch_${folder.id}", folder.observeChanges) { repository.library.updateFolder(folder.copy(observeChanges = !folder.observeChanges)) }
                    LibraryAction("Dejar de sincronizar", "library_remove_folder_${folder.id}") { repository.library.removeFolder(folder.id) }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LibraryFileTools(repository: FolioRepository, palette: MeridianPalette, busy: Boolean,
    onPickFile: (((String) -> Unit) -> Unit)?, onPickExport: ((String, (String) -> Unit) -> Unit)?,
    onImport: (String) -> Unit, onCreated: () -> Unit, onMessage: (String) -> Unit,
) {
    var path by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }
    var author by remember { mutableStateOf("") }
    var format by remember { mutableStateOf(DocumentFormat.EPUB) }
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxWidth().background(palette.elevatedSurface).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Importar / Crear / Exportar", color = palette.ink, fontWeight = FontWeight.SemiBold)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(path, { path = it }, label = { Text("Ruta de archivo PDF o EPUB") }, singleLine = true,
                modifier = Modifier.width(300.dp).shioriTextFieldFocusGuard().testTag("library_file_path"))
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
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(title, { title = it }, label = { Text("Título") }, singleLine = true,
                modifier = Modifier.width(240.dp).shioriTextFieldFocusGuard().testTag("library_new_title"))
            OutlinedTextField(author, { author = it }, label = { Text("Autor") }, singleLine = true,
                modifier = Modifier.width(240.dp).shioriTextFieldFocusGuard().testTag("library_new_author"))
            DocumentFormat.entries.forEach { f -> LibraryAction(f.name, "library_new_format_${f.name}", f == format) { format = f } }
            LibraryAction("Crear", "library_create", enabled = title.isNotBlank()) { repository.createNewDocument(title, author, format); onCreated() }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LibraryBookItem(book: BookDocument, telemetry: BookReadingTelemetry?, palette: MeridianPalette,
    prefs: LibraryPreferences, selected: Boolean, selectionMode: Boolean, reminder: PageReminder?, onToggleSelection: () -> Unit, onOpen: () -> Unit,
    onExport: () -> Unit, onReload: () -> Unit, onEditMetadata: () -> Unit,
) {
    val detailed = prefs.density == LibraryDensity.DETAILED
    if (prefs.layout == LibraryLayout.CARDS) {
        LibraryBookCard(book, telemetry, palette, detailed, selected, selectionMode, reminder, onToggleSelection, onOpen, onExport, onReload, onEditMetadata)
        return
    }
    val prediction = remember(book, telemetry) { ReadingPredictionEngine.computePrediction(book, telemetry) }
    Surface(color = palette.pageSurface, shape = RoundedCornerShape(10.dp),
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) palette.accentGreen else palette.border),
        modifier = Modifier.fillMaxWidth().testTag("library_book_${book.id}")) {
        BoxWithConstraints {
        if (maxWidth >= 760.dp) {
            Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Checkbox(selected, { onToggleSelection() }, modifier = Modifier.testTag("library_select_${book.id}").semantics { contentDescription = "Seleccionar ${book.title}" })
                Column(Modifier.weight(1.2f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(book.title, color = palette.ink, fontFamily = FontFamily.Serif, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(book.author, color = palette.inkSoft, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (book.isMissingOnDisk) Text("No disponible en disco", color = palette.warningAmber, fontSize = 12.sp, modifier = Modifier.testTag("library_missing_${book.id}"))
                }
                if (detailed) Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    LibraryBookMetrics(book, telemetry, prediction, palette, reminder)
                }
                Column {
                    LibraryAction("Abrir", "library_open_${book.id}") { onOpen() }
                    LibraryAction("Metadatos", "library_edit_metadata_${book.id}") { onEditMetadata() }
                    if (detailed) FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        LibraryAction("Exportar", "library_export_${book.id}") { onExport() }
                        if (book.filePath != null) LibraryAction("Recargar", "library_reload_${book.id}", enabled = !book.isMissingOnDisk) { onReload() }
                    }
                }
            }
        } else Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(selected, { onToggleSelection() }, modifier = Modifier.testTag("library_select_${book.id}").semantics { contentDescription = "Seleccionar ${book.title}" })
                Column(Modifier.weight(1f)) {
                    Text(book.title, color = palette.ink, fontFamily = FontFamily.Serif, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(book.author, color = palette.inkSoft, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                LibraryAction("Abrir", "library_open_${book.id}") { onOpen() }
                LibraryAction("Metadatos", "library_edit_metadata_${book.id}") { onEditMetadata() }
            }
            if (detailed) {
                LibraryBookMetrics(book, telemetry, prediction, palette, reminder)
            }
            if (book.isMissingOnDisk) Text("No disponible en disco", color = palette.warningAmber, fontSize = 12.sp, modifier = Modifier.testTag("library_missing_${book.id}"))
            if (detailed) Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                LibraryAction("Exportar", "library_export_${book.id}") { onExport() }
                if (book.filePath != null) LibraryAction("Recargar", "library_reload_${book.id}", enabled = !book.isMissingOnDisk) { onReload() }
            }
        }
        }
    }
}

@Composable
private fun LibraryBookMetrics(book: BookDocument, telemetry: BookReadingTelemetry?, prediction: FinishTimePrediction,
    palette: MeridianPalette, reminder: PageReminder?,
) {
    Text("${book.format.name} · ${book.libraryStatus.label}", color = palette.inkSoft, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    LinearProgressIndicator(progress = { book.libraryProgress() }, modifier = Modifier.fillMaxWidth())
    Text("${(book.libraryProgress() * 100).roundToInt()}% · ${book.totalPages} " + if (book.format == DocumentFormat.EPUB) "págs. estimadas" else "páginas", color = palette.inkSoft, fontSize = 12.sp)
    Text("${prediction.formattedRemainingTime()} restantes · " + if ((telemetry?.totalActiveSeconds ?: 0.0) > 0) "Según tu ritmo" else "Estimado inicial", color = palette.inkSoft, fontSize = 12.sp)
    reminder?.let { Text("Recordatorio · pág. ${it.pageNumber}: ${it.note}", color = palette.inkSoft, fontSize = 12.sp) }
}

@Composable
internal fun LibraryCover(book: BookDocument, palette: MeridianPalette) {
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
    Box(Modifier.fillMaxSize().background(palette.elevatedSurface), contentAlignment = Alignment.Center) {
        val image = bitmap
        if (image != null) Image(image, "Portada de ${book.title}", Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else Column(
            Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Brush.verticalGradient(
                listOf(palette.accentGreen.copy(alpha = 0.15f), palette.pageSurface, palette.elevatedSurface)))
                .padding(horizontal = 22.dp, vertical = 48.dp),
            horizontalAlignment = Alignment.Start, verticalArrangement = Arrangement.Center,
        ) {
            Text(book.format.name + " /", color = palette.accentGreen, fontFamily = FontFamily.Monospace,
                fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
            Spacer(Modifier.height(18.dp))
            Text(book.title, color = palette.ink, fontFamily = FontFamily.Serif, fontSize = 21.sp,
                lineHeight = 27.sp, fontWeight = FontWeight.Medium, maxLines = 5, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(18.dp))
            HorizontalDivider(Modifier.width(32.dp), color = palette.accentGreen, thickness = 2.dp)
            Spacer(Modifier.height(12.dp))
            Text(book.author, color = palette.inkSoft, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}
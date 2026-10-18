package dev.meridian.folio.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.meridian.folio.core.AnchorStatus
import dev.meridian.folio.core.BackupPeek
import dev.meridian.folio.core.BlockStyle
import dev.meridian.folio.core.BookDocument
import dev.meridian.folio.core.BookTextSearch
import dev.meridian.folio.core.FolioRepository

/**
 * Non-destructive editing dialogs: search/replace, page correction, chapter
 * management, metadata/cover editing and annotation re-anchoring. All changes
 * live in the reader state and are materialized only on export.
 */

@Composable
internal fun SearchReplaceDialog(
    repository: FolioRepository,
    palette: MeridianPalette,
    book: BookDocument,
    onClose: () -> Unit,
    onStatus: (String) -> Unit,
) {
    val state by repository.state.collectAsState()
    val liveBook = state.documents.find { it.id == book.id } ?: book
    val textFieldColors = shioriOutlinedTextFieldColors(palette)
    var query by remember { mutableStateOf("") }
    var replacement by remember { mutableStateOf("") }
    var chapterScope by remember { mutableStateOf(false) }
    var ignoreCase by remember { mutableStateOf(true) }
    val currentChapterTitle = liveBook.pages.find { it.pageNumber == liveBook.currentPage }?.chapterTitle
    val scope = if (chapterScope) currentChapterTitle else null
    val matches = remember(liveBook.pages, query, scope, ignoreCase) {
        BookTextSearch.findMatches(liveBook.pages, query, scope, ignoreCase)
    }

    AlertDialog(
        onDismissRequest = onClose,
        modifier = Modifier.testTag("search_replace_dialog"),
        title = { Text("Buscar y reemplazar") },
        text = {
            Column(Modifier.widthIn(min = 320.dp).heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Buscar", fontSize = 12.sp) },
                    singleLine = true,
                    colors = textFieldColors,
                    modifier = Modifier.fillMaxWidth().shioriTextFieldFocusGuard().testTag("search_query_input"),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = replacement,
                    onValueChange = { replacement = it },
                    label = { Text("Reemplazar con", fontSize = 12.sp) },
                    singleLine = true,
                    colors = textFieldColors,
                    modifier = Modifier.fillMaxWidth().shioriTextFieldFocusGuard().testTag("search_replace_input"),
                )
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ModernChromeAction(
                        "Capítulo actual", palette, "search_scope_chapter", selected = chapterScope,
                        description = "Buscar solo en el capítulo actual",
                    ) { chapterScope = true }
                    ModernChromeAction(
                        "Todo el libro", palette, "search_scope_book", selected = !chapterScope,
                        description = "Buscar en todo el libro",
                    ) { chapterScope = false }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Switch(ignoreCase, { ignoreCase = it }, modifier = Modifier.testTag("search_ignore_case"))
                    Text("Ignorar mayúsculas y minúsculas", color = palette.inkSoft, fontSize = 12.sp)
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = if (query.isBlank()) {
                        "Escribe para previsualizar las coincidencias."
                    } else {
                        "${matches.size} coincidencia(s) en ${matches.map { it.pageNumber }.distinct().size} página(s)."
                    },
                    color = palette.inkSoft,
                    fontSize = 12.sp,
                    modifier = Modifier.testTag("search_result_summary"),
                )
                matches.take(40).forEachIndexed { index, match ->
                    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Text("Pág. ${match.pageNumber} · ${match.chapterTitle}", color = palette.muted, fontSize = 11.sp)
                        Text(
                            "…${match.snippetBefore}[${match.matchedText}]${match.snippetAfter}…",
                            color = palette.ink,
                            fontSize = 12.sp,
                        )
                        TextButton(
                            onClick = {
                                val replaced = repository.replaceTextAt(
                                    liveBook.id, match.pageNumber, match.blockId, match.startIndex,
                                    query, replacement, ignoreCase,
                                )
                                if (replaced) onStatus("Ocurrencia reemplazada en la pág. ${match.pageNumber}.")
                            },
                            modifier = Modifier.testTag("search_replace_single_$index"),
                        ) { Text("Reemplazar esta", fontSize = 12.sp) }
                    }
                    HorizontalDivider(color = palette.border)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val count = repository.replaceAllText(liveBook.id, query, replacement, scope, ignoreCase)
                    onStatus(if (count > 0) "$count reemplazo(s) aplicados." else "No había coincidencias que reemplazar.")
                },
                enabled = query.isNotBlank() && matches.isNotEmpty(),
                modifier = Modifier.heightIn(min = 48.dp).testTag("search_replace_all"),
            ) { Text("Reemplazar todo") }
        },
        dismissButton = {
            OutlinedButton(onClick = onClose, modifier = Modifier.heightIn(min = 48.dp).testTag("search_close_btn")) {
                Text("Cerrar")
            }
        },
    )
}

@Composable
internal fun PageEditorDialog(
    repository: FolioRepository,
    palette: MeridianPalette,
    book: BookDocument,
    onClose: () -> Unit,
    onStatus: (String) -> Unit,
) {
    val state by repository.state.collectAsState()
    val liveBook = state.documents.find { it.id == book.id } ?: book
    val page = liveBook.pages.find { it.pageNumber == liveBook.currentPage } ?: liveBook.pages.firstOrNull() ?: return
    val textFieldColors = shioriOutlinedTextFieldColors(palette)
    var chapterTitle by remember(page.pageNumber, page.chapterTitle) { mutableStateOf(page.chapterTitle) }
    var blocks by remember(page.pageNumber, page.blocks) { mutableStateOf(page.blocks) }

    AlertDialog(
        onDismissRequest = onClose,
        modifier = Modifier.testTag("page_editor_dialog"),
        title = { Text("Editar página ${page.pageNumber}") },
        text = {
            Column(Modifier.widthIn(min = 320.dp).heightIn(max = 440.dp).verticalScroll(rememberScrollState())) {
                Text("Los cambios se guardan en tu biblioteca; el archivo original no se modifica.", color = palette.inkSoft, fontSize = 12.sp)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = chapterTitle,
                    onValueChange = { chapterTitle = it },
                    label = { Text("Título del capítulo", fontSize = 12.sp) },
                    singleLine = true,
                    colors = textFieldColors,
                    modifier = Modifier.fillMaxWidth().shioriTextFieldFocusGuard().testTag("page_editor_chapter_input"),
                )
                Spacer(Modifier.height(8.dp))
                blocks.forEachIndexed { index, block ->
                    if (block.style == BlockStyle.IMAGE) {
                        Text("🖼 Ilustración: ${block.text}", color = palette.muted, fontSize = 12.sp)
                    } else {
                        Text("${block.style.name} #${index + 1}", color = palette.muted, fontSize = 11.sp)
                        OutlinedTextField(
                            value = block.text,
                            onValueChange = { updated ->
                                blocks = blocks.toMutableList().also { it[index] = block.copy(text = updated) }
                            },
                            maxLines = 6,
                            colors = textFieldColors,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 56.dp)
                                .shioriTextFieldFocusGuard()
                                .testTag("page_editor_block_$index"),
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    repository.updatePageContent(liveBook.id, page.pageNumber, chapterTitle, blocks)
                    onStatus("Página ${page.pageNumber} actualizada.")
                    onClose()
                },
                modifier = Modifier.heightIn(min = 48.dp).testTag("page_editor_save_btn"),
            ) { Text("Guardar página") }
        },
        dismissButton = {
            OutlinedButton(onClick = onClose, modifier = Modifier.heightIn(min = 48.dp).testTag("page_editor_cancel_btn")) {
                Text("Cancelar")
            }
        },
    )
}

@Composable
internal fun ChapterManagerDialog(
    repository: FolioRepository,
    palette: MeridianPalette,
    book: BookDocument,
    onClose: () -> Unit,
    onStatus: (String) -> Unit,
) {
    val state by repository.state.collectAsState()
    val liveBook = state.documents.find { it.id == book.id } ?: book
    val toc = liveBook.tableOfContents
    val textFieldColors = shioriOutlinedTextFieldColors(palette)
    var editingStartPage by remember { mutableStateOf<Int?>(null) }
    var editText by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onClose,
        modifier = Modifier.testTag("chapter_manager_dialog"),
        title = { Text("Capítulos (${toc.size})") },
        text = {
            Column(Modifier.widthIn(min = 320.dp).heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                Text("Renombra, reordena o reconstruye el índice a partir de los títulos del contenido.", color = palette.inkSoft, fontSize = 12.sp)
                Spacer(Modifier.height(6.dp))
                toc.forEachIndexed { index, entry ->
                    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp).testTag("chapter_row_${entry.startPage}")) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    entry.title.ifBlank { "Capítulo ${index + 1}" },
                                    color = palette.ink,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text("Pág. ${entry.startPage}", color = palette.muted, fontSize = 11.sp)
                            }
                            ModernChromeAction(
                                "↑", palette, "chapter_move_up_${entry.startPage}", enabled = index > 0,
                                description = "Mover capítulo arriba",
                            ) {
                                editingStartPage = null
                                if (repository.moveChapter(liveBook.id, entry.startPage, upwards = true)) {
                                    onStatus("Capítulo movido hacia arriba.")
                                }
                            }
                            ModernChromeAction(
                                "↓", palette, "chapter_move_down_${entry.startPage}", enabled = index < toc.size - 1,
                                description = "Mover capítulo abajo",
                            ) {
                                editingStartPage = null
                                if (repository.moveChapter(liveBook.id, entry.startPage, upwards = false)) {
                                    onStatus("Capítulo movido hacia abajo.")
                                }
                            }
                            ModernChromeAction("Renombrar", palette, "chapter_rename_${entry.startPage}") {
                                editingStartPage = entry.startPage
                                editText = entry.title
                            }
                        }
                        if (editingStartPage == entry.startPage) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.padding(top = 4.dp),
                            ) {
                                OutlinedTextField(
                                    value = editText,
                                    onValueChange = { editText = it },
                                    singleLine = true,
                                    colors = textFieldColors,
                                    modifier = Modifier
                                        .weight(1f)
                                        .shioriTextFieldFocusGuard()
                                        .testTag("chapter_rename_input_${entry.startPage}"),
                                )
                                Button(
                                    onClick = {
                                        if (repository.renameChapter(liveBook.id, entry.startPage, editText)) {
                                            onStatus("Capítulo renombrado.")
                                        }
                                        editingStartPage = null
                                    },
                                    modifier = Modifier.testTag("chapter_rename_save_${entry.startPage}"),
                                ) { Text("Guardar", fontSize = 12.sp) }
                            }
                        }
                    }
                    HorizontalDivider(color = palette.border)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (repository.repairTableOfContents(liveBook.id)) {
                        onStatus("Índice reconstruido desde los títulos del contenido.")
                    } else {
                        onStatus("El índice ya está coherente; no hubo cambios.")
                    }
                },
                modifier = Modifier.heightIn(min = 48.dp).testTag("chapter_repair_toc_btn"),
            ) { Text("Reparar TOC") }
        },
        dismissButton = {
            OutlinedButton(onClick = onClose, modifier = Modifier.heightIn(min = 48.dp).testTag("chapter_close_btn")) {
                Text("Cerrar")
            }
        },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun BookMetadataDialog(
    palette: MeridianPalette,
    book: BookDocument,
    coverPicker: (((String) -> Unit) -> Unit)?,
    onSave: (title: String, author: String, language: String, coverImagePath: String?) -> Unit,
    onClose: () -> Unit,
) {
    val textFieldColors = shioriOutlinedTextFieldColors(palette)
    var title by remember(book.id, book.title) { mutableStateOf(book.title) }
    var author by remember(book.id, book.author) { mutableStateOf(book.author) }
    var language by remember(book.id, book.language) { mutableStateOf(book.language) }
    var coverPath by remember(book.id, book.coverImagePath) { mutableStateOf(book.coverImagePath) }
    val bookImages = remember(book.id, book.pages) {
        book.pages.flatMap { it.blocks }
            .filter { it.style == BlockStyle.IMAGE }
            .mapNotNull { it.imagePath }
            .distinct()
            .take(10)
    }

    AlertDialog(
        onDismissRequest = onClose,
        modifier = Modifier.testTag("book_metadata_dialog"),
        title = { Text("Editar metadatos") },
        text = {
            Column(
                Modifier.widthIn(min = 320.dp).heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Título", fontSize = 12.sp) },
                    singleLine = true,
                    colors = textFieldColors,
                    modifier = Modifier.fillMaxWidth().shioriTextFieldFocusGuard().testTag("metadata_title_input"),
                )
                OutlinedTextField(
                    value = author,
                    onValueChange = { author = it },
                    label = { Text("Autor", fontSize = 12.sp) },
                    singleLine = true,
                    colors = textFieldColors,
                    modifier = Modifier.fillMaxWidth().shioriTextFieldFocusGuard().testTag("metadata_author_input"),
                )
                OutlinedTextField(
                    value = language,
                    onValueChange = { language = it },
                    label = { Text("Idioma (p. ej. es, en)", fontSize = 12.sp) },
                    singleLine = true,
                    colors = textFieldColors,
                    modifier = Modifier.fillMaxWidth().shioriTextFieldFocusGuard().testTag("metadata_language_input"),
                )
                Text("Portada", color = palette.ink, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                if (bookImages.isEmpty() && coverPath.isNullOrBlank() && coverPicker == null) {
                    Text("Este libro no tiene imágenes incrustadas disponibles para usar como portada.", color = palette.inkSoft, fontSize = 12.sp)
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    bookImages.forEachIndexed { index, path ->
                        ModernChromeAction(
                            "Imagen ${index + 1}", palette, "metadata_cover_pick_$index", selected = coverPath == path,
                            description = "Usar la imagen ${index + 1} del libro como portada",
                        ) { coverPath = path }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (coverPicker != null) {
                        ModernChromeAction("Elegir archivo…", palette, "metadata_cover_file_btn", description = "Elegir una imagen del dispositivo como portada") {
                            coverPicker { picked -> coverPath = picked }
                        }
                    }
                    if (!coverPath.isNullOrBlank()) {
                        ModernChromeAction("Quitar portada", palette, "metadata_clear_cover_btn") { coverPath = null }
                    }
                }
                Text(
                    "La exportación reescribe título, autor, idioma y portada en el archivo generado.",
                    color = palette.inkSoft, fontSize = 11.5.sp,
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(title, author, language, coverPath); onClose() },
                modifier = Modifier.heightIn(min = 48.dp).testTag("metadata_save_btn"),
            ) { Text("Guardar") }
        },
        dismissButton = {
            OutlinedButton(onClick = onClose, modifier = Modifier.heightIn(min = 48.dp).testTag("metadata_cancel_btn")) {
                Text("Cancelar")
            }
        },
    )
}

@Composable
internal fun ReattachAnnotationDialog(
    repository: FolioRepository,
    palette: MeridianPalette,
    phraseId: String,
    onClose: () -> Unit,
    onStatus: (String) -> Unit,
) {
    val state by repository.state.collectAsState()
    val phrase = state.savedPhrases.find { it.id == phraseId }
    val book = phrase?.let { saved -> state.documents.find { it.id == saved.bookId } }
    val matches = remember(book?.id, phrase?.phrase, book?.pages) {
        if (book == null || phrase == null) emptyList() else BookTextSearch.findMatches(book.pages, phrase.phrase).take(15)
    }

    AlertDialog(
        onDismissRequest = onClose,
        modifier = Modifier.testTag("reattach_dialog"),
        title = { Text("Reanclar anotación") },
        text = {
            Column(Modifier.widthIn(min = 320.dp).heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                if (phrase == null) {
                    Text("La anotación ya no existe en tu cuaderno.", color = palette.inkSoft)
                } else {
                    Text("«${phrase.phrase}»", color = palette.ink, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Spacer(Modifier.height(6.dp))
                    when {
                        book == null -> Text(
                            "El libro de esta anotación ya no está en la biblioteca. Reimporta el archivo para volver a anclarla.",
                            color = palette.warningAmber, fontSize = 12.5.sp,
                        )
                        matches.isEmpty() -> Text(
                            "No se encontró ese texto en el libro actual. Corrige la frase desde el lector o elimina la anotación.",
                            color = palette.warningAmber, fontSize = 12.5.sp,
                        )
                        else -> {
                            Text("Elige la ocurrencia correcta:", color = palette.inkSoft, fontSize = 12.sp)
                            matches.forEachIndexed { index, match ->
                                Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                    Text("Pág. ${match.pageNumber} · ${match.chapterTitle}", color = palette.muted, fontSize = 11.sp)
                                    Text(
                                        "…${match.snippetBefore}[${match.matchedText}]${match.snippetAfter}…",
                                        color = palette.ink, fontSize = 12.sp,
                                    )
                                    Button(
                                        onClick = {
                                            repository.reattachPhrase(
                                                phraseId = phrase.id,
                                                pageNumber = match.pageNumber,
                                                chapterTitle = match.chapterTitle,
                                                anchorPrefix = match.snippetBefore,
                                                anchorSuffix = match.snippetAfter,
                                            )
                                            onStatus("Anotación reanclada en la pág. ${match.pageNumber}.")
                                            onClose()
                                        },
                                        modifier = Modifier.heightIn(min = 48.dp).testTag("reattach_match_$index"),
                                    ) { Text("Reanclar aquí", fontSize = 12.sp) }
                                }
                                HorizontalDivider(color = palette.border)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            OutlinedButton(onClick = onClose, modifier = Modifier.heightIn(min = 48.dp).testTag("reattach_cancel_btn")) {
                Text("Cancelar")
            }
        },
    )
}

@Composable
internal fun BackupImportConfirmDialog(
    palette: MeridianPalette,
    peek: BackupPeek,
    onConfirm: () -> Unit,
    onClose: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onClose,
        modifier = Modifier.testTag("backup_import_confirm_dialog"),
        title = { Text("Restaurar respaldo") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("El respaldo contiene:", color = palette.ink, fontSize = 13.sp)
                Text("• ${peek.documentCount} libro(s) con huella verificable", color = palette.inkSoft, fontSize = 12.5.sp)
                Text("• ${peek.phraseCount} anotación(es) y ${peek.collectionCount} colección(es)", color = palette.inkSoft, fontSize = 12.5.sp)
                Text("• ${peek.reminderCount} recordatorio(s)", color = palette.inkSoft, fontSize = 12.5.sp)
                Spacer(Modifier.height(4.dp))
                Text(
                    "El progreso, las notas, los recordatorios y las preferencias se aplicarán a los libros presentes en este dispositivo. " +
                        "Los libros ausentes se informarán al terminar y tus datos actuales no se eliminan.",
                    color = palette.inkSoft, fontSize = 12.sp,
                )
            }
        },
        confirmButton = {
            Button(onClick = onConfirm, modifier = Modifier.heightIn(min = 48.dp).testTag("backup_import_confirm")) {
                Text("Restaurar")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onClose, modifier = Modifier.heightIn(min = 48.dp).testTag("backup_import_cancel")) {
                Text("Cancelar")
            }
        },
    )
}

@Composable
internal fun BackupPathDialog(
    title: String,
    palette: MeridianPalette,
    onConfirm: (String) -> Unit,
    onClose: () -> Unit,
) {
    var path by remember { mutableStateOf("shiorifolio-backup.json") }
    AlertDialog(
        onDismissRequest = onClose,
        modifier = Modifier.testTag("backup_path_dialog"),
        title = { Text(title) },
        text = {
            Column {
                Text(
                    "Escribe la ruta completa del archivo .json (por ejemplo, dentro de tu carpeta de Documentos).",
                    color = palette.inkSoft, fontSize = 12.sp,
                )
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = path,
                    onValueChange = { path = it },
                    singleLine = true,
                    colors = shioriOutlinedTextFieldColors(palette),
                    modifier = Modifier.fillMaxWidth().shioriTextFieldFocusGuard().testTag("backup_path_input"),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(path) },
                enabled = path.isNotBlank(),
                modifier = Modifier.heightIn(min = 48.dp).testTag("backup_path_confirm"),
            ) { Text("Continuar") }
        },
        dismissButton = {
            OutlinedButton(onClick = onClose, modifier = Modifier.heightIn(min = 48.dp).testTag("backup_path_cancel")) {
                Text("Cancelar")
            }
        },
    )
}

package dev.meridian.folio.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.meridian.folio.core.AnchorStatus
import dev.meridian.folio.core.BookDocument
import dev.meridian.folio.core.FolioRepository
import dev.meridian.folio.core.HighlightTone
import dev.meridian.folio.core.PlatformFileAndZipIo
import dev.meridian.folio.core.SavedPhrase
import dev.meridian.folio.ui.theme.ClayChip
import dev.meridian.folio.ui.theme.ClayChromeAction
import dev.meridian.folio.ui.theme.ClayPalette
import dev.meridian.folio.ui.theme.ClayPrimaryButton
import dev.meridian.folio.ui.theme.ClayRadius
import dev.meridian.folio.ui.theme.ClaySecondaryButton
import dev.meridian.folio.ui.theme.ClaySpacing
import dev.meridian.folio.ui.theme.LocalClayTypography
import dev.meridian.folio.ui.theme.clayTextFieldColors

/** Width of the capture column when the form is open (desktop two-column layout). */
private val NotebookCaptureWidth = 340.dp

/**
 * Relative age of a saved entry from its real `createdAtMs`. Deliberately coarse (minutes,
 * hours, days): the notebook never fabricates timestamps the model does not carry.
 */
private fun notebookRelativeTime(createdAtMs: Long, nowMs: Long): String {
    val minutes = ((nowMs - createdAtMs).coerceAtLeast(0L)) / 60_000L
    return when {
        minutes < 1L -> "Ahora mismo"
        minutes < 60L -> "Hace $minutes min"
        minutes < 60L * 24L -> "Hace ${minutes / 60L} h"
        else -> "Hace ${minutes / (60L * 24L)} d"
    }
}

/**
 * Notebook surface (LEXICON section): real saved phrases with their anchor state, a capture
 * form backed by [FolioRepository.saveWordOrPhrase] and the same re-anchor flow used by
 * `EditingWorkspace`.
 *
 * Contract kept from the previous vault: the `lexicon_search_input` and
 * `reattach_phrase_<id>` tags, and the visible [AnchorStatus.ORPHANED] `Huérfana` badge.
 * The chrome is always the light clay palette; highlight tones only accent each entry.
 * Mock-only affordances (pin, TTS, export, fake sync, spaced repetition) are absent because
 * the model has no backing for them.
 */
@OptIn(ExperimentalLayoutApi::class)
@Suppress("DEPRECATION")
@Composable
internal fun SavedPhrasesVaultView(
    repository: FolioRepository,
    palette: ClayPalette,
    onOpenReader: () -> Unit,
    onReattachPhrase: (String) -> Unit,
) {
    val state by repository.state.collectAsState()
    val phrases = state.savedPhrases
    val books = state.documents
    val typography = LocalClayTypography.current
    val textFieldColors = clayTextFieldColors(palette)
    val clipboard = LocalClipboardManager.current
    val nowMs = remember { PlatformFileAndZipIo.currentEpochMillis() }
    val activeBookId = state.selectedBookId ?: books.firstOrNull()?.id

    var searchQuery by remember { mutableStateOf("") }
    var showFilters by remember { mutableStateOf(false) }
    var showCapture by remember { mutableStateOf(false) }
    var toneFilter by remember { mutableStateOf<HighlightTone?>(null) }
    var statusFilter by remember { mutableStateOf<AnchorStatus?>(null) }
    var captureBookId by remember { mutableStateOf(activeBookId.orEmpty()) }
    var captureText by remember { mutableStateOf("") }
    var captureNote by remember { mutableStateOf("") }
    var captureTone by remember { mutableStateOf(HighlightTone.SAGE) }
    var feedback by remember { mutableStateOf<String?>(null) }

    val captureBook = books.find { it.id == captureBookId }
        ?: books.find { it.id == activeBookId }
        ?: books.firstOrNull()
    val hasActiveCriteria = searchQuery.isNotBlank() || toneFilter != null || statusFilter != null
    val filtered = remember(phrases, searchQuery, toneFilter, statusFilter) {
        val query = searchQuery.trim().lowercase()
        phrases.filter { item ->
            (toneFilter == null || item.tone == toneFilter) &&
                (statusFilter == null || item.anchorStatus == statusFilter) &&
                (
                    query.isEmpty() ||
                        item.phrase.lowercase().contains(query) ||
                        item.note.lowercase().contains(query) ||
                        item.bookTitle.lowercase().contains(query) ||
                        item.contextSentence.lowercase().contains(query)
                    )
        }
    }

    fun clearCriteria() {
        searchQuery = ""
        toneFilter = null
        statusFilter = null
    }

    fun saveCapture() {
        val book = captureBook ?: return
        val entry = captureText.trim()
        if (entry.isEmpty()) return
        val page = book.pages.find { it.pageNumber == book.currentPage }
        val context = page?.blocks?.firstOrNull { it.text.contains(entry, ignoreCase = true) }?.text
            ?: page?.blocks?.firstOrNull { it.text.isNotBlank() }?.text
            ?: entry
        val saved = repository.saveWordOrPhrase(
            bookId = book.id,
            pageNumber = book.currentPage,
            phrase = entry,
            contextSentence = context,
            note = captureNote.trim(),
            tone = captureTone,
        )
        if (saved != null) {
            captureText = ""
            captureNote = ""
            feedback = "Añadido «${saved.phrase}» a tu cuaderno."
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.canvas)
            .padding(horizontal = ClaySpacing.lg, vertical = ClaySpacing.md),
        verticalArrangement = Arrangement.spacedBy(ClaySpacing.sm),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("CUADERNO DE LECTURA", color = palette.textMuted, style = typography.labelSm)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(ClaySpacing.sm),
            ) {
                Text(
                    "Palabras Guardadas",
                    color = palette.onSurface,
                    style = typography.headlineLarge,
                    modifier = Modifier.testTag("notebook_header"),
                )
                NotebookCountPill(total = phrases.size, palette = palette)
            }
            Text(
                "Tu léxico personal, anclado a la página donde lo encontraste.",
                color = palette.onSurfaceVariant,
                style = typography.bodyMd,
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ClaySpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = { Text("Buscar palabra, nota o libro…") },
                singleLine = true,
                colors = textFieldColors,
                modifier = Modifier
                    .weight(1f)
                    .widthIn(max = 380.dp)
                    .shioriTextFieldFocusGuard()
                    .testTag("lexicon_search_input"),
            )
            ClayChip(
                label = "Filtrar",
                selected = showFilters || toneFilter != null || statusFilter != null,
                onClick = { showFilters = !showFilters },
            )
            Text(
                "${filtered.size} de ${phrases.size}",
                color = palette.textMuted,
                style = typography.labelMd,
            )
            ClayPrimaryButton(
                label = "+ Nueva entrada",
                onClick = { showCapture = !showCapture },
                tag = "notebook_new_entry_btn",
            )
        }

        feedback?.let { message ->
            Text(
                "✓ $message",
                color = palette.onSurfaceVariant,
                style = typography.bodySm,
            )
        }

        if (showFilters) {
            NotebookFilterPanel(
                palette = palette,
                toneFilter = toneFilter,
                statusFilter = statusFilter,
                onToneFilter = { toneFilter = it },
                onStatusFilter = { statusFilter = it },
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalArrangement = Arrangement.spacedBy(ClaySpacing.md),
        ) {
            if (showCapture) {
                // The scroll lives outside the card so the clay surface grows with the form
                // (a card clipped to the viewport would paint no background below the fold).
                Column(
                    modifier = Modifier
                        .width(NotebookCaptureWidth)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState()),
                ) {
                    NotebookCapturePanel(
                        palette = palette,
                        books = books,
                        selectedBook = captureBook,
                        onSelectBook = { captureBookId = it },
                        text = captureText,
                        onTextChange = { captureText = it },
                        note = captureNote,
                        onNoteChange = { captureNote = it },
                        tone = captureTone,
                        onToneChange = { captureTone = it },
                        onSave = ::saveCapture,
                        onClose = { showCapture = false },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(ClaySpacing.md),
            ) {
                if (filtered.isEmpty()) {
                    NotebookEmptyState(
                        palette = palette,
                        hasEntries = phrases.isNotEmpty(),
                        hasActiveCriteria = hasActiveCriteria,
                        onClearCriteria = ::clearCriteria,
                        onOpenReader = onOpenReader,
                    )
                } else {
                    filtered.forEach { item ->
                        NotebookEntryCard(
                            item = item,
                            palette = palette,
                            nowMs = nowMs,
                            onOpenPage = {
                                repository.jumpToLocation(
                                    item.bookId,
                                    item.pageNumber,
                                    "Cita: ${item.phrase.take(24)}",
                                )
                                onOpenReader()
                            },
                            onCopy = {
                                clipboard.setText(AnnotatedString(item.phrase))
                                feedback = "Frase copiada al portapapeles."
                            },
                            onDelete = {
                                repository.deleteSavedPhrase(item.id)
                                feedback = "Entrada eliminada del cuaderno."
                            },
                            onReattach = { onReattachPhrase(item.id) },
                        )
                    }
                }
            }
        }
    }
}

/** Real entry count of the notebook, in the header capsule. */
@Composable
private fun NotebookCountPill(total: Int, palette: ClayPalette) {
    val shape = RoundedCornerShape(percent = 50)
    Box(
        modifier = Modifier
            .clip(shape)
            .background(palette.primaryContainer)
            .border(1.dp, palette.primary.copy(alpha = 0.35f), shape)
            .padding(horizontal = ClaySpacing.sm, vertical = 4.dp)
            .testTag("notebook_count"),
    ) {
        Text(
            if (total == 1) "1 guardada" else "$total guardadas",
            color = palette.onPrimaryContainer,
            style = LocalClayTypography.current.labelMd,
        )
    }
}

/** Filter chips for the only two real dimensions of a [SavedPhrase]: tone and anchor status. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NotebookFilterPanel(
    palette: ClayPalette,
    toneFilter: HighlightTone?,
    statusFilter: AnchorStatus?,
    onToneFilter: (HighlightTone?) -> Unit,
    onStatusFilter: (AnchorStatus?) -> Unit,
) {
    val typography = LocalClayTypography.current
    ClayContentCard(Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(ClaySpacing.md),
            verticalArrangement = Arrangement.spacedBy(ClaySpacing.xs),
        ) {
            Text("Filtrar entradas", color = palette.onSurface, style = typography.titleMd)
            Text("TONO DEL RESALTADOR", color = palette.textMuted, style = typography.labelSm)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(ClaySpacing.xs),
                verticalArrangement = Arrangement.spacedBy(ClaySpacing.xs),
            ) {
                ClayChip(
                    label = "Todos",
                    selected = toneFilter == null,
                    onClick = { onToneFilter(null) },
                    tag = "notebook_filter_TONE_ALL",
                )
                HighlightTone.entries.forEach { tone ->
                    ClayChip(
                        label = tone.label,
                        selected = toneFilter == tone,
                        onClick = { onToneFilter(if (toneFilter == tone) null else tone) },
                        tag = "notebook_filter_TONE_${tone.name}",
                    )
                }
            }
            Text("ANCLAJE", color = palette.textMuted, style = typography.labelSm)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(ClaySpacing.xs),
                verticalArrangement = Arrangement.spacedBy(ClaySpacing.xs),
            ) {
                ClayChip(
                    label = "Todas",
                    selected = statusFilter == null,
                    onClick = { onStatusFilter(null) },
                    tag = "notebook_filter_STATUS_ALL",
                )
                AnchorStatus.entries.forEach { status ->
                    ClayChip(
                        label = status.label,
                        selected = statusFilter == status,
                        onClick = { onStatusFilter(if (statusFilter == status) null else status) },
                        tag = "notebook_filter_STATUS_${status.name}",
                    )
                }
            }
        }
    }
}

/**
 * Capture form backed only by real data: the book list comes from persisted documents and the
 * save call goes through [FolioRepository.saveWordOrPhrase] with the selected book's current
 * page. The save button stays disabled without a book or text.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NotebookCapturePanel(
    palette: ClayPalette,
    books: List<BookDocument>,
    selectedBook: BookDocument?,
    onSelectBook: (String) -> Unit,
    text: String,
    onTextChange: (String) -> Unit,
    note: String,
    onNoteChange: (String) -> Unit,
    tone: HighlightTone,
    onToneChange: (HighlightTone) -> Unit,
    onSave: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val typography = LocalClayTypography.current
    val textFieldColors = clayTextFieldColors(palette)
    var bookMenuOpen by remember { mutableStateOf(false) }

    ClayContentCard(modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(ClaySpacing.md),
            verticalArrangement = Arrangement.spacedBy(ClaySpacing.sm),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text("Nueva entrada", color = palette.onSurface, style = typography.titleMd)
                    Text("Captura manual", color = palette.textMuted, style = typography.bodySm)
                }
                ClaySecondaryButton(label = "Ocultar", onClick = onClose)
            }

            Text("CONTEXTO LECTOR", color = palette.textMuted, style = typography.labelSm)
            Box {
                ClaySecondaryButton(
                    label = selectedBook?.title ?: "Sin libro importado",
                    onClick = { bookMenuOpen = true },
                    tag = "notebook_capture_book",
                    enabled = books.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth(),
                )
                DropdownMenu(
                    expanded = bookMenuOpen,
                    onDismissRequest = { bookMenuOpen = false },
                ) {
                    books.forEach { book ->
                        DropdownMenuItem(
                            text = { Text(book.title) },
                            onClick = {
                                onSelectBook(book.id)
                                bookMenuOpen = false
                            },
                        )
                    }
                }
            }
            Text(
                if (selectedBook != null) {
                    "Se guardará en la página ${selectedBook.currentPage} de la lectura."
                } else {
                    "Importa un libro para capturar entradas."
                },
                color = palette.onSurfaceVariant,
                style = typography.bodySm,
            )

            OutlinedTextField(
                value = text,
                onValueChange = onTextChange,
                label = { Text("Palabra o Frase") },
                singleLine = true,
                colors = textFieldColors,
                modifier = Modifier
                    .fillMaxWidth()
                    .shioriTextFieldFocusGuard()
                    .testTag("notebook_capture_text"),
            )
            OutlinedTextField(
                value = note,
                onValueChange = onNoteChange,
                label = { Text("Definición o Nota personal") },
                singleLine = true,
                colors = textFieldColors,
                modifier = Modifier
                    .fillMaxWidth()
                    .shioriTextFieldFocusGuard()
                    .testTag("notebook_capture_note"),
            )

            Text("TONO DE RESALTADOR", color = palette.textMuted, style = typography.labelSm)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(ClaySpacing.xs),
                verticalArrangement = Arrangement.spacedBy(ClaySpacing.xs),
            ) {
                HighlightTone.entries.forEach { option ->
                    ClayChip(
                        label = option.label,
                        selected = tone == option,
                        onClick = { onToneChange(option) },
                        tag = "notebook_capture_tone_${option.name}",
                    )
                }
            }

            ClayPrimaryButton(
                label = "+ Guardar Palabra / Frase",
                onClick = onSave,
                tag = "notebook_capture_save",
                enabled = selectedBook != null && text.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** Empty notebook or empty result set; both paths offer a real next action. */
@Composable
private fun NotebookEmptyState(
    palette: ClayPalette,
    hasEntries: Boolean,
    hasActiveCriteria: Boolean,
    onClearCriteria: () -> Unit,
    onOpenReader: () -> Unit,
) {
    val typography = LocalClayTypography.current
    ClayContentCard(Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(ClaySpacing.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(ClaySpacing.sm),
        ) {
            if (hasEntries) {
                Text("Sin coincidencias", color = palette.onSurface, style = typography.headlineMd)
                Text(
                    "Prueba con otra búsqueda o quita los filtros de tono y anclaje.",
                    color = palette.onSurfaceVariant,
                    style = typography.bodyMd,
                    textAlign = TextAlign.Center,
                )
                if (hasActiveCriteria) {
                    ClaySecondaryButton(label = "Limpiar filtros", onClick = onClearCriteria)
                }
            } else {
                Text("❝", color = palette.primary, style = typography.headlineLarge)
                Text("¿Leyendo en el Reader?", color = palette.onSurface, style = typography.headlineMd)
                Text(
                    "Marca una palabra o frase con «+ Frase» mientras lees y aparecerá aquí, " +
                        "con su página, su contexto y tu nota.",
                    color = palette.onSurfaceVariant,
                    style = typography.bodyMd,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.widthIn(max = 460.dp),
                )
                ClayPrimaryButton(label = "Ir al Reader", onClick = onOpenReader)
            }
        }
    }
}

/** One saved entry: real phrase, tone accent, anchor state, source and actions. */
@Composable
private fun NotebookEntryCard(
    item: SavedPhrase,
    palette: ClayPalette,
    nowMs: Long,
    onOpenPage: () -> Unit,
    onCopy: () -> Unit,
    onDelete: () -> Unit,
    onReattach: () -> Unit,
) {
    val typography = LocalClayTypography.current
    ClayContentCard(Modifier.fillMaxWidth().testTag("notebook_entry_${item.id}")) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(ClaySpacing.md),
            verticalArrangement = Arrangement.spacedBy(ClaySpacing.xs),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(ClayRadius.sm))
                        .background(palette.highlightColor(item.tone))
                        .padding(horizontal = ClaySpacing.sm, vertical = ClaySpacing.xs),
                ) {
                    Text(
                        "«${item.phrase}»",
                        color = palette.onSurface,
                        style = typography.titleMd,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.width(ClaySpacing.sm))
                AnchorStatusChip(status = item.anchorStatus, palette = palette)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(
                    Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(palette.highlightColor(item.tone)),
                )
                Text(
                    item.tone.label,
                    color = palette.textMuted,
                    style = typography.labelSm,
                )
                Text(
                    "${item.bookTitle} · Pág. ${item.pageNumber}",
                    color = palette.onSurfaceVariant,
                    style = typography.bodySm,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Text(
                    "· ${notebookRelativeTime(item.createdAtMs, nowMs)}",
                    color = palette.textMuted,
                    style = typography.bodySm,
                )
            }

            if (item.contextSentence.isNotBlank()) {
                Text(
                    "CONTEXTO EXTRAÍDO DE LA LECTURA",
                    color = palette.textMuted,
                    style = typography.labelSm,
                )
                Text(
                    item.contextSentence,
                    color = palette.onSurfaceVariant,
                    style = typography.bodyMd,
                    fontStyle = FontStyle.Italic,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            if (item.note.isNotBlank()) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("NOTA:", color = palette.textMuted, style = typography.labelSm)
                    Text(item.note, color = palette.onSurface, style = typography.bodyMd)
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(ClaySpacing.xs),
            ) {
                if (item.anchorStatus == AnchorStatus.ORPHANED) {
                    ClayChromeAction(
                        label = "Reanclar",
                        palette = palette,
                        tag = "reattach_phrase_${item.id}",
                        description = "Reanclar la anotación huérfana a un nuevo pasaje",
                        onClick = onReattach,
                    )
                }
                ClayChromeAction(
                    label = "Abrir Página ${item.pageNumber}",
                    palette = palette,
                    tag = "phrase_open_page_${item.id}",
                    description = "Abrir la página ${item.pageNumber} de ${item.bookTitle}",
                    onClick = onOpenPage,
                )
                ClayChromeAction(
                    label = "Copiar",
                    palette = palette,
                    tag = "phrase_copy_${item.id}",
                    description = "Copiar la frase al portapapeles",
                    onClick = onCopy,
                )
                ClayChromeAction(
                    label = "Eliminar",
                    palette = palette,
                    tag = "phrase_delete_${item.id}",
                    description = "Eliminar esta entrada del cuaderno",
                    onClick = onDelete,
                )
            }
        }
    }
}

/** Real anchor state of the entry; orphaned entries keep the visible `Huérfana` label. */
@Composable
private fun AnchorStatusChip(status: AnchorStatus, palette: ClayPalette) {
    val orphaned = status == AnchorStatus.ORPHANED
    val shape = RoundedCornerShape(percent = 50)
    Box(
        modifier = Modifier
            .clip(shape)
            .background(
                if (orphaned) palette.warningAmber.copy(alpha = 0.18f) else palette.successMint.copy(alpha = 0.16f),
            )
            .border(1.dp, if (orphaned) palette.warningAmber else palette.successMint, shape)
            .padding(horizontal = 10.dp, vertical = 4.dp)
            .semantics {
                contentDescription = if (orphaned) {
                    "Anotación huérfana: el texto anclado ya no coincide con la página"
                } else {
                    "Anotación anclada a la página"
                }
            },
    ) {
        Text(
            status.label,
            color = if (orphaned) palette.onWarningAmber else palette.onSuccessMint,
            style = LocalClayTypography.current.labelSm,
        )
    }
}

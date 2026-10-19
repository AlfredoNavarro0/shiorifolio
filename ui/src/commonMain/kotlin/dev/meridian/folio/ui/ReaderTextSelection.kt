package dev.meridian.folio.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.text.selection.rememberSelectionState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.platform.TextToolbar
import androidx.compose.ui.platform.TextToolbarStatus
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.*
import dev.meridian.folio.ui.theme.*
import kotlin.math.roundToInt

internal data class NotebookSelection(val text: String, val page: Int, val context: String)
internal val LocalSaveReaderSelection = staticCompositionLocalOf<(NotebookSelection) -> Unit> { {} }
private const val SourcePage = "reader_source_page"
private const val SourceContext = "reader_source_context"

internal fun AnnotatedString.withReaderSource(page: Int, context: String): AnnotatedString =
    AnnotatedString.Builder(this).apply {
        if (length > 0) {
            addStringAnnotation(SourcePage, page.toString(), 0, length)
            addStringAnnotation(SourceContext, context, 0, length)
        }
    }.toAnnotatedString()

internal fun selectedNotebookEntry(texts: List<AnnotatedString>, fallbackPage: Int): NotebookSelection? {
    val selected = texts.filter { it.text.isNotBlank() }
    val text = selected.joinToString("\n") { it.text }.trim()
    if (text.isBlank()) return null
    val first = selected.first()
    return NotebookSelection(text,
        first.getStringAnnotations(SourcePage, 0, first.length).firstOrNull()?.item?.toIntOrNull() ?: fallbackPage,
        first.getStringAnnotations(SourceContext, 0, first.length).firstOrNull()?.item ?: text)
}

// The reader provides its own small menu, while SelectionContainer retains native drag/handle behaviour.
private object ReaderSelectionToolbar : TextToolbar {
    override val status = TextToolbarStatus.Hidden
    override fun hide() = Unit
    override fun showMenu(rect: Rect, onCopyRequested: (() -> Unit)?, onPasteRequested: (() -> Unit)?,
        onCutRequested: (() -> Unit)?, onSelectAllRequested: (() -> Unit)?) = Unit
}

@Suppress("DEPRECATION")
@Composable
internal fun ReaderTextSelection(
    selectionKey: Any,
    page: Int,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    key(selectionKey) {
        val selection = rememberSelectionState()
        var pointer by remember { mutableStateOf(Offset(16f, 64f)) }
        var dragging by remember { mutableStateOf(false) }
        val selected = selectedNotebookEntry(selection.selectedTexts, page)
        var menuSelection by remember { mutableStateOf<NotebookSelection?>(null) }
        LaunchedEffect(selected, dragging) {
            if (dragging) menuSelection = null
            else if (selected != null) menuSelection = selected
        }
        val save = LocalSaveReaderSelection.current
        val turnPage = LocalReaderPageTurn.current
        val clipboard = LocalClipboardManager.current
        val palette = LocalClayPalette.current
        Box(modifier.onPreviewKeyEvent {
            if (it.key == Key.Escape && menuSelection != null) { menuSelection = null; selection.clear(); true } else false
        }) {
            CompositionLocalProvider(LocalTextToolbar provides ReaderSelectionToolbar) {
                SelectionContainer(state = selection, modifier = Modifier.fillMaxSize().pointerInput(selectionKey) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val change = event.changes.firstOrNull() ?: continue
                            if (event.type == PointerEventType.Scroll) {
                                menuSelection = null
                            } else {
                                if (event.changes.any { it.pressed && !it.previousPressed }) menuSelection = null
                                if (change.pressed || change.previousPressed) pointer = change.position
                                dragging = event.changes.any { it.pressed }
                            }
                        }
                    }
                }, content = content)
            }
            val entry = menuSelection
            if (entry != null && !dragging) {
                val position = remember(pointer) {
                    object : PopupPositionProvider {
                        override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize,
                            layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset {
                            val above = anchorBounds.top + pointer.y.roundToInt() - popupContentSize.height - 12
                            val y = if (above >= 0) above else anchorBounds.top + pointer.y.roundToInt() + 28
                            return IntOffset(
                                (anchorBounds.left + pointer.x.roundToInt()).coerceIn(0, (windowSize.width - popupContentSize.width).coerceAtLeast(0)),
                                y.coerceIn(0, (windowSize.height - popupContentSize.height).coerceAtLeast(0)))
                        }
                    }
                }
                Popup(popupPositionProvider = position, onDismissRequest = { menuSelection = null; selection.clear() },
                    properties = PopupProperties(focusable = true)) {
                    val menuFocus = remember { FocusRequester() }
                    LaunchedEffect(Unit) { menuFocus.requestFocus() }
                    val shape = RoundedCornerShape(ClayRadius.base)
                    Row(Modifier.background(palette.surfaceSecondary, shape).border(1.dp, palette.outlineVariant, shape)
                        .padding(6.dp).testTag("reader_selection_menu").onPreviewKeyEvent { event ->
                            if (event.type == KeyEventType.KeyDown && (event.key == Key.PageDown || event.key == Key.PageUp)) {
                                menuSelection = null
                                selection.clear()
                                turnPage(if (event.key == Key.PageDown) 1 else -1)
                                true
                            } else false
                        }.focusRequester(menuFocus).focusable(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        ClayHeaderAction("Guardar en Notebook", palette, "selection_save_notebook") {
                            save(entry)
                            menuSelection = null
                            selection.clear()
                        }
                        ClayHeaderAction("Copiar", palette, "selection_copy") {
                            clipboard.setText(AnnotatedString(entry.text))
                            menuSelection = null
                            selection.clear()
                        }
                        ClayHeaderAction("×", palette, "selection_close") { menuSelection = null; selection.clear() }
                    }
                }
            }
        }
    }
}

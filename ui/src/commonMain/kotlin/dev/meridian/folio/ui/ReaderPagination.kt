package dev.meridian.folio.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.meridian.folio.core.*
import dev.meridian.folio.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class LeafPagination {
    var index by mutableStateOf(0)
    var count by mutableStateOf(1)
    var enterAtEnd = false
}

/** Screen-sized continuations are transient; bookmarks and telemetry retain source page numbers. */
internal class ReaderPagination {
    val leaves = mutableStateMapOf<Int, LeafPagination>()
    private var enterAtEnd = emptySet<Int>()

    fun register(page: Int, leaf: LeafPagination) {
        if (page in enterAtEnd) {
            leaf.enterAtEnd = true
            enterAtEnd = enterAtEnd - page
        }
        leaves[page] = leaf
    }

    fun canTurn(current: Int, step: Int, direction: Int): Boolean =
        (current until current + step).any { page ->
            leaves[page]?.let { if (direction > 0) it.index < it.count - 1 else it.index > 0 } == true
        }

    fun turn(book: BookDocument, step: Int, direction: Int, onSourcePage: (Int) -> Unit) {
        val pages = (book.currentPage until book.currentPage + step).toList()
        val ordered = if (direction > 0) pages else pages.reversed()
        val leaf = ordered.mapNotNull { leaves[it] }.firstOrNull {
            if (direction > 0) it.index < it.count - 1 else it.index > 0
        }
        if (leaf != null) {
            leaf.index = (leaf.index + direction).coerceIn(0, leaf.count - 1)
            return
        }
        val target = (book.currentPage + direction * step).coerceIn(1, book.totalPages)
        if (target != book.currentPage) {
            enterAtEnd = if (direction < 0) (target until target + step).toSet() else emptySet()
            onSourcePage(target)
        }
    }
}

internal val LocalReaderPagination = staticCompositionLocalOf { ReaderPagination() }

/** One wheel burst turns one sheet. Continuous mode does not attach this modifier. */
internal fun Modifier.readerPageGestures(key: Any, onTurn: (Int) -> Unit): Modifier =
    composed {
      val latestTurn by rememberUpdatedState(onTurn)
      Modifier.pointerInput(key) {
        var lastScroll = Long.MIN_VALUE
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                if (event.type == PointerEventType.Scroll && event.changes.none { it.isConsumed }) {
                    val delta = event.changes.sumOf { it.scrollDelta.y.toDouble() }
                    val now = event.changes.firstOrNull()?.uptimeMillis ?: continue
                    if (delta != 0.0 && (lastScroll == Long.MIN_VALUE || now - lastScroll > 220)) {
                        latestTurn(if (delta > 0) 1 else -1)
                    }
                    if (delta != 0.0) lastScroll = now
                    event.changes.forEach { it.consume() }
                }
            }
        }
      }
    }

internal data class PagePiece(
    val text: AnnotatedString = AnnotatedString(""),
    val style: TextStyle = TextStyle.Default,
    val imagePath: String? = null,
    val heightPx: Int,
)

/** Splits at measured line boundaries, retaining every character and annotation. */
internal fun paginateText(
    text: AnnotatedString,
    style: TextStyle,
    width: Int,
    height: Int,
    measure: (AnnotatedString, TextStyle, Int) -> androidx.compose.ui.text.TextLayoutResult,
): List<PagePiece> {
    if (text.isEmpty()) return emptyList()
    val result = mutableListOf<PagePiece>()
    var offset = 0
    while (offset < text.length) {
        val remaining = text.subSequence(offset, text.length)
        val layout = measure(remaining, style, width)
        var lines = (0 until layout.lineCount).takeWhile { layout.getLineBottom(it) <= height }.size.coerceAtLeast(1)
        var end = layout.getLineEnd(lines - 1).coerceAtLeast(1).coerceAtMost(remaining.length)
        var fragment = remaining.subSequence(0, end)
        var fragmentHeight = measure(fragment, style, width).size.height
        // A trailing newline can introduce one additional line in the isolated fragment.
        while (fragmentHeight > height && lines > 1) {
            lines--
            end = layout.getLineEnd(lines - 1).coerceAtLeast(1).coerceAtMost(remaining.length)
            fragment = remaining.subSequence(0, end)
            fragmentHeight = measure(fragment, style, width).size.height
        }
        result += PagePiece(fragment, style, heightPx = fragmentHeight)
        offset += end
    }
    return result
}

@Composable
internal fun PagedBookBody(
    palette: ClayPalette,
    tone: ReadingTone,
    pdfPageColorMode: PdfPageColorMode,
    book: BookDocument,
    page: BookPage,
    savedPhrases: List<SavedPhrase>,
    fontSizeSp: Float,
    lineHeightSp: Float,
    typography: BookTypographySettings,
    onQuickSavePhrase: (String, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val pagination = LocalReaderPagination.current
    val leaf = remember(book.id, page.pageNumber) { LeafPagination() }
    DisposableEffect(pagination, page.pageNumber, leaf) {
        pagination.register(page.pageNumber, leaf)
        onDispose { if (pagination.leaves[page.pageNumber] === leaf) pagination.leaves.remove(page.pageNumber) }
    }
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    BoxWithConstraints(modifier.fillMaxSize().testTag("paged_body_${page.pageNumber}")) {
        val widthPx = constraints.maxWidth.coerceAtLeast(1)
        val bodyHeight = (constraints.maxHeight - with(density) { 30.dp.roundToPx() }).coerceAtLeast(1)
        val gap = with(density) { typography.paragraphSpacingDp.dp.roundToPx() }
        val pages = remember(page.blocks, savedPhrases, tone, typography, fontSizeSp, lineHeightSp, widthPx, bodyHeight, density, measurer) {
            val sheets = mutableListOf<List<PagePiece>>()
            var sheet = mutableListOf<PagePiece>()
            var used = 0
            fun append(piece: PagePiece) {
                val spacing = if (sheet.isEmpty()) 0 else gap
                if (sheet.isNotEmpty() && used + spacing + piece.heightPx > bodyHeight) {
                    sheets += sheet
                    sheet = mutableListOf()
                    used = 0
                }
                used += (if (sheet.isEmpty()) 0 else gap) + piece.heightPx
                sheet += piece
            }
            for (block in page.blocks) {
                if (block.style == BlockStyle.IMAGE && block.imagePath != null) {
                    append(PagePiece(imagePath = block.imagePath, heightPx = (bodyHeight * 0.72f).toInt()))
                }
                val style = TextStyle(
                    color = if (block.style == BlockStyle.QUOTE) tone.inkSoft else tone.ink,
                    fontFamily = typography.fontFamily.toComposeFontFamily(),
                    fontSize = (if (block.style == BlockStyle.HEADING) fontSizeSp + 4 else fontSizeSp).sp,
                    lineHeight = (if (block.style == BlockStyle.HEADING) lineHeightSp + 4 else lineHeightSp).sp,
                    fontWeight = if (block.style == BlockStyle.HEADING) FontWeight.SemiBold else FontWeight.Normal,
                    fontStyle = if (block.style == BlockStyle.QUOTE) FontStyle.Italic else FontStyle.Normal,
                    textAlign = typography.textAlignment.toComposeTextAlign(),
                    letterSpacing = 0.sp,
                )
                val annotated = buildHighlightedAnnotatedString(block.text, savedPhrases, tone)
                paginateText(annotated, style, widthPx, bodyHeight) { text, textStyle, width ->
                    measurer.measure(text, textStyle, constraints = Constraints.fixedWidth(width))
                }.forEach(::append)
            }
            if (sheet.isNotEmpty()) sheets += sheet
            sheets.ifEmpty { listOf(emptyList()) }
        }
        val index = leaf.index.coerceIn(0, pages.lastIndex)
        SideEffect {
            leaf.count = pages.size
            leaf.index = if (leaf.enterAtEnd) pages.lastIndex else index
            leaf.enterAtEnd = false
        }
        Column(Modifier.fillMaxSize()) {
            SelectionContainer(Modifier.weight(1f).fillMaxWidth()) {
                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(typography.paragraphSpacingDp.dp)) {
                    pages[index].forEach { piece ->
                        val path = piece.imagePath
                        if (path != null) {
                            val bitmap by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, path, book.filePath) {
                                value = withContext(Dispatchers.Default) {
                                    val file = book.filePath
                                    val resolved = if (path.startsWith("lazy_pdf://") && file != null)
                                        PlatformPdfEngine.renderPdfPageOnDemand(file, book.id, page.pageNumber) else path
                                    resolved?.let { PlatformFileAndZipIo.readFileBytes(it) }?.let { bytes ->
                                        runCatching { decodeImageBitmapFromBytes(bytes) }.getOrNull()
                                    }
                                }
                            }
                            Box(Modifier.fillMaxWidth().height(with(density) { piece.heightPx.toDp() })) {
                                val image = bitmap
                                if (image != null) Image(image, "Ilustración de la página ${page.pageNumber}", Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Fit,
                                    colorFilter = pdfPageImageColorFilter(palette, tone, pdfPageColorMode, book.format == DocumentFormat.PDF, false))
                            }
                        } else Text(piece.text, style = piece.style, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
            Row(Modifier.fillMaxWidth().height(30.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(if (pages.size > 1) "Página ${page.pageNumber} · ${index + 1}/${pages.size}" else "",
                    color = tone.muted, fontSize = 11.sp, modifier = Modifier.testTag("page_part_${page.pageNumber}"))
                val text = pages[index].joinToString(" ") { it.text.text }
                val candidate = remember(text) { extractCandidatePhrases(text).firstOrNull() }
                if (candidate != null) Text("+ Frase", color = tone.ink, fontSize = 11.sp,
                    modifier = Modifier.clayFocusableClickable(palette) { onQuickSavePhrase(candidate, text) }.padding(horizontal = 8.dp))
            }
        }
    }
}

package dev.meridian.folio.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.meridian.folio.core.*
import dev.meridian.folio.ui.theme.*
import kotlin.math.min

internal data class PdfViewport(val fitMode: PageFitMode = PageFitMode.FIT_PAGE, val zoom: Float = 1f)
internal val LocalPdfViewport = staticCompositionLocalOf { PdfViewport() }
internal val LocalReaderPageTurn = staticCompositionLocalOf<(Int) -> Unit> { {} }

internal fun BookDocument.hasOriginalPage(page: BookPage): Boolean =
    format == DocumentFormat.PDF && (page.renderedPageImagePath != null || filePath != null)

/** Original PDF pages never go through the text paginator. Zoom only changes their physical size. */
@Composable
internal fun OriginalPdfPage(
    book: BookDocument,
    page: BookPage,
    tone: ReadingTone,
    colorMode: PdfPageColorMode,
    modifier: Modifier = Modifier,
    onTurn: ((Int) -> Unit)? = null,
) {
    key(book.id, page.pageNumber) {
        val loaded = rememberLoadedPageImage(page.renderedPageImagePath ?: "lazy_pdf://${book.id}/${page.pageNumber}",
            book.filePath, book.id, page.pageNumber)
        val viewport = LocalPdfViewport.current
        val pageTurn = onTurn ?: LocalReaderPageTurn.current
        BoxWithConstraints(modifier.fillMaxSize().testTag("pdf_original_page_${page.pageNumber}"), contentAlignment = Alignment.Center) {
            val image = loaded.bitmap
            if (image == null) {
                Text(if (loaded.loading) "Preparando página ${page.pageNumber}…" else "No se pudo cargar la página. Comprueba que el PDF siga disponible.", color = tone.muted)
            } else {
                val ratio = image.width.toFloat() / image.height.coerceAtLeast(1)
                val fittedWidth = min(maxWidth.value, maxHeight.value * ratio)
                val width = when (viewport.fitMode) {
                    PageFitMode.FIT_PAGE -> fittedWidth
                    PageFitMode.FIT_WIDTH -> maxWidth.value
                    PageFitMode.MANUAL_ZOOM -> fittedWidth * viewport.zoom.coerceIn(0.5f, 2.5f)
                }.coerceAtLeast(1f).dp
                Box(Modifier.fillMaxSize().testTag("pdf_page_scroll_${page.pageNumber}")
                    .then(if (viewport.fitMode == PageFitMode.FIT_PAGE) Modifier.readerPageGestures(book.id, pageTurn) else Modifier)
                    .verticalScroll(rememberScrollState()).horizontalScroll(rememberScrollState()), contentAlignment = Alignment.TopCenter) {
                    Image(image, "Página ${page.pageNumber} de ${book.totalPages}",
                        Modifier.requiredSize(width, width / ratio).border(1.dp, tone.border).testTag("pdf_page_image_${page.pageNumber}"),
                        contentScale = ContentScale.Fit,
                        colorFilter = pdfPageImageColorFilter(tone, colorMode, true, false))
                }
            }
        }
    }
}

/** A lazy stack of complete source pages; the viewport determines the persisted reading position. */
@Composable
internal fun ContinuousPdfPages(
    book: BookDocument,
    tone: ReadingTone,
    colorMode: PdfPageColorMode,
    onPageChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val sourcePages = remember(book.pages) { book.pages.map { it.pageNumber } }
    val state = rememberSourcePageListState(book.id, sourcePages, book.currentPage, onPageChange)
    val viewport = LocalPdfViewport.current
    BoxWithConstraints(modifier.fillMaxSize()) {
      val availableWidth = maxWidth.value
      val availableHeight = (maxHeight.value - 40f).coerceAtLeast(1f)
      val listWidth = if (viewport.fitMode == PageFitMode.MANUAL_ZOOM)
          maxOf(availableWidth, min(availableWidth, availableHeight * 0.707f) * viewport.zoom) else availableWidth
      Box(Modifier.fillMaxSize().horizontalScroll(rememberScrollState()), contentAlignment = Alignment.TopCenter) {
      LazyColumn(state = state, modifier = Modifier.width(listWidth.dp).fillMaxHeight().testTag("continuous_page_list"),
        contentPadding = PaddingValues(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        items(book.pages, key = { it.pageNumber }) { page ->
            val loaded = rememberLoadedPageImage(page.renderedPageImagePath ?: "lazy_pdf://${book.id}/${page.pageNumber}",
                book.filePath, book.id, page.pageNumber)
            val bitmap = loaded.bitmap
            val ratio = bitmap?.let { it.width.toFloat() / it.height.coerceAtLeast(1) } ?: 0.707f
            val pageWidth = when (viewport.fitMode) {
                PageFitMode.FIT_PAGE -> min(availableWidth, availableHeight * ratio)
                PageFitMode.FIT_WIDTH -> availableWidth
                PageFitMode.MANUAL_ZOOM -> min(availableWidth, availableHeight * ratio) * viewport.zoom.coerceIn(0.5f, 2.5f)
            }
            Column(Modifier.width(pageWidth.dp).testTag("continuous_pdf_page_${page.pageNumber}"), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Página ${page.pageNumber} / ${book.totalPages}", color = tone.muted, style = LocalClayTypography.current.labelSm)
                Spacer(Modifier.height(6.dp))
                Box(Modifier.fillMaxWidth().aspectRatio(ratio).background(tone.sheet).border(1.dp, tone.border), contentAlignment = Alignment.TopCenter) {
                    val image = bitmap
                    if (image != null) Image(image, "Página ${page.pageNumber}", Modifier.fillMaxSize().testTag("continuous_pdf_image_${page.pageNumber}"),
                        contentScale = ContentScale.Fit, colorFilter = pdfPageImageColorFilter(tone, colorMode, true, false))
                    else Text(if (loaded.loading) "Preparando página…" else "No se pudo cargar esta página.", color = tone.muted,
                        modifier = Modifier.padding(24.dp))
                }
            }
        }
    }
    }
    }
}

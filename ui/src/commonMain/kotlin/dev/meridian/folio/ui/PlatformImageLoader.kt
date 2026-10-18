package dev.meridian.folio.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import dev.meridian.folio.core.PlatformFileAndZipIo
import dev.meridian.folio.core.PlatformPdfEngine

expect fun decodeImageBitmapFromBytes(bytes: ByteArray): ImageBitmap?

@Composable
fun rememberLoadedImageBitmap(
    path: String?,
    bookFilePath: String? = null,
    bookId: String? = null,
    pageNumber: Int = 1,
): ImageBitmap? =
    remember(path, bookFilePath, bookId, pageNumber) {
        if (path.isNullOrBlank()) {
            null
        } else {
            val needsOnDemandPdfRender = (path.startsWith("lazy_pdf://") || (path.contains("pdf_page_") && !path.contains("pdf_page_v4_"))) &&
                !bookFilePath.isNullOrBlank() && !bookId.isNullOrBlank()
            val resolvedPath = if (needsOnDemandPdfRender) {
                PlatformPdfEngine.renderPdfPageOnDemand(bookFilePath!!, bookId!!, pageNumber) ?: path
            } else {
                path
            }
            if (resolvedPath.isNullOrBlank()) {
                null
            } else {
                val bytes = PlatformFileAndZipIo.readFileBytes(resolvedPath)
                if (bytes != null && bytes.isNotEmpty()) {
                    runCatching { decodeImageBitmapFromBytes(bytes) }.getOrNull()
                } else {
                    null
                }
            }
        }
    }

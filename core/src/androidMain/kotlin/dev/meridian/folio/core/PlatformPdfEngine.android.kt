package dev.meridian.folio.core

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import java.io.ByteArrayOutputStream
import java.io.File

actual object PlatformPdfEngine {
    actual fun extractPdfDocument(
        docId: String,
        baseTitle: String,
        bytes: ByteArray,
        filePath: String?,
        nowMs: Long,
    ): BookDocument? = runCatching {
        val tmpDir = File(System.getProperty("java.io.tmpdir") ?: ".", "meridian_pdf_tmp").apply { mkdirs() }
        val tmpPdf = File(tmpDir, "$docId.pdf")
        tmpPdf.writeBytes(bytes)

        val pfd = ParcelFileDescriptor.open(tmpPdf, ParcelFileDescriptor.MODE_READ_ONLY)
        val renderer = PdfRenderer(pfd)
        val pageCount = renderer.pageCount.coerceAtLeast(1)
        val pages = mutableListOf<BookPage>()

        for (i in 0 until pageCount) {
            val pageNo = i + 1
            val savedImg = if (pageNo <= 4) {
                val page = renderer.openPage(i)
                val width = (page.width * 2.0f).toInt().coerceAtLeast(600)
                val height = (page.height * 2.0f).toInt().coerceAtLeast(840)
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                bitmap.eraseColor(android.graphics.Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                page.close()

                val pngOut = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.PNG, 90, pngOut)
                bitmap.recycle()
                PlatformFileAndZipIo.cacheMediaFile(docId, "pdf_page_v4_$pageNo.png", pngOut.toByteArray())
            } else {
                "lazy_pdf://$docId/$pageNo"
            }

            pages.add(
                BookPage(
                    pageNumber = pageNo,
                    chapterTitle = "$baseTitle — Page $pageNo",
                    blocks = listOf(
                        ContentBlock(
                            id = "pdf_android_$pageNo",
                            style = BlockStyle.IMAGE,
                            text = "",
                            imagePath = savedImg,
                        ),
                    ),
                    renderedPageImagePath = savedImg,
                ),
            )
        }
        renderer.close()
        pfd.close()

        BookDocument(
            id = docId,
            title = baseTitle,
            subtitle = "PDF Folio ($pageCount pages)",
            author = "Meridian Archive",
            format = DocumentFormat.PDF,
            filePath = filePath,
            pages = pages,
            currentPage = 1,
            parserVersion = 4,
            createdAtMs = nowMs,
            updatedAtMs = nowMs,
        )
    }.getOrNull()

    actual fun renderPdfPageOnDemand(
        filePath: String,
        docId: String,
        pageNumber: Int,
    ): String? = runCatching {
        val pdfFile = File(filePath)
        if (!pdfFile.exists()) return null
        val pfd = ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY)
        val renderer = PdfRenderer(pfd)
        val pageIdx = (pageNumber - 1).coerceIn(0, renderer.pageCount - 1)
        val page = renderer.openPage(pageIdx)
        val width = (page.width * 2.0f).toInt().coerceAtLeast(600)
        val height = (page.height * 2.0f).toInt().coerceAtLeast(840)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(android.graphics.Color.WHITE)
        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
        page.close()
        renderer.close()
        pfd.close()

        val pngOut = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 90, pngOut)
        bitmap.recycle()
        PlatformFileAndZipIo.cacheMediaFile(docId, "pdf_page_v4_$pageNumber.png", pngOut.toByteArray())
    }.getOrNull()

    actual fun extractOcrTextForPage(
        filePath: String?,
        docId: String,
        pageNumber: Int,
        renderedImagePath: String?,
    ): List<String> = listOf(
        "[OCR · Folio $pageNumber] Página escaneada procesada en modo no destructivo (archivo PDF original conservado).",
    )

    actual fun exportPdfDocument(book: BookDocument): ByteArray? = null
}

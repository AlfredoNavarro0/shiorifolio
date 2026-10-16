package dev.meridian.folio.core

import org.apache.pdfbox.cos.COSName
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject
import org.apache.pdfbox.rendering.ImageType
import org.apache.pdfbox.rendering.PDFRenderer
import org.apache.pdfbox.text.PDFTextStripper
import java.io.ByteArrayOutputStream
import java.io.File
import javax.imageio.ImageIO

actual object PlatformPdfEngine {

    actual fun extractPdfDocument(
        docId: String,
        baseTitle: String,
        bytes: ByteArray,
        filePath: String?,
        nowMs: Long,
    ): BookDocument? = runCatching {
        PDDocument.load(bytes).use { pdDoc ->
            val info = pdDoc.documentInformation
            val title = info?.title?.takeIf { it.isNotBlank() } ?: baseTitle
            val author = info?.author?.takeIf { it.isNotBlank() } ?: "Meridian Archive"
            val totalPages = pdDoc.numberOfPages.coerceAtLeast(1)

            val stripper = PDFTextStripper().apply {
                sortByPosition = true
            }
            val renderer = PDFRenderer(pdDoc)
            val pages = mutableListOf<BookPage>()

            for (pageIndex in 0 until totalPages) {
                val pageNo = pageIndex + 1
                stripper.startPage = pageNo
                stripper.endPage = pageNo
                val rawPageText = runCatching { stripper.getText(pdDoc) }.getOrDefault("").trim()
                val pdPage = pdDoc.getPage(pageIndex)
                val hasImages = pageHasImages(pdPage)

                // Pre-render the first 6 pages or any illustration-only page if filePath is null;
                // if filePath is available, render on-demand or for initial cover pages so import is super fast!
                val shouldRenderNow = pageNo <= 6 || (filePath == null && (hasImages || rawPageText.length < 45))
                val renderedImagePath: String? = if (shouldRenderNow && (hasImages || rawPageText.length < 45)) {
                    renderPageInternal(renderer, docId, pageIndex, pageNo)
                } else {
                    null
                }

                val blocks = mutableListOf<ContentBlock>()
                if ((hasImages || rawPageText.length < 45) && rawPageText.length < 60) {
                    blocks.add(
                        ContentBlock(
                            id = "pdf_${pageNo}_img",
                            style = BlockStyle.IMAGE,
                            text = rawPageText.lines().firstOrNull { it.isNotBlank() }?.trim() ?: "Page $pageNo Illustration",
                            imagePath = renderedImagePath ?: "lazy_pdf://$docId/$pageNo",
                        ),
                    )
                }

                val paragraphs = splitPdfTextIntoParagraphs(rawPageText)
                var chapterHeading = "$title — Page $pageNo"
                paragraphs.forEachIndexed { idx, para ->
                    if (idx == 0 && para.length in 3..85 && !para.startsWith("\"") && !para.startsWith("“")) {
                        chapterHeading = para
                        blocks.add(
                            ContentBlock(
                                id = "pdf_${pageNo}_$idx",
                                style = BlockStyle.HEADING,
                                text = para,
                            ),
                        )
                    } else {
                        blocks.add(
                            ContentBlock(
                                id = "pdf_${pageNo}_$idx",
                                style = BlockStyle.PARAGRAPH,
                                text = para,
                            ),
                        )
                    }
                }

                if (hasImages && rawPageText.length >= 60) {
                    blocks.add(
                        ContentBlock(
                            id = "pdf_${pageNo}_visual",
                            style = BlockStyle.IMAGE,
                            text = "Page $pageNo Visual Illustration",
                            imagePath = renderedImagePath ?: "lazy_pdf://$docId/$pageNo",
                        ),
                    )
                }

                if (blocks.isEmpty()) {
                    blocks.add(
                        ContentBlock(
                            id = "pdf_${pageNo}_empty",
                            style = BlockStyle.IMAGE,
                            text = "Page $pageNo",
                            imagePath = renderedImagePath ?: "lazy_pdf://$docId/$pageNo",
                        ),
                    )
                }

                pages.add(
                    BookPage(
                        pageNumber = pageNo,
                        chapterTitle = chapterHeading,
                        blocks = blocks,
                        renderedPageImagePath = renderedImagePath ?: if (hasImages || rawPageText.length < 45) "lazy_pdf://$docId/$pageNo" else null,
                    ),
                )
            }

            BookDocument(
                id = docId,
                title = title,
                subtitle = "PDF Folio ($totalPages pages)",
                author = author,
                format = DocumentFormat.PDF,
                filePath = filePath,
                pages = pages,
                currentPage = 1,
                parserVersion = 3,
                createdAtMs = nowMs,
                updatedAtMs = nowMs,
            )
        }
    }.getOrNull()

    actual fun renderPdfPageOnDemand(
        filePath: String,
        docId: String,
        pageNumber: Int,
    ): String? = runCatching {
        val home = System.getProperty("user.home") ?: System.getProperty("java.io.tmpdir") ?: "."
        val safeBook = docId.replace(Regex("[^A-Za-z0-9_\\-]"), "_")
        val cachedFile = File(File(File(home, ".meridian-folio"), "media/$safeBook"), "pdf_page_$pageNumber.png")
        if (cachedFile.exists() && cachedFile.length() > 0L) {
            return cachedFile.absolutePath
        }
        val pdfFile = File(filePath)
        if (!pdfFile.exists()) return null
        PDDocument.load(pdfFile).use { pdDoc ->
            val pageIndex = (pageNumber - 1).coerceIn(0, pdDoc.numberOfPages - 1)
            val renderer = PDFRenderer(pdDoc)
            renderPageInternal(renderer, docId, pageIndex, pageNumber)
        }
    }.getOrNull()

    private fun renderPageInternal(
        renderer: PDFRenderer,
        docId: String,
        pageIndex: Int,
        pageNo: Int,
    ): String? = runCatching {
        val buffered = renderer.renderImageWithDPI(pageIndex, 130f, ImageType.RGB)
        val pngOut = ByteArrayOutputStream()
        ImageIO.write(buffered, "png", pngOut)
        PlatformFileAndZipIo.cacheMediaFile(docId, "pdf_page_$pageNo.png", pngOut.toByteArray())
    }.getOrNull()

    private fun pageHasImages(page: PDPage): Boolean = runCatching {
        val resources = page.resources ?: return false
        for (name: COSName in resources.xObjectNames) {
            if (resources.isImageXObject(name)) return true
            val obj = resources.getXObject(name)
            if (obj is PDImageXObject) return true
        }
        false
    }.getOrDefault(false)

    private fun splitPdfTextIntoParagraphs(rawText: String): List<String> {
        if (rawText.isBlank()) return emptyList()
        val normalized = rawText.replace("\r\n", "\n").replace('\r', '\n')
        val lines = normalized.lines()
        val paragraphs = mutableListOf<String>()
        val current = StringBuilder()

        for (rawLine in lines) {
            val line = rawLine.trim()
            if (line.isEmpty()) {
                if (current.isNotEmpty()) {
                    paragraphs.add(current.toString().trim())
                    current.clear()
                }
                continue
            }
            val startsNewDialogueOrSection =
                line.startsWith("\"") ||
                    line.startsWith("“") ||
                    line.startsWith("—") ||
                    line.startsWith("─") ||
                    line.startsWith("[") ||
                    (line.length <= 3 && line.all { it.isDigit() || it in '０'..'９' || it == '★' })

            if (startsNewDialogueOrSection && current.isNotEmpty()) {
                paragraphs.add(current.toString().trim())
                current.clear()
                current.append(line)
            } else if (current.isEmpty()) {
                current.append(line)
            } else {
                val prevEndsSentence = current.endsWith(".") ||
                    current.endsWith(".\"") ||
                    current.endsWith(".”") ||
                    current.endsWith("?") ||
                    current.endsWith("?\"") ||
                    current.endsWith("?”") ||
                    current.endsWith("!") ||
                    current.endsWith("!\"") ||
                    current.endsWith("!”")
                if (prevEndsSentence && current.length >= 140) {
                    paragraphs.add(current.toString().trim())
                    current.clear()
                    current.append(line)
                } else {
                    if (current.endsWith("-") && !current.endsWith(" -") && !current.endsWith("—")) {
                        current.append(line)
                    } else {
                        current.append(' ').append(line)
                    }
                }
            }
        }
        if (current.isNotEmpty()) {
            paragraphs.add(current.toString().trim())
        }
        return paragraphs.filter { it.isNotBlank() }
    }

    actual fun exportPdfDocument(book: BookDocument): ByteArray? = runCatching {
        PDDocument().use { doc ->
            for (page in book.pages) {
                val pdPage = PDPage(PDRectangle.LETTER)
                doc.addPage(pdPage)
                PDPageContentStream(doc, pdPage).use { cs ->
                    var y = 735f
                    cs.beginText()
                    cs.setFont(PDType1Font.TIMES_BOLD, 10f)
                    cs.newLineAtOffset(54f, y)
                    cs.showText(sanitizeWinAnsi("${book.title}  —  Page ${page.pageNumber}"))
                    cs.endText()
                    y -= 24f

                    cs.beginText()
                    cs.setFont(PDType1Font.TIMES_BOLD, 14f)
                    cs.newLineAtOffset(54f, y)
                    cs.showText(sanitizeWinAnsi(page.chapterTitle.take(80)))
                    cs.endText()
                    y -= 24f

                    for (block in page.blocks) {
                        if (block.style == BlockStyle.IMAGE) continue
                        val isHeading = block.style == BlockStyle.HEADING
                        val font = if (isHeading) PDType1Font.TIMES_BOLD else PDType1Font.TIMES_ROMAN
                        val fontSize = if (isHeading) 12.5f else 11f
                        val wrapped = wrapTextSimple(sanitizeWinAnsi(block.text), 82)
                        for (wLine in wrapped) {
                            if (y < 54f) break
                            cs.beginText()
                            cs.setFont(font, fontSize)
                            cs.newLineAtOffset(54f, y)
                            cs.showText(wLine)
                            cs.endText()
                            y -= 15.5f
                        }
                        y -= 8f
                    }
                }
            }
            val out = ByteArrayOutputStream()
            doc.save(out)
            out.toByteArray()
        }
    }.getOrNull()

    private fun sanitizeWinAnsi(text: String): String =
        text
            .replace('“', '"')
            .replace('”', '"')
            .replace('‘', '\'')
            .replace('’', '\'')
            .replace('—', '-')
            .replace('–', '-')
            .replace('─', '-')
            .replace('〜', '~')
            .replace('…', '.')
            .map { ch -> if (ch.code in 32..126 || ch.code in 160..255) ch else ' ' }
            .joinToString("")

    private fun wrapTextSimple(text: String, maxCols: Int): List<String> {
        val words = text.trim().split(Regex("\\s+"))
        val lines = mutableListOf<String>()
        val sb = StringBuilder()
        for (w in words) {
            if (sb.length + w.length + 1 > maxCols && sb.isNotEmpty()) {
                lines.add(sb.toString())
                sb.clear()
            }
            if (sb.isNotEmpty()) sb.append(' ')
            sb.append(w)
        }
        if (sb.isNotEmpty()) lines.add(sb.toString())
        return lines
    }
}

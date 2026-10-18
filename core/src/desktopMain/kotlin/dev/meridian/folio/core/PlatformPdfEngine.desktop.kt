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
                val isIllustrationPage = (hasImages || rawPageText.length < 45) && rawPageText.length < 60

                // Pre-render the first 6 pages or any illustration-only page if filePath is null;
                // if filePath is available, render on-demand or for initial cover pages so import is super fast!
                val shouldRenderNow = pageNo <= 6 || (filePath == null && (hasImages || rawPageText.length < 45))
                val renderedImagePath: String? = if (shouldRenderNow && (hasImages || rawPageText.length < 45)) {
                    renderPageInternal(renderer, docId, pageIndex, pageNo, trimWhiteMargins = isIllustrationPage)
                } else {
                    null
                }

                val blocks = mutableListOf<ContentBlock>()
                if (isIllustrationPage) {
                    blocks.add(
                        ContentBlock(
                            id = "pdf_${pageNo}_img",
                            style = BlockStyle.IMAGE,
                            text = "",
                            imagePath = renderedImagePath ?: "lazy_pdf://$docId/$pageNo",
                        ),
                    )
                }

                val paragraphs = splitPdfTextIntoParagraphs(rawPageText)
                var chapterHeading = "$title — Page $pageNo"
                if (!isIllustrationPage) {
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
                }

                if (hasImages && rawPageText.length >= 60) {
                    blocks.add(
                        ContentBlock(
                            id = "pdf_${pageNo}_visual",
                            style = BlockStyle.IMAGE,
                            text = "",
                            imagePath = renderedImagePath ?: "lazy_pdf://$docId/$pageNo",
                        ),
                    )
                }

                if (blocks.isEmpty()) {
                    blocks.add(
                        ContentBlock(
                            id = "pdf_${pageNo}_empty",
                            style = BlockStyle.IMAGE,
                            text = "",
                            imagePath = renderedImagePath ?: "lazy_pdf://$docId/$pageNo",
                        ),
                    )
                }

                pages.add(
                    BookPage(
                        pageNumber = pageNo,
                        chapterTitle = chapterHeading,
                        blocks = blocks,
                        renderedPageImagePath = renderedImagePath ?: "lazy_pdf://$docId/$pageNo",
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
                parserVersion = 4,
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
        val cachedFile = File(File(File(home, ".meridian-folio"), "media/$safeBook"), "pdf_page_v4_$pageNumber.png")
        if (cachedFile.exists() && cachedFile.length() > 0L) {
            return cachedFile.absolutePath
        }
        val pdfFile = File(filePath)
        if (!pdfFile.exists()) return null
        PDDocument.load(pdfFile).use { pdDoc ->
            val pageIndex = (pageNumber - 1).coerceIn(0, pdDoc.numberOfPages - 1)
            val renderer = PDFRenderer(pdDoc)
            val stripper = PDFTextStripper().apply {
                startPage = pageNumber
                endPage = pageNumber
            }
            val rawPageText = runCatching { stripper.getText(pdDoc) }.getOrDefault("").trim()
            val isIllustrationPage = rawPageText.length < 60
            renderPageInternal(renderer, docId, pageIndex, pageNumber, trimWhiteMargins = isIllustrationPage)
        }
    }.getOrNull()

    private fun renderPageInternal(
        renderer: PDFRenderer,
        docId: String,
        pageIndex: Int,
        pageNo: Int,
        trimWhiteMargins: Boolean = true,
    ): String? = runCatching {
        val rawBuffered = renderer.renderImageWithDPI(pageIndex, 180f, ImageType.RGB)
        val processed = if (trimWhiteMargins) trimUniformWhiteMargins(rawBuffered) else rawBuffered
        val pngOut = ByteArrayOutputStream()
        ImageIO.write(processed, "png", pngOut)
        PlatformFileAndZipIo.cacheMediaFile(docId, "pdf_page_v4_$pageNo.png", pngOut.toByteArray())
    }.getOrNull()

    /**
     * Automatically crops uniform white / near-white PDF canvas margins around illustrations
     * so embedded artwork (like light novel inserts or covers placed inside a larger page box)
     * fills the full reader page without a white frame.
     */
    private fun trimUniformWhiteMargins(src: java.awt.image.BufferedImage): java.awt.image.BufferedImage {
        val w = src.width
        val h = src.height
        if (w < 64 || h < 64) return src

        fun isNearWhite(rgb: Int): Boolean {
            val r = (rgb shr 16) and 0xFF
            val g = (rgb shr 8) and 0xFF
            val b = rgb and 0xFF
            return r >= 244 && g >= 244 && b >= 244
        }

        val edgeInset = 3
        val maxTrimY = (h * 0.42f).toInt()
        val maxTrimX = (w * 0.42f).toInt()

        var top = edgeInset
        topLoop@ while (top < maxTrimY) {
            for (x in edgeInset until (w - edgeInset) step 2) {
                if (!isNearWhite(src.getRGB(x, top))) break@topLoop
            }
            top++
        }
        if (top == edgeInset) top = 0

        var bottom = h - 1 - edgeInset
        bottomLoop@ while (bottom > h - maxTrimY) {
            for (x in edgeInset until (w - edgeInset) step 2) {
                if (!isNearWhite(src.getRGB(x, bottom))) break@bottomLoop
            }
            bottom--
        }
        if (bottom == h - 1 - edgeInset) bottom = h - 1

        var left = edgeInset
        leftLoop@ while (left < maxTrimX) {
            for (y in top..bottom step 2) {
                if (!isNearWhite(src.getRGB(left, y))) break@leftLoop
            }
            left++
        }
        if (left == edgeInset) left = 0

        var right = w - 1 - edgeInset
        rightLoop@ while (right > w - maxTrimX) {
            for (y in top..bottom step 2) {
                if (!isNearWhite(src.getRGB(right, y))) break@rightLoop
            }
            right--
        }
        if (right == w - 1 - edgeInset) right = w - 1

        val cropW = right - left + 1
        val cropH = bottom - top + 1
        if (cropW <= w * 0.30 || cropH <= h * 0.30) return src
        if (left <= 4 && top <= 4 && right >= w - 5 && bottom >= h - 5) return src
        return src.getSubimage(left, top, cropW, cropH)
    }

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
        var prevLineLength = 0

        for (rawLine in lines) {
            val line = rawLine.trim()
            if (line.isEmpty()) {
                if (current.isNotEmpty()) {
                    paragraphs.add(current.toString().trim())
                    current.clear()
                }
                prevLineLength = 0
                continue
            }
            // Skip standalone running page number lines at top/bottom of page
            if (line.matches(Regex("""^(?:-\s*\d+\s*-|page\s+\d+|\d{1,4})$""", RegexOption.IGNORE_CASE)) &&
                (paragraphs.isEmpty() || rawLine === lines.last())
            ) {
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
                // Only start a new paragraph if the previous line ended a sentence AND was visibly shorter than a full PDF measure
                // (indicating an intentional paragraph break in the PDF layout) or the paragraph is already very long (>= 420 chars).
                val isShortTerminalLine = prevLineLength in 1..54 && prevEndsSentence
                if ((isShortTerminalLine && current.length >= 60) || (prevEndsSentence && current.length >= 420)) {
                    paragraphs.add(current.toString().trim())
                    current.clear()
                    current.append(line)
                } else {
                    if (current.endsWith("-") && !current.endsWith(" -") && !current.endsWith("—")) {
                        current.deleteCharAt(current.length - 1)
                        current.append(line)
                    } else {
                        current.append(' ').append(line)
                    }
                }
            }
            prevLineLength = line.length
        }
        if (current.isNotEmpty()) {
            paragraphs.add(current.toString().trim())
        }
        return paragraphs.filter { it.isNotBlank() }
    }

    actual fun extractOcrTextForPage(
        filePath: String?,
        docId: String,
        pageNumber: Int,
        renderedImagePath: String?,
    ): List<String> = runCatching {
        val resolvedImgPath = when {
            !renderedImagePath.isNullOrBlank() && !renderedImagePath.startsWith("lazy_pdf://") && File(renderedImagePath).exists() ->
                renderedImagePath
            !filePath.isNullOrBlank() ->
                renderPdfPageOnDemand(filePath, docId, pageNumber)
            else -> null
        }

        // 1. Try system Tesseract OCR CLI if available on Windows / Linux PATH
        if (!resolvedImgPath.isNullOrBlank() && File(resolvedImgPath).exists()) {
            val tesseractText = runCatching {
                val proc = ProcessBuilder("tesseract", resolvedImgPath, "stdout", "--psm", "3")
                    .redirectErrorStream(true)
                    .start()
                val out = proc.inputStream.bufferedReader().readText()
                val exit = proc.waitFor()
                if (exit == 0) out.trim() else ""
            }.getOrDefault("")
            if (tesseractText.length >= 12) {
                val split = splitPdfTextIntoParagraphs(tesseractText)
                if (split.isNotEmpty()) return split
            }
        }

        // 2. Deep PDFBox fallback: inspect non-suppressed overlapping glyphs & annotations on the page
        if (!filePath.isNullOrBlank() && File(filePath).exists()) {
            PDDocument.load(File(filePath)).use { pdDoc ->
                val stripper = PDFTextStripper().apply {
                    sortByPosition = true
                    suppressDuplicateOverlappingText = false
                    startPage = pageNumber
                    endPage = pageNumber
                }
                val deepText = runCatching { stripper.getText(pdDoc).trim() }.getOrDefault("")
                if (deepText.isNotBlank()) {
                    val split = splitPdfTextIntoParagraphs(deepText)
                    if (split.isNotEmpty()) return split
                }
            }
        }

        // 3. Non-destructive OCR summary placeholder when page is purely bitmap-scanned without external Tesseract binary
        listOf(
            "[OCR · Folio $pageNumber] Página escaneada procesada en memoria (archivo original conservado intacto). " +
                "Para reconocimiento óptico de caracteres completo en libros escaneados sin capa de texto, instala 'tesseract' en el sistema o alterna a Vista Original.",
        )
    }.getOrDefault(emptyList())

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

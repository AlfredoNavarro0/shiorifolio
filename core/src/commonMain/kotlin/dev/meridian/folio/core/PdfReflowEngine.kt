package dev.meridian.folio.core

/**
 * Represents a reconstructed block in Adaptive Reflow or Continuous Vertical Scroll mode,
 * retaining its origin page number for navigation, progress tracking, and annotations.
 */
data class ReflowedBlockItem(
    val id: String,
    val sourcePageNumber: Int,
    val chapterTitle: String,
    val style: BlockStyle,
    val text: String,
    val imagePath: String? = null,
    val isSceneBreak: Boolean = false,
)

/**
 * Diagnostic and reconstruction report for a book or page in Adaptive Reflow / Continuous mode.
 */
data class ReflowReconstructionResult(
    val items: List<ReflowedBlockItem>,
    val isReliable: Boolean,
    val reliabilityWarning: String? = null,
    val scannedPageNumbers: List<Int> = emptyList(),
    val mergedPageBreakCount: Int = 0,
    val strippedHeaderFooterCount: Int = 0,
    val dehyphenatedWordCount: Int = 0,
)

/**
 * Engine for Punto 5 ("Espacios entre párrafos y saltos de página"):
 * - Reconstructs continuous reading flow across PDF page breaks without mutating the original file.
 * - Strips running headers, footers, and standalone page numbers in PDFs.
 * - Joins hyphenated words split across line or page boundaries.
 * - Normalizes excessive paragraph/line breaks in EPUBs while preserving semantic scene breaks (* * *, ◇, ---) and headings.
 * - Detects multi-column / tabular layouts where reflow is unreliable so the UI can offer a one-click return to Original View.
 * - Detects scanned PDF pages lacking a text layer so the user can trigger non-destructive OCR.
 */
object PdfReflowEngine {

    private val SCENE_BREAK_REGEX = Regex(
        """^(\*\s*\*\s*\*|\*{3,}|-{3,}|—{2,}|◇+|•\s*•\s*•|✦+|§+)$""",
    )

    private val STANDALONE_PAGE_NUMBER_REGEX = Regex(
        """^(?:p(?:age|ág\.?)?\s*\d+(?:\s*(?:of|de|/)\s*\d+)?|folio\s*\d+|-\s*\d+\s*-|\[\s*\d+\s*]|\d{1,4})$""",
        RegexOption.IGNORE_CASE,
    )

    private val HYPHEN_LINE_SPLIT_REGEX = Regex("""(\p{L}{2,})-\s*\n\s*(\p{Ll}{2,})""")
    private val EXCESSIVE_NEWLINES_REGEX = Regex("""\n{2,}""")
    private val TABULAR_MULTICOLUMN_REGEX = Regex("""(?:\S+\s{4,}\S+\s{4,}\S+)|(?:\|.+\|.+\|)""")

    fun isSemanticSceneBreak(text: String): Boolean =
        SCENE_BREAK_REGEX.matches(text.trim())

    /**
     * Normalizes a single paragraph's internal whitespace and joins words hyphenated across line breaks.
     */
    fun normalizeParagraphText(
        raw: String,
        collapseInternalNewlines: Boolean = true,
    ): Pair<String, Int> {
        if (raw.isBlank()) return "" to 0
        var dehyphenations = 0
        var cleaned = HYPHEN_LINE_SPLIT_REGEX.replace(raw) { m ->
            dehyphenations++
            "${m.groupValues[1]}${m.groupValues[2]}"
        }
        cleaned = if (collapseInternalNewlines) {
            cleaned
                .replace(EXCESSIVE_NEWLINES_REGEX, " ")
                .replace('\n', ' ')
                .replace(Regex("""\s{2,}"""), " ")
                .trim()
        } else {
            cleaned.replace(EXCESSIVE_NEWLINES_REGEX, "\n").trim()
        }
        return cleaned to dehyphenations
    }

    /**
     * Reconstructs a single page's blocks for Adaptive Reflow or cleaned EPUB rendering.
     * In PDF mode, it also checks whether the first block is a continuation from the previous page
     * or the last block continues onto the next page, and strips running headers/footers.
     */
    fun buildReflowForPage(
        book: BookDocument,
        page: BookPage,
        normalizeSpaces: Boolean = true,
    ): ReflowReconstructionResult {
        val prevPage = book.pages.getOrNull(page.pageNumber - 2)
        val nextPage = book.pages.getOrNull(page.pageNumber)
        val windowPages = listOfNotNull(prevPage, page, nextPage)
        val fullResult = buildContinuousFlow(
            book = book.copy(pages = windowPages),
            normalizeSpaces = normalizeSpaces,
            preservePageBoundaries = true,
        )
        val pageItems = fullResult.items.filter { it.sourcePageNumber == page.pageNumber }
        val pageScanned = fullResult.scannedPageNumbers.filter { it == page.pageNumber }
        return fullResult.copy(
            items = pageItems,
            scannedPageNumbers = pageScanned,
        )
    }

    /**
     * Reconstructs continuous reading flow across all pages of [book] (or a subset of pages).
     * Never mutates the underlying [BookDocument] or file on disk.
     */
    fun buildContinuousFlow(
        book: BookDocument,
        normalizeSpaces: Boolean = true,
        preservePageBoundaries: Boolean = false,
    ): ReflowReconstructionResult {
        val isPdf = book.format == DocumentFormat.PDF
        val normalizedBookTitle = book.title.trim().lowercase()
        val items = mutableListOf<ReflowedBlockItem>()
        val scannedPages = mutableListOf<Int>()
        var strippedHeadersFooters = 0
        var mergedPageBreaks = 0
        var totalDehyphenated = 0
        var tabularLineCount = 0
        var totalTextBlocks = 0

        for (page in book.pages) {
            val rawBlocks = page.blocks
            val nonEmptyTextBlocks = rawBlocks.filter { it.style != BlockStyle.IMAGE && it.text.isNotBlank() }
            val imageBlocks = rawBlocks.filter { it.style == BlockStyle.IMAGE && !it.imagePath.isNullOrBlank() }

            // Detect scanned PDF page: has page image, but 0 extracted text blocks (and page > 2 so cover pages aren't false positives)
            if (isPdf && nonEmptyTextBlocks.isEmpty() && imageBlocks.isNotEmpty() && page.pageNumber > 2) {
                scannedPages.add(page.pageNumber)
            }

            for ((idx, block) in rawBlocks.withIndex()) {
                if (block.style == BlockStyle.IMAGE) {
                    // In PDF Adaptive Reflow mode, if a page already has extracted text blocks, skip the redundant full-page snapshot image
                    // so the reader displays clean reflowable text rather than duplicating the page image underneath!
                    val isRedundantPdfPageSnapshot = isPdf &&
                        nonEmptyTextBlocks.isNotEmpty() &&
                        (block.id.endsWith("_visual") || block.imagePath == page.renderedPageImagePath)
                    if (!isRedundantPdfPageSnapshot && !block.imagePath.isNullOrBlank()) {
                        items.add(
                            ReflowedBlockItem(
                                id = block.id,
                                sourcePageNumber = page.pageNumber,
                                chapterTitle = page.chapterTitle,
                                style = BlockStyle.IMAGE,
                                text = block.text,
                                imagePath = block.imagePath,
                            ),
                        )
                    }
                    continue
                }

                val rawTrimmed = block.text.trim()
                if (rawTrimmed.isEmpty()) continue

                // Check for tabular / multi-column patterns in PDF
                if (isPdf && TABULAR_MULTICOLUMN_REGEX.containsMatchIn(rawTrimmed)) {
                    tabularLineCount++
                }

                // Strip running headers / footers in PDF (first or last text block matching page number or repeated book title)
                val isFirstOrLastOnPage = idx == 0 || idx == rawBlocks.lastIndex
                if (isPdf && isFirstOrLastOnPage && isRunningHeaderOrFooter(rawTrimmed, normalizedBookTitle, page.pageNumber)) {
                    strippedHeadersFooters++
                    continue
                }

                // Check if this block is a semantic scene break (* * *, ---, ◇)
                if (isSemanticSceneBreak(rawTrimmed)) {
                    items.add(
                        ReflowedBlockItem(
                            id = block.id,
                            sourcePageNumber = page.pageNumber,
                            chapterTitle = page.chapterTitle,
                            style = BlockStyle.PARAGRAPH,
                            text = "* * *",
                            isSceneBreak = true,
                        ),
                    )
                    continue
                }

                val (cleanedText, dehyphCount) = normalizeParagraphText(
                    raw = rawTrimmed,
                    collapseInternalNewlines = normalizeSpaces,
                )
                totalDehyphenated += dehyphCount
                if (cleanedText.isBlank()) continue
                totalTextBlocks++

                // Check if this paragraph should merge with the previous paragraph across a PDF page break
                // or artificial mid-sentence split (Imagen 3 fix!)
                val prevItem = items.lastOrNull()
                if (prevItem != null &&
                    (!preservePageBoundaries || prevItem.sourcePageNumber == page.pageNumber) &&
                    !prevItem.isSceneBreak &&
                    prevItem.style == BlockStyle.PARAGRAPH &&
                    (block.style == BlockStyle.PARAGRAPH || (isPdf && block.style == BlockStyle.HEADING && looksLikeSentenceContinuation(prevItem.text, cleanedText))) &&
                    shouldMergeParagraphs(prevItem, cleanedText, page.pageNumber, isPdf, normalizeSpaces)
                ) {
                    val combinedText = if (prevItem.text.endsWith("-") && !prevItem.text.endsWith(" -") && !prevItem.text.endsWith("—")) {
                        totalDehyphenated++
                        prevItem.text.dropLast(1) + cleanedText
                    } else {
                        "${prevItem.text} $cleanedText"
                    }
                    if (prevItem.sourcePageNumber != page.pageNumber) {
                        mergedPageBreaks++
                    }
                    items[items.lastIndex] = prevItem.copy(text = combinedText)
                } else {
                    items.add(
                        ReflowedBlockItem(
                            id = block.id,
                            sourcePageNumber = page.pageNumber,
                            chapterTitle = page.chapterTitle,
                            style = block.style,
                            text = cleanedText,
                        ),
                    )
                }
            }
        }

        val unreliableRatio = if (totalTextBlocks > 0) tabularLineCount.toFloat() / totalTextBlocks.toFloat() else 0f
        val isReliable = !(isPdf && (tabularLineCount >= 2 || unreliableRatio > 0.20f))
        val warning = when {
            !isReliable -> "Se detectaron tablas o múltiples columnas en este PDF. Si el orden de lectura adaptable altera la estructura, puedes volver a la Vista Original con un clic."
            isPdf && scannedPages.isNotEmpty() -> "Se detectaron ${scannedPages.size} páginas escaneadas sin capa de texto (${scannedPages.take(4).joinToString(", ")}). Puedes ejecutar OCR para extraer texto adaptable."
            else -> null
        }

        return ReflowReconstructionResult(
            items = items,
            isReliable = isReliable,
            reliabilityWarning = warning,
            scannedPageNumbers = scannedPages,
            mergedPageBreakCount = mergedPageBreaks,
            strippedHeaderFooterCount = strippedHeadersFooters,
            dehyphenatedWordCount = totalDehyphenated,
        )
    }

    private fun isRunningHeaderOrFooter(
        text: String,
        normalizedBookTitle: String,
        pageNumber: Int,
    ): Boolean {
        val clean = text.trim()
        if (STANDALONE_PAGE_NUMBER_REGEX.matches(clean)) return true
        val lower = clean.lowercase()
        if (lower == normalizedBookTitle) return true
        if (lower == "$normalizedBookTitle — page $pageNumber" || lower == "$normalizedBookTitle - page $pageNumber") return true
        return false
    }

    private fun shouldMergeParagraphs(
        prev: ReflowedBlockItem,
        nextText: String,
        nextPageNumber: Int,
        isPdf: Boolean,
        normalizeSpaces: Boolean,
    ): Boolean {
        if (!normalizeSpaces && !isPdf) return false
        val prevTrimmed = prev.text.trimEnd()
        val nextTrimmed = nextText.trimStart()
        if (prevTrimmed.isEmpty() || nextTrimmed.isEmpty()) return false

        // Never merge if next starts a dialogue turn or bullet
        val nextFirst = nextTrimmed.first()
        if (nextFirst == '"' || nextFirst == '“' || nextFirst == '—' || nextFirst == '─' || nextFirst == '•' || nextFirst == '[') {
            return false
        }

        // Cross-page break in PDF: if previous page ended mid-sentence (not ending in terminal punctuation), merge!
        if (isPdf && prev.sourcePageNumber != nextPageNumber) {
            return looksLikeSentenceContinuation(prevTrimmed, nextTrimmed)
        }

        // Same-page split in PDF where a line wrap broke a sentence mid-flow
        if (isPdf && looksLikeSentenceContinuation(prevTrimmed, nextTrimmed)) {
            return true
        }

        return false
    }

    private fun looksLikeSentenceContinuation(prevTrimmed: String, nextTrimmed: String): Boolean {
        val endsWithTerminal = prevTrimmed.endsWith(".") ||
            prevTrimmed.endsWith(".\"") ||
            prevTrimmed.endsWith(".”") ||
            prevTrimmed.endsWith("!") ||
            prevTrimmed.endsWith("!\"") ||
            prevTrimmed.endsWith("!”") ||
            prevTrimmed.endsWith("?") ||
            prevTrimmed.endsWith("?\"") ||
            prevTrimmed.endsWith("?”") ||
            prevTrimmed.endsWith(":") ||
            prevTrimmed.endsWith("…")
        val nextStartsLower = nextTrimmed.firstOrNull()?.isLowerCase() == true
        val prevEndsHyphenOrComma = prevTrimmed.endsWith("-") || prevTrimmed.endsWith(",") || prevTrimmed.endsWith(";")
        return !endsWithTerminal || nextStartsLower || prevEndsHyphenOrComma
    }
}

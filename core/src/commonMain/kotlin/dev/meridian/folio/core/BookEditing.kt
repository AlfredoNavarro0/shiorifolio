package dev.meridian.folio.core

/** A single query occurrence inside a text block. */
data class TextMatch(
    val pageNumber: Int,
    val chapterTitle: String,
    val blockId: String,
    val startIndex: Int,
    val matchedText: String,
    val snippetBefore: String,
    val snippetAfter: String,
)

/** In-memory text search engine over the book's editable pages. */
object BookTextSearch {
    private const val SNIPPET_RADIUS = 48

    fun findMatches(
        pages: List<BookPage>,
        query: String,
        chapterTitle: String? = null,
        ignoreCase: Boolean = true,
    ): List<TextMatch> {
        if (query.isBlank()) return emptyList()
        val scope = chapterTitle?.let { ChapterEditing.cleanTitle(it) }
        val matches = mutableListOf<TextMatch>()
        for (page in pages) {
            if (scope != null && ChapterEditing.cleanTitle(page.chapterTitle) != scope) continue
            for (block in page.blocks) {
                if (block.style == BlockStyle.IMAGE || block.text.isBlank()) continue
                var index = block.text.indexOf(query, 0, ignoreCase)
                while (index >= 0) {
                    val end = index + query.length
                    matches.add(
                        TextMatch(
                            pageNumber = page.pageNumber,
                            chapterTitle = page.chapterTitle,
                            blockId = block.id,
                            startIndex = index,
                            matchedText = block.text.substring(index, end),
                            snippetBefore = block.text.substring((index - SNIPPET_RADIUS).coerceAtLeast(0), index),
                            snippetAfter = block.text.substring(
                                end.coerceAtMost(block.text.length),
                                (end + SNIPPET_RADIUS).coerceAtMost(block.text.length),
                            ),
                        ),
                    )
                    index = block.text.indexOf(query, index + query.length.coerceAtLeast(1), ignoreCase)
                }
            }
        }
        return matches
    }
}

data class ReplacementOutcome(
    val pages: List<BookPage>,
    val replacedCount: Int,
    val affectedPageNumbers: List<Int> = emptyList(),
)

/** Literal text replacement (no regular expressions). */
object BookTextReplace {
    fun replaceAll(
        pages: List<BookPage>,
        query: String,
        replacement: String,
        chapterTitle: String? = null,
        ignoreCase: Boolean = true,
    ): ReplacementOutcome {
        if (query.isEmpty()) return ReplacementOutcome(pages, 0)
        val scope = chapterTitle?.let { ChapterEditing.cleanTitle(it) }
        var count = 0
        val affected = mutableListOf<Int>()
        val updated = pages.map { page ->
            if (scope != null && ChapterEditing.cleanTitle(page.chapterTitle) != scope) {
                page
            } else {
                var pageCount = 0
                val blocks = page.blocks.map { block ->
                    if (block.style == BlockStyle.IMAGE || block.text.isEmpty()) {
                        block
                    } else {
                        val (replaced, occurrences) = replaceAllInText(block.text, query, replacement, ignoreCase)
                        pageCount += occurrences
                        if (occurrences > 0) block.copy(text = replaced) else block
                    }
                }
                if (pageCount > 0) {
                    count += pageCount
                    affected.add(page.pageNumber)
                    page.copy(blocks = blocks)
                } else {
                    page
                }
            }
        }
        return ReplacementOutcome(updated, count, affected)
    }

    fun replaceOne(
        pages: List<BookPage>,
        pageNumber: Int,
        blockId: String,
        startIndex: Int,
        query: String,
        replacement: String,
        ignoreCase: Boolean = true,
    ): ReplacementOutcome {
        if (query.isEmpty()) return ReplacementOutcome(pages, 0)
        var replaced = false
        val updated = pages.map { page ->
            if (page.pageNumber != pageNumber) return@map page
            val blocks = page.blocks.map { block ->
                if (replaced || block.id != blockId || block.style == BlockStyle.IMAGE) return@map block
                if (startIndex < 0 || startIndex + query.length > block.text.length) return@map block
                if (!block.text.regionMatches(startIndex, query, 0, query.length, ignoreCase)) return@map block
                replaced = true
                block.copy(text = block.text.substring(0, startIndex) + replacement + block.text.substring(startIndex + query.length))
            }
            if (replaced) page.copy(blocks = blocks) else page
        }
        return ReplacementOutcome(updated, if (replaced) 1 else 0, if (replaced) listOf(pageNumber) else emptyList())
    }

    private fun replaceAllInText(
        text: String,
        needle: String,
        replacement: String,
        ignoreCase: Boolean,
    ): Pair<String, Int> {
        var index = text.indexOf(needle, 0, ignoreCase)
        if (index < 0) return text to 0
        val sb = StringBuilder(text.length)
        var cursor = 0
        var occurrences = 0
        while (index >= 0) {
            sb.append(text, cursor, index)
            sb.append(replacement)
            occurrences++
            cursor = index + needle.length
            index = text.indexOf(needle, cursor, ignoreCase)
        }
        sb.append(text, cursor, text.length)
        return sb.toString() to occurrences
    }
}

/** Textual context around an annotation (neighborhood anchors). */
data class AnchorContext(val prefix: String, val suffix: String)

/**
 * Non-destructive annotation re-anchoring: every saved phrase is relocated inside the
 * current content using the phrase itself plus its context prefix/suffix. Phrases that
 * no longer exist in the book are flagged as orphaned for review.
 */
object AnnotationAnchorer {
    private const val CONTEXT_RADIUS = 48
    private const val CONTEXT_MATCH_LENGTH = 24

    fun anchorForPage(page: BookPage, phrase: String): AnchorContext? {
        val trimmed = phrase.trim()
        if (trimmed.isBlank()) return null
        val text = page.plainText
        val index = text.indexOf(trimmed)
        if (index < 0) return null
        return contextAt(text, index, trimmed.length)
    }

    fun reanchor(phrase: SavedPhrase, pages: List<BookPage>): SavedPhrase {
        val needle = phrase.phrase.trim()
        if (needle.isBlank() || pages.isEmpty()) return phrase

        data class Candidate(val page: BookPage, val context: AnchorContext, val score: Int)

        fun collect(ignoreCase: Boolean): List<Candidate> {
            val list = mutableListOf<Candidate>()
            for (page in pages) {
                val text = page.plainText
                var index = text.indexOf(needle, 0, ignoreCase)
                while (index >= 0) {
                    val context = contextAt(text, index, needle.length)
                    var score = 0
                    if (page.pageNumber == phrase.pageNumber) score += 1
                    if (phrase.anchorPrefix.isNotBlank() &&
                        context.prefix.endsWith(phrase.anchorPrefix.takeLast(CONTEXT_MATCH_LENGTH))
                    ) {
                        score += 4
                    }
                    if (phrase.anchorSuffix.isNotBlank() &&
                        context.suffix.startsWith(phrase.anchorSuffix.take(CONTEXT_MATCH_LENGTH))
                    ) {
                        score += 4
                    }
                    if (phrase.anchorChapterTitle.isNotBlank() &&
                        ChapterEditing.cleanTitle(page.chapterTitle) == ChapterEditing.cleanTitle(phrase.anchorChapterTitle)
                    ) {
                        score += 2
                    }
                    list.add(Candidate(page, context, score))
                    index = text.indexOf(needle, index + needle.length.coerceAtLeast(1), ignoreCase)
                }
            }
            return list
        }

        var candidates = collect(ignoreCase = false)
        if (candidates.isEmpty()) candidates = collect(ignoreCase = true)
        if (candidates.isEmpty()) {
            return if (phrase.anchorStatus == AnchorStatus.ORPHANED) phrase else phrase.copy(anchorStatus = AnchorStatus.ORPHANED)
        }
        val best = candidates.sortedWith(
            compareByDescending<Candidate> { it.score }
                .thenBy { if (it.page.pageNumber == phrase.pageNumber) 0 else 1 }
                .thenBy { it.page.pageNumber },
        ).first()
        // Without any evidence (same page, prefix/suffix, or chapter) the anchor is
        // broken even if the text still exists: flag it as orphaned so the reader
        // picks the right occurrence instead of a silent move.
        if (best.score == 0) {
            return phrase.copy(anchorStatus = AnchorStatus.ORPHANED)
        }
        return phrase.copy(
            pageNumber = best.page.pageNumber,
            anchorPrefix = best.context.prefix,
            anchorSuffix = best.context.suffix,
            anchorChapterTitle = best.page.chapterTitle,
            anchorStatus = AnchorStatus.ACTIVE,
        )
    }

    fun reanchorBook(phrases: List<SavedPhrase>, bookId: String, pages: List<BookPage>): List<SavedPhrase> =
        phrases.map { if (it.bookId == bookId) reanchor(it, pages) else it }

    private fun contextAt(text: String, index: Int, length: Int): AnchorContext {
        val prefixStart = (index - CONTEXT_RADIUS).coerceAtLeast(0)
        val suffixEnd = (index + length + CONTEXT_RADIUS).coerceAtMost(text.length)
        return AnchorContext(
            prefix = text.substring(prefixStart, index),
            suffix = text.substring(index + length, suffixEnd),
        )
    }
}

/** Chapter operations over the page list (title, order and TOC repair). */
object ChapterEditing {
    fun cleanTitle(raw: String): String = raw.substringBefore(" (p.").trim()

    fun chapterRange(pages: List<BookPage>, startPage: Int): IntRange? {
        val startIndex = pages.indexOfFirst { it.pageNumber == startPage }
        if (startIndex < 0) return null
        val title = cleanTitle(pages[startIndex].chapterTitle)
        var from = startIndex
        while (from > 0 && cleanTitle(pages[from - 1].chapterTitle) == title) from--
        var to = startIndex
        while (to + 1 < pages.size && cleanTitle(pages[to + 1].chapterTitle) == title) to++
        return from..to
    }

    fun renameChapter(pages: List<BookPage>, startPage: Int, newTitle: String): List<BookPage> {
        val trimmed = newTitle.trim()
        if (trimmed.isBlank()) return pages
        val range = chapterRange(pages, startPage) ?: return pages
        val oldClean = cleanTitle(pages[range.first].chapterTitle)
        return pages.mapIndexed { index, page ->
            when {
                index !in range -> page
                index == range.first -> {
                    val headingIdx = page.blocks.indexOfFirst { it.style == BlockStyle.HEADING }
                    val blocks = if (headingIdx >= 0 && page.blocks[headingIdx].text.trim() == oldClean) {
                        page.blocks.mapIndexed { bIdx, block -> if (bIdx == headingIdx) block.copy(text = trimmed) else block }
                    } else {
                        page.blocks
                    }
                    page.copy(chapterTitle = trimmed, blocks = blocks)
                }
                else -> page.copy(chapterTitle = trimmed)
            }
        }
    }

    fun moveChapter(pages: List<BookPage>, startPage: Int, upwards: Boolean): List<BookPage>? {
        val range = chapterRange(pages, startPage) ?: return null
        if (upwards) {
            if (range.first == 0) return null
            val prev = chapterRange(pages, pages[range.first - 1].pageNumber) ?: return null
            if (prev.last >= range.first) return null
            val result = pages.subList(0, prev.first) +
                pages.subList(range.first, range.last + 1) +
                pages.subList(prev.first, range.first) +
                pages.subList(range.last + 1, pages.size)
            return renumber(result)
        }
        if (range.last == pages.size - 1) return null
        val next = chapterRange(pages, pages[range.last + 1].pageNumber) ?: return null
        if (next.first <= range.last) return null
        val result = pages.subList(0, range.first) +
            pages.subList(next.first, next.last + 1) +
            pages.subList(range.first, range.last + 1) +
            pages.subList(next.last + 1, pages.size)
        return renumber(result)
    }

    /**
     * Rebuilds chapter titles from the real content headings, trimming "(p. N)"
     * continuations. Pages without a heading stay in the current chapter unless
     * their title is a distinct section with no explicit continuation.
     */
    fun repairTitles(pages: List<BookPage>): List<BookPage> {
        var carried = ""
        return pages.map { page ->
            val heading = page.blocks.firstOrNull { it.style == BlockStyle.HEADING && it.text.isNotBlank() }?.text?.trim()
            val existingClean = cleanTitle(page.chapterTitle)
            val title = when {
                !heading.isNullOrBlank() -> {
                    carried = heading
                    heading
                }
                existingClean.isBlank() -> carried
                existingClean == carried -> carried
                page.chapterTitle.contains(" (p.") -> carried
                else -> {
                    carried = existingClean
                    existingClean
                }
            }
            when {
                title.isBlank() || title == page.chapterTitle -> page
                else -> page.copy(chapterTitle = title)
            }
        }
    }

    private fun renumber(pages: List<BookPage>): List<BookPage> =
        pages.mapIndexed { index, page -> page.copy(pageNumber = index + 1) }
}

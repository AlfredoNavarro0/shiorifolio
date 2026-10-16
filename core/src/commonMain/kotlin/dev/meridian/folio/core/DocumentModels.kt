package dev.meridian.folio.core

import kotlinx.serialization.Serializable

@Serializable
enum class DocumentFormat(val label: String, val extension: String) {
    PDF("PDF Folio", "pdf"),
    EPUB("EPUB Archive", "epub"),
}

@Serializable
enum class PageViewMode(val label: String, val shortTag: String, val stitchLabel: String, val description: String) {
    ONE_COLUMN("1 Column", "1-Col", "Simple", "Focused single-column manuscript page"),
    TWO_COLUMNS("2 Columns", "2-Col", "Doble página", "Side-by-side dual-column folio spread"),
    STRETCH("Stretch", "Stretch", "Stretch", "Full-viewport proportional page stretch"),
    WIDE("Wide", "Wide", "Ancho", "Wide horizontal reading canvas with generous measure"),
}

@Serializable
enum class ReaderTheme(val label: String, val stitchLabel: String, val swatchHex: Long) {
    WHITE("White (Kindle Warm)", "Kindle Cálido", 0xFFFCF9F3),
    SEPIA("Sepia (Archival Parchment)", "Sepia", 0xFFF5EDDB),
    SAGE("Sage (Meridian Salvia)", "Salvia", 0xFFEEF3EE),
    DARK("Dark (Engraved Nocturne)", "Oscuro", 0xFF231F1C),
}

@Serializable
enum class BlockStyle(val label: String) {
    HEADING("Heading"),
    PARAGRAPH("Paragraph"),
    QUOTE("Engraved Quote"),
    CALLOUT("Editorial Note"),
    IMAGE("Illustration"),
}

@Serializable
enum class HighlightTone(val label: String, val hexLight: Long, val hexDark: Long) {
    SAGE("Sage Ink", 0xFFD2DEC9, 0xFF2B4233),
    AMBER("Gilded Amber", 0xFFF4DF9E, 0xFF52431B),
    TERRACOTTA("Sienna Clay", 0xFFF2C8BC, 0xFF542B22),
    SLATE("Viaduct Slate", 0xFFCBD9E6, 0xFF243746),
}

@Serializable
data class ContentBlock(
    val id: String,
    val style: BlockStyle = BlockStyle.PARAGRAPH,
    val text: String,
    val imagePath: String? = null,
) {
    val wordCount: Int
        get() = if (style == BlockStyle.IMAGE && text.isBlank()) {
            0
        } else {
            text.trim().split(Regex("\\s+")).count { it.isNotBlank() }
        }
}

@Serializable
data class BookPage(
    val pageNumber: Int,
    val chapterTitle: String,
    val blocks: List<ContentBlock>,
    val renderedPageImagePath: String? = null,
    val lastEditedEpochMs: Long? = null,
) {
    val wordCount: Int
        get() = blocks.sumOf { it.wordCount }

    val plainText: String
        get() = blocks.filter { it.style != BlockStyle.IMAGE }.joinToString("\n\n") { it.text }
}

@Serializable
data class ChapterTocEntry(
    val title: String,
    val startPage: Int,
)

@Serializable
data class BookDocument(
    val id: String,
    val title: String,
    val subtitle: String = "",
    val author: String,
    val format: DocumentFormat,
    val filePath: String? = null,
    val pages: List<BookPage>,
    val currentPage: Int = 1,
    val parserVersion: Int = 1,
    val createdAtMs: Long = 0L,
    val updatedAtMs: Long = 0L,
) {
    val totalPages: Int
        get() = pages.size.coerceAtLeast(1)

    val totalWords: Int
        get() = pages.sumOf { it.wordCount }

    val tableOfContents: List<ChapterTocEntry>
        get() {
            val result = mutableListOf<ChapterTocEntry>()
            var lastTitle = ""
            for (p in pages) {
                val clean = p.chapterTitle.substringBefore(" (p.").trim()
                if (clean.isNotBlank() && clean != lastTitle) {
                    result.add(ChapterTocEntry(title = clean, startPage = p.pageNumber))
                    lastTitle = clean
                }
            }
            return result
        }
}

@Serializable
data class PageReminder(
    val bookId: String,
    val bookTitle: String,
    val pageNumber: Int,
    val note: String,
    val targetLabel: String = "Next Session",
    val createdAtMs: Long,
)

@Serializable
data class SavedPhrase(
    val id: String,
    val bookId: String,
    val bookTitle: String,
    val pageNumber: Int,
    val phrase: String,
    val contextSentence: String,
    val note: String = "",
    val tone: HighlightTone = HighlightTone.SAGE,
    val createdAtMs: Long,
)

@Serializable
data class PageReadingStat(
    val pageNumber: Int,
    val secondsSpent: Double = 0.0,
    val visitCount: Int = 0,
    val wordsOnPage: Int = 0,
)

@Serializable
data class BookReadingTelemetry(
    val bookId: String,
    val totalActiveSeconds: Double = 0.0,
    val pagesVisited: Map<Int, PageReadingStat> = emptyMap(),
    val recentPagePaceSeconds: List<Double> = emptyList(),
    val recentWpmSamples: List<Double> = emptyList(),
    val recalculationCount: Int = 0,
    val lastReadEpochMs: Long = 0L,
)

@Serializable
data class FinishTimePrediction(
    val bookId: String,
    val currentPage: Int,
    val totalPages: Int,
    val remainingPages: Int,
    val remainingWords: Int,
    val activeReadingSeconds: Double,
    val estimatedSecondsPerPage: Double,
    val estimatedWordsPerMinute: Double,
    val estimatedRemainingSeconds: Double,
    val completionFraction: Float,
    val recalculationCount: Int,
    val paceTrendDeltaPercent: Double,
    val confidenceLabel: String,
) {
    fun formattedRemainingTime(): String = formatDurationSeconds(estimatedRemainingSeconds)
    fun formattedActiveTime(): String = formatDurationSeconds(activeReadingSeconds)
    fun formattedPacePerPage(): String = "${estimatedSecondsPerPage.toInt()}s / page"
    fun formattedWpm(): String = "${estimatedWordsPerMinute.toInt()} WPM"

    companion object {
        fun formatDurationSeconds(seconds: Double): String {
            val totalSec = seconds.toLong().coerceAtLeast(0L)
            val hrs = totalSec / 3600
            val mins = (totalSec % 3600) / 60
            val secs = totalSec % 60
            return when {
                hrs > 0 -> "${hrs}h ${mins}m ${secs}s"
                mins > 0 -> "${mins}m ${secs}s"
                else -> "${secs}s"
            }
        }
    }
}

@Serializable
data class FolioPersistedState(
    val documents: List<BookDocument> = emptyList(),
    val selectedBookId: String? = null,
    val viewMode: PageViewMode = PageViewMode.TWO_COLUMNS,
    val theme: ReaderTheme = ReaderTheme.WHITE,
    val fontSizePt: Float = 17f,
    val lineSpacingMultiplier: Float = 1.8f,
    val reminders: Map<String, PageReminder> = emptyMap(),
    val savedPhrases: List<SavedPhrase> = emptyList(),
    val telemetry: Map<String, BookReadingTelemetry> = emptyMap(),
)

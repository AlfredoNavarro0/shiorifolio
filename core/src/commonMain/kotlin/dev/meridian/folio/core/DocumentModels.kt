package dev.meridian.folio.core

import kotlinx.serialization.Serializable

@Serializable
enum class DocumentFormat(val label: String, val extension: String) {
    PDF("PDF Folio", "pdf"),
    EPUB("EPUB Archive", "epub"),
}

@Serializable
enum class PageViewMode(val label: String, val shortTag: String, val stitchLabel: String, val description: String) {
    ONE_COLUMN("Página Individual", "1-Col", "Simple", "Vista enfocada de una sola página"),
    TWO_COLUMNS("Doble Página", "2-Col", "Doble página", "Pliego abierto de dos páginas lado a lado"),
    VERTICAL_PAGINATED("Lectura Paginada", "V-Págs", "Paginado", "Avance por páginas completas sin desplazamiento dentro de la hoja"),
    VERTICAL_CONTINUOUS("Desplazamiento Continuo", "V-Cont", "Continuo", "Desplazamiento vertical continuo de lectura seguida"),
    REFLOW_TEXT("Texto Adaptable (Reflujo)", "Reflujo", "Reflujo", "Texto adaptable reconstruido para lectura fluida"),
    STRETCH("Stretch", "Stretch", "Stretch", "Full-viewport proportional page stretch"),
    WIDE("Wide", "Wide", "Ancho", "Wide horizontal reading canvas with generous measure"),
}

@Serializable
enum class PageFitMode(val label: String, val shortLabel: String) {
    FIT_PAGE("Ajustar a la página", "Ajustar Pág."),
    FIT_WIDTH("Ajustar al ancho", "Ajustar Ancho"),
    MANUAL_ZOOM("Zoom manual", "Zoom"),
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
    val libraryStatus: LibraryStatus = LibraryStatus.PENDING,
    val collectionIds: Set<String> = emptySet(),
    val completedAtMs: Long? = null,
    val contentSha256: String? = null,
    val sources: List<LibrarySource> = emptyList(),
    val isMissingOnDisk: Boolean = false,
    val language: String = "",
    val coverImagePath: String? = null,
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
enum class AnchorStatus(val label: String) {
    ACTIVE("Anclada"),
    ORPHANED("Huérfana"),
}

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
    val anchorPrefix: String = "",
    val anchorSuffix: String = "",
    val anchorChapterTitle: String = "",
    val anchorStatus: AnchorStatus = AnchorStatus.ACTIVE,
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
enum class PdfPageColorMode(val label: String, val shortLabel: String) {
    ORIGINAL("Original (Fiel al PDF)", "Original"),
    THEME_TINT("Tinte del Tema (Papel / Oscuro)", "Tinte Tema"),
    SMART_DARK("Noche Invertido (Alto Contraste)", "Invertir"),
}

@Serializable
data class KeyBindingSpec(
    val keyName: String,
    val ctrl: Boolean = false,
    val shift: Boolean = false,
    val alt: Boolean = false,
) {
    fun displayLabel(): String {
        val parts = mutableListOf<String>()
        if (ctrl) parts.add("Ctrl")
        if (alt) parts.add("Alt")
        if (shift) parts.add("Shift")
        val friendlyKey = when (keyName) {
            "DirectionLeft" -> "←"
            "DirectionRight" -> "→"
            "DirectionUp" -> "↑"
            "DirectionDown" -> "↓"
            "PageUp" -> "Page Up"
            "PageDown" -> "Page Down"
            else -> keyName
        }
        parts.add(friendlyKey)
        return parts.joinToString(" + ")
    }

    companion object {
        fun defaultBindings(): Map<ReaderAction, KeyBindingSpec> =
            ReaderAction.entries.associateWith { it.defaultSpec }
    }
}

@Serializable
enum class ReaderAction(
    val label: String,
    val description: String,
    val defaultSpec: KeyBindingSpec,
) {
    NEXT_PAGE(
        label = "Página Siguiente",
        description = "Avanzar folio o doble página (también Flechas → / ↓)",
        defaultSpec = KeyBindingSpec(keyName = "PageDown"),
    ),
    PREV_PAGE(
        label = "Página Anterior",
        description = "Retroceder folio o doble página (también Flechas ← / ↑)",
        defaultSpec = KeyBindingSpec(keyName = "PageUp"),
    ),
    NEXT_TAB(
        label = "Pestaña Siguiente",
        description = "Cambiar a la siguiente pestaña de la aplicación",
        defaultSpec = KeyBindingSpec(keyName = "Tab", ctrl = true),
    ),
    PREV_TAB(
        label = "Pestaña Anterior",
        description = "Cambiar a la pestaña anterior de la aplicación",
        defaultSpec = KeyBindingSpec(keyName = "Tab", ctrl = true, shift = true),
    ),
    NAV_BACK(
        label = "Volver al Punto Anterior (Historial)",
        description = "Regresar tras abrir un capítulo, enlace, nota o recordatorio",
        defaultSpec = KeyBindingSpec(keyName = "DirectionLeft", alt = true),
    ),
    NAV_FORWARD(
        label = "Avanzar en Historial",
        description = "Rehacer salto después de volver al punto anterior",
        defaultSpec = KeyBindingSpec(keyName = "DirectionRight", alt = true),
    ),
    TOGGLE_ZEN_MODE(
        label = "Modo Zen (Ocultar Controles)",
        description = "Ocultar o mostrar las barras y controles centrales",
        defaultSpec = KeyBindingSpec(keyName = "Z", ctrl = true, shift = true),
    ),
    TOGGLE_FULLSCREEN(
        label = "Pantalla Completa",
        description = "Alternar pantalla completa de la ventana del sistema",
        defaultSpec = KeyBindingSpec(keyName = "F11"),
    ),
    CYCLE_VIEW_MODE(
        label = "Cambiar Modo de Vista",
        description = "Alternar entre Simple, Doble página, Stretch y Ancho",
        defaultSpec = KeyBindingSpec(keyName = "M", ctrl = true),
    ),
    TOGGLE_THEME(
        label = "Alternar Tema Claro / Oscuro",
        description = "Cambiar rápidamente entre el tema claro y oscuro",
        defaultSpec = KeyBindingSpec(keyName = "T", ctrl = true),
    ),
    UNDO_EDIT(
        label = "Deshacer Edición",
        description = "Revertir el último cambio de contenido de esta sesión de edición",
        defaultSpec = KeyBindingSpec(keyName = "Z", ctrl = true),
    ),
    REDO_EDIT(
        label = "Rehacer Edición",
        description = "Reaplicar el último cambio de contenido deshecho",
        defaultSpec = KeyBindingSpec(keyName = "Y", ctrl = true),
    ),
    FIND_IN_BOOK(
        label = "Buscar en el Libro",
        description = "Abrir buscar y reemplazar sobre el libro abierto",
        defaultSpec = KeyBindingSpec(keyName = "F", ctrl = true),
    ),
}

@Serializable
data class NavigationHistoryEntry(
    val bookId: String,
    val pageNumber: Int,
    val reason: String,
    val timestampMs: Long = 0L,
)

@Serializable
enum class UiFontFamilyOption(val label: String, val shortLabel: String) {
    SANS_SERIF("Sans-Serif (Nítida UI)", "Sans"),
    SERIF("Serif (Editorial Clásica)", "Serif"),
    MONOSPACE("Monoespaciada (Técnica)", "Mono"),
}

@Serializable
enum class UiFontWeightOption(val label: String, val shortLabel: String) {
    REGULAR("Normal (400)", "Normal"),
    MEDIUM("Medio (500)", "Medio"),
    SEMIBOLD("Seminegrita (600)", "Seminegrita"),
}

@Serializable
data class UiAppearanceSettings(
    val fontFamily: UiFontFamilyOption = UiFontFamilyOption.SANS_SERIF,
    val fontSizeSp: Float = 13f,
    val fontWeight: UiFontWeightOption = UiFontWeightOption.MEDIUM,
    val uiScale: Float = 1.0f,
    val highContrastText: Boolean = false,
)

@Serializable
enum class BookFontFamilyOption(val label: String, val shortLabel: String) {
    SERIF("Serif Literaria", "Serif"),
    SANS_SERIF("Sans-Serif Limpia", "Sans"),
    MONOSPACE("Monoespaciada", "Mono"),
}

@Serializable
enum class BookTextAlignment(val label: String, val shortLabel: String) {
    JUSTIFY("Justificado", "Justificado"),
    START("Alineado a la Izquierda", "Izquierda"),
    CENTER("Centrado", "Centrado"),
}

@Serializable
data class BookTypographySettings(
    val fontFamily: BookFontFamilyOption = BookFontFamilyOption.SERIF,
    val paragraphSpacingDp: Float = 14f,
    val horizontalMarginDp: Float = 32f,
    val verticalMarginDp: Float = 18f,
    val textAlignment: BookTextAlignment = BookTextAlignment.JUSTIFY,
    val pdfPreferReflowText: Boolean = false,
    val normalizeExcessiveSpaces: Boolean = true,
)

@Serializable
data class BookViewPreference(
    val bookId: String,
    val viewMode: PageViewMode = PageViewMode.TWO_COLUMNS,
    val fitMode: PageFitMode = PageFitMode.FIT_PAGE,
    val zoomScale: Float = 1.0f,
    val lastPage: Int = 1,
    val scrollOffsetPx: Int = 0,
    val pdfPreferReflowText: Boolean = false,
)

@Serializable
data class FolioPersistedState(
    val watchedFolders: List<WatchedFolder> = emptyList(),
    val collections: List<LibraryCollection> = emptyList(),
    val libraryPreferences: LibraryPreferences = LibraryPreferences(),
    val excludedLibraryPaths: Set<String> = emptySet(),
    val documents: List<BookDocument> = emptyList(),
    val selectedBookId: String? = null,
    val viewMode: PageViewMode = PageViewMode.TWO_COLUMNS,
    val fitMode: PageFitMode = PageFitMode.FIT_PAGE,
    val zoomScale: Float = 1.0f,
    val theme: ReaderTheme = ReaderTheme.WHITE,
    val pdfPageColorMode: PdfPageColorMode = PdfPageColorMode.THEME_TINT,
    val fontSizePt: Float = 17f,
    val lineSpacingMultiplier: Float = 1.8f,
    val uiAppearance: UiAppearanceSettings = UiAppearanceSettings(),
    val bookTypography: BookTypographySettings = BookTypographySettings(),
    val bookPreferences: Map<String, BookViewPreference> = emptyMap(),
    val reminders: Map<String, PageReminder> = emptyMap(),
    val savedPhrases: List<SavedPhrase> = emptyList(),
    val telemetry: Map<String, BookReadingTelemetry> = emptyMap(),
    val keyBindings: Map<ReaderAction, KeyBindingSpec> = KeyBindingSpec.defaultBindings(),
    val navigationBackStack: List<NavigationHistoryEntry> = emptyList(),
    val navigationForwardStack: List<NavigationHistoryEntry> = emptyList(),
)

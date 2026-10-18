package dev.meridian.folio.core

import kotlinx.serialization.Serializable

/**
 * Portable personal-data backup for ShioriFolio.
 * Books are identified by their SHA-256 fingerprint so they can be
 * re-associated on another device after importing the same files.
 */
@Serializable
data class BackupBundle(
    val format: String = "shiorifolio-backup",
    val version: Int = 1,
    val exportedAtMs: Long = 0L,
    val documents: List<BackupDocumentEntry> = emptyList(),
    val savedPhrases: List<BackupPhraseEntry> = emptyList(),
    val reminders: List<BackupReminderEntry> = emptyList(),
    val telemetry: List<BackupTelemetryEntry> = emptyList(),
    val collections: List<LibraryCollection> = emptyList(),
    val keyBindings: Map<ReaderAction, KeyBindingSpec> = emptyMap(),
    val uiAppearance: UiAppearanceSettings = UiAppearanceSettings(),
    val bookTypography: BookTypographySettings = BookTypographySettings(),
    val libraryPreferences: LibraryPreferences = LibraryPreferences(),
    val readerSettings: BackupReaderSettings = BackupReaderSettings(),
)

@Serializable
data class BackupReaderSettings(
    val theme: ReaderTheme = ReaderTheme.WHITE,
    val pdfPageColorMode: PdfPageColorMode = PdfPageColorMode.THEME_TINT,
    val viewMode: PageViewMode = PageViewMode.TWO_COLUMNS,
    val fitMode: PageFitMode = PageFitMode.FIT_PAGE,
    val zoomScale: Float = 1f,
    val fontSizePt: Float = 17f,
    val lineSpacingMultiplier: Float = 1.8f,
)

@Serializable
data class BackupDocumentEntry(
    val sha256: String = "",
    val title: String = "",
    val author: String = "",
    val language: String = "",
    val currentPage: Int = 1,
    val libraryStatus: LibraryStatus = LibraryStatus.PENDING,
    val completedAtMs: Long? = null,
    val collectionIds: Set<String> = emptySet(),
    val createdAtMs: Long = 0L,
    val viewPreference: BookViewPreference? = null,
)

@Serializable
data class BackupPhraseEntry(
    val id: String = "",
    val bookSha256: String = "",
    val bookTitle: String = "",
    val pageNumber: Int = 1,
    val phrase: String = "",
    val contextSentence: String = "",
    val note: String = "",
    val tone: HighlightTone = HighlightTone.SAGE,
    val createdAtMs: Long = 0L,
    val anchorPrefix: String = "",
    val anchorSuffix: String = "",
    val anchorChapterTitle: String = "",
    val anchorStatus: AnchorStatus = AnchorStatus.ACTIVE,
)

@Serializable
data class BackupReminderEntry(
    val bookSha256: String = "",
    val bookTitle: String = "",
    val pageNumber: Int = 1,
    val note: String = "",
    val targetLabel: String = "Next Session",
    val createdAtMs: Long = 0L,
)

@Serializable
data class BackupTelemetryEntry(
    val bookSha256: String = "",
    val telemetry: BookReadingTelemetry = BookReadingTelemetry(bookId = ""),
)

data class BackupExportSummary(
    val booksIncluded: Int = 0,
    val booksSkipped: Int = 0,
    val phrasesIncluded: Int = 0,
    val phrasesSkipped: Int = 0,
    val remindersIncluded: Int = 0,
    val collectionsIncluded: Int = 0,
)

data class BackupImportSummary(
    val booksMatched: Int = 0,
    val booksMissing: Int = 0,
    val phrasesRestored: Int = 0,
    val phrasesSkipped: Int = 0,
    val remindersRestored: Int = 0,
    val remindersSkipped: Int = 0,
    val collectionsCreated: Int = 0,
    val telemetryRestored: Int = 0,
    val settingsApplied: Boolean = false,
    val missingBookTitles: List<String> = emptyList(),
)

/** Preview shown to confirm a restore before applying a backup. */
data class BackupPeek(
    val exportedAtMs: Long = 0L,
    val documentCount: Int = 0,
    val phraseCount: Int = 0,
    val reminderCount: Int = 0,
    val collectionCount: Int = 0,
)

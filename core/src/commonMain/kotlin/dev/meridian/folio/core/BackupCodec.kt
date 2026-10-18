package dev.meridian.folio.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Serialization and application of the portable personal-data backup.
 * The backup carries no book files: only reader state, re-associated
 * by SHA-256 fingerprint when the same files are imported again.
 */
object BackupCodec {
    const val FORMAT_ID = "shiorifolio-backup"
    const val VERSION = 1

    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    fun encode(bundle: BackupBundle): String = json.encodeToString(BackupBundle.serializer(), bundle)

    /** Throws [IllegalArgumentException] when the text is not a valid ShioriFolio backup. */
    fun decode(text: String): BackupBundle {
        val element = runCatching { json.parseToJsonElement(text) }.getOrNull()
        val obj = element as? JsonObject
        val declaredFormat = obj?.get("format")?.let { runCatching { it.jsonPrimitive.content }.getOrNull() }
        require(declaredFormat == FORMAT_ID) { "El archivo no es un respaldo de ShioriFolio válido" }
        val bundle = runCatching { json.decodeFromJsonElement(BackupBundle.serializer(), obj) }
            .getOrElse { throw IllegalArgumentException("El archivo no es un respaldo de ShioriFolio válido") }
        require(bundle.version <= VERSION) { "El respaldo proviene de una versión más reciente de ShioriFolio" }
        return bundle
    }

    fun buildBundle(
        state: FolioPersistedState,
        exportedAtMs: Long,
    ): Pair<BackupBundle, BackupExportSummary> {
        val shaById = state.documents.mapNotNull { doc -> doc.contentSha256?.let { doc.id to it } }.toMap()
        val includedDocs = state.documents.filter { it.contentSha256 != null }
        val skippedBooks = state.documents.size - includedDocs.size

        var phrasesSkipped = 0
        val phrases = state.savedPhrases.mapNotNull { phrase ->
            val sha = shaById[phrase.bookId]
            if (sha == null) {
                phrasesSkipped++
                null
            } else {
                BackupPhraseEntry(
                    id = phrase.id,
                    bookSha256 = sha,
                    bookTitle = phrase.bookTitle,
                    pageNumber = phrase.pageNumber,
                    phrase = phrase.phrase,
                    contextSentence = phrase.contextSentence,
                    note = phrase.note,
                    tone = phrase.tone,
                    createdAtMs = phrase.createdAtMs,
                    anchorPrefix = phrase.anchorPrefix,
                    anchorSuffix = phrase.anchorSuffix,
                    anchorChapterTitle = phrase.anchorChapterTitle,
                    anchorStatus = phrase.anchorStatus,
                )
            }
        }

        var remindersSkipped = 0
        val reminders = state.reminders.values.mapNotNull { reminder ->
            val sha = shaById[reminder.bookId]
            if (sha == null) {
                remindersSkipped++
                null
            } else {
                BackupReminderEntry(
                    bookSha256 = sha,
                    bookTitle = reminder.bookTitle,
                    pageNumber = reminder.pageNumber,
                    note = reminder.note,
                    targetLabel = reminder.targetLabel,
                    createdAtMs = reminder.createdAtMs,
                )
            }
        }

        val telemetry = includedDocs.map { doc ->
            BackupTelemetryEntry(
                bookSha256 = doc.contentSha256.orEmpty(),
                telemetry = state.telemetry[doc.id] ?: BookReadingTelemetry(bookId = doc.id),
            )
        }

        val bundle = BackupBundle(
            exportedAtMs = exportedAtMs,
            documents = includedDocs.map { doc ->
                BackupDocumentEntry(
                    sha256 = doc.contentSha256.orEmpty(),
                    title = doc.title,
                    author = doc.author,
                    language = doc.language,
                    currentPage = doc.currentPage,
                    libraryStatus = doc.libraryStatus,
                    completedAtMs = doc.completedAtMs,
                    collectionIds = doc.collectionIds,
                    createdAtMs = doc.createdAtMs,
                    viewPreference = state.bookPreferences[doc.id],
                )
            },
            savedPhrases = phrases,
            reminders = reminders,
            telemetry = telemetry,
            collections = state.collections,
            keyBindings = state.keyBindings,
            uiAppearance = state.uiAppearance,
            bookTypography = state.bookTypography,
            libraryPreferences = state.libraryPreferences,
            readerSettings = BackupReaderSettings(
                theme = state.theme,
                pdfPageColorMode = state.pdfPageColorMode,
                viewMode = state.viewMode,
                fitMode = state.fitMode,
                zoomScale = state.zoomScale,
                fontSizePt = state.fontSizePt,
                lineSpacingMultiplier = state.lineSpacingMultiplier,
            ),
        )
        return bundle to BackupExportSummary(
            booksIncluded = includedDocs.size,
            booksSkipped = skippedBooks,
            phrasesIncluded = phrases.size,
            phrasesSkipped = phrasesSkipped,
            remindersIncluded = reminders.size,
            collectionsIncluded = state.collections.size,
        )
    }

    fun applyBundle(
        current: FolioPersistedState,
        bundle: BackupBundle,
        nowMs: Long,
    ): Pair<FolioPersistedState, BackupImportSummary> {
        // Collections: re-associated by name; missing ones are created.
        var collectionsCreated = 0
        val collectionIdMap = mutableMapOf<String, String>()
        val collections = current.collections.toMutableList()
        for (collection in bundle.collections) {
            if (collection.name.isBlank()) continue
            val existing = collections.firstOrNull { it.name.equals(collection.name, ignoreCase = true) }
            if (existing != null) {
                collectionIdMap[collection.id] = existing.id
            } else {
                val newId = "collection_${nowMs}_${collection.name.hashCode()}"
                collections.add(LibraryCollection(newId, collection.name))
                collectionIdMap[collection.id] = newId
                collectionsCreated++
            }
        }

        val docsBySha = current.documents.mapNotNull { doc -> doc.contentSha256?.let { it to doc } }
            .toMap()
        val documentEntries = bundle.documents.distinctBy { it.sha256 }
        val phraseGroups = bundle.savedPhrases.groupBy { it.bookSha256 }
        val reminderGroups = bundle.reminders.groupBy { it.bookSha256 }
        val telemetryBySha = bundle.telemetry.associateBy { it.bookSha256 }

        var booksMatched = 0
        var booksMissing = 0
        var phrasesRestored = 0
        var remindersRestored = 0
        var telemetryRestored = 0
        val missingTitles = mutableListOf<String>()

        val documentUpdates = mutableMapOf<String, BookDocument>()
        val phrasesByLocalId = mutableMapOf<String, List<SavedPhrase>>()
        var reminders = current.reminders
        var telemetry = current.telemetry
        val preferences = current.bookPreferences.toMutableMap()

        for (entry in documentEntries) {
            val local = docsBySha[entry.sha256]
            if (local == null) {
                booksMissing++
                missingTitles.add(entry.title.ifBlank { entry.sha256.take(12) })
                continue
            }
            booksMatched++
            val clampedPage = entry.currentPage.coerceIn(1, local.totalPages)
            documentUpdates[local.id] = local.copy(
                currentPage = clampedPage,
                libraryStatus = entry.libraryStatus,
                completedAtMs = entry.completedAtMs,
                collectionIds = entry.collectionIds.mapNotNull { collectionIdMap[it] }.toSet(),
                createdAtMs = entry.createdAtMs.takeIf { it > 0 } ?: local.createdAtMs,
                language = entry.language.takeIf { it.isNotBlank() } ?: local.language,
            )
            entry.viewPreference?.let { pref ->
                preferences[local.id] = pref.copy(bookId = local.id, lastPage = clampedPage)
            }
            val importedPhrases = phraseGroups[entry.sha256].orEmpty().map { phrase ->
                SavedPhrase(
                    id = phrase.id.ifBlank { "ph_${nowMs}_${phrase.phrase.hashCode().toUInt()}" },
                    bookId = local.id,
                    bookTitle = local.title,
                    pageNumber = phrase.pageNumber.coerceIn(1, local.totalPages),
                    phrase = phrase.phrase,
                    contextSentence = phrase.contextSentence,
                    note = phrase.note,
                    tone = phrase.tone,
                    createdAtMs = phrase.createdAtMs,
                    anchorPrefix = phrase.anchorPrefix,
                    anchorSuffix = phrase.anchorSuffix,
                    anchorChapterTitle = phrase.anchorChapterTitle,
                    anchorStatus = phrase.anchorStatus,
                )
            }
            if (importedPhrases.isNotEmpty()) {
                phrasesByLocalId[local.id] = AnnotationAnchorer.reanchorBook(importedPhrases, local.id, local.pages)
                phrasesRestored += importedPhrases.size
            }
            reminderGroups[entry.sha256]?.firstOrNull()?.let { reminder ->
                reminders = reminders + (
                    local.id to PageReminder(
                        bookId = local.id,
                        bookTitle = local.title,
                        pageNumber = reminder.pageNumber.coerceIn(1, local.totalPages),
                        note = reminder.note,
                        targetLabel = reminder.targetLabel,
                        createdAtMs = reminder.createdAtMs,
                    )
                )
                remindersRestored++
            }
            telemetryBySha[entry.sha256]?.let { entryTelemetry ->
                telemetry = telemetry + (local.id to entryTelemetry.telemetry.copy(bookId = local.id))
                telemetryRestored++
            }
        }

        val phrasesSkipped = bundle.savedPhrases.count { it.bookSha256 !in docsBySha }
        val remindersSkipped = bundle.reminders.count { it.bookSha256 !in docsBySha }

        // Replace each restored book's phrases keeping their list position.
        val seenBooks = mutableSetOf<String>()
        val rebuiltPhrases = mutableListOf<SavedPhrase>()
        for (existing in current.savedPhrases) {
            val group = phrasesByLocalId[existing.bookId]
            if (group == null) {
                rebuiltPhrases.add(existing)
            } else if (seenBooks.add(existing.bookId) && group.isNotEmpty()) {
                rebuiltPhrases.addAll(group)
            }
        }
        phrasesByLocalId.filterKeys { it !in seenBooks }.values.forEach { rebuiltPhrases.addAll(it) }

        val updated = current.copy(
            collections = collections,
            documents = current.documents.map { documentUpdates[it.id] ?: it },
            savedPhrases = rebuiltPhrases,
            reminders = reminders,
            telemetry = telemetry,
            bookPreferences = preferences,
            theme = bundle.readerSettings.theme,
            pdfPageColorMode = bundle.readerSettings.pdfPageColorMode,
            viewMode = bundle.readerSettings.viewMode,
            fitMode = bundle.readerSettings.fitMode,
            zoomScale = bundle.readerSettings.zoomScale.coerceIn(0.50f, 2.50f),
            fontSizePt = bundle.readerSettings.fontSizePt.coerceIn(12f, 28f),
            lineSpacingMultiplier = bundle.readerSettings.lineSpacingMultiplier.coerceIn(1.2f, 2.4f),
            uiAppearance = bundle.uiAppearance,
            bookTypography = bundle.bookTypography,
            libraryPreferences = bundle.libraryPreferences,
            keyBindings = if (bundle.keyBindings.isEmpty()) {
                current.keyBindings
            } else {
                KeyBindingSpec.defaultBindings() + bundle.keyBindings
            },
        )

        return updated to BackupImportSummary(
            booksMatched = booksMatched,
            booksMissing = booksMissing,
            phrasesRestored = phrasesRestored,
            phrasesSkipped = phrasesSkipped,
            remindersRestored = remindersRestored,
            remindersSkipped = remindersSkipped,
            collectionsCreated = collectionsCreated,
            telemetryRestored = telemetryRestored,
            settingsApplied = true,
            missingBookTitles = missingTitles,
        )
    }
}

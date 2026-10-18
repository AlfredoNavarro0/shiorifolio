package dev.meridian.folio.core

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class LibraryManager internal constructor(private val repository: FolioRepository) {
    private val syncMutex = Mutex()
    private val _progress = MutableStateFlow(ImportProgressState())
    val progress = _progress.asStateFlow()

    fun setPreferences(preferences: LibraryPreferences) = repository.updateLibrary {
        it.copy(libraryPreferences = preferences)
    }

    fun setStatus(bookIds: Set<String>, status: LibraryStatus) {
        val now = PlatformFileAndZipIo.currentEpochMillis()
        repository.updateLibrary { state ->
            state.copy(documents = state.documents.map {
                if (it.id in bookIds) it.copy(
                    libraryStatus = status,
                    completedAtMs = if (status == LibraryStatus.FINISHED) it.completedAtMs ?: now else null,
                ) else it
            })
        }
    }

    fun createCollection(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        repository.updateLibrary { state ->
            if (state.collections.any { it.name.equals(trimmed, true) }) state
            else state.copy(collections = state.collections + LibraryCollection(
                "collection_${PlatformFileAndZipIo.currentEpochMillis()}_${trimmed.hashCode()}", trimmed,
            ))
        }
    }

    fun assignCollection(bookIds: Set<String>, collectionId: String, assign: Boolean) = repository.updateLibrary { state ->
        if (state.collections.none { it.id == collectionId }) state else state.copy(documents = state.documents.map {
            if (it.id in bookIds) it.copy(collectionIds = if (assign) it.collectionIds + collectionId else it.collectionIds - collectionId) else it
        })
    }

    fun removeCollection(collectionId: String) = repository.updateLibrary { state ->
        state.copy(
            collections = state.collections.filterNot { it.id == collectionId },
            documents = state.documents.map { it.copy(collectionIds = it.collectionIds - collectionId) },
        )
    }

    fun addFolder(location: String) {
        val canonical = PlatformLibraryIo.canonicalPath(location.trim())
        if (canonical.isBlank()) return
        repository.updateLibrary { state ->
            if (state.watchedFolders.any { it.location == canonical }) state
            else state.copy(watchedFolders = state.watchedFolders + WatchedFolder(
                id = "folder_${PlatformFileAndZipIo.currentEpochMillis()}_${canonical.hashCode()}", location = canonical,
            ))
        }
    }

    fun updateFolder(folder: WatchedFolder) = repository.updateLibrary { state ->
        state.copy(watchedFolders = state.watchedFolders.map { if (it.id == folder.id) folder else it })
    }

    fun removeFolder(folderId: String) = repository.updateLibrary { state ->
        state.copy(watchedFolders = state.watchedFolders.filterNot { it.id == folderId })
    }

    /** Deduplication never replaces a reader's identity, annotations or edited content. */
    fun importFile(path: String, name: String = path.substringAfterLast('/').substringAfterLast('\\'), folderId: String? = null, select: Boolean = false, refresh: Boolean = false): BookDocument {
        indexLegacySources()
        val canonical = PlatformLibraryIo.canonicalPath(path)
        val bytes = PlatformLibraryIo.read(canonical)
        val fingerprint = PlatformLibraryIo.sha256(bytes)
        val existing = repository.state.value.documents.firstOrNull { doc ->
            doc.contentSha256 == fingerprint || doc.filePath?.let { PlatformLibraryIo.canonicalPath(it) } == canonical ||
                doc.sources.any { it.path == canonical }
        }
        val parsed = existing?.takeUnless { refresh } ?: run {
            require(name.endsWith(".epub", true) || name.endsWith(".pdf", true)) { "Formato no compatible" }
            if (name.endsWith(".epub", true)) {
                val entries = PlatformFileAndZipIo.unzipEntries(bytes)
                val opf = entries.entries.firstOrNull { it.key.endsWith(".opf", true) }?.value?.decodeToString()
                require(opf != null && Regex("<(?:[\\w.-]+:)?package[\\s>]").containsMatchIn(opf) &&
                    Regex("<(?:[\\w.-]+:)?spine[\\s>]").containsMatchIn(opf)) { "EPUB dañado: manifiesto inválido" }
            } else {
                require(bytes.take(5).toByteArray().decodeToString() == "%PDF-") { "PDF dañado: cabecera inválida" }
                PlatformLibraryIo.validatePdf(bytes)
            }
            val importPath = PlatformLibraryIo.importPath(canonical, name, fingerprint, bytes)
            // Existing parser media caches use the filename as identity. Namespace it to
            // avoid overwriting illustrations of different books with the same filename.
            val prefix = "${fingerprint.take(16)}_"
            val imported = DocumentCodec.importFromBytes(prefix + name, bytes, importPath)
            imported.copy(
                id = "import_$fingerprint", title = imported.title.removePrefix(prefix), parserVersion = 4,
                pages = imported.pages.map { page -> page.copy(
                    chapterTitle = page.chapterTitle.removePrefix(prefix),
                    blocks = page.blocks.map { block ->
                        if (block.style == BlockStyle.HEADING) block.copy(text = block.text.removePrefix(prefix)) else block
                    },
                ) },
            )
        }
        var result = parsed
        repository.updateLibrary { state ->
            // Recheck after parsing: another import may have finished in the meantime.
            val current = state.documents.firstOrNull { it.id == existing?.id || it.contentSha256 == fingerprint || it.filePath == canonical } ?: parsed
            val source = LibrarySource(canonical, folderId)
            result = current.copy(
                contentSha256 = fingerprint,
                sources = (current.sources.filterNot { it.path == canonical && it.folderId == folderId } + source).distinct(),
                filePath = current.filePath?.takeIf { PlatformLibraryIo.exists(it) }
                    ?: PlatformLibraryIo.importPath(canonical, name, fingerprint, bytes),
                isMissingOnDisk = false,
                pages = if (refresh) parsed.pages else current.pages,
                currentPage = if (refresh) current.currentPage.coerceIn(1, parsed.totalPages) else current.currentPage,
                parserVersion = if (refresh) 4 else current.parserVersion,
            )
            state.copy(
                documents = if (state.documents.any { it.id == current.id }) state.documents.map { if (it.id == current.id) result else it }
                    else listOf(result) + state.documents,
                selectedBookId = if (select) result.id else state.selectedBookId ?: result.id,
                excludedLibraryPaths = state.excludedLibraryPaths - canonical,
            )
        }
        return result
    }

    private fun indexLegacySources() {
        val indexed = repository.state.value.documents.filter { it.contentSha256 == null && it.filePath != null }.mapNotNull { doc ->
            runCatching {
                val path = PlatformLibraryIo.canonicalPath(requireNotNull(doc.filePath))
                doc.id to (path to PlatformLibraryIo.sha256(PlatformLibraryIo.read(path)))
            }.getOrNull()
        }.toMap()
        if (indexed.isEmpty()) return
        repository.updateLibrary { state -> state.copy(documents = state.documents.map { doc ->
            val source = indexed[doc.id]
            if (source == null || doc.contentSha256 != null) doc else doc.copy(
                contentSha256 = source.second,
                sources = doc.sources.ifEmpty { listOf(LibrarySource(source.first)) },
            )
        }) }
    }

    suspend fun synchronizeFolders() = syncMutex.withLock {
        withContext(PlatformLibraryIo.ioDispatcher) {
            _progress.value = ImportProgressState(running = true)
            val errors = mutableListOf<String>()
            try {
                val folders = repository.state.value.watchedFolders
                val scans = folders.map { folder ->
                    currentCoroutineContext().ensureActive()
                    val scan = try { PlatformLibraryIo.scan(folder) } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        FolderScan(emptyList(), listOf("${folder.location}: ${e.message}"))
                    }
                    errors += scan.errors
                    folder to scan
                }
                _progress.value = _progress.value.copy(total = scans.sumOf { it.second.files.size }, errors = errors.toList())
                for ((folder, scan) in scans) {
                    for (file in scan.files) {
                        currentCoroutineContext().ensureActive()
                        if (PlatformLibraryIo.canonicalPath(file.path) in repository.state.value.excludedLibraryPaths) {
                            _progress.value = _progress.value.copy(processed = _progress.value.processed + 1)
                            continue
                        }
                        _progress.value = _progress.value.copy(currentFile = file.name)
                        try {
                            val before = repository.state.value.documents.size
                            importFile(file.path, file.name, folder.id)
                            val added = repository.state.value.documents.size > before
                            _progress.value = _progress.value.copy(
                                imported = _progress.value.imported + if (added) 1 else 0,
                                duplicates = _progress.value.duplicates + if (added) 0 else 1,
                            )
                        } catch (e: Exception) {
                            if (e is CancellationException) throw e
                            errors += "${file.name}: ${e.message ?: "No se pudo importar"}"
                        }
                        _progress.value = _progress.value.copy(processed = _progress.value.processed + 1, errors = errors.toList())
                    }
                    val found = scan.files.map { PlatformLibraryIo.canonicalPath(it.path) }.toSet()
                    // A failed/incomplete scan is not evidence of file disappearance.
                    repository.updateLibrary { state -> state.copy(
                        watchedFolders = state.watchedFolders.map {
                            if (it.id == folder.id) it.copy(
                                lastSyncEpochMs = if (scan.errors.isEmpty()) PlatformFileAndZipIo.currentEpochMillis() else it.lastSyncEpochMs,
                                lastError = scan.errors.joinToString("\n").ifBlank { null },
                            ) else it
                        },
                        documents = state.documents.map { doc ->
                            if (scan.errors.isNotEmpty() || doc.sources.none { it.folderId == folder.id }) doc
                            else {
                                val available = doc.sources.any { source ->
                                    (source.folderId == folder.id && source.path in found) || PlatformLibraryIo.exists(source.path)
                                }
                                doc.copy(isMissingOnDisk = !available)
                            }
                        },
                    ) }
                }
            } finally {
                _progress.value = _progress.value.copy(running = false, errors = errors.toList())
            }
        }
    }

    /** Failed disk deletions remain in the library and are reported to the caller. */
    suspend fun removeBooks(bookIds: Set<String>, deleteFiles: Boolean): List<String> = syncMutex.withLock {
        withContext(PlatformLibraryIo.ioDispatcher) {
            val failed = mutableListOf<String>()
            val removable = repository.state.value.documents.filter { it.id in bookIds }.filter { book ->
                val paths = (book.sources.map { it.path } + listOfNotNull(book.filePath)).distinct()
                val failures = if (deleteFiles) paths.filter { !PlatformLibraryIo.delete(it) } else emptyList()
                failed += failures
                failures.isEmpty()
            }.map { it.id }.toSet()
            repository.updateLibrary { state ->
                val docs = state.documents.filterNot { it.id in removable }
                val removedPaths = state.documents.filter { it.id in removable }
                    .flatMap { it.sources.map { source -> source.path } + listOfNotNull(it.filePath) }
                state.copy(
                    documents = docs,
                    selectedBookId = state.selectedBookId?.takeUnless { it in removable } ?: docs.firstOrNull()?.id,
                    reminders = state.reminders - removable,
                    telemetry = state.telemetry - removable,
                    bookPreferences = state.bookPreferences - removable,
                    savedPhrases = state.savedPhrases.filterNot { it.bookId in removable },
                    navigationBackStack = state.navigationBackStack.filterNot { it.bookId in removable },
                    navigationForwardStack = state.navigationForwardStack.filterNot { it.bookId in removable },
                    excludedLibraryPaths = state.excludedLibraryPaths + removedPaths,
                )
            }
            failed
        }
    }
}

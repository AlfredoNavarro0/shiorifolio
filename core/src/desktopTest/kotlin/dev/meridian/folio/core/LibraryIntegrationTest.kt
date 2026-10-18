package dev.meridian.folio.core

import java.io.File
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlin.test.*

class LibraryIntegrationTest {
    private fun <T> temporaryLibrary(block: (File, FolioRepository) -> T): T {
        val root = Files.createTempDirectory("shiorifolio-library-test-").toFile()
        try { return block(root, FolioRepository(persistToDisk = false)) }
        finally { root.deleteRecursively() }
    }

    private fun epub(repo: FolioRepository, title: String = "Libro de prueba"): ByteArray = DocumentCodec.exportToBytes(
        repo.state.value.documents.first { it.format == DocumentFormat.EPUB }.copy(title = title),
    )

    @Test
    fun identicalFilesDeduplicateWithoutLosingReadingDataAndSameNamesStayDistinct(): Unit = temporaryLibrary { root, repo ->
        val firstFile = File(root, "novela.epub").apply { writeBytes(epub(repo)) }
        val imported = repo.library.importFile(firstFile.path, select = true)
        repo.goToPage(imported.id, 2)
        repo.setPageReminder(imported.id, 2, "Retomar aquí")
        val phrase = repo.saveWordOrPhrase(imported.id, 2, "Una frase", "Contexto")!!
        repo.library.setStatus(setOf(imported.id), LibraryStatus.READING)
        repo.library.createCollection("Favoritos")
        val collection = repo.state.value.collections.single()
        repo.library.assignCollection(setOf(imported.id), collection.id, true)
        repo.updatePageContent(imported.id, 2, "Capítulo editado", listOf(ContentBlock("edited", text = "Texto editado por el lector")))
        // Simulate a pre-iteration-5 record without a source fingerprint.
        repo.updateLibrary { state -> state.copy(documents = state.documents.map {
            if (it.id == imported.id) it.copy(contentSha256 = null, sources = emptyList()) else it
        }) }
        val copy = File(root, "copia.epub").apply { writeBytes(firstFile.readBytes()) }
        val duplicate = repo.library.importFile(copy.path)
        assertEquals(imported.id, duplicate.id)
        assertEquals(2, duplicate.currentPage)
        assertEquals(LibraryStatus.READING, duplicate.libraryStatus)
        assertEquals(setOf(collection.id), duplicate.collectionIds)
        assertEquals(2, duplicate.sources.size)
        assertEquals("Texto editado por el lector", duplicate.pages[1].plainText)
        assertEquals("Retomar aquí", repo.state.value.reminders[imported.id]?.note)
        assertTrue(repo.state.value.savedPhrases.any { it.id == phrase.id })
        val refreshed = repo.library.importFile(firstFile.path, refresh = true)
        assertEquals(imported.id, refreshed.id)
        assertEquals(2, refreshed.currentPage)
        assertNotEquals("Texto editado por el lector", refreshed.pages[1].plainText)
        assertTrue(repo.state.value.savedPhrases.any { it.id == phrase.id })
        val other = File(File(root, "otra").apply { mkdirs() }, "novela.epub").apply { writeBytes(epub(repo, "Otra novela")) }
        val different = repo.library.importFile(other.path)
        assertNotEquals(imported.id, different.id)
        assertEquals("Otra novela", different.title)
    }

    @Test
    fun batchScanHandlesRecursionErrorsMissingFilesAndRecovery(): Unit = temporaryLibrary { root, repo -> runBlocking {
        val file = File(root, "libro.epub").apply { writeBytes(epub(repo)) }
        File(root, "roto.epub").writeText("No es un ZIP")
        File(root, "roto.pdf").writeText("%PDF-1.7\nesto no es un PDF")
        val nested = File(root, "sub").apply { mkdirs() }
        File(nested, "segundo.epub").writeBytes(epub(repo, "Segundo"))
        repo.library.addFolder(root.path)
        repo.library.synchronizeFolders()
        assertEquals(3, repo.library.progress.value.total)
        assertEquals(1, repo.library.progress.value.imported)
        assertEquals(2, repo.library.progress.value.errors.size)
        assertFalse(repo.library.progress.value.running)
        val book = repo.state.value.documents.first { it.contentSha256 != null }
        repo.setPageReminder(book.id, 2, "No perder")
        val bytes = file.readBytes()
        assertTrue(file.delete())
        repo.library.synchronizeFolders()
        assertTrue(repo.state.value.documents.first { it.id == book.id }.isMissingOnDisk)
        assertEquals("No perder", repo.state.value.reminders[book.id]?.note)
        file.writeBytes(bytes)
        repo.library.updateFolder(repo.state.value.watchedFolders.single().copy(includeSubfolders = true))
        repo.library.synchronizeFolders()
        assertFalse(repo.state.value.documents.first { it.id == book.id }.isMissingOnDisk)
        assertTrue(repo.state.value.documents.any { it.title == "Segundo" })
        repo.library.updateFolder(repo.state.value.watchedFolders.single().copy(includeSubfolders = false))
        repo.library.synchronizeFolders()
        assertFalse(repo.state.value.documents.first { it.title == "Segundo" }.isMissingOnDisk)
        // A revoked permission/unreadable root must not imply disappearance.
        repo.library.updateFolder(repo.state.value.watchedFolders.single().copy(location = File(root, "inaccesible").path))
        repo.library.synchronizeFolders()
        assertFalse(repo.state.value.documents.first { it.id == book.id }.isMissingOnDisk)
        assertNotNull(repo.state.value.watchedFolders.single().lastError)
    } }

    @Test
    fun removingBooksPreservesFilesAndDoesNotReimportUntilExplicitlyRequested(): Unit = temporaryLibrary { root, repo -> runBlocking {
        val file = File(root, "libro.epub").apply { writeBytes(epub(repo)) }
        repo.library.addFolder(root.path)
        repo.library.synchronizeFolders()
        val book = repo.state.value.documents.first { it.contentSha256 != null }
        assertTrue(repo.library.removeBooks(setOf(book.id), deleteFiles = false).isEmpty())
        assertTrue(file.exists())
        repo.library.synchronizeFolders()
        assertTrue(repo.state.value.documents.none { it.id == book.id })
        val restored = repo.library.importFile(file.path)
        assertEquals(book.id, restored.id)
        assertTrue(repo.library.removeBooks(setOf(restored.id), deleteFiles = true).isEmpty())
        assertFalse(file.exists())
    } }

    @Test
    fun filtersCombineAndCollectionsAreManyToMany(): Unit = temporaryLibrary { _, repo ->
        val book = repo.state.value.documents.first { it.format == DocumentFormat.EPUB }
        repo.library.createCollection("Estudio")
        repo.library.createCollection("Lecturas")
        val ids = repo.state.value.collections.map { it.id }
        ids.forEach { repo.library.assignCollection(setOf(book.id), it, true) }
        repo.library.setStatus(setOf(book.id), LibraryStatus.FINISHED)
        val finished = repo.state.value.documents.first { it.id == book.id }
        assertNotNull(finished.completedAtMs)
        val filter = LibraryFilter(search = book.author.uppercase(), formats = setOf(DocumentFormat.EPUB),
            statuses = setOf(LibraryStatus.FINISHED), collectionId = ids.first(), minProgress = 100,
            minPages = book.totalPages, maxPages = book.totalPages, completedAfterMs = finished.completedAtMs)
        assertEquals(listOf(book.id), queryLibrary(repo.state.value, filter).map { it.id })
        assertTrue(queryLibrary(repo.state.value, filter.copy(formats = setOf(DocumentFormat.PDF))).isEmpty())
        repo.library.assignCollection(setOf(book.id), ids.first(), false)
        assertTrue(queryLibrary(repo.state.value, filter).isEmpty())
        assertEquals(setOf(ids.last()), repo.state.value.documents.first { it.id == book.id }.collectionIds)
        repo.library.removeCollection(ids.last())
        assertTrue(repo.state.value.documents.first { it.id == book.id }.collectionIds.isEmpty())
        repo.library.setStatus(setOf(book.id), LibraryStatus.ABANDONED)
        assertNull(repo.state.value.documents.first { it.id == book.id }.completedAtMs)
        LibrarySort.entries.forEach { sort ->
            repo.library.setPreferences(LibraryPreferences(sort = sort, descending = false))
            val ascending = queryLibrary(repo.state.value, LibraryFilter())
            repo.library.setPreferences(repo.state.value.libraryPreferences.copy(descending = true))
            assertEquals(ascending.reversed(), queryLibrary(repo.state.value, LibraryFilter()))
        }
    }

    @Test
    fun defaultsReadOldStateAndEmptyLibraryPersistsAcrossRestart(): Unit = temporaryLibrary { root, repo -> runBlocking {
        val json = Json { encodeDefaults = true }
        val legacy = json.decodeFromString<FolioPersistedState>("""{"documents":[],"selectedBookId":null}""")
        assertEquals(emptyList(), legacy.watchedFolders)
        assertEquals(LibraryPreferences(), legacy.libraryPreferences)
        val path = File(root, "state.json")
        val persisted = FolioRepository(path.path)
        persisted.library.createCollection("Persistida")
        persisted.library.setPreferences(LibraryPreferences(layout = LibraryLayout.LIST, density = LibraryDensity.COMPACT))
        persisted.library.removeBooks(persisted.state.value.documents.map { it.id }.toSet(), false)
        val restarted = FolioRepository(path.path)
        assertTrue(restarted.state.value.documents.isEmpty())
        assertEquals("Persistida", restarted.state.value.collections.single().name)
        assertEquals(LibraryLayout.LIST, restarted.state.value.libraryPreferences.layout)
        assertEquals(LibraryDensity.COMPACT, restarted.state.value.libraryPreferences.density)
    } }

    @Test
    fun desktopObserverNotifiesOnFolderChangesAndCanBeClosed(): Unit = temporaryLibrary { root, _ ->
        val changed = CountDownLatch(1)
        val observer = PlatformLibraryIo.observe(WatchedFolder(location = root.path, observeChanges = true)) { changed.countDown() }
        try {
            // Multiple writes avoid assuming registration has completed on the worker thread.
            repeat(10) { index ->
                if (changed.count > 0) {
                    File(root, "file_$index.epub").writeText("event")
                    changed.await(300, TimeUnit.MILLISECONDS)
                }
            }
            assertTrue(changed.await(3, TimeUnit.SECONDS))
        } finally { observer.close() }
    }
}

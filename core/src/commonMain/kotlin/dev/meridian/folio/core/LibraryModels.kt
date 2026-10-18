package dev.meridian.folio.core

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.serialization.Serializable

@Serializable
enum class LibraryStatus(val label: String) {
    PENDING("Pendiente"), READING("Leyendo"), ABANDONED("Abandonado"), FINISHED("Terminado"),
}

@Serializable
data class LibrarySource(val path: String = "", val folderId: String? = null)

@Serializable
data class WatchedFolder(
    val id: String = "",
    val location: String = "",
    val includeSubfolders: Boolean = false,
    val observeChanges: Boolean = false,
    val lastSyncEpochMs: Long? = null,
    val lastError: String? = null,
)

@Serializable
data class LibraryCollection(val id: String = "", val name: String = "")

@Serializable
enum class LibraryLayout(val label: String) { CARDS("Tarjetas"), LIST("Lista") }

@Serializable
enum class LibraryDensity(val label: String) { COMPACT("Compacta"), DETAILED("Detallada") }

@Serializable
enum class LibrarySort(val label: String) {
    TITLE("Título"), AUTHOR("Autor"), PROGRESS("Progreso"), PAGES("Páginas"),
    ADDED("Incorporación"), LAST_READ("Última lectura"),
}

@Serializable
data class LibraryPreferences(
    val layout: LibraryLayout = LibraryLayout.CARDS,
    val density: LibraryDensity = LibraryDensity.DETAILED,
    val sort: LibrarySort = LibrarySort.ADDED,
    val descending: Boolean = true,
)

data class LibraryFilter(
    val search: String = "",
    val statuses: Set<LibraryStatus> = emptySet(),
    val formats: Set<DocumentFormat> = emptySet(),
    val collectionId: String? = null,
    val minProgress: Int = 0,
    val maxProgress: Int = 100,
    val minPages: Int = 0,
    val maxPages: Int = Int.MAX_VALUE,
    val addedAfterMs: Long? = null,
    val addedBeforeMs: Long? = null,
    val completedAfterMs: Long? = null,
    val completedBeforeMs: Long? = null,
)

fun BookDocument.libraryProgress(): Float = if (libraryStatus == LibraryStatus.FINISHED) 1f
    else ((currentPage - 1).toFloat() / totalPages).coerceIn(0f, 1f)

fun queryLibrary(state: FolioPersistedState, filter: LibraryFilter): List<BookDocument> {
    val books = state.documents.filter { book ->
        val progress = book.libraryProgress() * 100
        (filter.search.isBlank() || "${book.title} ${book.author}".contains(filter.search.trim(), true)) &&
            (filter.statuses.isEmpty() || book.libraryStatus in filter.statuses) &&
            (filter.formats.isEmpty() || book.format in filter.formats) &&
            (filter.collectionId == null || filter.collectionId in book.collectionIds) &&
            progress >= filter.minProgress && progress <= filter.maxProgress &&
            book.totalPages in filter.minPages..filter.maxPages &&
            (filter.addedAfterMs == null || book.createdAtMs >= filter.addedAfterMs) &&
            (filter.addedBeforeMs == null || book.createdAtMs <= filter.addedBeforeMs) &&
            (filter.completedAfterMs == null || (book.completedAtMs ?: Long.MIN_VALUE) >= filter.completedAfterMs) &&
            (filter.completedBeforeMs == null || (book.completedAtMs != null && book.completedAtMs <= filter.completedBeforeMs))
    }
    val sorted = when (state.libraryPreferences.sort) {
        LibrarySort.TITLE -> books.sortedBy { it.title.lowercase() }
        LibrarySort.AUTHOR -> books.sortedBy { it.author.lowercase() }
        LibrarySort.PROGRESS -> books.sortedBy { it.libraryProgress() }
        LibrarySort.PAGES -> books.sortedBy { it.totalPages }
        LibrarySort.ADDED -> books.sortedBy { it.createdAtMs }
        LibrarySort.LAST_READ -> books.sortedBy { state.telemetry[it.id]?.lastReadEpochMs ?: 0L }
    }
    return if (state.libraryPreferences.descending) sorted.reversed() else sorted
}

data class ImportProgressState(
    val running: Boolean = false,
    val currentFile: String = "",
    val processed: Int = 0,
    val total: Int = 0,
    val imported: Int = 0,
    val duplicates: Int = 0,
    val errors: List<String> = emptyList(),
)

data class LibraryFile(val path: String, val name: String)
data class FolderScan(val files: List<LibraryFile>, val errors: List<String> = emptyList())
fun interface FolderObservation { fun close() }

expect object PlatformLibraryIo {
    fun stateWrite(block: () -> Unit)
    val ioDispatcher: CoroutineDispatcher
    fun canonicalPath(path: String): String
    fun sha256(bytes: ByteArray): String
    fun scan(folder: WatchedFolder): FolderScan
    fun read(path: String): ByteArray
    fun importPath(path: String, name: String, fingerprint: String, bytes: ByteArray): String
    fun validatePdf(bytes: ByteArray)
    fun exists(path: String): Boolean
    fun delete(path: String): Boolean
    fun observe(folder: WatchedFolder, onChanged: () -> Unit): FolderObservation
}

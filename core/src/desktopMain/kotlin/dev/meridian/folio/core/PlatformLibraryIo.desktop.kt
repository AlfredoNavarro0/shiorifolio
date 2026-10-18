package dev.meridian.folio.core

import java.io.File
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.StandardWatchEventKinds
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

actual object PlatformLibraryIo {
    actual fun stateWrite(block: () -> Unit) { synchronized(this) { block() } }
    actual val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
    actual fun canonicalPath(path: String): String = File(path).canonicalPath
    actual fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    actual fun read(path: String): ByteArray = File(path).readBytes()
    actual fun importPath(path: String, name: String, fingerprint: String, bytes: ByteArray): String = path
    actual fun validatePdf(bytes: ByteArray) {
        org.apache.pdfbox.pdmodel.PDDocument.load(bytes).use { require(it.numberOfPages > 0) { "PDF sin páginas" } }
    }
    actual fun exists(path: String): Boolean = File(path).isFile
    actual fun delete(path: String): Boolean = runCatching {
        val target = File(path).toPath()
        if (Files.isDirectory(target)) false else { Files.deleteIfExists(target); true }
    }.getOrDefault(false)

    actual fun scan(folder: WatchedFolder): FolderScan {
        val files = mutableListOf<LibraryFile>()
        val errors = mutableListOf<String>()
        fun visit(dir: File) {
            val children = dir.listFiles()
            if (children == null) { errors += "${dir.path}: carpeta inaccesible"; return }
            for (file in children.sortedBy { it.name }) {
                if (file.isDirectory && folder.includeSubfolders && !Files.isSymbolicLink(file.toPath())) visit(file)
                else if (file.isFile && file.extension.lowercase() in setOf("epub", "pdf")) files += LibraryFile(file.canonicalPath, file.name)
            }
        }
        visit(File(folder.location))
        return FolderScan(files, errors)
    }

    actual fun observe(folder: WatchedFolder, onChanged: () -> Unit): FolderObservation {
        if (!folder.observeChanges) return FolderObservation {}
        val service = FileSystems.getDefault().newWatchService()
        val stopped = AtomicBoolean(false)
        val directories = mutableSetOf<Path>()
        fun register(dir: Path) {
            if (Files.isDirectory(dir, LinkOption.NOFOLLOW_LINKS) && directories.add(dir)) {
                dir.register(service, StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_DELETE, StandardWatchEventKinds.ENTRY_MODIFY)
            }
        }
        fun registerTree() {
            val root = File(folder.location).toPath()
            register(root)
            if (folder.includeSubfolders && Files.isDirectory(root)) Files.walk(root).use { paths -> paths.forEach(::register) }
        }
        val thread = Thread({
            try {
                registerTree()
                while (!stopped.get()) {
                    val key = service.take()
                    val changed = key.pollEvents().isNotEmpty()
                    key.reset()
                    if (changed) {
                        Thread.sleep(600) // Coalesce editor saves and file copy bursts.
                        registerTree()
                        onChanged()
                    }
                }
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
            } catch (_: java.nio.file.ClosedWatchServiceException) {
                // Normal disposal.
            } catch (_: Exception) {
                // Manual refresh remains available if the filesystem cannot be watched.
                onChanged()
            } finally {
                service.close()
            }
        }, "shiorifolio-folder-watch").apply { isDaemon = true; start() }
        return FolderObservation { stopped.set(true); service.close(); thread.interrupt() }
    }
}

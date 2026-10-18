package dev.meridian.folio.core

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

actual object PlatformLibraryIo {
    actual fun stateWrite(block: () -> Unit) { synchronized(this) { block() } }
    private lateinit var context: Context
    fun initialize(context: Context) {
        this.context = context.applicationContext
        PlatformFileAndZipIo.setApplicationStorageDirectory(context.filesDir.absolutePath)
    }
    actual val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
    actual fun canonicalPath(path: String): String = if (path.startsWith("content://")) path else File(path).canonicalPath
    actual fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    actual fun read(path: String): ByteArray = if (path.startsWith("content://")) {
        requireNotNull(context.contentResolver.openInputStream(Uri.parse(path))).use { it.readBytes() }
    } else File(path).readBytes()

    actual fun exists(path: String): Boolean = runCatching {
        if (!path.startsWith("content://")) File(path).isFile else {
            context.contentResolver.query(Uri.parse(path), arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID), null, null, null)?.use { it.moveToFirst() } == true
        }
    }.getOrDefault(false)

    actual fun delete(path: String): Boolean = runCatching {
        if (path.startsWith("content://")) DocumentsContract.deleteDocument(context.contentResolver, Uri.parse(path)) else {
            val target = File(path).toPath()
            if (java.nio.file.Files.isDirectory(target)) false else { java.nio.file.Files.deleteIfExists(target); true }
        }
    }.getOrDefault(false)

    actual fun importPath(path: String, name: String, fingerprint: String, bytes: ByteArray): String {
        if (!path.startsWith("content://")) return path
        val file = File(context.filesDir, "library/$fingerprint.${name.substringAfterLast('.').lowercase()}")
        file.parentFile?.mkdirs()
        file.writeBytes(bytes)
        return file.absolutePath
    }

    actual fun validatePdf(bytes: ByteArray) {
        val temporary = File.createTempFile("validate_pdf_", ".pdf", context.cacheDir)
        try {
            temporary.writeBytes(bytes)
            android.os.ParcelFileDescriptor.open(temporary, android.os.ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                android.graphics.pdf.PdfRenderer(descriptor).use { require(it.pageCount > 0) { "PDF sin páginas" } }
            }
        } finally { temporary.delete() }
    }

    actual fun scan(folder: WatchedFolder): FolderScan {
        val files = mutableListOf<LibraryFile>()
        val errors = mutableListOf<String>()
        val tree = Uri.parse(folder.location)
        val visited = mutableSetOf<String>()
        fun visit(documentId: String) {
            if (!visited.add(documentId)) return
            try {
                val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, documentId)
                val projection = arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_MIME_TYPE)
                val cursor = context.contentResolver.query(children, projection, null, null, null)
                    ?: error("Carpeta inaccesible")
                cursor.use {
                    while (it.moveToNext()) {
                        val id = it.getString(0)
                        val name = it.getString(1) ?: ""
                        if (it.getString(2) == DocumentsContract.Document.MIME_TYPE_DIR) {
                            if (folder.includeSubfolders) visit(id)
                        } else if (name.endsWith(".pdf", true) || name.endsWith(".epub", true)) {
                            files += LibraryFile(DocumentsContract.buildDocumentUriUsingTree(tree, id).toString(), name)
                        }
                    }
                }
            } catch (e: Exception) { errors += "$documentId: ${e.message}" }
        }
        visit(DocumentsContract.getTreeDocumentId(tree))
        return FolderScan(files, errors)
    }

    // SAF providers do not guarantee live filesystem events; refresh on resume instead.
    actual fun observe(folder: WatchedFolder, onChanged: () -> Unit): FolderObservation = FolderObservation {}
}

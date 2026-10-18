package dev.meridian.folio.core

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

actual object PlatformFileAndZipIo {
    private var applicationStorageDirectory: String? = null
    fun setApplicationStorageDirectory(path: String) { applicationStorageDirectory = path }
    actual fun unzipEntries(bytes: ByteArray): Map<String, ByteArray> {
        val entries = LinkedHashMap<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    entries[entry.name.replace('\\', '/')] = zis.readBytes()
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
        return entries
    }

    actual fun zipEntries(entries: Map<String, ByteArray>, uncompressedFirstEntry: String?): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zos ->
            if (uncompressedFirstEntry != null && entries.containsKey(uncompressedFirstEntry)) {
                val data = entries.getValue(uncompressedFirstEntry)
                val stored = ZipEntry(uncompressedFirstEntry).apply {
                    method = ZipEntry.STORED
                    size = data.size.toLong()
                    compressedSize = data.size.toLong()
                    val crc = CRC32()
                    crc.update(data)
                    this.crc = crc.value
                }
                zos.putNextEntry(stored)
                zos.write(data)
                zos.closeEntry()
            }
            for ((name, data) in entries) {
                if (name == uncompressedFirstEntry) continue
                val ze = ZipEntry(name)
                zos.putNextEntry(ze)
                zos.write(data)
                zos.closeEntry()
            }
        }
        return out.toByteArray()
    }

    actual fun readFileBytes(path: String): ByteArray? =
        runCatching { File(path).takeIf { it.exists() && it.isFile }?.readBytes() }.getOrNull()

    actual fun writeFileBytes(path: String, bytes: ByteArray): Boolean =
        runCatching {
            val file = File(path)
            file.parentFile?.mkdirs()
            file.writeBytes(bytes)
            true
        }.getOrDefault(false)

    actual fun cacheMediaFile(bookId: String, relativeName: String, bytes: ByteArray): String? =
        runCatching {
            val home = System.getProperty("user.home") ?: System.getProperty("java.io.tmpdir") ?: "."
            val safeBook = bookId.replace(Regex("[^A-Za-z0-9_\\-]"), "_")
            val safeName = relativeName.replace('\\', '/').trimStart('/').replace("..", "_")
            val storage = applicationStorageDirectory?.let(::File) ?: File(home, ".meridian-folio")
            val target = File(File(storage, "media/$safeBook"), safeName)
            target.parentFile?.mkdirs()
            target.writeBytes(bytes)
            target.absolutePath
        }.getOrNull()

    actual fun defaultStateFilePath(): String {
        val home = System.getProperty("user.home") ?: "."
        return File(File(home, ".meridian-folio"), "meridian_folio_state.json").absolutePath
    }

    actual fun currentEpochMillis(): Long = System.currentTimeMillis()
}

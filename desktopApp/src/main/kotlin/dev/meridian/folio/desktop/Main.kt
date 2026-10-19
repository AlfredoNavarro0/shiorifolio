package dev.meridian.folio.desktop

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import dev.meridian.folio.core.FolioRepository
import dev.meridian.folio.ui.MeridianFolioApp
import dev.meridian.folio.ui.shioriFolioIconPainter
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import javax.swing.JFileChooser

fun main(args: Array<String>) {
    val repository = FolioRepository()

    if (args.contains("--self-test")) {
        val book = repository.currentBook() ?: error("Expected default sample book")
        repository.recordReadingTimeTick(book.id, 15.0)
        println("SHIORIFOLIO_SELF_TEST_OK: book=${book.title}, pages=${book.totalPages}")
        return
    }

    application {
        val windowState = rememberWindowState(
            size = DpSize(1280.dp, 820.dp),
            position = WindowPosition.Aligned(androidx.compose.ui.Alignment.Center),
        )
        val appIcon = shioriFolioIconPainter()

        Window(
            onCloseRequest = ::exitApplication,
            state = windowState,
            title = "ShioriFolio (栞) — PDF & EPUB Reader and Editor",
            icon = appIcon,
        ) {
            MeridianFolioApp(
                repository = repository,
                onPickFolder = { onSelected ->
                    val chooser = JFileChooser().apply {
                        dialogTitle = "Añadir carpeta a la biblioteca"
                        fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
                    }
                    if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
                        onSelected(chooser.selectedFile.canonicalPath)
                    }
                },
                isWindowFullscreen = windowState.placement == WindowPlacement.Fullscreen,
                onToggleWindowFullscreen = {
                    windowState.placement = if (windowState.placement == WindowPlacement.Fullscreen) {
                        WindowPlacement.Floating
                    } else {
                        WindowPlacement.Fullscreen
                    }
                },
                onPickFileToImport = { onSelected ->
                    val dialog = FileDialog(null as Frame?, "Open PDF or EPUB Document", FileDialog.LOAD)
                    dialog.setFilenameFilter { _, name ->
                        val lower = name.lowercase()
                        lower.endsWith(".pdf") || lower.endsWith(".epub")
                    }
                    dialog.isVisible = true
                    val dir = dialog.directory
                    val file = dialog.file
                    if (dir != null && file != null) {
                        onSelected(File(dir, file).absolutePath)
                    }
                },
                onPickFileToExport = { defaultName, onSelected ->
                    val dialog = FileDialog(null as Frame?, "Export PDF or EPUB Document", FileDialog.SAVE)
                    dialog.file = defaultName
                    dialog.isVisible = true
                    val dir = dialog.directory
                    val file = dialog.file
                    if (dir != null && file != null) {
                        onSelected(File(dir, file).absolutePath)
                    }
                },
                onPickBackupFileToImport = { onSelected ->
                    val dialog = FileDialog(null as Frame?, "Import ShioriFolio Backup", FileDialog.LOAD)
                    dialog.setFilenameFilter { _, name -> name.lowercase().endsWith(".json") }
                    dialog.isVisible = true
                    val dir = dialog.directory
                    val file = dialog.file
                    if (dir != null && file != null) {
                        onSelected(File(dir, file).absolutePath)
                    }
                },
                onPickBackupFileToExport = { defaultName, onSelected ->
                    val dialog = FileDialog(null as Frame?, "Export ShioriFolio Backup", FileDialog.SAVE)
                    dialog.file = defaultName
                    dialog.isVisible = true
                    val dir = dialog.directory
                    val file = dialog.file
                    if (dir != null && file != null) {
                        onSelected(File(dir, file).absolutePath)
                    }
                },
                onPickImageFile = { onSelected ->
                    val dialog = FileDialog(null as Frame?, "Choose Cover Image", FileDialog.LOAD)
                    dialog.setFilenameFilter { _, name ->
                        val lower = name.lowercase()
                        lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg") ||
                            lower.endsWith(".webp") || lower.endsWith(".gif")
                    }
                    dialog.isVisible = true
                    val dir = dialog.directory
                    val file = dialog.file
                    if (dir != null && file != null) {
                        onSelected(File(dir, file).absolutePath)
                    }
                },
            )
        }
    }
}

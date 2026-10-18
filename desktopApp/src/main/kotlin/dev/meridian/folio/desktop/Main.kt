package dev.meridian.folio.desktop

import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import dev.meridian.folio.core.FolioRepository
import dev.meridian.folio.ui.MeridianFolioApp
import java.awt.BasicStroke
import java.awt.Color
import java.awt.FileDialog
import java.awt.Frame
import java.awt.RenderingHints
import java.awt.geom.Path2D
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import javax.imageio.ImageIO
import org.jetbrains.skia.Image

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
        val appIcon = remember { createShioriFolioWindowIcon() }

        Window(
            onCloseRequest = ::exitApplication,
            state = windowState,
            title = "ShioriFolio (栞) — PDF & EPUB Reader and Editor",
            icon = appIcon,
        ) {
            MeridianFolioApp(
                repository = repository,
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
            )
        }
    }
}

private fun createShioriFolioWindowIcon(): BitmapPainter? = runCatching {
    val img = BufferedImage(128, 128, BufferedImage.TYPE_INT_ARGB)
    val g = img.createGraphics()
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

    val paper = Color(0xEB, 0xEE, 0xE7)
    val ink = Color(0x2B, 0x3A, 0x30)

    // Medallion background
    g.color = paper
    g.fillOval(4, 4, 120, 120)
    g.color = ink
    g.stroke = BasicStroke(5f)
    g.drawOval(6, 6, 116, 116)
    g.stroke = BasicStroke(2f)
    g.drawOval(14, 14, 100, 100)

    // Rising star above open pages
    val star = Path2D.Float().apply {
        moveTo(64f, 20f)
        lineTo(68f, 31f)
        lineTo(79f, 35f)
        lineTo(68f, 39f)
        lineTo(64f, 50f)
        lineTo(60f, 39f)
        lineTo(49f, 35f)
        lineTo(60f, 31f)
        closePath()
    }
    g.fill(star)

    // Open book pages
    g.stroke = BasicStroke(4.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
    val leftPage = Path2D.Float().apply {
        moveTo(64f, 52f)
        curveTo(52f, 46f, 38f, 46f, 26f, 50f)
        lineTo(26f, 86f)
        curveTo(38f, 82f, 52f, 82f, 64f, 89f)
        closePath()
    }
    val rightPage = Path2D.Float().apply {
        moveTo(64f, 52f)
        curveTo(76f, 46f, 90f, 46f, 102f, 50f)
        lineTo(102f, 86f)
        curveTo(90f, 82f, 76f, 82f, 64f, 89f)
        closePath()
    }
    g.draw(leftPage)
    g.draw(rightPage)

    // Swallowtail Shiori bookmark ribbon
    val ribbon = Path2D.Float().apply {
        moveTo(58f, 87f)
        lineTo(70f, 87f)
        lineTo(70f, 112f)
        lineTo(64f, 105f)
        lineTo(58f, 112f)
        closePath()
    }
    g.fill(ribbon)
    g.dispose()

    val out = ByteArrayOutputStream()
    ImageIO.write(img, "png", out)
    BitmapPainter(Image.makeFromEncoded(out.toByteArray()).toComposeImageBitmap())
}.getOrNull()

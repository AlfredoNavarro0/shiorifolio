package dev.meridian.folio.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.meridian.folio.core.*

internal val LocalModernUiAppearance = staticCompositionLocalOf { UiAppearanceSettings() }

/** Shared, quiet chrome; the reading surface keeps the user's own typography. */
@Composable
internal fun ModernChromeAction(
    label: String,
    palette: MeridianPalette,
    tag: String,
    selected: Boolean = false,
    enabled: Boolean = true,
    description: String = label,
    onClick: () -> Unit,
) {
    val appearance = LocalModernUiAppearance.current
    Surface(onClick = onClick, enabled = enabled, shape = RoundedCornerShape(12.dp),
        color = if (selected) palette.accentGreen else palette.paper.copy(alpha = 0f),
        contentColor = if (!enabled) palette.disabledInk else if (selected) palette.onAccent else palette.inkSoft,
        modifier = Modifier.heightIn(min = 40.dp).testTag(tag).semantics { contentDescription = description }) {
        Box(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), contentAlignment = Alignment.Center) {
            Text(label, fontFamily = appearance.fontFamily.toComposeFontFamily(),
                fontWeight = if (selected) FontWeight.SemiBold else appearance.fontWeight.toComposeFontWeight(),
                fontSize = appearance.fontSizeSp.coerceIn(11f, 15f).sp,
                maxLines = 1, softWrap = false)
        }
    }
}

@Composable
internal fun ModernAppNavigation(
    palette: MeridianPalette,
    activeSection: MeridianSection,
    theme: ReaderTheme,
    onSelectSection: (MeridianSection) -> Unit,
    onToggleTheme: () -> Unit,
) {
    Row(Modifier.fillMaxWidth().background(palette.pageSurface).padding(horizontal = 24.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MeridianCompassMark(palette.accentGreen, palette.pageSurface, size = 30.dp)
            Text("shiorifolio", color = palette.ink, fontFamily = FontFamily.SansSerif,
                fontSize = 19.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.7).sp)
        }
        Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            MeridianSection.entries.forEach { section ->
                ModernChromeAction(section.stitchTabTitle, palette, "section_tab_${section.name}", section == activeSection) {
                    onSelectSection(section)
                }
            }
        }
        ModernChromeAction(if (theme == ReaderTheme.DARK) "◐  Oscuro" else "◐  Claro", palette, "theme_toggle_button", onClick = onToggleTheme)
    }
}

@Composable
internal fun ModernReaderToolbar(
    palette: MeridianPalette,
    book: BookDocument,
    back: NavigationHistoryEntry?,
    forward: NavigationHistoryEntry?,
    showToc: Boolean,
    phraseCount: Int,
    fitMode: PageFitMode,
    zoomScale: Float,
    pdfColorMode: PdfPageColorMode,
    onBack: () -> Unit,
    onForward: () -> Unit,
    onToc: () -> Unit,
    onNotebook: () -> Unit,
    onFit: (PageFitMode) -> Unit,
    onZoom: (Float) -> Unit,
    onPdfColor: (PdfPageColorMode) -> Unit,
) {
    var showTools by remember { mutableStateOf(false) }
    Column(Modifier.widthIn(max = 1440.dp).fillMaxWidth().padding(bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.background(palette.pageSurface, RoundedCornerShape(12.dp))) {
                ModernChromeAction("←", palette, "nav_history_back_btn", enabled = back != null, description = "Volver en el historial", onClick = onBack)
                ModernChromeAction("→", palette, "nav_history_forward_btn", enabled = forward != null, description = "Avanzar en el historial", onClick = onForward)
            }
            Column(Modifier.weight(1f).padding(start = 6.dp)) {
                Text(book.title, color = palette.ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(book.author, color = palette.inkSoft, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (book.tableOfContents.size > 1) ModernChromeAction("☰  Capítulos", palette, "toc_toggle_btn", showToc, onClick = onToc)
            ZoomAndFitControlStrip(palette, fitMode, zoomScale, onFit, onZoom)
            Box {
                ModernChromeAction("⋯", palette, "reader_tools_menu", description = "Herramientas del libro") { showTools = true }
                DropdownMenu(showTools, { showTools = false }) {
                    DropdownMenuItem(text = { Text("$phraseCount anotaciones") }, onClick = { showTools = false; onNotebook() }, modifier = Modifier.testTag("reader_notebook"))
                    if (book.format == DocumentFormat.PDF) {
                        HorizontalDivider()
                        PdfPageColorMode.entries.forEach { mode ->
                            DropdownMenuItem(text = { Text((if (mode == pdfColorMode) "✓  " else "") + mode.shortLabel) },
                                onClick = { onPdfColor(mode); showTools = false }, modifier = Modifier.testTag("reader_pdf_color_${mode.name}"))
                        }
                    }
                }
            }
        }
    }
}


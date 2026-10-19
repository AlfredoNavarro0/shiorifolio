package dev.meridian.folio.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.meridian.folio.core.BookDocument
import dev.meridian.folio.core.PageFitMode
import dev.meridian.folio.ui.theme.*

/** Secondary actions stay discoverable in one menu, including configured shortcuts. */
internal data class ReaderHeaderMenuItem(
    val label: String,
    val tag: String,
    val shortcut: String = "",
    val enabled: Boolean = true,
    val onClick: () -> Unit,
)

/** One compact book bar; tool panels and chapter lists expand only when requested. */
@Composable
internal fun ReaderHeaderBar(
    palette: ClayPalette,
    book: BookDocument,
    appearanceOpen: Boolean,
    hasReminder: Boolean,
    isFullscreen: Boolean,
    fitMode: PageFitMode,
    zoomScale: Float,
    onBackToLibrary: () -> Unit,
    onAppearance: () -> Unit,
    onReminder: () -> Unit,
    onMarkPhrase: () -> Unit,
    onFullscreen: () -> Unit,
    onFit: (PageFitMode) -> Unit,
    onZoom: (Float) -> Unit,
    onChapter: (Int, String) -> Unit,
    menuItems: List<ReaderHeaderMenuItem>,
) {
    var showTools by remember(book.id) { mutableStateOf(false) }
    var showChapters by remember(book.id) { mutableStateOf(false) }
    val entries = remember(book.id, book.pages) { book.tableOfContents }
    val chapterNumber = remember(entries, book.currentPage) {
        entries.indexOfLast { it.startPage <= book.currentPage } + 1
    }
    ClayContentCard(Modifier.fillMaxWidth().padding(horizontal = ClaySpacing.md, vertical = 4.dp)) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val compact = maxWidth < 1120.dp
            Row(
                Modifier.fillMaxWidth().padding(horizontal = ClaySpacing.sm, vertical = 6.dp)
                    .testTag("reader_book_strip"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                ClayHeaderAction("←", palette, "reader_back_to_library_btn", description = "Volver a Biblioteca", onClick = onBackToLibrary)
                Column(Modifier.weight(1f).padding(horizontal = 4.dp)) {
                    Text(
                        if (chapterNumber > 0) "${book.format.label} · Cap. $chapterNumber" else book.format.label,
                        color = palette.textMuted, style = LocalClayTypography.current.labelSm,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    Text(book.title, color = palette.onSurface, style = LocalClayTypography.current.labelLg,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                ClayHeaderAction(if (compact) "Aa" else "Aa  Apariencia", palette, "top_typography_popover_btn",
                    appearanceOpen, description = "Apariencia", onClick = onAppearance)
                ClayHeaderAction(if (compact) "⚑" else "⚑  Marcapáginas", palette, "open_reminder_btn",
                    hasReminder, description = "Marcapáginas", onClick = onReminder)
                ClayHeaderAction(if (compact) "+" else "+  Frase", palette, "open_mark_phrase_btn",
                    description = "Guardar frase", onClick = onMarkPhrase)
                Box {
                    ClayHeaderAction(if (compact) "☷" else "☷  Capítulos", palette, "toc_toggle_btn",
                        showChapters, description = "Ir a un capítulo") { showChapters = true }
                    DropdownMenu(showChapters, { showChapters = false }) {
                        entries.forEach { entry ->
                            DropdownMenuItem(
                                text = { Text(entry.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                trailingIcon = { Text("${entry.startPage}", color = palette.textMuted) },
                                modifier = Modifier.testTag("toc_chapter_${entry.startPage}"),
                                onClick = { showChapters = false; onChapter(entry.startPage, entry.title) },
                            )
                        }
                    }
                }
                ZoomAndFitControlStrip(palette, fitMode, zoomScale, onFit, onZoom)
                ClayHeaderAction("⛶", palette, "fullscreen_toggle_btn", isFullscreen,
                    description = "Pantalla completa", onClick = onFullscreen)
                Box {
                    ClayHeaderAction("⋯", palette, "reader_tools_menu", showTools,
                        description = "Más herramientas y atajos") { showTools = true }
                    DropdownMenu(showTools, { showTools = false }) {
                        menuItems.forEach { item ->
                            DropdownMenuItem(
                                text = { Text(item.label) },
                                trailingIcon = {
                                    if (item.shortcut.isNotEmpty()) Text(item.shortcut, color = palette.textMuted,
                                        style = LocalClayTypography.current.labelSm)
                                },
                                enabled = item.enabled,
                                modifier = Modifier.testTag(item.tag),
                                onClick = { showTools = false; item.onClick() },
                            )
                        }
                    }
                }
            }
        }
    }
}

internal val MainNavigationSections = listOf(
    MeridianSection.LIBRARY, MeridianSection.READER, MeridianSection.LEXICON, MeridianSection.SETTINGS,
)

/** The navigation well is centred on desktop and scrolls in smaller windows. */
@Composable
internal fun MinimalTopNavigation(
    activeSection: MeridianSection,
    onSelectSection: (MeridianSection) -> Unit,
    themeLabel: String,
    themeGlyph: String,
    onToggleTheme: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalClayPalette.current
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val wide = maxWidth >= 1000.dp
        Row(
            Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("clay_top_nav")
                .padding(horizontal = ClaySpacing.lg, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(ClaySpacing.md),
        ) {
            Row(if (wide) Modifier.weight(1f) else Modifier,
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(ClaySpacing.xs)) {
                ClayLogoMark(32.dp)
                Text("shiorifolio", color = palette.onSurface, style = LocalClayTypography.current.titleMd,
                    fontWeight = FontWeight.ExtraBold, maxLines = 1)
            }
            val shape = RoundedCornerShape(percent = 50)
            Row(
                (if (wide) Modifier else Modifier.weight(1f))
                    .clip(shape).background(palette.surfaceSecondary).padding(3.dp)
                    .horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                MainNavigationSections.forEach { section ->
                    ClayHeaderAction(section.stitchTabTitle, palette, "section_tab_${section.name}",
                        section == activeSection, quiet = true) { onSelectSection(section) }
                }
            }
            Box(if (wide) Modifier.weight(1f) else Modifier, contentAlignment = Alignment.CenterEnd) {
                ClayHeaderAction(themeGlyph, palette, "theme_toggle_button", description = themeLabel,
                    onClick = onToggleTheme)
            }
        }
    }
}

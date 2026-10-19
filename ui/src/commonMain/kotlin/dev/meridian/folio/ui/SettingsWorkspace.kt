package dev.meridian.folio.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.meridian.folio.core.*
import dev.meridian.folio.ui.theme.*

/** Width of the category rail when the surface is wide enough for two columns. */
private val SettingsRailWidth = 196.dp

/** Width at which the category pills become a vertical rail instead of a scrolling strip. */
private val SettingsRailBreakpoint = 760.dp

/**
 * One entry of the settings rail. [id] is part of the test contract
 * (`settings_category_<id>`), so it must never be renamed.
 */
private enum class SettingsCategory(val id: String, val label: String, val glyph: String) {
    APPEARANCE("appearance", "Apariencia", "🎨"),
    READING("reading", "Lectura", "📖"),
    SHORTCUTS("shortcuts", "Atajos", "⌨"),
    DATA("data", "Datos y Almacenamiento", "🗄"),
}

/** Short presentation copy for the four real [ReaderTheme] tones. */
private fun toneDescription(theme: ReaderTheme): String = when (theme) {
    ReaderTheme.WHITE -> "Blanco cálido, como una página impresa bajo una luz suave."
    ReaderTheme.SEPIA -> "Pergamino de archivo para sesiones largas de lectura."
    ReaderTheme.SAGE -> "Verde salvia que descansa la vista en pantalla."
    ReaderTheme.DARK -> "Tinta nocturna con el mínimo brillo emitido."
}

/**
 * Desktop **Ajustes** surface, restyled on the clay design kit.
 *
 * Contract kept from the previous workspace (pinned by `MeridianFolioUiTest`,
 * `ReaderPaginationTest`, `ReaderKeyboardTest` and `EditingUiTest`): the
 * `settings_workspace_view` root, the four `settings_category_<id>` entries, the
 * `settings_theme_card` / `settings_ui_appearance_card` / `settings_book_typography_card` /
 * `settings_shortcuts_card` panels, `settings_theme_<THEME>`, the
 * `settings_pdf_original_mode_btn` / `settings_pdf_reflow_mode_btn` pair,
 * `ui_font_family_*` / `ui_font_weight_*` / `ui_high_contrast_toggle` / `ui_scale_*` /
 * `ui_font_size_*`, `book_*` typography controls, `pdf_original_typography_notice`,
 * `rebind_prompt_banner` / `cancel_rebind_btn` / `rebind_action_<ACTION>` /
 * `shortcut_badge_<ACTION>` / `reset_shortcuts_btn`, the `reset_*` buttons,
 * `backup_export_btn` / `backup_import_btn` and `data_status_message`.
 *
 * New tags from this task: `settings_header`, `settings_preview_card`,
 * `settings_try_reader_btn` and `settings_tone_card_<THEME>`.
 *
 * The chrome is always the light clay palette (`ClayTokens.chrome`): this surface never uses
 * the reading tone, which belongs to the Reader sheet. Every control is backed by real state
 * or a real repository callback; the reference's cloud auto-save, reader profile, "¡Respaldado!"
 * toast, fifth tone ("Clay Pastel") and recommendation badges are absent because the model has
 * no backing for them.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SettingsAndShortcutsWorkspaceView(
    palette: ClayPalette,
    theme: ReaderTheme,
    pdfPageColorMode: PdfPageColorMode,
    currentBook: BookDocument,
    fontSizePt: Float,
    lineSpacingMultiplier: Float,
    uiAppearance: UiAppearanceSettings,
    bookTypography: BookTypographySettings,
    keyBindings: Map<ReaderAction, KeyBindingSpec>,
    rebindingAction: ReaderAction?,
    onStartRebind: (ReaderAction) -> Unit,
    onCancelRebind: () -> Unit,
    onResetKeyBindings: () -> Unit,
    onSelectTheme: (ReaderTheme) -> Unit,
    onSelectPdfColorMode: (PdfPageColorMode) -> Unit,
    onFontSizeChange: (Float) -> Unit,
    onLineSpacingChange: (Float) -> Unit,
    onUpdateUiAppearance: ((UiAppearanceSettings) -> UiAppearanceSettings) -> Unit,
    onUpdateBookTypography: ((BookTypographySettings) -> BookTypographySettings) -> Unit,
    onResetUiAppearance: () -> Unit,
    onResetBookTypography: () -> Unit,
    onResetAllAppearance: () -> Unit,
    onExportBackup: (() -> Unit)? = null,
    onImportBackup: (() -> Unit)? = null,
    dataStatusMessage: String? = null,
    onOpenReader: () -> Unit = {},
) {
    var category by remember { mutableStateOf(SettingsCategory.APPEARANCE) }
    val originalPdf = currentBook.format == DocumentFormat.PDF && !bookTypography.pdfPreferReflowText
    SettingsShell(palette, category, { category = it; onCancelRebind() }, onExportBackup) {
        when (category) {
            SettingsCategory.APPEARANCE -> {
                SettingsPanelCard(
                    "settings_theme_card",
                    "Tema del Lector",
                    "El ambiente de la hoja de lectura; el resto de la interfaz mantiene el clay claro.",
                    palette,
                ) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(ClaySpacing.xs),
                    ) {
                        ReaderTheme.entries.forEach { value ->
                            SettingsToneCard(
                                value = value,
                                selected = value == theme,
                                palette = palette,
                                onSelect = onSelectTheme,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }

                SettingsPanelCard(
                    "settings_ui_appearance_card",
                    "Interfaz & Tipografía",
                    "Texto y tamaño de los menús, botones y paneles.",
                    palette,
                ) {
                    SettingsRow("Familia Tipográfica", palette) {
                        UiFontFamilyOption.entries.forEach { value ->
                            ClayChromeAction(value.label, palette, "ui_font_family_${value.name}", value == uiAppearance.fontFamily) {
                                onUpdateUiAppearance { it.copy(fontFamily = value) }
                            }
                        }
                    }
                    SettingsRow("Peso del Trazo", palette) {
                        UiFontWeightOption.entries.forEach { value ->
                            ClayChromeAction(value.label, palette, "ui_font_weight_${value.name}", value == uiAppearance.fontWeight) {
                                onUpdateUiAppearance { it.copy(fontWeight = value) }
                            }
                        }
                    }
                    SettingsRow("Tamaño de Texto", palette) {
                        SettingsStepper(
                            "${uiAppearance.fontSizeSp.toInt()} sp",
                            "ui_font_size",
                            palette,
                            { onUpdateUiAppearance { it.copy(fontSizeSp = (it.fontSizeSp - 1).coerceIn(11f, 18f)) } },
                            { onUpdateUiAppearance { it.copy(fontSizeSp = (it.fontSizeSp + 1).coerceIn(11f, 18f)) } },
                        )
                    }
                    SettingsRow("Escala de Interfaz", palette) {
                        SettingsStepper(
                            "${(uiAppearance.uiScale * 100).toInt()}%",
                            "ui_scale",
                            palette,
                            { onUpdateUiAppearance { it.copy(uiScale = (it.uiScale - 0.05f).coerceIn(0.85f, 1.3f)) } },
                            { onUpdateUiAppearance { it.copy(uiScale = (it.uiScale + 0.05f).coerceIn(0.85f, 1.3f)) } },
                        )
                    }
                    SettingsRow(
                        "Contraste Alto Táctil",
                        palette,
                        description = "Intensifica los bordes y el relieve clay para mejorar la legibilidad de los controles.",
                    ) {
                        Switch(
                            uiAppearance.highContrastText,
                            { checked -> onUpdateUiAppearance { it.copy(highContrastText = checked) } },
                            modifier = Modifier.testTag("ui_high_contrast_toggle"),
                        )
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        ClaySecondaryButton(
                            label = "Restablecer interfaz",
                            onClick = onResetUiAppearance,
                            tag = "reset_ui_appearance_btn",
                        )
                    }
                }

                SettingsPreviewCard(
                    palette = palette,
                    theme = theme,
                    currentBook = currentBook,
                    fontSizePt = fontSizePt,
                    lineSpacingMultiplier = lineSpacingMultiplier,
                    uiAppearance = uiAppearance,
                )

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    ClaySecondaryButton(
                        label = "Restablecer apariencia y lectura",
                        onClick = onResetAllAppearance,
                        tag = "reset_all_appearance_btn",
                    )
                }
            }

            SettingsCategory.READING -> {
                SettingsPanelCard(
                    "settings_book_typography_card",
                    "Tipografía del Libro",
                    "Personaliza cómo se distribuye el contenido en las páginas del libro abierto.",
                    palette,
                ) {
                    if (currentBook.format == DocumentFormat.PDF) {
                        SettingsRow("Contenido PDF", palette) {
                            ClayChromeAction("Original", palette, "settings_pdf_original_mode_btn", originalPdf) {
                                onUpdateBookTypography { it.copy(pdfPreferReflowText = false) }
                            }
                            ClayChromeAction("Texto adaptable", palette, "settings_pdf_reflow_mode_btn", !originalPdf) {
                                onUpdateBookTypography { it.copy(pdfPreferReflowText = true) }
                            }
                        }
                    }
                    if (originalPdf) {
                        Text(
                            "El PDF original conserva sus fuentes y su distribución. Activa Texto adaptable para personalizar la tipografía.",
                            color = palette.onSurfaceVariant,
                            style = LocalClayTypography.current.bodySm,
                            modifier = Modifier.testTag("pdf_original_typography_notice"),
                        )
                    } else {
                        SettingsRow("Fuente del libro", palette) {
                            BookFontFamilyOption.entries.forEach { value ->
                                ClayChromeAction(value.shortLabel, palette, "book_font_family_${value.name}", value == bookTypography.fontFamily) {
                                    onUpdateBookTypography { it.copy(fontFamily = value) }
                                }
                            }
                        }
                        SettingsRow("Alineación", palette) {
                            BookTextAlignment.entries.forEach { value ->
                                ClayChromeAction(value.shortLabel, palette, "book_text_align_${value.name}", value == bookTypography.textAlignment) {
                                    onUpdateBookTypography { it.copy(textAlignment = value) }
                                }
                            }
                        }
                        SettingsRow("Tamaño de letra", palette) {
                            SettingsStepper(
                                "${fontSizePt.toInt()} pt",
                                "book_font_size",
                                palette,
                                { onFontSizeChange(fontSizePt - 1) },
                                { onFontSizeChange(fontSizePt + 1) },
                            )
                        }
                        SettingsRow("Interlineado", palette) {
                            SettingsStepper(
                                "${(lineSpacingMultiplier * 10).toInt() / 10f}×",
                                "book_line_spacing",
                                palette,
                                { onLineSpacingChange(lineSpacingMultiplier - 0.1f) },
                                { onLineSpacingChange(lineSpacingMultiplier + 0.1f) },
                            )
                        }
                        SettingsRow("Separación entre párrafos", palette) {
                            SettingsStepper(
                                "${bookTypography.paragraphSpacingDp.toInt()}",
                                "book_para_spacing",
                                palette,
                                { onUpdateBookTypography { it.copy(paragraphSpacingDp = (it.paragraphSpacingDp - 2).coerceIn(4f, 32f)) } },
                                { onUpdateBookTypography { it.copy(paragraphSpacingDp = (it.paragraphSpacingDp + 2).coerceIn(4f, 32f)) } },
                            )
                        }
                        SettingsRow(
                            "Normalizar espacios",
                            palette,
                            description = "Colapsa los espacios excesivos que arrastran algunos EPUB.",
                        ) {
                            Switch(
                                bookTypography.normalizeExcessiveSpaces,
                                { checked -> onUpdateBookTypography { it.copy(normalizeExcessiveSpaces = checked) } },
                                modifier = Modifier.testTag("book_normalize_spaces_toggle"),
                            )
                        }
                    }
                    SettingsRow("Márgenes laterales", palette) {
                        SettingsStepper(
                            "${bookTypography.horizontalMarginDp.toInt()}",
                            "book_h_margin",
                            palette,
                            { onUpdateBookTypography { it.copy(horizontalMarginDp = (it.horizontalMarginDp - 4).coerceIn(8f, 72f)) } },
                            { onUpdateBookTypography { it.copy(horizontalMarginDp = (it.horizontalMarginDp + 4).coerceIn(8f, 72f)) } },
                        )
                    }
                    SettingsRow("Márgenes superior e inferior", palette) {
                        SettingsStepper(
                            "${bookTypography.verticalMarginDp.toInt()}",
                            "book_v_margin",
                            palette,
                            { onUpdateBookTypography { it.copy(verticalMarginDp = (it.verticalMarginDp - 4).coerceIn(8f, 56f)) } },
                            { onUpdateBookTypography { it.copy(verticalMarginDp = (it.verticalMarginDp + 4).coerceIn(8f, 56f)) } },
                        )
                    }
                    SettingsRow("Color de páginas PDF", palette) {
                        PdfPageColorMode.entries.forEach { value ->
                            ClayChromeAction(value.shortLabel, palette, "settings_pdf_color_${value.name}", value == pdfPageColorMode) {
                                onSelectPdfColorMode(value)
                            }
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        ClaySecondaryButton(
                            label = "Restablecer lectura",
                            onClick = onResetBookTypography,
                            tag = "reset_book_typography_btn",
                        )
                    }
                }
            }

            SettingsCategory.SHORTCUTS -> {
                val fullscreenSpec = keyBindings[ReaderAction.TOGGLE_FULLSCREEN] ?: ReaderAction.TOGGLE_FULLSCREEN.defaultSpec
                SettingsPanelCard(
                    "settings_shortcuts_card",
                    "Atajos de Teclado",
                    "Selecciona Cambiar y pulsa la nueva combinación. Esc cancela la captura.",
                    palette,
                ) {
                    if (rebindingAction != null) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(ClayRadius.base))
                                .background(palette.primaryContainer)
                                .padding(ClaySpacing.md)
                                .testTag("rebind_prompt_banner"),
                            horizontalArrangement = Arrangement.spacedBy(ClaySpacing.sm),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "Pulsa el nuevo atajo: ${rebindingAction.label}",
                                color = palette.onPrimaryContainer,
                                style = LocalClayTypography.current.bodyMd,
                                modifier = Modifier.weight(1f),
                            )
                            ClaySecondaryButton(
                                label = "Cancelar",
                                onClick = onCancelRebind,
                                tag = "cancel_rebind_btn",
                            )
                        }
                    }
                    ReaderAction.entries.forEach { action ->
                        val spec = keyBindings[action] ?: action.defaultSpec
                        SettingsRow(action.label, palette, description = action.description) {
                            ShortcutBadge(action, spec, palette)
                            ClayChromeAction(
                                label = if (rebindingAction == action) "Cancelar" else "Cambiar",
                                palette = palette,
                                tag = "rebind_action_${action.name}",
                                description = "Cambiar el atajo de ${action.label}",
                            ) {
                                if (rebindingAction == action) onCancelRebind() else onStartRebind(action)
                            }
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        ClaySecondaryButton(
                            label = "Restablecer atajos",
                            onClick = onResetKeyBindings,
                            tag = "reset_shortcuts_btn",
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(ClaySpacing.sm),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                "Modo Inmersivo (${fullscreenSpec.displayLabel()})",
                                color = palette.onSurface,
                                style = LocalClayTypography.current.titleMd,
                            )
                            Text(
                                "Atajo real de pantalla completa: al abrir el Reader puedes ocultar los controles con esta combinación.",
                                color = palette.onSurfaceVariant,
                                style = LocalClayTypography.current.bodySm,
                            )
                        }
                        ClayPrimaryButton(
                            label = "Probar en Reader",
                            onClick = onOpenReader,
                            tag = "settings_try_reader_btn",
                        )
                    }
                }
            }

            SettingsCategory.DATA -> {
                SettingsPanelCard(
                    "settings_data_backup_card",
                    "Respaldo Portátil",
                    "Exporta tu progreso, anotaciones, colecciones, recordatorios y preferencias a un archivo .json que puedes llevar a otro dispositivo.",
                    palette,
                ) {
                    SettingsRow("Copia de seguridad", palette) {
                        if (onExportBackup != null) {
                            ClaySecondaryButton(label = "Exportar respaldo", onClick = onExportBackup, tag = "backup_export_btn")
                        }
                        if (onImportBackup != null) {
                            ClaySecondaryButton(label = "Importar respaldo", onClick = onImportBackup, tag = "backup_import_btn")
                        }
                    }
                    Text(
                        "Los libros se reasocian por huella de contenido al importar: el progreso vuelve a su sitio aunque el archivo esté en otra carpeta. " +
                            "Los libros ausentes se informan al terminar y nunca se elimina nada de lo que ya tienes.",
                        color = palette.onSurfaceVariant,
                        style = LocalClayTypography.current.bodySm,
                    )
                    if (dataStatusMessage != null) {
                        // Clay snackbar: frosted card surface in a sibling layer (`clayFrosted`
                        // blurs its own content, so the message must not live inside it).
                        val statusShape = RoundedCornerShape(ClayRadius.md)
                        Box(modifier = Modifier.fillMaxWidth().testTag("data_status_message")) {
                            Box(Modifier.matchParentSize().clayFrosted(statusShape, surface = palette.cardSurface))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.dp, palette.outlineVariant.copy(alpha = 0.7f), statusShape)
                                    .padding(ClaySpacing.sm),
                            ) {
                                Text(dataStatusMessage, color = palette.onSurface, style = LocalClayTypography.current.bodySm)
                            }
                        }
                    }
                }
                SettingsPanelCard(
                    "settings_data_validation_card",
                    "Validación de EPUB",
                    "Cada exportación regenera contenido, índice y referencias internas, y se valida antes de entregarla.",
                    palette,
                ) {
                    Text(
                        "ShioriFolio valida por sí mismo la estructura del EPUB generado (manifiesto, índice y referencias). " +
                            "Si tienes epubcheck instalado, además se ejecuta esa comprobación externa sin bloquear nada si no está disponible.",
                        color = palette.onSurfaceVariant,
                        style = LocalClayTypography.current.bodySm,
                    )
                }
            }
        }
    }
}

/**
 * Header (`settings_header`) plus the mock's `Respaldar` action. The action reuses the real
 * `onExportBackup` callback (hidden when absent); the contract export control
 * (`backup_export_btn`) stays in the Datos panel so `EditingUiTest` keeps clicking a control
 * inside the scrollable panel.
 */
@Composable
private fun SettingsHeader(palette: ClayPalette, onExportBackup: (() -> Unit)?) {
    val typography = LocalClayTypography.current
    Row(
        modifier = Modifier.fillMaxWidth().testTag("settings_header"),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.weight(1f)) {
            Text("Preferencias del Sistema", color = palette.onSurface, style = typography.headlineLarge)
            Text("Tu espacio de lectura, a tu manera.", color = palette.onSurfaceVariant, style = typography.bodyMd)
        }
        if (onExportBackup != null) {
            ClayPrimaryButton(label = "⤓  Respaldar", onClick = onExportBackup)
        }
    }
}

/** Rail (wide) or scrolling pill strip (narrow) with the four real categories. */
@Composable
private fun SettingsShell(
    palette: ClayPalette,
    selected: SettingsCategory,
    onSelect: (SettingsCategory) -> Unit,
    onExportBackup: (() -> Unit)?,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.canvas)
            .padding(ClaySpacing.lg)
            .testTag("settings_workspace_view"),
        verticalArrangement = Arrangement.spacedBy(ClaySpacing.lg),
    ) {
        SettingsHeader(palette, onExportBackup)
        BoxWithConstraints(Modifier.weight(1f)) {
            val panel: @Composable (Modifier) -> Unit = { modifier ->
                key(selected) {
                    Column(
                        modifier = modifier.verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(ClaySpacing.md),
                        content = content,
                    )
                }
            }
            if (maxWidth >= SettingsRailBreakpoint) {
                Row(horizontalArrangement = Arrangement.spacedBy(ClaySpacing.lg)) {
                    SettingsCategoryRail(selected, palette, onSelect, Modifier.width(SettingsRailWidth))
                    panel(Modifier.weight(1f).fillMaxHeight())
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(ClaySpacing.md)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(ClaySpacing.xs),
                    ) {
                        SettingsCategory.entries.forEach { entry ->
                            ClayChromeAction(entry.label, palette, "settings_category_${entry.id}", selected == entry) {
                                onSelect(entry)
                            }
                        }
                    }
                    panel(Modifier.weight(1f).fillMaxWidth())
                }
            }
        }
    }
}

/** Vertical clay rail of categories; each item keeps the `settings_category_<id>` tag. */
@Composable
private fun SettingsCategoryRail(
    selected: SettingsCategory,
    palette: ClayPalette,
    onSelect: (SettingsCategory) -> Unit,
    modifier: Modifier = Modifier,
) {
    val typography = LocalClayTypography.current
    Column(modifier, verticalArrangement = Arrangement.spacedBy(ClaySpacing.xs)) {
        SettingsCategory.entries.forEach { entry ->
            val isSelected = entry == selected
            val shape = RoundedCornerShape(ClayRadius.base)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(shape)
                    .background(if (isSelected) palette.primary else palette.surfaceContainerHigh)
                    .then(
                        if (isSelected) {
                            Modifier
                        } else {
                            Modifier.border(1.dp, palette.outlineVariant.copy(alpha = 0.6f), shape)
                        },
                    )
                    .clayFocusableClickable(palette, shape = shape, onClick = { onSelect(entry) })
                    .testTag("settings_category_${entry.id}")
                    .padding(horizontal = ClaySpacing.md, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(ClaySpacing.sm),
            ) {
                Text(entry.glyph, fontSize = 15.sp)
                Text(
                    entry.label,
                    color = if (isSelected) palette.onPrimary else palette.onSurface,
                    style = typography.labelLg,
                    maxLines = 2,
                )
            }
        }
    }
}

/** Content-sized clay panel (`tag` is the category card pinned by the tests). */
@Composable
private fun SettingsPanelCard(
    tag: String,
    title: String,
    subtitle: String,
    palette: ClayPalette,
    content: @Composable ColumnScope.() -> Unit,
) {
    val typography = LocalClayTypography.current
    ClayContentCard(Modifier.fillMaxWidth().testTag(tag)) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(ClaySpacing.lg),
            verticalArrangement = Arrangement.spacedBy(ClaySpacing.md),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, color = palette.onSurface, style = typography.headlineSm)
                Text(subtitle, color = palette.onSurfaceVariant, style = typography.bodySm)
            }
            content()
        }
    }
}

/**
 * One of the four real [ReaderTheme] tones as a selectable clay card: the real swatch color
 * (`Color(theme.swatchHex)`), its `stitchLabel` and a short description. The card carries
 * `settings_tone_card_<THEME>` and the clickable body keeps the contract tag
 * `settings_theme_<THEME>`; selection paints a `primary` ring.
 */
@Composable
private fun SettingsToneCard(
    value: ReaderTheme,
    selected: Boolean,
    palette: ClayPalette,
    onSelect: (ReaderTheme) -> Unit,
    modifier: Modifier = Modifier,
) {
    val typography = LocalClayTypography.current
    val shape = RoundedCornerShape(ClayRadius.md)
    ClayContentCard(modifier.testTag("settings_tone_card_${value.name}")) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 140.dp)
                .clip(shape)
                .then(if (selected) Modifier.border(2.dp, palette.primary, shape) else Modifier)
                .clayFocusableClickable(palette, shape = shape, onClick = { onSelect(value) })
                .testTag("settings_theme_${value.name}")
                .padding(ClaySpacing.md),
            verticalArrangement = Arrangement.spacedBy(ClaySpacing.xs),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(ClaySpacing.xs),
            ) {
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(Color(value.swatchHex))
                        .border(1.dp, palette.outlineVariant, CircleShape),
                )
                Text(
                    value.stitchLabel,
                    color = palette.onSurface,
                    style = typography.labelLg,
                    modifier = Modifier.weight(1f),
                    maxLines = 2,
                )
            }
            Text(
                toneDescription(value),
                color = palette.onSurfaceVariant,
                style = typography.bodySm,
                maxLines = 3,
            )
            if (selected) {
                Text("Tono activo", color = palette.primary, style = typography.labelSm)
            }
        }
    }
}

/**
 * Live preview of the interface typography with the real current values: the label reports
 * the active font size and weight, the paragraph is rendered with the active family/weight/size
 * and the footer shows the real reading position and book typography numbers.
 */
@Composable
private fun SettingsPreviewCard(
    palette: ClayPalette,
    theme: ReaderTheme,
    currentBook: BookDocument,
    fontSizePt: Float,
    lineSpacingMultiplier: Float,
    uiAppearance: UiAppearanceSettings,
) {
    val typography = LocalClayTypography.current
    val currentPage = currentBook.pages.find { it.pageNumber == currentBook.currentPage }
    SettingsPanelCard(
        tag = "settings_preview_card",
        title = "Vista Previa en Vivo",
        subtitle = "Así se ve el texto de la interfaz con los valores activos ahora mismo.",
        palette = palette,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(ClaySpacing.xs),
        ) {
            SettingsPill("VISTA PREVIA EN VIVO", palette)
            Text(
                "${uiAppearance.fontSizeSp.toInt()} sp · ${uiAppearance.fontWeight.label} · ${theme.stitchLabel}",
                color = palette.textMuted,
                style = typography.labelMd,
            )
        }
        Text(
            "El clay guarda la calidez de la página impresa: bordes suaves, sombras profundas y una tipografía que se adapta a tu ritmo de lectura.",
            color = palette.onSurface,
            style = typography.bodyMd.copy(
                fontFamily = uiAppearance.fontFamily.toComposeFontFamily(),
                fontWeight = uiAppearance.fontWeight.toComposeFontWeight(),
                fontSize = uiAppearance.fontSizeSp.sp,
                lineHeight = (uiAppearance.fontSizeSp * 1.6f).sp,
            ),
        )
        Text(
            "${currentPage?.chapterTitle?.takeIf { it.isNotBlank() } ?: currentBook.title} · Pág. ${currentBook.currentPage} de ${currentBook.totalPages} · " +
                "${fontSizePt.toInt()} pt · interlineado ${lineSpacingMultiplier}×",
            color = palette.onSurfaceVariant,
            style = typography.bodySm,
        )
    }
}

/** Non-interactive clay capsule for labels (no click handler, so it is not a dead control). */
@Composable
private fun SettingsPill(text: String, palette: ClayPalette) {
    val shape = RoundedCornerShape(percent = 50)
    Box(
        modifier = Modifier
            .clip(shape)
            .background(palette.primaryContainer)
            .border(1.dp, palette.primary.copy(alpha = 0.35f), shape)
            .padding(horizontal = ClaySpacing.sm, vertical = 4.dp),
    ) {
        Text(text, color = palette.onPrimaryContainer, style = LocalClayTypography.current.labelSm, maxLines = 1, softWrap = false)
    }
}

/** Real key combination of [spec] in the `shortcut_badge_<ACTION>` capsule. */
@Composable
private fun ShortcutBadge(action: ReaderAction, spec: KeyBindingSpec, palette: ClayPalette) {
    val shape = RoundedCornerShape(ClayRadius.sm)
    Box(
        modifier = Modifier
            .clip(shape)
            .background(palette.surfaceContainerHigh)
            .border(1.dp, palette.outlineVariant.copy(alpha = 0.6f), shape)
            .padding(horizontal = ClaySpacing.sm, vertical = 8.dp),
    ) {
        Text(
            spec.displayLabel(),
            color = palette.onSurface,
            style = LocalClayTypography.current.labelMd,
            modifier = Modifier.testTag("shortcut_badge_${action.name}"),
            maxLines = 1,
            softWrap = false,
        )
    }
}

/** Label (+ optional description) at the left and the controls at the right (stacked when narrow). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SettingsRow(
    label: String,
    palette: ClayPalette,
    description: String? = null,
    controls: @Composable FlowRowScope.() -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (maxWidth >= 540.dp) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(ClaySpacing.md),
            ) {
                SettingsRowLabel(label, description, palette, Modifier.weight(1f))
                FlowRow(
                    modifier = Modifier.widthIn(max = 420.dp),
                    horizontalArrangement = Arrangement.spacedBy(ClaySpacing.xs),
                    verticalArrangement = Arrangement.spacedBy(ClaySpacing.xs),
                    content = controls,
                )
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(ClaySpacing.xs)) {
                SettingsRowLabel(label, description, palette)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(ClaySpacing.xs),
                    verticalArrangement = Arrangement.spacedBy(ClaySpacing.xs),
                    content = controls,
                )
            }
        }
    }
}

@Composable
private fun SettingsRowLabel(label: String, description: String?, palette: ClayPalette, modifier: Modifier = Modifier) {
    val typography = LocalClayTypography.current
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, color = palette.onSurface, style = typography.bodyMd.copy(fontWeight = typography.labelLg.fontWeight))
        if (description != null) {
            Text(description, color = palette.textMuted, style = typography.bodySm)
        }
    }
}

/** Recessed stepper; keeps the `${tag}_dec_btn` / `${tag}_label` / `${tag}_inc_btn` contract. */
@Composable
private fun SettingsStepper(value: String, tag: String, palette: ClayPalette, onDecrease: () -> Unit, onIncrease: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(ClayRadius.base))
            .background(palette.surfaceContainerHigh)
            .padding(ClaySpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ClayChromeAction("−", palette, "${tag}_dec_btn", description = "Disminuir $tag", onClick = onDecrease)
        Text(
            value,
            color = palette.onSurface,
            style = LocalClayTypography.current.labelLg,
            modifier = Modifier.padding(horizontal = ClaySpacing.sm).testTag("${tag}_label"),
            maxLines = 1,
            softWrap = false,
        )
        ClayChromeAction("+", palette, "${tag}_inc_btn", description = "Aumentar $tag", onClick = onIncrease)
    }
}

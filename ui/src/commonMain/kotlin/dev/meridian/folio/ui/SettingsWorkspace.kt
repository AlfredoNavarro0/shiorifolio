package dev.meridian.folio.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.meridian.folio.core.*

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SettingsAndShortcutsWorkspaceView(
    palette: MeridianPalette,
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
) {
    var category by remember { mutableStateOf("appearance") }
    val originalPdf = currentBook.format == DocumentFormat.PDF && !bookTypography.pdfPreferReflowText
    SettingsShell(palette, category, { category = it; onCancelRebind() }) {
        when (category) {
            "appearance" -> {
                SettingsGroup("Tema", "Elige el ambiente de tu biblioteca y del lector.", palette, "settings_theme_card") {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ReaderTheme.entries.forEach { value ->
                            ModernChromeAction(value.stitchLabel, palette, "settings_theme_${value.name}", value == theme) { onSelectTheme(value) }
                        }
                    }
                }
                SettingsGroup("Interfaz", "Texto y tamaño de los menús y controles.", palette, "settings_ui_appearance_card") {
                    SettingsRow("Tipografía", palette) {
                        UiFontFamilyOption.entries.forEach { value ->
                            ModernChromeAction(value.label, palette, "ui_font_family_${value.name}", value == uiAppearance.fontFamily) {
                                onUpdateUiAppearance { it.copy(fontFamily = value) }
                            }
                        }
                    }
                    SettingsRow("Peso del texto", palette) {
                        UiFontWeightOption.entries.forEach { value ->
                            ModernChromeAction(value.label, palette, "ui_font_weight_${value.name}", value == uiAppearance.fontWeight) {
                                onUpdateUiAppearance { it.copy(fontWeight = value) }
                            }
                        }
                    }
                    SettingsRow("Tamaño del texto", palette) {
                        SettingsStepper("${uiAppearance.fontSizeSp.toInt()} sp", "ui_font_size", palette,
                            { onUpdateUiAppearance { it.copy(fontSizeSp = (it.fontSizeSp - 1).coerceIn(11f, 18f)) } },
                            { onUpdateUiAppearance { it.copy(fontSizeSp = (it.fontSizeSp + 1).coerceIn(11f, 18f)) } })
                    }
                    SettingsRow("Escala de la interfaz", palette) {
                        SettingsStepper("${(uiAppearance.uiScale * 100).toInt()}%", "ui_scale", palette,
                            { onUpdateUiAppearance { it.copy(uiScale = (it.uiScale - 0.05f).coerceIn(0.85f, 1.3f)) } },
                            { onUpdateUiAppearance { it.copy(uiScale = (it.uiScale + 0.05f).coerceIn(0.85f, 1.3f)) } })
                    }
                    SettingsRow("Contraste alto", palette) {
                        Switch(uiAppearance.highContrastText, { checked -> onUpdateUiAppearance { it.copy(highContrastText = checked) } },
                            modifier = Modifier.testTag("ui_high_contrast_toggle"))
                    }
                    TextButton(onResetUiAppearance, modifier = Modifier.testTag("reset_ui_appearance_btn")) { Text("Restablecer interfaz") }
                }
                TextButton(onResetAllAppearance, modifier = Modifier.testTag("reset_all_appearance_btn")) { Text("Restablecer apariencia y lectura") }
            }
            "reading" -> {
                SettingsGroup("Lectura", "Personaliza cómo se distribuye el contenido en las páginas.", palette, "settings_book_typography_card") {
                    if (currentBook.format == DocumentFormat.PDF) {
                        SettingsRow("Contenido PDF", palette) {
                            ModernChromeAction("Original", palette, "settings_pdf_original_mode_btn", originalPdf) { onUpdateBookTypography { it.copy(pdfPreferReflowText = false) } }
                            ModernChromeAction("Texto adaptable", palette, "settings_pdf_reflow_mode_btn", !originalPdf) { onUpdateBookTypography { it.copy(pdfPreferReflowText = true) } }
                        }
                    }
                    if (originalPdf) {
                        Text("El PDF original conserva sus fuentes y su distribución. Activa Texto adaptable para personalizar la tipografía.",
                            color = palette.inkSoft, fontSize = 13.sp, modifier = Modifier.testTag("pdf_original_typography_notice"))
                    } else {
                        SettingsRow("Fuente del libro", palette) {
                            BookFontFamilyOption.entries.forEach { value ->
                                ModernChromeAction(value.shortLabel, palette, "book_font_family_${value.name}", value == bookTypography.fontFamily) {
                                    onUpdateBookTypography { it.copy(fontFamily = value) }
                                }
                            }
                        }
                        SettingsRow("Alineación", palette) {
                            BookTextAlignment.entries.forEach { value ->
                                ModernChromeAction(value.shortLabel, palette, "book_text_align_${value.name}", value == bookTypography.textAlignment) {
                                    onUpdateBookTypography { it.copy(textAlignment = value) }
                                }
                            }
                        }
                        SettingsRow("Tamaño de letra", palette) {
                            SettingsStepper("${fontSizePt.toInt()} pt", "book_font_size", palette, { onFontSizeChange(fontSizePt - 1) }, { onFontSizeChange(fontSizePt + 1) })
                        }
                        SettingsRow("Interlineado", palette) {
                            SettingsStepper("${(lineSpacingMultiplier * 10).toInt() / 10f}×", "book_line_spacing", palette,
                                { onLineSpacingChange(lineSpacingMultiplier - 0.1f) }, { onLineSpacingChange(lineSpacingMultiplier + 0.1f) })
                        }
                        SettingsRow("Separación entre párrafos", palette) {
                            SettingsStepper("${bookTypography.paragraphSpacingDp.toInt()}", "book_para_spacing", palette,
                                { onUpdateBookTypography { it.copy(paragraphSpacingDp = (it.paragraphSpacingDp - 2).coerceIn(4f, 32f)) } },
                                { onUpdateBookTypography { it.copy(paragraphSpacingDp = (it.paragraphSpacingDp + 2).coerceIn(4f, 32f)) } })
                        }
                        SettingsRow("Normalizar espacios", palette) {
                            Switch(bookTypography.normalizeExcessiveSpaces, { checked -> onUpdateBookTypography { it.copy(normalizeExcessiveSpaces = checked) } },
                                modifier = Modifier.testTag("book_normalize_spaces_toggle"))
                        }
                    }
                    SettingsRow("Márgenes laterales", palette) {
                        SettingsStepper("${bookTypography.horizontalMarginDp.toInt()}", "book_h_margin", palette,
                            { onUpdateBookTypography { it.copy(horizontalMarginDp = (it.horizontalMarginDp - 4).coerceIn(8f, 72f)) } },
                            { onUpdateBookTypography { it.copy(horizontalMarginDp = (it.horizontalMarginDp + 4).coerceIn(8f, 72f)) } })
                    }
                    SettingsRow("Márgenes superior e inferior", palette) {
                        SettingsStepper("${bookTypography.verticalMarginDp.toInt()}", "book_v_margin", palette,
                            { onUpdateBookTypography { it.copy(verticalMarginDp = (it.verticalMarginDp - 4).coerceIn(8f, 56f)) } },
                            { onUpdateBookTypography { it.copy(verticalMarginDp = (it.verticalMarginDp + 4).coerceIn(8f, 56f)) } })
                    }
                    SettingsRow("Color de páginas PDF", palette) {
                        PdfPageColorMode.entries.forEach { value ->
                            ModernChromeAction(value.shortLabel, palette, "settings_pdf_color_${value.name}", value == pdfPageColorMode) { onSelectPdfColorMode(value) }
                        }
                    }
                    TextButton(onResetBookTypography, modifier = Modifier.testTag("reset_book_typography_btn")) { Text("Restablecer lectura") }
                }
            }
            "shortcuts" -> SettingsGroup("Atajos de teclado", "Selecciona Cambiar y pulsa la nueva combinación. Esc cancela la captura.", palette, "settings_shortcuts_card") {
                if (rebindingAction != null) {
                    Surface(color = palette.accentGreen.copy(alpha = 0.1f), shape = RoundedCornerShape(12.dp), modifier = Modifier.testTag("rebind_prompt_banner")) {
                        Column(Modifier.fillMaxWidth().padding(16.dp)) {
                            Text("Pulsa el nuevo atajo: ${rebindingAction.label}", color = palette.ink)
                            TextButton(onCancelRebind, modifier = Modifier.testTag("cancel_rebind_btn")) { Text("Cancelar") }
                        }
                    }
                }
                ReaderAction.entries.forEach { action ->
                    SettingsRow(action.label, palette) {
                        Surface(color = palette.pillBg, shape = RoundedCornerShape(8.dp)) {
                            Text((keyBindings[action] ?: action.defaultSpec).displayLabel(), color = palette.ink,
                                modifier = Modifier.padding(10.dp).testTag("shortcut_badge_${action.name}"), fontSize = 12.sp)
                        }
                        ModernChromeAction(if (rebindingAction == action) "Cancelar" else "Cambiar", palette, "rebind_action_${action.name}") {
                            if (rebindingAction == action) onCancelRebind() else onStartRebind(action)
                        }
                    }
                }
                TextButton(onResetKeyBindings, modifier = Modifier.testTag("reset_shortcuts_btn")) { Text("Restablecer atajos") }
            }
            "data" -> {
                SettingsGroup(
                    "Respaldo portátil",
                    "Exporta tu progreso, anotaciones, colecciones, recordatorios y preferencias a un archivo .json que puedes llevar a otro dispositivo.",
                    palette,
                    "settings_data_backup_card",
                ) {
                    SettingsRow("Copia de seguridad", palette) {
                        if (onExportBackup != null) {
                            ModernChromeAction("Exportar respaldo", palette, "backup_export_btn") { onExportBackup() }
                        }
                        if (onImportBackup != null) {
                            ModernChromeAction("Importar respaldo", palette, "backup_import_btn") { onImportBackup() }
                        }
                    }
                    Text(
                        "Los libros se reasocian por huella de contenido al importar: el progreso vuelve a su sitio aunque el archivo esté en otra carpeta. " +
                            "Los libros ausentes se informan al terminar y nunca se elimina nada de lo que ya tienes.",
                        color = palette.inkSoft,
                        fontSize = 12.sp,
                    )
                    if (dataStatusMessage != null) {
                        Surface(
                            color = palette.pillBg,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth().testTag("data_status_message"),
                        ) {
                            Text(dataStatusMessage, color = palette.ink, fontSize = 12.5.sp, modifier = Modifier.padding(12.dp))
                        }
                    }
                }
                SettingsGroup(
                    "Validación de EPUB",
                    "Cada exportación regenera contenido, índice y referencias internas, y se valida antes de entregarla.",
                    palette,
                    "settings_data_validation_card",
                ) {
                    Text(
                        "ShioriFolio valida por sí mismo la estructura del EPUB generado (manifiesto, índice y referencias). " +
                            "Si tienes epubcheck instalado, además se ejecuta esa comprobación externa sin bloquear nada si no está disponible.",
                        color = palette.inkSoft,
                        fontSize = 12.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsShell(palette: MeridianPalette, selected: String, onSelect: (String) -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val categories = listOf("appearance" to "Apariencia", "reading" to "Lectura", "shortcuts" to "Atajos", "data" to "Datos")
    Column(Modifier.fillMaxSize().background(palette.paper).padding(24.dp).testTag("settings_workspace_view"), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Column {
            Text("Ajustes", color = palette.ink, fontSize = 30.sp, fontWeight = FontWeight.Bold)
            Text("Tu espacio de lectura, a tu manera.", color = palette.inkSoft, fontSize = 13.sp)
        }
        BoxWithConstraints(Modifier.weight(1f)) {
            val panel: @Composable (Modifier) -> Unit = { modifier ->
                key(selected) {
                    Column(modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp), content = content)
                }
            }
            if (maxWidth >= 760.dp) Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Column(Modifier.width(180.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    categories.forEach { (id, label) ->
                        Surface(onClick = { onSelect(id) }, color = if (selected == id) palette.accentGreen else palette.paper,
                            shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().testTag("settings_category_$id")) {
                            Text(label, color = if (selected == id) palette.onAccent else palette.inkSoft,
                                fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(16.dp))
                        }
                    }
                }
                panel(Modifier.weight(1f).fillMaxHeight())
            } else Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                    categories.forEach { (id, label) -> ModernChromeAction(label, palette, "settings_category_$id", selected == id) { onSelect(id) } }
                }
                panel(Modifier.weight(1f).fillMaxWidth())
            }
        }
    }
}

@Composable
private fun SettingsGroup(title: String, subtitle: String, palette: MeridianPalette, tag: String, content: @Composable ColumnScope.() -> Unit) {
    Surface(color = palette.pageSurface, shape = RoundedCornerShape(18.dp), border = BorderStroke(1.dp, palette.border), modifier = Modifier.fillMaxWidth().testTag(tag)) {
        Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(title, color = palette.ink, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                Text(subtitle, color = palette.inkSoft, fontSize = 12.sp)
            }
            content()
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SettingsRow(label: String, palette: MeridianPalette, controls: @Composable FlowRowScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        HorizontalDivider(color = palette.border.copy(alpha = 0.6f))
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            if (maxWidth >= 540.dp) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(label, color = palette.ink, fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                FlowRow(Modifier.widthIn(max = 370.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), content = controls)
            } else Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(label, color = palette.ink, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), content = controls)
            }
        }
    }
}

@Composable
private fun SettingsStepper(value: String, tag: String, palette: MeridianPalette, onDecrease: () -> Unit, onIncrease: () -> Unit) {
    Row(Modifier.background(palette.pillBg, RoundedCornerShape(10.dp)), verticalAlignment = Alignment.CenterVertically) {
        ModernChromeAction("−", palette, "${tag}_dec_btn", description = "Disminuir $tag", onClick = onDecrease)
        Text(value, color = palette.ink, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 12.dp).testTag("${tag}_label"))
        ModernChromeAction("+", palette, "${tag}_inc_btn", description = "Aumentar $tag", onClick = onIncrease)
    }
}
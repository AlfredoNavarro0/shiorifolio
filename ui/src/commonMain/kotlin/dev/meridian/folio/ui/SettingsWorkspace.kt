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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import dev.meridian.folio.core.*
import dev.meridian.folio.ui.theme.*

/** Width of the appearance panel when the preview sits in its own right-hand column. */
private const val SettingsPanelWeight = 7f

/** Width of the live-preview column when the surface is wide enough for the mock's 7/5 grid. */
private const val SettingsPreviewWeight = 5f

/** Width at which the category pills wrap into two rows instead of a single stretched row. */
private val SettingsTabsWrapBreakpoint = 900.dp

/** Width at which the header's title block and the backup action stack instead of sitting in a row. */
private val SettingsHeaderRowBreakpoint = 720.dp

/** Width at which a settings row stacks its label above its controls. */
private val SettingsRowStackBreakpoint = 540.dp

/** Width at which the appearance panel and the live preview become two columns (mock's `lg` grid). */
private val SettingsTwoColumnBreakpoint = 1040.dp

/** Width of the circular stepper value column. */
private val SettingsStepperWidth = 210.dp

/** Fixed height of a tone card so the four real tones always align (mock's `h-36`). */
private val SettingsToneCardHeight = 144.dp

/**
 * One entry of the settings navigation. [id] is part of the test contract
 * (`settings_category_<id>`), so it must never be renamed.
 */
private enum class SettingsCategory(val id: String, val label: String, val glyph: String) {
    APPEARANCE("appearance", "Apariencia", "🎨"),
    READING("reading", "Lectura", "📖"),
    SHORTCUTS("shortcuts", "Atajos", "⌨"),
    DATA("data", "Datos y Almacenamiento", "🗄"),
}

/** Tile palette of a panel header; the appearance panels follow the mock, the rest reuse tokens. */
private enum class SettingsAccent { PRIMARY, SECONDARY, MUTED, MINT }

private fun SettingsAccent.tileContainer(palette: ClayPalette): Color = when (this) {
    SettingsAccent.PRIMARY -> palette.primaryContainer
    SettingsAccent.SECONDARY -> palette.secondaryContainer
    SettingsAccent.MUTED -> palette.surfaceContainerHigh
    SettingsAccent.MINT -> palette.successMint.copy(alpha = 0.18f)
}

private fun SettingsAccent.tileContent(palette: ClayPalette): Color = when (this) {
    SettingsAccent.PRIMARY -> palette.primary
    SettingsAccent.SECONDARY -> palette.secondary
    SettingsAccent.MUTED -> palette.onSurfaceVariant
    SettingsAccent.MINT -> palette.onSuccessMint
}

/**
 * Short presentation copy for the four real [ReaderTheme] tones. Static labels only (the mock's
 * wording for the same four pigments); no value is read from the model here.
 */
private fun toneDescription(theme: ReaderTheme): String = when (theme) {
    ReaderTheme.WHITE -> "Luz ámbar, 2800K"
    ReaderTheme.SEPIA -> "Papel vitela suave"
    ReaderTheme.SAGE -> "Verde botánico relajante"
    ReaderTheme.DARK -> "Tinta negra mate OLED"
}

/** Glyph inside a tone card's swatch circle; plain text, no icon font is bundled. */
private fun toneGlyph(theme: ReaderTheme): String = when (theme) {
    ReaderTheme.WHITE -> "☀"
    ReaderTheme.SEPIA -> "❋"
    ReaderTheme.SAGE -> "❧"
    ReaderTheme.DARK -> "☾"
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
 * New tags from the earlier task: `settings_header`, `settings_preview_card`,
 * `settings_try_reader_btn` and `settings_tone_card_<THEME>`; this pass adds the
 * `settings_quick_tone_<THEME>` quick swatches.
 *
 * The chrome is always the light clay palette (`ClayTokens.chrome`): this surface never uses
 * the reading tone for its own chrome, only to tint the live-preview canvas. Every control is
 * backed by real state or a real repository callback; the reference's cloud auto-save, reader
 * profile, "¡Respaldado!" toast, fifth tone ("Clay Pastel") and recommendation badges are absent
 * because the model has no backing for them. The header's `Respaldar` pill deliberately carries
 * no tag (documented): the tagged export control stays inside the Datos panel.
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

    // The live preview only accompanies the appearance panel (the mock only documents that tab).
    val preview: (@Composable ColumnScope.() -> Unit)? = if (category == SettingsCategory.APPEARANCE) {
        {
            SettingsPreviewCard(
                palette = palette,
                theme = theme,
                currentBook = currentBook,
                fontSizePt = fontSizePt,
                lineSpacingMultiplier = lineSpacingMultiplier,
                uiAppearance = uiAppearance,
                onSelectTheme = onSelectTheme,
            )
        }
    } else {
        null
    }

    SettingsShell(palette, category, { category = it; onCancelRebind() }, onExportBackup, preview) {
        when (category) {
            SettingsCategory.APPEARANCE -> {
                SettingsPanelCard(
                    tag = "settings_theme_card",
                    title = "Tema del Lector",
                    subtitle = "Luminancia y pigmentación adaptadas para descanso visual prolongado.",
                    palette = palette,
                    glyph = "🎨",
                    accent = SettingsAccent.PRIMARY,
                ) {
                    Column(
                        Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(ClaySpacing.md),
                    ) {
                        ReaderTheme.entries.chunked(2).forEach { row ->
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(ClaySpacing.md),
                            ) {
                                row.forEach { value ->
                                    SettingsToneCard(
                                        value = value,
                                        selected = value == theme,
                                        palette = palette,
                                        highContrast = uiAppearance.highContrastText,
                                        onSelect = onSelectTheme,
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                            }
                        }
                    }
                }

                SettingsPanelCard(
                    tag = "settings_ui_appearance_card",
                    title = "Interfaz & Tipografía",
                    subtitle = "Control dimensional de fuentes, pesos y volumen de lectura.",
                    palette = palette,
                    glyph = "Aa",
                    accent = SettingsAccent.SECONDARY,
                ) {
                    SettingsFamilyWell(
                        selected = uiAppearance.fontFamily,
                        palette = palette,
                        onSelect = { onUpdateUiAppearance { settings -> settings.copy(fontFamily = it) } },
                    )
                    SettingsWeightWell(
                        selected = uiAppearance.fontWeight,
                        palette = palette,
                        onSelect = { onUpdateUiAppearance { settings -> settings.copy(fontWeight = it) } },
                    )
                    SettingsRow("Tamaño de Texto", palette) {
                        SettingsStepper(
                            value = "${uiAppearance.fontSizeSp.toInt()} sp",
                            tag = "ui_font_size",
                            palette = palette,
                            accent = palette.primaryContainer,
                            fraction = (uiAppearance.fontSizeSp - 11f) / 7f,
                            onDecrease = { onUpdateUiAppearance { it.copy(fontSizeSp = (it.fontSizeSp - 1).coerceIn(11f, 18f)) } },
                            onIncrease = { onUpdateUiAppearance { it.copy(fontSizeSp = (it.fontSizeSp + 1).coerceIn(11f, 18f)) } },
                        )
                    }
                    SettingsRow("Escala de Interfaz", palette) {
                        SettingsStepper(
                            value = "${(uiAppearance.uiScale * 100).toInt()}%",
                            tag = "ui_scale",
                            palette = palette,
                            accent = palette.secondaryContainer,
                            fraction = (uiAppearance.uiScale - 0.85f) / 0.45f,
                            onDecrease = { onUpdateUiAppearance { it.copy(uiScale = (it.uiScale - 0.05f).coerceIn(0.85f, 1.3f)) } },
                            onIncrease = { onUpdateUiAppearance { it.copy(uiScale = (it.uiScale + 0.05f).coerceIn(0.85f, 1.3f)) } },
                        )
                    }
                    SettingsContrastRow(
                        checked = uiAppearance.highContrastText,
                        palette = palette,
                        onCheckedChange = { checked -> onUpdateUiAppearance { it.copy(highContrastText = checked) } },
                        onResetUiAppearance = onResetUiAppearance,
                    )
                }

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
                    tag = "settings_book_typography_card",
                    title = "Tipografía del Libro",
                    subtitle = "Personaliza cómo se distribuye el contenido en las páginas del libro abierto.",
                    palette = palette,
                    glyph = "📖",
                    accent = SettingsAccent.SECONDARY,
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
                                value = "${fontSizePt.toInt()} pt",
                                tag = "book_font_size",
                                palette = palette,
                                accent = palette.primaryContainer,
                                onDecrease = { onFontSizeChange(fontSizePt - 1) },
                                onIncrease = { onFontSizeChange(fontSizePt + 1) },
                            )
                        }
                        SettingsRow("Interlineado", palette) {
                            SettingsStepper(
                                value = "${(lineSpacingMultiplier * 10).toInt() / 10f}×",
                                tag = "book_line_spacing",
                                palette = palette,
                                accent = palette.primaryContainer,
                                onDecrease = { onLineSpacingChange(lineSpacingMultiplier - 0.1f) },
                                onIncrease = { onLineSpacingChange(lineSpacingMultiplier + 0.1f) },
                            )
                        }
                        SettingsRow("Separación entre párrafos", palette) {
                            SettingsStepper(
                                value = "${bookTypography.paragraphSpacingDp.toInt()}",
                                tag = "book_para_spacing",
                                palette = palette,
                                accent = palette.primaryContainer,
                                onDecrease = { onUpdateBookTypography { it.copy(paragraphSpacingDp = (it.paragraphSpacingDp - 2).coerceIn(4f, 32f)) } },
                                onIncrease = { onUpdateBookTypography { it.copy(paragraphSpacingDp = (it.paragraphSpacingDp + 2).coerceIn(4f, 32f)) } },
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
                            value = "${bookTypography.horizontalMarginDp.toInt()}",
                            tag = "book_h_margin",
                            palette = palette,
                            accent = palette.primaryContainer,
                            onDecrease = { onUpdateBookTypography { it.copy(horizontalMarginDp = (it.horizontalMarginDp - 4).coerceIn(8f, 72f)) } },
                            onIncrease = { onUpdateBookTypography { it.copy(horizontalMarginDp = (it.horizontalMarginDp + 4).coerceIn(8f, 72f)) } },
                        )
                    }
                    SettingsRow("Márgenes superior e inferior", palette) {
                        SettingsStepper(
                            value = "${bookTypography.verticalMarginDp.toInt()}",
                            tag = "book_v_margin",
                            palette = palette,
                            accent = palette.primaryContainer,
                            onDecrease = { onUpdateBookTypography { it.copy(verticalMarginDp = (it.verticalMarginDp - 4).coerceIn(8f, 56f)) } },
                            onIncrease = { onUpdateBookTypography { it.copy(verticalMarginDp = (it.verticalMarginDp + 4).coerceIn(8f, 56f)) } },
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
                    tag = "settings_shortcuts_card",
                    title = "Atajos de Teclado",
                    subtitle = "Selecciona Cambiar y pulsa la nueva combinación. Esc cancela la captura.",
                    palette = palette,
                    glyph = "⌨",
                    accent = SettingsAccent.MUTED,
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
                    tag = "settings_data_backup_card",
                    title = "Respaldo Portátil",
                    subtitle = "Exporta tu progreso, anotaciones, colecciones, recordatorios y preferencias a un archivo .json que puedes llevar a otro dispositivo.",
                    palette = palette,
                    glyph = "⤓",
                    accent = SettingsAccent.MINT,
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
                    tag = "settings_data_validation_card",
                    title = "Validación de EPUB",
                    subtitle = "Cada exportación regenera contenido, índice y referencias internas, y se valida antes de entregarla.",
                    palette = palette,
                    glyph = "✓",
                    accent = SettingsAccent.MUTED,
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
 * Header (`settings_header`) with the mock's clay icon tile, eyebrow and the quiet `Respaldar`
 * pill. The pill reuses the real `onExportBackup` callback (hidden when absent); the contract
 * export control (`backup_export_btn`) stays in the Datos panel so `EditingUiTest` keeps clicking
 * a control inside the scrollable panel.
 */
@Composable
private fun SettingsHeader(palette: ClayPalette, onExportBackup: (() -> Unit)?) {
    val typography = LocalClayTypography.current
    BoxWithConstraints(Modifier.fillMaxWidth().testTag("settings_header")) {
        val stack = maxWidth < SettingsHeaderRowBreakpoint
        val identity: @Composable () -> Unit = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(ClaySpacing.md),
            ) {
                SettingsHeaderTile(palette)
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(ClaySpacing.xs),
                    ) {
                        Box(Modifier.size(6.dp).clip(CircleShape).background(palette.primary))
                        Text(
                            "PREFERENCIAS DEL SISTEMA",
                            color = palette.primary,
                            style = typography.labelSm.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = 0.1.em),
                        )
                    }
                    Text("Ajustes", color = palette.onSurface, style = typography.headlineLarge)
                    Text("Tu espacio de lectura, a tu manera.", color = palette.onSurfaceVariant, style = typography.bodyMd)
                }
            }
        }
        if (stack) {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(ClaySpacing.md)) {
                identity()
                SettingsQuietPill("Respaldar", "⤓", palette, onExportBackup)
            }
        } else {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.weight(1f)) { identity() }
                SettingsQuietPill("Respaldar", "⤓", palette, onExportBackup)
            }
        }
    }
}

/** 80.dp clay tile with the palette glyph and the tune badge of the mock; deliberately untagged. */
@Composable
private fun SettingsHeaderTile(palette: ClayPalette) {
    val shape = RoundedCornerShape(ClayRadius.md)
    ClayShadowBox(shadow = ClayShadows.button, shape = shape, modifier = Modifier.size(80.dp)) {
        Box(Modifier.matchParentSize().clip(shape).background(palette.primaryContainer))
        Text("🎨", color = palette.primary, fontSize = 34.sp, modifier = Modifier.align(Alignment.Center))
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = 3.dp, y = (-3).dp)
                .size(24.dp)
                .clip(CircleShape)
                .background(palette.secondary),
            contentAlignment = Alignment.Center,
        ) {
            Text("⚙", color = palette.onSecondary, fontSize = 12.sp)
        }
    }
}

/** Quiet, fully-rounded header action (mock: `bg-surface-secondary` + clay-floating). Untagged. */
@Composable
private fun SettingsQuietPill(label: String, glyph: String, palette: ClayPalette, onClick: (() -> Unit)?) {
    if (onClick == null) return
    val shape = RoundedCornerShape(percent = 50)
    Row(
        modifier = Modifier
            .heightIn(min = 44.dp)
            .clip(shape)
            .background(palette.surfaceSecondary)
            .border(1.dp, palette.outlineVariant.copy(alpha = 0.6f), shape)
            .clayFocusableClickable(palette, shape = shape, onClick = onClick)
            .padding(horizontal = ClaySpacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ClaySpacing.xs),
    ) {
        Text(glyph, color = palette.primary, fontSize = 16.sp)
        Text(label, color = palette.primary, style = LocalClayTypography.current.labelLg, maxLines = 1, softWrap = false)
    }
}

/**
 * Top clay tab strip (mock's horizontal navigation) plus the appearance panel and — on wide
 * surfaces — its live-preview column. Each tab keeps `settings_category_<id>`.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SettingsShell(
    palette: ClayPalette,
    selected: SettingsCategory,
    onSelect: (SettingsCategory) -> Unit,
    onExportBackup: (() -> Unit)?,
    preview: (@Composable ColumnScope.() -> Unit)?,
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
        SettingsCategoryTabs(selected, palette, onSelect)
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
            if (preview != null && maxWidth >= SettingsTwoColumnBreakpoint) {
                Row(
                    Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(ClaySpacing.lg),
                ) {
                    panel(Modifier.weight(SettingsPanelWeight).fillMaxHeight())
                    Column(
                        modifier = Modifier
                            .weight(SettingsPreviewWeight)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(ClaySpacing.md),
                        content = preview,
                    )
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(ClaySpacing.md),
                ) {
                    key(selected) { content() }
                    preview?.invoke(this)
                }
            }
        }
    }
}

/** Recessed strip with the four category tabs; stretched at wide widths, scrollable when narrow. */
@Composable
private fun SettingsCategoryTabs(
    selected: SettingsCategory,
    palette: ClayPalette,
    onSelect: (SettingsCategory) -> Unit,
) {
    val shape = RoundedCornerShape(ClayRadius.lg)
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val stretched = maxWidth >= SettingsTabsWrapBreakpoint
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(palette.surfaceSecondary.copy(alpha = 0.7f))
                .clayPressed(shape)
                // `clayPressed` paints an inset band 4..12dp inside the edge; padding children by
                // `sm` keeps the tabs (and the active fill) clear of it.
                .padding(ClaySpacing.sm),
        ) {
            Row(
                modifier = Modifier
                    .then(if (stretched) Modifier.fillMaxWidth() else Modifier.horizontalScroll(rememberScrollState())),
                horizontalArrangement = Arrangement.spacedBy(ClaySpacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SettingsCategory.entries.forEach { entry ->
                    SettingsCategoryTab(
                        entry = entry,
                        selected = entry == selected,
                        palette = palette,
                        onSelect = onSelect,
                        modifier = if (stretched) Modifier.weight(1f) else Modifier,
                    )
                }
            }
        }
    }
}

/** One clay tab: filled `primary-container` when active (mock) with dot + glyph. */
@Composable
private fun SettingsCategoryTab(
    entry: SettingsCategory,
    selected: Boolean,
    palette: ClayPalette,
    onSelect: (SettingsCategory) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(ClayRadius.md)
    Row(
        modifier = modifier
            .heightIn(min = 46.dp)
            .clip(shape)
            .background(if (selected) palette.primaryContainer else Color.Transparent)
            .clayFocusableClickable(palette, shape = shape, onClick = { onSelect(entry) })
            .testTag("settings_category_${entry.id}")
            .padding(horizontal = ClaySpacing.md, vertical = ClaySpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ClaySpacing.xs, Alignment.CenterHorizontally),
    ) {
        if (selected) Box(Modifier.size(8.dp).clip(CircleShape).background(palette.onPrimaryContainer))
        Text(entry.glyph, fontSize = 15.sp)
        Text(
            entry.label,
            color = if (selected) palette.onPrimaryContainer else palette.onSurfaceVariant,
            style = LocalClayTypography.current.labelLg,
            maxLines = 2,
            textAlign = TextAlign.Center,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Content-sized clay panel with a 40.dp icon tile in its header (`tag` is the pinned card). */
@Composable
private fun SettingsPanelCard(
    tag: String,
    title: String,
    subtitle: String,
    palette: ClayPalette,
    glyph: String,
    accent: SettingsAccent,
    content: @Composable ColumnScope.() -> Unit,
) {
    val typography = LocalClayTypography.current
    ClayContentCard(Modifier.fillMaxWidth().testTag(tag)) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(ClaySpacing.lg),
            verticalArrangement = Arrangement.spacedBy(ClaySpacing.md),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(ClaySpacing.sm),
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(ClayRadius.base))
                        .background(accent.tileContainer(palette)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(glyph, color = accent.tileContent(palette), fontSize = 18.sp)
                }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(title, color = palette.onSurface, style = typography.headlineSm)
                    Text(subtitle, color = palette.onSurfaceVariant, style = typography.bodySm)
                }
            }
            content()
        }
    }
}

/**
 * One of the four real [ReaderTheme] tones as a *tonal* clay card: the reading-tone tokens
 * (`ClayTokens.readingTone`) paint the surface, the ink and the border; the swatch circle uses the
 * real `Color(theme.swatchHex)`. Fixed height so the four always align. The card carries
 * `settings_tone_card_<THEME>` and the clickable body keeps the contract tag
 * `settings_theme_<THEME>`; the active tone shows the mock's check.
 */
@Composable
private fun SettingsToneCard(
    value: ReaderTheme,
    selected: Boolean,
    palette: ClayPalette,
    highContrast: Boolean,
    onSelect: (ReaderTheme) -> Unit,
    modifier: Modifier = Modifier,
) {
    val typography = LocalClayTypography.current
    val tone = ClayTokens.readingTone(value, highContrast)
    val shape = RoundedCornerShape(ClayRadius.md)
    ClayShadowBox(
        shadow = ClayShadows.card,
        shape = shape,
        modifier = modifier.height(SettingsToneCardHeight).testTag("settings_tone_card_${value.name}"),
    ) {
        Box(
            Modifier
                .matchParentSize()
                .clip(shape)
                .background(tone.sheet)
                .border(if (selected) 2.dp else 1.dp, if (selected) palette.primary else tone.border, shape)
                .clayFocusableClickable(palette, shape = shape, onClick = { onSelect(value) })
                .testTag("settings_theme_${value.name}")
                .padding(ClaySpacing.md),
        ) {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top,
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(Color(value.swatchHex))
                            .border(1.dp, tone.border, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(toneGlyph(value), color = tone.ink, fontSize = 13.sp)
                    }
                    if (selected) {
                        Box(
                            modifier = Modifier.size(24.dp).clip(CircleShape).background(palette.primary),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("✓", color = palette.onPrimary, fontSize = 13.sp)
                        }
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        value.stitchLabel,
                        color = tone.ink,
                        style = typography.titleMd,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        toneDescription(value),
                        color = tone.inkSoft,
                        style = typography.bodySm,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/** Sunken well with the three real UI font families (mock's `font-family-selector`). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SettingsFamilyWell(
    selected: UiFontFamilyOption,
    palette: ClayPalette,
    onSelect: (UiFontFamilyOption) -> Unit,
) {
    val typography = LocalClayTypography.current
    val shape = RoundedCornerShape(ClayRadius.md)
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(ClaySpacing.xs)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Familia Tipográfica", color = palette.onSurface, style = typography.labelLg)
            Text("Optimizada para legibilidad", color = palette.textMuted, style = typography.labelSm)
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(palette.surfaceSecondary.copy(alpha = 0.8f))
                .clayPressed(shape)
                // Keep the family pills clear of the 4..12dp inset band `clayPressed` paints.
                .padding(ClaySpacing.sm),
        ) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(ClaySpacing.xs), verticalArrangement = Arrangement.spacedBy(ClaySpacing.xs)) {
                UiFontFamilyOption.entries.forEach { value ->
                    SettingsFamilyPill(
                        label = value.label,
                        tag = "ui_font_family_${value.name}",
                        selected = value == selected,
                        palette = palette,
                        onClick = { onSelect(value) },
                    )
                }
            }
        }
    }
}

/** Family pill: bright clay chip on the active option, transparent on the resting ones. */
@Composable
private fun SettingsFamilyPill(label: String, tag: String, selected: Boolean, palette: ClayPalette, onClick: () -> Unit) {
    val typography = LocalClayTypography.current
    val shape = RoundedCornerShape(ClayRadius.base)
    Box(
        modifier = Modifier
            .heightIn(min = 44.dp)
            .clip(shape)
            .background(if (selected) palette.surface else Color.Transparent)
            .clayFocusableClickable(palette, shape = shape, onClick = onClick)
            .testTag(tag)
            .padding(horizontal = ClaySpacing.md),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (selected) palette.primary else palette.onSurfaceVariant,
            style = typography.labelLg,
            maxLines = 1,
            softWrap = false,
        )
    }
}

/** Weight row: the active stroke weight is a filled `primary` pill with the mock's check. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SettingsWeightWell(
    selected: UiFontWeightOption,
    palette: ClayPalette,
    onSelect: (UiFontWeightOption) -> Unit,
) {
    val typography = LocalClayTypography.current
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(ClaySpacing.xs)) {
        Text("Peso del Trazo", color = palette.onSurface, style = typography.labelLg)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(ClaySpacing.sm),
            verticalArrangement = Arrangement.spacedBy(ClaySpacing.xs),
        ) {
            UiFontWeightOption.entries.forEach { value ->
                val isSelected = value == selected
                val shape = RoundedCornerShape(ClayRadius.base)
                Row(
                    modifier = Modifier
                        .heightIn(min = 44.dp)
                        .clip(shape)
                        .background(if (isSelected) palette.primary else palette.surfaceSecondary)
                        .then(
                            if (isSelected) {
                                Modifier
                            } else {
                                Modifier.border(1.dp, palette.outlineVariant.copy(alpha = 0.6f), shape)
                            },
                        )
                        .clayFocusableClickable(palette, shape = shape, onClick = { onSelect(value) })
                        .testTag("ui_font_weight_${value.name}")
                        .padding(horizontal = ClaySpacing.md),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(ClaySpacing.xs),
                ) {
                    if (isSelected) Text("✓", color = palette.onPrimary, fontSize = 13.sp)
                    Text(
                        value.label,
                        color = if (isSelected) palette.onPrimary else palette.onSurfaceVariant,
                        style = typography.labelLg,
                        maxLines = 1,
                        softWrap = false,
                    )
                }
            }
        }
    }
}

/**
 * Tactile high-contrast switch (mock's 16×9 pill with an inline knob) plus the interface reset
 * button on the same row. The toggle keeps the `ui_high_contrast_toggle` tag and its real click.
 */
@Composable
private fun SettingsContrastRow(
    checked: Boolean,
    palette: ClayPalette,
    onCheckedChange: (Boolean) -> Unit,
    onResetUiAppearance: () -> Unit,
) {
    val typography = LocalClayTypography.current
    val label: @Composable () -> Unit = {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("Contraste Alto Táctil", color = palette.onSurface, style = typography.labelLg)
            Text(
                "Intensifica los bordes y el relieve clay para mejorar la legibilidad de los controles.",
                color = palette.textMuted,
                style = typography.bodySm,
            )
        }
    }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (maxWidth >= SettingsRowStackBreakpoint) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(ClaySpacing.md),
            ) {
                SettingsContrastToggle(checked, palette) { onCheckedChange(!checked) }
                Box(Modifier.weight(1f)) { label() }
                ClaySecondaryButton(
                    label = "Restablecer interfaz",
                    onClick = onResetUiAppearance,
                    tag = "reset_ui_appearance_btn",
                )
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(ClaySpacing.sm)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(ClaySpacing.md),
                ) {
                    SettingsContrastToggle(checked, palette) { onCheckedChange(!checked) }
                    label()
                }
                ClaySecondaryButton(
                    label = "Restablecer interfaz",
                    onClick = onResetUiAppearance,
                    tag = "reset_ui_appearance_btn",
                )
            }
        }
    }
}

/** Recessed tactile switch with an inline knob; keeps `ui_high_contrast_toggle`. */
@Composable
private fun SettingsContrastToggle(checked: Boolean, palette: ClayPalette, onToggle: () -> Unit) {
    val shape = RoundedCornerShape(percent = 50)
    Box(
        modifier = Modifier
            .size(64.dp, 36.dp)
            .clip(shape)
            .clayFocusableClickable(palette, shape = shape, onClick = onToggle)
            .testTag("ui_high_contrast_toggle")
            .semantics {
                role = Role.Switch
                toggleableState = if (checked) ToggleableState.On else ToggleableState.Off
                // No `contentDescription` here: the row's visible "Contraste Alto Táctil" label
                // already names this control, so adding one would announce the same string twice.
            },
        contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        // Recessed track painted behind; the knob is a sibling drawn above it so the inset bands
        // `clayPressed` paints (4..12dp inside the edge) never shave the knob's own edge.
        Box(
            modifier = Modifier
                .matchParentSize()
                .clip(shape)
                .background(if (checked) palette.primary else palette.surfaceContainerHigh)
                .clayPressed(shape),
        )
        Box(
            modifier = Modifier
                .padding(4.dp)
                .size(28.dp)
                .clip(CircleShape)
                .background(palette.surface)
                .border(1.dp, palette.outlineVariant.copy(alpha = 0.5f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text("◐", color = if (checked) palette.primary else palette.onSurfaceVariant, fontSize = 13.sp)
        }
    }
}

/**
 * Live preview of the interface typography with the real current values: the badge reports the
 * active font size and weight, the canvas is tinted with the active *reading* tone and rendered
 * with the active family/weight/size, the swatches switch tone for real and the footer shows the
 * real book typography numbers.
 */
@Composable
private fun SettingsPreviewCard(
    palette: ClayPalette,
    theme: ReaderTheme,
    currentBook: BookDocument,
    fontSizePt: Float,
    lineSpacingMultiplier: Float,
    uiAppearance: UiAppearanceSettings,
    onSelectTheme: (ReaderTheme) -> Unit,
) {
    val typography = LocalClayTypography.current
    val tone = ClayTokens.readingTone(theme, uiAppearance.highContrastText)
    val currentPage = currentBook.pages.find { it.pageNumber == currentBook.currentPage }
    SettingsPanelCard(
        tag = "settings_preview_card",
        title = "Vista Previa en Vivo",
        subtitle = "Así se ve el texto de la interfaz con los valores activos ahora mismo.",
        palette = palette,
        glyph = "👁",
        accent = SettingsAccent.SECONDARY,
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SettingsPill(
                "VISTA PREVIA EN VIVO · ${uiAppearance.fontSizeSp.toInt()} sp · ${uiAppearance.fontWeight.label}",
                palette,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                listOf(palette.error, palette.warningAmber, palette.successMint).forEach { dot ->
                    Box(Modifier.size(12.dp).clip(CircleShape).background(dot.copy(alpha = 0.7f)))
                }
            }
        }
        val canvasShape = RoundedCornerShape(ClayRadius.md)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(canvasShape)
                .background(tone.sheet)
                .border(1.dp, tone.border, canvasShape)
                .padding(ClaySpacing.md),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(ClaySpacing.sm)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        (currentPage?.chapterTitle?.takeIf { it.isNotBlank() } ?: currentBook.title).uppercase(),
                        color = tone.muted,
                        style = typography.labelSm,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Text("Pág. ${currentBook.currentPage}", color = tone.muted, style = typography.labelSm, maxLines = 1)
                }
                Text(
                    currentBook.title,
                    color = tone.ink,
                    style = typography.headlineMd.copy(
                        fontFamily = uiAppearance.fontFamily.toComposeFontFamily(),
                    ),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "El clay guarda la calidez de la página impresa: bordes suaves, sombras profundas y una tipografía que se adapta a tu ritmo de lectura.",
                    color = tone.ink,
                    style = typography.bodyMd.copy(
                        fontFamily = uiAppearance.fontFamily.toComposeFontFamily(),
                        fontWeight = uiAppearance.fontWeight.toComposeFontWeight(),
                        fontSize = uiAppearance.fontSizeSp.sp,
                        lineHeight = (uiAppearance.fontSizeSp * 1.6f).sp,
                    ),
                    textAlign = TextAlign.Justify,
                )
            }
        }
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Cambio rápido de atmósfera:", color = palette.onSurfaceVariant, style = typography.labelMd)
            ClayToneSwatches(
                selected = theme,
                onSelect = onSelectTheme,
                palette = palette,
                tagPrefix = "settings_quick_tone_",
            )
        }
        Text(
            "${currentBook.title} · Pág. ${currentBook.currentPage} de ${currentBook.totalPages} · " +
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
    Row(
        modifier = Modifier
            .clip(shape)
            .background(palette.primaryContainer)
            .border(1.dp, palette.primary.copy(alpha = 0.35f), shape)
            .padding(horizontal = ClaySpacing.sm, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(palette.primary))
        Text(
            text,
            color = palette.onPrimaryContainer,
            style = LocalClayTypography.current.labelSm,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
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
        if (maxWidth >= SettingsRowStackBreakpoint) {
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

/** Row caption in the label token of the type scale (mock's `label-lg`), muted description below. */
@Composable
private fun SettingsRowLabel(label: String, description: String?, palette: ClayPalette, modifier: Modifier = Modifier) {
    val typography = LocalClayTypography.current
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, color = palette.onSurface, style = typography.labelLg)
        if (description != null) {
            Text(description, color = palette.textMuted, style = typography.bodySm)
        }
    }
}

/**
 * Recessed stepper close to the mock: circular −/+ clay buttons around a sunken track, with the
 * value in an accent pill above. Keeps the `${tag}_dec_btn` / `${tag}_label` / `${tag}_inc_btn`
 * contract. [fraction] is the real position inside the control's own clamp (null when the clamp
 * lives in the repository and cannot be stated here).
 */
@Composable
private fun SettingsStepper(
    value: String,
    tag: String,
    palette: ClayPalette,
    accent: Color,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
    fraction: Float? = null,
) {
    val trackShape = RoundedCornerShape(percent = 50)
    Column(
        modifier = Modifier.width(SettingsStepperWidth),
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        SettingsPill(value, palette, accent, "${tag}_label")
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(ClaySpacing.xs),
        ) {
            SettingsRoundStepButton("−", "${tag}_dec_btn", palette, "Disminuir $tag", onDecrease)
            // 12dp recessed track: the whole bar sits inside the `clayPressed` inset bands, but it
            // holds only decorative fills (background + accent progress), no text or icon, so the
            // bands simply read as the sunken edge. Left as-is on purpose.
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(12.dp)
                    .clip(trackShape)
                    .background(palette.surfaceContainerHigh)
                    .clayPressed(trackShape),
            ) {
                if (fraction != null) {
                    Box(
                        Modifier
                            .fillMaxWidth(fraction.coerceIn(0f, 1f))
                            .fillMaxHeight()
                            .clip(trackShape)
                            .background(accent),
                    )
                }
            }
            SettingsRoundStepButton("+", "${tag}_inc_btn", palette, "Aumentar $tag", onIncrease)
        }
    }
}

/** Circular 48.dp clay step button of the stepper. */
@Composable
private fun SettingsRoundStepButton(glyph: String, tag: String, palette: ClayPalette, description: String, onClick: () -> Unit) {
    val shape = CircleShape
    ClayShadowBox(
        shadow = ClayShadows.floating,
        shape = shape,
        modifier = Modifier
            .size(48.dp)
            .clayFocusableClickable(palette, shape = shape, onClick = onClick)
            .testTag(tag)
            .semantics { contentDescription = description },
    ) {
        Box(
            Modifier
                .matchParentSize()
                .clip(shape)
                .background(palette.surface)
                .border(1.dp, palette.outlineVariant.copy(alpha = 0.6f), shape),
        )
        Text(glyph, color = palette.onSurface, fontSize = 20.sp, modifier = Modifier.align(Alignment.Center))
    }
}

/** Accent value pill of a stepper (`${tag}_label`); [container] is the real accent of that row. */
@Composable
private fun SettingsPill(text: String, palette: ClayPalette, container: Color, tag: String) {
    val shape = RoundedCornerShape(percent = 50)
    Box(
        modifier = Modifier
            .clip(shape)
            .background(container)
            .border(1.dp, palette.outlineVariant.copy(alpha = 0.4f), shape)
            .padding(horizontal = ClaySpacing.sm, vertical = 5.dp)
            .testTag(tag),
    ) {
        Text(
            text,
            color = palette.onSurface,
            style = LocalClayTypography.current.labelMd,
            maxLines = 1,
            softWrap = false,
        )
    }
}

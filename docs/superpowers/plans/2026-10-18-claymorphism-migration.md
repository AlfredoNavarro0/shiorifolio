# ShioriFolio — Migración de `:ui` a *Tactile Claymorphism* — Plan de implementación

**Para quien ejecuta:** este plan asume que no has visto la conversación de diseño. El spec aprobado está en `docs/superpowers/specs/2026-10-18-claymorphism-migration-design.md`; el sistema de diseño de referencia (tokens, tipografía, sombras) y las capturas/HTML de las 9 pantallas están en `C:\Users\terra\AppData\Local\Temp\opencode\stitch-claymorphic\` y en el proyecto Stitch `projects/14806878120696973328`. Lee el spec antes de empezar. Usa `test-driven-development` al implementar y `shiorifolio-code-review` antes de cada commit.

**Objetivo:** reconstruir `:ui` sobre el sistema *Tactile Claymorphism* (chrome claro + tonos de lectura), recuperar el build verde y reproducir las 4 superficies de escritorio (Biblioteca, Reader, Notebook, Ajustes), sin tocar `:core`.

**Stack:** Kotlin Multiplatform + Compose Multiplatform (`:ui` commonMain + desktopTest). Sin APIs de plataforma en commonMain. Java 17 / toolchain 25.

**Estado de partida:** `main` en `b964fc9`+ (spec `14a75ec`), build de `:ui` intencionalmente rojo (~330 referencias sin resolver: `MeridianPalette`, `ModernChromeAction`, `shioriFocusableClickable`, etc.). `:core` intacto y verde.

---

## Restricciones globales

- **`:core` no se toca.** Cualquier diff en `core/` invalida la tarea. Sin cambios de modelo, codec ni `CURRENT_PARSER_VERSION`.
- **Contratos de UI:** conservar todos los `testTag` que usan los tests en `ui/src/desktopTest` (no renombrar; añadir solo nuevos). Preservar la firma de `MeridianFolioApp(...)`, el texto visible `shiorifolio` y el contrato de `theme_toggle_button` (alterna `ReaderTheme.WHITE` ↔ `ReaderTheme.DARK`).
- **Chrome siempre claro.** `ReaderTheme` solo cambia el *tono de lectura* (hoja del Reader). No existe chrome oscuro.
- **Regla clay:** primitiva propia (ClayCard/ClayChromeAction/…) primero; `MaterialTheme`/M3 solo donde no exista equivalente (menús, sliders, diálogos).
- Todo texto de UI en español. Etiquetas nuevas: copiar literalmente del HTML de referencia (`stitch-claymorphic\*.html`), no inventar.
- Comandos de puerta (desde la raíz del repo): `.\gradlew.bat :core:desktopTest :ui:desktopTest :desktopApp:classes :androidApp:compileDebugKotlin --console=plain`.
- Commit por tarea, en `main`, **sin push**. Mensajes en inglés. Nunca commitear con la puerta en rojo.
- Rutas: paquete de tema nuevo = `ui/src/commonMain/kotlin/dev/meridian/folio/ui/theme/` (paquete `dev.meridian.folio.ui.theme`).

## Puntos de atención de revisión (los 5 fallos más probables)

1. **Fuentes que no cargan** (crash o texto invisible al runtime) → test de composición en Task 4 que renderiza `ClayTheme { Text(…, headlineLarge) }`; solo corre a partir de Task 5.
2. **Deriva de tokens** (hex/px distintos al DESIGN.md) → `ClayTokensTest` con valores exactos (Task 2).
3. **Contraste** (texto clay sobre superficies claras) → test de ratio WCAG para 6 pares en `ClayTokensTest` (Task 2).
4. **Regresión de contratos** (testTags/textos perdidos en la sustitución) → suite `:ui:desktopTest` completa (13 tests) en Task 5 y `ClayShellTest` nuevo.
5. **Atajos rotos tras perder los helpers** (`canonicalKeyName`/`matches`/`toKeyBindingSpecOrNull`) → `ClayHelpersTest` + `ReaderKeyboardTest` existente (Tasks 3 y 5).

---

## Tabla de sustitución (usada por Task 5)

| Antiguo (borrado en `b964fc9`) | Nuevo |
|---|---|
| `MeridianPalette` (tipo) | `ClayPalette` |
| `MeridianDesign.paletteFor(theme, hc)` | `ClayTokens.chrome(hc)` (chrome claro; el tono se aplica en Task 7) |
| `palette.paper` | `palette.canvas` |
| `palette.pageSurface` | `palette.surfaceContainerLowest` |
| `palette.elevatedSurface` | `palette.surfaceSecondary` |
| `palette.ink` | `palette.onSurface` |
| `palette.inkSoft` | `palette.onSurfaceVariant` |
| `palette.muted` | `palette.textMuted` |
| `palette.border` | `palette.outlineVariant` |
| `palette.pillBg` | `palette.surfaceContainerHigh` |
| `palette.accentGreen` | `palette.primary` |
| `palette.onAccent` | `palette.onPrimary` |
| `palette.disabledInk` | `palette.disabledContent` |
| `palette.disabledBg` | `palette.disabledContainer` |
| `palette.warningAmber` / `onWarningAmber` / `focusRing` / `spineShadow` | sin cambio de nombre |
| `palette.isDark` | `palette.isDarkReading` (la hoja lo reemplaza en Task 7) |
| `shioriOutlinedTextFieldColors(palette)` | `clayTextFieldColors(palette)` |
| `ModernChromeAction` | `ClayChromeAction` (misma firma, `palette` incluido) |
| `ModernAppNavigation(...)` | `ClayTopNav(...)` |
| `ModernReaderToolbar(...)` | `ClayReaderToolbar(...)` |
| `MeridianCompassMark(ink, paper, size)` | `ClayLogoMark(size = size)` |
| `SwallowtailBookmarkRibbon(...)` | `ClayBookmarkRibbon(...)` |
| `Modifier.shioriFocusableClickable(palette, shape, enabled, onClick)` | `Modifier.clayFocusableClickable(...)` (misma firma) |
| `LocalModernUiAppearance` | `LocalClayUiAppearance` |
| `MeridianDesign` (test) | `ClayTokens` |
| `MeridianDotNavBar`, `MeridianWordmarkTitle`, `MeridianViaductFooterBanner` | sin uso: no se recrean |

---

### Task 1: Fuentes empaquetadas y plumbing de recursos

**Files:** Modify `ui/build.gradle.kts`; Create `ui/src/commonMain/composeResources/font/*.ttf` (6), `ui/src/commonMain/composeResources/files/licenses/*.txt` (2).

**Interfaces (produce):** recursos `Res.font.nunito_sans_bold`, `nunito_sans_extrabold`, `nunito_sans_black`, `dm_sans_regular`, `dm_sans_medium`, `dm_sans_bold`.

**Steps:**
1. Añadir `implementation(compose.components.resources)` a `commonMain.dependencies` en `ui/build.gradle.kts`.
2. Descargar TTFs estáticos con el truco de la CSS API (UA antiguo devuelve TTF) o el zip oficial si falla:
   `Invoke-WebRequest "https://fonts.googleapis.com/css2?family=Nunito+Sans:wght@700;800;900" -UserAgent "Mozilla/4.0 (compatible; MSIE 6.0; Windows NT 5.1)"` → extraer las URLs `format('truetype')` y descargar cada una como `nunito_sans_bold.ttf` (700), `nunito_sans_extrabold.ttf` (800), `nunito_sans_black.ttf` (900). Repetir con `family=DM+Sans:wght@400;500;700` → `dm_sans_regular/medium/bold.ttf`.
   Nombres en minúsculas con `_` (requisito de Compose resources).
3. Descargar las licencias OFL: `https://raw.githubusercontent.com/google/fonts/main/ofl/nunitosans/OFL.txt` → `files/licenses/nunito_sans_ofl.txt`, y `.../ofl/dmsans/OFL.txt` → `files/licenses/dm_sans_ofl.txt`.
4. Validar cada TTF: tamaño > 50 KB y primeros 4 bytes `00 01 00 00` (o `OTTO`).
5. Verificar: `.\gradlew.bat :ui:generateComposeResClass --console=plain` → BUILD SUCCESSFUL, y que el `Res.kt` generado contiene `nunito_sans_bold` y `dm_sans_bold`.
6. Commit: `chore(ui): bundle Nunito Sans and DM Sans resources`.

### Task 2: `theme/ClayTokens.kt`

**Files:** Create `theme/ClayTokens.kt`, `ui/src/desktopTest/kotlin/dev/meridian/folio/ui/theme/ClayTokensTest.kt`. (Los tests quedan escritos pero no ejecutables hasta Task 5; verificación de esta tarea = sin errores nuevos en `theme/`.)

**Interfaces (produce):** `ClayPalette` (data class inmutable), `ReadingTone`, `ClayTokens.chrome(highContrast: Boolean = false)`, `ClayTokens.readingTone(theme: ReaderTheme, highContrast: Boolean = false)`, `ClaySpacing`, `ClayRadius`, `ClayTypeScale`, `ClayShadowLayer`, `ClayShadowStack`, `ClayShadows`.

**Steps:**
1. `ClayPalette` con los tokens exactos del DESIGN.md (valores por defecto en el constructor, para que `ClayPalette()` sea el chrome): `canvas #F4F1FA`, `surface #FEF7FF`, `surfaceContainerLowest #FFFFFF`, `surfaceContainerLow #F9F1FF`, `surfaceContainer #F3EBFA`, `surfaceContainerHigh #EDE5F4`, `surfaceContainerHighest #E7E0EE`, `surfaceSecondary #EFEBF5`, `cardSurface = Color.White.copy(alpha = 0.75f)`, `onSurface #332F3A`, `onSurfaceVariant #4A4455`, `textMuted #635F69`, `outline #7B7487`, `outlineVariant #CCC3D8`, `primary #7C3AED`, `primaryDeep #630ED4`, `onPrimary #FFFFFF`, `primaryContainer #EDE0FF`, `onPrimaryContainer #25005A`, `secondary #DB2777`, `onSecondary #FFFFFF`, `secondaryContainer #FFD9E2`, `onSecondaryContainer #3F001C`, `tertiary #0EA5E9`, `onTertiary #FFFFFF`, `successMint #10B981`, `onSuccessMint #06341F`, `warningAmber #F59E0B`, `onWarningAmber #3D2800`, `error #BA1A1A`, `onError #FFFFFF`, `errorContainer #FFDAD6`, `onErrorContainer #93000A`, `disabledContent #9A93A6`, `disabledContainer #E9E3F0`, `focusRing #630ED4`, `spineShadow #332F3A`, `isDarkReading = false`. Métodos: `highlightColor(tone: HighlightTone)` (usa `hexLight`/`hexDark` según `isDarkReading`) y `toMaterialColorScheme(): ColorScheme` (mapeo claro: primary/primaryContainer/secondary/tertiary/error/background=canvas/surface/onSurface/surfaceVariant=surfaceSecondary/outline…).
2. `ClayTokens.chrome(highContrast)`: devuelve `ClayPalette()`; con high contrast sustituye `onSurface → #0A0A08`, `onSurfaceVariant → #1F241F`, `textMuted → #3E443E`, `outlineVariant → #B5A592`.
3. `ReadingTone(isDark, sheet, ink, inkSoft, muted, border)` + `readingTone(theme, highContrast)`: WHITE `#FCF9F3/#2B2520/#4A4238/#6E655A/#E4DCD0`; SEPIA `#F5EDDB/#261D14/#47392B/#6E5C49/#DECBB3`; SAGE `#EEF3EE/#1B2B21/#36433A/#5A695D/#C8D6C9`; DARK `#221E2A/#F4EFFC/#D9D2E6/#B0A8BF/#4A4455` (`isDark = true`). High contrast oscurece/aumenta el contraste de `ink`/`inkSoft`/`muted`/`border` en tonos claros y lo aclara en DARK.
4. `ClaySpacing` (xs 8, sm 12, md 16, lg 24, xl 36, gutter 16, gutterDesktop 32, marginDesktop 48 — en dp), `ClayRadius` (sm 8, base 16, md 24, lg 32, xl 48 — dp), `ClayTypeScale` (constantes `fontSize/lineHeight/weight/tracking` de las 12 escalas del DESIGN.md, exactas).
5. `ClayShadowLayer(dx, dy, blur, color)` + `ClayShadowStack(outer, inner)` + `ClayShadows.card`, `.button`, `.pressed`, `.floating` con los valores literales del DESIGN.md (p. ej. card: `(16,16,32,#38A096B4)`, `(-10,-10,24,#F2FFFFFF)`, inner `(6,6,12,#0A8B5CF6)`, inner `(-6,-6,12,#FFFFFFFF)`).
6. Tests `ClayTokensTest` (kotlin.test): hex exactos de 8 tokens clave; spacing/radii; capas de sombra (dx/dy/blur/color); tonos (DARK `ink.luminance > 0.7f`, WHITE sheet); high contrast; ratios de contraste WCAG ≥ 4.5 para `onSurface/canvas`, `onSurfaceVariant/surfaceContainerLow`, `textMuted/canvas`, `onPrimary/primary`, `onWarningAmber/warningAmber`, `onSuccessMint/successMint`.
7. Verificar con `.\gradlew.bat :ui:compileKotlinDesktop --console=plain 2>&1` que **ninguna** línea de error menciona `theme/ClayTokens.kt` (el resto del módulo sigue rojo). Commit: `feat(ui): add clay design tokens from the Stitch design system`.

### Task 3: `theme/ClayHelpers.kt` + `theme/ClayModifiers.kt`

**Files:** Create `theme/ClayHelpers.kt`, `theme/ClayModifiers.kt`, `theme/ClayHelpersTest.kt`.

**Interfaces (produce):** `@Composable UiFontFamilyOption.toComposeFontFamily()` (SANS_SERIF → familia DM Sans del tema; SERIF/MONOSPACE → sistema), `UiFontWeightOption.toComposeFontWeight()`, `BookFontFamilyOption.toComposeFontFamily()` (sistema, igual que `f0edce9`), `BookTextAlignment.toComposeTextAlign()`, `canonicalKeyName(Key)`, `KeyEvent.matches(KeyBindingSpec)`, `KeyEvent.toKeyBindingSpecOrNull()`, `@Composable Modifier.clayFocusableClickable(palette, shape, enabled, onClick)`, `@Composable clayTextFieldColors(palette)`, `ClayShadowBox`, `Modifier.clayPressed(shape)`, `Modifier.clayFrosted(shape)`.

**Steps:**
1. Recrear los helpers de teclado **verbatim** desde `git show f0edce9:ui/src/commonMain/kotlin/dev/meridian/folio/ui/MeridianTheme.kt` (líneas 737-815): `canonicalKeyName`, `KeyEvent.matches`, `KeyEvent.toKeyBindingSpecOrNull`.
2. `clayFocusableClickable`: igual que el antiguo `shioriFocusableClickable` (anillo de foco 2.dp con `palette.focusRing`, `onFocusChanged` + `clickable`), firma idéntica con `ClayPalette`.
3. `clayTextFieldColors(palette)`: como el antiguo pero con la paleta clay: contenedor `surfaceSecondary` (pozo hundido), texto `onSurface`, cursor `primary`, borde enfocado `primary`, no enfocado `outlineVariant`, placeholder `textMuted`.
4. `ClayModifiers.kt`: `ClayShadowBox(shadow: ClayShadowStack, shape: Shape, modifier, content)` implementado con cajas apiladas (`matchParentSize`, `offset`, `Modifier.blur` para difuminar; en Android <12 `blur` es no-op y queda el fallback translúcido) + capa base clipeada con sombras internas vía `drawWithContent`; `Modifier.clayPressed(shape)` (inset oscuro arriba-izquierda + blanco abajo-derecha); `Modifier.clayFrosted(shape)` (fondo `cardSurface` + blur donde exista).
5. Tests `ClayHelpersTest`: roundtrip `toKeyBindingSpecOrNull`/`matches` (PageDown + Ctrl/Shift/Alt; tecla desconocida → null; `canonicalKeyName` para 10 teclas); `toComposeFontWeight` para los 3 valores; `toComposeTextAlign` para los 3 valores; `BookFontFamilyOption` → familia no nula; `clayTextFieldColors` en composición devuelve borde enfocado == `palette.primary`.
6. Verificar igual que Task 2 (sin errores en `theme/`). Commit: `feat(ui): restore keyboard/typography helpers and clay modifiers`.

### Task 4: `theme/ClayTheme.kt` + `theme/ClayComponents.kt`

**Files:** Create `theme/ClayTheme.kt`, `theme/ClayComponents.kt`, `ClayThemeTest.kt`, `ClayComponentsTest.kt`.

**Interfaces (produce):** `LocalClayPalette`, `LocalClayTypography`, `LocalClayUiAppearance`, `ClayTypographyData`, `@Composable ClayTheme(palette, uiAppearance, content)`, `ClayCard`, `ClayPrimaryButton`, `ClaySecondaryButton`, `ClayChromeAction`, `ClayChip`, `ClaySegmentedTabs`, `ClayProgressBar`, `ClayToneSwatches`.

**Steps:**
1. `ClayTypographyData` (TextStyles de las 12 escalas + `displayFamily` Nunito Sans + `bodyFamily` DM Sans) construido en `@Composable rememberClayTypography()` con `Font(Res.font.nunito_sans_black, FontWeight.Black)` etc.
2. `ClayTheme`: provee los 3 locals + `MaterialTheme(colorScheme = palette.toMaterialColorScheme(), typography = Typography(defaultFontFamily = typography.bodyFamily))`.
3. Componentes (diseño del spec, valores exactos): `ClayCard` (ClayShadowBox + `clayFrosted` + radio 28dp), `ClayPrimaryButton` (gradiente `primary→primaryDeep`, texto blanco, sombra `button`, radio 20dp, alto mín. 48dp), `ClaySecondaryButton` (`surfaceSecondary`, radio 20dp), `ClayChromeAction(label, palette, tag, selected, enabled, description, onClick)` (pill; seleccionada `primary`/`onPrimary`; usa `LocalClayUiAppearance` para fuente), `ClayChip` (pill seleccionable), `ClaySegmentedTabs(items, selectedIndex, onSelect, tagPrefix)` (contenedor `surfaceSecondary`, indicador `primary`), `ClayProgressBar` (pista `surfaceContainerHigh`, relleno gradiente), `ClayToneSwatches(selected, onSelect, palette, tagPrefix)` (puntos con anillo `primary` en el seleccionado).
4. Tests: `ClayThemeTest` (render con `ClayTheme` de `headlineLarge` y de M3: `MaterialTheme.colorScheme.primary == palette.primary`); `ClayComponentsTest` (click de botón/chip/tabs dispara callback; `enabled=false` no dispara; tags presentes; swatches emiten `ReaderTheme`).
5. Verificar igual que Task 2. Commit: `feat(ui): add clay theme provider and base component kit`.

### Task 5: Recuperar el build — shell clay + sustitución mecánica

**Files:** Create `ui/src/commonMain/.../ui/ShellChrome.kt`; Modify `MeridianFolioApp.kt`, `LibraryControls.kt`, `LibraryWorkspace.kt`, `LibraryBookCard.kt`, `SettingsWorkspace.kt`, `EditingWorkspace.kt`, `ReaderViewModes.kt` (+ cualquier archivo con símbolos de la tabla); Modify `ui/src/desktopTest/.../MeridianFolioUiTest.kt` (línea 126) ; Create `ClayShellTest.kt`.

**Interfaces (produce):** `ClayLogoMark(size, modifier)`, `ClayTopNav(activeSection, onSelectSection, theme, onToggleTheme, modifier)`, `ClayBottomNav(activeSection, onSelectSection, modifier)`, `ClayFooter(bookCount, modifier)`, `ClayBookmarkRibbon(isActive, tint, width, height, onClick, modifier)`, `ClayReaderToolbar(… misma firma que `ModernReaderToolbar` sin el tipo viejo …)`.

**Steps:**
1. `ShellChrome.kt`: `ClayTopNav` = barra superior flotante (ClayCard pill) con `ClayLogoMark` + texto `shiorifolio` + tabs (`section_tab_<NAME>`, etiqueta `section.stitchTabTitle`) + `ClayChromeAction` de tema (`theme_toggle_button`, texto `☀ Claro`/`☾ Oscuro`) + toggle de contraste alto (`high_contrast_toggle`) si el diseño lo pide; `ClayFooter` = barra fina con `ShioriFolio · Lector y editor de PDF & EPUB` a la izquierda y `"$bookCount títulos en tu biblioteca"` a la derecha; `ClayBottomNav` para compacto con los mismos tags; `ClayBookmarkRibbon` (cinta clay con radio y color `tint`); `ClayLogoMark` (tile redondeado violeta con libro/cinta dibujados en Canvas).
2. `MeridianFolioApp.kt`: `val palette = ClayTokens.chrome(uiAppearance.highContrastText)`; eliminar la variable local `materialColorScheme`; el `CompositionLocalProvider(LocalDensity, LocalReaderPagination, LocalTextFieldFocusTracker)` existente se **mantiene** y envuelve a `ClayTheme(palette = palette, uiAppearance = uiAppearance)` (quitando solo `LocalModernUiAppearance`, que pasa a proveerla `ClayTheme`); `ModernAppNavigation(palette, activeSection, theme, onSelectSection, onToggleTheme)` → `ClayTopNav(activeSection, onSelectSection, theme, onToggleTheme)`; añadir `ClayFooter(state.documents.size)` al final del shell de escritorio; en la rama compacta (`isMobileKindleView`, ~línea 528/630) usar `ClayBottomNav`; `ModernReaderToolbar(...)` → `ClayReaderToolbar(...)`; `MeridianCompassMark(...)` → `ClayLogoMark(size = ...)` (2 sitios); `SwallowtailBookmarkRibbon(...)` → `ClayBookmarkRibbon(...)` (1 sitio); `shioriFocusableClickable` → `clayFocusableClickable` (18 sitios).
3. Aplicar la **tabla de sustitución** en todos los archivos listados (Tipo + 17 renames de campos `palette.*` + `shioriOutlinedTextFieldColors`→`clayTextFieldColors` + `ModernChromeAction`→`ClayChromeAction` en los 37 sitios). Añadir los imports de `dev.meridian.folio.ui.theme.*` que falten.
4. Tests: actualizar `MeridianFolioUiTest.kt` línea 126 a `assertTrue(ClayTokens.readingTone(ReaderTheme.DARK).ink.luminance > 0.7f)` (+ import). Añadir `ClayShellTest`: `shiorifolio` visible, 4 `section_tab_*`, `theme_toggle_button` alterna WHITE↔DARK, footer visible, `ClayBottomNav` aparece con `mobile_preview_toggle` pulsado.
5. Puerta completa: `.\gradlew.bat :core:desktopTest :ui:desktopTest :desktopApp:classes :androidApp:compileDebugKotlin --console=plain` → todo verde (los 13+ tests de `:ui` incluyen los `ClayTokensTest`/`ClayHelpersTest`/etc. de Tasks 2-4; corregir lo que falle).
6. Code review (`shiorifolio-code-review`), commit: `feat(ui): restore green build with claymorphic shell and tokens`. **Este es el hito "build verde otra vez".**

### Task 6: Biblioteca de escritorio

**Files:** Rewrite `LibraryControls.kt`; Modify `LibraryWorkspace.kt`; Rewrite `LibraryBookCard.kt`; Modify `LibraryWorkspaceTest.kt`.

**Interfaces (consume):** ClayCard, ClayPrimaryButton, ClayChip, ClaySegmentedTabs, clayTextFieldColors, ClayTopNav ya existentes. (Produce nuevos tags: `library_header`, `library_import_btn`, `library_search_field`, `library_filter_chip_<X>`, `library_sort_pill`, `library_filter_panel`.)

**Steps:**
1. Cabecera: `Tu biblioteca` en `headlineLarge` (Nunito) + cápsulas `"$resultCount / ${state.documents.size} volúmenes"` + `ClayPrimaryButton("Importar libro", tag = "library_import_btn")` + campo de búsqueda clay (`library_search_field`, placeholder del HTML de referencia).
2. Fila de chips: `Todos`, `Leyendo`, `PDF`, `EPUB`, `Filtros`, `Ordenar` (`library_filter_chip_*`); al activar `Filtros`/`Ordenar` abrir panel clay (`library_filter_panel`) con estado/orden/colecciones/formato (lógica existente de `LibraryFilters`).
3. Rejilla de tarjetas (`ClayCard`): portada (existente `LibraryCover`), badges (`Nuevo`, `%`), título/autor, `ClayProgressBar` + páginas; conservar `library_missing_<id>`, selección (`library_selection_bar`) y menú de acciones.
4. Conservar **todos** los tags previos (`library_workspace`, `library_toolbar`, `library_tools`, `view_mode_<MODE>`, `library_message`, `library_import_errors`, …).
5. Tests: extender `LibraryWorkspaceTest` (cabecera `Tu biblioteca`, `library_import_btn`, chips de filtro y panel, 4 variantes siguen funcionando, búsqueda por autor intacta).
6. Puerta verde + review + commit: `feat(ui): rebuild library workspace on claymorphism (desktop)`.

### Task 7: Reader de escritorio + extracción

**Files:** Create `ui/src/commonMain/.../ui/ReaderWorkspace.kt`; Modify `MeridianFolioApp.kt` (mover `StitchDesktopReaderSpreadWorkspace` + leaves + deck), `ReaderViewModes.kt`, `ReaderPagination.kt` si aplica; Create/extend tests (`ReaderToneTest.kt` o añadidos a `MeridianFolioUiTest`).

**Interfaces (consume):** `ClayTokens.readingTone(...)` de Task 2; `ClayToneSwatches`; `ClayBookmarkRibbon`. (Produce tags nuevos: `reader_tone_<THEME>`, `reader_page_indicator`, `reader_progress_slider`, `reader_font_step_down/up`; conserva `view_mode_*`, `next_page_button`, `prev_page_button`, `nav_history_*`, `zoom_fit_control_strip`, `fit_mode_*`, `zoom_*`, `dock_pdf_reflow_toggle`, `vertical_paginated_canvas`, `vertical_continuous_canvas`, `adaptive_reflow_canvas`, `reflow_fallback_original_btn`, `page_part_*`, `top_typography_popover_btn`, `open_reminder_btn`, `open_mark_phrase_btn`, `reader_search_btn`, `open_page_editor_btn`, `open_chapter_manager_btn`, `open_metadata_btn`, `reader_undo_btn`, `reader_redo_btn`, `zen_mode_toggle_btn`, `fullscreen_toggle_btn`, `mobile_preview_toggle`, `reader_open_file`, `reader_tools_menu`, `reader_notebook`, `reader_pdf_color_<MODE>`, `prediction_badge`, `page_reminder_banner`, `exit_zen_mode_btn`.)

**Steps:**
1. Extraer el workspace del pliego y el deck del Reader desde `MeridianFolioApp.kt` a `ReaderWorkspace.kt` sin cambios de comportamiento (conservar las firmas de las funciones movidas).
2. Reconstruir según la captura `01-reader-desktop.png`/HTML: tira superior dentro del libro (título, capítulo, folio), pliego centrado con flechas laterales, deck inferior: indicador de página + slider, badges, control A-/A+, **`ClayToneSwatches`** (`reader_tone_*`), fila de pills `Doble página / Simple / Paginado / Continuo / Reflujo` que mapean a `PageViewMode` con los tags `view_mode_*` existentes.
3. Aplicar el tono a la hoja: `val tone = ClayTokens.readingTone(state.theme, highContrast)`; sustituir los 6 usos de `palette.isDarkReading`/`highlightColor` de la hoja por `tone` (fondo, tinta, highlights). El chrome (deck, barra) permanece clay claro.
4. Conservar `ZoomAndFitControlStrip`, historial, TOC, frase rápida, recordatorios, telemetría y atajos tal cual.
5. Tests: nuevo `ReaderToneTest` (click en `reader_tone_SAGE` → `repo.state.value.theme == SAGE`; el deck sigue visible y `ClayTokens.chrome()` no cambia); `MeridianFolioUiTest` (modos de vista, historial, zen/fullscreen, zoom, reflow) y `ReaderPaginationTest`/`ReaderKeyboardTest` siguen verdes.
6. Puerta verde + review + commit: `feat(ui): rebuild reader workspace on claymorphism (desktop)`.

### Task 8: Notebook de escritorio + extracción

**Files:** Create `NotebookWorkspace.kt` (extraer `SavedPhrasesVaultView` de `MeridianFolioApp.kt`); Modify `MeridianFolioApp.kt`; Create `NotebookWorkspaceTest.kt`.

**Interfaces (produce tags nuevos):** `notebook_workspace`, `notebook_new_entry_card`, `notebook_save_btn`, `notebook_entry_<id>`, `notebook_tag_toolbar`, `notebook_export_btn`, `notebook_orphan_badge_<id>`, `notebook_reattach_btn_<id>`, `notebook_go_reader_btn`. Conserva los tags del flujo huérfano que usa `EditingUiTest` (líneas 114-158) y los del guardado de frase de `MeridianFolioApp` (`open_mark_phrase_btn`, etc.).

**Steps:**
1. Extracción mecánica del composable existente a `NotebookWorkspace.kt` (misma lógica: `saveWordOrPhrase`, `reattachPhrase`, `AnchorStatus.ORPHANED`).
2. Layout de dos columnas según `03-notebook-desktop.png`: izquierda *Nueva entrada* (contexto, palabra/frase, nota, ejemplo, chips de tono con `ClayChip` y los nombres de `HighlightTone`, `ClayPrimaryButton("Guardar Palabra / Frase", "notebook_save_btn")`, aviso de respaldo); derecha toolbar de tag (`sincronizar`/`exportar`), tarjetas `ClayCard` con cita en burbuja, nota de anclaje, acciones (eliminar, añadir etiqueta), insignia huérfana + `Reanclar`, y tarjeta vacía `¿Leyendo en el Reader?` con `Ir al Reader`.
3. Tests: `NotebookWorkspaceTest` (guardar entrada nueva la muestra en la lista; flujo huérfano sigue funcionando con los tags de `EditingUiTest`).
4. Puerta verde + review + commit: `feat(ui): rebuild notebook workspace on claymorphism (desktop)`.

### Task 9: Ajustes de escritorio

**Files:** Rewrite `SettingsWorkspace.kt`; Modify `MeridianFolioUiTest.kt` (añadir aserciones de pestañas si hace falta).

**Interfaces (produce tags nuevos):** `settings_tab_APARIENCIA`, `settings_tab_LECTURA`, `settings_tab_ACCESOS`, `settings_tab_DATOS`, `settings_preview_card`. Conserva todos los tags existentes (`settings_workspace_view`, `settings_category_*`, `settings_ui_appearance_card`, `settings_book_typography_card`, `settings_theme_<THEME>`, `ui_font_family_*`, `ui_font_weight_*`, `ui_scale_*`, `ui_high_contrast_toggle`, `reset_all_appearance_btn`, `book_font_family_*`, `book_text_align_*`, `settings_pdf_*`, `pdf_original_typography_notice`, `shortcut_badge_*`, `reset_shortcuts_btn`, `settings_data_*`).

**Steps:**
1. Pestañas segmentadas (`ClaySegmentedTabs`) con las 4 categorías; el contenido existente se reorganiza bajo ellas (sin cambiar handlers ni estado).
2. Paneles en `ClayCard`: Tema del Lector (4 tarjetas de tono con swatch; tarjeta "Clay Pastel" informativa del diseño fijo), Interfaz y Tipografía (familia/peso/tamaño/interlineado/contraste alto/restablecer), vista previa en vivo (`settings_preview_card`) con el texto de muestra del HTML de referencia, y Datos y Almacenamiento (respaldo/exportación/importación existentes).
3. Tests: `MeridianFolioUiTest` existente verde (categorías, atajos, apariencia, tipografía) + aserciones de las 4 pestañas nuevas.
4. Puerta verde + review + commit: `feat(ui): rebuild settings workspace on claymorphism (desktop)`.

### Task 10: Flujos secundarios (solo restyle)

**Files:** Modify `EditingWorkspace.kt`, `MeridianFolioApp.kt` (popovers, snackbar, estados vacío/carga), `ReaderViewModes.kt` (marcadores/menús de la hoja).

**Steps:**
1. Sustituir contenedores `Surface`/radios viejos por `ClayCard`/`ClayShadowBox` y botones por `ClayPrimaryButton`/`ClaySecondaryButton`/`ClayChromeAction` en: buscar/reemplazar, editor de página, gestor de capítulos, metadatos, reanclaje, editor de recordatorio, respaldo y confirmaciones.
2. Snackbar de estado y popovers de tipografía: contenedor clay (frosted + pill), texto con `ClayTypographyData`.
3. Estados vacío/carga de cada superficie con `ClayCard` y textos muted del sistema.
4. Puerta verde + review; `EditingUiTest` completa debe seguir verde. Commit: `feat(ui): apply claymorphism to secondary flows`.

### Task 11: Pasada visual + reglas y documentación

**Files:** Create `.agents/skills/shiorifolio-design/SKILL.md`; Modify `.agents/skills/shiorifolio-code-review/SKILL.md`, `.agents/skills/shiorifolio-architecture/SKILL.md`, `README.md`, `implementation_plan.md`.

**Steps:**
1. Pasada visual: `.\gradlew.bat :desktopApp:run`, capturar ventana a ancho amplio (>1400dp) y estrecho (<700dp) para las 4 superficies; comparar contra `stitch-claymorphic\*.png` (Biblioteca↔`02`, Reader↔`01`, Notebook↔`03`, Ajustes↔`04`); corregir desviaciones de tokens/estructura detectadas.
2. Crear el skill `shiorifolio-design` con las reglas del nuevo sistema: tokens exactos, regla "primitiva clay primero", chrome siempre claro, tonos solo en la hoja, fuentes Nunito/DM Sans, sombras y radios, y la fuente de verdad (proyecto Stitch + DESIGN.md).
3. Actualizar `shiorifolio-code-review` (bloqueantes: diff de `:core` vacío, primitiva clay primero, chrome claro, tokens exactos, testTags) y `shiorifolio-architecture` (mapa de archivos con `theme/`, `ShellChrome.kt`, `ReaderWorkspace.kt`, `NotebookWorkspace.kt`).
4. Actualizar `README.md` e `implementation_plan.md` para reflejar el diseño claymorphic (retirar menciones visuales del diseño antiguo).
5. Commit: `docs: document claymorphism rules and refresh UI references`.

---

## Después del plan

- Pasada móvil (5 pantallas mobile) queda fuera: es un esfuerzo separado tras este plan.
- Actualizar wiki (nota de sesión, arquitectura, índice) al cerrar la sesión.
- No hacer push hasta que el usuario lo pida.

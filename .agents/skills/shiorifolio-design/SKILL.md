---
name: shiorifolio-design
description: Sistema visual Tactile Claymorphism de ShioriFolio — tokens, primitivas clay, regla chrome-claro/tono-solo-en-la-hoja, fuentes y verificación visual. Usar al crear o modificar cualquier componente o superficie de :ui.
---

# ShioriFolio — Diseño (Tactile Claymorphism)

Fuente de verdad: proyecto Stitch **«Claymorphic App Redesign»** (`projects/14806878120696973328`, design system `assets/ac83ed6dddb54c399f1f3020fb17c8d4`) y su DESIGN.md. El sistema anterior (*Serene Vintage Reader*) fue retirado (2026-10-18) y no debe reaparecer. Referencias locales: `stitch-claymorphic/0X-*.html|png` (01 Reader, 02 Biblioteca, 03 Notebook, 04 Ajustes de escritorio; las 5 móviles están pendientes de migrar).

## Regla cero: primitiva clay primero

- Antes de Material 3, usar el kit de `ui/src/commonMain/kotlin/dev/meridian/folio/ui/theme/`:
  - `ClayComponents.kt`: `ClayCard`, `ClayPrimaryButton`, `ClaySecondaryButton`, `ClayChromeAction`, `ClayChip`, `ClaySegmentedTabs`, `ClayProgressBar`, `ClayToneSwatches`.
  - `ClayModifiers.kt`: `ClayShadowBox`, `Modifier.clayPressed(shape)`, `clayFrosted`, `clayInsetOverlay`.
  - `ClayTheme.kt`: `ClayTheme`, `LocalClayPalette`, `LocalClayTypography`, `LocalClayUiAppearance`, `ClayTypographyData`, `rememberClayTypography`.
- `ClayContentCard` (tarjeta content-sized) vive en `LibraryControls.kt`.
- **`ClayCard`/`ClayShadowBox` NO se dimensionan solos** (todas sus capas son `matchParentSize`): el llamante fija tamaño o los envuelve en un `Box` dimensionado por su columna. Un nodo 0×0 suele ser este fallo.
- **`clayFrosted`/`clayPressed` pintan capas propias**: el contenido debe ir en una capa hermana POR ENCIMA (contrato documentado en el kit). `clayPressed` pinta bandas internas a 4..12 dp del borde — el contenido pegado al borde (texto/knob) se recorta: deja ≥ `ClaySpacing.sm` (12 dp) de padding o estructura el contenido como capa hermana.
- M3 es aceptable donde el kit no llega (`Switch`, `OutlinedTextField`, `DropdownMenu`, `AlertDialog` con `containerColor`/`shape` clay), siempre que el contenido interno sea clay. Un `Surface`/`Button` M3 desnudo en chrome nuevo es un hallazgo bloqueante.

## Tokens — cero hex fuera de `ClayTokens.kt`

- Colores: `ClayPalette` vía `ClayTokens.chrome(highContrast)` (canvas lavanda, `primary #7C3AED`, `primaryContainer #EDE0FF`, `secondary #DB2777`, `warningAmber`, etc.).
- Sombras: `ClayShadows.{card, button, pressed, floating}` (valores literales del DESIGN.md; `pressed` = bandas internas).
- Radios: `ClayRadius.{sm 8, base 16, md 24, lg 32, xl 48}`. Espaciados: `ClaySpacing.{xs 8, sm 12, md 16, lg 24, xl 36, gutter 16, gutterDesktop 32, marginDesktop 48}`.
- Tipografía: `ClayTypeScale` (12 estilos: displayLarge…labelSm) — Nunito Sans para display/headline/title y DM Sans para body/label según DESIGN.md; el recurso empaquetado es **DM Sans** (`LocalClayUiFontFamilies.sans`, OFL), serif/mono = familias de sistema. El bridge de `ClayTheme` mapea los 12 estilos a 15 roles M3.
- Alto contraste: `ClayTokens.chrome(uiAppearance.highContrastText)` y `readingTone(theme, highContrast)`.

## Regla de oro: chrome siempre claro; el tono solo en la hoja de lectura

- El chrome (nav, barra del libro, dock, Biblioteca, Cuaderno, Ajustes) es SIEMPRE claro: `ClayTokens.chrome(...)`. No existe chrome oscuro.
- El tema de lectura (`ReaderTheme.WHITE/SEPIA/SAGE/DARK`) se materializa SOLO con `ClayTokens.readingTone(theme, highContrast)` → `ReadingTone(isDark, sheet, ink, inkSoft, muted, border, accent)`:
  - hoja/página = `tone.sheet`; tinta = `tone.ink`/`inkSoft`/`muted`; bordes = `tone.border`; acentos dentro de la hoja = `tone.accent` (≥3:1 anotado contra su hoja).
- Highlights dentro de la hoja: `Color(if (tone.isDark) tone.hexDark else tone.hexLight)` (helper `readingHighlight` en `ReaderWorkspace.kt`). `palette.highlightColor(tone)` es para superficies de chrome claro (p. ej. Cuaderno), NUNCA para la hoja.
- `palette.isDarkReading` no debe usarse para pintar la hoja (el chrome ya no es oscuro).

## Estructura de superficies (escritorio)

- Shell: `MeridianFolioApp.kt` + `ShellChrome.kt` (`ClayTopNav`, `ClayBottomNav`, `ClayFooter`, `ClayLogoMark`, `BookmarkRibbon`, `ReaderToolbar`; `ClayBottomNavHeight = 76.dp`).
- Superficies en archivo propio: `LibraryWorkspace.kt`/`LibraryControls.kt`, `ReaderWorkspace.kt` (+ `ReaderViewModes.kt`, `ReaderPagination.kt`), `NotebookWorkspace.kt`, `SettingsWorkspace.kt`; diálogos en `EditingWorkspace.kt`.
- Breakpoints vigentes: ≥1040 dp layout de dos columnas (Ajustes 7/5), ≥760 dp rail/grid, <700 dp el shell cambia a la maquetación móvil (`isMobileKindleView`). La pasada móvil claymorphism (= las 5 pantallas mobile del proyecto Stitch) está PENDIENTE.
- Datos ficticios del mock están prohibidos: nada de sync falso, retención inventada, toasts decorativos o tonos inexistentes (p. ej. **"Clay Pastel" no existe** en core; los tonos reales son WHITE/SEPIA/SAGE/DARK). Donde el mock enseñe datos falsos, usar el dato real equivalente.

## Verificación visual

- `.\gradlew.bat :ui:desktopTest` — `VisualCaptureTest` captura las 4 superficies en ancho amplio/medio/estrecho → `ui/build/visual/{wide,mid,narrow}-{biblioteca,lector,cuaderno,ajustes}.png`. Regenera y revisa tras cada cambio visual.
- Los `testTag` del contrato (ver `shiorifolio-code-review`) son la prueba de que cada superficie sigue montable; no renombrar jamás.
- Antes de cerrar un cambio visual: chrome claro intacto, tono solo en la hoja, hit targets ≥48 dp, contraste ≥4.5:1 para texto y ≥3:1 para acentos en las 4 hojas.

## Prohibiciones explícitas

- Hex hardcodeado fuera de `theme/ClayTokens.kt`.
- Renombrar o perder `testTag` existentes.
- Tocar `:core` en un cambio puramente visual.
- Inventar componentes/tonos/estados que no existan en `:core` o en el kit.

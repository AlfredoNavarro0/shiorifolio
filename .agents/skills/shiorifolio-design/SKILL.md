---
name: shiorifolio-design
description: Reglas de diseño visual de ShioriFolio (Stitch "Serene Vintage Reader"): tokens de paleta, layouts Escritorio/Móvil, componentes y defectos visuales conocidos. Usar al crear o modificar cualquier composable de :ui.
---

# ShioriFolio — Diseño

Referencia completa de tokens y anatomía: `DESIGN.md` en la raíz. Este skill fija las **reglas de aplicación**.

## Colores
- Usar exclusivamente `MeridianPalette` obtenida con `MeridianDesign.paletteFor(state.theme)`. Prohibido `Color(0x...)` literal en composables, salvo `Color(theme.swatchHex)` para muestras de tono.
- Mapeo semántico:
  - Fondo de ventana → `paper`; hoja de lectura → `pageSurface`; paneles/docks → `elevatedSurface` o `paper.copy(alpha≈0.95)`.
  - Texto: `ink` (cuerpo/títulos), `inkSoft` (secundario), `muted` (metadatos). Nunca `muted` para contenido legible largo.
  - Bordes → `border`; chips y botones neutros → `pillBg`.
  - Acción primaria/selección → `accentGreen`; marcapáginas y recordatorios → `warningAmber`.
- Resaltados: `palette.highlightColor(tone)`; ya resuelve claro/oscuro.
- Todo color nuevo se añade como campo de `MeridianPalette` con valor en los **cuatro** temas.

## Defecto conocido: contraste en modo oscuro
No existe `MaterialTheme` envolviendo la app ⇒ los componentes Material 3 (`OutlinedTextField`, `Button`, `OutlinedButton`, `Slider`) usan el esquema claro por defecto: texto/labels oscuros sobre fondo oscuro.
Corrección esperada: envolver el contenido de `MeridianFolioApp` en `MaterialTheme(colorScheme = palette.toColorScheme())` (función nueva en `MeridianTheme.kt` que mapee `primary=accentGreen`, `onPrimary`, `surface=pageSurface`, `onSurface=ink`, `onSurfaceVariant=inkSoft`, `outline=border`, `background=paper`). Además, `Color.White` sobre `accentGreen` falla en `DARK` (`#89B093` es claro): usar un `onAccent` derivado de la paleta.

## Tipografía
- `FontFamily.Serif`: cuerpo literario y títulos de capítulo.
- `FontFamily.Monospace`: números de folio, contadores, porcentajes, telemetría.
- `FontFamily.SansSerif`: controles, pestañas, formularios.
- Tamaño del cuerpo = `state.fontSizePt` (13–26); `lineHeight = fontSize * state.lineSpacingMultiplier` (1.5/1.8/2.1). No codificar tamaños de cuerpo fijos en vistas de lectura.
- Texto de controles ≥ 11.sp con peso `Medium`+; por debajo se pixela en Windows (queja explícita sobre las pestañas).

## Layouts
`MeridianFolioApp` elige vista por `BoxWithConstraints`: `< 760.dp` o toggle manual ⇒ Móvil Kindle; si no, Escritorio.

**Escritorio** (orden vertical): `StitchDesktopTopNavigationBar` → banners opcionales (recordatorio, frase) → workspace de la sección → `StitchDesktopStatusStreamBar`. En `READER` el workspace es `StitchDesktopReaderSpreadWorkspace` = barra auxiliar + pliego + `StitchFloatingCommandDock`.

**Móvil** (`StitchMobileKindleReaderLayout`): lienzo a sangre con HUD superior y dock inferior superpuestos; tocar el lienzo con HUD oculto lo muestra. Solo se usa en sección `READER`; las demás secciones reutilizan las vistas de escritorio.

**Pliego (`StitchBookSpreadLeaf`)**: header (`título · FOLIO n`) → cuerpo con scroll → footer (`Folio • SF-nn`). En `TWO_COLUMNS` se superpone el gradiente de lomo central; paginación avanza de 2 en 2.

## Componentes y patrones
- Botón/chip: `Box` + `clip(RoundedCornerShape(5–8.dp))` + `background` + `border(1.dp, palette.border)` + `clickable` + `padding`. Seleccionado ⇒ fondo `accentGreen`.
- Cada control interactivo relevante lleva `testTag` (ver `shiorifolio-code-review`).
- Cinta `SwallowtailBookmarkRibbon` (`MeridianTheme.kt`): no debe solaparse con texto; hoy tapa `FOLIO n` (backlog #6).
- Iconografía: glifos Unicode (`‹ › ⛶ ◐ 栞 ⚙`); no hay dependencia de iconos Material. Mantenerlo así o añadir `material-icons-extended` explícitamente.
- Animaciones discretas (`AnimatedVisibility`, `tween` ≤ 300 ms). El friso `MeridianViaductFooterBanner` existe en `MeridianTheme.kt` pero no se usa en el layout actual.

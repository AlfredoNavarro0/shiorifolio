# Migración de `:ui` al diseño *Tactile Claymorphism* (Stitch — "Claymorphic App Redesign")

- **Fecha:** 2026-10-18
- **Estado:** aprobado en conversación (4 secciones); pendiente de revisión escrita del humano
- **Commit base:** `b964fc9` (retirada del sistema visual anterior — la UI no compila a propósito)
- **Proyecto Stitch:** `projects/14806878120696973328`
- **Design system:** `assets/ac83ed6dddb54c399f1f3020fb17c8d4` — *Tactile Claymorphism* (light, fidelity, semilla `#7c3aed`, `ROUND_FULL`, escala de espaciado 2)
- **Referencias crudas descargadas:** `%TEMP%\opencode\stitch-claymorphic\` (9 capturas PNG + 9 HTML; se archivarán en el wiki)

---

## 1. Contexto y objetivo

El commit `b964fc9` eliminó el sistema visual *Serene Vintage Reader*: `DESIGN.md`, el skill `shiorifolio-design`, el mock `index.html`, `MeridianTheme.kt` y `ModernReaderChrome.kt`. La UI quedó rota a propósito (~1086 referencias sin resolver), conservando todo el código funcional y `:core` intacto.

**Objetivo:** reconstruir `:ui` sobre el sistema *Tactile Claymorphism* del proyecto Stitch, con paridad funcional total y sin tocar `:core`, el formato de datos ni los contratos de UI (testTags, firma de `MeridianFolioApp`, texto `shiorifolio` visible).

**Métrica de éxito:** la aplicación compila y pasa todas las puertas; las 4 superficies principales (Biblioteca, Reader, Notebook, Ajustes) reproducen las referencias de Stitch en escritorio; ningún flujo existente se pierde.

## 2. Decisiones aprobadas

1. **Fidelidad:** reconstrucción fiel de las 4 superficies principales; a los flujos secundarios (diálogos, importación, respaldo, edición) se les aplica el lenguaje claymorphic (no hay referencias de Stitch para ellos).
2. **Temas:** el chrome de la app es siempre claro (canvas lavanda); los 4 temas de lectura (`WHITE`, `SEPIA`, `SAGE`, `DARK`) tiñen únicamente la hoja de lectura, expuestos como *Tono ambiental* (como en la pantalla Reader de Stitch). No se inventa un modo oscuro claymorphic.
3. **Secuencia:** primero la fundación (build verde de nuevo), después superficie por superficie.
4. **Form factor:** todo el escritorio primero; la pasada móvil (5 pantallas mobile) es un esfuerzo posterior y separado.
5. **Capa de tema (enfoque híbrido):** primitivas clay propias para la UI firma + `MaterialTheme` teñido con tokens clay para controles estándar (menús, sliders, checkboxes, diálogos).

## 3. Capa de tema (paquete nuevo `ui/.../ui/theme/`)

### 3.1 Archivos

| Archivo | Contenido |
|---|---|
| `ClayTokens.kt` | `ClayPalette` (tokens del `DESIGN.md`), `ClaySpacing`, `ClayRadius`, `ClayTypography`, definiciones de las sombras clay |
| `ClayModifiers.kt` | `Modifier.clayCard()`, `clayButton()`, `clayPressed()`, `clayFloating()`, superficie frosted; sombras multicapa con `drawBehind`/`drawWithContent`; blur donde la plataforma lo soporte (Skia / API 31+), fallback translúcido |
| `ClayTheme.kt` | `LocalClayPalette` + `LocalClayDesign` y puente `MaterialTheme(colorScheme, typography, shapes)` |
| `ClayComponents.kt` | Kit base: `ClayCard`, `ClayPrimaryButton`, `ClaySecondaryButton`, `ClayIconButton`, `ClayTextField`, `ClayChip`, `ClayFilterPill`, `ClaySegmentedTabs`, `ClayTopNav`, `ClayBottomNav`, `ClayProgressBar`, `ClayDialogScaffold`, `ClayToneSwatches` |
| `ClayHelpers.kt` | Helpers funcionales que murieron con `MeridianTheme.kt` (desde `f0edce9`): `UiFontFamilyOption.toComposeFontFamily`, `UiFontWeightOption.toComposeFontWeight`, `BookFontFamilyOption.toComposeFontFamily`, `BookTextAlignment.toComposeTextAlign`, `canonicalKeyName`, `KeyEvent.matches`, `KeyEvent.toKeyBindingSpecOrNull`, `Modifier.shioriFocusableClickable`, colores de `TextField` (sustituye `shioriOutlinedTextFieldColors`) |
| `composeResources/font/` | Nunito Sans + DM Sans (TTF estáticos, licencia OFL) — requiere añadir `compose.components.resources` a `:ui` |

### 3.2 Tokens (resumen)

- **Color:** canvas `#F4F1FA`; superficie tarjeta `rgba(255,255,255,0.75)` + blur 16px; secundaria `#EFEBF5`; contenedores `#f9f1ff` → `#e7e0ee`; texto `#332F3A` / `#1d1a24`, muted `#635F69`; primary `#630ed4`/`#7C3AED`, secondary `#b5005d`/`#DB2777`, tertiary `#005479`/`#0EA5E9`; mint `#10B981`; amber `#F59E0B`; error `#ba1a1a`; outline `#7b7487` / variante `#ccc3d8`.
- **Tipografía:** display/headline/title en Nunito Sans (700–900); body/label en DM Sans (400–700); tamaños y line-heights exactos del `DESIGN.md`.
- **Radios:** contenedores 36–48px, tarjetas 28–32px, botones/inputs 20–24px, badges full pill.
- **Espaciado:** escala del design system (`space-xs` 0.5rem … `space-xl` 2.25rem; gutters 1/1.5/2rem; márgenes 1/2/3rem).
- **Sombras:** `clayCard`, `clayButton`, `clayPressed`, `clayFloating` con los valores literales del `DESIGN.md`.

### 3.3 Regla de convivencia

**Primitiva clay primero; `MaterialTheme`/M3 solo cuando no exista equivalente clay** (menús desplegables, sliders, checkbox, plumbing de diálogos, snackbars).

### 3.4 Temas de lectura

`ReaderTheme` conserva sus 4 valores (sin cambios en `:core`). `WHITE`/`SEPIA`/`SAGE`/`DARK` pasan a ser tonos de la hoja de lectura y del HUD asociado; `highContrast` sigue existiendo como modificador de accesibilidad. La hoja de lectura mantiene la tipografía configurable por el usuario.

## 4. Shell de escritorio

- **Barra superior flotante (pill):** marca + wordmark `shiorifolio` (contrato visible); pestañas **Biblioteca / Reader / Notebook / Ajustes** con pill violeta activa (etiquetas tomadas de las pantallas de Stitch); a la derecha `theme_toggle_button` reutilizado como **toggle de tono de lectura** (WHITE ↔ DARK, conserva el contrato de `toggleTheme()`) + toggle rápido de contraste alto.
- **Pie:** barra fina con `ShioriFolio · Lector y editor de PDF & EPUB` y, a la derecha, solo datos reales existentes (versión, número de libros, telemetría, antigüedad del respaldo cuando se conozca). Sin indicadores falsos de sincronización.
- **Canvas:** `#F4F1FA` con blobs ambientales estáticos dibujados en Compose; contenido en contenedor centrado (márgenes 3rem, gutters 2rem).
- **Shell compacto:** barra inferior clay mínima (4 secciones) para que la ruta Android/ventana estrecha compile y no se degrade; se refina en la pasada móvil.
- **Descartado del mock:** avatar, "Local time", "Sincronizado", páginas de privacidad/términos (no mapean a funciones reales).

## 5. Superficies (escritorio)

### 5.1 Biblioteca — `LibraryWorkspace.kt`, `LibraryControls.kt`, `LibraryBookCard.kt`
- Cabecera `Tu biblioteca` + cápsulas de conteo; botón violeta `Importar libro`; búsqueda; fila de chips de filtro (Todos / Leyendo / PDF / EPUB / Filtros / Ordenar) con panel de filtros clay (estado, orden, colecciones, formato).
- Rejilla de tarjetas clay: portada, badges (`Nuevo`, % de progreso, opciones), título/autor, barra de progreso + páginas.
- Se reutiliza toda la lógica: importación y observación de carpetas, filtros combinables, 4 variantes de visualización, acciones por libro, telemetría. `testTag`s obligatorios intactos (`view_mode_<MODE>`, etc.).

### 5.2 Reader — workspace del pliego en `MeridianFolioApp.kt` → extraído a `ReaderWorkspace.kt` + `ReaderViewModes.kt`, `ReaderPagination.kt`
- Tira superior: título/autor, historial ←/→, chips de herramientas (Apariencia, Marcaciones, Frase, Capítulos), zoom/ajuste, color de PDF.
- Pliego centrado con flechas laterales; cabecera de hoja (`FOLIO N`, capítulo).
- Deck inferior: indicador de página + slider de progreso, badges de velocidad/pausa/tiempo, control de tamaño de fuente, **swatches de tono ambiental**, fila de pills de modo de vista (Doble página / Simple / Paginado / Continuo / Reflujo).
- Se reutiliza: paginación, modos de vista, zoom/fit, historial, TOC, selección de frases, recordatorios/marcapáginas, telemetría, atajos. La hoja toma el tono de lectura; el chrome permanece clay.

### 5.3 Notebook — extraído de `MeridianFolioApp.kt` a `NotebookWorkspace.kt`
- Columna izquierda: *Nueva entrada* (contexto del lector, palabra/frase, nota, ejemplo, chips de tono de resaltado — los nombres coinciden con los enums de `:core`), botón guardar, aviso de respaldo.
- Columna derecha: toolbar de filtro por tag (sincronizar/exportar), tarjetas de entrada con burbuja de cita, nota de anclaje, acciones, insignia de huérfana + **Reanclar**, estado vacío `¿Leyendo en el Reader?` con `Ir al Reader`.
- Se reutiliza: `saveWordOrPhrase`, `reattachPhrase`, `AnchorStatus.ORPHANED`, notas/tags.

### 5.4 Ajustes — `SettingsWorkspace.kt`
- Pestañas segmentadas: **Apariencia / Lectura / Accesos / Datos y Almacenamiento**.
- Paneles: Tema del Lector (los 4 tonos como tarjetas; "Clay Pastel" mostrado como diseño fijo de la app), Interfaz y Tipografía (familia, peso, tamaño, interlineado, contraste alto, restablecer), **vista previa en vivo**, y los flujos existentes de respaldo/exportación/importación.
- Se reutiliza: todos los handlers y el estado; sin cambios de comportamiento.

## 6. Flujos secundarios (solo restyle)

`EditingWorkspace.kt` (editor de página, buscar/reemplazar, capítulos, metadatos, reanclaje, recordatorios, respaldo, confirmaciones), editores de frase/tono, snackbars y estados vacío/carga pasan a primitivas clay. Lógica intacta.

## 7. Estado y persistencia

- **Cero cambios en `:core`**: ni modelos, ni codecs, ni `CURRENT_PARSER_VERSION` (no hay cambio de salida), ni formato de respaldo.
- El cambio de semántica de temas es solo de UI (chrome fijo claro; tono para la hoja).
- Sin campos persistidos nuevos → sin riesgo de serialización.

## 8. Verificación

- **Puertas por paso:** `:core:desktopTest`, `:ui:desktopTest`, `:desktopApp:classes`, `:androidApp:compileDebugKotlin`.
- **Contratos:** `testTag`s requeridos presentes; firma de `MeridianFolioApp` sin cambios; `toggleTheme()` sigue alternando WHITE ↔ DARK; texto `shiorifolio` visible.
- **Pruebas nuevas (smoke, una por superficie):** navegación entre pestañas, filtros de Biblioteca, pills de modo de vista + swatches de tono en Reader, alta/reanclaje en Notebook, pestañas de Ajustes.
- **Pasada visual:** ejecutar la app de escritorio, capturar a ancho amplio y estrecho y comparar con las capturas de Stitch.
- **Accesibilidad:** objetivos de 48dp, `contentDescription`, contraste.

## 9. Secuencia de entrega

Cada paso termina en verde, con code review (`shiorifolio-code-review`), commit en `main` y **sin push** hasta que se pida:

1. **Fundación** — paquete de tema, fuentes, helpers, primitivas, shell (top nav + pie + bottom nav compacta) y sustitución mecánica en la UI existente → compila y tests verdes.
2. **Biblioteca** escritorio.
3. **Reader** escritorio (+ extracción del workspace a su archivo).
4. **Notebook** escritorio (+ extracción).
5. **Ajustes** escritorio.
6. **Flujos secundarios** + pruebas smoke + pasada visual; después, nuevo skill `shiorifolio-design` con las reglas de *Tactile Claymorphism*, y actualización de skills/wiki.
7. **Pasada móvil** contra las 5 pantallas mobile — esfuerzo separado, fuera de este spec.

## 10. Fuera de alcance

Elementos del mock sin función real (avatar, "Local time", sincronización falsa, privacidad/términos); modo oscuro claymorphic; cambios en `:core` o formatos; fidelidad móvil antes del paso 7; rediseño de la lógica de edición.

## 11. Riesgos y mitigaciones

| Riesgo | Mitigación |
|---|---|
| Sombras multicapa costosas en draw | Mantenerlas estáticas y hoisted; evitar recomposición; medir en el paso visual |
| `MaterialTheme` y primitivas clay en conflicto | Regla 3.3: primitiva clay primero |
| `MeridianFolioApp.kt` de ~3 800 líneas | Extracciones puntuales (Reader, Notebook) durante sus pasos |
| Pérdida de contratos al reescribir layouts | Checklist de testTags + pruebas existentes en cada paso |
| Fuentes no disponibles | TTF estáticos empaquetados (OFL), sin dependencia de red en runtime |

## 12. Criterios de aceptación

1. `:core:desktopTest`, `:ui:desktopTest`, `:desktopApp:classes` y `:androidApp:compileDebugKotlin` en verde.
2. Las 4 superficies de escritorio reproducen las referencias de Stitch (estructura, tokens, tipografía, formas, sombras).
3. Ningún flujo existente se pierde: lectura/edición, telemetría, modos de vista, recordatorios, frases, respaldo, atajos, accesibilidad.
4. `git diff` de `:core` vacío; sin cambios de formato persistido.
5. Contratos de UI intactos (testTags, firma raíz, wordmark, `toggleTheme()`).
6. El chrome permanece claro en todo momento; los tonos de lectura se aplican solo a la hoja.

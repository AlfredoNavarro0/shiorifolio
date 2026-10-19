---
name: shiorifolio-architecture
description: Estructura de módulos, flujo de estado, persistencia, puntos expect/actual y comandos de verificación de ShioriFolio. Usar al modificar código en :core, :ui, :desktopApp o :androidApp.
---

# ShioriFolio — Arquitectura

## Módulos
| Módulo | Responsabilidad | Archivos clave |
|---|---|---|
| `:core` | Dominio, parsing, predicción, persistencia. Sin Compose. | `DocumentModels.kt`, `FolioRepository.kt`, `DocumentCodec.kt`, `ReadingPredictionEngine.kt`, `SampleLibrary.kt` |
| `:ui` | Toda la UI compartida | Shell: `MeridianFolioApp.kt`, `ShellChrome.kt`. Superficies: `LibraryWorkspace.kt`/`LibraryControls.kt`, `ReaderWorkspace.kt` (+ `ReaderViewModes.kt`, `ReaderPagination.kt`), `NotebookWorkspace.kt`, `SettingsWorkspace.kt`, `EditingWorkspace.kt` (diálogos). Tema: `theme/` (ver skill `shiorifolio-design`). |
| `:desktopApp` | Ventana JVM, diálogos de archivo AWT, icono | `Main.kt` |
| `:androidApp` | Activity, SAF/picker | `MainActivity.kt` |

Source sets de `:core`: `commonMain`, `jvmSharedMain` (compartido desktop+android), `desktopMain`, `androidMain`, `commonTest`.

## Flujo de estado
- Fuente única de verdad: `FolioRepository.state: StateFlow<FolioPersistedState>`.
- La UI solo lee `state` y llama métodos del repositorio; nunca muta modelos directamente.
- Cada método mutador termina en `saveCurrentState()`. Excepción: `recordReadingTimeTick` guarda cada 5 recálculos (se llama cada segundo).
- Estado efímero de UI (sección activa, paneles abiertos, Zen) vive en `remember` dentro de `MeridianFolioApp`, no en el repositorio.

## Persistencia
- JSON en `~/.meridian-folio/meridian_folio_state.json`; imágenes extraídas en `~/.meridian-folio/media/<libro>/`.
- `Json { ignoreUnknownKeys = true; encodeDefaults = true }` ⇒ **todo campo nuevo en un modelo `@Serializable` requiere valor por defecto**, o se pierde el estado existente del usuario.
- Nunca renombrar campos ni valores de enum serializados; añadir nuevos.
- `BookDocument.parserVersion`: al cambiar la salida del parser, subir `DocumentCodec.CURRENT_PARSER_VERSION` (actualmente `5`) para que los libros importados se re-parseen desde `filePath` conservando `id` y `currentPage`.

## Costuras de plataforma (expect/actual)
| Declaración | Desktop | Android |
|---|---|---|
| `PlatformPdfEngine` | Apache PDFBox 2.0.32 (texto + `PDFRenderer`) | `android.graphics.pdf.PdfRenderer` |
| `PlatformFileAndZipIo` | `jvmSharedMain` (común a ambos) | idem |
| `decodeImageBitmapFromBytes` | Skia | `BitmapFactory` |

- Rutas `lazy_pdf://` en `ContentBlock.imagePath` se renderizan bajo demanda en `rememberLoadedImageBitmap`; no pre-renderizar páginas PDF al importar.
- Lógica nueva va en `commonMain` salvo que dependa de una API de plataforma.

## Contrato de `MeridianFolioApp`
Parámetros consumidos por ambas plataformas: `repository`, `onPickFileToImport`, `onPickFileToExport`. Añadir parámetros solo con valor por defecto; si cambia la firma, actualizar `Main.kt` y `MainActivity.kt` en el mismo cambio.

## Particularidades de Kotlin Multiplatform
- Propiedades nullable de clases de `:core` no admiten smart-cast en `:ui`: copiar a `val` local antes de comprobar (`val p = block.imagePath; if (p != null) ...`).
- `MeridianFolioApp.kt` es el shell (~2.000 líneas tras extraer las superficies Reader/Notebook): cada superficie vive en su propio archivo dentro de `dev.meridian.folio.ui` y los componentes nuevos van a archivo propio, nunca engordando el shell.
- El sistema visual (Tactile Claymorphism) vive en `theme/` (`ClayTokens`, `ClayComponents`, `ClayModifiers`, `ClayTheme`, `ClayHelpers`); reglas de uso en el skill `shiorifolio-design`.

## Verificación obligatoria
```powershell
.\gradlew.bat :core:desktopTest :ui:desktopTest :desktopApp:classes --no-daemon
.\gradlew.bat :androidApp:compileDebugKotlin --no-daemon   # si se tocó código común o Android
```
- `MeridianCoreTest.parsesRealLightNovelEpub...` lee el EPUB real del usuario en `Downloads`.
- `MeridianFolioUiTest` depende de los `testTag` listados en `shiorifolio-code-review`.
- `VisualCaptureTest` (desktopTest) captura las 4 superficies en ancho amplio/medio/estrecho → `ui/build/visual/*.png`; úsalo para verificar cualquier cambio visual.

## Repositorio
`https://github.com/AlfredoNavarro0/shiorifolio`, rama `main`. Sin historial previo al commit inicial: ante un cambio destructivo, revertir con `git`.

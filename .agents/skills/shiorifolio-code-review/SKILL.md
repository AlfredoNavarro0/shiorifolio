---
name: shiorifolio-code-review
description: Checklist de revisión de código para ShioriFolio con criterios bloqueantes. Usar al revisar un diff, antes de hacer commit/push, o cuando el usuario pida un code review.
---

# ShioriFolio — Code Review

## Procedimiento
1. `git diff --stat` y `git status`: identificar archivos tocados y archivos nuevos no rastreados.
2. Revisar cada archivo contra las secciones siguientes.
3. Ejecutar la verificación de `shiorifolio-architecture`. Un build rojo es bloqueante sin excepción.
4. Informar hallazgos como **Bloqueante / Recomendado / Nota**, con `archivo:línea`.

## Bloqueantes
**Persistencia**
- Campo nuevo en clase `@Serializable` sin valor por defecto.
- Campo o constante de enum serializado renombrado o eliminado.
- Cambio en la salida de `DocumentCodec`/`PlatformPdfEngine` sin subir `parserVersion`.
- Mutador nuevo en `FolioRepository` que no llama `saveCurrentState()`.

**Contratos**
- Cambio de firma de `MeridianFolioApp` sin actualizar `Main.kt` y `MainActivity.kt`.
- `testTag` existente eliminado o renombrado. Tags requeridos: `meridian_root`, `prediction_badge`, `page_reminder_banner`, `view_mode_<MODE>`, `theme_toggle_button`, `reading_timer_toggle`, `prev_page_button`, `next_page_button`, `open_reminder_btn`, `open_mark_phrase_btn`. El texto `shiorifolio` debe seguir visible en la vista de escritorio.
- `toggleTheme()` debe seguir alternando `WHITE ↔ DARK` (lo asume el test de UI).
- Pérdida de cualquier funcionalidad listada en `shiorifolio-product`.

**Plataforma**
- API JVM/Android (`java.io`, `android.*`, AWT) en `commonMain`.
- Compose importado en `:core`.
- Trabajo de I/O o render de PDF en el hilo de composición (usar `produceState` + `Dispatchers.Default`/`IO`, como `rememberLoadedImageBitmap`).

**Diseño**
- El sistema visual anterior fue retirado (2026-10-18); la UI está en migración a Claymorphism. Hasta que exista la nueva guía visual, los hallazgos de diseño se reportan como **Nota**, no como bloqueantes.

## Recomendados
- Componente nuevo añadido a `MeridianFolioApp.kt` en vez de a un archivo propio.
- `remember` sin las claves que invalidan el cálculo (p. ej. `remember(book.id, book.pages.size)` para TOC).
- Recorrer `book.pages` completo dentro de composición en libros grandes sin `remember`.
- Strings de UI nuevos en inglés.
- Reescritura masiva de un archivo cuando bastaba un cambio local. Precedente: una sesión previa (GPT Sol) dejó el proyecto inconsistente con una refactorización a medias y hubo que revertirla.
- Lógica de dominio nueva sin test en `MeridianCoreTest`; control nuevo de UI sin `testTag`.

## Fuera de alcance
- Formato/estilo cubierto por el compilador.
- Warnings existentes de `expect/actual` Beta y de `runComposeUiTest` deprecado.

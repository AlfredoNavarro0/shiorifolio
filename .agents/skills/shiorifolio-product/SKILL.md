---
name: shiorifolio-product
description: Visión, alcance funcional, invariantes de producto y backlog de ShioriFolio. Usar antes de proponer o implementar cualquier funcionalidad nueva o cambio de comportamiento.
---

# ShioriFolio — Producto

Lector **y editor** nativo de PDF/EPUB para Windows, Linux y Android (Compose Multiplatform). **No es una app web**.

## Funcionalidades no negociables
Toda modificación debe conservar:
1. Lectura de PDF y EPUB reales (texto Unicode + imágenes) y edición/exportación de ambos formatos.
2. Registro de tiempo activo y páginas visitadas por libro.
3. Predicción de tiempo restante que se **recalcula continuamente** (`ReadingPredictionEngine`).
4. Cuatro modos de vista: `ONE_COLUMN` (Simple), `TWO_COLUMNS` (Doble página), `STRETCH`, `WIDE` (Ancho).
5. Recordatorio de página para la próxima sesión (`PageReminder`, uno por libro).
6. Marcar y guardar palabras/frases con tono y nota (`SavedPhrase`).
7. Temas: `WHITE`, `SEPIA`, `SAGE`, `DARK`; tamaño de fuente y interlineado ajustables.

## Identidad
- Nombre: **ShioriFolio (栞 · Folio)**; wordmark en minúsculas `shiorifolio`.
- Los identificadores `Meridian*` / paquete `dev.meridian.folio` son herencia de la primera versión. **No renombrar**: rompe persistencia, tests e imports.
- Textos de UI en **español**. Términos de marca (`Reader`, `Archive`, `Notebook`, `Telemetry`) pueden quedarse en inglés.

## Datos de referencia del usuario
- `C:\Users\terra\Downloads\The Gal Is Sitting Behind Me, and Loves Me vol 1 - Fungus FTL.epub` (237 págs.) y su `.pdf` (146 págs.). Úsalos para validar parsing y layout con contenido real.

## Backlog pendiente (solicitado por el usuario, sin implementar)
| # | Requisito | Nota técnica |
|---|---|---|
| 1 | Atajos de teclado: cambiar pestañas, página anterior/siguiente, ocultar dock central, ciclar modo de vista | `Modifier.onPreviewKeyEvent` en la raíz + `FocusRequester`; no capturar teclas mientras un `TextField` tiene foco |
| 2 | El editor de recordatorio no se puede cerrar sin guardar | Añadir acción "Cancelar/✕" que ponga `showReminderEditor = false` |
| 3 | Modo Zen: las hojas ocupan toda la ventana | Ocultar barra superior, dock y status bar; combinar con `onToggleFullscreen` de la plataforma |
| 4 | Ajustes tipográficos: familia, peso y color de fuente; títulos de pestañas poco nítidos | Persistir en `FolioPersistedState` (con valores por defecto) |

## Fuente del diseño (en migración)
La dirección anterior (*Serene Vintage Reader*) fue retirada el 2026-10-18. El nuevo sistema visual es **Tactile Claymorphism**, del proyecto de Stitch **"Claymorphic App Redesign"** (`projects/14806878120696973328`).

<p align="center">
  <img src="assets/shiorifolio-logo.svg" width="110" alt="ShioriFolio Logo" />
</p>

<h1 align="center">ShioriFolio (栞 · Folio)</h1>

<p align="center">
  <strong>Lector y Editor Multiplataforma de PDF &amp; EPUB · Windows, Linux y Android</strong><br/>
  <em>栞 Shiori (Marcapáginas) + Folio (Hoja de libro) — Donde cada página deja su huella.</em>
</p>

---

## Descripción General

**ShioriFolio** (`shiorifolio`) es una aplicación nativa multiplataforma construida con **Kotlin Multiplatform** y **Compose Multiplatform** para **Windows, Linux y Android** (sin depender de un navegador web). Combina un lector literario inmersivo (pensado para lectura prolongada y dispositivos e-ink tipo Kindle) con un editor estructurado de documentos **PDF** y **EPUB**, telemetría de lectura en tiempo real y predicción adaptativa del tiempo restante de lectura.

> [!NOTE]
> La dirección visual anterior (*Serene Vintage Reader*) fue retirada. La interfaz se está migrando al sistema **Tactile Claymorphism** del proyecto Stitch **«Claymorphic App Redesign»**.

---

## Características Principales

- **Lector y Editor Nativo de PDF y EPUB**:
  - Importación y renderizado de archivos `.epub` (respetando el orden del `spine` OPF, tabla de contenidos e ilustraciones de capítulos/portada) y `.pdf` (extracción de texto Unicode mediante **Apache PDFBox** en escritorio y renderizado bajo demanda de páginas con `PDFRenderer` / `PdfRenderer` en Android).
  - Editor integrado para modificar metadatos (título, subtítulo, autor, formato), editar bloques de contenido (encabezados, párrafos, citas, notas editoriales), insertar o eliminar páginas y exportar documentos a `.pdf` o `.epub`.
- **Seguimiento de Tiempo y Predicción Dinámica de Finalización (`ReadingPredictionEngine`)**:
  - Cronómetro activo segundo a segundo por libro y por página.
  - Cálculo adaptativo (media móvil ponderada exponencialmente **EWMA**) que recalcula continuamente el ritmo en **segundos por página**, **palabras por minuto (WPM)** y el **tiempo estimado restante** para terminar el libro.
- **4 Modos de Visualización de Página**:
  - **Doble página (`2 Columns`)**: Pliego de libro abierto con sombra de lomo central realista, cabeceras de archivo y cinta marcapáginas en cola de golondrina (*Swallowtail Ribbon*).
  - **Simple (`1 Column`)**: Hoja de manuscrito centrada en una sola columna.
  - **Ancho (`Wide`)**: Lienzo amplio con columna lateral de marginalia, ritmo de lectura y notas del folio activo.
  - **Stretch (`Stretch`)**: Vista proporcional expandida a pantalla completa.
- **Recordatorio de Página y Cinta Marcapáginas (`栞 Shiori`)**:
  - Guarda un recordatorio en cualquier página con nota personalizada y etiqueta de sesión (ej. *"Próxima Sesión"*, *"Esta noche"*) para retomar la lectura exactamente donde la dejaste.
- **Cuaderno de Palabras y Frases Guardadas (`Notebook / Lexicon`)**:
  - Subraya y guarda palabras o frases directamente desde la página activa con 4 tonos de resaltado (*Sage Ink*, *Gilded Amber*, *Sienna Clay*, *Viaduct Slate*), oración de contexto y notas personales.
- **4 Tonos de Papel y Temas (Claro / Oscuro) + Controles Tipográficos**:
  - **Kindle Cálido (`#FCF9F3`)**, **Sepia (`#F5EDDB`)**, **Salvia (`#EEF3EE`)** y **Oscuro / Noche (`#231F1C`)**.
  - Ajuste en vivo del tamaño de fuente (`A-` / `17pt` / `A+`) e interlineado (`1.5×`, `1.8×`, `2.1×`).
- **Doble Interfaz Adaptativa**:
  - **Vista de Escritorio**: Barra editorial superior, pliego de doble página con cinta marcapáginas, dock flotante de comandos con deslizador de progreso y barra de telemetría inferior.
  - **Vista Móvil Kindle Edge-to-Edge**: HUD flotante translúcido, progreso superior a sangre, píldora flotante de ritmo de lectura y hoja desplegable de *Ajustes de Lectura Kindle*.

---

## Arquitectura del Proyecto

```text
shiorifolio/
├── assets/
│   └── shiorifolio-logo.svg       # Emblema grabado de ShioriFolio (Libro abierto + Cinta Shiori + Estrella)
├── core/                          # Modelos de dominio, motor de predicción EWMA, codecs PDF/EPUB y persistencia
│   ├── src/commonMain/            # DocumentModels, ReadingPredictionEngine, FolioRepository, SampleLibrary
│   ├── src/desktopMain/           # PlatformPdfEngine (Apache PDFBox + PDFRenderer)
│   └── src/androidMain/           # PlatformPdfEngine (Android PdfRenderer)
├── ui/                            # Interfaz compartida en Compose Multiplatform
│   ├── src/commonMain/            # MeridianFolioApp, workspaces y componentes compartidos
│   ├── src/desktopMain/           # Decodificador de imágenes Skia (Desktop)
│   └── src/androidMain/           # Decodificador de imágenes BitmapFactory (Android)
├── desktopApp/                    # Punto de entrada JVM Desktop (Windows / Linux) y empaquetado nativo
└── androidApp/                    # Punto de entrada Android (MainActivity + AndroidManifest)
```

---

## Requisitos y Ejecución

### Requisitos
- **JDK 17+** (compatible con JDK 17–21+)
- **Gradle Wrapper** incluido en el repositorio (`./gradlew` / `.\gradlew.bat`)

### Ejecutar en Escritorio (Windows / Linux)
```bash
# Windows (PowerShell / CMD)
.\gradlew.bat :desktopApp:run

# Linux / macOS
./gradlew :desktopApp:run
```

### Ejecutar Pruebas Unitarias y de Interfaz
```bash
.\gradlew.bat :core:desktopTest :ui:desktopTest
```

### Compilar Aplicación Android
```bash
.\gradlew.bat :androidApp:assembleDebug
```

---

## Licencia

© 2026 **ShioriFolio (栞 · Folio)** — *Read, track, and edit from folio to folio.*

package dev.meridian.folio.core

actual object PlatformTooling {
    /** Android no ejecuta herramientas externas de escritorio como epubcheck. */
    actual fun runEpubcheck(epubFilePath: String): String? = null
}

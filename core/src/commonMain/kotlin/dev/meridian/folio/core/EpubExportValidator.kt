package dev.meridian.folio.core

/** Result of validating an exported EPUB (internal structure + epubcheck when available). */
data class ExportValidationReport(
    val errors: List<String> = emptyList(),
    val warnings: List<String> = emptyList(),
    val epubcheckOutput: String? = null,
) {
    val isClean: Boolean get() = errors.isEmpty()

    fun summaryLine(): String = when {
        errors.isNotEmpty() -> "EPUB con ${errors.size} error(es) estructural(es)"
        warnings.isNotEmpty() -> "EPUB válido con ${warnings.size} aviso(s)"
        else -> "EPUB válido"
    }
}

/**
 * Structural validation of the generated EPUB: mimetype, container, OPF, manifest,
 * spine, navigation and XHTML documents. Independent from external tools.
 */
object EpubExportValidator {
    fun validateStructure(bytes: ByteArray): ExportValidationReport {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()

        val rawEntries = runCatching { PlatformFileAndZipIo.unzipEntries(bytes) }.getOrNull()
            ?: return ExportValidationReport(errors = listOf("El archivo generado no es un ZIP válido"))
        val entries = rawEntries.mapKeys { it.key.replace('\\', '/') }

        if (entries["mimetype"]?.decodeToString()?.trim() != "application/epub+zip") {
            errors.add("mimetype ausente o incorrecto")
        }
        val container = entries["META-INF/container.xml"]?.decodeToString()
        when {
            container == null -> errors.add("Falta META-INF/container.xml")
            !container.contains("content.opf") -> errors.add("container.xml no declara el OPF")
        }

        val opfKey = entries.keys.firstOrNull { it.endsWith("content.opf", ignoreCase = true) }
        if (opfKey == null) {
            errors.add("Falta content.opf")
            return ExportValidationReport(errors, warnings)
        }
        val opfDir = opfKey.substringBeforeLast('/', missingDelimiterValue = "")
        val opf = entries.getValue(opfKey).decodeToString()

        fun tagValue(tag: String): String? = Regex("<$tag[^>]*>([\\s\\S]*?)</$tag>", RegexOption.IGNORE_CASE)
            .find(opf)?.groupValues?.get(1)?.replace(Regex("<[^>]+>"), "")?.trim()

        if (tagValue("dc:title").isNullOrBlank()) errors.add("dc:title ausente en el OPF")
        if (tagValue("dc:creator").isNullOrBlank()) warnings.add("dc:creator ausente en el OPF")
        if (tagValue("dc:language").isNullOrBlank()) errors.add("dc:language ausente en el OPF")
        if (!opf.contains("dcterms:modified")) warnings.add("Falta la fecha dcterms:modified")

        val idToHref = mutableMapOf<String, String>()
        val seenIds = mutableSetOf<String>()
        Regex("""<item\b([^>]*)/?>""", RegexOption.IGNORE_CASE).findAll(opf).forEach { item ->
            val attrs = item.groupValues[1]
            val id = attr(attrs, "id") ?: return@forEach
            val href = attr(attrs, "href") ?: return@forEach
            if (!seenIds.add(id)) errors.add("id duplicado en el manifest: $id")
            idToHref[id] = href
            val target = resolveZipPath(opfDir, href)
            if (target.isNotBlank() && !entries.containsKey(target)) {
                errors.add("El manifest referencia un recurso inexistente: $href")
            }
        }

        var spineItems = 0
        Regex("""<itemref\b([^>]*)/?>""", RegexOption.IGNORE_CASE).findAll(opf).forEach { itemref ->
            spineItems++
            val idref = attr(itemref.groupValues[1], "idref")
            if (idref == null || idref !in idToHref) errors.add("itemref sin destino válido: ${idref ?: "?"}")
        }
        if (spineItems == 0) errors.add("El spine está vacío")

        val navHref = idToHref.values.firstOrNull { it.endsWith("nav.xhtml", ignoreCase = true) }
        if (navHref == null) {
            errors.add("Falta el documento de navegación (nav.xhtml)")
        } else if (!Regex("""properties\s*=\s*"[^"]*\bnav\b""").containsMatchIn(opf)) {
            warnings.add("nav.xhtml sin properties=\"nav\" en el manifest")
        }
        if (idToHref.values.none { it.endsWith("toc.ncx", ignoreCase = true) }) {
            warnings.add("Sin toc.ncx (compatibilidad EPUB2)")
        }

        entries.filterKeys { it.endsWith(".xhtml", ignoreCase = true) }.forEach { (key, value) ->
            if (!value.decodeToString().contains("<html", ignoreCase = true)) {
                errors.add("Documento XHTML inválido: $key")
            }
        }

        return ExportValidationReport(errors, warnings)
    }

    private fun attr(attrs: String, name: String): String? =
        Regex("""\b$name\s*=\s*["']([^"']*)["']""", RegexOption.IGNORE_CASE).find(attrs)?.groupValues?.get(1)

    private fun resolveZipPath(baseDir: String, href: String): String {
        val clean = href.substringBefore('#').substringBefore('?')
        return if (baseDir.isBlank()) clean else "$baseDir/$clean"
    }
}

/** Runs optional external validation tools when they are available. */
expect object PlatformTooling {
    /**
     * Runs `epubcheck` on the given file when the system has it installed.
     * Returns its output, or null when the tool is not available.
     */
    fun runEpubcheck(epubFilePath: String): String?
}

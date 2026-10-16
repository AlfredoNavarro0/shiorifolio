package dev.meridian.folio.core

expect object PlatformFileAndZipIo {
    fun unzipEntries(bytes: ByteArray): Map<String, ByteArray>
    fun zipEntries(entries: Map<String, ByteArray>, uncompressedFirstEntry: String? = null): ByteArray
    fun readFileBytes(path: String): ByteArray?
    fun writeFileBytes(path: String, bytes: ByteArray): Boolean
    fun cacheMediaFile(bookId: String, relativeName: String, bytes: ByteArray): String?
    fun defaultStateFilePath(): String
    fun currentEpochMillis(): Long
}

expect object PlatformPdfEngine {
    fun extractPdfDocument(
        docId: String,
        baseTitle: String,
        bytes: ByteArray,
        filePath: String?,
        nowMs: Long,
    ): BookDocument?

    fun renderPdfPageOnDemand(
        filePath: String,
        docId: String,
        pageNumber: Int,
    ): String?

    fun exportPdfDocument(book: BookDocument): ByteArray?
}

object DocumentCodec {

    fun importFromBytes(
        fileName: String,
        bytes: ByteArray,
        filePath: String? = null,
        nowMs: Long = PlatformFileAndZipIo.currentEpochMillis(),
    ): BookDocument {
        val lower = fileName.lowercase()
        val isEpub = lower.endsWith(".epub") || (bytes.size >= 4 && bytes[0] == 0x50.toByte() && bytes[1] == 0x4B.toByte())
        return if (isEpub) {
            parseEpubBytes(fileName, bytes, filePath, nowMs)
        } else {
            parsePdfBytes(fileName, bytes, filePath, nowMs)
        }
    }

    fun exportToBytes(book: BookDocument): ByteArray =
        when (book.format) {
            DocumentFormat.PDF -> PlatformPdfEngine.exportPdfDocument(book) ?: generatePdfBytes(book)
            DocumentFormat.EPUB -> generateEpubBytes(book)
        }

    fun parseEpubBytes(
        fileName: String,
        bytes: ByteArray,
        filePath: String? = null,
        nowMs: Long = PlatformFileAndZipIo.currentEpochMillis(),
    ): BookDocument {
        val rawEntries = runCatching { PlatformFileAndZipIo.unzipEntries(bytes) }.getOrDefault(emptyMap())
        val entries = rawEntries.mapKeys { (k, _) -> normalizeZipPath(k) }
        val baseTitle = fileName.substringBeforeLast('.').ifBlank { "Untitled EPUB" }
        val docId = "epub_${baseTitle.hashCode().toUInt()}"

        // 1. Locate OPF file via META-INF/container.xml or fallback to first .opf entry
        val containerXml = entries["META-INF/container.xml"]?.decodeToString() ?: ""
        val rootfilePath = Regex("""full-path\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
            .find(containerXml)?.groupValues?.get(1)?.let { normalizeZipPath(it) }
        val opfEntry = (if (rootfilePath != null) entries.entries.find { it.key.equals(rootfilePath, ignoreCase = true) } else null)
            ?: entries.entries.firstOrNull { it.key.endsWith(".opf", ignoreCase = true) }

        val opfPath = opfEntry?.key ?: ""
        val opfDir = if (opfPath.contains('/')) opfPath.substringBeforeLast('/') else ""
        val opfXml = opfEntry?.value?.decodeToString() ?: ""

        val parsedTitle = extractXmlTag(opfXml, "dc:title")
            ?: extractXmlTag(opfXml, "title")
            ?: baseTitle
        val parsedAuthor = extractXmlTag(opfXml, "dc:creator")
            ?: extractXmlTag(opfXml, "creator")
            ?: "Meridian Archive Author"

        // 2. Cache all image assets inside the EPUB so cover art and chapter illustrations render natively
        val cachedImageMap = LinkedHashMap<String, String>()
        for ((entryKey, entryBytes) in entries) {
            val lowerKey = entryKey.lowercase()
            if (lowerKey.endsWith(".jpg") || lowerKey.endsWith(".jpeg") || lowerKey.endsWith(".png") || lowerKey.endsWith(".webp") || lowerKey.endsWith(".gif")) {
                val cachedPath = PlatformFileAndZipIo.cacheMediaFile(docId, entryKey, entryBytes)
                if (cachedPath != null) {
                    cachedImageMap[entryKey.lowercase()] = cachedPath
                }
            }
        }

        // 3. Parse OPF <manifest> (id -> resolved zip path) and <spine> (<itemref idref="..."/>)
        val manifestIdToPath = LinkedHashMap<String, String>()
        val itemRegex = Regex("""<item\b([^>]*)/?>""", RegexOption.IGNORE_CASE)
        for (m in itemRegex.findAll(opfXml)) {
            val attrs = m.groupValues[1]
            val id = extractAttr(attrs, "id") ?: continue
            val href = extractAttr(attrs, "href") ?: continue
            val resolved = resolveRelativeZipPath(opfDir, decodeUrlPercent(href))
            manifestIdToPath[id] = resolved
        }

        val spinePaths = mutableListOf<String>()
        val itemrefRegex = Regex("""<itemref\b([^>]*)/?>""", RegexOption.IGNORE_CASE)
        for (m in itemrefRegex.findAll(opfXml)) {
            val idref = extractAttr(m.groupValues[1], "idref") ?: continue
            val targetPath = manifestIdToPath[idref] ?: continue
            spinePaths.add(targetPath)
        }

        val orderedHtmlEntries = if (spinePaths.isNotEmpty()) {
            spinePaths.mapNotNull { sp ->
                entries.entries.find { it.key.equals(sp, ignoreCase = true) }
            }
        } else {
            entries.entries
                .filter {
                    val k = it.key.lowercase()
                    (k.endsWith(".xhtml") || k.endsWith(".html") || k.endsWith(".htm")) &&
                        !k.contains("nav.") && !k.contains("toc.")
                }
                .sortedBy { it.key }
        }

        // 4. Parse each spine XHTML into ContentBlocks (headings, paragraphs, quotes, and images!)
        val pages = mutableListOf<BookPage>()
        var pageIdx = 1

        for (entry in orderedHtmlEntries) {
            val entryPath = entry.key
            val entryDir = if (entryPath.contains('/')) entryPath.substringBeforeLast('/') else ""
            val rawHtml = entry.value.decodeToString()
            val fileStem = entryPath.substringAfterLast('/').substringBeforeLast('.')

            val extractedBlocks = extractXhtmlBlocksWithImages(
                html = rawHtml,
                xhtmlDir = entryDir,
                cachedImageMap = cachedImageMap,
            )

            if (extractedBlocks.isEmpty()) continue

            val firstHeading = extractedBlocks.firstOrNull { it.style == BlockStyle.HEADING }?.text
                ?: extractXmlTag(rawHtml, "title")?.takeIf { it.isNotBlank() }
                ?: humanizeStem(fileStem)

            // Paginate blocks comfortably (~280 words or max 14 blocks per page; full-page image gets its own page if standalone)
            val currentPageBlocks = mutableListOf<ContentBlock>()
            var currentPageWords = 0
            var subPageIndex = 1

            fun flushPage() {
                if (currentPageBlocks.isEmpty()) return
                val chapterLabel = if (subPageIndex == 1) firstHeading else "$firstHeading (p. $subPageIndex)"
                val numberedBlocks = currentPageBlocks.mapIndexed { bIdx, blk ->
                    blk.copy(id = "epub_${pageIdx}_$bIdx")
                }
                val firstImg = numberedBlocks.firstOrNull { it.style == BlockStyle.IMAGE }?.imagePath
                pages.add(
                    BookPage(
                        pageNumber = pageIdx++,
                        chapterTitle = chapterLabel,
                        blocks = numberedBlocks,
                        renderedPageImagePath = if (numberedBlocks.size == 1 && firstImg != null) firstImg else null,
                    ),
                )
                currentPageBlocks.clear()
                currentPageWords = 0
                subPageIndex++
            }

            for (blk in extractedBlocks) {
                if (blk.style == BlockStyle.IMAGE) {
                    if (currentPageBlocks.isNotEmpty()) {
                        flushPage()
                    }
                    currentPageBlocks.add(blk)
                    flushPage()
                } else {
                    val w = blk.wordCount
                    if (currentPageBlocks.isNotEmpty() && (currentPageWords + w > 280 || currentPageBlocks.size >= 14)) {
                        flushPage()
                    }
                    currentPageBlocks.add(blk)
                    currentPageWords += w
                }
            }
            flushPage()
        }

        if (pages.isEmpty()) {
            pages.add(
                BookPage(
                    pageNumber = 1,
                    chapterTitle = parsedTitle,
                    blocks = listOf(
                        ContentBlock("p_1_0", BlockStyle.HEADING, parsedTitle),
                        ContentBlock("p_1_1", BlockStyle.PARAGRAPH, "Imported EPUB archive ($fileName)."),
                    ),
                ),
            )
        }

        return BookDocument(
            id = docId,
            title = cleanHtmlEntities(parsedTitle),
            subtitle = "EPUB Folio (${pages.size} pages)",
            author = cleanHtmlEntities(parsedAuthor),
            format = DocumentFormat.EPUB,
            filePath = filePath,
            pages = pages,
            currentPage = 1,
            parserVersion = 3,
            createdAtMs = nowMs,
            updatedAtMs = nowMs,
        )
    }

    private fun extractXhtmlBlocksWithImages(
        html: String,
        xhtmlDir: String,
        cachedImageMap: Map<String, String>,
    ): List<ContentBlock> {
        val bodyHtml = Regex("<body[^>]*>([\\s\\S]*?)</body>", RegexOption.IGNORE_CASE)
            .find(html)?.groupValues?.get(1) ?: html

        val results = mutableListOf<ContentBlock>()
        val tokenRegex = Regex(
            """<(h1|h2|h3|p|blockquote|li)\b[^>]*>([\s\S]*?)</\1>|<(img|image)\b([^>]*)/?>""",
            RegexOption.IGNORE_CASE,
        )

        for (match in tokenRegex.findAll(bodyHtml)) {
            val blockTag = match.groupValues[1].lowercase()
            val innerHtml = match.groupValues[2]
            val imgTag = match.groupValues[3].lowercase()
            val imgAttrs = match.groupValues[4]

            if (imgTag.isNotEmpty()) {
                val src = extractAttr(imgAttrs, "src")
                    ?: extractAttr(imgAttrs, "href")
                    ?: extractAttr(imgAttrs, "xlink:href")
                if (src != null) {
                    val resolved = resolveRelativeZipPath(xhtmlDir, decodeUrlPercent(src)).lowercase()
                    val localImgPath = cachedImageMap[resolved]
                        ?: cachedImageMap.entries.firstOrNull { it.key.endsWith(resolved.substringAfterLast('/')) }?.value
                    if (localImgPath != null) {
                        val alt = extractAttr(imgAttrs, "alt")?.takeIf { it.isNotBlank() } ?: ""
                        results.add(
                            ContentBlock(
                                id = "tmp_img_${results.size}",
                                style = BlockStyle.IMAGE,
                                text = alt,
                                imagePath = localImgPath,
                            ),
                        )
                    }
                }
                continue
            }

            // Check if innerHtml contains an embedded <img> or <image>
            val innerImgMatch = Regex("""<(img|image)\b([^>]*)/?>""", RegexOption.IGNORE_CASE).find(innerHtml)
            if (innerImgMatch != null) {
                val attrs = innerImgMatch.groupValues[2]
                val src = extractAttr(attrs, "src")
                    ?: extractAttr(attrs, "href")
                    ?: extractAttr(attrs, "xlink:href")
                if (src != null) {
                    val resolved = resolveRelativeZipPath(xhtmlDir, decodeUrlPercent(src)).lowercase()
                    val localImgPath = cachedImageMap[resolved]
                        ?: cachedImageMap.entries.firstOrNull { it.key.endsWith(resolved.substringAfterLast('/')) }?.value
                    if (localImgPath != null) {
                        results.add(
                            ContentBlock(
                                id = "tmp_img_${results.size}",
                                style = BlockStyle.IMAGE,
                                text = "",
                                imagePath = localImgPath,
                            ),
                        )
                    }
                }
            }

            val stripped = cleanHtmlEntities(
                innerHtml
                    .replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
                    .replace(Regex("<[^>]+>"), "")
                    .replace(Regex("[ \\t\\r]+"), " ")
                    .trim(),
            )
            if (stripped.isBlank()) continue

            val style = when (blockTag) {
                "h1", "h2", "h3" -> BlockStyle.HEADING
                "blockquote" -> BlockStyle.QUOTE
                else -> BlockStyle.PARAGRAPH
            }
            // Avoid consecutive duplicate headings
            if (style == BlockStyle.HEADING && results.lastOrNull()?.style == BlockStyle.HEADING) {
                val prev = results.removeAt(results.lastIndex)
                results.add(prev.copy(text = "${prev.text} — $stripped"))
            } else {
                results.add(
                    ContentBlock(
                        id = "tmp_${results.size}",
                        style = style,
                        text = stripped,
                    ),
                )
            }
        }
        return results
    }

    fun generateEpubBytes(book: BookDocument): ByteArray {
        val entries = LinkedHashMap<String, ByteArray>()
        entries["mimetype"] = "application/epub+zip".encodeToByteArray()

        entries["META-INF/container.xml"] = """
            <?xml version="1.0" encoding="UTF-8"?>
            <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
              <rootfiles>
                <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
              </rootfiles>
            </container>
        """.trimIndent().encodeToByteArray()

        entries["OEBPS/style.css"] = """
            body { background: #ebeee7; color: #2b3a30; font-family: Georgia, serif; line-height: 1.65; margin: 2em; }
            h1, h2 { color: #2b3a30; font-weight: 600; }
            blockquote { border-left: 3px solid #2b3a30; padding-left: 1em; color: #3d4a41; font-style: italic; }
            .callout { background: #dfe4da; padding: 0.8em 1em; border-radius: 4px; }
        """.trimIndent().encodeToByteArray()

        val manifestItems = StringBuilder()
        val spineItems = StringBuilder()

        book.pages.forEachIndexed { idx, page ->
            val pageNum = idx + 1
            val fileName = "page_$pageNum.xhtml"
            val itemId = "page_$pageNum"
            manifestItems.append("""    <item id="$itemId" href="$fileName" media-type="application/xhtml+xml"/>""").append("\n")
            spineItems.append("""    <itemref idref="$itemId"/>""").append("\n")

            val bodyHtml = buildString {
                append("<h2>").append(escapeXml(page.chapterTitle)).append("</h2>\n")
                for (block in page.blocks) {
                    when (block.style) {
                        BlockStyle.HEADING -> append("<h3>").append(escapeXml(block.text)).append("</h3>\n")
                        BlockStyle.QUOTE -> append("<blockquote>").append(escapeXml(block.text)).append("</blockquote>\n")
                        BlockStyle.CALLOUT -> append("<p class=\"callout\">").append(escapeXml(block.text)).append("</p>\n")
                        BlockStyle.PARAGRAPH -> append("<p>").append(escapeXml(block.text)).append("</p>\n")
                        BlockStyle.IMAGE -> if (block.text.isNotBlank()) {
                            append("<p><em>[Illustration: ").append(escapeXml(block.text)).append("]</em></p>\n")
                        }
                    }
                }
            }

            val xhtml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <!DOCTYPE html>
                <html xmlns="http://www.w3.org/1999/xhtml" lang="en">
                <head>
                  <meta charset="UTF-8"/>
                  <title>${escapeXml(page.chapterTitle)}</title>
                  <link rel="stylesheet" type="text/css" href="style.css"/>
                </head>
                <body>
                $bodyHtml
                </body>
                </html>
            """.trimIndent()
            entries["OEBPS/$fileName"] = xhtml.encodeToByteArray()
        }

        val opf = """
            <?xml version="1.0" encoding="UTF-8"?>
            <package xmlns="http://www.idpf.org/2007/opf" unique-identifier="BookId" version="3.0">
              <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                <dc:identifier id="BookId">${escapeXml(book.id)}</dc:identifier>
                <dc:title>${escapeXml(book.title)}</dc:title>
                <dc:creator>${escapeXml(book.author)}</dc:creator>
                <dc:language>en</dc:language>
              </metadata>
              <manifest>
                <item id="css" href="style.css" media-type="text/css"/>
            $manifestItems  </manifest>
              <spine>
            $spineItems  </spine>
            </package>
        """.trimIndent()
        entries["OEBPS/content.opf"] = opf.encodeToByteArray()

        return PlatformFileAndZipIo.zipEntries(entries, uncompressedFirstEntry = "mimetype")
    }

    fun parsePdfBytes(
        fileName: String,
        bytes: ByteArray,
        filePath: String? = null,
        nowMs: Long = PlatformFileAndZipIo.currentEpochMillis(),
    ): BookDocument {
        val baseTitle = fileName.substringBeforeLast('.').ifBlank { "Untitled PDF" }
        val docId = "pdf_${baseTitle.hashCode().toUInt()}"

        // 1. Use PlatformPdfEngine (Apache PDFBox on Desktop JVM, PdfRenderer on Android)
        val platformParsed = PlatformPdfEngine.extractPdfDocument(
            docId = docId,
            baseTitle = baseTitle,
            bytes = bytes,
            filePath = filePath,
            nowMs = nowMs,
        )
        if (platformParsed != null && platformParsed.pages.isNotEmpty()) {
            return platformParsed
        }

        // 2. Fallback for uncompressed synthetic PDF streams
        val rawLatin = bytes.decodeToString(throwOnInvalidSequence = false)
        val extractedLines = mutableListOf<String>()
        val parenRegex = Regex("\\(([^()\\\\]*(?:\\\\.[^()\\\\]*)*)\\)")
        val btBlocks = Regex("BT([\\s\\S]*?)ET").findAll(rawLatin).toList()

        for (bt in btBlocks) {
            val blockText = parenRegex.findAll(bt.groupValues[1])
                .map { unescapePdfString(it.groupValues[1]) }
                .filter { str -> str.isNotBlank() && str.all { ch -> ch.code in 32..126 || ch == '\n' } }
                .joinToString(" ")
                .trim()
            if (blockText.length >= 2) {
                extractedLines.add(blockText)
            }
        }

        val pages = if (extractedLines.isNotEmpty()) {
            extractedLines.chunked(6).mapIndexed { idx, chunk ->
                val pageNo = idx + 1
                BookPage(
                    pageNumber = pageNo,
                    chapterTitle = "$baseTitle — Page $pageNo",
                    blocks = chunk.mapIndexed { bIdx, pText ->
                        ContentBlock(
                            id = "pdf_${pageNo}_$bIdx",
                            style = if (bIdx == 0 && pText.length < 75) BlockStyle.HEADING else BlockStyle.PARAGRAPH,
                            text = pText,
                        )
                    },
                )
            }
        } else {
            listOf(
                BookPage(
                    pageNumber = 1,
                    chapterTitle = "$baseTitle — Page 1",
                    blocks = listOf(
                        ContentBlock("pdf_1_0", BlockStyle.HEADING, baseTitle),
                        ContentBlock("pdf_1_1", BlockStyle.PARAGRAPH, "Loaded PDF document ($fileName)."),
                    ),
                ),
            )
        }

        return BookDocument(
            id = docId,
            title = baseTitle,
            subtitle = "PDF Archival Edition",
            author = "Meridian Press",
            format = DocumentFormat.PDF,
            filePath = filePath,
            pages = pages,
            currentPage = 1,
            parserVersion = 3,
            createdAtMs = nowMs,
            updatedAtMs = nowMs,
        )
    }

    fun generatePdfBytes(book: BookDocument): ByteArray {
        PlatformPdfEngine.exportPdfDocument(book)?.let { return it }
        val objects = mutableListOf<String>()
        objects.add("1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n")
        objects.add("")
        objects.add("3 0 obj\n<< /Type /Font /Subtype /Type1 /BaseFont /Times-Roman >>\nendobj\n")
        objects.add("4 0 obj\n<< /Type /Font /Subtype /Type1 /BaseFont /Times-Bold >>\nendobj\n")

        val pageObjectIds = mutableListOf<Int>()
        for (page in book.pages) {
            val contentStream = buildPdfPageStream(book.title, page)
            val contentObjId = objects.size + 1
            objects.add(
                "$contentObjId 0 obj\n<< /Length ${contentStream.length} >>\nstream\n$contentStream\nendstream\nendobj\n",
            )
            val pageObjId = objects.size + 1
            pageObjectIds.add(pageObjId)
            objects.add(
                "$pageObjId 0 obj\n<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] " +
                    "/Resources << /Font << /F1 3 0 R /F2 4 0 R >> >> /Contents $contentObjId 0 R >>\nendobj\n",
            )
        }

        val kidsRef = pageObjectIds.joinToString(" ") { "$it 0 R" }
        objects[1] = "2 0 obj\n<< /Type /Pages /Kids [ $kidsRef ] /Count ${pageObjectIds.size} >>\nendobj\n"

        val sb = StringBuilder()
        sb.append("%PDF-1.4\n")
        val offsets = IntArray(objects.size)
        for (i in objects.indices) {
            offsets[i] = sb.length
            sb.append(objects[i])
        }
        val xrefOffset = sb.length
        sb.append("xref\n0 ${objects.size + 1}\n")
        sb.append("0000000000 65535 f \n")
        for (offset in offsets) {
            sb.append(offset.toString().padStart(10, '0')).append(" 00000 n \n")
        }
        sb.append("trailer\n<< /Size ${objects.size + 1} /Root 1 0 R >>\n")
        sb.append("startxref\n$xrefOffset\n%%EOF\n")
        return sb.toString().encodeToByteArray()
    }

    private fun buildPdfPageStream(bookTitle: String, page: BookPage): String {
        val sb = StringBuilder()
        sb.append("BT /F2 10 Tf 56 742 Td (")
            .append(escapePdfString("${bookTitle.uppercase()}  -  PAGE ${page.pageNumber}"))
            .append(") Tj ET\n")
        sb.append("BT /F2 15 Tf 56 714 Td (")
            .append(escapePdfString(page.chapterTitle))
            .append(") Tj ET\n")

        var y = 682
        for (block in page.blocks) {
            if (block.style == BlockStyle.IMAGE) continue
            val font = if (block.style == BlockStyle.HEADING) "/F2 13 Tf" else "/F1 11 Tf"
            val wrapped = wrapLine(block.text, 78)
            for (line in wrapped) {
                if (y < 60) break
                sb.append("BT $font 56 $y Td (").append(escapePdfString(line)).append(") Tj ET\n")
                y -= 16
            }
            y -= 10
        }
        return sb.toString()
    }

    private fun wrapLine(text: String, maxChars: Int): List<String> {
        val words = text.trim().split(Regex("\\s+"))
        val lines = mutableListOf<String>()
        val cur = StringBuilder()
        for (w in words) {
            if (cur.length + w.length + 1 > maxChars && cur.isNotEmpty()) {
                lines.add(cur.toString())
                cur.clear()
            }
            if (cur.isNotEmpty()) cur.append(' ')
            cur.append(w)
        }
        if (cur.isNotEmpty()) lines.add(cur.toString())
        return lines
    }

    private fun escapePdfString(input: String): String =
        input
            .replace("\\", "\\\\")
            .replace("(", "\\(")
            .replace(")", "\\)")
            .replace("\r", " ")
            .replace("\n", " ")

    private fun unescapePdfString(input: String): String =
        input
            .replace("\\(", "(")
            .replace("\\)", ")")
            .replace("\\\\", "\\")
            .replace("\\n", "\n")
            .replace("\\r", " ")

    private fun normalizeZipPath(path: String): String {
        val parts = mutableListOf<String>()
        for (seg in path.replace('\\', '/').split('/')) {
            when (seg) {
                "", "." -> {}
                ".." -> if (parts.isNotEmpty()) parts.removeAt(parts.lastIndex)
                else -> parts.add(seg)
            }
        }
        return parts.joinToString("/")
    }

    private fun resolveRelativeZipPath(baseDir: String, relativeHref: String): String {
        val cleanHref = relativeHref.substringBefore('#').substringBefore('?')
        return if (baseDir.isBlank()) {
            normalizeZipPath(cleanHref)
        } else {
            normalizeZipPath("$baseDir/$cleanHref")
        }
    }

    private fun decodeUrlPercent(s: String): String {
        if (!s.contains('%')) return s
        val out = StringBuilder()
        var i = 0
        while (i < s.length) {
            if (s[i] == '%' && i + 2 < s.length) {
                val hex = s.substring(i + 1, i + 3)
                val code = hex.toIntOrNull(16)
                if (code != null) {
                    out.append(code.toChar())
                    i += 3
                    continue
                }
            }
            out.append(s[i])
            i++
        }
        return out.toString()
    }

    private fun extractAttr(attrs: String, attrName: String): String? {
        val regex = Regex("""\b$attrName\s*=\s*["']([^"']*)["']""", RegexOption.IGNORE_CASE)
        return regex.find(attrs)?.groupValues?.get(1)
    }

    private fun humanizeStem(stem: String): String =
        stem
            .replace(Regex("([a-z])([A-Z0-9])"), "$1 $2")
            .replace('_', ' ')
            .trim()
            .ifBlank { "Section" }

    private fun extractXmlTag(xml: String, tag: String): String? {
        val regex = Regex("<$tag[^>]*>([\\s\\S]*?)</$tag>", RegexOption.IGNORE_CASE)
        val raw = regex.find(xml)?.groupValues?.get(1) ?: return null
        return cleanHtmlEntities(raw.replace(Regex("<[^>]+>"), " ").replace(Regex("\\s+"), " ").trim())
            .takeIf { it.isNotBlank() }
    }

    private fun cleanHtmlEntities(input: String): String {
        var s = input
            .replace("&nbsp;", " ")
            .replace("&#160;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace("&#39;", "'")
            .replace("&lsquo;", "‘")
            .replace("&rsquo;", "’")
            .replace("&ldquo;", "“")
            .replace("&rdquo;", "”")
            .replace("&mdash;", "—")
            .replace("&ndash;", "–")
            .replace("&hellip;", "…")

        // Numeric decimal entities &#1234;
        s = Regex("&#(\\d+);").replace(s) { m ->
            val code = m.groupValues[1].toIntOrNull()
            if (code != null && code in 32..0x10FFFF) code.toChar().toString() else ""
        }
        // Numeric hex entities &#x1F600;
        s = Regex("&#x([0-9a-fA-F]+);").replace(s) { m ->
            val code = m.groupValues[1].toIntOrNull(16)
            if (code != null && code in 32..0x10FFFF) code.toChar().toString() else ""
        }
        return s
    }

    private fun escapeXml(input: String): String =
        input
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
}

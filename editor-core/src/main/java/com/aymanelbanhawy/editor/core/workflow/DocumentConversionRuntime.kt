package com.aymanelbanhawy.editor.core.workflow

import android.content.Context
import com.aymanelbanhawy.editor.core.model.DocumentModel
import com.aymanelbanhawy.editor.core.search.IndexedPageContent
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import java.io.File
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface DocumentConversionProvider {
    val providerId: String
    val displayName: String

    fun supportsExportToWord(document: DocumentModel): Boolean = true
    fun supportsImport(source: File): Boolean

    suspend fun exportPdfToWord(
        document: DocumentModel,
        pages: List<IndexedPageContent>,
        destination: File,
    ): ExportArtifactModel

    suspend fun importSourceToPdf(
        source: File,
        destination: File,
        displayName: String,
    ): File
}

class DocumentConversionRuntime(
    private val providers: List<DocumentConversionProvider>,
) {
    fun providerForExport(document: DocumentModel): DocumentConversionProvider {
        return providers.firstOrNull { it.supportsExportToWord(document) }
            ?: error("No document conversion provider is available for Word export.")
    }

    fun providerForImport(source: File): DocumentConversionProvider {
        return providers.firstOrNull { it.supportsImport(source) }
            ?: error("No document conversion provider can import ${source.name}.")
    }
}

class OpenXmlDocxDocumentConversionProvider(
    context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : DocumentConversionProvider {

    override val providerId: String = "openxml-docx"
    override val displayName: String = "OpenXML DOCX Conversion"

    init {
        PDFBoxResourceLoader.init(context)
    }

    override fun supportsImport(source: File): Boolean {
        return when (source.extension.lowercase(Locale.US)) {
            "docx", "txt", "md", "markdown", "png", "jpg", "jpeg", "webp", "bmp", "gif", "pdf" -> true
            else -> false
        }
    }

    override suspend fun exportPdfToWord(
        document: DocumentModel,
        pages: List<IndexedPageContent>,
        destination: File,
    ): ExportArtifactModel = withContext(ioDispatcher) {
        destination.parentFile?.mkdirs()
        val paragraphs = buildList {
            add(DocxParagraph(document.documentRef.displayName, style = "Title"))
            pages.forEachIndexed { index, page ->
                add(DocxParagraph("Page ${index + 1}", pageBreakBefore = index > 0, style = "Heading1"))
                if (page.blocks.isEmpty()) {
                    add(DocxParagraph(page.pageText.ifBlank { "(No text detected)" }))
                } else {
                    page.blocks.forEach { block ->
                        val text = block.text.ifBlank { page.pageText }.ifBlank { "(No text detected)" }
                        add(DocxParagraph(text))
                    }
                }
            }
        }
        writeMinimalDocx(destination, paragraphs)
        ExportArtifactModel(
            path = destination.absolutePath,
            displayName = destination.name,
            mimeType = "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        )
    }

    override suspend fun importSourceToPdf(source: File, destination: File, displayName: String): File = withContext(ioDispatcher) {
        destination.parentFile?.mkdirs()
        when (source.extension.lowercase(Locale.US)) {
            "pdf" -> source.copyTo(destination, overwrite = true)
            "docx" -> renderTextPdf(extractDocxText(source), destination, displayName)
            "txt", "md", "markdown" -> renderTextPdf(source.readText(), destination, displayName)
            "png", "jpg", "jpeg", "webp", "bmp", "gif" -> renderImagePdf(source, destination)
            else -> error("Unsupported import format: ${source.extension}")
        }
        destination
    }

    private fun extractDocxText(source: File): String {
        ZipFile(source).use { zip ->
            val entry = zip.getEntry("word/document.xml") ?: error("DOCX is missing word/document.xml")
            val xml = zip.getInputStream(entry).bufferedReader().use { it.readText() }
            return extractWordprocessingText(xml)
        }
    }

    private fun renderTextPdf(text: String, destination: File, title: String) {
        val normalized = text.ifBlank { "(No content detected in source document.)" }
        val lines = wrapLines(normalized)
        PDDocument().use { document ->
            var page = PDPage(PDRectangle.LETTER)
            document.addPage(page)
            var stream = PDPageContentStream(document, page)
            var cursorY = page.mediaBox.height - 56f
            val marginLeft = 48f
            val lineHeight = 16f
            try {
                stream.beginText()
                stream.setFont(PDType1Font.HELVETICA_BOLD, 18f)
                stream.newLineAtOffset(marginLeft, cursorY)
                stream.showText(sanitizePdfText(title))
                stream.endText()
                cursorY -= 28f
                lines.forEach { line ->
                    if (cursorY <= 56f) {
                        stream.close()
                        page = PDPage(PDRectangle.LETTER)
                        document.addPage(page)
                        stream = PDPageContentStream(document, page)
                        cursorY = page.mediaBox.height - 56f
                    }
                    stream.beginText()
                    stream.setFont(PDType1Font.HELVETICA, 11f)
                    stream.newLineAtOffset(marginLeft, cursorY)
                    stream.showText(sanitizePdfText(line))
                    stream.endText()
                    cursorY -= lineHeight
                }
            } finally {
                stream.close()
            }
            document.save(destination)
        }
    }

    private fun renderImagePdf(source: File, destination: File) {
        PDDocument().use { document ->
            val page = PDPage(PDRectangle.LETTER)
            document.addPage(page)
            val image = PDImageXObject.createFromFileByContent(source, document)
            val pageWidth = page.mediaBox.width
            val pageHeight = page.mediaBox.height
            val drawableWidth = pageWidth - 48f
            val drawableHeight = pageHeight - 48f
            val scale = min(drawableWidth / image.width.toFloat(), drawableHeight / image.height.toFloat())
            val scaledWidth = max(1f, image.width * scale)
            val scaledHeight = max(1f, image.height * scale)
            val x = (pageWidth - scaledWidth) / 2f
            val y = (pageHeight - scaledHeight) / 2f
            PDPageContentStream(document, page).use { stream ->
                stream.drawImage(image, x, y, scaledWidth, scaledHeight)
            }
            document.save(destination)
        }
    }

    private fun wrapLines(text: String, maxCharsPerLine: Int = 92): List<String> {
        val output = mutableListOf<String>()
        text.lineSequence().forEach { rawLine ->
            val line = rawLine.trim()
            if (line.isBlank()) {
                output += ""
            } else {
                var current = StringBuilder()
                line.split(Regex("\\s+")).forEach { word ->
                    if (current.isEmpty()) {
                        current.append(word)
                    } else if (current.length + 1 + word.length <= maxCharsPerLine) {
                        current.append(' ').append(word)
                    } else {
                        output += current.toString()
                        current = StringBuilder(word)
                    }
                }
                if (current.isNotEmpty()) output += current.toString()
            }
        }
        return output.ifEmpty { listOf("") }
    }

    private fun sanitizePdfText(input: String): String {
        return input
            .replace('\u0000', ' ')
            .replace(Regex("[\\r\\n\\t]+"), " ")
            .trim()
            .ifBlank { " " }
    }

    companion object {
        fun writeMinimalDocx(destination: File, paragraphs: List<DocxParagraph>) {
            destination.parentFile?.mkdirs()
            ZipOutputStream(destination.outputStream().buffered()).use { zip ->
                zip.writeEntry(
                    "[Content_Types].xml",
                    """
                    <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                    <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
                      <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
                      <Default Extension="xml" ContentType="application/xml"/>
                      <Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
                    </Types>
                    """.trimIndent(),
                )
                zip.writeEntry(
                    "_rels/.rels",
                    """
                    <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                    <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                      <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
                    </Relationships>
                    """.trimIndent(),
                )
                zip.writeEntry(
                    "word/document.xml",
                    buildDocumentXml(paragraphs),
                )
            }
        }

        private fun buildDocumentXml(paragraphs: List<DocxParagraph>): String {
            val body = buildString {
                paragraphs.forEach { paragraph ->
                    append("<w:p>")
                    if (paragraph.style != null) {
                        append("<w:pPr><w:pStyle w:val=\"")
                        append(escapeXml(paragraph.style))
                        append("\"/></w:pPr>")
                    }
                    append("<w:r>")
                    if (paragraph.pageBreakBefore) {
                        append("<w:br w:type=\"page\"/>")
                    }
                    append("<w:t xml:space=\"preserve\">")
                    append(escapeXml(paragraph.text))
                    append("</w:t>")
                    append("</w:r>")
                    append("</w:p>")
                }
            }
            return """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
                  <w:body>
                    $body
                    <w:sectPr/>
                  </w:body>
                </w:document>
            """.trimIndent()
        }

        private fun extractWordprocessingText(xml: String): String {
            val paragraphMatches = Regex("<w:p[\\s>].*?</w:p>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
                .findAll(xml)
                .map { paragraphMatch ->
                    val paragraphXml = paragraphMatch.value
                    val hasPageBreak = paragraphXml.contains("w:type=\"page\"")
                    val text = Regex("<w:t(?:[^>]*)>(.*?)</w:t>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
                        .findAll(paragraphXml)
                        .joinToString(separator = "") { decodeXml(it.groupValues[1]) }
                        .trim()
                    when {
                        hasPageBreak && text.isNotBlank() -> "\n\n$text"
                        hasPageBreak -> "\n"
                        else -> text
                    }
                }
                .filter { it.isNotEmpty() }
                .toList()
            return paragraphMatches.joinToString(separator = "\n").replace(Regex("\n{3,}"), "\n\n").trim()
        }

        private fun escapeXml(value: String): String {
            return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;")
        }

        private fun decodeXml(value: String): String {
            return value
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&apos;", "'")
                .replace("&amp;", "&")
        }
    }
}

data class DocxParagraph(
    val text: String,
    val pageBreakBefore: Boolean = false,
    val style: String? = null,
)

private fun ZipOutputStream.writeEntry(path: String, contents: String) {
    putNextEntry(ZipEntry(path))
    write(contents.toByteArray(Charsets.UTF_8))
    closeEntry()
}

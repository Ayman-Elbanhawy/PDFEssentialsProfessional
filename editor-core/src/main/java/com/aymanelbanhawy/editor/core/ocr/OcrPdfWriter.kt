package com.aymanelbanhawy.editor.core.ocr

import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.state.RenderingMode
import com.tom_roush.pdfbox.util.Matrix
import java.io.File
import kotlin.math.max
import kotlin.math.min

interface OcrPdfWriter {
    fun rewriteSearchableDocument(pdfFile: File, payload: OcrDocumentPayload)
}

class PdfBoxOcrPdfWriter : OcrPdfWriter {
    override fun rewriteSearchableDocument(pdfFile: File, payload: OcrDocumentPayload) {
        require(pdfFile.exists()) { "OCR target PDF does not exist: ${pdfFile.absolutePath}" }
        val baseFile = File(pdfFile.absolutePath + BASE_FILE_SUFFIX)
        if (!baseFile.exists()) {
            pdfFile.copyTo(baseFile, overwrite = true)
        }
        val tempFile = File(pdfFile.parentFile, "${pdfFile.name}.ocrrewrite.tmp")
        if (tempFile.exists()) {
            tempFile.delete()
        }
        PDDocument.load(baseFile).use { document ->
            payload.pages.sortedBy { it.pageIndex }.forEach { page ->
                if (page.pageIndex !in 0 until document.numberOfPages) return@forEach
                embedPage(document, page)
            }
            applyMetadata(document, payload)
            document.save(tempFile)
        }
        tempFile.copyTo(pdfFile, overwrite = true)
        tempFile.delete()
    }

    private fun embedPage(document: PDDocument, page: OcrPageContent) {
        val pdPage = document.getPage(page.pageIndex)
        val mediaBox = pdPage.mediaBox
        PDPageContentStream(document, pdPage, PDPageContentStream.AppendMode.APPEND, true, true).use { stream ->
            stream.setRenderingMode(RenderingMode.NEITHER)
            page.blocks.flatMap { block -> block.lines }.forEach { line ->
                val text = line.text.trim().replace(Regex("\\s+"), " ")
                if (text.isBlank()) return@forEach
                val width = mediaBox.width
                val height = mediaBox.height
                val x = line.bounds.left.coerceIn(0f, 1f) * width
                val y = height - (line.bounds.bottom.coerceIn(0f, 1f) * height)
                val fontSize = min(26f, max(7f, line.bounds.height.coerceAtLeast(0.012f) * height * 0.78f))
                stream.beginText()
                stream.setFont(PDType1Font.HELVETICA, fontSize)
                stream.setTextMatrix(Matrix.getTranslateInstance(x, y))
                stream.showText(text)
                stream.endText()
            }
        }
    }

    private fun applyMetadata(document: PDDocument, payload: OcrDocumentPayload) {
        val info = document.documentInformation
        val keywords = buildSet {
            info.keywords?.split(',')?.map { it.trim() }?.filter { it.isNotBlank() }?.forEach(::add)
            add("searchable")
            add("ocr")
            add("enterprise-pdf")
        }.joinToString(", ")
        info.keywords = keywords
        info.subject = listOf(info.subject, "Searchable OCR document").filter { !it.isNullOrBlank() }.joinToString(" | ")
        info.setCustomMetadataValue("EnterprisePdfOcrStatus", "completed")
        info.setCustomMetadataValue("EnterprisePdfOcrUpdatedAt", payload.updatedAtEpochMillis.toString())
        info.setCustomMetadataValue("EnterprisePdfOcrPageCount", payload.pages.size.toString())
        info.setCustomMetadataValue("EnterprisePdfOcrDeliveryMode", payload.settings.deliveryMode.name)
        if (payload.settings.languageHints.isNotEmpty()) {
            info.setCustomMetadataValue("EnterprisePdfOcrLanguageHints", payload.settings.languageHints.joinToString(","))
        }
        document.documentInformation = info
    }

    companion object {
        const val BASE_FILE_SUFFIX: String = ".ocrbase.pdf"
    }
}

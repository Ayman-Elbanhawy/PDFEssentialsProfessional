package com.aymanelbanhawy.editor.core.search

import com.aymanelbanhawy.editor.core.model.NormalizedRect
import com.aymanelbanhawy.editor.core.model.PdfDocumentRef
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.PDDocumentOutline
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import java.io.File
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext

class PdfBoxTextExtractionService : TextExtractionService {
    override suspend fun extract(documentRef: PdfDocumentRef): List<IndexedPageContent> {
        val pages = mutableListOf<IndexedPageContent>()
        extractInChunks(documentRef) { pages += it }
        return pages
    }

    suspend fun extractInChunks(
        documentRef: PdfDocumentRef,
        chunkSize: Int = 16,
        onChunk: suspend (List<IndexedPageContent>) -> Unit,
    ) {
        val file = File(documentRef.workingCopyPath)
        if (!file.exists()) return
        PDDocument.load(file).use { document ->
            val safeChunkSize = chunkSize.coerceIn(4, 32)
            var startIndex = 0
            while (startIndex < document.numberOfPages) {
                coroutineContext.ensureActive()
                val endExclusive = min(document.numberOfPages, startIndex + safeChunkSize)
                val chunk = (startIndex until endExclusive).map { pageIndex ->
                    val page = document.getPage(pageIndex)
                    val stripper = PageStripper(pageIndex, page.mediaBox.width, page.mediaBox.height)
                    stripper.startPage = pageIndex + 1
                    stripper.endPage = pageIndex + 1
                    stripper.getText(document)
                    IndexedPageContent(
                        pageIndex = pageIndex,
                        pageText = stripper.pageText.toString().trim(),
                        blocks = stripper.blocks,
                    )
                }
                onChunk(chunk)
                startIndex = endExclusive
            }
        }
    }

    override suspend fun extractOutline(documentRef: PdfDocumentRef): List<OutlineItem> {
        val file = File(documentRef.workingCopyPath)
        if (!file.exists()) return emptyList()
        return PDDocument.load(file).use { document ->
            val outline = document.documentCatalog?.documentOutline ?: return@use emptyList()
            readOutline(document, outline)
        }
    }

    private fun readOutline(document: PDDocument, outline: PDDocumentOutline): List<OutlineItem> {
        val items = mutableListOf<OutlineItem>()
        var cursor: PDOutlineItem? = outline.firstChild
        while (cursor != null) {
            items += readOutlineItem(document, cursor)
            cursor = cursor.nextSibling
        }
        return items
    }

    private fun readOutlineItem(document: PDDocument, item: PDOutlineItem): OutlineItem {
        val destination = item.findDestinationPage(document)
        val pageIndex = document.pages.indexOf(destination).coerceAtLeast(0)
        val children = mutableListOf<OutlineItem>()
        var child = item.firstChild
        while (child != null) {
            children += readOutlineItem(document, child)
            child = child.nextSibling
        }
        return OutlineItem(item.title ?: "Section ${pageIndex + 1}", pageIndex, children)
    }

    private class PageStripper(
        pageIndex: Int,
        private val pageWidth: Float,
        private val pageHeight: Float,
    ) : PDFTextStripper() {
        val blocks = mutableListOf<ExtractedTextBlock>()
        val pageText = StringBuilder()

        init {
            startPage = pageIndex + 1
            endPage = pageIndex + 1
            sortByPosition = true
        }

        override fun writeString(text: String?, textPositions: MutableList<TextPosition>?) {
            val safeText = text?.replace("\u0000", "")?.trim().orEmpty()
            if (safeText.isBlank()) return
            val positions = textPositions.orEmpty().filter { !it.unicode.isNullOrBlank() }
            if (positions.isEmpty()) return
            pageText.append(safeText).append('\n')
            val left = positions.minOf { it.xDirAdj } / pageWidth
            val right = positions.maxOf { it.xDirAdj + it.widthDirAdj } / pageWidth
            val topY = positions.maxOf { it.yDirAdj } / pageHeight
            val bottomY = positions.minOf { it.yDirAdj - it.heightDir } / pageHeight
            val top = (1f - topY).coerceIn(0f, 1f)
            val bottom = (1f - bottomY).coerceIn(0f, 1f)
            val normalized = NormalizedRect(
                left = left.coerceIn(0f, 1f),
                top = min(top, bottom).coerceIn(0f, 1f),
                right = right.coerceIn(0f, 1f),
                bottom = max(top, bottom).coerceIn(0f, 1f),
            ).normalized()
            blocks += ExtractedTextBlock(pageIndex = startPage - 1, text = safeText, bounds = normalized)
        }
    }
}

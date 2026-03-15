package com.aymanelbanhawy.editor.core.search

import com.aymanelbanhawy.editor.core.model.DocumentModel
import com.aymanelbanhawy.editor.core.model.NormalizedRect
import com.aymanelbanhawy.editor.core.model.PdfDocumentRef

interface TextExtractionService {
    suspend fun extract(documentRef: PdfDocumentRef): List<IndexedPageContent>
    suspend fun extractOutline(documentRef: PdfDocumentRef): List<OutlineItem>
}

interface DocumentSearchService {
    suspend fun ensureIndex(document: DocumentModel, forceRefresh: Boolean = false): List<IndexedPageContent>
    suspend fun search(document: DocumentModel, query: String): SearchResultSet
    suspend fun recentSearches(documentKey: String, limit: Int = 8): List<String>
    suspend fun outline(documentRef: PdfDocumentRef): List<OutlineItem>
    suspend fun selectionForBounds(document: DocumentModel, pageIndex: Int, bounds: NormalizedRect): TextSelectionPayload?
    suspend fun attachOcrResult(documentKey: String, pageIndex: Int, pageText: String, blocks: List<ExtractedTextBlock>)
}

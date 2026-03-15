package com.aymanelbanhawy.editor.core.search

import com.aymanelbanhawy.editor.core.data.RecentSearchDao
import com.aymanelbanhawy.editor.core.data.RecentSearchEntity
import com.aymanelbanhawy.editor.core.data.SearchIndexDao
import com.aymanelbanhawy.editor.core.data.SearchIndexEntity
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

class RoomSearchIndexStore(
    private val searchIndexDao: SearchIndexDao,
    private val recentSearchDao: RecentSearchDao,
    private val json: Json,
) {
    suspend fun indexedPages(documentKey: String): List<IndexedPageContent> {
        return searchIndexDao.indexForDocument(documentKey).map { entity ->
            val embeddedBlocks = decodeBlocks(entity.textBlocksJson)
            val ocrBlocks = decodeBlocks(entity.ocrBlocksJson)
            val preferredBlocks = if (ocrBlocks.isNotEmpty()) ocrBlocks else embeddedBlocks
            val preferredPageText = entity.ocrText?.takeIf { it.isNotBlank() } ?: entity.pageText
            IndexedPageContent(
                pageIndex = entity.pageIndex,
                pageText = preferredPageText.trim(),
                blocks = preferredBlocks.sortedBy { it.bounds.top },
                source = if (ocrBlocks.isNotEmpty()) SearchContentSource.Ocr else SearchContentSource.EmbeddedText,
            )
        }
    }

    suspend fun clearDocument(documentKey: String) {
        searchIndexDao.deleteForDocument(documentKey)
    }

    suspend fun saveEmbeddedIndex(documentKey: String, pages: List<IndexedPageContent>) {
        val now = System.currentTimeMillis()
        searchIndexDao.upsertAll(
            pages.map { page ->
                SearchIndexEntity(
                    documentKey = documentKey,
                    pageIndex = page.pageIndex,
                    pageText = page.pageText,
                    textBlocksJson = encodeBlocks(page.blocks.filter { it.source == SearchContentSource.EmbeddedText }),
                    updatedAtEpochMillis = now,
                )
            },
        )
    }

    suspend fun saveEmbeddedIndexChunk(documentKey: String, pages: List<IndexedPageContent>) {
        saveEmbeddedIndex(documentKey, pages)
    }

    suspend fun saveOcrIndex(documentKey: String, pageIndex: Int, pageText: String, blocks: List<ExtractedTextBlock>) {
        val now = System.currentTimeMillis()
        val current = searchIndexDao.indexForDocument(documentKey).firstOrNull { it.pageIndex == pageIndex }
        val ocrBlocks = encodeBlocks(blocks.map { it.copy(source = SearchContentSource.Ocr) })
        if (current == null) {
            searchIndexDao.upsertAll(
                listOf(
                    SearchIndexEntity(
                        documentKey = documentKey,
                        pageIndex = pageIndex,
                        pageText = "",
                        textBlocksJson = encodeBlocks(emptyList()),
                        ocrText = pageText,
                        ocrBlocksJson = ocrBlocks,
                        updatedAtEpochMillis = now,
                    ),
                ),
            )
        } else {
            searchIndexDao.updateOcrPayload(
                documentKey = documentKey,
                pageIndex = pageIndex,
                ocrText = pageText,
                ocrBlocksJson = ocrBlocks,
                updatedAtEpochMillis = now,
            )
        }
    }

    suspend fun rememberSearch(documentKey: String, query: String, keepCount: Int = 8) {
        recentSearchDao.insert(
            RecentSearchEntity(
                documentKey = documentKey,
                query = query,
                searchedAtEpochMillis = System.currentTimeMillis(),
            ),
        )
        recentSearchDao.trim(documentKey, keepCount)
    }

    suspend fun recentSearches(documentKey: String, limit: Int): List<String> {
        return recentSearchDao.recentForDocument(documentKey, limit)
            .map { it.query.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
    }

    private fun encodeBlocks(blocks: List<ExtractedTextBlock>): String {
        return json.encodeToString(ListSerializer(ExtractedTextBlock.serializer()), blocks)
    }

    private fun decodeBlocks(encoded: String?): List<ExtractedTextBlock> {
        if (encoded.isNullOrBlank()) return emptyList()
        return runCatching {
            json.decodeFromString(ListSerializer(ExtractedTextBlock.serializer()), encoded)
        }.getOrDefault(emptyList())
    }
}

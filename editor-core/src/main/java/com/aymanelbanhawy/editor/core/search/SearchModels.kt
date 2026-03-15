package com.aymanelbanhawy.editor.core.search

import com.aymanelbanhawy.editor.core.model.NormalizedRect
import kotlinx.serialization.Serializable

@Serializable
enum class SearchContentSource {
    EmbeddedText,
    Ocr,
}

@Serializable
data class ExtractedTextBlock(
    val pageIndex: Int,
    val text: String,
    val bounds: NormalizedRect,
    val source: SearchContentSource = SearchContentSource.EmbeddedText,
    val confidence: Float? = null,
    val languageTag: String? = null,
    val scriptTag: String? = null,
    val lineCount: Int = 1,
    val elementCount: Int = 0,
)

@Serializable
data class IndexedPageContent(
    val pageIndex: Int,
    val pageText: String,
    val blocks: List<ExtractedTextBlock>,
    val source: SearchContentSource = SearchContentSource.EmbeddedText,
)

@Serializable
data class SearchHit(
    val pageIndex: Int,
    val matchText: String,
    val preview: String,
    val bounds: NormalizedRect,
    val source: SearchContentSource,
)

@Serializable
data class SearchResultSet(
    val query: String = "",
    val hits: List<SearchHit> = emptyList(),
    val selectedHitIndex: Int = -1,
    val indexedPageCount: Int = 0,
) {
    val selectedHit: SearchHit?
        get() = hits.getOrNull(selectedHitIndex)
}

@Serializable
data class OutlineItem(
    val title: String,
    val pageIndex: Int,
    val children: List<OutlineItem> = emptyList(),
)

@Serializable
data class TextSelectionPayload(
    val pageIndex: Int,
    val text: String,
    val blocks: List<ExtractedTextBlock>,
)
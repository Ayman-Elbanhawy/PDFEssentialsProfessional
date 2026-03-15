package com.aymanelbanhawy.editor.core.organize

import kotlinx.serialization.Serializable

@Serializable
enum class SplitMode {
    PageRanges,
    OddPages,
    EvenPages,
    SelectedPages,
}

@Serializable
data class SplitRequest(
    val mode: SplitMode,
    val rangeExpression: String = "",
    val selectedPageIndexes: Set<Int> = emptySet(),
)

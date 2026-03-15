package com.aymanelbanhawy.editor.core.organize

import com.aymanelbanhawy.editor.core.model.DocumentModel

object SplitPlanner {
    fun plan(document: DocumentModel, request: SplitRequest): List<List<Int>> {
        val pageIndexes = when (request.mode) {
            SplitMode.PageRanges -> PageRangeParser.parse(request.rangeExpression, document.pageCount).map { it.toList() }
            SplitMode.OddPages -> listOf(document.pages.indices.filter { it % 2 == 0 })
            SplitMode.EvenPages -> listOf(document.pages.indices.filter { it % 2 == 1 })
            SplitMode.SelectedPages -> listOf(request.selectedPageIndexes.toList().sorted())
        }
        return pageIndexes.filter { it.isNotEmpty() }
    }
}

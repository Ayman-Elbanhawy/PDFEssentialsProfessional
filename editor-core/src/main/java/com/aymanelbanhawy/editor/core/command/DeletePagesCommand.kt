package com.aymanelbanhawy.editor.core.command

import com.aymanelbanhawy.editor.core.model.PageContentType
import com.aymanelbanhawy.editor.core.model.PageModel

class DeletePagesCommand(
    private val pageIndexes: Set<Int>,
) : EditorCommand {
    override val name: String = "Delete pages"
    private var removedPages: List<Pair<Int, PageModel>> = emptyList()

    override fun apply(state: EditableDocumentState): EditableDocumentState {
        removedPages = state.document.pages.mapIndexedNotNull { index, page -> if (index in pageIndexes) index to page else null }
        val pages = state.document.pages.filterIndexed { index, _ -> index !in pageIndexes }.renumbered()
        val selectionPage = state.selection.selectedPageIndex.coerceAtMost((pages.lastIndex).coerceAtLeast(0))
        return state.copy(document = state.document.copy(pages = pages), selection = state.selection.copy(selectedPageIndex = selectionPage))
    }

    override fun revert(state: EditableDocumentState): EditableDocumentState {
        val pages = state.document.pages.toMutableList()
        removedPages.sortedBy { it.first }.forEach { (index, page) -> pages.add(index.coerceAtMost(pages.size), page) }
        return state.copy(document = state.document.copy(pages = pages.renumbered()))
    }
}

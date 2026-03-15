package com.aymanelbanhawy.editor.core.command

import com.aymanelbanhawy.editor.core.model.PageModel

class DuplicatePagesCommand(
    private val pageIndexes: List<Int>,
) : EditorCommand {
    override val name: String = "Duplicate pages"
    private var insertedAt: List<Int> = emptyList()

    override fun apply(state: EditableDocumentState): EditableDocumentState {
        val pages = state.document.pages.toMutableList()
        val offsets = mutableListOf<Int>()
        pageIndexes.sorted().forEachIndexed { offset, sourceIndex ->
            val page = state.document.pages[sourceIndex]
            val insertIndex = (sourceIndex + 1 + offset).coerceAtMost(pages.size)
            pages.add(insertIndex, page.copy())
            offsets += insertIndex
        }
        insertedAt = offsets
        return state.copy(document = state.document.copy(pages = pages.renumbered()), selection = state.selection.copy(selectedPageIndex = insertedAt.lastOrNull() ?: state.selection.selectedPageIndex))
    }

    override fun revert(state: EditableDocumentState): EditableDocumentState {
        val pages = state.document.pages.toMutableList()
        insertedAt.sortedDescending().forEach { index -> if (index in pages.indices) pages.removeAt(index) }
        return state.copy(document = state.document.copy(pages = pages.renumbered()))
    }
}

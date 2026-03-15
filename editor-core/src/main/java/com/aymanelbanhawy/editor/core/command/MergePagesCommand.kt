package com.aymanelbanhawy.editor.core.command

import com.aymanelbanhawy.editor.core.model.PageModel

class MergePagesCommand(
    private val insertIndex: Int,
    private val importedPages: List<PageModel>,
) : EditorCommand {
    override val name: String = "Merge documents"

    override fun apply(state: EditableDocumentState): EditableDocumentState {
        val pages = state.document.pages.toMutableList().apply {
            addAll(insertIndex.coerceIn(0, size), importedPages)
        }.renumbered()
        return state.copy(document = state.document.copy(pages = pages), selection = state.selection.copy(selectedPageIndex = insertIndex.coerceIn(0, pages.lastIndex)))
    }

    override fun revert(state: EditableDocumentState): EditableDocumentState {
        val pages = state.document.pages.toMutableList().apply {
            repeat(importedPages.size) {
                val index = insertIndex.coerceIn(0, lastIndex)
                if (index in indices) removeAt(index)
            }
        }.renumbered()
        return state.copy(document = state.document.copy(pages = pages))
    }
}

package com.aymanelbanhawy.editor.core.command

import com.aymanelbanhawy.editor.core.model.PageModel

class ExtractPagesCommand(
    private val pageIndexes: Set<Int>,
) : EditorCommand {
    override val name: String = "Extract pages"
    private var originalPages: List<PageModel> = emptyList()

    override fun apply(state: EditableDocumentState): EditableDocumentState {
        originalPages = state.document.pages
        val pages = state.document.pages.filterIndexed { index, _ -> index in pageIndexes }.renumbered()
        return state.copy(document = state.document.copy(pages = pages), selection = state.selection.copy(selectedPageIndex = 0))
    }

    override fun revert(state: EditableDocumentState): EditableDocumentState {
        return state.copy(document = state.document.copy(pages = originalPages))
    }
}

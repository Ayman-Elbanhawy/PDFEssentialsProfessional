package com.aymanelbanhawy.editor.core.command

class ReorderPagesCommand(
    private val fromIndex: Int,
    private val toIndex: Int,
) : EditorCommand {
    override val name: String = "Reorder pages"

    override fun apply(state: EditableDocumentState): EditableDocumentState {
        if (fromIndex == toIndex) return state
        val updatedPages = state.document.pages.toMutableList().apply {
            val page = removeAt(fromIndex)
            add(toIndex, page)
        }.renumbered()
        return state.copy(
            document = state.document.copy(pages = updatedPages),
            selection = state.selection.copy(selectedPageIndex = toIndex),
        )
    }

    override fun revert(state: EditableDocumentState): EditableDocumentState {
        if (fromIndex == toIndex) return state
        val updatedPages = state.document.pages.toMutableList().apply {
            val page = removeAt(toIndex)
            add(fromIndex, page)
        }.renumbered()
        return state.copy(
            document = state.document.copy(pages = updatedPages),
            selection = state.selection.copy(selectedPageIndex = fromIndex),
        )
    }
}

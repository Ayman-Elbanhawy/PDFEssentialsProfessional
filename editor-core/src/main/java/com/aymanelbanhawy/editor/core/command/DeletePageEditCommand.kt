package com.aymanelbanhawy.editor.core.command

import com.aymanelbanhawy.editor.core.model.PageEditModel

class DeletePageEditCommand(
    private val pageIndex: Int,
    private val editObject: PageEditModel,
) : EditorCommand {
    override val name: String = "Delete ${editObject.type.name.lowercase()}"

    override fun apply(state: EditableDocumentState): EditableDocumentState {
        val pages = state.document.pages.replacePage(pageIndex) { it.withoutEditObject(editObject.id) }
        return state.copy(document = state.document.copy(pages = pages), selection = state.selection.copy(selectedEditId = null))
    }

    override fun revert(state: EditableDocumentState): EditableDocumentState {
        val pages = state.document.pages.replacePage(pageIndex) { it.withEditObject(editObject) }
        return state.copy(
            document = state.document.copy(pages = pages),
            selection = state.selection.copy(selectedPageIndex = pageIndex, selectedEditId = editObject.id),
        )
    }
}

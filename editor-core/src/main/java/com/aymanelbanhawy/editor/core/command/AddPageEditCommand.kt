package com.aymanelbanhawy.editor.core.command

import com.aymanelbanhawy.editor.core.model.PageEditModel

class AddPageEditCommand(
    private val pageIndex: Int,
    private val editObject: PageEditModel,
) : EditorCommand {
    override val name: String = "Add ${editObject.type.name.lowercase()}"

    override fun apply(state: EditableDocumentState): EditableDocumentState {
        val pages = state.document.pages.replacePage(pageIndex) { it.withEditObject(editObject) }
        return state.copy(
            document = state.document.copy(pages = pages),
            selection = state.selection.copy(selectedPageIndex = pageIndex, selectedAnnotationIds = emptySet(), selectedEditId = editObject.id),
        )
    }

    override fun revert(state: EditableDocumentState): EditableDocumentState {
        val pages = state.document.pages.replacePage(pageIndex) { it.withoutEditObject(editObject.id) }
        return state.copy(
            document = state.document.copy(pages = pages),
            selection = state.selection.copy(selectedEditId = null),
        )
    }
}

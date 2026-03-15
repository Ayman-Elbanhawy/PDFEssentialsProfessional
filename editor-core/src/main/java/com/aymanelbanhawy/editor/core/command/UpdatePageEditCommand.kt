package com.aymanelbanhawy.editor.core.command

import com.aymanelbanhawy.editor.core.model.PageEditModel

class UpdatePageEditCommand(
    private val before: PageEditModel,
    private val after: PageEditModel,
) : EditorCommand {
    override val name: String = "Update ${after.type.name.lowercase()}"

    override fun apply(state: EditableDocumentState): EditableDocumentState {
        val pages = state.document.pages.replacePage(after.pageIndex) { it.replaceEditObject(after) }
        return state.copy(
            document = state.document.copy(pages = pages),
            selection = state.selection.copy(selectedPageIndex = after.pageIndex, selectedEditId = after.id, selectedAnnotationIds = emptySet()),
        )
    }

    override fun revert(state: EditableDocumentState): EditableDocumentState {
        val pages = state.document.pages.replacePage(before.pageIndex) { it.replaceEditObject(before) }
        return state.copy(
            document = state.document.copy(pages = pages),
            selection = state.selection.copy(selectedPageIndex = before.pageIndex, selectedEditId = before.id, selectedAnnotationIds = emptySet()),
        )
    }
}

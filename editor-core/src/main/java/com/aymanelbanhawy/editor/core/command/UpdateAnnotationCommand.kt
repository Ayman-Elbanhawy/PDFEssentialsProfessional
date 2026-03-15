package com.aymanelbanhawy.editor.core.command

import com.aymanelbanhawy.editor.core.model.AnnotationModel

class UpdateAnnotationCommand(
    private val pageIndex: Int,
    private val before: AnnotationModel,
    private val after: AnnotationModel,
) : EditorCommand {
    override val name: String = "Update annotation"

    override fun apply(state: EditableDocumentState): EditableDocumentState {
        val pages = state.document.pages.replacePage(pageIndex) { it.replaceAnnotation(after) }
        return state.copy(
            document = state.document.copy(pages = pages),
            selection = state.selection.copy(selectedPageIndex = pageIndex, selectedAnnotationIds = setOf(after.id)),
        )
    }

    override fun revert(state: EditableDocumentState): EditableDocumentState {
        val pages = state.document.pages.replacePage(pageIndex) { it.replaceAnnotation(before) }
        return state.copy(
            document = state.document.copy(pages = pages),
            selection = state.selection.copy(selectedPageIndex = pageIndex, selectedAnnotationIds = setOf(before.id)),
        )
    }
}

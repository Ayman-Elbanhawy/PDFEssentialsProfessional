package com.aymanelbanhawy.editor.core.command

import com.aymanelbanhawy.editor.core.model.AnnotationModel

class DeleteAnnotationCommand(
    private val pageIndex: Int,
    private val annotation: AnnotationModel,
) : EditorCommand {
    override val name: String = "Delete annotation"

    override fun apply(state: EditableDocumentState): EditableDocumentState {
        val pages = state.document.pages.replacePage(pageIndex) { it.withoutAnnotation(annotation.id) }
        return state.copy(
            document = state.document.copy(pages = pages),
            selection = state.selection.copy(selectedAnnotationIds = emptySet()),
        )
    }

    override fun revert(state: EditableDocumentState): EditableDocumentState {
        val pages = state.document.pages.replacePage(pageIndex) { it.withAnnotation(annotation) }
        return state.copy(
            document = state.document.copy(pages = pages),
            selection = state.selection.copy(selectedAnnotationIds = setOf(annotation.id)),
        )
    }
}

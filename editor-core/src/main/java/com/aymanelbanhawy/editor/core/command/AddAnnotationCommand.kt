package com.aymanelbanhawy.editor.core.command

import com.aymanelbanhawy.editor.core.model.AnnotationModel

class AddAnnotationCommand(
    private val pageIndex: Int,
    private val annotation: AnnotationModel,
) : EditorCommand {
    override val name: String = "Add annotation"

    override fun apply(state: EditableDocumentState): EditableDocumentState {
        val pages = state.document.pages.replacePage(pageIndex) { it.withAnnotation(annotation) }
        return state.copy(
            document = state.document.copy(pages = pages),
            selection = state.selection.copy(selectedPageIndex = pageIndex, selectedAnnotationIds = setOf(annotation.id)),
        )
    }

    override fun revert(state: EditableDocumentState): EditableDocumentState {
        val pages = state.document.pages.replacePage(pageIndex) { it.withoutAnnotation(annotation.id) }
        return state.copy(
            document = state.document.copy(pages = pages),
            selection = state.selection.copy(selectedAnnotationIds = emptySet()),
        )
    }
}

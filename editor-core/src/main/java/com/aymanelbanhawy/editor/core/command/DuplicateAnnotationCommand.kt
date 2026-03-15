package com.aymanelbanhawy.editor.core.command

import com.aymanelbanhawy.editor.core.model.AnnotationModel

class DuplicateAnnotationCommand(
    private val pageIndex: Int,
    private val source: AnnotationModel,
    private val duplicate: AnnotationModel,
) : EditorCommand {
    override val name: String = "Duplicate annotation"

    override fun apply(state: EditableDocumentState): EditableDocumentState {
        val pages = state.document.pages.replacePage(pageIndex) { it.withAnnotation(duplicate) }
        return state.copy(
            document = state.document.copy(pages = pages),
            selection = state.selection.copy(selectedPageIndex = pageIndex, selectedAnnotationIds = setOf(duplicate.id)),
        )
    }

    override fun revert(state: EditableDocumentState): EditableDocumentState {
        val pages = state.document.pages.replacePage(pageIndex) { it.withoutAnnotation(duplicate.id) }
        return state.copy(
            document = state.document.copy(pages = pages),
            selection = state.selection.copy(selectedPageIndex = pageIndex, selectedAnnotationIds = setOf(source.id)),
        )
    }
}

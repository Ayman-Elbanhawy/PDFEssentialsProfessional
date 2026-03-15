package com.aymanelbanhawy.editor.core.command

import com.aymanelbanhawy.editor.core.model.PageContentType
import com.aymanelbanhawy.editor.core.model.PageModel

class InsertBlankPageCommand(
    private val insertIndex: Int,
    private val widthPoints: Float = 612f,
    private val heightPoints: Float = 792f,
) : EditorCommand {
    override val name: String = "Insert blank page"

    override fun apply(state: EditableDocumentState): EditableDocumentState {
        val pages = state.document.pages.toMutableList().apply {
            add(insertIndex.coerceIn(0, size), PageModel(index = insertIndex, label = "", contentType = PageContentType.Blank, sourceDocumentPath = "", sourcePageIndex = -1, widthPoints = widthPoints, heightPoints = heightPoints))
        }.renumbered()
        return state.copy(document = state.document.copy(pages = pages), selection = state.selection.copy(selectedPageIndex = insertIndex.coerceIn(0, pages.lastIndex)))
    }

    override fun revert(state: EditableDocumentState): EditableDocumentState {
        val targetIndex = insertIndex.coerceIn(0, state.document.pages.lastIndex)
        val pages = state.document.pages.toMutableList().apply { if (targetIndex in indices) removeAt(targetIndex) }.renumbered()
        return state.copy(document = state.document.copy(pages = pages))
    }
}

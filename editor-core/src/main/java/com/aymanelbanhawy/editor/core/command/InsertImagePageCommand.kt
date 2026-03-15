package com.aymanelbanhawy.editor.core.command

import com.aymanelbanhawy.editor.core.model.PageContentType
import com.aymanelbanhawy.editor.core.model.PageModel

class InsertImagePageCommand(
    private val insertIndex: Int,
    private val imagePath: String,
    private val widthPoints: Float,
    private val heightPoints: Float,
) : EditorCommand {
    override val name: String = "Insert image page"

    override fun apply(state: EditableDocumentState): EditableDocumentState {
        val page = PageModel(
            index = insertIndex,
            label = "",
            contentType = PageContentType.Image,
            sourceDocumentPath = "",
            sourcePageIndex = -1,
            insertedImagePath = imagePath,
            widthPoints = widthPoints,
            heightPoints = heightPoints,
        )
        val pages = state.document.pages.toMutableList().apply { add(insertIndex.coerceIn(0, size), page) }.renumbered()
        return state.copy(document = state.document.copy(pages = pages), selection = state.selection.copy(selectedPageIndex = insertIndex.coerceIn(0, pages.lastIndex)))
    }

    override fun revert(state: EditableDocumentState): EditableDocumentState {
        val targetIndex = insertIndex.coerceIn(0, state.document.pages.lastIndex)
        val pages = state.document.pages.toMutableList().apply { if (targetIndex in indices) removeAt(targetIndex) }.renumbered()
        return state.copy(document = state.document.copy(pages = pages))
    }
}

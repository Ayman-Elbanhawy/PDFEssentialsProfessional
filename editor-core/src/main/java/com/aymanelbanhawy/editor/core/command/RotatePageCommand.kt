package com.aymanelbanhawy.editor.core.command

class RotatePageCommand(
    private val pageIndex: Int,
    private val deltaDegrees: Int,
) : EditorCommand {
    override val name: String = "Rotate page"

    override fun apply(state: EditableDocumentState): EditableDocumentState {
        val pages = state.document.pages.replacePage(pageIndex) { page ->
            page.copy(rotationDegrees = (page.rotationDegrees + deltaDegrees).mod(360))
        }
        return state.copy(document = state.document.copy(pages = pages), selection = state.selection.copy(selectedPageIndex = pageIndex))
    }

    override fun revert(state: EditableDocumentState): EditableDocumentState {
        val pages = state.document.pages.replacePage(pageIndex) { page ->
            page.copy(rotationDegrees = (page.rotationDegrees - deltaDegrees).mod(360))
        }
        return state.copy(document = state.document.copy(pages = pages), selection = state.selection.copy(selectedPageIndex = pageIndex))
    }
}

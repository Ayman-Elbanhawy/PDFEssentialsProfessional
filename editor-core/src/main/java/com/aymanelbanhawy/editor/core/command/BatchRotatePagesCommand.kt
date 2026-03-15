package com.aymanelbanhawy.editor.core.command

class BatchRotatePagesCommand(
    private val pageIndexes: Set<Int>,
    private val deltaDegrees: Int,
) : EditorCommand {
    override val name: String = "Rotate pages"

    override fun apply(state: EditableDocumentState): EditableDocumentState {
        val pages = state.document.pages.mapIndexed { index, page ->
            if (index in pageIndexes) page.copy(rotationDegrees = (page.rotationDegrees + deltaDegrees).mod(360)) else page
        }
        return state.copy(document = state.document.copy(pages = pages))
    }

    override fun revert(state: EditableDocumentState): EditableDocumentState {
        val pages = state.document.pages.mapIndexed { index, page ->
            if (index in pageIndexes) page.copy(rotationDegrees = (page.rotationDegrees - deltaDegrees).mod(360)) else page
        }
        return state.copy(document = state.document.copy(pages = pages))
    }
}

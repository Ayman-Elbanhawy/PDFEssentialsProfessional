package com.aymanelbanhawy.editor.core.command

import com.aymanelbanhawy.editor.core.model.AnnotationModel
import com.aymanelbanhawy.editor.core.model.DocumentModel
import com.aymanelbanhawy.editor.core.model.PageEditModel
import com.aymanelbanhawy.editor.core.model.PageModel
import com.aymanelbanhawy.editor.core.model.SelectionModel
import com.aymanelbanhawy.editor.core.model.UndoRedoState
import com.aymanelbanhawy.editor.core.model.replaceEdit
import com.aymanelbanhawy.editor.core.model.withoutEdit

interface EditorCommand {
    val name: String
    fun apply(state: EditableDocumentState): EditableDocumentState
    fun revert(state: EditableDocumentState): EditableDocumentState
}

data class EditableDocumentState(
    val document: DocumentModel,
    val selection: SelectionModel,
)

class UndoRedoManager {
    private val undoStack = ArrayDeque<EditorCommand>()
    private val redoStack = ArrayDeque<EditorCommand>()
    private var lastCommandName: String? = null

    fun execute(command: EditorCommand, state: EditableDocumentState): EditableDocumentState {
        val updated = command.apply(state)
        undoStack.addLast(command)
        redoStack.clear()
        lastCommandName = command.name
        return updated
    }

    fun undo(state: EditableDocumentState): EditableDocumentState {
        val command = undoStack.removeLastOrNull() ?: return state
        val updated = command.revert(state)
        redoStack.addLast(command)
        lastCommandName = command.name
        return updated
    }

    fun redo(state: EditableDocumentState): EditableDocumentState {
        val command = redoStack.removeLastOrNull() ?: return state
        val updated = command.apply(state)
        undoStack.addLast(command)
        lastCommandName = command.name
        return updated
    }

    fun clear() {
        undoStack.clear()
        redoStack.clear()
        lastCommandName = null
    }

    fun snapshot(): UndoRedoState {
        return UndoRedoState(
            canUndo = undoStack.isNotEmpty(),
            canRedo = redoStack.isNotEmpty(),
            undoCount = undoStack.size,
            redoCount = redoStack.size,
            lastCommandName = lastCommandName,
        )
    }
}

internal fun List<PageModel>.replacePage(index: Int, transform: (PageModel) -> PageModel): List<PageModel> {
    return mapIndexed { pageIndex, pageModel ->
        if (pageIndex == index) transform(pageModel) else pageModel
    }
}

internal fun List<PageModel>.renumbered(): List<PageModel> = mapIndexed { index, page ->
    page.copy(
        index = index,
        label = "${index + 1}",
        annotations = page.annotations.map { it.withPage(index) },
        editObjects = page.editObjects.map { it.withPage(index) },
    )
}

internal fun PageModel.withAnnotation(annotation: AnnotationModel): PageModel = copy(annotations = annotations + annotation)

internal fun PageModel.withoutAnnotation(annotationId: String): PageModel = copy(annotations = annotations.filterNot { it.id == annotationId })

internal fun PageModel.replaceAnnotation(annotation: AnnotationModel): PageModel = copy(
    annotations = annotations.map { existing -> if (existing.id == annotation.id) annotation else existing },
)

internal fun PageModel.withEditObject(editObject: PageEditModel): PageModel = copy(editObjects = editObjects + editObject)

internal fun PageModel.withoutEditObject(editId: String): PageModel = copy(editObjects = editObjects.withoutEdit(editId))

internal fun PageModel.replaceEditObject(editObject: PageEditModel): PageModel = copy(editObjects = editObjects.replaceEdit(editObject))

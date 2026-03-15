package com.aymanelbanhawy.editor.core.session

import com.aymanelbanhawy.editor.core.command.EditorCommand
import com.aymanelbanhawy.editor.core.model.AnnotationExportMode
import com.aymanelbanhawy.editor.core.model.DocumentModel
import com.aymanelbanhawy.editor.core.model.EditorAction
import com.aymanelbanhawy.editor.core.model.EditorSessionState
import com.aymanelbanhawy.editor.core.model.OpenDocumentRequest
import com.aymanelbanhawy.editor.core.model.SelectionModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import java.io.File

sealed interface EditorSessionEvent {
    data class UserMessage(val message: String) : EditorSessionEvent
    data class ShareDocument(val document: DocumentModel) : EditorSessionEvent
    data class ShareText(val title: String, val text: String) : EditorSessionEvent
}

interface EditorSession {
    val state: StateFlow<EditorSessionState>
    val events: Flow<EditorSessionEvent>

    suspend fun openDocument(request: OpenDocumentRequest)
    fun onDocumentLoaded(pageCount: Int)
    fun onPageChanged(page: Int, pageCount: Int)
    fun updateSelection(selection: SelectionModel)
    fun onActionSelected(action: EditorAction)
    fun execute(command: EditorCommand)
    fun undo()
    fun redo()
    fun manualSave(exportMode: AnnotationExportMode = AnnotationExportMode.Editable)
    fun saveAs(destination: File, exportMode: AnnotationExportMode = AnnotationExportMode.Editable)
    suspend fun restoreDraft(sourceKey: String): Boolean
}

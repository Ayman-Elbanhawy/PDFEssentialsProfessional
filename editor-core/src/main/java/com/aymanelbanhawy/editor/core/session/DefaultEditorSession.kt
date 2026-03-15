package com.aymanelbanhawy.editor.core.session

import com.aymanelbanhawy.editor.core.command.EditableDocumentState
import com.aymanelbanhawy.editor.core.command.EditorCommand
import com.aymanelbanhawy.editor.core.command.UndoRedoManager
import com.aymanelbanhawy.editor.core.forms.FormValidationEngine
import com.aymanelbanhawy.editor.core.model.AnnotationExportMode
import com.aymanelbanhawy.editor.core.model.DirtyState
import com.aymanelbanhawy.editor.core.model.DocumentModel
import com.aymanelbanhawy.editor.core.model.DraftPayload
import com.aymanelbanhawy.editor.core.model.EditorAction
import com.aymanelbanhawy.editor.core.model.EditorSessionState
import com.aymanelbanhawy.editor.core.model.OpenDocumentRequest
import com.aymanelbanhawy.editor.core.model.PageModel
import com.aymanelbanhawy.editor.core.model.SelectionModel
import com.aymanelbanhawy.editor.core.repository.DocumentRepository
import com.aymanelbanhawy.editor.core.work.AutosaveScheduler
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

class DefaultEditorSession(
    private val repository: DocumentRepository,
    private val autosaveScheduler: AutosaveScheduler,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : EditorSession {

    private val scope = CoroutineScope(SupervisorJob() + ioDispatcher)
    private val mutableState = MutableStateFlow(EditorSessionState())
    private val mutableEvents = MutableSharedFlow<EditorSessionEvent>(extraBufferCapacity = 8, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    private val undoRedoManager = UndoRedoManager()

    override val state: StateFlow<EditorSessionState> = mutableState.asStateFlow()
    override val events: Flow<EditorSessionEvent> = mutableEvents.asSharedFlow()

    override suspend fun openDocument(request: OpenDocumentRequest) {
        mutableState.update { it.copy(isLoading = true) }
        undoRedoManager.clear()
        val document = repository.open(request)
        val restored = repository.restoreDraft(document.documentRef.sourceKey)
        mutableState.value = if (restored != null) {
            sessionState(restored.document, restored.selection, restored.undoRedoState, isLoading = false)
        } else {
            sessionState(document, SelectionModel(), undoRedoManager.snapshot(), isLoading = false)
        }
    }

    override fun onDocumentLoaded(pageCount: Int) {
        val current = state.value.document ?: return
        val currentPages = current.pages
        val pages = if (currentPages.size == pageCount && pageCount > 0) {
            currentPages
        } else {
            List(pageCount.coerceAtLeast(1)) { index ->
                currentPages.getOrNull(index)?.copy(index = index, label = "${index + 1}", annotations = currentPages.getOrNull(index)?.annotations.orEmpty().map { it.withPage(index) })
                    ?: PageModel(index = index, label = "${index + 1}")
            }
        }
        updateDocument(current.copy(pages = pages))
    }

    override fun onPageChanged(page: Int, pageCount: Int) {
        mutableState.update { it.copy(selection = it.selection.copy(selectedPageIndex = page)) }
        onDocumentLoaded(pageCount)
    }

    override fun updateSelection(selection: SelectionModel) {
        mutableState.update { it.copy(selection = selection) }
    }

    override fun onActionSelected(action: EditorAction) {
        scope.launch {
            val message = when (action) {
                EditorAction.Annotate -> "Annotation workspace is active."
                EditorAction.Organize -> "Page organization remains available from the session layer."
                EditorAction.Forms -> "Forms workspace is active."
                EditorAction.Sign -> "Signature workspace is active."
                EditorAction.Search -> "Search remains available while editing."
                EditorAction.Assistant -> "AI assistant workspace is active."
                EditorAction.Review -> "Review workspace is active."
                EditorAction.Activity -> "Activity workspace is active."
                EditorAction.Protect -> "Protection settings remain a repository concern."
                EditorAction.Settings -> "Settings and admin workspace is active."
                EditorAction.Diagnostics -> "Diagnostics workspace is active."
                EditorAction.Share -> null
            }
            if (action == EditorAction.Share) {
                state.value.document?.let { mutableEvents.emit(EditorSessionEvent.ShareDocument(it)) }
            } else if (message != null) {
                mutableEvents.emit(EditorSessionEvent.UserMessage(message))
            }
        }
    }

    override fun execute(command: EditorCommand) {
        val current = state.value.document ?: return
        val updated = undoRedoManager.execute(command, EditableDocumentState(current, state.value.selection))
        publishCommandState(updated.document, updated.selection, "Draft updated")
    }

    override fun undo() {
        val current = state.value.document ?: return
        val updated = undoRedoManager.undo(EditableDocumentState(current, state.value.selection))
        publishCommandState(updated.document, updated.selection, "Undo applied")
    }

    override fun redo() {
        val current = state.value.document ?: return
        val updated = undoRedoManager.redo(EditableDocumentState(current, state.value.selection))
        publishCommandState(updated.document, updated.selection, "Redo applied")
    }

    override fun manualSave(exportMode: AnnotationExportMode) {
        val current = state.value.document ?: return
        scope.launch {
            val saved = repository.save(current, exportMode)
            mutableState.value = sessionState(saved, state.value.selection, undoRedoManager.snapshot())
            mutableEvents.emit(EditorSessionEvent.UserMessage(saved.dirtyState.saveMessage))
        }
    }

    override fun saveAs(destination: File, exportMode: AnnotationExportMode) {
        val current = state.value.document ?: return
        scope.launch {
            val saved = repository.saveAs(current, destination, exportMode)
            mutableState.value = sessionState(saved, state.value.selection, undoRedoManager.snapshot())
            mutableEvents.emit(EditorSessionEvent.UserMessage(saved.dirtyState.saveMessage))
        }
    }

    override suspend fun restoreDraft(sourceKey: String): Boolean {
        val restored = repository.restoreDraft(sourceKey) ?: return false
        mutableState.value = sessionState(restored.document, restored.selection, restored.undoRedoState, isLoading = false)
        return true
    }

    private fun publishCommandState(document: DocumentModel, selection: SelectionModel, message: String) {
        val signaturesPresent = document.formDocument.fields.any { field ->
            val signature = field.value as? com.aymanelbanhawy.editor.core.forms.FormFieldValue.SignatureValue
            signature != null && signature.status != com.aymanelbanhawy.editor.core.forms.SignatureVerificationStatus.Unsigned
        }
        val updatedFormDocument = if (signaturesPresent) {
            document.formDocument.invalidateSignatures("Document modified after signing.")
        } else {
            document.formDocument
        }
        val dirtyDocument = document.copy(
            formDocument = updatedFormDocument,
            dirtyState = DirtyState(
                isDirty = true,
                lastModifiedAtEpochMillis = System.currentTimeMillis(),
                saveMessage = message,
            ),
        )
        val undoState = undoRedoManager.snapshot()
        mutableState.value = sessionState(dirtyDocument, selection, undoState)
        scope.launch {
            val payload = DraftPayload(
                document = dirtyDocument,
                selection = selection,
                undoCount = undoState.undoCount,
                redoCount = undoState.redoCount,
                lastCommandName = undoState.lastCommandName,
            )
            repository.persistDraft(payload, autosave = false)
            autosaveScheduler.enqueue(dirtyDocument, payload, undoState)
        }
    }

    private fun updateDocument(document: DocumentModel) {
        mutableState.value = sessionState(document, state.value.selection, state.value.undoRedoState, state.value.isLoading)
    }

    private fun sessionState(
        document: DocumentModel,
        selection: SelectionModel,
        undoRedoState: com.aymanelbanhawy.editor.core.model.UndoRedoState,
        isLoading: Boolean = false,
    ): EditorSessionState {
        return EditorSessionState(
            document = document,
            selection = selection,
            isLoading = isLoading,
            undoRedoState = undoRedoState,
            formValidationSummary = FormValidationEngine.validate(document.formDocument),
        )
    }
}



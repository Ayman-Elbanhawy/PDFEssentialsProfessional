package com.aymanelbanhawy.editor.core.session

import com.aymanelbanhawy.editor.core.model.DocumentModel
import com.aymanelbanhawy.editor.core.model.DraftPayload
import com.aymanelbanhawy.editor.core.model.UndoRedoState
import com.aymanelbanhawy.editor.core.work.AutosaveScheduler

class NoOpAutosaveScheduler : AutosaveScheduler {
    override fun enqueue(document: DocumentModel, payload: DraftPayload, undoRedoState: UndoRedoState) = Unit
}

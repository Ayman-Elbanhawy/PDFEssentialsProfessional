package com.aymanelbanhawy.editor.core.work

import android.content.Context
import androidx.work.WorkManager
import com.aymanelbanhawy.editor.core.model.DocumentModel
import com.aymanelbanhawy.editor.core.model.DraftPayload
import com.aymanelbanhawy.editor.core.model.UndoRedoState
import com.aymanelbanhawy.editor.core.repository.DocumentRepository

interface AutosaveScheduler {
    fun enqueue(document: DocumentModel, payload: DraftPayload, undoRedoState: UndoRedoState)
}

class WorkManagerAutosaveScheduler(
    private val repository: DocumentRepository,
    private val workManager: WorkManager,
) : AutosaveScheduler {
    override fun enqueue(document: DocumentModel, payload: DraftPayload, undoRedoState: UndoRedoState) {
        val tempFile = repository.createAutosaveTempFile(document.sessionId)
        tempFile.writeText(kotlinx.serialization.json.Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            classDiscriminator = "_type"
        }.encodeToString(DraftPayload.serializer(), payload))
        AutosaveDraftWorker.enqueue(
            workManager = workManager,
            sessionId = document.sessionId,
            sourceKey = document.documentRef.sourceKey,
            displayName = document.documentRef.displayName,
            draftTempPath = tempFile.absolutePath,
            workingCopyPath = document.documentRef.workingCopyPath,
            undoCount = undoRedoState.undoCount,
            redoCount = undoRedoState.redoCount,
            lastCommandName = undoRedoState.lastCommandName,
        )
    }
}


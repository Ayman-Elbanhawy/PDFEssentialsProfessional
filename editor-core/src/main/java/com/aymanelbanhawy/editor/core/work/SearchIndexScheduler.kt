package com.aymanelbanhawy.editor.core.work

import androidx.work.WorkManager
import com.aymanelbanhawy.editor.core.model.DocumentModel
import com.aymanelbanhawy.editor.core.search.IndexingPolicy

class SearchIndexScheduler(
    private val workManager: WorkManager,
    private val indexingPolicy: IndexingPolicy = IndexingPolicy(),
) {
    fun scheduleIfNeeded(document: DocumentModel) {
        if (!indexingPolicy.shouldIndexInBackground(document)) return
        DocumentIndexWorker.enqueue(
            workManager = workManager,
            documentKey = document.documentRef.sourceKey,
            workingCopyPath = document.documentRef.workingCopyPath,
            displayName = document.documentRef.displayName,
        )
    }
}

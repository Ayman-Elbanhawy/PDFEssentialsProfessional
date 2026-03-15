package com.aymanelbanhawy.editor.core.work

import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.aymanelbanhawy.editor.core.collaboration.CollaborationSyncScheduler
import java.util.concurrent.TimeUnit

class WorkManagerCollaborationSyncScheduler(
    private val workManager: WorkManager,
) : CollaborationSyncScheduler {
    override fun schedule(documentKey: String) {
        val request = OneTimeWorkRequestBuilder<CollaborationSyncWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .setInputData(workDataOf(CollaborationSyncWorker.KEY_DOCUMENT_KEY to documentKey))
            .setBackoffCriteria(androidx.work.BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
            .build()
        workManager.enqueueUniqueWork("collaboration-sync-$documentKey", ExistingWorkPolicy.REPLACE, request)
    }
}

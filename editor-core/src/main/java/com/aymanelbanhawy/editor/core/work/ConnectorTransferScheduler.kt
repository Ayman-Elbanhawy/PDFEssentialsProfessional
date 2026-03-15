package com.aymanelbanhawy.editor.core.work

import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

class ConnectorTransferScheduler(
    private val workManager: WorkManager,
) {
    fun enqueue() {
        val request = OneTimeWorkRequestBuilder<ConnectorTransferWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        workManager.enqueueUniqueWork(UNIQUE_NAME, ExistingWorkPolicy.KEEP, request)
    }

    companion object {
        private const val UNIQUE_NAME = "connector-transfer-sync"
    }
}

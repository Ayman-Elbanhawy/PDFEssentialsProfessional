package com.aymanelbanhawy.editor.core.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.aymanelbanhawy.editor.core.EditorCoreContainer

class TelemetryUploadWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val repository = EditorCoreContainer(applicationContext).enterpriseAdminRepository
        val uploaded = repository.flushTelemetry()
        return if (uploaded > 0 || runAttemptCount == 0) {
            Result.success()
        } else {
            Result.retry()
        }
    }
}

package com.aymanelbanhawy.editor.core.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.io.File
import java.util.concurrent.TimeUnit

class CleanupExportsWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val exportsDir = File(applicationContext.filesDir, EXPORTS_DIR)
        if (!exportsDir.exists()) return Result.success()
        val cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(7)
        exportsDir.listFiles().orEmpty()
            .filter { it.isFile && it.lastModified() < cutoff }
            .forEach { it.delete() }
        return Result.success()
    }

    companion object {
        private const val EXPORTS_DIR = "exports"
        private const val WORK_NAME = "cleanup-editor-exports"

        fun enqueue(workManager: WorkManager) {
            val request = PeriodicWorkRequestBuilder<CleanupExportsWorker>(1, TimeUnit.DAYS).build()
            workManager.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
        }
    }
}

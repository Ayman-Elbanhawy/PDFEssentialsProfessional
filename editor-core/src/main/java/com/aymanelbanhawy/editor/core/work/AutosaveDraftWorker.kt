package com.aymanelbanhawy.editor.core.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import androidx.room.Room
import com.aymanelbanhawy.editor.core.data.DraftEntity
import com.aymanelbanhawy.editor.core.data.EditHistoryMetadataEntity
import com.aymanelbanhawy.editor.core.data.PdfWorkspaceDatabase
import java.io.File

class AutosaveDraftWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val sessionId = inputData.getString(KEY_SESSION_ID) ?: return Result.failure()
        val sourceKey = inputData.getString(KEY_SOURCE_KEY) ?: return Result.failure()
        val displayName = inputData.getString(KEY_DISPLAY_NAME) ?: return Result.failure()
        val draftTempPath = inputData.getString(KEY_DRAFT_TEMP_PATH) ?: return Result.failure()
        val workingCopyPath = inputData.getString(KEY_WORKING_COPY_PATH) ?: return Result.failure()
        val undoCount = inputData.getInt(KEY_UNDO_COUNT, 0)
        val redoCount = inputData.getInt(KEY_REDO_COUNT, 0)
        val lastCommandName = inputData.getString(KEY_LAST_COMMAND_NAME)

        val tempFile = File(draftTempPath)
        if (!tempFile.exists()) return Result.retry()
        val stableDir = File(applicationContext.filesDir, DRAFTS_DIR).apply { mkdirs() }
        val stableFile = File(stableDir, "$sessionId.json")
        tempFile.copyTo(stableFile, overwrite = true)
        tempFile.delete()

        val database = Room.databaseBuilder(applicationContext, PdfWorkspaceDatabase::class.java, DB_NAME).build()
        try {
            val now = System.currentTimeMillis()
            database.draftDao().upsert(
                DraftEntity(
                    sessionId = sessionId,
                    sourceKey = sourceKey,
                    displayName = displayName,
                    draftFilePath = stableFile.absolutePath,
                    workingCopyPath = workingCopyPath,
                    isAutosave = true,
                    updatedAtEpochMillis = now,
                ),
            )
            database.editHistoryMetadataDao().upsert(
                EditHistoryMetadataEntity(
                    sessionId = sessionId,
                    sourceKey = sourceKey,
                    undoCount = undoCount,
                    redoCount = redoCount,
                    lastCommandName = lastCommandName,
                    updatedAtEpochMillis = now,
                ),
            )
        } finally {
            database.close()
        }
        return Result.success()
    }

    companion object {
        private const val DB_NAME = "enterprise-editor.db"
        private const val DRAFTS_DIR = "drafts"
        private const val KEY_SESSION_ID = "session_id"
        private const val KEY_SOURCE_KEY = "source_key"
        private const val KEY_DISPLAY_NAME = "display_name"
        private const val KEY_DRAFT_TEMP_PATH = "draft_temp_path"
        private const val KEY_WORKING_COPY_PATH = "working_copy_path"
        private const val KEY_UNDO_COUNT = "undo_count"
        private const val KEY_REDO_COUNT = "redo_count"
        private const val KEY_LAST_COMMAND_NAME = "last_command_name"

        fun enqueue(
            workManager: WorkManager,
            sessionId: String,
            sourceKey: String,
            displayName: String,
            draftTempPath: String,
            workingCopyPath: String,
            undoCount: Int,
            redoCount: Int,
            lastCommandName: String?,
        ) {
            val input: Data = workDataOf(
                KEY_SESSION_ID to sessionId,
                KEY_SOURCE_KEY to sourceKey,
                KEY_DISPLAY_NAME to displayName,
                KEY_DRAFT_TEMP_PATH to draftTempPath,
                KEY_WORKING_COPY_PATH to workingCopyPath,
                KEY_UNDO_COUNT to undoCount,
                KEY_REDO_COUNT to redoCount,
                KEY_LAST_COMMAND_NAME to lastCommandName,
            )
            val request = OneTimeWorkRequestBuilder<AutosaveDraftWorker>().setInputData(input).build()
            workManager.enqueueUniqueWork("autosave-$sessionId", ExistingWorkPolicy.REPLACE, request)
        }
    }
}

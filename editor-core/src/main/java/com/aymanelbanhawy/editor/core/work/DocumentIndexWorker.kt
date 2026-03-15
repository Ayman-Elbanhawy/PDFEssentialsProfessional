package com.aymanelbanhawy.editor.core.work

import android.content.Context
import androidx.core.net.toUri
import androidx.room.Room
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.aymanelbanhawy.editor.core.data.PdfWorkspaceDatabase
import com.aymanelbanhawy.editor.core.model.DocumentSourceType
import com.aymanelbanhawy.editor.core.model.PdfDocumentRef
import com.aymanelbanhawy.editor.core.runtime.DefaultRuntimeDiagnosticsRepository
import com.aymanelbanhawy.editor.core.runtime.RuntimeEventCategory
import com.aymanelbanhawy.editor.core.runtime.RuntimeLogLevel
import com.aymanelbanhawy.editor.core.search.PdfBoxTextExtractionService
import com.aymanelbanhawy.editor.core.search.RoomSearchIndexStore
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.json.Json
import java.io.File
import kotlin.coroutines.coroutineContext

class DocumentIndexWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val documentKey = inputData.getString(KEY_DOCUMENT_KEY) ?: return Result.failure()
        val workingCopyPath = inputData.getString(KEY_WORKING_COPY_PATH) ?: return Result.failure()
        val displayName = inputData.getString(KEY_DISPLAY_NAME) ?: return Result.failure()
        val file = File(workingCopyPath)
        if (!file.exists()) return Result.retry()

        val database = Room.databaseBuilder(applicationContext, PdfWorkspaceDatabase::class.java, DB_NAME).build()
        return try {
            val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; classDiscriminator = "_type" }
            val diagnostics = DefaultRuntimeDiagnosticsRepository(
                context = applicationContext,
                breadcrumbDao = database.runtimeBreadcrumbDao(),
                draftDao = database.draftDao(),
                ocrJobDao = database.ocrJobDao(),
                syncQueueDao = database.syncQueueDao(),
                connectorTransferJobDao = database.connectorTransferJobDao(),
                connectorAccountDao = database.connectorAccountDao(),
                json = json,
            )
            val store = RoomSearchIndexStore(database.searchIndexDao(), database.recentSearchDao(), json)
            val extractor = PdfBoxTextExtractionService()
            val documentRef = PdfDocumentRef(
                uriString = file.toUri().toString(),
                displayName = displayName,
                sourceType = DocumentSourceType.File,
                sourceKey = documentKey,
                workingCopyPath = workingCopyPath,
            )
            store.clearDocument(documentKey)
            extractor.extractInChunks(documentRef, chunkSize = 12) { chunk ->
                coroutineContext.ensureActive()
                store.saveEmbeddedIndexChunk(documentKey, chunk)
            }
            diagnostics.recordBreadcrumb(RuntimeEventCategory.Indexing, RuntimeLogLevel.Info, "background_index_complete", "Background indexing completed.", mapOf("documentKey" to documentKey))
            Result.success()
        } catch (_: Throwable) {
            Result.retry()
        } finally {
            database.close()
        }
    }

    companion object {
        private const val DB_NAME = "enterprise-editor.db"
        private const val KEY_DOCUMENT_KEY = "document_key"
        private const val KEY_WORKING_COPY_PATH = "working_copy_path"
        private const val KEY_DISPLAY_NAME = "display_name"

        fun enqueue(workManager: WorkManager, documentKey: String, workingCopyPath: String, displayName: String) {
            val request = OneTimeWorkRequestBuilder<DocumentIndexWorker>()
                .setInputData(
                    workDataOf(
                        KEY_DOCUMENT_KEY to documentKey,
                        KEY_WORKING_COPY_PATH to workingCopyPath,
                        KEY_DISPLAY_NAME to displayName,
                    ),
                )
                .build()
            workManager.enqueueUniqueWork("index-$documentKey", ExistingWorkPolicy.REPLACE, request)
        }
    }
}

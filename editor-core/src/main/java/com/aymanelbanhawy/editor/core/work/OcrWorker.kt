package com.aymanelbanhawy.editor.core.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.aymanelbanhawy.editor.core.data.createEditorCoreDatabase
import com.aymanelbanhawy.editor.core.ocr.MlKitOcrEngine
import com.aymanelbanhawy.editor.core.ocr.OcrEngineDiagnostics
import com.aymanelbanhawy.editor.core.ocr.OcrEngineException
import com.aymanelbanhawy.editor.core.ocr.OcrJobPipeline
import com.aymanelbanhawy.editor.core.ocr.OcrPageRequest
import com.aymanelbanhawy.editor.core.ocr.OcrSessionStore
import com.aymanelbanhawy.editor.core.ocr.PdfBoxOcrPdfWriter
import com.aymanelbanhawy.editor.core.runtime.DefaultRuntimeDiagnosticsRepository
import com.aymanelbanhawy.editor.core.runtime.RuntimeEventCategory
import com.aymanelbanhawy.editor.core.runtime.RuntimeLogLevel
import com.aymanelbanhawy.editor.core.search.DefaultDocumentSearchService
import com.aymanelbanhawy.editor.core.search.PdfBoxTextExtractionService
import com.aymanelbanhawy.editor.core.search.RoomSearchIndexStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.json.Json
import kotlin.coroutines.coroutineContext

class OcrWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val documentKey = inputData.getString(KEY_DOCUMENT_KEY) ?: return Result.failure()
        val database = createEditorCoreDatabase(applicationContext)
        return try {
            val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; classDiscriminator = "_type" }
            val ocrSessionStore = OcrSessionStore(json, database.ocrJobDao())
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
            val searchService = DefaultDocumentSearchService(store, PdfBoxTextExtractionService(), ocrSessionStore, diagnostics)
            val pipeline = OcrJobPipeline(
                ocrJobDao = database.ocrJobDao(),
                ocrSettingsDao = database.ocrSettingsDao(),
                searchService = searchService,
                workManager = WorkManager.getInstance(applicationContext),
                json = json,
                ocrSessionStore = ocrSessionStore,
                ocrPdfWriter = PdfBoxOcrPdfWriter(),
                diagnosticsRepository = diagnostics,
            )
            val engine = MlKitOcrEngine(applicationContext)
            val settings = pipeline.loadSettings()
            val jobs = pipeline.pendingWork(documentKey, limit = settings.pagesPerWorkerBatch, staleAfterMillis = 2 * 60 * 1000L)
            if (jobs.isEmpty()) return Result.success()
            var shouldRetry = false
            jobs.forEach { job ->
                coroutineContext.ensureActive()
                if (isStopped) {
                    pipeline.pause(documentKey)
                    return Result.retry()
                }
                val running = pipeline.markRunning(job)
                runCatching {
                    pipeline.updateProgress(running, 20)
                    val result = engine.recognize(
                        OcrPageRequest(
                            imagePath = running.imagePath,
                            pageIndex = running.pageIndex,
                            outputDirectoryPath = applicationContext.cacheDir.resolve("ocr-preprocessed/${running.documentKey.hashCode()}").absolutePath,
                            settings = settings,
                        ),
                    )
                    pipeline.updateProgress(running, 85, result.preprocessedImagePath)
                    pipeline.complete(running, result, settings)
                }.onFailure { error ->
                    if (error is CancellationException || isStopped) {
                        pipeline.pause(documentKey, running.pageIndex)
                        shouldRetry = true
                        return@onFailure
                    }
                    val diagnosticsPayload = when (error) {
                        is OcrEngineException -> error.diagnostics
                        else -> OcrEngineDiagnostics(
                            code = "ocr-worker-failure",
                            message = error.message ?: "OCR failed.",
                            retryable = false,
                        )
                    }
                    pipeline.fail(running, diagnosticsPayload)
                    shouldRetry = shouldRetry || diagnosticsPayload.retryable
                }
            }
            diagnostics.recordBreadcrumb(RuntimeEventCategory.Ocr, RuntimeLogLevel.Info, "ocr_worker_complete", "OCR worker processed ${jobs.size} pages.", mapOf("documentKey" to documentKey))
            if (shouldRetry) Result.retry() else Result.success()
        } catch (_: CancellationException) {
            Result.retry()
        } catch (_: Throwable) {
            Result.retry()
        } finally {
            database.close()
        }
    }

    companion object {
        private const val KEY_DOCUMENT_KEY = "document_key"

        fun enqueue(workManager: WorkManager, documentKey: String) {
            val request = OneTimeWorkRequestBuilder<OcrWorker>()
                .setInputData(workDataOf(KEY_DOCUMENT_KEY to documentKey))
                .build()
            workManager.enqueueUniqueWork("ocr-$documentKey", ExistingWorkPolicy.REPLACE, request)
        }
    }
}


package com.aymanelbanhawy.editor.core.ocr

import androidx.work.WorkManager
import com.aymanelbanhawy.editor.core.data.OcrJobDao
import com.aymanelbanhawy.editor.core.data.OcrJobEntity
import com.aymanelbanhawy.editor.core.data.OcrSettingsDao
import com.aymanelbanhawy.editor.core.data.OcrSettingsEntity
import com.aymanelbanhawy.editor.core.runtime.RuntimeDiagnosticsRepository
import com.aymanelbanhawy.editor.core.runtime.RuntimeEventCategory
import com.aymanelbanhawy.editor.core.runtime.RuntimeLogLevel
import com.aymanelbanhawy.editor.core.search.DocumentSearchService
import com.aymanelbanhawy.editor.core.search.ExtractedTextBlock
import com.aymanelbanhawy.editor.core.work.OcrWorker
import java.io.File
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

class OcrJobPipeline(
    private val ocrJobDao: OcrJobDao,
    private val ocrSettingsDao: OcrSettingsDao,
    private val searchService: DocumentSearchService,
    private val workManager: WorkManager,
    private val json: Json,
    private val ocrSessionStore: OcrSessionStore,
    private val ocrPdfWriter: OcrPdfWriter? = null,
    private val diagnosticsRepository: RuntimeDiagnosticsRepository? = null,
) {
    suspend fun enqueue(documentKey: String, jobs: List<QueuedOcrPage>, settings: OcrSettingsModel) {
        saveSettings(settings)
        val now = System.currentTimeMillis()
        ocrJobDao.upsertAll(
            jobs.map { job ->
                val existing = ocrJobDao.jobForPage(documentKey, job.pageIndex)
                (existing ?: OcrJobEntity(
                    id = buildJobId(documentKey, job.pageIndex),
                    documentKey = documentKey,
                    pageIndex = job.pageIndex,
                    imagePath = job.imagePath,
                    status = OcrJobStatus.Pending.name,
                    createdAtEpochMillis = now,
                    updatedAtEpochMillis = now,
                )).copy(
                    imagePath = job.imagePath,
                    status = OcrJobStatus.Pending.name,
                    progressPercent = 0,
                    attemptCount = 0,
                    maxAttempts = settings.maxRetryCount.coerceAtLeast(1),
                    resultText = null,
                    resultBlocksJson = null,
                    resultPageJson = null,
                    diagnosticsJson = null,
                    settingsJson = json.encodeToString(OcrSettingsModel.serializer(), settings),
                    preprocessedImagePath = null,
                    errorMessage = null,
                    startedAtEpochMillis = null,
                    completedAtEpochMillis = null,
                    updatedAtEpochMillis = now,
                )
            },
        )
        diagnosticsRepository?.recordBreadcrumb(
            category = RuntimeEventCategory.Ocr,
            level = RuntimeLogLevel.Info,
            eventName = "ocr_jobs_enqueued",
            message = "Queued ${jobs.size} OCR jobs.",
            metadata = mapOf("documentKey" to documentKey),
        )
        OcrWorker.enqueue(workManager, documentKey)
    }

    fun observe(documentKey: String): Flow<List<OcrJobSummary>> {
        return ocrJobDao.observeJobsForDocument(documentKey).map { entities -> entities.map(::toSummary) }
    }

    suspend fun loadSettings(): OcrSettingsModel {
        return ocrSettingsDao.get()?.let { json.decodeFromString(OcrSettingsModel.serializer(), it.payloadJson) }
            ?: OcrSettingsModel()
    }

    suspend fun saveSettings(settings: OcrSettingsModel) {
        ocrSettingsDao.upsert(
            OcrSettingsEntity(
                payloadJson = json.encodeToString(OcrSettingsModel.serializer(), settings),
                updatedAtEpochMillis = System.currentTimeMillis(),
            ),
        )
    }

    suspend fun pendingWork(documentKey: String, limit: Int, staleAfterMillis: Long): List<OcrJobEntity> {
        val settings = loadSettings()
        return ocrJobDao.pendingOrResumable(
            documentKey,
            System.currentTimeMillis() - staleAfterMillis,
            limit.coerceAtMost(settings.pagesPerWorkerBatch.coerceIn(1, 24)),
        )
    }

    suspend fun markRunning(job: OcrJobEntity, progressPercent: Int = 8, preprocessedImagePath: String? = null): OcrJobEntity {
        val updated = job.copy(
            status = OcrJobStatus.Running.name,
            progressPercent = progressPercent,
            preprocessedImagePath = preprocessedImagePath ?: job.preprocessedImagePath,
            attemptCount = if (job.status == OcrJobStatus.Running.name) job.attemptCount else job.attemptCount + 1,
            startedAtEpochMillis = job.startedAtEpochMillis ?: System.currentTimeMillis(),
            updatedAtEpochMillis = System.currentTimeMillis(),
        )
        ocrJobDao.upsert(updated)
        return updated
    }

    suspend fun updateProgress(job: OcrJobEntity, progressPercent: Int, preprocessedImagePath: String? = null) {
        ocrJobDao.upsert(
            job.copy(
                progressPercent = progressPercent.coerceIn(0, 99),
                preprocessedImagePath = preprocessedImagePath ?: job.preprocessedImagePath,
                updatedAtEpochMillis = System.currentTimeMillis(),
            ),
        )
    }

    suspend fun complete(job: OcrJobEntity, result: OcrEngineResult, settings: OcrSettingsModel) {
        val payload = ocrSessionStore.mergePage(job.documentKey, settings, result.page)
        ocrPdfWriter?.rewriteSearchableDocument(File(job.documentKey), payload)
        ocrSessionStore.persistPayload(payload)
        val now = System.currentTimeMillis()
        val blocks = result.blocks
        ocrJobDao.upsert(
            job.copy(
                status = OcrJobStatus.Completed.name,
                progressPercent = 100,
                resultText = result.pageText,
                resultBlocksJson = json.encodeToString(ListSerializer(ExtractedTextBlock.serializer()), blocks),
                resultPageJson = json.encodeToString(OcrPageContent.serializer(), result.page),
                diagnosticsJson = result.diagnostics?.let { json.encodeToString(OcrEngineDiagnostics.serializer(), it) },
                settingsJson = json.encodeToString(OcrSettingsModel.serializer(), settings),
                preprocessedImagePath = result.preprocessedImagePath,
                errorMessage = result.diagnostics?.message,
                completedAtEpochMillis = now,
                updatedAtEpochMillis = now,
            ),
        )
        searchService.attachOcrResult(job.documentKey, job.pageIndex, result.pageText, blocks)
    }

    suspend fun fail(job: OcrJobEntity, diagnostics: OcrEngineDiagnostics) {
        val canRetry = diagnostics.retryable && job.attemptCount < job.maxAttempts
        ocrJobDao.upsert(
            job.copy(
                status = if (canRetry) OcrJobStatus.RetryScheduled.name else OcrJobStatus.Failed.name,
                progressPercent = 0,
                diagnosticsJson = json.encodeToString(OcrEngineDiagnostics.serializer(), diagnostics),
                errorMessage = diagnostics.message,
                completedAtEpochMillis = if (canRetry) null else System.currentTimeMillis(),
                updatedAtEpochMillis = System.currentTimeMillis(),
            ),
        )
        diagnosticsRepository?.recordBreadcrumb(
            category = RuntimeEventCategory.Ocr,
            level = if (canRetry) RuntimeLogLevel.Warn else RuntimeLogLevel.Error,
            eventName = if (canRetry) "ocr_retry_scheduled" else "ocr_failed",
            message = diagnostics.message,
            metadata = mapOf("documentKey" to job.documentKey, "pageIndex" to job.pageIndex.toString()),
        )
        if (canRetry) {
            OcrWorker.enqueue(workManager, job.documentKey)
        }
    }

    suspend fun pause(documentKey: String, pageIndex: Int? = null) {
        val jobs = ocrJobDao.jobsForDocument(documentKey).filter {
            (pageIndex == null || it.pageIndex == pageIndex) && it.status in setOf(
                OcrJobStatus.Pending.name,
                OcrJobStatus.RetryScheduled.name,
                OcrJobStatus.Running.name,
            )
        }
        if (jobs.isEmpty()) return
        val now = System.currentTimeMillis()
        ocrJobDao.upsertAll(
            jobs.map {
                it.copy(
                    status = OcrJobStatus.Paused.name,
                    updatedAtEpochMillis = now,
                )
            },
        )
    }

    suspend fun resume(documentKey: String, pageIndex: Int? = null) {
        val settings = loadSettings()
        val jobs = ocrJobDao.jobsForDocument(documentKey)
            .filter { pageIndex == null || it.pageIndex == pageIndex }
            .filter { it.status == OcrJobStatus.Paused.name || it.status == OcrJobStatus.Failed.name }
            .map {
                it.copy(
                    status = OcrJobStatus.Pending.name,
                    progressPercent = 0,
                    maxAttempts = settings.maxRetryCount.coerceAtLeast(1),
                    diagnosticsJson = null,
                    errorMessage = null,
                    completedAtEpochMillis = null,
                    updatedAtEpochMillis = System.currentTimeMillis(),
                )
            }
        if (jobs.isEmpty()) return
        ocrJobDao.upsertAll(jobs)
        OcrWorker.enqueue(workManager, documentKey)
    }

    suspend fun rerun(documentKey: String, pageIndex: Int? = null) {
        val settings = loadSettings()
        val jobs = ocrJobDao.jobsForDocument(documentKey)
            .filter { pageIndex == null || it.pageIndex == pageIndex }
            .map {
                it.copy(
                    status = OcrJobStatus.Pending.name,
                    progressPercent = 0,
                    attemptCount = 0,
                    maxAttempts = settings.maxRetryCount.coerceAtLeast(1),
                    resultText = null,
                    resultBlocksJson = null,
                    resultPageJson = null,
                    diagnosticsJson = null,
                    preprocessedImagePath = null,
                    errorMessage = null,
                    startedAtEpochMillis = null,
                    completedAtEpochMillis = null,
                    settingsJson = json.encodeToString(OcrSettingsModel.serializer(), settings),
                    updatedAtEpochMillis = System.currentTimeMillis(),
                )
            }
        if (jobs.isNotEmpty()) {
            ocrJobDao.upsertAll(jobs)
            OcrWorker.enqueue(workManager, documentKey)
        }
    }

    private fun toSummary(entity: OcrJobEntity): OcrJobSummary {
        val diagnostics = entity.diagnosticsJson?.let {
            runCatching { json.decodeFromString(OcrEngineDiagnostics.serializer(), it) }.getOrNull()
        }
        return OcrJobSummary(
            id = entity.id,
            documentKey = entity.documentKey,
            pageIndex = entity.pageIndex,
            status = runCatching { OcrJobStatus.valueOf(entity.status) }.getOrDefault(OcrJobStatus.Pending),
            progressPercent = entity.progressPercent,
            attemptCount = entity.attemptCount,
            maxAttempts = entity.maxAttempts,
            imagePath = entity.imagePath,
            preprocessedImagePath = entity.preprocessedImagePath,
            pageText = entity.resultText,
            errorMessage = entity.errorMessage,
            diagnostics = buildList {
                diagnostics?.message?.let(::add)
                addAll(diagnostics?.details.orEmpty())
            },
            updatedAtEpochMillis = entity.updatedAtEpochMillis,
        )
    }

    companion object {
        fun buildJobId(documentKey: String, pageIndex: Int): String = "$documentKey::$pageIndex"
    }
}

data class QueuedOcrPage(
    val pageIndex: Int,
    val imagePath: String,
)

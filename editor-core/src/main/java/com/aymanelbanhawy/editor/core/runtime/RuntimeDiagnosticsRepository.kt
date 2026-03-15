package com.aymanelbanhawy.editor.core.runtime

import android.content.Context
import com.aymanelbanhawy.editor.core.data.ConnectorAccountDao
import com.aymanelbanhawy.editor.core.data.ConnectorTransferJobDao
import com.aymanelbanhawy.editor.core.data.DraftDao
import com.aymanelbanhawy.editor.core.data.OcrJobDao
import com.aymanelbanhawy.editor.core.data.RuntimeBreadcrumbDao
import com.aymanelbanhawy.editor.core.data.RuntimeBreadcrumbEntity
import com.aymanelbanhawy.editor.core.data.SyncQueueDao
import com.aymanelbanhawy.editor.core.migration.AppMigrationReport
import com.aymanelbanhawy.editor.core.model.DocumentModel
import com.aymanelbanhawy.editor.core.ocr.OcrJobStatus
import java.io.File
import java.util.UUID
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

interface RuntimeDiagnosticsRepository {
    suspend fun recordBreadcrumb(
        category: RuntimeEventCategory,
        level: RuntimeLogLevel,
        eventName: String,
        message: String,
        metadata: Map<String, String> = emptyMap(),
    )

    suspend fun recordAppStart(elapsedMillis: Long, repairResult: StartupRepairResult)
    suspend fun recordDocumentOpen(document: DocumentModel, elapsedMillis: Long)
    suspend fun recordSave(document: DocumentModel, elapsedMillis: Long, success: Boolean, fileSizeBytes: Long, checksumSha256: String?)
    suspend fun recordProviderHealth(name: String, healthy: Boolean, detail: String)
    suspend fun captureSnapshot(currentDocument: DocumentModel?): RuntimeDiagnosticsSnapshot
    suspend fun runStartupRepair(): StartupRepairResult
}

class DefaultRuntimeDiagnosticsRepository(
    private val context: Context,
    private val breadcrumbDao: RuntimeBreadcrumbDao,
    private val draftDao: DraftDao,
    private val ocrJobDao: OcrJobDao,
    private val syncQueueDao: SyncQueueDao,
    private val connectorTransferJobDao: ConnectorTransferJobDao,
    private val connectorAccountDao: ConnectorAccountDao,
    private val json: Json,
) : RuntimeDiagnosticsRepository {

    private var lastStartupElapsedMillis: Long = 0
    private var lastDocumentOpenElapsedMillis: Long = 0
    private var lastSaveElapsedMillis: Long = 0
    private val providerHealth = linkedMapOf<String, ProviderHealthModel>()

    override suspend fun recordBreadcrumb(
        category: RuntimeEventCategory,
        level: RuntimeLogLevel,
        eventName: String,
        message: String,
        metadata: Map<String, String>,
    ) {
        val safeMessage = redact(message)
        breadcrumbDao.upsert(
            RuntimeBreadcrumbEntity(
                id = UUID.randomUUID().toString(),
                category = category.name,
                level = level.name,
                eventName = eventName,
                message = safeMessage,
                metadataJson = json.encodeToString(MapSerializer, metadata.mapValues { redact(it.value) }),
                createdAtEpochMillis = System.currentTimeMillis(),
            ),
        )
        breadcrumbDao.trimOlderThan(System.currentTimeMillis() - MAX_BREADCRUMB_AGE_MILLIS)
    }

    override suspend fun recordAppStart(elapsedMillis: Long, repairResult: StartupRepairResult) {
        lastStartupElapsedMillis = elapsedMillis
        recordBreadcrumb(
            category = RuntimeEventCategory.Startup,
            level = RuntimeLogLevel.Info,
            eventName = "app_start",
            message = "Application startup completed in ${elapsedMillis}ms.",
            metadata = mapOf(
                "repairedDrafts" to repairResult.repairedDraftCount.toString(),
                "recoveredSaves" to repairResult.recoveredSaveCount.toString(),
                "resumedOcr" to repairResult.resumedOcrCount.toString(),
                "resumedSync" to repairResult.resumedSyncCount.toString(),
                "quarantinedCompatibilityFiles" to repairResult.quarantinedCompatibilityFileCount.toString(),
            ),
        )
    }

    override suspend fun recordDocumentOpen(document: DocumentModel, elapsedMillis: Long) {
        lastDocumentOpenElapsedMillis = elapsedMillis
        recordBreadcrumb(
            category = RuntimeEventCategory.DocumentOpen,
            level = RuntimeLogLevel.Info,
            eventName = "document_open",
            message = "Opened document ${document.documentRef.displayName} in ${elapsedMillis}ms.",
            metadata = mapOf(
                "pageCount" to document.pageCount.toString(),
                "sourceType" to document.documentRef.sourceType.name,
                "sessionId" to document.sessionId,
            ),
        )
    }

    override suspend fun recordSave(document: DocumentModel, elapsedMillis: Long, success: Boolean, fileSizeBytes: Long, checksumSha256: String?) {
        lastSaveElapsedMillis = elapsedMillis
        recordBreadcrumb(
            category = RuntimeEventCategory.Save,
            level = if (success) RuntimeLogLevel.Info else RuntimeLogLevel.Error,
            eventName = if (success) "document_save" else "document_save_failed",
            message = if (success) "Saved ${document.documentRef.displayName} in ${elapsedMillis}ms." else "Save failed for ${document.documentRef.displayName} after ${elapsedMillis}ms.",
            metadata = buildMap {
                put("pageCount", document.pageCount.toString())
                put("fileSizeBytes", fileSizeBytes.toString())
                checksumSha256?.let { put("checksumSha256", it) }
            },
        )
    }

    override suspend fun recordProviderHealth(name: String, healthy: Boolean, detail: String) {
        providerHealth[name] = ProviderHealthModel(name, if (healthy) "Healthy" else "Unavailable", redact(detail))
        recordBreadcrumb(
            category = RuntimeEventCategory.Provider,
            level = if (healthy) RuntimeLogLevel.Debug else RuntimeLogLevel.Warn,
            eventName = "provider_health",
            message = "$name is ${if (healthy) "healthy" else "unavailable"}.",
            metadata = mapOf("detail" to detail),
        )
    }

    override suspend fun captureSnapshot(currentDocument: DocumentModel?): RuntimeDiagnosticsSnapshot {
        val recent = breadcrumbDao.recent(40).map(::toModel)
        val failures = breadcrumbDao.recentFailures(15).map(::toModel)
        val allOcrJobs = ocrJobDao.all()
        val allConnectorJobs = connectorTransferJobDao.all()
        val connectorAccounts = connectorAccountDao.all()
        val syncOps = syncQueueDao.eligibleAll(Long.MAX_VALUE)
        return RuntimeDiagnosticsSnapshot(
            startupElapsedMillis = lastStartupElapsedMillis,
            lastDocumentOpenElapsedMillis = lastDocumentOpenElapsedMillis,
            lastSaveElapsedMillis = lastSaveElapsedMillis,
            cache = CacheDiagnosticsModel(
                thumbnailFileCount = countFiles(File(context.cacheDir, "organize-thumbnails")),
                thumbnailBytes = totalBytes(File(context.cacheDir, "organize-thumbnails")),
                connectorCacheFileCount = countFiles(File(context.cacheDir, "connector-cache")),
                connectorCacheBytes = totalBytes(File(context.cacheDir, "connector-cache")),
                exportCacheFileCount = countFiles(File(context.cacheDir, "exports")),
                exportCacheBytes = totalBytes(File(context.cacheDir, "exports")),
            ),
            queues = QueueDiagnosticsModel(
                pendingOcrJobs = allOcrJobs.count { it.status == OcrJobStatus.Pending.name || it.status == OcrJobStatus.RetryScheduled.name },
                runningOcrJobs = allOcrJobs.count { it.status == OcrJobStatus.Running.name },
                failedOcrJobs = allOcrJobs.count { it.status == OcrJobStatus.Failed.name },
                pendingSyncOperations = syncOps.size,
                connectorTransferJobs = allConnectorJobs.count { it.status != "Completed" },
            ),
            migration = loadMigrationDiagnostics(),
            connectors = ConnectorDiagnosticsModel(
                configuredAccountCount = connectorAccounts.size,
                activeTransferCount = allConnectorJobs.count { it.status == "Running" || it.status == "Pending" || it.status == "Paused" },
                failedTransferCount = allConnectorJobs.count { it.status == "Failed" },
                enterpriseManagedCount = connectorAccounts.count { it.isEnterpriseManaged },
                supportsResumableTransferCount = connectorAccounts.count { it.supportsResumableTransfer },
            ),
            providerHealth = buildList {
                addAll(providerHealth.values)
                connectorAccounts.forEach { account ->
                    val activeJob = allConnectorJobs.firstOrNull { it.connectorAccountId == account.id }
                    add(
                        ProviderHealthModel(
                            name = account.displayName,
                            status = activeJob?.status ?: "Idle",
                            detail = activeJob?.lastError ?: account.baseUrl,
                        ),
                    )
                }
            }.distinctBy { it.name },
            recentBreadcrumbs = recent,
            recentFailures = failures,
            failureSummaries = failures
                .groupBy { it.category }
                .map { (category, events) ->
                    FailureSummaryModel(
                        category = category,
                        count = events.size,
                        latestMessage = events.maxByOrNull { it.createdAtEpochMillis }?.message.orEmpty(),
                    )
                }
                .sortedByDescending { it.count },
        )
    }

    override suspend fun runStartupRepair(): StartupRepairResult {
        var repairedDrafts = 0
        var recoveredSaves = 0
        var resumedOcr = 0
        var resumedSync = 0
        var quarantinedCompatibilityFiles = 0

        listOf(
            File(context.filesDir, "working-documents"),
            File(context.filesDir, "drafts"),
            File(context.cacheDir, "exports"),
        ).forEach { directory ->
            directory.mkdirs()
            directory.walkTopDown().filter { it.isFile }.forEach { file ->
                when {
                    file.name.endsWith(".saving.lock") -> {
                        val target = File(file.absolutePath.removeSuffix(".saving.lock"))
                        val backup = File(target.absolutePath + ".bak")
                        if (!target.exists() && backup.exists()) {
                            backup.copyTo(target, overwrite = true)
                            recoveredSaves += 1
                        }
                        file.delete()
                    }
                    file.name.endsWith(".tmp") -> {
                        val target = File(file.parentFile, file.name.removeSuffix(".tmp"))
                        if (!target.exists() && file.length() > 0L) {
                            file.copyTo(target, overwrite = true)
                            recoveredSaves += 1
                        }
                        file.delete()
                    }
                    file.name.endsWith(".mutations.json") || file.name.endsWith(LEGACY_PAGE_EDIT_SUFFIX) -> {
                        if (!isJsonFileHealthy(file)) {
                            quarantine(file)
                            quarantinedCompatibilityFiles += 1
                        }
                    }
                }
            }
        }

        File(context.filesDir, "drafts").walkTopDown().filter { it.isFile && it.extension == "json" }.forEach { draftFile ->
            if (!isJsonFileHealthy(draftFile)) {
                quarantine(draftFile)
                repairedDrafts += 1
            }
        }

        resumedOcr = ocrJobDao.all().count { it.status == OcrJobStatus.Pending.name || it.status == OcrJobStatus.RetryScheduled.name || it.status == OcrJobStatus.Running.name }
        resumedSync = syncQueueDao.eligibleAll(System.currentTimeMillis()).size

        return StartupRepairResult(
            repairedDraftCount = repairedDrafts,
            recoveredSaveCount = recoveredSaves,
            resumedOcrCount = resumedOcr,
            resumedSyncCount = resumedSync,
            quarantinedCompatibilityFileCount = quarantinedCompatibilityFiles,
        )
    }

    private fun loadMigrationDiagnostics(): MigrationDiagnosticsModel {
        val reportFile = File(File(context.filesDir, "migration-reports"), "latest-app-migration.json")
        if (!reportFile.exists()) return MigrationDiagnosticsModel()
        val report = runCatching {
            json.decodeFromString(AppMigrationReport.serializer(), reportFile.readText())
        }.getOrNull() ?: return MigrationDiagnosticsModel(reportPath = reportFile.absolutePath, notice = "Migration report is unreadable.")
        return MigrationDiagnosticsModel(
            completedAtEpochMillis = report.core.completedAtEpochMillis,
            successCount = report.core.successCount,
            failureCount = report.core.failureCount,
            compatibilityModeUsed = report.core.compatibilityModeUsed,
            aiStateNormalizedCount = report.aiStateNormalizedCount,
            notice = report.core.notice ?: report.aiStateMessage.takeIf { it.isNotBlank() },
            reportPath = reportFile.absolutePath,
        )
    }

    private fun isJsonFileHealthy(file: File): Boolean {
        return runCatching {
            val text = file.readText()
            text.isNotBlank() && (text.trimStart().startsWith("{") || text.trimStart().startsWith("["))
        }.getOrDefault(false)
    }

    private fun quarantine(file: File) {
        val target = File(file.parentFile, file.name + ".corrupt")
        file.copyTo(target, overwrite = true)
        file.delete()
    }

    private fun countFiles(root: File): Int = if (!root.exists()) 0 else root.walkTopDown().count { it.isFile }
    private fun totalBytes(root: File): Long = if (!root.exists()) 0L else root.walkTopDown().filter { it.isFile }.sumOf { it.length() }

    private fun redact(value: String): String {
        return value
            .replace(Regex("[A-Za-z]:\\\\[^\\s]+"), "<path>")
            .replace(Regex("/[^\\s]+"), "<path>")
            .replace(Regex("[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}", RegexOption.IGNORE_CASE), "<email>")
    }

    private fun toModel(entity: RuntimeBreadcrumbEntity): RuntimeBreadcrumbModel {
        val metadata = runCatching { json.decodeFromString(MapSerializer, entity.metadataJson) }.getOrDefault(emptyMap())
        return RuntimeBreadcrumbModel(
            id = entity.id,
            category = runCatching { RuntimeEventCategory.valueOf(entity.category) }.getOrDefault(RuntimeEventCategory.Failure),
            level = runCatching { RuntimeLogLevel.valueOf(entity.level) }.getOrDefault(RuntimeLogLevel.Info),
            eventName = entity.eventName,
            message = entity.message,
            metadata = metadata,
            createdAtEpochMillis = entity.createdAtEpochMillis,
        )
    }

    private companion object {
        private const val MAX_BREADCRUMB_AGE_MILLIS = 14L * 24L * 60L * 60L * 1000L
        private val MapSerializer = kotlinx.serialization.builtins.MapSerializer(String.serializer(), String.serializer())
        private const val LEGACY_PAGE_EDIT_SUFFIX = ".page" + "edits.json"
    }
}




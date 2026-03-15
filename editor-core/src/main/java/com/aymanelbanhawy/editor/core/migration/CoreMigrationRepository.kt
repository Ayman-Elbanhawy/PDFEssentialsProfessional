package com.aymanelbanhawy.editor.core.migration

import android.content.Context
import com.aymanelbanhawy.editor.core.data.DraftDao
import com.aymanelbanhawy.editor.core.data.OcrJobDao
import com.aymanelbanhawy.editor.core.data.SearchIndexDao
import com.aymanelbanhawy.editor.core.data.SyncQueueDao
import com.aymanelbanhawy.editor.core.model.DraftPayload
import com.aymanelbanhawy.editor.core.ocr.OcrJobStatus
import com.aymanelbanhawy.editor.core.runtime.RuntimeDiagnosticsRepository
import com.aymanelbanhawy.editor.core.runtime.RuntimeEventCategory
import com.aymanelbanhawy.editor.core.runtime.RuntimeLogLevel
import java.io.File
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

interface CoreMigrationRepository {
    suspend fun runUpgradePass(): CoreMigrationReport
    suspend fun latestReport(): CoreMigrationReport?
    suspend fun exportLatestReport(destination: File): File
    suspend fun importReport(source: File): CoreMigrationReport
}

class DefaultCoreMigrationRepository(
    private val context: Context,
    private val draftDao: DraftDao,
    private val ocrJobDao: OcrJobDao,
    private val searchIndexDao: SearchIndexDao,
    private val syncQueueDao: SyncQueueDao,
    private val diagnosticsRepository: RuntimeDiagnosticsRepository,
    private val json: Json,
    private val legacyEditCompatibilityBridge: LegacyEditCompatibilityBridge = FileLegacyEditCompatibilityBridge(json),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : CoreMigrationRepository {

    override suspend fun runUpgradePass(): CoreMigrationReport = withContext(ioDispatcher) {
        val startedAt = System.currentTimeMillis()
        val backupDirectory = createBackupDirectory(startedAt)
        val steps = mutableListOf<MigrationStepReport>()
        var compatibilityModeUsed = false

        backupLegacyFiles(backupDirectory)
        steps += MigrationStepReport(
            id = "backup",
            title = "Backup local upgrade inputs",
            status = MigrationStepStatus.Applied,
            message = "Copied local databases, drafts, and legacy compatibility files into ${backupDirectory.absolutePath}.",
        )

        val draftStep = normalizeDrafts()
        steps += draftStep

        val compatibilityStep = migrateLegacyPageEditCompatibilityFiles()
        steps += compatibilityStep
        compatibilityModeUsed = compatibilityModeUsed || compatibilityStep.migratedCount > 0

        val ocrStep = resumeInterruptedOcrJobs()
        steps += ocrStep

        val syncStep = resumeInterruptedSyncQueue()
        steps += syncStep

        val indexStep = writeIndexRefreshMarkers()
        steps += indexStep

        val report = CoreMigrationReport(
            engineVersion = ENGINE_VERSION,
            startedAtEpochMillis = startedAt,
            completedAtEpochMillis = System.currentTimeMillis(),
            compatibilityModeUsed = compatibilityModeUsed,
            backupDirectoryPath = backupDirectory.absolutePath,
            steps = steps,
        )
        writeState(report)
        writeReport(report)
        diagnosticsRepository.recordBreadcrumb(
            category = RuntimeEventCategory.Recovery,
            level = if (report.failureCount == 0) RuntimeLogLevel.Info else RuntimeLogLevel.Warn,
            eventName = "migration_pass_completed",
            message = report.notice ?: "Migration pass completed.",
            metadata = mapOf(
                "engineVersion" to report.engineVersion.toString(),
                "successCount" to report.successCount.toString(),
                "failureCount" to report.failureCount.toString(),
            ),
        )
        report
    }

    override suspend fun latestReport(): CoreMigrationReport? = withContext(ioDispatcher) {
        val file = latestReportFile()
        if (!file.exists()) return@withContext null
        runCatching { json.decodeFromString(CoreMigrationReport.serializer(), file.readText()) }.getOrNull()
    }

    override suspend fun exportLatestReport(destination: File): File = withContext(ioDispatcher) {
        val reportFile = latestReportFile()
        require(reportFile.exists()) { "No migration report is available." }
        destination.parentFile?.mkdirs()
        reportFile.copyTo(destination, overwrite = true)
        destination
    }

    override suspend fun importReport(source: File): CoreMigrationReport = withContext(ioDispatcher) {
        val imported = json.decodeFromString(CoreMigrationReport.serializer(), source.readText())
        writeReport(imported)
        imported
    }

    private suspend fun normalizeDrafts(): MigrationStepReport {
        val draftsDir = File(context.filesDir, "drafts")
        if (!draftsDir.exists()) {
            return MigrationStepReport(
                id = "drafts",
                title = "Normalize existing drafts",
                status = MigrationStepStatus.Skipped,
                message = "No persisted drafts were found.",
            )
        }
        var migrated = 0
        var repaired = 0
        draftsDir.walkTopDown().filter { it.isFile && it.extension.equals("json", ignoreCase = true) }.forEach { file ->
            val originalText = file.readText()
            val payload = runCatching { json.decodeFromString(DraftPayload.serializer(), originalText) }.getOrNull()
            if (payload == null) {
                file.copyTo(File(file.absolutePath + ".migration-corrupt"), overwrite = true)
                repaired += 1
                return@forEach
            }
            val normalized = json.encodeToString(DraftPayload.serializer(), payload)
            if (normalized != originalText) {
                File(file.absolutePath + ".bak").writeText(originalText)
                file.writeText(normalized)
                migrated += 1
            }
        }
        return MigrationStepReport(
            id = "drafts",
            title = "Normalize existing drafts",
            status = when {
                repaired > 0 -> MigrationStepStatus.Repaired
                migrated > 0 -> MigrationStepStatus.Applied
                else -> MigrationStepStatus.Skipped
            },
            severity = if (repaired > 0) MigrationSeverity.Warning else MigrationSeverity.Info,
            message = when {
                repaired > 0 -> "Normalized $migrated draft(s) and quarantined $repaired unreadable draft file(s)."
                migrated > 0 -> "Normalized $migrated draft file(s) to the current schema."
                else -> "Draft files already matched the current schema."
            },
            migratedCount = migrated,
            repairedCount = repaired,
        )
    }

    private suspend fun migrateLegacyPageEditCompatibilityFiles(): MigrationStepReport {
        val workingDir = File(context.filesDir, "working-documents")
        if (!workingDir.exists()) {
            return MigrationStepReport(
                id = "pageedits",
                title = "Upgrade legacy page edit compatibility files",
                status = MigrationStepStatus.Skipped,
                message = "No working documents were found.",
            )
        }
        var migrated = 0
        var repaired = 0
        workingDir.walkTopDown().filter { it.isFile && it.name.endsWith(FileLegacyEditCompatibilityBridge.legacySuffix()) }.forEach { legacyFile ->
            val upgraded = runCatching { legacyEditCompatibilityBridge.migrateLegacyArtifact(legacyFile) }.getOrNull()
            when {
                upgraded != null -> migrated += 1
                legacyFile.exists() -> {
                    legacyFile.copyTo(File(legacyFile.absolutePath + ".migration-corrupt"), overwrite = true)
                    repaired += 1
                }
            }
        }
        return MigrationStepReport(
            id = "pageedits",
            title = "Upgrade legacy page edit compatibility files",
            status = when {
                repaired > 0 -> MigrationStepStatus.Repaired
                migrated > 0 -> MigrationStepStatus.Applied
                else -> MigrationStepStatus.Skipped
            },
            severity = if (repaired > 0) MigrationSeverity.Warning else MigrationSeverity.Info,
            message = when {
                repaired > 0 -> "Created current mutation sessions for $migrated legacy page-edit file(s) and quarantined $repaired unreadable file(s)."
                migrated > 0 -> "Upgraded $migrated legacy page-edit compatibility file(s)."
                else -> "No legacy page-edit compatibility files required migration."
            },
            migratedCount = migrated,
            repairedCount = repaired,
        )
    }

    private suspend fun resumeInterruptedOcrJobs(): MigrationStepReport {
        val now = System.currentTimeMillis()
        val staleBefore = now - STALE_OPERATION_AGE_MILLIS
        val jobs = ocrJobDao.all()
        val updated = jobs.mapNotNull { job ->
            when {
                job.status == OcrJobStatus.Running.name && job.updatedAtEpochMillis < staleBefore -> {
                    job.copy(
                        status = OcrJobStatus.RetryScheduled.name,
                        errorMessage = "Rescheduled automatically after upgrade.",
                        updatedAtEpochMillis = now,
                    )
                }
                else -> null
            }
        }
        if (updated.isNotEmpty()) {
            ocrJobDao.upsertAll(updated)
        }
        return MigrationStepReport(
            id = "ocr",
            title = "Resume interrupted OCR jobs",
            status = if (updated.isNotEmpty()) MigrationStepStatus.Repaired else MigrationStepStatus.Skipped,
            message = if (updated.isNotEmpty()) "Rescheduled ${updated.size} stale OCR job(s)." else "No stale OCR jobs required rescheduling.",
            repairedCount = updated.size,
        )
    }

    private suspend fun resumeInterruptedSyncQueue(): MigrationStepReport {
        val now = System.currentTimeMillis()
        val operations = syncQueueDao.all()
        val updated = operations.mapNotNull { operation ->
            if (operation.state == "Syncing") {
                operation.copy(
                    state = "Pending",
                    updatedAtEpochMillis = now,
                    nextAttemptAtEpochMillis = now,
                    lastError = "Returned to pending during upgrade reconciliation.",
                )
            } else {
                null
            }
        }
        if (updated.isNotEmpty()) {
            for (operation in updated) {
                syncQueueDao.upsert(operation)
            }
        }
        return MigrationStepReport(
            id = "sync",
            title = "Resume interrupted collaboration sync",
            status = if (updated.isNotEmpty()) MigrationStepStatus.Repaired else MigrationStepStatus.Skipped,
            message = if (updated.isNotEmpty()) "Returned ${updated.size} sync operation(s) to the pending queue." else "No interrupted sync operations required migration.",
            repairedCount = updated.size,
        )
    }

    private suspend fun writeIndexRefreshMarkers(): MigrationStepReport {
        val documents = searchIndexDao.documentKeys()
        val markerDir = File(context.cacheDir, "search-index-invalidations").apply { mkdirs() }
        documents.forEach { documentKey ->
            File(markerDir, "${documentKey.hashCode()}.marker").writeText(System.currentTimeMillis().toString())
        }
        return MigrationStepReport(
            id = "search-index",
            title = "Queue index refresh markers",
            status = if (documents.isNotEmpty()) MigrationStepStatus.Applied else MigrationStepStatus.Skipped,
            message = if (documents.isNotEmpty()) "Marked ${documents.size} indexed document(s) for refresh after upgrade." else "No indexed documents required refresh.",
            migratedCount = documents.size,
        )
    }

    private fun backupLegacyFiles(backupDirectory: File) {
        copyIfExists(File(context.getDatabasePath("enterprise-editor.db").absolutePath), backupDirectory)
        copyIfExists(File(context.getDatabasePath("ai-assistant.db").absolutePath), backupDirectory)
        copyDirectoryIfExists(File(context.filesDir, "drafts"), File(backupDirectory, "drafts"))
        copyDirectoryIfExists(File(context.filesDir, "working-documents"), File(backupDirectory, "working-documents"))
    }

    private fun createBackupDirectory(timestamp: Long): File {
        return File(File(context.filesDir, "migration-reports"), "backup-$timestamp").apply { mkdirs() }
    }

    private fun latestReportFile(): File = File(reportDirectory(), "latest-core-migration.json")
    private fun stateFile(): File = File(reportDirectory(), "migration-state.json")
    private fun reportDirectory(): File = File(context.filesDir, "migration-reports").apply { mkdirs() }

    private fun writeReport(report: CoreMigrationReport) {
        val latest = latestReportFile()
        latest.writeText(json.encodeToString(CoreMigrationReport.serializer(), report))
        File(reportDirectory(), "core-migration-${report.completedAtEpochMillis}.json")
            .writeText(json.encodeToString(CoreMigrationReport.serializer(), report))
    }

    private fun writeState(report: CoreMigrationReport) {
        stateFile().writeText(
            json.encodeToString(
                MigrationState.serializer(),
                MigrationState(engineVersion = report.engineVersion, lastCompletedAtEpochMillis = report.completedAtEpochMillis),
            ),
        )
    }

    private fun copyIfExists(source: File, backupDirectory: File) {
        if (!source.exists()) return
        source.copyTo(File(backupDirectory, source.name), overwrite = true)
        val wal = File(source.absolutePath + "-wal")
        if (wal.exists()) wal.copyTo(File(backupDirectory, wal.name), overwrite = true)
        val shm = File(source.absolutePath + "-shm")
        if (shm.exists()) shm.copyTo(File(backupDirectory, shm.name), overwrite = true)
    }

    private fun copyDirectoryIfExists(source: File, destination: File) {
        if (!source.exists()) return
        destination.mkdirs()
        source.copyRecursively(destination, overwrite = true)
    }

    companion object {
        private const val ENGINE_VERSION = 1
        private const val STALE_OPERATION_AGE_MILLIS = 15L * 60L * 1000L
    }
}

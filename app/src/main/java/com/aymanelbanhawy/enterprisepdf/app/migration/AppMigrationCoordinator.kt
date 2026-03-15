package com.aymanelbanhawy.enterprisepdf.app.migration

import android.content.Context
import com.aymanelbanhawy.aiassistant.core.AiAssistantMigrationSupport
import com.aymanelbanhawy.editor.core.EditorCoreContainer
import com.aymanelbanhawy.editor.core.migration.AppMigrationReport
import java.io.File
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

interface AppMigrationCoordinator {
    suspend fun runUpgradePass(): AppMigrationReport
    suspend fun latestReport(): AppMigrationReport?
    suspend fun exportLatestReport(destination: File): File
    suspend fun importReport(source: File): AppMigrationReport
}

class DefaultAppMigrationCoordinator(
    private val context: Context,
    private val editorCoreContainer: EditorCoreContainer,
    private val json: Json = Json { ignoreUnknownKeys = true; encodeDefaults = true },
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : AppMigrationCoordinator {

    override suspend fun runUpgradePass(): AppMigrationReport = withContext(ioDispatcher) {
        val coreReport = editorCoreContainer.coreMigrationRepository.runUpgradePass()
        val aiSummary = AiAssistantMigrationSupport.normalizePersistedState(context)
        val report = AppMigrationReport(
            core = coreReport,
            aiStateNormalizedCount = aiSummary.normalizedProfileCount,
            aiStateMessage = aiSummary.message,
        )
        val latest = latestReportFile()
        latest.parentFile?.mkdirs()
        latest.writeText(json.encodeToString(AppMigrationReport.serializer(), report))
        File(latest.parentFile, "app-migration-${coreReport.completedAtEpochMillis}.json")
            .writeText(json.encodeToString(AppMigrationReport.serializer(), report))
        report
    }

    override suspend fun latestReport(): AppMigrationReport? = withContext(ioDispatcher) {
        val file = latestReportFile()
        if (!file.exists()) return@withContext null
        runCatching { json.decodeFromString(AppMigrationReport.serializer(), file.readText()) }.getOrNull()
    }

    override suspend fun exportLatestReport(destination: File): File = withContext(ioDispatcher) {
        val source = latestReportFile()
        require(source.exists()) { "No migration report is available." }
        destination.parentFile?.mkdirs()
        source.copyTo(destination, overwrite = true)
        destination
    }

    override suspend fun importReport(source: File): AppMigrationReport = withContext(ioDispatcher) {
        val report = json.decodeFromString(AppMigrationReport.serializer(), source.readText())
        latestReportFile().apply {
            parentFile?.mkdirs()
            writeText(json.encodeToString(AppMigrationReport.serializer(), report))
        }
        report
    }

    private fun latestReportFile(): File = File(File(context.filesDir, "migration-reports"), "latest-app-migration.json")
}

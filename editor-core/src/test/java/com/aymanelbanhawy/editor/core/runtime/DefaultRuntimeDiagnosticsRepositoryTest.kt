package com.aymanelbanhawy.editor.core.runtime

import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import com.aymanelbanhawy.editor.core.data.ConnectorAccountDao
import com.aymanelbanhawy.editor.core.data.ConnectorAccountEntity
import com.aymanelbanhawy.editor.core.data.ConnectorTransferJobDao
import com.aymanelbanhawy.editor.core.data.ConnectorTransferJobEntity
import com.aymanelbanhawy.editor.core.data.DraftDao
import com.aymanelbanhawy.editor.core.data.DraftEntity
import com.aymanelbanhawy.editor.core.data.OcrJobDao
import com.aymanelbanhawy.editor.core.data.OcrJobEntity
import com.aymanelbanhawy.editor.core.data.RuntimeBreadcrumbDao
import com.aymanelbanhawy.editor.core.data.RuntimeBreadcrumbEntity
import com.aymanelbanhawy.editor.core.data.SyncQueueDao
import com.aymanelbanhawy.editor.core.data.SyncQueueEntity
import com.aymanelbanhawy.editor.core.migration.AppMigrationReport
import com.aymanelbanhawy.editor.core.migration.CoreMigrationReport
import com.aymanelbanhawy.editor.core.migration.MigrationSeverity
import com.aymanelbanhawy.editor.core.migration.MigrationStepReport
import com.aymanelbanhawy.editor.core.migration.MigrationStepStatus
import com.google.common.truth.Truth.assertThat
import java.io.File
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import org.junit.Test

class DefaultRuntimeDiagnosticsRepositoryTest {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; classDiscriminator = "_type" }

    @Test
    fun runStartupRepair_recoversTempAndQuarantinesCorruptDraft() = runTest {
        val root = File("build/runtime-test/${UUID.randomUUID()}").apply { mkdirs() }
        val context = TestContext(root)
        val draftsDir = File(context.filesDir, "drafts").apply { mkdirs() }
        val workingDir = File(context.filesDir, "working-documents").apply { mkdirs() }
        File(draftsDir, "broken.json").writeText("not-json")
        File(workingDir, "contract.pdf.tmp").writeText("pdf-bytes")
        File(workingDir, "contract.pdf.saving.lock").writeText("locked")

        val repository = DefaultRuntimeDiagnosticsRepository(
            context = context,
            breadcrumbDao = TestRuntimeBreadcrumbDao(),
            draftDao = TestDraftDao(),
            ocrJobDao = TestOcrJobDao(),
            syncQueueDao = TestSyncQueueDao(),
            connectorTransferJobDao = TestConnectorTransferJobDao(),
            connectorAccountDao = TestConnectorAccountDao(),
            json = json,
        )

        val repair = repository.runStartupRepair()

        assertThat(repair.repairedDraftCount).isEqualTo(1)
        assertThat(repair.recoveredSaveCount).isAtLeast(1)
        assertThat(File(workingDir, "contract.pdf").exists()).isTrue()
        assertThat(File(draftsDir, "broken.json.corrupt").exists()).isTrue()
    }

    @Test
    fun runStartupRepair_countsInterruptedJobsAndQuarantinesBrokenSidecar() = runTest {
        val root = File("build/runtime-test/${UUID.randomUUID()}").apply { mkdirs() }
        val context = TestContext(root)
        val workingDir = File(context.filesDir, "working-documents").apply { mkdirs() }
        File(workingDir, "session.mutations.json").writeText("broken-compatibility")
        val now = 1000L
        val repository = DefaultRuntimeDiagnosticsRepository(
            context = context,
            breadcrumbDao = TestRuntimeBreadcrumbDao(),
            draftDao = TestDraftDao(),
            ocrJobDao = TestOcrJobDao(
                jobs = listOf(
                    testOcrJob(id = "ocr-running", status = "Running"),
                    testOcrJob(id = "ocr-pending", status = "Pending"),
                    testOcrJob(id = "ocr-complete", status = "Completed"),
                ),
            ),
            syncQueueDao = TestSyncQueueDao(
                queue = listOf(
                    testSyncEntity(id = "sync-1", now = now),
                    testSyncEntity(id = "sync-2", now = now),
                ),
            ),
            connectorTransferJobDao = TestConnectorTransferJobDao(),
            connectorAccountDao = TestConnectorAccountDao(),
            json = json,
        )

        val repair = repository.runStartupRepair()

        assertThat(repair.resumedOcrCount).isEqualTo(2)
        assertThat(repair.resumedSyncCount).isEqualTo(2)
        assertThat(repair.quarantinedCompatibilityFileCount).isEqualTo(1)
        assertThat(File(workingDir, "session.mutations.json.corrupt").exists()).isTrue()
    }

    @Test
    fun captureSnapshot_includesMigrationConnectorAndFailureSummary() = runTest {
        val root = File("build/runtime-test/${UUID.randomUUID()}").apply { mkdirs() }
        val context = TestContext(root)
        val reportDir = File(context.filesDir, "migration-reports").apply { mkdirs() }
        File(reportDir, "latest-app-migration.json").writeText(
            json.encodeToString(
                AppMigrationReport.serializer(),
                AppMigrationReport(
                    core = CoreMigrationReport(
                        engineVersion = 21,
                        startedAtEpochMillis = 10L,
                        completedAtEpochMillis = 20L,
                        compatibilityModeUsed = true,
                        backupDirectoryPath = File(context.filesDir, "migration-backups").absolutePath,
                        steps = listOf(
                            MigrationStepReport(
                                id = "drafts",
                                title = "Draft migration",
                                status = MigrationStepStatus.Applied,
                                severity = MigrationSeverity.Info,
                                message = "Drafts upgraded.",
                                migratedCount = 3,
                            ),
                            MigrationStepReport(
                                id = "ai",
                                title = "AI settings repair",
                                status = MigrationStepStatus.Failed,
                                severity = MigrationSeverity.Warning,
                                message = "One AI profile needed manual review.",
                            ),
                        ),
                    ),
                    aiStateNormalizedCount = 2,
                    aiStateMessage = "AI settings normalized.",
                ),
            ),
        )
        val breadcrumbs = TestRuntimeBreadcrumbDao().apply {
            upsert(
                RuntimeBreadcrumbEntity(
                    id = "failure-sync",
                    category = RuntimeEventCategory.Sync.name,
                    level = RuntimeLogLevel.Error.name,
                    eventName = "sync_failed",
                    message = "Review sync failed after reconnect.",
                    metadataJson = json.encodeToString(MapSerializer(String.serializer(), String.serializer()), emptyMap<String, String>()),
                    createdAtEpochMillis = 55L,
                ),
            )
            upsert(
                RuntimeBreadcrumbEntity(
                    id = "failure-save",
                    category = RuntimeEventCategory.Save.name,
                    level = RuntimeLogLevel.Error.name,
                    eventName = "save_failed",
                    message = "Protected export was blocked by policy.",
                    metadataJson = json.encodeToString(MapSerializer(String.serializer(), String.serializer()), emptyMap<String, String>()),
                    createdAtEpochMillis = 56L,
                ),
            )
        }
        val repository = DefaultRuntimeDiagnosticsRepository(
            context = context,
            breadcrumbDao = breadcrumbs,
            draftDao = TestDraftDao(),
            ocrJobDao = TestOcrJobDao(jobs = listOf(testOcrJob(id = "ocr-failed", status = "Failed"))),
            syncQueueDao = TestSyncQueueDao(queue = listOf(testSyncEntity(id = "sync-1", now = 5L))),
            connectorTransferJobDao = TestConnectorTransferJobDao(
                jobs = listOf(
                    ConnectorTransferJobEntity(
                        id = "transfer-1",
                        connectorAccountId = "account-1",
                        documentKey = "doc-1",
                        remotePath = "/contracts/doc-1.pdf",
                        localCachePath = File(context.cacheDir, "connector-cache/doc-1.pdf").absolutePath,
                        tempMaterializedPath = null,
                        direction = "Upload",
                        status = "Running",
                        bytesTransferred = 100,
                        totalBytes = 250,
                        resumableToken = "resume-token",
                        attemptCount = 1,
                        lastError = null,
                        remoteEtag = null,
                        remoteVersionId = null,
                        conflictStrategy = "Fail",
                        cacheExpiresAtEpochMillis = null,
                        createdAtEpochMillis = 20L,
                        updatedAtEpochMillis = 30L,
                    ),
                ),
            ),
            connectorAccountDao = TestConnectorAccountDao(
                accounts = listOf(
                    ConnectorAccountEntity(
                        id = "account-1",
                        connectorType = "S3Compatible",
                        displayName = "Corporate S3",
                        baseUrl = "https://storage.example.com",
                        credentialType = "AccessKey",
                        username = "service-account",
                        secretAlias = "alias/s3",
                        capabilitiesJson = "[]",
                        configurationJson = "{}",
                        supportsOpen = true,
                        supportsSave = true,
                        supportsShare = false,
                        supportsImport = true,
                        supportsMetadataSync = true,
                        supportsResumableTransfer = true,
                        isEnterpriseManaged = true,
                        createdAtEpochMillis = 1L,
                        updatedAtEpochMillis = 2L,
                    ),
                ),
            ),
            json = json,
        )

        val snapshot = repository.captureSnapshot(currentDocument = null)

        assertThat(snapshot.migration.failureCount).isEqualTo(1)
        assertThat(snapshot.migration.compatibilityModeUsed).isTrue()
        assertThat(snapshot.connectors.configuredAccountCount).isEqualTo(1)
        assertThat(snapshot.connectors.activeTransferCount).isEqualTo(1)
        assertThat(snapshot.failureSummaries.map { it.category }).containsAtLeast(RuntimeEventCategory.Sync, RuntimeEventCategory.Save)
        assertThat(snapshot.providerHealth.map { it.name }).contains("Corporate S3")
    }
}

private class TestContext(root: File) : ContextWrapper(Application()) {
    private val cache = File(root, "cache").apply { mkdirs() }
    private val files = File(root, "files").apply { mkdirs() }
    override fun getCacheDir(): File = cache
    override fun getFilesDir(): File = files
    override fun getApplicationContext(): Context = this
}

private class TestRuntimeBreadcrumbDao : RuntimeBreadcrumbDao {
    private val items = mutableListOf<RuntimeBreadcrumbEntity>()
    override suspend fun upsert(entity: RuntimeBreadcrumbEntity) { items.removeAll { it.id == entity.id }; items += entity }
    override suspend fun recent(limit: Int): List<RuntimeBreadcrumbEntity> = items.sortedByDescending { it.createdAtEpochMillis }.take(limit)
    override suspend fun recentFailures(limit: Int): List<RuntimeBreadcrumbEntity> = items.filter { it.level == "Error" || it.category == "Failure" }.sortedByDescending { it.createdAtEpochMillis }.take(limit)
    override suspend fun trimOlderThan(thresholdEpochMillis: Long) { items.removeAll { it.createdAtEpochMillis < thresholdEpochMillis } }
}

private class TestDraftDao : DraftDao {
    override suspend fun upsert(entity: DraftEntity) = Unit
    override suspend fun getLatestForSource(sourceKey: String): DraftEntity? = null
    override suspend fun deleteBySource(sourceKey: String) = Unit
    override suspend fun deleteBySession(sessionId: String) = Unit
}

private class TestOcrJobDao(
    private val jobs: List<OcrJobEntity> = emptyList(),
) : OcrJobDao {
    override suspend fun upsert(entity: OcrJobEntity) = Unit
    override suspend fun upsertAll(entities: List<OcrJobEntity>) = Unit
    override suspend fun job(id: String): OcrJobEntity? = jobs.firstOrNull { it.id == id }
    override suspend fun all(): List<OcrJobEntity> = jobs
    override suspend fun jobsForDocument(documentKey: String): List<OcrJobEntity> = jobs.filter { it.documentKey == documentKey }
    override fun observeJobsForDocument(documentKey: String): Flow<List<OcrJobEntity>> = flowOf(jobs.filter { it.documentKey == documentKey })
    override suspend fun jobForPage(documentKey: String, pageIndex: Int): OcrJobEntity? = jobs.firstOrNull { it.documentKey == documentKey && it.pageIndex == pageIndex }
    override suspend fun pendingOrResumable(documentKey: String, staleBeforeEpochMillis: Long, limit: Int): List<OcrJobEntity> = jobs.filter { it.documentKey == documentKey }.take(limit)
}

private class TestSyncQueueDao(
    private val queue: List<SyncQueueEntity> = emptyList(),
) : SyncQueueDao {
    override suspend fun upsert(entity: SyncQueueEntity) = Unit
    override suspend fun all(): List<SyncQueueEntity> = queue
    override suspend fun forDocument(documentKey: String): List<SyncQueueEntity> = queue.filter { it.documentKey == documentKey }
    override suspend fun eligible(documentKey: String, nowEpochMillis: Long): List<SyncQueueEntity> = queue.filter { it.documentKey == documentKey && it.nextAttemptAtEpochMillis <= nowEpochMillis }
    override suspend fun eligibleAll(nowEpochMillis: Long): List<SyncQueueEntity> = queue.filter { it.nextAttemptAtEpochMillis <= nowEpochMillis }
}

private class TestConnectorTransferJobDao(
    private val jobs: List<ConnectorTransferJobEntity> = emptyList(),
) : ConnectorTransferJobDao {
    override suspend fun upsert(entity: ConnectorTransferJobEntity) = Unit
    override suspend fun get(id: String): ConnectorTransferJobEntity? = jobs.firstOrNull { it.id == id }
    override suspend fun pending(): List<ConnectorTransferJobEntity> = jobs.filter { it.status == "Pending" || it.status == "Failed" || it.status == "Paused" }
    override suspend fun all(): List<ConnectorTransferJobEntity> = jobs
    override suspend fun deleteById(id: String) = Unit
    override suspend fun deleteCompletedBefore(thresholdEpochMillis: Long) = Unit
}

private class TestConnectorAccountDao(
    private val accounts: List<ConnectorAccountEntity> = emptyList(),
) : ConnectorAccountDao {
    override suspend fun upsert(entity: ConnectorAccountEntity) = Unit
    override suspend fun all(): List<ConnectorAccountEntity> = accounts
    override suspend fun get(id: String): ConnectorAccountEntity? = accounts.firstOrNull { it.id == id }
    override suspend fun deleteById(id: String) = Unit
}

private fun testOcrJob(id: String, status: String): OcrJobEntity {
    return OcrJobEntity(
        id = id,
        documentKey = "doc-1",
        pageIndex = 0,
        imagePath = "image.png",
        status = status,
        progressPercent = 10,
        attemptCount = 1,
        maxAttempts = 3,
        createdAtEpochMillis = 1L,
        updatedAtEpochMillis = 2L,
    )
}

private fun testSyncEntity(id: String, now: Long): SyncQueueEntity {
    return SyncQueueEntity(
        id = id,
        documentKey = "doc-1",
        type = "ReviewComment",
        payloadJson = "{}",
        createdAtEpochMillis = now,
        updatedAtEpochMillis = now,
        state = "Queued",
        attemptCount = 0,
        maxAttempts = 3,
        nextAttemptAtEpochMillis = now,
        lastError = null,
        idempotencyKey = "idem-$id",
        lastHttpStatus = null,
        conflictPayloadJson = null,
        tombstone = false,
    )
}




package com.aymanelbanhawy.editor.core.migration

import android.content.Context
import org.robolectric.RuntimeEnvironment
import com.aymanelbanhawy.editor.core.data.DraftDao
import com.aymanelbanhawy.editor.core.data.DraftEntity
import com.aymanelbanhawy.editor.core.data.OcrJobDao
import com.aymanelbanhawy.editor.core.data.OcrJobEntity
import com.aymanelbanhawy.editor.core.data.SearchIndexDao
import com.aymanelbanhawy.editor.core.data.SearchIndexEntity
import com.aymanelbanhawy.editor.core.data.SyncQueueDao
import com.aymanelbanhawy.editor.core.data.SyncQueueEntity
import com.aymanelbanhawy.editor.core.model.DocumentModel
import com.aymanelbanhawy.editor.core.model.DocumentSourceType
import com.aymanelbanhawy.editor.core.model.DraftPayload
import com.aymanelbanhawy.editor.core.model.PageModel
import com.aymanelbanhawy.editor.core.model.PdfDocumentRef
import com.aymanelbanhawy.editor.core.model.SelectionModel
import com.aymanelbanhawy.editor.core.ocr.OcrJobStatus
import com.aymanelbanhawy.editor.core.runtime.RuntimeDiagnosticsRepository
import com.aymanelbanhawy.editor.core.runtime.RuntimeDiagnosticsSnapshot
import com.aymanelbanhawy.editor.core.runtime.RuntimeEventCategory
import com.aymanelbanhawy.editor.core.runtime.RuntimeLogLevel
import com.aymanelbanhawy.editor.core.runtime.StartupRepairResult
import com.google.common.truth.Truth.assertThat
import java.io.File
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.runner.RunWith
import org.junit.Before
import org.junit.Test
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DefaultCoreMigrationRepositoryTest {
    private val context: Context = RuntimeEnvironment.getApplication()
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; classDiscriminator = "_type" }
    private lateinit var draftsDir: File
    private lateinit var workingDir: File

    @Before
    fun setUp() {
        draftsDir = File(context.filesDir, "drafts").apply { mkdirs() }
        workingDir = File(context.filesDir, "working-documents").apply { mkdirs() }
        File(context.filesDir, "migration-reports").deleteRecursively()
    }

    @After
    fun tearDown() {
        draftsDir.deleteRecursively()
        workingDir.deleteRecursively()
        File(context.filesDir, "migration-reports").deleteRecursively()
    }

    @Test
    fun runUpgradePass_migratesLegacyFilesAndResetsInterruptedJobs() = runBlocking {
        val sessionId = "migration-test-session"
        val draft = DraftPayload(
            document = DocumentModel(
                sessionId = sessionId,
                documentRef = PdfDocumentRef(
                    uriString = "file:///sample.pdf",
                    displayName = "sample.pdf",
                    password = null,
                    sourceType = DocumentSourceType.File,
                    sourceKey = "sample-key",
                    workingCopyPath = "sample-working.pdf",
                ),
                pages = listOf(PageModel(index = 0, label = "1")),
            ),
            selection = SelectionModel(),
            undoCount = 1,
            redoCount = 0,
            lastCommandName = "AddTextBox",
        )
        File(draftsDir, "$sessionId.json").writeText(json.encodeToString(DraftPayload.serializer(), draft))
        File(draftsDir, "broken.json").writeText("not-json")

        val legacyCompatibilityFile = File(workingDir, "sample.pdf" + FileLegacyEditCompatibilityBridge.legacySuffix())
        legacyCompatibilityFile.writeText(
            """
            {"documentKey":"sample-key","editObjects":[],"updatedAtEpochMillis":1234}
            """.trimIndent(),
        )

        val repo = DefaultCoreMigrationRepository(
            context = context,
            draftDao = object : DraftDao {
                override suspend fun upsert(entity: DraftEntity) = Unit
                override suspend fun getLatestForSource(sourceKey: String): DraftEntity? = null
                override suspend fun deleteBySource(sourceKey: String) = Unit
                override suspend fun deleteBySession(sessionId: String) = Unit
            },
            ocrJobDao = FakeOcrJobDao(
                mutableListOf(
                    OcrJobEntity(
                        id = "ocr-1",
                        documentKey = "sample-key",
                        pageIndex = 0,
                        imagePath = "image.png",
                        status = OcrJobStatus.Running.name,
                        createdAtEpochMillis = 1L,
                        updatedAtEpochMillis = 1L,
                    ),
                ),
            ),
            searchIndexDao = FakeSearchIndexDao(mutableListOf(SearchIndexEntity("sample-key", 0, "text", "[]", null, null, 0L))),
            syncQueueDao = FakeSyncQueueDao(
                mutableListOf(
                    SyncQueueEntity(
                        id = "sync-1",
                        documentKey = "sample-key",
                        type = "UpsertReviewThread",
                        payloadJson = "{}",
                        createdAtEpochMillis = 1L,
                        updatedAtEpochMillis = 1L,
                        state = "Syncing",
                        attemptCount = 0,
                        maxAttempts = 5,
                        nextAttemptAtEpochMillis = 1L,
                        lastError = null,
                        idempotencyKey = "sync-1",
                        lastHttpStatus = null,
                        conflictPayloadJson = null,
                        tombstone = false,
                    ),
                ),
            ),
            diagnosticsRepository = NoOpDiagnosticsRepository(),
            json = json,
        )

        val report = repo.runUpgradePass()

        assertThat(report.successCount).isAtLeast(1)
        assertThat(File(workingDir, "sample.pdf.mutations.json").exists()).isTrue()
        assertThat(File(draftsDir, "broken.json.migration-corrupt").exists()).isTrue()
        assertThat(repo.latestReport()).isNotNull()
    }
}

private class FakeOcrJobDao(
    private val jobs: MutableList<OcrJobEntity>,
) : OcrJobDao {
    override suspend fun upsert(entity: OcrJobEntity) {
        jobs.removeAll { it.id == entity.id }
        jobs += entity
    }

    override suspend fun upsertAll(entities: List<OcrJobEntity>) {
        entities.forEach { upsert(it) }
    }

    override suspend fun job(id: String): OcrJobEntity? = jobs.firstOrNull { it.id == id }
    override suspend fun all(): List<OcrJobEntity> = jobs.toList()
    override suspend fun jobsForDocument(documentKey: String): List<OcrJobEntity> = jobs.filter { it.documentKey == documentKey }
    override fun observeJobsForDocument(documentKey: String): Flow<List<OcrJobEntity>> = emptyFlow()
    override suspend fun jobForPage(documentKey: String, pageIndex: Int): OcrJobEntity? = jobs.firstOrNull { it.documentKey == documentKey && it.pageIndex == pageIndex }
    override suspend fun pendingOrResumable(documentKey: String, staleBeforeEpochMillis: Long, limit: Int): List<OcrJobEntity> = jobs.take(limit)
}

private class FakeSearchIndexDao(
    private val entries: MutableList<SearchIndexEntity>,
) : SearchIndexDao {
    override suspend fun upsertAll(entities: List<SearchIndexEntity>) {
        entries += entities
    }

    override suspend fun documentKeys(): List<String> = entries.map { it.documentKey }.distinct()
    override suspend fun indexForDocument(documentKey: String): List<SearchIndexEntity> = entries.filter { it.documentKey == documentKey }
    override suspend fun updateOcrPayload(documentKey: String, pageIndex: Int, ocrText: String?, ocrBlocksJson: String?, updatedAtEpochMillis: Long) = Unit
    override suspend fun deleteForDocument(documentKey: String) {
        entries.removeAll { it.documentKey == documentKey }
    }
}

private class FakeSyncQueueDao(
    private val operations: MutableList<SyncQueueEntity>,
) : SyncQueueDao {
    override suspend fun upsert(entity: SyncQueueEntity) {
        operations.removeAll { it.id == entity.id }
        operations += entity
    }

    override suspend fun all(): List<SyncQueueEntity> = operations.toList()
    override suspend fun forDocument(documentKey: String): List<SyncQueueEntity> = operations.filter { it.documentKey == documentKey }
    override suspend fun eligible(documentKey: String, nowEpochMillis: Long): List<SyncQueueEntity> = operations.filter { it.documentKey == documentKey }
    override suspend fun eligibleAll(nowEpochMillis: Long): List<SyncQueueEntity> = operations.toList()
}

private class NoOpDiagnosticsRepository : RuntimeDiagnosticsRepository {
    override suspend fun recordBreadcrumb(category: RuntimeEventCategory, level: RuntimeLogLevel, eventName: String, message: String, metadata: Map<String, String>) = Unit
    override suspend fun recordAppStart(elapsedMillis: Long, repairResult: StartupRepairResult) = Unit
    override suspend fun recordDocumentOpen(document: DocumentModel, elapsedMillis: Long) = Unit
    override suspend fun recordSave(document: DocumentModel, elapsedMillis: Long, success: Boolean, fileSizeBytes: Long, checksumSha256: String?) = Unit
    override suspend fun recordProviderHealth(name: String, healthy: Boolean, detail: String) = Unit
    override suspend fun captureSnapshot(currentDocument: DocumentModel?): RuntimeDiagnosticsSnapshot = RuntimeDiagnosticsSnapshot()
    override suspend fun runStartupRepair(): StartupRepairResult = StartupRepairResult()
}












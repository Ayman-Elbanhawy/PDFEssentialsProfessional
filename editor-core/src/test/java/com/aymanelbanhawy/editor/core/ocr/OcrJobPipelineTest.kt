package com.aymanelbanhawy.editor.core.ocr

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.testing.WorkManagerTestInitHelper
import com.aymanelbanhawy.editor.core.data.OcrJobDao
import com.aymanelbanhawy.editor.core.data.OcrJobEntity
import com.aymanelbanhawy.editor.core.data.OcrSettingsDao
import com.aymanelbanhawy.editor.core.data.OcrSettingsEntity
import com.aymanelbanhawy.editor.core.model.DocumentModel
import com.aymanelbanhawy.editor.core.model.DocumentSourceType
import com.aymanelbanhawy.editor.core.model.NormalizedRect
import com.aymanelbanhawy.editor.core.model.PageModel
import com.aymanelbanhawy.editor.core.model.PdfDocumentRef
import com.aymanelbanhawy.editor.core.search.DocumentSearchService
import com.aymanelbanhawy.editor.core.search.ExtractedTextBlock
import com.aymanelbanhawy.editor.core.search.IndexedPageContent
import com.aymanelbanhawy.editor.core.search.OutlineItem
import com.aymanelbanhawy.editor.core.search.PdfBoxTextExtractionService
import com.aymanelbanhawy.editor.core.search.SearchResultSet
import com.aymanelbanhawy.editor.core.search.TextSelectionPayload
import com.google.common.truth.Truth.assertThat
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import java.io.File
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class OcrJobPipelineTest {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; classDiscriminator = "_type" }
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        PDFBoxResourceLoader.init(context)
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration.Builder().setMinimumLoggingLevel(android.util.Log.DEBUG).build(),
        )
    }

    @Test
    fun completePersistsSearchAndDatabaseBackedPayload() = runBlocking {
        val dao = FakeOcrJobDao()
        val settingsDao = FakeOcrSettingsDao()
        val searchService = RecordingSearchService()
        val tempDir = createTempDir(prefix = "ocr-pipeline-test")
        val sessionStore = OcrSessionStore(json, dao)
        val pipeline = OcrJobPipeline(
            ocrJobDao = dao,
            ocrSettingsDao = settingsDao,
            searchService = searchService,
            workManager = androidx.work.WorkManager.getInstance(context),
            json = json,
            ocrSessionStore = sessionStore,
        )
        val settings = OcrSettingsModel()
        val job = OcrJobEntity(
            id = "doc::0",
            documentKey = File(tempDir, "doc.pdf").absolutePath,
            pageIndex = 0,
            imagePath = File(tempDir, "page.png").absolutePath,
            status = OcrJobStatus.Running.name,
            createdAtEpochMillis = 1L,
            updatedAtEpochMillis = 1L,
        )
        dao.upsert(job)

        pipeline.complete(job = job, settings = settings, result = sampleResult(tempDir))

        val stored = dao.job("doc::0")
        assertThat(stored?.status).isEqualTo(OcrJobStatus.Completed.name)
        assertThat(searchService.lastPageText).isEqualTo("Invoice total due")
        val persisted = sessionStore.loadForDocumentKey(job.documentKey)
        assertThat(persisted?.pages).hasSize(1)
        assertThat(persisted?.pages?.first()?.text).isEqualTo("Invoice total due")
        assertThat(File(job.documentKey + OcrSessionStore.COMPATIBILITY_SUFFIX).exists()).isTrue()
    }

    @Test
    fun completeEmbedsSearchablePdfText() = runBlocking {
        val dao = FakeOcrJobDao()
        val settingsDao = FakeOcrSettingsDao()
        val searchService = RecordingSearchService()
        val tempDir = createTempDir(prefix = "ocr-searchable-pdf")
        val pdfFile = File(tempDir, "doc.pdf")
        PDDocument().use { document ->
            document.addPage(PDPage(PDRectangle.LETTER))
            document.save(pdfFile)
        }
        val pipeline = OcrJobPipeline(
            ocrJobDao = dao,
            ocrSettingsDao = settingsDao,
            searchService = searchService,
            workManager = androidx.work.WorkManager.getInstance(context),
            json = json,
            ocrSessionStore = OcrSessionStore(json, dao),
            ocrPdfWriter = PdfBoxOcrPdfWriter(),
        )
        val job = OcrJobEntity(
            id = "doc::0",
            documentKey = pdfFile.absolutePath,
            pageIndex = 0,
            imagePath = File(tempDir, "page.png").absolutePath,
            status = OcrJobStatus.Running.name,
            createdAtEpochMillis = 1L,
            updatedAtEpochMillis = 1L,
        )
        dao.upsert(job)

        pipeline.complete(job = job, settings = OcrSettingsModel(), result = sampleResult(tempDir))

        val extracted = PdfBoxTextExtractionService().extract(
            PdfDocumentRef(
                uriString = pdfFile.toURI().toString(),
                displayName = pdfFile.name,
                sourceType = DocumentSourceType.File,
                sourceKey = pdfFile.absolutePath,
                workingCopyPath = pdfFile.absolutePath,
            ),
        )
        assertThat(extracted.first().pageText).contains("Invoice total due")
    }

    @Test
    fun pauseAndResumeUpdateState() = runBlocking {
        val dao = FakeOcrJobDao()
        val pipeline = OcrJobPipeline(
            ocrJobDao = dao,
            ocrSettingsDao = FakeOcrSettingsDao(),
            searchService = RecordingSearchService(),
            workManager = androidx.work.WorkManager.getInstance(context),
            json = json,
            ocrSessionStore = OcrSessionStore(json, dao),
        )
        val pending = OcrJobEntity(
            id = "doc::1",
            documentKey = "/tmp/doc.pdf",
            pageIndex = 1,
            imagePath = "/tmp/one.png",
            status = OcrJobStatus.Pending.name,
            createdAtEpochMillis = 1L,
            updatedAtEpochMillis = 1L,
        )
        dao.upsert(pending)

        pipeline.pause("/tmp/doc.pdf", 1)
        assertThat(dao.job("doc::1")?.status).isEqualTo(OcrJobStatus.Paused.name)

        pipeline.resume("/tmp/doc.pdf", 1)
        assertThat(dao.job("doc::1")?.status).isEqualTo(OcrJobStatus.Pending.name)
    }

    @Test
    fun failMarksJobFailedWhenRetryIsNotAllowed() = runBlocking {
        val dao = FakeOcrJobDao()
        val pipeline = OcrJobPipeline(
            ocrJobDao = dao,
            ocrSettingsDao = FakeOcrSettingsDao(),
            searchService = RecordingSearchService(),
            workManager = androidx.work.WorkManager.getInstance(context),
            json = json,
            ocrSessionStore = OcrSessionStore(json, dao),
        )
        val job = OcrJobEntity(
            id = "doc::1",
            documentKey = "/tmp/doc.pdf",
            pageIndex = 1,
            imagePath = "/tmp/one.png",
            status = OcrJobStatus.Running.name,
            attemptCount = 3,
            maxAttempts = 3,
            createdAtEpochMillis = 1L,
            updatedAtEpochMillis = 1L,
        )
        dao.upsert(job)

        pipeline.fail(job, OcrEngineDiagnostics(code = "model-unavailable", message = "Model unavailable", retryable = true))

        assertThat(dao.job("doc::1")?.status).isEqualTo(OcrJobStatus.Failed.name)
    }

    private fun sampleResult(tempDir: File): OcrEngineResult {
        return OcrEngineResult(
            page = OcrPageContent(
                pageIndex = 0,
                text = "Invoice total due",
                blocks = listOf(
                    OcrTextBlockModel(
                        text = "Invoice total due",
                        bounds = NormalizedRect(0.1f, 0.1f, 0.8f, 0.2f),
                        lines = listOf(
                            OcrTextLineModel(
                                text = "Invoice total due",
                                bounds = NormalizedRect(0.1f, 0.1f, 0.8f, 0.2f),
                                elements = listOf(OcrTextElementModel("Invoice", NormalizedRect(0.1f, 0.1f, 0.3f, 0.2f))),
                            ),
                        ),
                    ),
                ),
                imageWidth = 1000,
                imageHeight = 1600,
            ),
            preprocessedImagePath = File(tempDir, "processed.png").absolutePath,
        )
    }
}

private class FakeOcrJobDao : OcrJobDao {
    private val entities = linkedMapOf<String, OcrJobEntity>()
    private val flow = MutableStateFlow(emptyList<OcrJobEntity>())

    override suspend fun upsert(entity: OcrJobEntity) {
        entities[entity.id] = entity
        emit()
    }

    override suspend fun upsertAll(entities: List<OcrJobEntity>) {
        entities.forEach { this.entities[it.id] = it }
        emit()
    }

    override suspend fun job(id: String): OcrJobEntity? = entities[id]

    override suspend fun all(): List<OcrJobEntity> = entities.values.toList()

    override suspend fun jobsForDocument(documentKey: String): List<OcrJobEntity> =
        entities.values.filter { it.documentKey == documentKey }.sortedBy { it.pageIndex }

    override fun observeJobsForDocument(documentKey: String): Flow<List<OcrJobEntity>> = flow

    override suspend fun jobForPage(documentKey: String, pageIndex: Int): OcrJobEntity? =
        entities.values.firstOrNull { it.documentKey == documentKey && it.pageIndex == pageIndex }

    override suspend fun pendingOrResumable(documentKey: String, staleBeforeEpochMillis: Long, limit: Int): List<OcrJobEntity> =
        entities.values.filter { it.documentKey == documentKey }.take(limit)

    private fun emit() {
        flow.value = entities.values.sortedBy { it.pageIndex }
    }
}

private class FakeOcrSettingsDao : OcrSettingsDao {
    private var entity: OcrSettingsEntity? = null

    override suspend fun upsert(entity: OcrSettingsEntity) {
        this.entity = entity
    }

    override suspend fun get(id: String): OcrSettingsEntity? = entity
}

private class RecordingSearchService : DocumentSearchService {
    var lastPageText: String? = null

    override suspend fun ensureIndex(document: DocumentModel, forceRefresh: Boolean): List<IndexedPageContent> = emptyList()
    override suspend fun search(document: DocumentModel, query: String): SearchResultSet = SearchResultSet()
    override suspend fun recentSearches(documentKey: String, limit: Int): List<String> = emptyList()
    override suspend fun outline(documentRef: PdfDocumentRef): List<OutlineItem> = emptyList()
    override suspend fun selectionForBounds(document: DocumentModel, pageIndex: Int, bounds: NormalizedRect): TextSelectionPayload? = null

    override suspend fun attachOcrResult(documentKey: String, pageIndex: Int, pageText: String, blocks: List<ExtractedTextBlock>) {
        lastPageText = pageText
    }
}



private var workManagerInitialized: Boolean = false



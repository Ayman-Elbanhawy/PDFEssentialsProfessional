package com.aymanelbanhawy.editor.core.workflow

import android.content.Context
import android.content.ContextWrapper
import com.aymanelbanhawy.editor.core.collaboration.ActivityEventModel
import com.aymanelbanhawy.editor.core.collaboration.ActivityEventType
import com.aymanelbanhawy.editor.core.data.ActivityEventDao
import com.aymanelbanhawy.editor.core.data.ActivityEventEntity
import com.aymanelbanhawy.editor.core.data.CompareReportDao
import com.aymanelbanhawy.editor.core.data.CompareReportEntity
import com.aymanelbanhawy.editor.core.data.FormTemplateDao
import com.aymanelbanhawy.editor.core.data.FormTemplateEntity
import com.aymanelbanhawy.editor.core.data.WorkflowRequestDao
import com.aymanelbanhawy.editor.core.data.WorkflowRequestEntity
import com.aymanelbanhawy.editor.core.enterprise.AdminPolicyModel
import com.aymanelbanhawy.editor.core.enterprise.AuthenticationMode
import com.aymanelbanhawy.editor.core.enterprise.AuthSessionModel
import com.aymanelbanhawy.editor.core.enterprise.EnterpriseAdminRepository
import com.aymanelbanhawy.editor.core.enterprise.EnterpriseAdminStateModel
import com.aymanelbanhawy.editor.core.enterprise.EntitlementStateModel
import com.aymanelbanhawy.editor.core.enterprise.LicensePlan
import com.aymanelbanhawy.editor.core.enterprise.TelemetryEventModel
import com.aymanelbanhawy.editor.core.enterprise.TenantConfigurationModel
import com.aymanelbanhawy.editor.core.forms.FormDocumentModel
import com.aymanelbanhawy.editor.core.forms.FormFieldModel
import com.aymanelbanhawy.editor.core.forms.FormFieldType
import com.aymanelbanhawy.editor.core.forms.FormFieldValue
import com.aymanelbanhawy.editor.core.model.AnnotationExportMode
import com.aymanelbanhawy.editor.core.model.DocumentModel
import com.aymanelbanhawy.editor.core.model.DocumentSourceType
import com.aymanelbanhawy.editor.core.model.DraftPayload
import com.aymanelbanhawy.editor.core.model.NormalizedRect
import com.aymanelbanhawy.editor.core.model.OpenDocumentRequest
import com.aymanelbanhawy.editor.core.model.PageContentType
import com.aymanelbanhawy.editor.core.model.PageModel
import com.aymanelbanhawy.editor.core.model.PdfDocumentRef
import com.aymanelbanhawy.editor.core.ocr.OcrSessionStore
import com.aymanelbanhawy.editor.core.repository.DocumentRepository
import com.aymanelbanhawy.editor.core.repository.DraftRestoreResult
import com.aymanelbanhawy.editor.core.scan.ScanImportOptions
import com.aymanelbanhawy.editor.core.scan.ScanImportService
import com.aymanelbanhawy.editor.core.search.PdfBoxTextExtractionService
import com.google.common.truth.Truth.assertThat
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import java.io.File
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class DefaultWorkflowRepositoryTest {
    private val context = object : ContextWrapper(RuntimeEnvironment.getApplication()) {
        private val root = File(System.getProperty("java.io.tmpdir"), "workflow-repository-test").apply { mkdirs() }
        override fun getFilesDir(): File = File(root, "files").apply { mkdirs() }
        override fun getCacheDir(): File = File(root, "cache").apply { mkdirs() }
        override fun getApplicationContext(): Context = this
    }
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; classDiscriminator = "_type" }

    init {
        PDFBoxResourceLoader.init(context)
    }

    @Test
    fun compareDocuments_generatesReviewableChangeList() = runBlocking {
        val repository = repository()
        val baseline = createPdf("baseline-${System.nanoTime()}.pdf", "alpha")
        val compared = createPdf("compared-${System.nanoTime()}.pdf", "alpha beta")

        val report = repository.compareDocuments(document(baseline), ref(compared))

        assertThat(report.summary.changedPages).isEqualTo(1)
        assertThat(report.pageChanges).hasSize(1)
        assertThat(report.pageChanges.single().markers).isNotEmpty()
        assertThat(repository.compareReports(document(baseline).documentRef.sourceKey)).hasSize(1)
    }

    @Test
    fun exportDocumentAsText_andMarkdown_writeStructuredOutput() = runBlocking {
        val repository = repository()
        val pdf = createPdf("export-${System.nanoTime()}.pdf", "alpha beta")
        val document = document(pdf)

        val textResult = repository.exportDocumentAsText(document, File(context.cacheDir, "export.txt"))
        val markdownResult = repository.exportDocumentAsMarkdown(document, File(context.cacheDir, "export.md"))

        assertThat(File(textResult.artifacts.first().path).readText()).contains("alpha beta")
        assertThat(File(markdownResult.artifacts.first().path).readText()).contains("## Page 1")
        assertThat(File(markdownResult.artifacts.first().path).readText()).contains("alpha beta")
    }

    @Test
    fun optimizeDocument_createsExportCopy() = runBlocking {
        val pdf = createPdf("optimize-${System.nanoTime()}.pdf", "compress me")
        val document = document(pdf)
        val repository = repository(documentRepository = RecordingDocumentRepository())
        val destination = File(context.cacheDir, "optimized.pdf")

        val result = repository.optimizeDocument(document, destination, PdfOptimizationPreset.Balanced)

        assertThat(result.destination.exists()).isTrue()
        assertThat(result.destination.length()).isGreaterThan(0L)
        assertThat(result.preset).isEqualTo(PdfOptimizationPreset.Balanced)
    }

    @Test
    fun exportDocumentAsWord_andImportSourceAsPdf_roundTripDocx() = runBlocking {
        val repository = repository()
        val pdf = createPdf("word-export-${System.nanoTime()}.pdf", "alpha beta gamma")
        val document = document(pdf)
        val docx = File(context.cacheDir, "export.docx")

        val exportResult = repository.exportDocumentAsWord(document, docx)
        val imported = repository.importSourceAsPdf(File(exportResult.artifacts.first().path), "roundtrip.pdf")
        val importedRequest = imported.request as OpenDocumentRequest.FromFile

        assertThat(File(exportResult.artifacts.first().path).exists()).isTrue()
        assertThat(File(importedRequest.absolutePath).exists()).isTrue()
        PDDocument.load(File(importedRequest.absolutePath)).use { importedDocument ->
            assertThat(importedDocument.numberOfPages).isAtLeast(1)
        }
    }

    @Test
    fun mergeSourcesAsPdf_combinesPdfAndDocxSources() = runBlocking {
        val repository = repository()
        val pdf = createPdf("merge-source-${System.nanoTime()}.pdf", "existing pdf page")
        val docx = createDocx("merge-source-${System.nanoTime()}.docx", listOf("docx page one", "docx page two"))

        val merged = repository.mergeSourcesAsPdf(listOf(pdf, docx), "merged-sources.pdf")
        val mergedRequest = merged.request as OpenDocumentRequest.FromFile

        assertThat(mergedRequest.displayNameOverride).contains("merged-sources")
        assertThat(File(mergedRequest.absolutePath).exists()).isTrue()
        PDDocument.load(File(mergedRequest.absolutePath)).use { mergedDocument ->
            assertThat(mergedDocument.numberOfPages).isAtLeast(2)
        }
    }

    @Test
    fun exportCompareReport_writesNavigableMarkdown() = runBlocking {
        val repository = repository()
        val baseline = createPdf("baseline-${System.nanoTime()}.pdf", "alpha")
        val compared = createPdf("compared-${System.nanoTime()}.pdf", "alpha beta")
        val report = repository.compareDocuments(document(baseline), ref(compared))
        val destination = File(context.cacheDir, "compare-report.md")

        val artifact = repository.exportCompareReport(report.id, destination)

        assertThat(File(artifact.path).readText()).contains("Compare Report")
        assertThat(File(artifact.path).readText()).contains("Page 1")
    }

    @Test
    fun createFormRequest_roundTripsSubmissionLifecycle() = runBlocking {
        val repository = repository()
        val document = document(createPdf("form-${System.nanoTime()}.pdf", "form"))
        val template = repository.createFormTemplate(document, "Vendor Intake")
        val request = repository.createFormRequest(
            document = document,
            templateId = template.id,
            title = "Collect vendor details",
            recipients = listOf(WorkflowRecipientModel("person@tenant.com", "Person", WorkflowRecipientRole.Submitter, 1)),
            reminderIntervalDays = 2,
            expiresAtEpochMillis = System.currentTimeMillis() + 86_400_000L,
        )

        val updated = repository.updateRequestResponse(
            request.id,
            WorkflowResponseModel(
                recipientEmail = "person@tenant.com",
                status = WorkflowRequestStatus.Completed,
                actedAtEpochMillis = System.currentTimeMillis(),
                fieldValues = mapOf("vendorName" to "Acme"),
            ),
        )

        assertThat(updated?.status).isEqualTo(WorkflowRequestStatus.Completed)
        assertThat(updated?.submissions).hasSize(1)
        assertThat(repository.workflowRequests(document.documentRef.sourceKey)).hasSize(1)
    }

    @Test
    fun createSignatureRequest_blocksExternalRecipientsWhenPolicyDisallows() = runBlocking {
        val repository = repository(
            state = EnterpriseAdminStateModel(
                authSession = AuthSessionModel(mode = AuthenticationMode.Enterprise, isSignedIn = true, email = "owner@tenant.com", displayName = "Owner"),
                tenantConfiguration = TenantConfigurationModel(domain = "tenant.com"),
                adminPolicy = AdminPolicyModel(allowExternalSharing = false),
                plan = LicensePlan.Enterprise,
            ),
        )
        val document = document(createPdf("signature-${System.nanoTime()}.pdf", "sign"))

        val failure = runCatching {
            repository.createSignatureRequest(
                document = document,
                title = "Sign contract",
                recipients = listOf(WorkflowRecipientModel("outside@example.com", "Outside", WorkflowRecipientRole.Signer, 1)),
                reminderIntervalDays = 1,
                expiresAtEpochMillis = System.currentTimeMillis() + 86_400_000L,
            )
        }.exceptionOrNull()

        assertThat(failure).isInstanceOf(SecurityException::class.java)
    }

    @Test
    fun exportFlows_areBlockedAndAudited_whenPolicyRestrictsExport() = runBlocking {
        val activityDao = RecordingActivityEventDao()
        val repository = repository(
            state = EnterpriseAdminStateModel(
                authSession = AuthSessionModel(mode = AuthenticationMode.Enterprise, isSignedIn = true, email = "owner@tenant.com", displayName = "Owner"),
                tenantConfiguration = TenantConfigurationModel(domain = "tenant.com"),
                adminPolicy = AdminPolicyModel(restrictExport = true),
                plan = LicensePlan.Enterprise,
            ),
            activityEventDao = activityDao,
        )
        val pdf = createPdf("restricted-${System.nanoTime()}.pdf", "restricted export content")
        val document = document(pdf)

        val failure = runCatching {
            repository.exportDocumentAsWord(document, File(context.cacheDir, "restricted.docx"))
        }.exceptionOrNull()

        assertThat(failure).isInstanceOf(SecurityException::class.java)
        assertThat(activityDao.allItems()).hasSize(1)
        assertThat(activityDao.allItems().single().summary).contains("Blocked word export")
        assertThat(activityDao.allItems().single().metadataJson).contains("restrictExport")
    }

    @Test
    fun workflowEvidenceBundle_generatesVerifiedOutputs() = runBlocking {
        val activityDao = RecordingActivityEventDao()
        val repository = repository(activityEventDao = activityDao)
        val evidenceRoot = resolveWorkflowEvidenceRoot().apply {
            deleteRecursively()
            mkdirs()
        }
        val sourceDir = File(evidenceRoot, "sources").apply { mkdirs() }
        val outputDir = File(evidenceRoot, "outputs").apply { mkdirs() }
        val logDir = File(evidenceRoot, "logs").apply { mkdirs() }

        val baselinePdf = createPdf("evidence-baseline-${System.nanoTime()}.pdf", "alpha beta gamma")
        val comparedPdf = createPdf("evidence-compared-${System.nanoTime()}.pdf", "alpha beta gamma delta")
        baselinePdf.copyTo(File(sourceDir, "baseline.pdf"), overwrite = true)
        comparedPdf.copyTo(File(sourceDir, "compared.pdf"), overwrite = true)
        val importDocx = createDocx("evidence-import-${System.nanoTime()}.docx", listOf("Imported heading", "Imported body paragraph"))
        importDocx.copyTo(File(sourceDir, "import-source.docx"), overwrite = true)
        val imageSource = createImageFile("evidence-image-${System.nanoTime()}.png")
        imageSource.copyTo(File(sourceDir, "import-source.png"), overwrite = true)

        val document = document(baselinePdf)
        val textExport = repository.exportDocumentAsText(document, File(outputDir, "document.txt"))
        val markdownExport = repository.exportDocumentAsMarkdown(document, File(outputDir, "document.md"))
        val wordExport = repository.exportDocumentAsWord(document, File(outputDir, "document.docx"))
        val imageExport = repository.exportDocumentAsImages(document, File(outputDir, "images"), ExportImageFormat.Png)
        val importedFromDocx = repository.importSourceAsPdf(importDocx, "imported-from-docx.pdf")
        val importedFromImage = repository.importSourceAsPdf(imageSource, "imported-from-image.pdf")
        val merged = repository.mergeSourcesAsPdf(listOf(baselinePdf, importDocx), "merged.pdf")
        val optimized = repository.optimizeDocument(document, File(outputDir, "optimized.pdf"), PdfOptimizationPreset.Balanced)
        val compareReport = repository.compareDocuments(document, ref(comparedPdf))
        val compareReportMarkdown = repository.exportCompareReport(compareReport.id, File(outputDir, "compare-report.md"))
        val compareReportJson = repository.exportCompareReport(compareReport.id, File(outputDir, "compare-report.json"), CompareReportExportFormat.Json)

        assertThat(textExport.artifacts).isNotEmpty()
        assertThat(markdownExport.artifacts).isNotEmpty()
        assertThat(wordExport.artifacts).isNotEmpty()
        assertThat(imageExport.artifacts).isNotEmpty()

        val importedDocxPath = (importedFromDocx.request as OpenDocumentRequest.FromFile).absolutePath
        val importedImagePath = (importedFromImage.request as OpenDocumentRequest.FromFile).absolutePath
        val mergedPath = (merged.request as OpenDocumentRequest.FromFile).absolutePath
        File(importedDocxPath).copyTo(File(outputDir, "imported-from-docx.pdf"), overwrite = true)
        File(importedImagePath).copyTo(File(outputDir, "imported-from-image.pdf"), overwrite = true)
        File(mergedPath).copyTo(File(outputDir, "merged.pdf"), overwrite = true)

        val manifest = WorkflowEvidenceManifest(
            generatedAtEpochMillis = System.currentTimeMillis(),
            outputs = listOf(
                evidenceEntry("text-export", File(textExport.artifacts.first().path)),
                evidenceEntry("markdown-export", File(markdownExport.artifacts.first().path)),
                evidenceEntry("word-export", File(wordExport.artifacts.first().path)),
                evidenceEntry("image-export", File(imageExport.artifacts.first().path)),
                evidenceEntry("docx-import", File(outputDir, "imported-from-docx.pdf")),
                evidenceEntry("image-import", File(outputDir, "imported-from-image.pdf")),
                evidenceEntry("merge", File(outputDir, "merged.pdf")),
                evidenceEntry("optimize", optimized.destination),
                evidenceEntry("compare-report-markdown", File(compareReportMarkdown.path)),
                evidenceEntry("compare-report-json", File(compareReportJson.path)),
            ),
            compareSummary = compareReport.summary,
            exportLogPath = File(logDir, "activity-events.json").absolutePath,
        )
        File(logDir, "activity-events.json").writeText(json.encodeToString(activityDao.snapshot(json)))
        File(evidenceRoot, "workflow-manifest.json").writeText(json.encodeToString(WorkflowEvidenceManifest.serializer(), manifest))

        assertThat(File(evidenceRoot, "workflow-manifest.json").exists()).isTrue()
        assertThat(File(outputDir, "document.docx").exists()).isTrue()
        assertThat(File(outputDir, "imported-from-docx.pdf").exists()).isTrue()
        assertThat(File(outputDir, "merged.pdf").exists()).isTrue()
        assertThat(File(outputDir, "optimized.pdf").exists()).isTrue()
        assertThat(File(outputDir, "compare-report.json").readText()).contains("\"pageChanges\"")
        PDDocument.load(File(outputDir, "merged.pdf")).use { mergedDocument ->
            assertThat(mergedDocument.numberOfPages).isAtLeast(2)
        }
    }

    private fun repository(
        state: EnterpriseAdminStateModel = EnterpriseAdminStateModel(),
        documentRepository: DocumentRepository = RecordingDocumentRepository(),
        scanImportService: ScanImportService = RecordingScanImportService(),
        activityEventDao: ActivityEventDao = RecordingActivityEventDao(),
    ): WorkflowRepository {
        return DefaultWorkflowRepository(
            context = context,
            compareReportDao = RecordingCompareReportDao(),
            formTemplateDao = RecordingFormTemplateDao(),
            workflowRequestDao = RecordingWorkflowRequestDao(),
            activityEventDao = activityEventDao,
            enterpriseAdminRepository = RecordingEnterpriseAdminRepository(state),
            extractionService = PdfBoxTextExtractionService(),
            ocrSessionStore = OcrSessionStore(json, null),
            documentRepository = documentRepository,
            scanImportService = scanImportService,
            conversionRuntime = DocumentConversionRuntime(listOf(OpenXmlDocxDocumentConversionProvider(context))),
            json = json,
        )
    }

    private fun document(file: File): DocumentModel {
        return DocumentModel(
            sessionId = "session-${file.nameWithoutExtension}",
            documentRef = ref(file),
            pages = listOf(
                PageModel(
                    index = 0,
                    label = "1",
                    contentType = PageContentType.Pdf,
                    sourceDocumentPath = file.absolutePath,
                    sourcePageIndex = 0,
                    widthPoints = PDRectangle.LETTER.width,
                    heightPoints = PDRectangle.LETTER.height,
                ),
            ),
            formDocument = FormDocumentModel(
                fields = listOf(
                    FormFieldModel(
                        name = "vendorName",
                        label = "Vendor name",
                        pageIndex = 0,
                        bounds = NormalizedRect(0.1f, 0.1f, 0.4f, 0.2f),
                        type = FormFieldType.Text,
                        required = true,
                        value = FormFieldValue.Text(""),
                    ),
                    FormFieldModel(
                        name = "signHere",
                        label = "Sign here",
                        pageIndex = 0,
                        bounds = NormalizedRect(0.5f, 0.6f, 0.8f, 0.75f),
                        type = FormFieldType.Signature,
                        required = true,
                        value = FormFieldValue.SignatureValue(signerName = ""),
                    ),
                ),
            ),
        )
    }

    private fun ref(file: File): PdfDocumentRef = PdfDocumentRef(
        uriString = file.toURI().toString(),
        displayName = file.name,
        sourceType = DocumentSourceType.File,
        sourceKey = file.absolutePath,
        workingCopyPath = file.absolutePath,
    )

    private fun createDocx(name: String, paragraphs: List<String>): File {
        val file = File(context.filesDir, name)
        OpenXmlDocxDocumentConversionProvider.writeMinimalDocx(
            destination = file,
            paragraphs = paragraphs.map { DocxParagraph(it) },
        )
        return file
    }

    private fun createImageFile(name: String): File {
        val file = File(context.filesDir, name)
        android.graphics.Bitmap.createBitmap(32, 32, android.graphics.Bitmap.Config.ARGB_8888).apply {
            eraseColor(android.graphics.Color.rgb(0x12, 0x5B, 0xD8))
            compress(android.graphics.Bitmap.CompressFormat.PNG, 100, file.outputStream())
            recycle()
        }
        return file
    }

    private fun createPdf(name: String, text: String): File {
        val file = File(context.filesDir, name)
        PDDocument().use { document ->
            val page = PDPage(PDRectangle.LETTER)
            document.addPage(page)
            PDPageContentStream(document, page).use { stream ->
                stream.beginText()
                stream.setFont(PDType1Font.HELVETICA_BOLD, 18f)
                stream.newLineAtOffset(72f, 700f)
                stream.showText(text)
                stream.endText()
            }
            document.save(file)
        }
        return file
    }

    private fun evidenceEntry(flow: String, file: File): WorkflowEvidenceEntry {
        return WorkflowEvidenceEntry(
            flow = flow,
            path = file.absolutePath,
            exists = file.exists(),
            sizeBytes = file.length(),
        )
    }

    private fun resolveWorkflowEvidenceRoot(): File {
        val workingDirectory = File(System.getProperty("user.dir") ?: ".")
        val moduleDir = if (workingDirectory.name == "editor-core") workingDirectory else File(workingDirectory, "editor-core")
        return File(moduleDir, "build/reports/workflow-evidence")
    }
}

private class RecordingCompareReportDao : CompareReportDao {
    private val items = linkedMapOf<String, CompareReportEntity>()
    override suspend fun upsert(entity: CompareReportEntity) { items[entity.id] = entity }
    override suspend fun forDocument(documentKey: String): List<CompareReportEntity> = items.values.filter { it.documentKey == documentKey }.sortedByDescending { it.createdAtEpochMillis }
    override suspend fun byId(reportId: String): CompareReportEntity? = items[reportId]
}

private class RecordingFormTemplateDao : FormTemplateDao {
    private val items = linkedMapOf<String, FormTemplateEntity>()
    override suspend fun upsert(entity: FormTemplateEntity) { items[entity.id] = entity }
    override suspend fun forDocument(documentKey: String): List<FormTemplateEntity> = items.values.filter { it.documentKey == documentKey }.sortedByDescending { it.updatedAtEpochMillis }
    override suspend fun byId(templateId: String): FormTemplateEntity? = items[templateId]
}

private class RecordingWorkflowRequestDao : WorkflowRequestDao {
    private val items = linkedMapOf<String, WorkflowRequestEntity>()
    override suspend fun upsert(entity: WorkflowRequestEntity) { items[entity.id] = entity }
    override suspend fun forDocument(documentKey: String): List<WorkflowRequestEntity> = items.values.filter { it.documentKey == documentKey }.sortedByDescending { it.updatedAtEpochMillis }
    override suspend fun byId(requestId: String): WorkflowRequestEntity? = items[requestId]
}

private class RecordingActivityEventDao : ActivityEventDao {
    private val items = linkedMapOf<String, ActivityEventEntity>()
    override suspend fun upsert(entity: ActivityEventEntity) { items[entity.id] = entity }
    override suspend fun upsertAll(entities: List<ActivityEventEntity>) { entities.forEach { upsert(it) } }
    override suspend fun forDocument(documentKey: String): List<ActivityEventEntity> = items.values.filter { it.documentKey == documentKey }.sortedByDescending { it.createdAtEpochMillis }
    override suspend fun deleteForDocument(documentKey: String) { items.entries.removeIf { it.value.documentKey == documentKey } }
    fun allItems(): List<ActivityEventEntity> = items.values.toList()
    fun snapshot(json: Json): List<ActivityEventModel> = items.values.map { entity ->
        ActivityEventModel(
            id = entity.id,
            documentKey = entity.documentKey,
            type = ActivityEventType.valueOf(entity.type),
            actor = entity.actor,
            summary = entity.summary,
            createdAtEpochMillis = entity.createdAtEpochMillis,
            threadId = entity.threadId,
            metadata = json.decodeFromString(MapSerializer(String.serializer(), String.serializer()), entity.metadataJson),
            remoteVersion = entity.remoteVersion,
            serverUpdatedAtEpochMillis = entity.serverUpdatedAtEpochMillis,
            lastSyncedAtEpochMillis = entity.lastSyncedAtEpochMillis,
        )
    }
}

private class RecordingEnterpriseAdminRepository(
    private var state: EnterpriseAdminStateModel,
) : EnterpriseAdminRepository {
    override suspend fun loadState(): EnterpriseAdminStateModel = state
    override suspend fun saveState(state: EnterpriseAdminStateModel) { this.state = state }
    override suspend fun signInPersonal(displayName: String): EnterpriseAdminStateModel = state
    override suspend fun signInEnterprise(email: String, tenant: TenantConfigurationModel): EnterpriseAdminStateModel = state
    override suspend fun signOut(): EnterpriseAdminStateModel = state
    override suspend fun refreshRemoteState(force: Boolean): EnterpriseAdminStateModel = state
    override suspend fun refreshSessionIfNeeded(): EnterpriseAdminStateModel = state
    override suspend fun resolveEntitlements(state: EnterpriseAdminStateModel): EntitlementStateModel = EntitlementStateModel(state.plan, emptySet())
    override suspend fun queueTelemetry(event: TelemetryEventModel) = Unit
    override suspend fun pendingTelemetry(): List<TelemetryEventModel> = emptyList()
    override suspend fun flushTelemetry(): Int = 0
    override suspend fun diagnosticsBundle(destination: File, appSummary: Map<String, String>): File {
        destination.parentFile?.mkdirs()
        destination.writeText(appSummary.toString())
        return destination
    }
}

private class RecordingDocumentRepository : DocumentRepository {
    override suspend fun open(request: OpenDocumentRequest): DocumentModel = error("Not used")
    override suspend fun importPages(requests: List<OpenDocumentRequest>): List<PageModel> = error("Not used")
    override suspend fun persistDraft(payload: DraftPayload, autosave: Boolean) = Unit
    override suspend fun restoreDraft(sourceKey: String): DraftRestoreResult? = null
    override suspend fun clearDraft(sourceKey: String) = Unit
    override suspend fun save(document: DocumentModel, exportMode: AnnotationExportMode): DocumentModel = document
    override suspend fun saveAs(document: DocumentModel, destination: File, exportMode: AnnotationExportMode): DocumentModel {
        File(document.documentRef.workingCopyPath).copyTo(destination, overwrite = true)
        return document.copy(documentRef = document.documentRef.copy(sourceKey = destination.absolutePath, workingCopyPath = destination.absolutePath))
    }
    override suspend fun split(document: DocumentModel, request: com.aymanelbanhawy.editor.core.organize.SplitRequest, outputDirectory: File): List<File> = emptyList()
    override fun createAutosaveTempFile(sessionId: String): File = File.createTempFile(sessionId, ".json")
}

private class RecordingScanImportService : ScanImportService {
    override suspend fun importImages(imageFiles: List<File>, options: ScanImportOptions): OpenDocumentRequest {
        val destination = File(imageFiles.first().parentFile, options.displayName)
        imageFiles.first().copyTo(destination, overwrite = true)
        return OpenDocumentRequest.FromFile(destination.absolutePath, displayNameOverride = destination.name)
    }
}

@Serializable
private data class WorkflowEvidenceEntry(
    val flow: String,
    val path: String,
    val exists: Boolean,
    val sizeBytes: Long,
)

@Serializable
private data class WorkflowEvidenceManifest(
    val generatedAtEpochMillis: Long,
    val outputs: List<WorkflowEvidenceEntry>,
    val compareSummary: CompareSummaryModel,
    val exportLogPath: String,
)




package com.aymanelbanhawy.editor.core.workflow

import android.content.Context
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
import com.aymanelbanhawy.editor.core.enterprise.CollaborationScope
import com.aymanelbanhawy.editor.core.enterprise.EnterpriseAdminRepository
import com.aymanelbanhawy.editor.core.enterprise.EnterpriseAdminStateModel
import com.aymanelbanhawy.editor.core.forms.FormFieldType
import com.aymanelbanhawy.editor.core.model.DocumentModel
import com.aymanelbanhawy.editor.core.model.DocumentSourceType
import com.aymanelbanhawy.editor.core.model.NormalizedRect
import com.aymanelbanhawy.editor.core.model.OpenDocumentRequest
import com.aymanelbanhawy.editor.core.ocr.OcrSessionStore
import com.aymanelbanhawy.editor.core.repository.DocumentRepository
import com.aymanelbanhawy.editor.core.scan.ScanImportService
import com.aymanelbanhawy.editor.core.model.PdfDocumentRef
import com.aymanelbanhawy.editor.core.search.PdfBoxTextExtractionService
import com.aymanelbanhawy.editor.core.search.IndexedPageContent
import java.io.File
import java.util.UUID
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

class DefaultWorkflowRepository(
    private val context: Context,
    private val compareReportDao: CompareReportDao,
    private val formTemplateDao: FormTemplateDao,
    private val workflowRequestDao: WorkflowRequestDao,
    private val activityEventDao: ActivityEventDao,
    private val enterpriseAdminRepository: EnterpriseAdminRepository,
    private val extractionService: PdfBoxTextExtractionService,
    private val ocrSessionStore: OcrSessionStore,
    private val documentRepository: DocumentRepository,
    private val scanImportService: ScanImportService,
    private val conversionRuntime: DocumentConversionRuntime,
    private val json: Json,
) : WorkflowRepository {

    private val fileWorkflowService = DocumentFileWorkflowService(
        context = context,
        extractionService = extractionService,
        ocrSessionStore = ocrSessionStore,
        documentRepository = documentRepository,
        scanImportService = scanImportService,
        conversionRuntime = conversionRuntime,
        json = json,
    )

    override suspend fun compareReports(documentKey: String): List<CompareReportModel> {
        return compareReportDao.forDocument(documentKey).map { it.toModel(json) }
    }

    override suspend fun compareDocuments(document: DocumentModel, comparedRef: PdfDocumentRef): CompareReportModel {
        val basePages = extractionService.extract(document.documentRef)
        val comparedPages = extractionService.extract(comparedRef)
        val report = buildCompareReport(
            documentKey = document.documentRef.sourceKey,
            baselineDisplayName = document.documentRef.displayName,
            comparedDisplayName = comparedRef.displayName,
            comparedFilePath = comparedRef.workingCopyPath,
            basePages = basePages,
            comparedPages = comparedPages,
        )
        compareReportDao.upsert(report.toEntity(json))
        recordActivity(
            ActivityEventModel(
                id = UUID.randomUUID().toString(),
                documentKey = document.documentRef.sourceKey,
                type = ActivityEventType.Compared,
                actor = actorName(enterpriseAdminRepository.loadState()),
                summary = "Compared ${document.documentRef.displayName} with ${comparedRef.displayName}",
                createdAtEpochMillis = System.currentTimeMillis(),
                metadata = mapOf("changedPages" to report.summary.changedPages.toString()),
            ),
        )
        return report
    }

    override suspend fun compareWithSnapshot(document: DocumentModel, snapshotPath: String, snapshotLabel: String): CompareReportModel {
        return compareDocuments(
            document = document,
            comparedRef = PdfDocumentRef(
                uriString = File(snapshotPath).toURI().toString(),
                displayName = snapshotLabel,
                sourceType = DocumentSourceType.File,
                sourceKey = snapshotPath,
                workingCopyPath = snapshotPath,
            ),
        )
    }

    override suspend fun exportCompareReport(reportId: String, destination: File, format: CompareReportExportFormat): ExportArtifactModel {
        val report = requireNotNull(compareReportDao.byId(reportId)?.toModel(json)) { "Compare report not found." }
        requireExportAllowed(report.documentKey, "compare report export")
        return fileWorkflowService.exportCompareReport(report, destination, format).also { artifact ->
            recordExportActivity(
                documentKey = report.documentKey,
                summary = "Exported compare report for ${report.baselineDisplayName}",
                metadata = mapOf("format" to format.name, "path" to artifact.path),
            )
        }
    }

    override suspend fun exportDocumentAsText(document: DocumentModel, destination: File): ExportBundleResult {
        requireExportAllowed(document.documentRef.sourceKey, "text export")
        return fileWorkflowService.exportDocumentAsText(document, destination).also { bundle ->
            recordExportActivity(
                documentKey = document.documentRef.sourceKey,
                summary = "Exported ${document.documentRef.displayName} as text",
                metadata = mapOf("artifactCount" to bundle.artifacts.size.toString(), "destination" to destination.absolutePath),
            )
        }
    }

    override suspend fun exportDocumentAsMarkdown(document: DocumentModel, destination: File): ExportBundleResult {
        requireExportAllowed(document.documentRef.sourceKey, "markdown export")
        return fileWorkflowService.exportDocumentAsMarkdown(document, destination).also { bundle ->
            recordExportActivity(
                documentKey = document.documentRef.sourceKey,
                summary = "Exported ${document.documentRef.displayName} as markdown",
                metadata = mapOf("artifactCount" to bundle.artifacts.size.toString(), "destination" to destination.absolutePath),
            )
        }
    }

    override suspend fun exportDocumentAsWord(document: DocumentModel, destination: File): ExportBundleResult {
        requireExportAllowed(document.documentRef.sourceKey, "word export")
        return fileWorkflowService.exportDocumentAsWord(document, destination).also { bundle ->
            recordExportActivity(
                documentKey = document.documentRef.sourceKey,
                summary = "Exported ${document.documentRef.displayName} as Word",
                metadata = mapOf("artifactCount" to bundle.artifacts.size.toString(), "destination" to destination.absolutePath),
            )
        }
    }

    override suspend fun exportDocumentAsImages(document: DocumentModel, outputDirectory: File, format: ExportImageFormat): ExportBundleResult {
        requireExportAllowed(document.documentRef.sourceKey, "image export")
        return fileWorkflowService.exportDocumentAsImages(document, outputDirectory, format).also { bundle ->
            recordExportActivity(
                documentKey = document.documentRef.sourceKey,
                summary = "Exported ${document.documentRef.displayName} as page images",
                metadata = mapOf(
                    "artifactCount" to bundle.artifacts.size.toString(),
                    "format" to format.name,
                    "outputDirectory" to outputDirectory.absolutePath,
                ),
            )
        }
    }

    override suspend fun createPdfFromImages(imageFiles: List<File>, displayName: String): CreatedPdfResult {
        return fileWorkflowService.createPdfFromImages(imageFiles, displayName).also { created ->
            val request = created.request as? OpenDocumentRequest.FromFile
            recordExportActivity(
                documentKey = request?.absolutePath ?: displayName,
                summary = "Created PDF from ${created.sourceImageCount} image(s)",
                metadata = mapOf("displayName" to displayName, "sourceCount" to created.sourceImageCount.toString()),
            )
        }
    }

    override suspend fun importSourceAsPdf(source: File, displayName: String): ImportedPdfResult {
        return fileWorkflowService.importSourceAsPdf(source, displayName).also { imported ->
            val request = imported.request as? OpenDocumentRequest.FromFile
            recordExportActivity(
                documentKey = request?.absolutePath ?: source.absolutePath,
                summary = "Imported ${source.name} into PDF",
                metadata = mapOf("sourceFormat" to imported.sourceFormat.name, "destination" to (request?.absolutePath ?: "")),
            )
        }
    }

    override suspend fun mergeSourcesAsPdf(sources: List<File>, displayName: String): ImportedPdfResult {
        requireExportAllowed(sources.firstOrNull()?.absolutePath ?: displayName, "merge export")
        return fileWorkflowService.mergeSourcesAsPdf(sources, displayName).also { imported ->
            val request = imported.request as? OpenDocumentRequest.FromFile
            recordExportActivity(
                documentKey = request?.absolutePath ?: displayName,
                summary = "Merged ${sources.size} source file(s) into ${displayName}",
                metadata = mapOf("sourceCount" to sources.size.toString(), "destination" to (request?.absolutePath ?: "")),
            )
        }
    }

    override suspend fun optimizeDocument(document: DocumentModel, destination: File, preset: PdfOptimizationPreset): OptimizationResult {
        requireExportAllowed(document.documentRef.sourceKey, "optimization export")
        return fileWorkflowService.optimizeDocument(document, destination, preset).also { result ->
            recordExportActivity(
                documentKey = document.documentRef.sourceKey,
                summary = "Optimized ${document.documentRef.displayName} with ${preset.name}",
                metadata = mapOf(
                    "preset" to preset.name,
                    "originalBytes" to result.originalSizeBytes.toString(),
                    "optimizedBytes" to result.optimizedSizeBytes.toString(),
                    "destination" to destination.absolutePath,
                ),
            )
        }
    }
    override suspend fun formTemplates(documentKey: String): List<FormTemplateModel> {
        return formTemplateDao.forDocument(documentKey).map { it.toModel(json) }
    }

    override suspend fun createFormTemplate(document: DocumentModel, name: String): FormTemplateModel {
        val now = System.currentTimeMillis()
        val schema = FormTemplateSchemaModel(
            documentDisplayName = document.documentRef.displayName,
            fieldMappings = document.formDocument.fields.map { field ->
                FormTemplateFieldModel(
                    fieldName = field.name,
                    label = field.label.ifBlank { field.name },
                    fieldType = field.type,
                    required = field.required,
                    pageIndex = field.pageIndex,
                    bounds = field.bounds,
                    options = field.options.map { it.value },
                    defaultValue = when (val value = field.value) {
                        is com.aymanelbanhawy.editor.core.forms.FormFieldValue.Text -> value.text
                        is com.aymanelbanhawy.editor.core.forms.FormFieldValue.Choice -> value.selected
                        is com.aymanelbanhawy.editor.core.forms.FormFieldValue.BooleanValue -> value.checked.toString()
                        is com.aymanelbanhawy.editor.core.forms.FormFieldValue.SignatureValue -> value.signerName
                    },
                )
            },
        )
        val template = FormTemplateModel(
            id = UUID.randomUUID().toString(),
            documentKey = document.documentRef.sourceKey,
            name = name.ifBlank { "${document.documentRef.displayName} Template" },
            schema = schema,
            createdAtEpochMillis = now,
            updatedAtEpochMillis = now,
        )
        formTemplateDao.upsert(template.toEntity(json))
        recordActivity(
            ActivityEventModel(
                id = UUID.randomUUID().toString(),
                documentKey = document.documentRef.sourceKey,
                type = ActivityEventType.FormTemplateSaved,
                actor = actorName(enterpriseAdminRepository.loadState()),
                summary = "Saved form template ${template.name}",
                createdAtEpochMillis = now,
            ),
        )
        return template
    }

    override suspend fun exportFormTemplate(templateId: String, destination: File): File {
        val template = requireNotNull(formTemplateDao.byId(templateId)?.toModel(json)) { "Form template not found." }
        destination.parentFile?.mkdirs()
        destination.writeText(json.encodeToString(FormTemplateModel.serializer(), template))
        val updated = template.copy(updatedAtEpochMillis = System.currentTimeMillis(), exportedSchemaPath = destination.absolutePath)
        formTemplateDao.upsert(updated.toEntity(json))
        return destination
    }

    override suspend fun importFormTemplate(documentKey: String, source: File): FormTemplateModel {
        require(source.exists()) { "Template file not found." }
        val imported = json.decodeFromString(FormTemplateModel.serializer(), source.readText())
        val now = System.currentTimeMillis()
        val normalized = imported.copy(
            id = UUID.randomUUID().toString(),
            documentKey = documentKey,
            createdAtEpochMillis = now,
            updatedAtEpochMillis = now,
            exportedSchemaPath = source.absolutePath,
        )
        formTemplateDao.upsert(normalized.toEntity(json))
        return normalized
    }

    override suspend fun workflowRequests(documentKey: String): List<WorkflowRequestModel> {
        return workflowRequestDao.forDocument(documentKey).map { it.toModel(json) }
    }

    override suspend fun createSignatureRequest(document: DocumentModel, title: String, recipients: List<WorkflowRecipientModel>, reminderIntervalDays: Int?, expiresAtEpochMillis: Long?): WorkflowRequestModel {
        validateRecipients(recipients, enterpriseAdminRepository.loadState())
        val now = System.currentTimeMillis()
        val signatureFields = document.formDocument.fields
            .filter { it.type == FormFieldType.Signature || it.type == FormFieldType.Date || it.type == FormFieldType.Text }
            .map { field ->
                RequestFieldAssignmentModel(
                    fieldName = field.name,
                    label = field.label.ifBlank { field.name },
                    recipientEmail = recipients.sortedBy { it.order }.firstOrNull()?.email.orEmpty(),
                    kind = when (field.type) {
                        FormFieldType.Signature -> RequestFieldKind.Signature
                        FormFieldType.Date -> RequestFieldKind.Date
                        else -> RequestFieldKind.Text
                    },
                    pageIndex = field.pageIndex,
                    bounds = field.bounds,
                )
            }
        val request = WorkflowRequestModel(
            id = UUID.randomUUID().toString(),
            documentKey = document.documentRef.sourceKey,
            type = WorkflowRequestType.Signature,
            title = title.ifBlank { "Signature Request" },
            createdBy = actorName(enterpriseAdminRepository.loadState()),
            createdAtEpochMillis = now,
            updatedAtEpochMillis = now,
            recipients = recipients.sortedBy { it.order },
            assignedFields = signatureFields,
            signingOrderEnforced = true,
            status = WorkflowRequestStatus.Sent,
            expiresAtEpochMillis = clampExpiration(expiresAtEpochMillis, enterpriseAdminRepository.loadState()),
            reminderSchedule = reminderIntervalDays?.let { ReminderScheduleModel(it, now + (it * DAY_MS)) },
            metadata = mapOf("requestKind" to "signature"),
        )
        workflowRequestDao.upsert(request.toEntity(json))
        recordActivity(
            ActivityEventModel(
                id = UUID.randomUUID().toString(),
                documentKey = request.documentKey,
                type = ActivityEventType.SignatureRequested,
                actor = request.createdBy,
                summary = "Sent signature request ${request.title}",
                createdAtEpochMillis = now,
                metadata = mapOf("recipientCount" to recipients.size.toString()),
            ),
        )
        return request
    }

    override suspend fun createFormRequest(document: DocumentModel, templateId: String, title: String, recipients: List<WorkflowRecipientModel>, reminderIntervalDays: Int?, expiresAtEpochMillis: Long?): WorkflowRequestModel {
        val state = enterpriseAdminRepository.loadState()
        validateRecipients(recipients, state)
        val template = requireNotNull(formTemplateDao.byId(templateId)?.toModel(json)) { "Form template not found." }
        val now = System.currentTimeMillis()
        val assignments = template.schema.fieldMappings.map { field ->
            RequestFieldAssignmentModel(
                fieldName = field.fieldName,
                label = field.label,
                recipientEmail = recipients.sortedBy { it.order }.firstOrNull()?.email.orEmpty(),
                kind = when (field.fieldType) {
                    FormFieldType.Signature -> RequestFieldKind.Signature
                    FormFieldType.Date -> RequestFieldKind.Date
                    else -> RequestFieldKind.Text
                },
                pageIndex = field.pageIndex,
                bounds = field.bounds,
                required = field.required,
            )
        }
        val request = WorkflowRequestModel(
            id = UUID.randomUUID().toString(),
            documentKey = document.documentRef.sourceKey,
            type = WorkflowRequestType.FormFill,
            title = title.ifBlank { "Form Request" },
            createdBy = actorName(state),
            createdAtEpochMillis = now,
            updatedAtEpochMillis = now,
            recipients = recipients.sortedBy { it.order },
            assignedFields = assignments,
            templateId = template.id,
            signingOrderEnforced = false,
            status = WorkflowRequestStatus.Sent,
            expiresAtEpochMillis = clampExpiration(expiresAtEpochMillis, state),
            reminderSchedule = reminderIntervalDays?.let { ReminderScheduleModel(it, now + (it * DAY_MS)) },
            metadata = mapOf("requestKind" to "form", "templateName" to template.name),
        )
        workflowRequestDao.upsert(request.toEntity(json))
        recordActivity(
            ActivityEventModel(
                id = UUID.randomUUID().toString(),
                documentKey = request.documentKey,
                type = ActivityEventType.FormRequested,
                actor = request.createdBy,
                summary = "Sent form request ${request.title}",
                createdAtEpochMillis = now,
                metadata = mapOf("templateId" to template.id),
            ),
        )
        return request
    }

    override suspend fun updateRequestResponse(requestId: String, response: WorkflowResponseModel): WorkflowRequestModel? {
        val existing = workflowRequestDao.byId(requestId)?.toModel(json) ?: return null
        val now = System.currentTimeMillis()
        val nextResponses = existing.responses.filterNot { it.recipientEmail.equals(response.recipientEmail, ignoreCase = true) } + response
        val nextSubmissions = if (existing.type == WorkflowRequestType.FormFill && response.fieldValues.isNotEmpty()) {
            existing.submissions + FormSubmissionMetadataModel(
                id = UUID.randomUUID().toString(),
                templateId = existing.templateId.orEmpty(),
                submittedBy = response.recipientEmail,
                submittedAtEpochMillis = response.actedAtEpochMillis,
                values = response.fieldValues,
            )
        } else {
            existing.submissions
        }
        val nextStatus = when {
            response.status == WorkflowRequestStatus.Declined -> WorkflowRequestStatus.Declined
            nextResponses.size >= existing.recipients.size -> WorkflowRequestStatus.Completed
            nextResponses.isNotEmpty() -> WorkflowRequestStatus.InProgress
            else -> existing.status
        }
        val updated = existing.copy(
            updatedAtEpochMillis = now,
            responses = nextResponses.sortedBy { it.actedAtEpochMillis },
            submissions = nextSubmissions,
            status = nextStatus,
        )
        workflowRequestDao.upsert(updated.toEntity(json))
        recordActivity(
            ActivityEventModel(
                id = UUID.randomUUID().toString(),
                documentKey = updated.documentKey,
                type = ActivityEventType.RequestResponded,
                actor = response.recipientEmail,
                summary = "${response.recipientEmail} updated ${updated.title}",
                createdAtEpochMillis = now,
                metadata = mapOf("status" to response.status.name),
            ),
        )
        return updated
    }

    override suspend fun sendReminder(requestId: String): WorkflowRequestModel? {
        val existing = workflowRequestDao.byId(requestId)?.toModel(json) ?: return null
        val schedule = existing.reminderSchedule ?: return existing
        val now = System.currentTimeMillis()
        val updated = existing.copy(
            updatedAtEpochMillis = now,
            reminderSchedule = schedule.copy(
                lastReminderAtEpochMillis = now,
                nextReminderAtEpochMillis = now + (schedule.intervalDays * DAY_MS),
            ),
        )
        workflowRequestDao.upsert(updated.toEntity(json))
        recordActivity(
            ActivityEventModel(
                id = UUID.randomUUID().toString(),
                documentKey = updated.documentKey,
                type = ActivityEventType.RequestReminderSent,
                actor = updated.createdBy,
                summary = "Sent reminder for ${updated.title}",
                createdAtEpochMillis = now,
            ),
        )
        return updated
    }

    private suspend fun recordActivity(event: ActivityEventModel) {
        activityEventDao.upsert(event.toEntity(json))
    }

    private suspend fun requireExportAllowed(documentKey: String, action: String) {
        val state = enterpriseAdminRepository.loadState()
        if (!state.adminPolicy.restrictExport) return
        recordActivity(
            ActivityEventModel(
                id = UUID.randomUUID().toString(),
                documentKey = documentKey,
                type = ActivityEventType.Exported,
                actor = actorName(state),
                summary = "Blocked $action by tenant policy",
                createdAtEpochMillis = System.currentTimeMillis(),
                metadata = mapOf("allowed" to "false", "policy" to "restrictExport", "action" to action),
            ),
        )
        throw SecurityException("Export is restricted by tenant policy.")
    }

    private suspend fun recordExportActivity(documentKey: String, summary: String, metadata: Map<String, String>) {
        val state = enterpriseAdminRepository.loadState()
        recordActivity(
            ActivityEventModel(
                id = UUID.randomUUID().toString(),
                documentKey = documentKey,
                type = ActivityEventType.Exported,
                actor = actorName(state),
                summary = summary,
                createdAtEpochMillis = System.currentTimeMillis(),
                metadata = metadata + mapOf("allowed" to "true"),
            ),
        )
    }

    private fun buildCompareReport(
        documentKey: String,
        baselineDisplayName: String,
        comparedDisplayName: String,
        comparedFilePath: String,
        basePages: List<IndexedPageContent>,
        comparedPages: List<IndexedPageContent>,
    ): CompareReportModel {
        val pageCount = maxOf(basePages.size, comparedPages.size)
        val pageChanges = mutableListOf<ComparePageChangeModel>()
        repeat(pageCount) { pageIndex ->
            val basePage = basePages.getOrNull(pageIndex)
            val comparedPage = comparedPages.getOrNull(pageIndex)
            when {
                basePage == null && comparedPage != null -> {
                    pageChanges += ComparePageChangeModel(
                        pageIndex = pageIndex,
                        changeType = CompareChangeType.Added,
                        summary = "Page ${pageIndex + 1} was added.",
                        addedLines = comparedPage.pageText.lines().filter { it.isNotBlank() },
                        markers = comparedPage.blocks.take(12).map {
                            CompareMarkerModel(pageIndex, it.bounds, CompareChangeType.Added, it.text.take(120))
                        }.ifEmpty {
                            listOf(CompareMarkerModel(pageIndex, NormalizedRect(0f, 0f, 1f, 1f), CompareChangeType.Added, "Added page"))
                        },
                    )
                }
                basePage != null && comparedPage == null -> {
                    pageChanges += ComparePageChangeModel(
                        pageIndex = pageIndex,
                        changeType = CompareChangeType.Removed,
                        summary = "Page ${pageIndex + 1} was removed.",
                        removedLines = basePage.pageText.lines().filter { it.isNotBlank() },
                        markers = listOf(CompareMarkerModel(pageIndex, NormalizedRect(0f, 0f, 1f, 1f), CompareChangeType.Removed, "Removed page")),
                    )
                }
                basePage != null && comparedPage != null -> {
                    val baseLines = normalizeLines(basePage.pageText)
                    val comparedLines = normalizeLines(comparedPage.pageText)
                    if (baseLines != comparedLines) {
                        val addedLines = comparedLines.filterNot { it in baseLines }
                        val removedLines = baseLines.filterNot { it in comparedLines }
                        val changedBlocks = comparedPage.blocks.filter { block ->
                            val normalized = block.text.trim().replace(Regex("\\s+"), " ")
                            normalized.isNotBlank() && normalized !in baseLines
                        }
                        pageChanges += ComparePageChangeModel(
                            pageIndex = pageIndex,
                            changeType = CompareChangeType.Modified,
                            summary = "Page ${pageIndex + 1} changed with ${addedLines.size} additions and ${removedLines.size} removals.",
                            addedLines = addedLines,
                            removedLines = removedLines,
                            markers = changedBlocks.take(16).map {
                                CompareMarkerModel(pageIndex, it.bounds, CompareChangeType.Modified, it.text.take(120))
                            }.ifEmpty {
                                listOf(CompareMarkerModel(pageIndex, NormalizedRect(0f, 0f, 1f, 1f), CompareChangeType.Modified, "Page content changed"))
                            },
                        )
                    }
                }
            }
        }
        val summary = CompareSummaryModel(
            totalPagesCompared = pageCount,
            changedPages = pageChanges.size,
            addedLineCount = pageChanges.sumOf { it.addedLines.size },
            removedLineCount = pageChanges.sumOf { it.removedLines.size },
            summaryText = if (pageChanges.isEmpty()) {
                "No textual differences were detected."
            } else {
                "Detected ${pageChanges.size} changed page(s), ${pageChanges.sumOf { it.addedLines.size }} added line(s), and ${pageChanges.sumOf { it.removedLines.size }} removed line(s)."
            },
        )
        return CompareReportModel(
            id = UUID.randomUUID().toString(),
            documentKey = documentKey,
            baselineDisplayName = baselineDisplayName,
            comparedDisplayName = comparedDisplayName,
            createdAtEpochMillis = System.currentTimeMillis(),
            summary = summary,
            pageChanges = pageChanges,
            comparedFilePath = comparedFilePath,
        )
    }

    private fun validateRecipients(recipients: List<WorkflowRecipientModel>, state: EnterpriseAdminStateModel) {
        require(recipients.isNotEmpty()) { "At least one recipient is required." }
        require(recipients.map { it.email.lowercase() }.distinct().size == recipients.size) { "Duplicate recipients are not allowed." }
        val domain = state.tenantConfiguration.domain.trim().lowercase()
        val externalRecipients = recipients.filterNot { recipient ->
            domain.isBlank() || recipient.email.substringAfter('@', "").lowercase() == domain
        }
        if (!state.adminPolicy.allowExternalSharing && externalRecipients.isNotEmpty()) {
            throw SecurityException("External recipients are blocked by tenant policy.")
        }
        if (state.adminPolicy.collaborationScope == CollaborationScope.TenantOnly && externalRecipients.isNotEmpty()) {
            throw SecurityException("Only tenant recipients are allowed for this workflow.")
        }
    }

    private fun clampExpiration(expiresAtEpochMillis: Long?, state: EnterpriseAdminStateModel): Long? {
        if (expiresAtEpochMillis == null) return null
        val retentionLimit = System.currentTimeMillis() + (state.adminPolicy.retentionDays * DAY_MS)
        return minOf(expiresAtEpochMillis, retentionLimit)
    }

    private fun actorName(state: EnterpriseAdminStateModel): String {
        return state.authSession.displayName.ifBlank { state.authSession.email.ifBlank { "Guest" } }
    }

    private fun normalizeLines(text: String): List<String> {
        return text.lineSequence().map { it.trim().replace(Regex("\\s+"), " ") }.filter { it.isNotBlank() }.toList()
    }

    companion object {
        private const val DAY_MS = 24L * 60L * 60L * 1000L
    }
}

private fun CompareReportModel.toEntity(json: Json): CompareReportEntity = CompareReportEntity(
    id = id,
    documentKey = documentKey,
    baselineDisplayName = baselineDisplayName,
    comparedDisplayName = comparedDisplayName,
    createdAtEpochMillis = createdAtEpochMillis,
    summaryJson = json.encodeToString(CompareSummaryModel.serializer(), summary),
    pageChangesJson = json.encodeToString(ListSerializer(ComparePageChangeModel.serializer()), pageChanges),
    comparedFilePath = comparedFilePath,
)

private fun CompareReportEntity.toModel(json: Json): CompareReportModel = CompareReportModel(
    id = id,
    documentKey = documentKey,
    baselineDisplayName = baselineDisplayName,
    comparedDisplayName = comparedDisplayName,
    createdAtEpochMillis = createdAtEpochMillis,
    summary = json.decodeFromString(CompareSummaryModel.serializer(), summaryJson),
    pageChanges = json.decodeFromString(ListSerializer(ComparePageChangeModel.serializer()), pageChangesJson),
    comparedFilePath = comparedFilePath,
)

private fun FormTemplateModel.toEntity(json: Json): FormTemplateEntity = FormTemplateEntity(
    id = id,
    documentKey = documentKey,
    name = name,
    schemaJson = json.encodeToString(FormTemplateSchemaModel.serializer(), schema),
    createdAtEpochMillis = createdAtEpochMillis,
    updatedAtEpochMillis = updatedAtEpochMillis,
    exportedSchemaPath = exportedSchemaPath,
)

private fun FormTemplateEntity.toModel(json: Json): FormTemplateModel = FormTemplateModel(
    id = id,
    documentKey = documentKey,
    name = name,
    schema = json.decodeFromString(FormTemplateSchemaModel.serializer(), schemaJson),
    createdAtEpochMillis = createdAtEpochMillis,
    updatedAtEpochMillis = updatedAtEpochMillis,
    exportedSchemaPath = exportedSchemaPath,
)

private fun WorkflowRequestModel.toEntity(json: Json): WorkflowRequestEntity = WorkflowRequestEntity(
    id = id,
    documentKey = documentKey,
    type = type.name,
    title = title,
    createdBy = createdBy,
    createdAtEpochMillis = createdAtEpochMillis,
    updatedAtEpochMillis = updatedAtEpochMillis,
    recipientsJson = json.encodeToString(ListSerializer(WorkflowRecipientModel.serializer()), recipients),
    assignedFieldsJson = json.encodeToString(ListSerializer(RequestFieldAssignmentModel.serializer()), assignedFields),
    templateId = templateId,
    signingOrderEnforced = signingOrderEnforced,
    status = status.name,
    expiresAtEpochMillis = expiresAtEpochMillis,
    reminderScheduleJson = reminderSchedule?.let { json.encodeToString(ReminderScheduleModel.serializer(), it) },
    responsesJson = json.encodeToString(ListSerializer(WorkflowResponseModel.serializer()), responses),
    submissionsJson = json.encodeToString(ListSerializer(FormSubmissionMetadataModel.serializer()), submissions),
    metadataJson = json.encodeToString(MapSerializer(String.serializer(), String.serializer()), metadata),
)

private fun WorkflowRequestEntity.toModel(json: Json): WorkflowRequestModel = WorkflowRequestModel(
    id = id,
    documentKey = documentKey,
    type = WorkflowRequestType.valueOf(type),
    title = title,
    createdBy = createdBy,
    createdAtEpochMillis = createdAtEpochMillis,
    updatedAtEpochMillis = updatedAtEpochMillis,
    recipients = json.decodeFromString(ListSerializer(WorkflowRecipientModel.serializer()), recipientsJson),
    assignedFields = json.decodeFromString(ListSerializer(RequestFieldAssignmentModel.serializer()), assignedFieldsJson),
    templateId = templateId,
    signingOrderEnforced = signingOrderEnforced,
    status = WorkflowRequestStatus.valueOf(status),
    expiresAtEpochMillis = expiresAtEpochMillis,
    reminderSchedule = reminderScheduleJson?.let { json.decodeFromString(ReminderScheduleModel.serializer(), it) },
    responses = json.decodeFromString(ListSerializer(WorkflowResponseModel.serializer()), responsesJson),
    submissions = json.decodeFromString(ListSerializer(FormSubmissionMetadataModel.serializer()), submissionsJson),
    metadata = json.decodeFromString(MapSerializer(String.serializer(), String.serializer()), metadataJson),
)

private fun ActivityEventModel.toEntity(json: Json): ActivityEventEntity = ActivityEventEntity(
    id = id,
    documentKey = documentKey,
    type = type.name,
    actor = actor,
    summary = summary,
    createdAtEpochMillis = createdAtEpochMillis,
    threadId = threadId,
    metadataJson = json.encodeToString(MapSerializer(String.serializer(), String.serializer()), metadata),
    remoteVersion = remoteVersion,
    serverUpdatedAtEpochMillis = serverUpdatedAtEpochMillis,
    lastSyncedAtEpochMillis = lastSyncedAtEpochMillis,
)












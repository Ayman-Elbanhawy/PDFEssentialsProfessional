package com.aymanelbanhawy.editor.core.workflow

import com.aymanelbanhawy.editor.core.forms.FormFieldType
import com.aymanelbanhawy.editor.core.model.DocumentModel
import com.aymanelbanhawy.editor.core.model.NormalizedRect
import com.aymanelbanhawy.editor.core.model.OpenDocumentRequest
import com.aymanelbanhawy.editor.core.model.PdfDocumentRef
import java.io.File
import kotlinx.serialization.Serializable

@Serializable
enum class CompareChangeType {
    Added,
    Removed,
    Modified,
}

@Serializable
data class CompareMarkerModel(
    val pageIndex: Int,
    val bounds: NormalizedRect,
    val changeType: CompareChangeType,
    val summary: String,
)

@Serializable
data class ComparePageChangeModel(
    val pageIndex: Int,
    val changeType: CompareChangeType,
    val summary: String,
    val addedLines: List<String> = emptyList(),
    val removedLines: List<String> = emptyList(),
    val markers: List<CompareMarkerModel> = emptyList(),
)

@Serializable
data class CompareSummaryModel(
    val totalPagesCompared: Int,
    val changedPages: Int,
    val addedLineCount: Int,
    val removedLineCount: Int,
    val summaryText: String,
)

@Serializable
data class CompareReportModel(
    val id: String,
    val documentKey: String,
    val baselineDisplayName: String,
    val comparedDisplayName: String,
    val createdAtEpochMillis: Long,
    val summary: CompareSummaryModel,
    val pageChanges: List<ComparePageChangeModel>,
    val comparedFilePath: String,
)

@Serializable
enum class CompareReportExportFormat {
    Markdown,
    Json,
}

@Serializable
data class FormTemplateFieldModel(
    val fieldName: String,
    val label: String,
    val fieldType: FormFieldType,
    val required: Boolean,
    val pageIndex: Int,
    val bounds: NormalizedRect,
    val options: List<String> = emptyList(),
    val defaultValue: String = "",
)

@Serializable
data class FormTemplateSchemaModel(
    val documentDisplayName: String,
    val version: Int = 1,
    val fieldMappings: List<FormTemplateFieldModel>,
)

@Serializable
data class FormTemplateModel(
    val id: String,
    val documentKey: String,
    val name: String,
    val schema: FormTemplateSchemaModel,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
    val exportedSchemaPath: String? = null,
)

@Serializable
enum class WorkflowRequestType {
    Signature,
    FormFill,
}

@Serializable
enum class WorkflowRecipientRole {
    Signer,
    Approver,
    Viewer,
    Submitter,
}

@Serializable
data class WorkflowRecipientModel(
    val email: String,
    val displayName: String,
    val role: WorkflowRecipientRole,
    val order: Int,
)

@Serializable
enum class RequestFieldKind {
    Signature,
    Date,
    Text,
}

@Serializable
data class RequestFieldAssignmentModel(
    val fieldName: String,
    val label: String,
    val recipientEmail: String,
    val kind: RequestFieldKind,
    val pageIndex: Int,
    val bounds: NormalizedRect,
    val required: Boolean = true,
)

@Serializable
enum class WorkflowRequestStatus {
    Draft,
    Prepared,
    Sent,
    InProgress,
    Completed,
    Declined,
    Expired,
    Cancelled,
}

@Serializable
data class ReminderScheduleModel(
    val intervalDays: Int,
    val nextReminderAtEpochMillis: Long?,
    val lastReminderAtEpochMillis: Long? = null,
)

@Serializable
data class WorkflowResponseModel(
    val recipientEmail: String,
    val status: WorkflowRequestStatus,
    val actedAtEpochMillis: Long,
    val note: String = "",
    val fieldValues: Map<String, String> = emptyMap(),
)

@Serializable
data class FormSubmissionMetadataModel(
    val id: String,
    val templateId: String,
    val submittedBy: String,
    val submittedAtEpochMillis: Long,
    val values: Map<String, String>,
)

@Serializable
data class WorkflowRequestModel(
    val id: String,
    val documentKey: String,
    val type: WorkflowRequestType,
    val title: String,
    val createdBy: String,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
    val recipients: List<WorkflowRecipientModel>,
    val assignedFields: List<RequestFieldAssignmentModel> = emptyList(),
    val templateId: String? = null,
    val signingOrderEnforced: Boolean = true,
    val status: WorkflowRequestStatus = WorkflowRequestStatus.Draft,
    val expiresAtEpochMillis: Long? = null,
    val reminderSchedule: ReminderScheduleModel? = null,
    val responses: List<WorkflowResponseModel> = emptyList(),
    val submissions: List<FormSubmissionMetadataModel> = emptyList(),
    val metadata: Map<String, String> = emptyMap(),
)

@Serializable
data class WorkflowStateModel(
    val compareReports: List<CompareReportModel> = emptyList(),
    val formTemplates: List<FormTemplateModel> = emptyList(),
    val requests: List<WorkflowRequestModel> = emptyList(),
)

@Serializable
enum class ExportImageFormat {
    Png,
    Jpeg,
}

@Serializable
enum class PdfOptimizationPreset {
    HighQuality,
    Balanced,
    SmallSize,
    ArchivalSafe,
}

@Serializable
enum class DocumentImportFormat {
    Pdf,
    Docx,
    Text,
    Markdown,
    Image,
}

@Serializable
data class ExportArtifactModel(
    val path: String,
    val displayName: String,
    val mimeType: String,
    val pageIndex: Int? = null,
)

data class ExportBundleResult(
    val title: String,
    val artifacts: List<ExportArtifactModel>,
)

data class OptimizationResult(
    val destination: File,
    val preset: PdfOptimizationPreset,
    val originalSizeBytes: Long,
    val optimizedSizeBytes: Long,
)

data class CreatedPdfResult(
    val request: OpenDocumentRequest,
    val sourceImageCount: Int,
)

data class ImportedPdfResult(
    val request: OpenDocumentRequest,
    val sourceFormat: DocumentImportFormat,
)

interface WorkflowRepository {
    suspend fun compareReports(documentKey: String): List<CompareReportModel>
    suspend fun compareDocuments(document: DocumentModel, comparedRef: PdfDocumentRef): CompareReportModel
    suspend fun compareWithSnapshot(document: DocumentModel, snapshotPath: String, snapshotLabel: String): CompareReportModel
    suspend fun exportCompareReport(reportId: String, destination: File, format: CompareReportExportFormat = CompareReportExportFormat.Markdown): ExportArtifactModel
    suspend fun exportDocumentAsText(document: DocumentModel, destination: File): ExportBundleResult
    suspend fun exportDocumentAsMarkdown(document: DocumentModel, destination: File): ExportBundleResult
    suspend fun exportDocumentAsWord(document: DocumentModel, destination: File): ExportBundleResult
    suspend fun exportDocumentAsImages(document: DocumentModel, outputDirectory: File, format: ExportImageFormat = ExportImageFormat.Png): ExportBundleResult
    suspend fun createPdfFromImages(imageFiles: List<File>, displayName: String): CreatedPdfResult
    suspend fun importSourceAsPdf(source: File, displayName: String): ImportedPdfResult
    suspend fun mergeSourcesAsPdf(sources: List<File>, displayName: String): ImportedPdfResult
    suspend fun optimizeDocument(document: DocumentModel, destination: File, preset: PdfOptimizationPreset): OptimizationResult
    suspend fun formTemplates(documentKey: String): List<FormTemplateModel>
    suspend fun createFormTemplate(document: DocumentModel, name: String): FormTemplateModel
    suspend fun exportFormTemplate(templateId: String, destination: File): File
    suspend fun importFormTemplate(documentKey: String, source: File): FormTemplateModel
    suspend fun workflowRequests(documentKey: String): List<WorkflowRequestModel>
    suspend fun createSignatureRequest(document: DocumentModel, title: String, recipients: List<WorkflowRecipientModel>, reminderIntervalDays: Int?, expiresAtEpochMillis: Long?): WorkflowRequestModel
    suspend fun createFormRequest(document: DocumentModel, templateId: String, title: String, recipients: List<WorkflowRecipientModel>, reminderIntervalDays: Int?, expiresAtEpochMillis: Long?): WorkflowRequestModel
    suspend fun updateRequestResponse(requestId: String, response: WorkflowResponseModel): WorkflowRequestModel?
    suspend fun sendReminder(requestId: String): WorkflowRequestModel?
}

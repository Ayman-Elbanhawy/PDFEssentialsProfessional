package com.aymanelbanhawy.editor.core.security

import com.aymanelbanhawy.editor.core.forms.TimestampValidationModel
import com.aymanelbanhawy.editor.core.model.NormalizedRect
import kotlinx.serialization.Serializable

@Serializable
enum class RestrictedAction {
    Print,
    Copy,
    Share,
    Export,
}

@Serializable
enum class AuditEventType {
    DocumentOpened,
    UnlockAttempted,
    UnlockSucceeded,
    UnlockFailed,
    PolicyBlocked,
    PasswordProtectionUpdated,
    WatermarkUpdated,
    MetadataScrubbed,
    InspectionGenerated,
    RedactionMarked,
    RedactionApplied,
    SignatureApplied,
    SignatureVerified,
    SignatureInvalidated,
    ProtectedExported,
    AuditExported,
    AiProviderChanged,
    AiModelChanged,
    AiPromptSubmitted,
    AiPromptCancelled,
    AiPromptCompleted,
    AiPromptFailed,
    ConnectorOpened,
    ConnectorSaved,
    ConnectorShared,
    ConnectorBlocked,
}

@Serializable
data class AuditTrailEventModel(
    val id: String,
    val documentKey: String,
    val type: AuditEventType,
    val actor: String,
    val message: String,
    val createdAtEpochMillis: Long,
    val metadata: Map<String, String> = emptyMap(),
)

@Serializable
data class AuditTrailExportModel(
    val formatVersion: Int = 1,
    val documentKey: String,
    val exportedAtEpochMillis: Long,
    val events: List<AuditTrailEventModel>,
)

@Serializable
enum class AppLockReason {
    Launch,
    Manual,
    Timeout,
}

@Serializable
data class AppLockSettingsModel(
    val enabled: Boolean = false,
    val pinHash: String = "",
    val biometricsEnabled: Boolean = false,
    val lockTimeoutSeconds: Int = 300,
)

@Serializable
data class AppLockStateModel(
    val isLocked: Boolean = false,
    val failedPinAttempts: Int = 0,
    val lastUnlockedAtEpochMillis: Long? = null,
    val reason: AppLockReason? = null,
)

@Serializable
data class DocumentPermissionModel(
    val allowPrint: Boolean = true,
    val allowCopy: Boolean = true,
    val allowShare: Boolean = true,
    val allowExport: Boolean = true,
)

@Serializable
data class TenantPolicyHooksModel(
    val disablePrint: Boolean = false,
    val disableCopy: Boolean = false,
    val disableShare: Boolean = false,
    val disableExport: Boolean = false,
    val forcedWatermarkText: String = "",
    val forceMetadataScrub: Boolean = false,
)

@Serializable
data class PasswordProtectionModel(
    val enabled: Boolean = false,
    val userPassword: String = "",
    val ownerPassword: String = "",
)

@Serializable
data class WatermarkModel(
    val enabled: Boolean = false,
    val text: String = "Confidential",
    val textColorHex: String = "#55111111",
    val opacity: Float = 0.18f,
    val rotationDegrees: Float = -32f,
    val fontSize: Float = 32f,
)

@Serializable
data class MetadataScrubOptionsModel(
    val enabled: Boolean = false,
    val scrubTitle: Boolean = true,
    val scrubAuthor: Boolean = true,
    val scrubSubject: Boolean = true,
    val scrubKeywords: Boolean = true,
    val scrubCreator: Boolean = true,
    val scrubProducer: Boolean = false,
    val scrubDates: Boolean = false,
)

@Serializable
enum class InspectionSeverity {
    Info,
    Warning,
    Critical,
}

@Serializable
data class InspectionFindingModel(
    val id: String,
    val title: String,
    val message: String,
    val severity: InspectionSeverity,
)

@Serializable
data class InspectionReportModel(
    val generatedAtEpochMillis: Long = 0L,
    val findings: List<InspectionFindingModel> = emptyList(),
    val metadataSummary: Map<String, String> = emptyMap(),
    val hiddenAnnotationCount: Int = 0,
    val embeddedContentFlags: List<String> = emptyList(),
    val redactionCoverageSummary: String = "",
    val protectionFlags: List<String> = emptyList(),
    val signatureStatusSummary: Map<String, String> = emptyMap(),
)

@Serializable
enum class RedactionStatus {
    Marked,
    Applied,
}

@Serializable
data class RedactionMarkModel(
    val id: String,
    val pageIndex: Int,
    val bounds: NormalizedRect,
    val label: String,
    val overlayText: String = "REDACTED",
    val fillColorHex: String = "#E5111111",
    val status: RedactionStatus = RedactionStatus.Marked,
    val createdAtEpochMillis: Long,
    val appliedAtEpochMillis: Long? = null,
)

@Serializable
data class RedactionWorkflowModel(
    val marks: List<RedactionMarkModel> = emptyList(),
    val previewEnabled: Boolean = false,
    val irreversibleConfirmed: Boolean = false,
)

@Serializable
data class DocumentTimestampPolicyModel(
    val enabled: Boolean = false,
    val authorityUrl: String = "",
    val requireTimestampOnSign: Boolean = false,
    val lastValidation: TimestampValidationModel = TimestampValidationModel(),
)

@Serializable
data class SecurityDocumentModel(
    val appLockRequired: Boolean = false,
    val permissions: DocumentPermissionModel = DocumentPermissionModel(),
    val tenantPolicy: TenantPolicyHooksModel = TenantPolicyHooksModel(),
    val passwordProtection: PasswordProtectionModel = PasswordProtectionModel(),
    val watermark: WatermarkModel = WatermarkModel(),
    val metadataScrub: MetadataScrubOptionsModel = MetadataScrubOptionsModel(),
    val inspectionReport: InspectionReportModel = InspectionReportModel(),
    val redactionWorkflow: RedactionWorkflowModel = RedactionWorkflowModel(),
    val timestampPolicy: DocumentTimestampPolicyModel = DocumentTimestampPolicyModel(),
)

data class PolicyDecision(
    val allowed: Boolean,
    val message: String? = null,
)

package com.aymanelbanhawy.editor.core.security

import android.content.Context
import com.aymanelbanhawy.editor.core.data.AppLockSettingsDao
import com.aymanelbanhawy.editor.core.data.AppLockSettingsEntity
import com.aymanelbanhawy.editor.core.data.AuditTrailEventDao
import com.aymanelbanhawy.editor.core.data.AuditTrailEventEntity
import com.aymanelbanhawy.editor.core.data.DocumentSecurityDao
import com.aymanelbanhawy.editor.core.data.DocumentSecurityEntity
import com.aymanelbanhawy.editor.core.forms.DigitalSignatureService
import com.aymanelbanhawy.editor.core.forms.SignatureVerificationStatus
import com.aymanelbanhawy.editor.core.model.DocumentModel
import com.tom_roush.pdfbox.pdmodel.PDDocument
import java.io.File
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

interface SecurityRepository {
    val appLockState: StateFlow<AppLockStateModel>

    suspend fun loadAppLockSettings(): AppLockSettingsModel
    suspend fun updateAppLockSettings(enabled: Boolean, pin: String, biometricsEnabled: Boolean, timeoutSeconds: Int): AppLockSettingsModel
    suspend fun lockApp(reason: AppLockReason = AppLockReason.Manual)
    suspend fun unlockWithPin(pin: String): Boolean
    suspend fun unlockWithBiometric(): Boolean

    suspend fun loadDocumentSecurity(documentKey: String): SecurityDocumentModel
    suspend fun persistDocumentSecurity(documentKey: String, security: SecurityDocumentModel)
    suspend fun inspectDocument(document: DocumentModel): InspectionReportModel

    fun evaluatePolicy(security: SecurityDocumentModel, action: RestrictedAction): PolicyDecision

    suspend fun recordAudit(event: AuditTrailEventModel)
    suspend fun auditEvents(documentKey: String): List<AuditTrailEventModel>
    suspend fun exportAuditTrail(documentKey: String, destination: File): File
}

class DefaultSecurityRepository(
    private val context: Context,
    private val appLockSettingsDao: AppLockSettingsDao,
    private val documentSecurityDao: DocumentSecurityDao,
    private val auditTrailEventDao: AuditTrailEventDao,
    private val json: Json,
    private val digitalSignatureService: DigitalSignatureService? = null,
) : SecurityRepository {

    private val mutableAppLockState = MutableStateFlow(AppLockStateModel())
    override val appLockState: StateFlow<AppLockStateModel> = mutableAppLockState

    override suspend fun loadAppLockSettings(): AppLockSettingsModel {
        return appLockSettingsDao.get()?.toModel() ?: AppLockSettingsModel()
    }

    override suspend fun updateAppLockSettings(enabled: Boolean, pin: String, biometricsEnabled: Boolean, timeoutSeconds: Int): AppLockSettingsModel {
        val settings = AppLockSettingsModel(
            enabled = enabled,
            pinHash = if (pin.isBlank()) "" else PinHasher.hash(pin),
            biometricsEnabled = biometricsEnabled,
            lockTimeoutSeconds = timeoutSeconds.coerceAtLeast(15),
        )
        appLockSettingsDao.upsert(settings.toEntity())
        mutableAppLockState.value = if (settings.enabled) {
            AppLockStateModel(isLocked = true, reason = AppLockReason.Manual)
        } else {
            AppLockStateModel(isLocked = false, lastUnlockedAtEpochMillis = System.currentTimeMillis())
        }
        return settings
    }

    override suspend fun lockApp(reason: AppLockReason) {
        val settings = loadAppLockSettings()
        if (!settings.enabled) return
        mutableAppLockState.value = mutableAppLockState.value.copy(isLocked = true, reason = reason)
    }

    override suspend fun unlockWithPin(pin: String): Boolean {
        val settings = loadAppLockSettings()
        recordAudit(
            AuditTrailEventModel(
                id = UUID.randomUUID().toString(),
                documentKey = GLOBAL_AUDIT_KEY,
                type = AuditEventType.UnlockAttempted,
                actor = "local-user",
                message = "PIN unlock attempted",
                createdAtEpochMillis = System.currentTimeMillis(),
            ),
        )
        val success = settings.enabled && settings.pinHash.isNotBlank() && settings.pinHash == PinHasher.hash(pin)
        mutableAppLockState.value = if (success) {
            AppLockStateModel(isLocked = false, lastUnlockedAtEpochMillis = System.currentTimeMillis())
        } else {
            mutableAppLockState.value.copy(isLocked = true, failedPinAttempts = mutableAppLockState.value.failedPinAttempts + 1)
        }
        recordAudit(
            AuditTrailEventModel(
                id = UUID.randomUUID().toString(),
                documentKey = GLOBAL_AUDIT_KEY,
                type = if (success) AuditEventType.UnlockSucceeded else AuditEventType.UnlockFailed,
                actor = "local-user",
                message = if (success) "PIN unlock succeeded" else "PIN unlock failed",
                createdAtEpochMillis = System.currentTimeMillis(),
            ),
        )
        return success
    }

    override suspend fun unlockWithBiometric(): Boolean {
        val settings = loadAppLockSettings()
        if (!settings.enabled || !settings.biometricsEnabled) return false
        mutableAppLockState.value = AppLockStateModel(isLocked = false, lastUnlockedAtEpochMillis = System.currentTimeMillis())
        recordAudit(
            AuditTrailEventModel(
                id = UUID.randomUUID().toString(),
                documentKey = GLOBAL_AUDIT_KEY,
                type = AuditEventType.UnlockSucceeded,
                actor = "local-user",
                message = "Biometric unlock succeeded",
                createdAtEpochMillis = System.currentTimeMillis(),
            ),
        )
        return true
    }

    override suspend fun loadDocumentSecurity(documentKey: String): SecurityDocumentModel {
        return documentSecurityDao.get(documentKey)?.let { json.decodeFromString(SecurityDocumentModel.serializer(), it.payloadJson) }
            ?: SecurityDocumentModel()
    }

    override suspend fun persistDocumentSecurity(documentKey: String, security: SecurityDocumentModel) {
        documentSecurityDao.upsert(
            DocumentSecurityEntity(
                documentKey = documentKey,
                payloadJson = json.encodeToString(SecurityDocumentModel.serializer(), security),
                updatedAtEpochMillis = System.currentTimeMillis(),
            ),
        )
    }

    override suspend fun inspectDocument(document: DocumentModel): InspectionReportModel {
        val findings = mutableListOf<InspectionFindingModel>()
        val file = File(document.documentRef.workingCopyPath)
        val metadataSummary = linkedMapOf<String, String>()
        val embeddedContentFlags = mutableListOf<String>()
        val protectionFlags = mutableListOf<String>()
        val signatureStatusSummary = linkedMapOf<String, String>()
        var hiddenAnnotationCount = 0
        val appliedRedactions = document.security.redactionWorkflow.marks.count { it.status == RedactionStatus.Applied }
        val totalRedactions = document.security.redactionWorkflow.marks.size
        if (!file.exists()) {
            findings += InspectionFindingModel("missing-file", "Working Copy Missing", "The working copy is missing from local storage.", InspectionSeverity.Critical)
        } else {
            PDDocument.load(file, document.security.passwordProtection.userPassword.takeIf { document.security.passwordProtection.enabled && it.isNotBlank() }).use { pdDocument ->
                val info = pdDocument.documentInformation
                metadataSummary["title"] = info.title.orEmpty()
                metadataSummary["author"] = info.author.orEmpty()
                metadataSummary["subject"] = info.subject.orEmpty()
                metadataSummary["keywords"] = info.keywords.orEmpty()
                metadataSummary["creator"] = info.creator.orEmpty()
                metadataSummary["producer"] = info.producer.orEmpty()
                if (pdDocument.isEncrypted) {
                    protectionFlags += "password-protected"
                } else {
                    findings += InspectionFindingModel("encryption", "Document Not Password Protected", "The file can be opened without a password.", InspectionSeverity.Warning)
                }
                if (metadataSummary.values.any { it.isNotBlank() }) {
                    findings += InspectionFindingModel("metadata", "Metadata Present", "Title, author, subject, or keyword metadata are still present in the document.", InspectionSeverity.Warning)
                }
                hiddenAnnotationCount = pdDocument.pages.sumOf { page -> page.annotations.count { it.isHidden || it.isInvisible || it.isNoView } }
                if (hiddenAnnotationCount > 0) {
                    findings += InspectionFindingModel("hidden-annotations", "Hidden annotations detected", "$hiddenAnnotationCount annotation(s) are hidden or non-visible.", InspectionSeverity.Warning)
                }
                val embeddedFiles = pdDocument.documentCatalog.names?.embeddedFiles
                if (embeddedFiles != null) {
                    embeddedContentFlags += "embedded-files"
                    findings += InspectionFindingModel("embedded-content", "Embedded content present", "The document includes embedded files or attachment name trees.", InspectionSeverity.Warning)
                }
                if (pdDocument.currentAccessPermission != null) {
                    val permission = pdDocument.currentAccessPermission
                    if (!permission.canPrint()) protectionFlags += "print-blocked"
                    if (!permission.canExtractContent()) protectionFlags += "copy-blocked"
                    if (!permission.canModify()) protectionFlags += "export-blocked"
                }
            }
        }
        if (document.security.redactionWorkflow.marks.any { it.status == RedactionStatus.Marked }) {
            findings += InspectionFindingModel("redaction-pending", "Pending Redactions", "There are marked redactions that have not been irreversibly applied.", InspectionSeverity.Critical)
        }
        if (document.security.watermark.enabled) {
            protectionFlags += "watermark"
            findings += InspectionFindingModel("watermark", "Watermark Enabled", "A visible watermark will be included in exports.", InspectionSeverity.Info)
        }
        if (document.security.metadataScrub.enabled) {
            protectionFlags += "metadata-scrub"
        }
        if (appliedRedactions > 0) {
            protectionFlags += "redactions-applied"
        }
        digitalSignatureService?.verifyDocument(file, document.security.passwordProtection.userPassword.takeIf { document.security.passwordProtection.enabled && it.isNotBlank() })?.forEach { (fieldName, verification) ->
            signatureStatusSummary[fieldName] = verification.verificationStatus.name
            if (verification.verificationStatus != SignatureVerificationStatus.Unsigned) {
                protectionFlags += "digitally-signed"
            }
            val severity = when (verification.verificationStatus) {
                SignatureVerificationStatus.Verified -> InspectionSeverity.Info
                SignatureVerificationStatus.Signed -> InspectionSeverity.Warning
                SignatureVerificationStatus.Unsigned -> InspectionSeverity.Warning
                SignatureVerificationStatus.Invalid, SignatureVerificationStatus.VerificationFailed -> InspectionSeverity.Critical
            }
            findings += InspectionFindingModel(
                id = "signature-$fieldName",
                title = "Signature ${verification.verificationStatus.name}",
                message = "$fieldName: ${verification.verificationMessage.ifBlank { "No verification message recorded." }}",
                severity = severity,
            )
        }
        val report = InspectionReportModel(
            generatedAtEpochMillis = System.currentTimeMillis(),
            findings = findings,
            metadataSummary = metadataSummary,
            hiddenAnnotationCount = hiddenAnnotationCount,
            embeddedContentFlags = embeddedContentFlags,
            redactionCoverageSummary = "$appliedRedactions of $totalRedactions redaction region(s) were irreversibly applied.",
            protectionFlags = protectionFlags.distinct(),
            signatureStatusSummary = signatureStatusSummary,
        )
        persistDocumentSecurity(document.documentRef.sourceKey, document.security.copy(inspectionReport = report))
        recordAudit(
            AuditTrailEventModel(
                id = UUID.randomUUID().toString(),
                documentKey = document.documentRef.sourceKey,
                type = AuditEventType.InspectionGenerated,
                actor = "local-user",
                message = "Generated inspection report",
                createdAtEpochMillis = report.generatedAtEpochMillis,
                metadata = mapOf("findings" to report.findings.size.toString()),
            ),
        )
        return report
    }
    override fun evaluatePolicy(security: SecurityDocumentModel, action: RestrictedAction): PolicyDecision {
        val blockedByTenant = when (action) {
            RestrictedAction.Print -> security.tenantPolicy.disablePrint
            RestrictedAction.Copy -> security.tenantPolicy.disableCopy
            RestrictedAction.Share -> security.tenantPolicy.disableShare
            RestrictedAction.Export -> security.tenantPolicy.disableExport
        }
        if (blockedByTenant) {
            return PolicyDecision(false, "$action is disabled by tenant policy.")
        }
        val allowed = when (action) {
            RestrictedAction.Print -> security.permissions.allowPrint
            RestrictedAction.Copy -> security.permissions.allowCopy
            RestrictedAction.Share -> security.permissions.allowShare
            RestrictedAction.Export -> security.permissions.allowExport
        }
        return if (allowed) PolicyDecision(true) else PolicyDecision(false, "$action is disabled for this document.")
    }

    override suspend fun recordAudit(event: AuditTrailEventModel) {
        auditTrailEventDao.upsert(event.toEntity(json))
    }

    override suspend fun auditEvents(documentKey: String): List<AuditTrailEventModel> {
        return auditTrailEventDao.forDocument(documentKey).map { it.toModel(json) }
    }

    override suspend fun exportAuditTrail(documentKey: String, destination: File): File {
        destination.parentFile?.mkdirs()
        val export = AuditTrailExportModel(
            documentKey = documentKey,
            exportedAtEpochMillis = System.currentTimeMillis(),
            events = auditEvents(documentKey),
        )
        destination.writeText(json.encodeToString(AuditTrailExportModel.serializer(), export))
        recordAudit(
            AuditTrailEventModel(
                id = UUID.randomUUID().toString(),
                documentKey = documentKey,
                type = AuditEventType.AuditExported,
                actor = "local-user",
                message = "Exported audit trail",
                createdAtEpochMillis = System.currentTimeMillis(),
                metadata = mapOf("path" to destination.absolutePath),
            ),
        )
        return destination
    }

    private fun AppLockSettingsModel.toEntity(): AppLockSettingsEntity = AppLockSettingsEntity(
        singletonId = APP_LOCK_SINGLETON_ID,
        enabled = enabled,
        pinHash = pinHash,
        biometricsEnabled = biometricsEnabled,
        lockTimeoutSeconds = lockTimeoutSeconds,
    )

    private fun AppLockSettingsEntity.toModel(): AppLockSettingsModel = AppLockSettingsModel(
        enabled = enabled,
        pinHash = pinHash,
        biometricsEnabled = biometricsEnabled,
        lockTimeoutSeconds = lockTimeoutSeconds,
    )

    companion object {
        private const val APP_LOCK_SINGLETON_ID = "app-lock"
        private const val GLOBAL_AUDIT_KEY = "__app__"
    }
}

private fun AuditTrailEventModel.toEntity(json: Json): AuditTrailEventEntity = AuditTrailEventEntity(
    id = id,
    documentKey = documentKey,
    type = type.name,
    actor = actor,
    message = message,
    createdAtEpochMillis = createdAtEpochMillis,
    metadataJson = json.encodeToString(MapSerializer(String.serializer(), String.serializer()), metadata),
)

private fun AuditTrailEventEntity.toModel(json: Json): AuditTrailEventModel = AuditTrailEventModel(
    id = id,
    documentKey = documentKey,
    type = AuditEventType.valueOf(type),
    actor = actor,
    message = message,
    createdAtEpochMillis = createdAtEpochMillis,
    metadata = json.decodeFromString(MapSerializer(String.serializer(), String.serializer()), metadataJson),
)



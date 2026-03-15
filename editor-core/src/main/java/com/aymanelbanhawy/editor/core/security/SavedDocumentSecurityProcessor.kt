package com.aymanelbanhawy.editor.core.security

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.aymanelbanhawy.editor.core.forms.DigitalSignatureMetadata
import com.aymanelbanhawy.editor.core.forms.DigitalSignatureService
import com.aymanelbanhawy.editor.core.forms.FormDocumentModel
import com.aymanelbanhawy.editor.core.forms.FormFieldModel
import com.aymanelbanhawy.editor.core.forms.FormFieldType
import com.aymanelbanhawy.editor.core.forms.FormFieldValue
import com.aymanelbanhawy.editor.core.forms.SignatureSourceType
import com.aymanelbanhawy.editor.core.forms.SignatureVerificationStatus
import com.aymanelbanhawy.editor.core.model.AnnotationExportMode
import com.aymanelbanhawy.editor.core.model.DocumentModel
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDAcroForm
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDCheckBox
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDChoice
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDRadioButton
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDTextField
import com.tom_roush.pdfbox.util.Matrix
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SavedDocumentSecurityProcessor(
    private val securityRepository: SecurityRepository?,
    private val digitalSignatureService: DigitalSignatureService?,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {

    suspend fun decorateOpenedDocument(document: DocumentModel): DocumentModel = withContext(ioDispatcher) {
        if (digitalSignatureService == null) return@withContext document
        val file = File(document.documentRef.workingCopyPath)
        if (!file.exists()) return@withContext document
        val verification = digitalSignatureService.verifyDocument(file, openPassword(document.security))
        document.copy(formDocument = applyVerification(document.formDocument, verification))
    }

    suspend fun applyWriteThroughSecurity(
        document: DocumentModel,
        destination: File,
        exportMode: AnnotationExportMode,
    ): DocumentModel = withContext(ioDispatcher) {
        val effectiveSecurity = buildEffectiveSecurity(document.security)
        enforceExportPolicy(document.documentRef.sourceKey, effectiveSecurity)
        applyFormValues(document.formDocument, destination, exportMode)
        applyMetadataAndWatermark(document.documentRef.sourceKey, destination, effectiveSecurity)
        val appliedRedactions = applyIrreversibleRedactions(document.documentRef.sourceKey, destination, effectiveSecurity)
        applyPasswordProtection(document.documentRef.sourceKey, destination, effectiveSecurity)
        val verifiedSignatures = applyCertificateSignatures(document, destination, effectiveSecurity)
        val updatedFormDocument = applyVerification(document.formDocument, verifiedSignatures)
        val inspectedDocument = document.copy(
            formDocument = updatedFormDocument,
            security = effectiveSecurity,
            documentRef = document.documentRef.copy(workingCopyPath = destination.absolutePath),
        )
        val inspectionReport = securityRepository?.inspectDocument(inspectedDocument)
            ?: buildFallbackInspectionReport(destination, effectiveSecurity, verifiedSignatures, appliedRedactions)
        val finalDocument = document.copy(
            formDocument = updatedFormDocument,
            security = effectiveSecurity.copy(inspectionReport = inspectionReport),
        )
        if (hasProtection(finalDocument.security) || verifiedSignatures.isNotEmpty()) {
            recordAudit(
                AuditEventType.ProtectedExported,
                document.documentRef.sourceKey,
                "Saved protected output",
                mapOf(
                    "password" to finalDocument.security.passwordProtection.enabled.toString(),
                    "watermark" to finalDocument.security.watermark.enabled.toString(),
                    "redactions" to appliedRedactions.toString(),
                    "signatures" to verifiedSignatures.size.toString(),
                ),
            )
        }
        finalDocument
    }

    private fun buildEffectiveSecurity(security: SecurityDocumentModel): SecurityDocumentModel {
        val mergedPermissions = security.permissions.copy(
            allowPrint = security.permissions.allowPrint && !security.tenantPolicy.disablePrint,
            allowCopy = security.permissions.allowCopy && !security.tenantPolicy.disableCopy,
            allowShare = security.permissions.allowShare && !security.tenantPolicy.disableShare,
            allowExport = security.permissions.allowExport && !security.tenantPolicy.disableExport,
        )
        val mergedWatermark = if (security.tenantPolicy.forcedWatermarkText.isNotBlank()) {
            security.watermark.copy(enabled = true, text = security.tenantPolicy.forcedWatermarkText)
        } else {
            security.watermark
        }
        val mergedScrub = if (security.tenantPolicy.forceMetadataScrub) {
            security.metadataScrub.copy(enabled = true)
        } else {
            security.metadataScrub
        }
        return security.copy(
            permissions = mergedPermissions,
            watermark = mergedWatermark,
            metadataScrub = mergedScrub,
        )
    }

    private suspend fun enforceExportPolicy(documentKey: String, security: SecurityDocumentModel) {
        val decision = securityRepository?.evaluatePolicy(security, RestrictedAction.Export)
            ?: PolicyDecision(security.permissions.allowExport, if (security.permissions.allowExport) null else "Export is disabled for this document.")
        if (!decision.allowed) {
            recordAudit(
                AuditEventType.PolicyBlocked,
                documentKey,
                decision.message ?: "Export blocked by policy.",
                mapOf("action" to RestrictedAction.Export.name),
            )
            throw SecurityException(decision.message ?: "Export blocked by policy.")
        }
    }

    private fun applyFormValues(formDocument: FormDocumentModel, destination: File, exportMode: AnnotationExportMode) {
        if (!destination.exists()) return
        PDDocument.load(destination, null as String?).use { pdDocument ->
            val acroForm: PDAcroForm = pdDocument.documentCatalog?.acroForm ?: return@use
            formDocument.fields.forEach { fieldModel ->
                val field = acroForm.getField(fieldModel.name) ?: return@forEach
                when (val value = fieldModel.value) {
                    is FormFieldValue.Text -> if (field is PDTextField) runCatching { field.setValue(value.text) }
                    is FormFieldValue.BooleanValue -> runCatching { if (field is PDCheckBox) if (value.checked) field.check() else field.unCheck() }
                    is FormFieldValue.Choice -> when (field) {
                        is PDRadioButton -> runCatching { field.setValue(value.selected) }
                        is PDChoice -> runCatching { field.setValue(value.selected) }
                    }
                    is FormFieldValue.SignatureValue -> {
                        if (fieldModel.type == FormFieldType.Signature && value.sourceType == SignatureSourceType.Handwritten && !value.imagePath.isNullOrBlank()) {
                            placeSignatureAppearance(pdDocument, fieldModel, value.imagePath)
                        }
                    }
                }
            }
            if (exportMode == AnnotationExportMode.Flatten && !hasCertificateSignatureRequests(formDocument)) {
                runCatching { acroForm.flatten() }
            }
            pdDocument.save(destination)
        }
    }

    private suspend fun applyMetadataAndWatermark(documentKey: String, destination: File, security: SecurityDocumentModel) {
        if (!destination.exists()) return
        PDDocument.load(destination, null as String?).use { document ->
            if (security.metadataScrub.enabled) {
                scrubMetadata(document, security.metadataScrub)
                recordAudit(AuditEventType.MetadataScrubbed, documentKey, "Scrubbed document metadata before save")
            }
            if (security.watermark.enabled && security.watermark.text.isNotBlank()) {
                applyWatermark(document, security.watermark)
                recordAudit(AuditEventType.WatermarkUpdated, documentKey, "Applied export watermark", mapOf("text" to security.watermark.text))
            }
            document.save(destination)
        }
    }

    private suspend fun applyIrreversibleRedactions(documentKey: String, destination: File, security: SecurityDocumentModel): Int {
        val appliedMarks = security.redactionWorkflow.marks.filter { it.status == RedactionStatus.Applied }
        if (appliedMarks.isEmpty()) return 0
        require(security.redactionWorkflow.irreversibleConfirmed) { "Redactions must be confirmed before export." }
        val sourceSnapshot = File(destination.parentFile, destination.name + ".redaction-source.pdf")
        val redactedOutput = File(destination.parentFile, destination.name + ".redacted.tmp.pdf")
        destination.copyTo(sourceSnapshot, overwrite = true)
        ParcelFileDescriptor.open(sourceSnapshot, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
            PdfRenderer(descriptor).use { renderer ->
                PDDocument.load(sourceSnapshot, null as String?).use { sourceDocument ->
                    PDDocument().use { outputDocument ->
                        repeat(sourceDocument.numberOfPages) { pageIndex ->
                            val sourcePage = sourceDocument.getPage(pageIndex)
                            val pageMarks = appliedMarks.filter { it.pageIndex == pageIndex }
                            if (pageMarks.isEmpty()) {
                                val imported = outputDocument.importPage(sourcePage)
                                imported.rotation = sourcePage.rotation
                            } else if (pageMarks.any { it.bounds.isWholePage() }) {
                                val outputPage = PDPage(PDRectangle(sourcePage.mediaBox.width, sourcePage.mediaBox.height))
                                outputDocument.addPage(outputPage)
                                PDPageContentStream(outputDocument, outputPage).use { stream ->
                                    stream.setNonStrokingColor(1f, 1f, 1f)
                                    stream.addRect(0f, 0f, outputPage.mediaBox.width, outputPage.mediaBox.height)
                                    stream.fill()
                                    stream.setNonStrokingColor(0f, 0f, 0f)
                                    pageMarks.forEach { mark ->
                                        val left = mark.bounds.left * outputPage.mediaBox.width
                                        val width = mark.bounds.width * outputPage.mediaBox.width
                                        val top = outputPage.mediaBox.height - (mark.bounds.top * outputPage.mediaBox.height)
                                        val height = mark.bounds.height * outputPage.mediaBox.height
                                        val bottom = top - height
                                        stream.addRect(left, bottom, width, height)
                                        stream.fill()
                                    }
                                }
                            } else {
                                val page = renderer.openPage(pageIndex)
                                val scale = 2f
                                val bitmap = Bitmap.createBitmap(
                                    (page.width * scale).toInt().coerceAtLeast(1),
                                    (page.height * scale).toInt().coerceAtLeast(1),
                                    Bitmap.Config.ARGB_8888,
                                )
                                bitmap.eraseColor(Color.WHITE)
                                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                page.close()
                                val canvas = Canvas(bitmap)
                                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                                    style = Paint.Style.FILL
                                    color = Color.BLACK
                                }
                                pageMarks.forEach { mark ->
                                    val left = mark.bounds.left * bitmap.width
                                    val top = mark.bounds.top * bitmap.height
                                    val right = mark.bounds.right * bitmap.width
                                    val bottom = mark.bounds.bottom * bitmap.height
                                    canvas.drawRect(left, top, right, bottom, paint)
                                }
                                val outputPage = PDPage(PDRectangle(sourcePage.mediaBox.width, sourcePage.mediaBox.height))
                                outputDocument.addPage(outputPage)
                                PDPageContentStream(outputDocument, outputPage).use { stream ->
                                    val image = LosslessFactory.createFromImage(outputDocument, bitmap)
                                    stream.drawImage(image, 0f, 0f, outputPage.mediaBox.width, outputPage.mediaBox.height)
                                }
                                bitmap.recycle()
                            }
                        }
                        outputDocument.save(redactedOutput)
                    }
                }
            }
        }
        redactedOutput.copyTo(destination, overwrite = true)
        redactedOutput.delete()
        sourceSnapshot.delete()
        recordAudit(
            AuditEventType.RedactionApplied,
            documentKey,
            "Applied irreversible redactions to saved output",
            mapOf("count" to appliedMarks.size.toString()),
        )
        return appliedMarks.size
    }

    private suspend fun applyPasswordProtection(documentKey: String, destination: File, security: SecurityDocumentModel) {
        if (!security.passwordProtection.enabled) return
        val protection = security.passwordProtection
        if (protection.ownerPassword.isBlank() && protection.userPassword.isBlank()) return
        PDDocument.load(destination, null as String?).use { document ->
            val accessPermission = AccessPermission().apply {
                setCanPrint(security.permissions.allowPrint)
                setCanExtractContent(security.permissions.allowCopy)
                setCanModify(security.permissions.allowExport)
                setCanModifyAnnotations(true)
            }
            val ownerPassword = protection.ownerPassword.ifBlank { protection.userPassword.ifBlank { UUID.randomUUID().toString() } }
            val policy = StandardProtectionPolicy(ownerPassword, protection.userPassword, accessPermission).apply {
                encryptionKeyLength = 256
            }
            document.protect(policy)
            document.save(destination)
        }
        recordAudit(
            AuditEventType.PasswordProtectionUpdated,
            documentKey,
            "Applied password protection to saved output",
            mapOf(
                "allowPrint" to security.permissions.allowPrint.toString(),
                "allowCopy" to security.permissions.allowCopy.toString(),
                "allowExport" to security.permissions.allowExport.toString(),
            ),
        )
    }

    private suspend fun applyCertificateSignatures(
        document: DocumentModel,
        destination: File,
        security: SecurityDocumentModel,
    ): Map<String, DigitalSignatureMetadata> {
        val signatureService = digitalSignatureService ?: return emptyMap()
        val signatureFields = document.formDocument.fields.filter { field ->
            val value = field.value as? FormFieldValue.SignatureValue ?: return@filter false
            field.type == FormFieldType.Signature &&
                value.sourceType == SignatureSourceType.CertificateBacked &&
                value.signingIdentityId.isNotBlank() &&
                value.status != SignatureVerificationStatus.Invalid
        }
        if (signatureFields.isEmpty()) return emptyMap()
        signatureFields.forEach { field ->
            val value = field.value as FormFieldValue.SignatureValue
            val metadata = signatureService.signDocument(
                documentFile = destination,
                fieldName = field.name,
                pageIndex = field.pageIndex,
                bounds = field.bounds,
                signatureValue = value,
                timestampAuthorityUrl = security.timestampPolicy.authorityUrl,
                password = openPassword(security),
            )
            recordAudit(
                AuditEventType.SignatureApplied,
                document.documentRef.sourceKey,
                "Applied digital signature to ${field.name}",
                mapOf(
                    "field" to field.name,
                    "status" to metadata.verificationStatus.name,
                    "identity" to value.signingIdentityId,
                ),
            )
        }
        val verified = signatureService.verifyDocument(destination, openPassword(security))
        verified.forEach { (fieldName, metadata) ->
            recordAudit(
                AuditEventType.SignatureVerified,
                document.documentRef.sourceKey,
                "Verified signature for $fieldName",
                mapOf("status" to metadata.verificationStatus.name),
            )
        }
        return verified
    }

    private fun applyVerification(
        formDocument: FormDocumentModel,
        verification: Map<String, DigitalSignatureMetadata>,
    ): FormDocumentModel {
        if (verification.isEmpty()) return formDocument
        return formDocument.copy(
            fields = formDocument.fields.map { field ->
                val signatureValue = field.value as? FormFieldValue.SignatureValue ?: return@map field
                val metadata = verification[field.name] ?: return@map field
                field.copy(
                    value = signatureValue.copy(
                        status = metadata.verificationStatus,
                        digitalSignature = metadata,
                        signedAtEpochMillis = metadata.signedAtEpochMillis,
                        signerName = metadata.signerDisplayName.ifBlank { signatureValue.signerName },
                    ),
                    signatureStatus = metadata.verificationStatus,
                )
            },
        )
    }

    private fun buildFallbackInspectionReport(
        destination: File,
        security: SecurityDocumentModel,
        verification: Map<String, DigitalSignatureMetadata>,
        appliedRedactions: Int,
    ): InspectionReportModel {
        val metadataSummary = mutableMapOf<String, String>()
        val protectionFlags = mutableListOf<String>()
        val findings = mutableListOf<InspectionFindingModel>()
        if (destination.exists()) {
            PDDocument.load(destination, openPassword(security)).use { document ->
                val info = document.documentInformation
                metadataSummary["title"] = info.title.orEmpty()
                metadataSummary["author"] = info.author.orEmpty()
                if (document.isEncrypted) protectionFlags += "password-protected"
            }
        }
        if (security.watermark.enabled) protectionFlags += "watermark"
        if (security.metadataScrub.enabled) protectionFlags += "metadata-scrub"
        if (appliedRedactions > 0) protectionFlags += "redactions-applied"
        if (verification.isNotEmpty()) protectionFlags += "digitally-signed"
        findings += verification.map { (fieldName, metadata) ->
            InspectionFindingModel(
                id = "signature-$fieldName",
                title = "Signature ${metadata.verificationStatus.name}",
                message = metadata.verificationMessage.ifBlank { "No verification message recorded." },
                severity = when (metadata.verificationStatus) {
                    SignatureVerificationStatus.Verified -> InspectionSeverity.Info
                    SignatureVerificationStatus.Signed, SignatureVerificationStatus.Unsigned -> InspectionSeverity.Warning
                    SignatureVerificationStatus.Invalid, SignatureVerificationStatus.VerificationFailed -> InspectionSeverity.Critical
                },
            )
        }
        return InspectionReportModel(
            generatedAtEpochMillis = System.currentTimeMillis(),
            findings = findings,
            metadataSummary = metadataSummary,
            redactionCoverageSummary = "$appliedRedactions redaction region(s) were irreversibly applied.",
            protectionFlags = protectionFlags.distinct(),
            signatureStatusSummary = verification.mapValues { it.value.verificationStatus.name },
        )
    }

    private fun hasProtection(security: SecurityDocumentModel): Boolean {
        return security.passwordProtection.enabled ||
            security.watermark.enabled ||
            security.metadataScrub.enabled ||
            security.redactionWorkflow.marks.any { it.status == RedactionStatus.Applied }
    }

    private fun hasCertificateSignatureRequests(formDocument: FormDocumentModel): Boolean {
        return formDocument.fields.any { field ->
            val value = field.value as? FormFieldValue.SignatureValue ?: return@any false
            field.type == FormFieldType.Signature && value.sourceType == SignatureSourceType.CertificateBacked && value.signingIdentityId.isNotBlank()
        }
    }

    private fun scrubMetadata(document: PDDocument, options: MetadataScrubOptionsModel) {
        val info = document.documentInformation
        if (options.scrubAuthor) info.author = ""
        if (options.scrubTitle) info.title = ""
        if (options.scrubSubject) info.subject = ""
        if (options.scrubKeywords) info.keywords = ""
        if (options.scrubCreator) info.creator = ""
        if (options.scrubProducer) info.producer = ""
        if (options.scrubDates) {
            info.creationDate = null
            info.modificationDate = null
        }
        document.documentInformation = info
    }

    private fun applyWatermark(document: PDDocument, watermark: WatermarkModel) {
        val color = Color.parseColor(watermark.textColorHex)
        val red = Color.red(color) / 255f
        val green = Color.green(color) / 255f
        val blue = Color.blue(color) / 255f
        repeat(document.numberOfPages) { pageIndex ->
            val page = document.getPage(pageIndex)
            val mediaBox = page.mediaBox
            PDPageContentStream(document, page, PDPageContentStream.AppendMode.APPEND, true, true).use { content ->
                content.setNonStrokingColor(red, green, blue)
                content.beginText()
                content.setFont(PDType1Font.HELVETICA_BOLD, watermark.fontSize)
                content.setTextMatrix(Matrix.getRotateInstance(Math.toRadians(watermark.rotationDegrees.toDouble()), mediaBox.width / 4f, mediaBox.height / 2f))
                content.showText(watermark.text)
                content.endText()
            }
        }
    }

    private fun placeSignatureAppearance(document: PDDocument, fieldModel: FormFieldModel, imagePath: String) {
        val page = document.getPage(fieldModel.pageIndex.coerceIn(0, document.numberOfPages - 1))
        val mediaBox = page.mediaBox
        val rect = PDRectangle(
            fieldModel.bounds.left * mediaBox.width,
            mediaBox.height - (fieldModel.bounds.bottom * mediaBox.height),
            fieldModel.bounds.width * mediaBox.width,
            fieldModel.bounds.height * mediaBox.height,
        )
        val bitmap = BitmapFactory.decodeFile(imagePath) ?: return
        PDPageContentStream(document, page, PDPageContentStream.AppendMode.APPEND, true, true).use { contentStream ->
            val image = LosslessFactory.createFromImage(document, bitmap)
            contentStream.drawImage(image, rect.lowerLeftX, rect.lowerLeftY - rect.height, rect.width, rect.height)
        }
    }

    private suspend fun recordAudit(
        type: AuditEventType,
        documentKey: String,
        message: String,
        metadata: Map<String, String> = emptyMap(),
    ) {
        securityRepository?.recordAudit(
            AuditTrailEventModel(
                id = UUID.randomUUID().toString(),
                documentKey = documentKey,
                type = type,
                actor = "local-user",
                message = message,
                createdAtEpochMillis = System.currentTimeMillis(),
                metadata = metadata,
            ),
        )
    }

    private fun openPassword(security: SecurityDocumentModel): String? {
        if (!security.passwordProtection.enabled) return null
        return security.passwordProtection.userPassword.ifBlank { security.passwordProtection.ownerPassword }.ifBlank { null }
    }
}

private fun com.aymanelbanhawy.editor.core.model.NormalizedRect.isWholePage(): Boolean {
    return left <= 0.001f && top <= 0.001f && right >= 0.999f && bottom >= 0.999f
}

package com.aymanelbanhawy.editor.core.security

import android.content.Context
import android.content.ContextWrapper
import com.aymanelbanhawy.editor.core.forms.DigitalSignatureMetadata
import com.aymanelbanhawy.editor.core.forms.DigitalSignatureService
import com.aymanelbanhawy.editor.core.forms.FormDocumentModel
import com.aymanelbanhawy.editor.core.forms.FormFieldModel
import com.aymanelbanhawy.editor.core.forms.FormFieldType
import com.aymanelbanhawy.editor.core.forms.FormFieldValue
import com.aymanelbanhawy.editor.core.forms.SignatureSourceType
import com.aymanelbanhawy.editor.core.forms.SignatureVerificationStatus
import com.aymanelbanhawy.editor.core.forms.SigningIdentityModel
import com.aymanelbanhawy.editor.core.model.AnnotationExportMode
import com.aymanelbanhawy.editor.core.model.DocumentModel
import com.aymanelbanhawy.editor.core.model.DocumentSourceType
import com.aymanelbanhawy.editor.core.model.NormalizedRect
import com.aymanelbanhawy.editor.core.model.PageContentType
import com.aymanelbanhawy.editor.core.model.PageModel
import com.aymanelbanhawy.editor.core.model.PdfDocumentRef
import com.google.common.truth.Truth.assertThat
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class SavedDocumentSecurityProcessorTest {
    private val context = object : ContextWrapper(RuntimeEnvironment.getApplication()) {
        private val root = File(System.getProperty("java.io.tmpdir"), "saved-document-security-processor-test").apply { mkdirs() }
        override fun getFilesDir(): File = File(root, "files").apply { mkdirs() }
        override fun getCacheDir(): File = File(root, "cache").apply { mkdirs() }
        override fun getApplicationContext(): Context = this
    }

    init {
        PDFBoxResourceLoader.init(context)
    }

    @Test
    fun applyWriteThroughSecurity_blocksExportWhenPolicyDisallows() = runBlocking {
        val securityRepository = RecordingSecurityRepository(blockExport = true)
        val processor = SavedDocumentSecurityProcessor(securityRepository, null)
        val file = createPdfWithText("blocked-${System.nanoTime()}.pdf", "blocked")
        val document = document(file).copy(
            security = SecurityDocumentModel(
                permissions = DocumentPermissionModel(allowExport = true),
                tenantPolicy = TenantPolicyHooksModel(disableExport = true),
            ),
        )

        val failure = runCatching {
            processor.applyWriteThroughSecurity(document, file, AnnotationExportMode.Editable)
        }.exceptionOrNull()

        assertThat(failure).isInstanceOf(SecurityException::class.java)
        assertThat(securityRepository.events.any { it.type == AuditEventType.PolicyBlocked }).isTrue()
    }

    @Test
    fun applyWriteThroughSecurity_appliesPasswordProtectionAndMetadataScrub() = runBlocking {
        val file = createPdfWithText("protected-${System.nanoTime()}.pdf", "internal")
        PDDocument.load(file).use { document ->
            document.documentInformation.title = "Secret Title"
            document.documentInformation.author = "Ayman"
            document.save(file)
        }
        val processor = SavedDocumentSecurityProcessor(RecordingSecurityRepository(), null)
        val document = document(file).copy(
            security = SecurityDocumentModel(
                passwordProtection = PasswordProtectionModel(enabled = true, userPassword = "user-pass", ownerPassword = "owner-pass"),
                metadataScrub = MetadataScrubOptionsModel(enabled = true),
            ),
        )

        val updated = processor.applyWriteThroughSecurity(document, file, AnnotationExportMode.Editable)

        PDDocument.load(file, "user-pass").use { protectedDoc ->
            assertThat(protectedDoc.isEncrypted).isTrue()
            assertThat(protectedDoc.documentInformation.title).isEmpty()
            assertThat(protectedDoc.documentInformation.author).isEmpty()
        }
        assertThat(updated.security.inspectionReport.protectionFlags).contains("password-protected")
    }

    @Test
    fun applyWriteThroughSecurity_appliesRedactionIrreversibly() = runBlocking {
        val file = createPdfWithText("redaction-${System.nanoTime()}.pdf", "SECRET")
        val processor = SavedDocumentSecurityProcessor(RecordingSecurityRepository(), null)
        val document = document(file).copy(
            security = SecurityDocumentModel(
                redactionWorkflow = RedactionWorkflowModel(
                    marks = listOf(
                        RedactionMarkModel(
                            id = "redact-1",
                            pageIndex = 0,
                            bounds = NormalizedRect(0f, 0f, 1f, 1f),
                            label = "Remove secret",
                            status = RedactionStatus.Applied,
                            createdAtEpochMillis = 1L,
                            appliedAtEpochMillis = 2L,
                        ),
                    ),
                    irreversibleConfirmed = true,
                ),
            ),
        )

        processor.applyWriteThroughSecurity(document, file, AnnotationExportMode.Editable)

        PDDocument.load(file).use { redacted ->
            val text = PDFTextStripper().getText(redacted)
            assertThat(text).doesNotContain("SECRET")
        }
    }

    @Test
    fun applyWriteThroughSecurity_updatesSignatureVerificationState() = runBlocking {
        val file = createPdfWithText("signed-${System.nanoTime()}.pdf", "signed")
        val signatureService = RecordingDigitalSignatureService()
        val processor = SavedDocumentSecurityProcessor(RecordingSecurityRepository(), signatureService)
        val document = document(file).copy(
            formDocument = FormDocumentModel(
                fields = listOf(
                    FormFieldModel(
                        name = "sig-1",
                        label = "Signature",
                        pageIndex = 0,
                        bounds = NormalizedRect(0.1f, 0.1f, 0.4f, 0.2f),
                        type = FormFieldType.Signature,
                        value = FormFieldValue.SignatureValue(
                            signerName = "Signer",
                            sourceType = SignatureSourceType.CertificateBacked,
                            signingIdentityId = "identity-1",
                            status = SignatureVerificationStatus.Signed,
                        ),
                        signatureStatus = SignatureVerificationStatus.Signed,
                    ),
                ),
            ),
        )

        val updated = processor.applyWriteThroughSecurity(document, file, AnnotationExportMode.Editable)
        val field = updated.formDocument.fields.single()
        val value = field.value as FormFieldValue.SignatureValue

        assertThat(signatureService.signedFields).containsExactly("sig-1")
        assertThat(field.signatureStatus).isEqualTo(SignatureVerificationStatus.Verified)
        assertThat(value.digitalSignature?.verificationStatus).isEqualTo(SignatureVerificationStatus.Verified)
    }

    private fun document(file: File): DocumentModel {
        return DocumentModel(
            sessionId = "session-${file.nameWithoutExtension}",
            documentRef = PdfDocumentRef(
                uriString = file.toURI().toString(),
                displayName = file.name,
                sourceType = DocumentSourceType.File,
                sourceKey = file.absolutePath,
                workingCopyPath = file.absolutePath,
            ),
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
        )
    }

    private fun createPdfWithText(name: String, text: String): File {
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
}

private class RecordingSecurityRepository(
    private val blockExport: Boolean = false,
) : SecurityRepository {
    override val appLockState: StateFlow<AppLockStateModel> = MutableStateFlow(AppLockStateModel())
    val events = mutableListOf<AuditTrailEventModel>()

    override suspend fun loadAppLockSettings(): AppLockSettingsModel = AppLockSettingsModel()
    override suspend fun updateAppLockSettings(enabled: Boolean, pin: String, biometricsEnabled: Boolean, timeoutSeconds: Int): AppLockSettingsModel = AppLockSettingsModel(enabled, pin, biometricsEnabled, timeoutSeconds)
    override suspend fun lockApp(reason: AppLockReason) = Unit
    override suspend fun unlockWithPin(pin: String): Boolean = true
    override suspend fun unlockWithBiometric(): Boolean = true
    override suspend fun loadDocumentSecurity(documentKey: String): SecurityDocumentModel = SecurityDocumentModel()
    override suspend fun persistDocumentSecurity(documentKey: String, security: SecurityDocumentModel) = Unit
    override suspend fun inspectDocument(document: DocumentModel): InspectionReportModel = InspectionReportModel(
        generatedAtEpochMillis = System.currentTimeMillis(),
        protectionFlags = buildList {
            if (document.security.passwordProtection.enabled) add("password-protected")
            if (document.security.watermark.enabled) add("watermark")
            if (document.security.metadataScrub.enabled) add("metadata-scrub")
            if (document.security.redactionWorkflow.marks.any { it.status == RedactionStatus.Applied }) add("redactions-applied")
        },
        signatureStatusSummary = document.formDocument.fields.mapNotNull { field ->
            val value = field.value as? FormFieldValue.SignatureValue ?: return@mapNotNull null
            field.name to (value.digitalSignature?.verificationStatus?.name ?: value.status.name)
        }.toMap(),
    )
    override fun evaluatePolicy(security: SecurityDocumentModel, action: RestrictedAction): PolicyDecision {
        if (blockExport && action == RestrictedAction.Export) return PolicyDecision(false, "Export is disabled by tenant policy.")
        return PolicyDecision(true)
    }
    override suspend fun recordAudit(event: AuditTrailEventModel) { events += event }
    override suspend fun auditEvents(documentKey: String): List<AuditTrailEventModel> = events.filter { it.documentKey == documentKey }
    override suspend fun exportAuditTrail(documentKey: String, destination: File): File {
        destination.parentFile?.mkdirs()
        destination.writeText(events.joinToString("\n") { it.message })
        return destination
    }
}

private class RecordingDigitalSignatureService : DigitalSignatureService {
    val signedFields = mutableListOf<String>()

    override suspend fun importSigningIdentity(displayName: String, pkcs12File: File, password: CharArray): SigningIdentityModel {
        throw UnsupportedOperationException()
    }

    override suspend fun loadSigningIdentities(): List<SigningIdentityModel> = emptyList()
    override suspend fun findSigningIdentity(id: String): SigningIdentityModel? = null

    override suspend fun signDocument(
        documentFile: File,
        fieldName: String,
        pageIndex: Int,
        bounds: NormalizedRect,
        signatureValue: FormFieldValue.SignatureValue,
        timestampAuthorityUrl: String,
        password: String?,
    ): DigitalSignatureMetadata {
        signedFields += fieldName
        return DigitalSignatureMetadata(
            fieldName = fieldName,
            signerDisplayName = signatureValue.signerName,
            signingIdentityId = signatureValue.signingIdentityId,
            signedAtEpochMillis = System.currentTimeMillis(),
            verificationStatus = SignatureVerificationStatus.Verified,
            verificationMessage = "The digital signature is valid.",
        )
    }

    override suspend fun verifyDocument(documentFile: File, password: String?): Map<String, DigitalSignatureMetadata> {
        return signedFields.associateWith { fieldName ->
            DigitalSignatureMetadata(
                fieldName = fieldName,
                signerDisplayName = "Signer",
                signingIdentityId = "identity-1",
                signedAtEpochMillis = System.currentTimeMillis(),
                verificationStatus = SignatureVerificationStatus.Verified,
                verificationMessage = "The digital signature is valid.",
            )
        }
    }
}


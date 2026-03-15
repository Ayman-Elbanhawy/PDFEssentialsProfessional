package com.aymanelbanhawy.editor.core.forms

import com.aymanelbanhawy.editor.core.model.NormalizedPoint
import com.aymanelbanhawy.editor.core.model.NormalizedRect
import kotlinx.serialization.Serializable

@Serializable
enum class FormFieldType {
    Text,
    MultilineText,
    Checkbox,
    RadioGroup,
    Dropdown,
    Date,
    Signature,
}

@Serializable
enum class SignatureVerificationStatus {
    Unsigned,
    Signed,
    Verified,
    Invalid,
    VerificationFailed,
}

@Serializable
enum class SignatureKind {
    Signature,
    Initials,
}

@Serializable
enum class SignatureSourceType {
    Handwritten,
    CertificateBacked,
}

@Serializable
enum class SignatureDigestAlgorithm {
    Sha256,
}

@Serializable
enum class TimestampHookStatus {
    Disabled,
    PendingHook,
    Applied,
    Failed,
}

@Serializable
data class FormFieldOption(
    val label: String,
    val value: String,
)

@Serializable
data class TimestampValidationModel(
    val enabled: Boolean = false,
    val authorityUrl: String = "",
    val status: TimestampHookStatus = TimestampHookStatus.Disabled,
    val appliedAtEpochMillis: Long? = null,
    val message: String = "",
)

@Serializable
data class DigitalSignatureMetadata(
    val fieldName: String = "",
    val sourceType: SignatureSourceType = SignatureSourceType.Handwritten,
    val signingIdentityId: String = "",
    val signerDisplayName: String = "",
    val certificateSubject: String = "",
    val certificateIssuer: String = "",
    val certificateSerialNumberHex: String = "",
    val certificateSha256: String = "",
    val digestAlgorithm: SignatureDigestAlgorithm = SignatureDigestAlgorithm.Sha256,
    val documentDigestSha256: String = "",
    val signedAtEpochMillis: Long = 0L,
    val invalidatedAtEpochMillis: Long? = null,
    val lastVerifiedAtEpochMillis: Long? = null,
    val verificationStatus: SignatureVerificationStatus = SignatureVerificationStatus.Unsigned,
    val verificationMessage: String = "",
    val signatureSubFilter: String = "",
    val byteRange: List<Int> = emptyList(),
    val timestamp: TimestampValidationModel = TimestampValidationModel(),
)

@Serializable
data class SigningIdentityModel(
    val id: String,
    val displayName: String,
    val subjectCommonName: String,
    val issuerCommonName: String,
    val serialNumberHex: String,
    val certificateSha256: String,
    val validFromEpochMillis: Long,
    val validToEpochMillis: Long,
    val createdAtEpochMillis: Long,
)

@Serializable
sealed interface FormFieldValue {
    @Serializable
    data class Text(val text: String = "") : FormFieldValue

    @Serializable
    data class BooleanValue(val checked: Boolean = false) : FormFieldValue

    @Serializable
    data class Choice(val selected: String = "") : FormFieldValue

    @Serializable
    data class SignatureValue(
        val savedSignatureId: String = "",
        val signerName: String = "",
        val signedAtEpochMillis: Long = 0L,
        val status: SignatureVerificationStatus = SignatureVerificationStatus.Unsigned,
        val imagePath: String? = null,
        val kind: SignatureKind = SignatureKind.Signature,
        val sourceType: SignatureSourceType = SignatureSourceType.Handwritten,
        val signingIdentityId: String = "",
        val certificateDisplayName: String = "",
        val reason: String = "Approved",
        val location: String = "",
        val contactInfo: String = "",
        val digitalSignature: DigitalSignatureMetadata? = null,
    ) : FormFieldValue
}

@Serializable
data class FormFieldModel(
    val name: String,
    val label: String,
    val pageIndex: Int,
    val bounds: NormalizedRect,
    val type: FormFieldType,
    val required: Boolean = false,
    val options: List<FormFieldOption> = emptyList(),
    val value: FormFieldValue,
    val placeholder: String = "",
    val maxLength: Int? = null,
    val readOnly: Boolean = false,
    val exportValue: String = "",
    val helperText: String = "",
    val signatureStatus: SignatureVerificationStatus = SignatureVerificationStatus.Unsigned,
)

@Serializable
data class FormDocumentModel(
    val fields: List<FormFieldModel> = emptyList(),
) {
    fun updateField(updated: FormFieldModel): FormDocumentModel = copy(
        fields = fields.map { field -> if (field.name == updated.name) updated else field },
    )

    fun invalidateSignatures(message: String): FormDocumentModel {
        val now = System.currentTimeMillis()
        return copy(
            fields = fields.map { field ->
                val signatureValue = field.value as? FormFieldValue.SignatureValue ?: return@map field
                val activeStatus = signatureValue.digitalSignature?.verificationStatus ?: signatureValue.status
                if (activeStatus == SignatureVerificationStatus.Unsigned) {
                    field
                } else {
                    field.copy(
                        value = signatureValue.copy(
                            status = SignatureVerificationStatus.Invalid,
                            digitalSignature = signatureValue.digitalSignature?.copy(
                                verificationStatus = SignatureVerificationStatus.Invalid,
                                verificationMessage = message,
                                invalidatedAtEpochMillis = now,
                                lastVerifiedAtEpochMillis = now,
                            ),
                        ),
                        signatureStatus = SignatureVerificationStatus.Invalid,
                    )
                }
            },
        )
    }

    fun field(name: String): FormFieldModel? = fields.firstOrNull { it.name == name }
}

@Serializable
enum class ValidationSeverity {
    Info,
    Warning,
    Error,
}

@Serializable
data class FormValidationIssue(
    val fieldName: String,
    val message: String,
    val severity: ValidationSeverity,
)

@Serializable
data class FormValidationSummary(
    val issues: List<FormValidationIssue> = emptyList(),
) {
    val isValid: Boolean get() = issues.none { it.severity == ValidationSeverity.Error }
    fun issueFor(fieldName: String): FormValidationIssue? = issues.firstOrNull { it.fieldName == fieldName }
}

@Serializable
data class SignatureStroke(
    val points: List<NormalizedPoint>,
)

@Serializable
data class SignatureCapture(
    val strokes: List<SignatureStroke>,
    val width: Float,
    val height: Float,
)

@Serializable
data class SavedSignatureModel(
    val id: String,
    val name: String,
    val kind: SignatureKind,
    val imagePath: String,
    val createdAtEpochMillis: Long,
    val sourceType: SignatureSourceType = SignatureSourceType.Handwritten,
    val signingIdentityId: String = "",
    val signerDisplayName: String = "",
    val certificateSubject: String = "",
    val certificateSha256: String = "",
)

@Serializable
data class FormProfileModel(
    val id: String,
    val name: String,
    val values: Map<String, FormFieldValue>,
    val createdAtEpochMillis: Long,
)

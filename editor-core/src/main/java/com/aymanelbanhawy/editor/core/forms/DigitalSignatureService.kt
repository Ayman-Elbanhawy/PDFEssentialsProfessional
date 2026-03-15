package com.aymanelbanhawy.editor.core.forms

import android.content.Context
import android.graphics.BitmapFactory
import com.aymanelbanhawy.editor.core.data.SigningIdentityDao
import com.aymanelbanhawy.editor.core.data.SigningIdentityEntity
import com.aymanelbanhawy.editor.core.model.NormalizedRect
import com.aymanelbanhawy.editor.core.security.SecureFileCipher
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.interactive.digitalsignature.PDSignature
import com.tom_roush.pdfbox.pdmodel.interactive.digitalsignature.SignatureInterface
import com.tom_roush.pdfbox.pdmodel.interactive.digitalsignature.SignatureOptions
import com.tom_roush.pdfbox.pdmodel.interactive.digitalsignature.visible.PDVisibleSigProperties
import com.tom_roush.pdfbox.pdmodel.interactive.digitalsignature.visible.PDVisibleSignDesigner
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDSignatureField
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.security.KeyStore
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.Security
import java.security.cert.X509Certificate
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.bouncycastle.cert.X509CertificateHolder
import org.bouncycastle.cert.jcajce.JcaCertStore
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cms.CMSProcessableByteArray
import org.bouncycastle.cms.CMSSignedData
import org.bouncycastle.cms.CMSSignedDataGenerator
import org.bouncycastle.cms.SignerInformation
import org.bouncycastle.cms.jcajce.JcaSignerInfoGeneratorBuilder
import org.bouncycastle.cms.jcajce.JcaSimpleSignerInfoVerifierBuilder
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder
import org.bouncycastle.util.Store

interface DigitalSignatureService {
    suspend fun importSigningIdentity(displayName: String, pkcs12File: File, password: CharArray): SigningIdentityModel
    suspend fun loadSigningIdentities(): List<SigningIdentityModel>
    suspend fun findSigningIdentity(id: String): SigningIdentityModel?
    suspend fun signDocument(
        documentFile: File,
        fieldName: String,
        pageIndex: Int,
        bounds: NormalizedRect,
        signatureValue: FormFieldValue.SignatureValue,
        timestampAuthorityUrl: String = "",
        password: String? = null,
    ): DigitalSignatureMetadata
    suspend fun verifyDocument(documentFile: File, password: String? = null): Map<String, DigitalSignatureMetadata>
}

class PdfBoxDigitalSignatureService(
    private val context: Context,
    private val signingIdentityDao: SigningIdentityDao,
    private val secureFileCipher: SecureFileCipher,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : DigitalSignatureService {

    init {
        PDFBoxResourceLoader.init(context)
        if (Security.getProvider(BOUNCY_CASTLE_PROVIDER) == null) {
            Security.addProvider(BouncyCastleProvider())
        }
    }

    override suspend fun importSigningIdentity(displayName: String, pkcs12File: File, password: CharArray): SigningIdentityModel = withContext(ioDispatcher) {
        require(pkcs12File.exists()) { "Signing identity file does not exist: ${pkcs12File.absolutePath}" }
        val pkcs12Bytes = pkcs12File.readBytes()
        val keyStore = KeyStore.getInstance("PKCS12").apply {
            load(ByteArrayInputStream(pkcs12Bytes), password)
        }
        val alias = keyStore.aliases().toList().firstOrNull { keyStore.isKeyEntry(it) }
            ?: error("No private key entry found in PKCS#12 file")
        val certificate = keyStore.getCertificate(alias) as? X509Certificate
            ?: error("No X509 certificate found for alias $alias")
        val id = UUID.randomUUID().toString()
        val encryptedFile = File(context.filesDir, "signing-identities/$id.p12.enc")
        val encryptedPasswordFile = File(context.filesDir, "signing-identities/$id.password.enc")
        secureFileCipher.encryptToFile(pkcs12Bytes, encryptedFile)
        secureFileCipher.encryptToFile(password.concatToString().toByteArray(Charsets.UTF_8), encryptedPasswordFile)
        val model = SigningIdentityModel(
            id = id,
            displayName = displayName.ifBlank { commonName(certificate.subjectX500Principal.name) },
            subjectCommonName = commonName(certificate.subjectX500Principal.name),
            issuerCommonName = commonName(certificate.issuerX500Principal.name),
            serialNumberHex = certificate.serialNumber.toString(16),
            certificateSha256 = sha256(certificate.encoded),
            validFromEpochMillis = certificate.notBefore.time,
            validToEpochMillis = certificate.notAfter.time,
            createdAtEpochMillis = System.currentTimeMillis(),
        )
        signingIdentityDao.upsert(
            SigningIdentityEntity(
                id = model.id,
                displayName = model.displayName,
                subjectCommonName = model.subjectCommonName,
                issuerCommonName = model.issuerCommonName,
                serialNumberHex = model.serialNumberHex,
                certificateSha256 = model.certificateSha256,
                validFromEpochMillis = model.validFromEpochMillis,
                validToEpochMillis = model.validToEpochMillis,
                encryptedPkcs12Path = encryptedFile.absolutePath,
                encryptedPasswordPath = encryptedPasswordFile.absolutePath,
                certificateAlias = alias,
                createdAtEpochMillis = model.createdAtEpochMillis,
            ),
        )
        model
    }

    override suspend fun loadSigningIdentities(): List<SigningIdentityModel> = withContext(ioDispatcher) {
        signingIdentityDao.all().map { it.toModel() }
    }

    override suspend fun findSigningIdentity(id: String): SigningIdentityModel? = withContext(ioDispatcher) {
        signingIdentityDao.findById(id)?.toModel()
    }

    override suspend fun signDocument(
        documentFile: File,
        fieldName: String,
        pageIndex: Int,
        bounds: NormalizedRect,
        signatureValue: FormFieldValue.SignatureValue,
        timestampAuthorityUrl: String,
        password: String?,
    ): DigitalSignatureMetadata = withContext(ioDispatcher) {
        val identityId = signatureValue.signingIdentityId.ifBlank {
            error("A certificate-backed signature requires a signing identity")
        }
        val identity = signingIdentityDao.findById(identityId)
            ?: error("Signing identity $identityId was not found")
        val credentials = loadIdentity(identity)
        val tempSigned = File(documentFile.parentFile, documentFile.name + ".signed.tmp")
        val signatureDate = Calendar.getInstance()
        val digitalSignature = PDSignature().apply {
            setFilter(PDSignature.FILTER_ADOBE_PPKLITE)
            setSubFilter(PDSignature.SUBFILTER_ADBE_PKCS7_DETACHED)
            name = signatureValue.signerName.ifBlank { credentials.certificate.subjectX500Principal.name }
            location = signatureValue.location
            reason = signatureValue.reason
            contactInfo = signatureValue.contactInfo
            signDate = signatureDate
        }
        PDDocument.load(documentFile, password).use { document ->
            val options = SignatureOptions()
            try {
                if (!signatureValue.imagePath.isNullOrBlank()) {
                    val properties = buildVisibleSignature(document, signatureValue.imagePath, fieldName, pageIndex, bounds, signatureValue)
                    options.setVisualSignature(properties)
                    options.page = pageIndex
                }
                document.addSignature(digitalSignature, CmsSignatureInterface(credentials.privateKey, credentials.certificateChain), options)
                FileOutputStream(tempSigned).use { output -> document.saveIncremental(output) }
            } finally {
                options.close()
            }
        }
        tempSigned.copyTo(documentFile, overwrite = true)
        tempSigned.delete()
        verifyDocument(documentFile, password)[fieldName]
            ?: DigitalSignatureMetadata(
                fieldName = fieldName,
                sourceType = SignatureSourceType.CertificateBacked,
                signingIdentityId = identity.id,
                signerDisplayName = signatureValue.signerName.ifBlank { identity.displayName },
                certificateSubject = identity.subjectCommonName,
                certificateIssuer = identity.issuerCommonName,
                certificateSerialNumberHex = identity.serialNumberHex,
                certificateSha256 = identity.certificateSha256,
                documentDigestSha256 = sha256(documentFile.readBytes()),
                signedAtEpochMillis = signatureDate.timeInMillis,
                verificationStatus = SignatureVerificationStatus.VerificationFailed,
                verificationMessage = "The document was signed, but verification metadata could not be reconstructed.",
                signatureSubFilter = PDSignature.SUBFILTER_ADBE_PKCS7_DETACHED.name,
                timestamp = timestampMetadata(timestampAuthorityUrl),
            )
    }

    override suspend fun verifyDocument(documentFile: File, password: String?): Map<String, DigitalSignatureMetadata> = withContext(ioDispatcher) {
        if (!documentFile.exists()) return@withContext emptyMap()
        val bytes = documentFile.readBytes()
        PDDocument.load(documentFile, password).use { document ->
            val verificationByField = linkedMapOf<String, DigitalSignatureMetadata>()
            document.documentCatalog?.acroForm?.fieldTree
                ?.filterIsInstance<PDSignatureField>()
                ?.forEach { field ->
                    val signature = field.signature ?: return@forEach
                    val fieldName = field.fullyQualifiedName ?: field.partialName ?: "signature-${verificationByField.size + 1}"
                    verificationByField[fieldName] = verifySignature(fieldName, signature, bytes)
                }
            verificationByField
        }
    }

    private fun buildVisibleSignature(
        document: PDDocument,
        imagePath: String,
        fieldName: String,
        pageIndex: Int,
        bounds: NormalizedRect,
        signatureValue: FormFieldValue.SignatureValue,
    ): PDVisibleSigProperties {
        val bitmap = BitmapFactory.decodeFile(imagePath) ?: error("Unable to decode signature appearance image: $imagePath")
        val page = document.getPage(pageIndex.coerceIn(0, document.numberOfPages - 1))
        val mediaBox = page.mediaBox
        val width = bounds.width * mediaBox.width
        val height = bounds.height * mediaBox.height
        val lowerLeftX = bounds.left * mediaBox.width
        val lowerLeftY = mediaBox.height - (bounds.bottom * mediaBox.height)
        val designer = PDVisibleSignDesigner(document, bitmap, pageIndex + 1)
            .xAxis(lowerLeftX)
            .yAxis(lowerLeftY - height)
            .width(width)
            .height(height)
            .signatureFieldName(fieldName)
            .signatureText(
                buildString {
                    append(signatureValue.signerName.ifBlank { "Signed" })
                    append("\n")
                    append(SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date()))
                },
            )
            .adjustForRotation()
        return PDVisibleSigProperties()
            .signerName(signatureValue.signerName)
            .signerLocation(signatureValue.location)
            .signatureReason(signatureValue.reason)
            .page(pageIndex)
            .preferredSize(SignatureOptions.DEFAULT_SIGNATURE_SIZE * 2)
            .visualSignEnabled(true)
            .setPdVisibleSignature(designer)
            .also { it.buildSignature() }
    }

    private fun verifySignature(fieldName: String, signature: PDSignature, bytes: ByteArray): DigitalSignatureMetadata {
        val cms = CMSSignedData(CMSProcessableByteArray(signature.getSignedContent(bytes)), signature.getContents(bytes))
        val signerInfo = cms.signerInfos.signers.firstOrNull() as? SignerInformation
        if (signerInfo == null) {
            return DigitalSignatureMetadata(
                fieldName = fieldName,
                sourceType = SignatureSourceType.CertificateBacked,
                verificationStatus = SignatureVerificationStatus.VerificationFailed,
                verificationMessage = "No signer information was found in the CMS payload.",
                signatureSubFilter = signature.subFilter.orEmpty(),
                byteRange = signature.byteRange?.toList().orEmpty(),
            )
        }
        val certificateStore = cms.certificates as Store<X509CertificateHolder>
        val certificateHolder = certificateStore.getMatches(null)
            .firstOrNull { holder ->
                holder is X509CertificateHolder && holder.serialNumber == signerInfo.sid.serialNumber
            } as? X509CertificateHolder
        if (certificateHolder == null) {
            return DigitalSignatureMetadata(
                fieldName = fieldName,
                sourceType = SignatureSourceType.CertificateBacked,
                verificationStatus = SignatureVerificationStatus.VerificationFailed,
                verificationMessage = "The signer certificate could not be located in the CMS payload.",
                signatureSubFilter = signature.subFilter.orEmpty(),
                byteRange = signature.byteRange?.toList().orEmpty(),
            )
        }
        val certificate = JcaX509CertificateConverter().setProvider(BOUNCY_CASTLE_PROVIDER).getCertificate(certificateHolder)
        val cryptographicallyValid = runCatching {
            signerInfo.verify(JcaSimpleSignerInfoVerifierBuilder().setProvider(BOUNCY_CASTLE_PROVIDER).build(certificate))
        }.getOrDefault(false)
        val statusMessage = runCatching {
            certificate.checkValidity(Date())
            if (cryptographicallyValid) {
                "The digital signature is valid."
            } else {
                "The PDF signature no longer matches the document contents."
            }
        }.getOrElse { throwable -> throwable.message ?: "The certificate is no longer valid." }
        return DigitalSignatureMetadata(
            fieldName = fieldName,
            sourceType = SignatureSourceType.CertificateBacked,
            signerDisplayName = signature.name.orEmpty(),
            certificateSubject = certificate.subjectX500Principal.name,
            certificateIssuer = certificate.issuerX500Principal.name,
            certificateSerialNumberHex = certificate.serialNumber.toString(16),
            certificateSha256 = sha256(certificate.encoded),
            documentDigestSha256 = sha256(bytes),
            signedAtEpochMillis = signature.signDate?.timeInMillis ?: 0L,
            lastVerifiedAtEpochMillis = System.currentTimeMillis(),
            verificationStatus = if (cryptographicallyValid) SignatureVerificationStatus.Verified else SignatureVerificationStatus.Invalid,
            verificationMessage = statusMessage,
            signatureSubFilter = signature.subFilter.orEmpty(),
            byteRange = signature.byteRange?.toList().orEmpty(),
        )
    }

    private fun loadIdentity(entity: SigningIdentityEntity): LoadedIdentity {
        val decryptedBytes = secureFileCipher.decryptFromFile(File(entity.encryptedPkcs12Path))
        val password = secureFileCipher.decryptFromFile(File(entity.encryptedPasswordPath)).toString(Charsets.UTF_8)
        val keyStore = KeyStore.getInstance("PKCS12").apply {
            load(ByteArrayInputStream(decryptedBytes), password.toCharArray())
        }
        val alias = if (keyStore.containsAlias(entity.certificateAlias)) entity.certificateAlias else keyStore.aliases().toList().first { keyStore.isKeyEntry(it) }
        val privateKey = keyStore.getKey(alias, password.toCharArray()) as? PrivateKey
            ?: error("Unable to unlock private key for signing identity ${entity.displayName}")
        val chain = keyStore.getCertificateChain(alias).map { it as X509Certificate }
        return LoadedIdentity(privateKey, chain.first(), chain)
    }

    private fun timestampMetadata(authorityUrl: String): TimestampValidationModel {
        return if (authorityUrl.isBlank()) {
            TimestampValidationModel()
        } else {
            TimestampValidationModel(
                enabled = true,
                authorityUrl = authorityUrl,
                status = TimestampHookStatus.PendingHook,
                message = "Timestamp authority hooks are configured for $authorityUrl and can be connected without changing the signing pipeline.",
            )
        }
    }

    private fun SigningIdentityEntity.toModel(): SigningIdentityModel = SigningIdentityModel(
        id = id,
        displayName = displayName,
        subjectCommonName = subjectCommonName,
        issuerCommonName = issuerCommonName,
        serialNumberHex = serialNumberHex,
        certificateSha256 = certificateSha256,
        validFromEpochMillis = validFromEpochMillis,
        validToEpochMillis = validToEpochMillis,
        createdAtEpochMillis = createdAtEpochMillis,
    )

    private fun commonName(distinguishedName: String): String {
        return distinguishedName.split(',')
            .map { it.trim() }
            .firstOrNull { it.startsWith("CN=", ignoreCase = true) }
            ?.substringAfter('=')
            ?.trim()
            .orEmpty()
    }

    private fun sha256(bytes: ByteArray): String {
        return MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString(separator = "") { "%02x".format(it) }
    }

    private inner class CmsSignatureInterface(
        private val privateKey: PrivateKey,
        private val certificateChain: List<X509Certificate>,
    ) : SignatureInterface {
        override fun sign(content: InputStream): ByteArray {
            val contentBytes = content.readBytes()
            val generator = CMSSignedDataGenerator()
            val contentSigner = JcaContentSignerBuilder(signatureAlgorithm(privateKey))
                .setProvider(BOUNCY_CASTLE_PROVIDER)
                .build(privateKey)
            val digestProvider = JcaDigestCalculatorProviderBuilder()
                .setProvider(BOUNCY_CASTLE_PROVIDER)
                .build()
            val signerInfoGenerator = JcaSignerInfoGeneratorBuilder(digestProvider)
                .build(contentSigner, certificateChain.first())
            generator.addSignerInfoGenerator(signerInfoGenerator)
            generator.addCertificates(JcaCertStore(certificateChain))
            return generator.generate(CMSProcessableByteArray(contentBytes), false).encoded
        }
    }

    private fun signatureAlgorithm(privateKey: PrivateKey): String {
        return when (privateKey.algorithm.uppercase(Locale.US)) {
            "EC", "ECDSA" -> "SHA256withECDSA"
            else -> "SHA256withRSA"
        }
    }

    private data class LoadedIdentity(
        val privateKey: PrivateKey,
        val certificate: X509Certificate,
        val certificateChain: List<X509Certificate>,
    )

    private companion object {
        const val BOUNCY_CASTLE_PROVIDER = "BC"
    }
}




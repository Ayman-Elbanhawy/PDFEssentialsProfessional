package com.aymanelbanhawy.editor.core.repository

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.core.net.toUri
import com.aymanelbanhawy.editor.core.compat.FileLegacyAnnotationCompatibilityStore
import com.aymanelbanhawy.editor.core.compat.LegacyAnnotationCompatibilityStore
import com.aymanelbanhawy.editor.core.data.DocumentSecurityDao
import com.aymanelbanhawy.editor.core.data.DocumentSecurityEntity
import com.aymanelbanhawy.editor.core.data.DraftDao
import com.aymanelbanhawy.editor.core.data.DraftEntity
import com.aymanelbanhawy.editor.core.data.EditHistoryMetadataDao
import com.aymanelbanhawy.editor.core.data.EditHistoryMetadataEntity
import com.aymanelbanhawy.editor.core.data.RecentDocumentDao
import com.aymanelbanhawy.editor.core.data.RecentDocumentEntity
import com.aymanelbanhawy.editor.core.forms.DigitalSignatureService
import com.aymanelbanhawy.editor.core.migration.FileLegacyEditCompatibilityBridge
import com.aymanelbanhawy.editor.core.migration.LegacyEditCompatibilityBridge
import com.aymanelbanhawy.editor.core.forms.FormDocumentModel
import com.aymanelbanhawy.editor.core.forms.FormFieldModel
import com.aymanelbanhawy.editor.core.forms.FormFieldOption
import com.aymanelbanhawy.editor.core.forms.FormFieldType
import com.aymanelbanhawy.editor.core.forms.FormFieldValue
import com.aymanelbanhawy.editor.core.forms.SignatureVerificationStatus
import com.aymanelbanhawy.editor.core.model.AnnotationExportMode
import com.aymanelbanhawy.editor.core.model.AnnotationModel
import com.aymanelbanhawy.editor.core.model.DocumentModel
import com.aymanelbanhawy.editor.core.model.DocumentSourceType
import com.aymanelbanhawy.editor.core.model.DraftPayload
import com.aymanelbanhawy.editor.core.model.DirtyState
import com.aymanelbanhawy.editor.core.model.NormalizedRect
import com.aymanelbanhawy.editor.core.model.OpenDocumentRequest
import com.aymanelbanhawy.editor.core.model.PageContentType
import com.aymanelbanhawy.editor.core.model.PageModel
import com.aymanelbanhawy.editor.core.model.PdfDocumentRef
import com.aymanelbanhawy.editor.core.model.SelectionModel
import com.aymanelbanhawy.editor.core.model.UndoRedoState
import com.aymanelbanhawy.editor.core.ocr.OcrSessionStore
import com.aymanelbanhawy.editor.core.organize.SplitPlanner
import com.aymanelbanhawy.editor.core.organize.SplitRequest
import com.aymanelbanhawy.editor.core.runtime.RuntimeDiagnosticsRepository
import com.aymanelbanhawy.editor.core.runtime.RuntimeEventCategory
import com.aymanelbanhawy.editor.core.runtime.RuntimeLogLevel
import com.aymanelbanhawy.editor.core.security.AndroidSecureFileCipher
import com.aymanelbanhawy.editor.core.security.MetadataScrubOptionsModel
import com.aymanelbanhawy.editor.core.security.PasswordProtectionModel
import com.aymanelbanhawy.editor.core.security.RedactionMarkModel
import com.aymanelbanhawy.editor.core.security.RedactionStatus
import com.aymanelbanhawy.editor.core.security.SecureFileCipher
import com.aymanelbanhawy.editor.core.security.SavedDocumentSecurityProcessor
import com.aymanelbanhawy.editor.core.security.SecurityDocumentModel
import com.aymanelbanhawy.editor.core.security.SecurityRepository
import com.aymanelbanhawy.editor.core.security.WatermarkModel
import com.aymanelbanhawy.editor.core.write.PdfBoxWriteEngine
import com.aymanelbanhawy.editor.core.write.PdfWriteEngine
import com.aymanelbanhawy.editor.core.write.SaveStrategy
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDAcroForm
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDCheckBox
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDChoice
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDField
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDRadioButton
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDSignatureField
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDTextField
import java.io.File
import java.io.InputStream
import java.util.UUID
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

interface DocumentRepository {
    suspend fun open(request: OpenDocumentRequest): DocumentModel
    suspend fun importPages(requests: List<OpenDocumentRequest>): List<PageModel>
    suspend fun persistDraft(payload: DraftPayload, autosave: Boolean)
    suspend fun restoreDraft(sourceKey: String): DraftRestoreResult?
    suspend fun clearDraft(sourceKey: String)
    suspend fun save(document: DocumentModel, exportMode: AnnotationExportMode = AnnotationExportMode.Editable): DocumentModel
    suspend fun saveAs(document: DocumentModel, destination: File, exportMode: AnnotationExportMode = AnnotationExportMode.Editable): DocumentModel
    suspend fun split(document: DocumentModel, request: SplitRequest, outputDirectory: File): List<File>
    fun createAutosaveTempFile(sessionId: String): File
}

data class DraftRestoreResult(
    val document: DocumentModel,
    val selection: SelectionModel,
    val undoRedoState: UndoRedoState,
)

class DefaultDocumentRepository(
    private val context: Context,
    private val recentDocumentDao: RecentDocumentDao,
    private val draftDao: DraftDao,
    private val editHistoryMetadataDao: EditHistoryMetadataDao,
    private val documentSecurityDao: DocumentSecurityDao,
    private val pdfWriteEngine: PdfWriteEngine = PdfBoxWriteEngine(context),
    private val secureFileCipher: SecureFileCipher = AndroidSecureFileCipher(context),
    private val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        classDiscriminator = "_type"
    },
    private val ocrSessionStore: OcrSessionStore = OcrSessionStore(json),
    private val legacyEditCompatibilityBridge: LegacyEditCompatibilityBridge = FileLegacyEditCompatibilityBridge(json),
    private val legacyAnnotationCompatibilityStore: LegacyAnnotationCompatibilityStore = FileLegacyAnnotationCompatibilityStore(json),
    private val digitalSignatureService: DigitalSignatureService? = null,
    private val securityRepository: SecurityRepository? = null,
    private val diagnosticsRepository: RuntimeDiagnosticsRepository? = null,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : DocumentRepository {

    private val securityProcessor = SavedDocumentSecurityProcessor(securityRepository, digitalSignatureService, ioDispatcher)

    init {
        PDFBoxResourceLoader.init(context)
    }

    override suspend fun open(request: OpenDocumentRequest): DocumentModel = withContext(ioDispatcher) {
        repairStaleWorkingArtifacts()
        val startedAt = System.currentTimeMillis()
        val sessionId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val opened = when (request) {
            is OpenDocumentRequest.FromFile -> {
                val sourceFile = File(request.absolutePath)
                val workingFile = copyToWorkingFile(sourceFile.name, sourceFile.inputStream())
                buildDocument(sessionId, request.displayNameOverride ?: sourceFile.name, request.password, DocumentSourceType.File, sourceFile.absolutePath, workingFile)
            }
            is OpenDocumentRequest.FromUri -> {
                val uri = request.uriString.toUri()
                val input = requireNotNull(context.contentResolver.openInputStream(uri))
                val workingFile = copyToWorkingFile(request.displayName, input)
                buildDocument(sessionId, request.displayName, request.password, DocumentSourceType.Uri, request.uriString, workingFile)
            }
            is OpenDocumentRequest.FromAsset -> {
                val workingFile = copyToWorkingFile(request.displayName, context.assets.open(request.assetName))
                buildDocument(sessionId, request.displayName, request.password, DocumentSourceType.Asset, "asset://${request.assetName}", workingFile)
            }
            is OpenDocumentRequest.FromBytes -> {
                val workingFile = createWorkingFile(request.displayName).apply { writeBytes(request.bytes) }
                buildDocument(sessionId, request.displayName, request.password, DocumentSourceType.Memory, "memory://$sessionId/${request.displayName}", workingFile)
            }
        }
        legacyEditCompatibilityBridge.upgradeIfNeeded(opened.documentRef)
        val editableState = pdfWriteEngine.load(opened.documentRef)
        val legacyAnnotations = legacyAnnotationCompatibilityStore.loadAnnotations(opened.documentRef)
        val document = securityProcessor.decorateOpenedDocument(
            opened.copy(
                pages = opened.pages.map { page ->
                    page.copy(
                        annotations = editableState.annotationsByPage[page.index].orEmpty().ifEmpty { legacyAnnotations[page.index].orEmpty() },
                        editObjects = editableState.editObjectsByPage[page.index].orEmpty(),
                    )
                },
                formDocument = detectForms(opened.documentRef.workingCopyPath),
                security = loadSecurity(opened.documentRef.sourceKey),
            ),
        )
        recentDocumentDao.upsert(RecentDocumentEntity(document.documentRef.sourceKey, document.documentRef.displayName, document.documentRef.sourceType.name, document.documentRef.workingCopyPath, now))
        diagnosticsRepository?.recordDocumentOpen(document, System.currentTimeMillis() - startedAt)
        document
    }

    override suspend fun importPages(requests: List<OpenDocumentRequest>): List<PageModel> = withContext(ioDispatcher) {
        requests.flatMap { request ->
            val imported = open(request)
            imported.pages.map { page -> page.copy(sourceDocumentPath = imported.documentRef.workingCopyPath, sourcePageIndex = page.sourcePageIndex) }
        }
    }

    override suspend fun persistDraft(payload: DraftPayload, autosave: Boolean) = withContext(ioDispatcher) {
        val draftFile = stableDraftFile(payload.document.sessionId)
        draftFile.writeText(json.encodeToString(DraftPayload.serializer(), payload))
        val now = System.currentTimeMillis()
        draftDao.upsert(DraftEntity(payload.document.sessionId, payload.document.documentRef.sourceKey, payload.document.documentRef.displayName, draftFile.absolutePath, payload.document.documentRef.workingCopyPath, autosave, now))
        editHistoryMetadataDao.upsert(EditHistoryMetadataEntity(payload.document.sessionId, payload.document.documentRef.sourceKey, payload.undoCount, payload.redoCount, payload.lastCommandName, now))
    }

    override suspend fun restoreDraft(sourceKey: String): DraftRestoreResult? = withContext(ioDispatcher) {
        val draft = draftDao.getLatestForSource(sourceKey) ?: return@withContext null
        val payloadFile = File(draft.draftFilePath)
        if (!payloadFile.exists()) return@withContext null
        val payload = runCatching { json.decodeFromString(DraftPayload.serializer(), payloadFile.readText()) }.getOrElse {
            payloadFile.copyTo(File(payloadFile.absolutePath + ".corrupt"), overwrite = true)
            payloadFile.delete()
            draftDao.deleteBySession(draft.sessionId)
            diagnosticsRepository?.recordBreadcrumb(RuntimeEventCategory.Recovery, RuntimeLogLevel.Warn, "corrupted_draft", "Corrupted draft was quarantined.", mapOf("sourceKey" to sourceKey))
            return@withContext null
        }
        val metadata = editHistoryMetadataDao.getLatestForSource(sourceKey)
        DraftRestoreResult(
            document = payload.document.copy(restoredFromDraft = true),
            selection = payload.selection,
            undoRedoState = UndoRedoState(
                canUndo = (metadata?.undoCount ?: payload.undoCount) > 0,
                canRedo = (metadata?.redoCount ?: payload.redoCount) > 0,
                undoCount = metadata?.undoCount ?: payload.undoCount,
                redoCount = metadata?.redoCount ?: payload.redoCount,
                lastCommandName = metadata?.lastCommandName ?: payload.lastCommandName,
            ),
        )
    }

    override suspend fun clearDraft(sourceKey: String) = withContext(ioDispatcher) {
        draftDao.getLatestForSource(sourceKey)?.let { File(it.draftFilePath).delete() }
        draftDao.deleteBySource(sourceKey)
        editHistoryMetadataDao.deleteBySource(sourceKey)
    }

    override suspend fun save(document: DocumentModel, exportMode: AnnotationExportMode): DocumentModel = withContext(ioDispatcher) {
        val startedAt = System.currentTimeMillis()
        val ref = document.documentRef
        val workingFile = File(ref.workingCopyPath)
        val mutationResult = pdfWriteEngine.persist(document, workingFile, exportMode, SaveStrategy.IncrementalPreferred)
        val securedDocument = securityProcessor.applyWriteThroughSecurity(document, workingFile, exportMode)
        verifyFileIntegrity(workingFile)
        val destination = when (ref.sourceType) {
            DocumentSourceType.File -> File(ref.sourceKey).also {
                if (it.absolutePath != workingFile.absolutePath) workingFile.copyTo(it, overwrite = true)
            }
            else -> workingFile
        }
        ocrSessionStore.copyCompatibilityPayload(document.documentRef, destination)
        persistSecurity(destination.absolutePath, securedDocument.security)
        clearDraft(ref.sourceKey)
        diagnosticsRepository?.recordSave(securedDocument, System.currentTimeMillis() - startedAt, true, destination.length(), mutationResult.pdfSha256)
        securedDocument.copy(
            dirtyState = DirtyState(isDirty = false, saveMessage = "Saved (${exportMode.name.lowercase()})"),
            lastSavedAtEpochMillis = System.currentTimeMillis(),
        )
    }

    override suspend fun saveAs(document: DocumentModel, destination: File, exportMode: AnnotationExportMode): DocumentModel = withContext(ioDispatcher) {
        val startedAt = System.currentTimeMillis()
        destination.parentFile?.mkdirs()
        val mutationResult = pdfWriteEngine.persist(document, destination, exportMode, SaveStrategy.SaveAs)
        val securedDocument = securityProcessor.applyWriteThroughSecurity(document, destination, exportMode)
        verifyFileIntegrity(destination)
        ocrSessionStore.copyCompatibilityPayload(document.documentRef, destination)
        persistSecurity(destination.absolutePath, securedDocument.security)
        clearDraft(document.documentRef.sourceKey)
        diagnosticsRepository?.recordSave(securedDocument, System.currentTimeMillis() - startedAt, true, destination.length(), mutationResult.pdfSha256)
        val updatedRef = securedDocument.documentRef.copy(
            uriString = Uri.fromFile(destination).toString(),
            displayName = destination.name,
            sourceType = DocumentSourceType.File,
            sourceKey = destination.absolutePath,
            workingCopyPath = destination.absolutePath,
        )
        securedDocument.copy(
            documentRef = updatedRef,
            dirtyState = DirtyState(isDirty = false, saveMessage = "Saved as ${destination.name} (${exportMode.name.lowercase()})"),
            lastSavedAtEpochMillis = System.currentTimeMillis(),
        )
    }

    override suspend fun split(document: DocumentModel, request: SplitRequest, outputDirectory: File): List<File> = withContext(ioDispatcher) {
        outputDirectory.mkdirs()
        val groups = SplitPlanner.plan(document, request)
        groups.mapIndexed { index, group ->
            val extractedPages = group.map { document.pages[it] }.mapIndexed { pageIndex, page ->
                page.copy(
                    index = pageIndex,
                    label = "${pageIndex + 1}",
                    annotations = page.annotations.map { it.withPage(pageIndex) },
                    editObjects = page.editObjects.map { it.withPage(pageIndex) },
                )
            }
            val extracted = document.copy(
                pages = extractedPages,
                formDocument = FormDocumentModel(
                    document.formDocument.fields.filter { field -> group.contains(field.pageIndex) }
                        .map { field -> field.copy(pageIndex = group.indexOf(field.pageIndex).coerceAtLeast(0)) },
                ),
            )
            val file = File(outputDirectory, "${document.documentRef.displayName.removeSuffix(".pdf")}_part_${index + 1}.pdf")
            pdfWriteEngine.persist(extracted, file, AnnotationExportMode.Editable, SaveStrategy.ExportCopy)
            val securedExtracted = securityProcessor.applyWriteThroughSecurity(extracted, file, AnnotationExportMode.Editable)
            verifyFileIntegrity(file)
            ocrSessionStore.copyCompatibilityPayload(document.documentRef, file)
            persistSecurity(file.absolutePath, securedExtracted.security)
            file
        }
    }

    override fun createAutosaveTempFile(sessionId: String): File {
        val dir = File(context.cacheDir, "autosave").apply { mkdirs() }
        return File(dir, "$sessionId.json")
    }

    private fun repairStaleWorkingArtifacts() {
        listOf(File(context.filesDir, "working-documents"), File(context.cacheDir, "exports")).forEach { directory ->
            directory.mkdirs()
            directory.walkTopDown().filter { it.isFile }.forEach { file ->
                when {
                    file.name.endsWith(".saving.lock") -> file.delete()
                    file.name.endsWith(".tmp") -> {
                        val target = File(file.parentFile, file.name.removeSuffix(".tmp"))
                        if (!target.exists() && file.length() > 0L) file.copyTo(target, overwrite = true)
                        file.delete()
                    }
                }
            }
        }
    }

    private fun verifyFileIntegrity(file: File) {
        require(file.exists() && file.length() > 0L) { "Saved file is empty." }
        PDDocument.load(file).use { require(it.numberOfPages > 0) { "Saved file is unreadable." } }
    }

    private fun buildDocument(sessionId: String, displayName: String, password: String?, sourceType: DocumentSourceType, sourceKey: String, workingFile: File): DocumentModel {
        PDDocument.load(workingFile).use { source ->
            val pages = source.pages.mapIndexed { index, page ->
                PageModel(index = index, label = "${index + 1}", contentType = PageContentType.Pdf, sourceDocumentPath = workingFile.absolutePath, sourcePageIndex = index, widthPoints = page.mediaBox.width, heightPoints = page.mediaBox.height, rotationDegrees = page.rotation)
            }
            return DocumentModel(sessionId = sessionId, documentRef = PdfDocumentRef(Uri.fromFile(workingFile).toString(), displayName, password, sourceType, sourceKey, workingFile.absolutePath), pages = pages)
        }
    }

    private fun detectForms(sourcePath: String): FormDocumentModel {
        val file = File(sourcePath)
        if (!file.exists()) return FormDocumentModel()
        return PDDocument.load(file).use { document ->
            val acroForm = document.documentCatalog?.acroForm ?: return@use FormDocumentModel()
            FormDocumentModel(fields = acroForm.fieldTree.flatMap { field -> detectField(field, document) })
        }
    }

    private fun detectField(field: PDField, document: PDDocument): List<FormFieldModel> {
        val widgets = field.widgets
        if (widgets.isEmpty()) return emptyList()
        return widgets.mapNotNull { widget ->
            val page = widget.page ?: return@mapNotNull null
            val pageIndex = document.pages.indexOf(page).takeIf { it >= 0 } ?: return@mapNotNull null
            val rect = widget.rectangle ?: return@mapNotNull null
            val normalized = rectToNormalized(rect, page.mediaBox)
            val name = field.fullyQualifiedName ?: field.partialName ?: "field_$pageIndex"
            val label = field.partialName ?: name
            when (field) {
                is PDTextField -> FormFieldModel(name = name, label = label, pageIndex = pageIndex, bounds = normalized, type = if (field.isMultiline) FormFieldType.MultilineText else if (name.contains("date", true)) FormFieldType.Date else FormFieldType.Text, required = field.isRequired, value = FormFieldValue.Text(field.value ?: ""), placeholder = label, maxLength = field.maxLen.takeIf { it > 0 }, readOnly = field.isReadOnly)
                is PDCheckBox -> FormFieldModel(name, label, pageIndex, normalized, FormFieldType.Checkbox, field.isRequired, value = FormFieldValue.BooleanValue(field.isChecked), readOnly = field.isReadOnly, exportValue = field.onValue)
                is PDRadioButton -> FormFieldModel(name, label, pageIndex, normalized, FormFieldType.RadioGroup, field.isRequired, options = field.exportValues.map { FormFieldOption(it, it) }, value = FormFieldValue.Choice(field.value ?: ""), readOnly = field.isReadOnly)
                is PDChoice -> FormFieldModel(name, label, pageIndex, normalized, FormFieldType.Dropdown, field.isRequired, options = field.options.map { FormFieldOption(it, it) }, value = FormFieldValue.Choice(field.value.firstOrNull() ?: ""), readOnly = field.isReadOnly)
                is PDSignatureField -> {
                    val signature = field.signature
                    FormFieldModel(name = name, label = label, pageIndex = pageIndex, bounds = normalized, type = FormFieldType.Signature, value = FormFieldValue.SignatureValue(signerName = signature?.name ?: "", signedAtEpochMillis = signature?.signDate?.timeInMillis ?: 0L, status = if (signature != null) SignatureVerificationStatus.Verified else SignatureVerificationStatus.Unsigned), signatureStatus = if (signature != null) SignatureVerificationStatus.Verified else SignatureVerificationStatus.Unsigned, readOnly = field.isReadOnly)
                }
                else -> null
            }
        }
    }

    private fun rectToNormalized(rect: PDRectangle, mediaBox: PDRectangle): NormalizedRect {
        val left = rect.lowerLeftX / mediaBox.width
        val right = rect.upperRightX / mediaBox.width
        val top = 1f - (rect.upperRightY / mediaBox.height)
        val bottom = 1f - (rect.lowerLeftY / mediaBox.height)
        return NormalizedRect(left, top, right, bottom).normalized()
    }

    private fun applyFormData(document: DocumentModel, destination: File, exportMode: AnnotationExportMode) {
        if (!destination.exists()) return
        PDDocument.load(destination).use { pdDocument ->
            val acroForm: PDAcroForm = pdDocument.documentCatalog?.acroForm ?: return@use
            document.formDocument.fields.forEach { fieldModel ->
                val field = acroForm.getField(fieldModel.name) ?: return@forEach
                when (val value = fieldModel.value) {
                    is FormFieldValue.Text -> if (field is PDTextField) runCatching { field.setValue(value.text) }
                    is FormFieldValue.BooleanValue -> runCatching { if (field is PDCheckBox) if (value.checked) field.check() else field.unCheck() }
                    is FormFieldValue.Choice -> when (field) {
                        is PDRadioButton -> runCatching { field.setValue(value.selected) }
                        is PDChoice -> runCatching { field.setValue(value.selected) }
                    }
                    is FormFieldValue.SignatureValue -> if (!value.imagePath.isNullOrBlank()) placeSignatureAppearance(pdDocument, fieldModel, value.imagePath)
                }
            }
            if (exportMode == AnnotationExportMode.Flatten) runCatching { acroForm.flatten() }
            pdDocument.save(destination)
        }
    }

    private fun placeSignatureAppearance(document: PDDocument, fieldModel: FormFieldModel, imagePath: String) {
        val page = document.getPage(fieldModel.pageIndex.coerceIn(0, document.numberOfPages - 1))
        val mediaBox = page.mediaBox
        val rect = PDRectangle(fieldModel.bounds.left * mediaBox.width, mediaBox.height - (fieldModel.bounds.bottom * mediaBox.height), fieldModel.bounds.width * mediaBox.width, fieldModel.bounds.height * mediaBox.height)
        val bitmap = BitmapFactory.decodeFile(imagePath) ?: return
        PDPageContentStream(document, page, PDPageContentStream.AppendMode.APPEND, true, true).use { contentStream ->
            val image = LosslessFactory.createFromImage(document, bitmap)
            contentStream.drawImage(image, rect.lowerLeftX, rect.lowerLeftY - rect.height, rect.width, rect.height)
        }
    }

    private suspend fun loadSecurity(documentKey: String): SecurityDocumentModel {
        return documentSecurityDao.get(documentKey)?.let { json.decodeFromString(SecurityDocumentModel.serializer(), it.payloadJson) } ?: SecurityDocumentModel()
    }

    private suspend fun persistSecurity(documentKey: String, security: SecurityDocumentModel) {
        documentSecurityDao.upsert(DocumentSecurityEntity(documentKey = documentKey, payloadJson = json.encodeToString(SecurityDocumentModel.serializer(), security), updatedAtEpochMillis = System.currentTimeMillis()))
    }

    private fun applySecurity(security: SecurityDocumentModel, destination: File) {
        if (!destination.exists()) return
        PDDocument.load(destination).use { document ->
            if (security.metadataScrub.enabled) scrubMetadata(document, security.metadataScrub)
            if (security.watermark.enabled && security.watermark.text.isNotBlank()) applyWatermark(document, security.watermark)
            val redactions = security.redactionWorkflow.marks.filter { it.status == RedactionStatus.Applied }
            if (redactions.isNotEmpty()) applyRedactions(document, redactions)
            if (security.passwordProtection.enabled && (security.passwordProtection.ownerPassword.isNotBlank() || security.passwordProtection.userPassword.isNotBlank())) applyPasswordProtection(document, security.passwordProtection, security.permissions)
            document.save(destination)
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

    private fun applyPasswordProtection(document: PDDocument, protection: PasswordProtectionModel, permissions: com.aymanelbanhawy.editor.core.security.DocumentPermissionModel) {
        val accessPermission = AccessPermission().apply {
            setCanPrint(permissions.allowPrint)
            setCanExtractContent(permissions.allowCopy)
            setCanModify(permissions.allowExport)
            setCanModifyAnnotations(true)
        }
        val policy = StandardProtectionPolicy(protection.ownerPassword.ifBlank { protection.userPassword.ifBlank { UUID.randomUUID().toString() } }, protection.userPassword, accessPermission)
        policy.encryptionKeyLength = 256
        document.protect(policy)
    }

    private fun applyWatermark(document: PDDocument, watermark: WatermarkModel) {
        val color = android.graphics.Color.parseColor(watermark.textColorHex)
        val red = android.graphics.Color.red(color) / 255f
        val green = android.graphics.Color.green(color) / 255f
        val blue = android.graphics.Color.blue(color) / 255f
        repeat(document.numberOfPages) { pageIndex ->
            val page = document.getPage(pageIndex)
            val mediaBox = page.mediaBox
            PDPageContentStream(document, page, PDPageContentStream.AppendMode.APPEND, true, true).use { content ->
                content.setNonStrokingColor(red, green, blue)
                content.beginText()
                content.setFont(PDType1Font.HELVETICA_BOLD, watermark.fontSize)
                content.setTextMatrix(com.tom_roush.pdfbox.util.Matrix.getRotateInstance(Math.toRadians(watermark.rotationDegrees.toDouble()), mediaBox.width / 4f, mediaBox.height / 2f))
                content.showText(watermark.text)
                content.endText()
            }
        }
    }

    private fun applyRedactions(document: PDDocument, marks: List<RedactionMarkModel>) {
        marks.groupBy { it.pageIndex }.forEach { (pageIndex, pageMarks) ->
            if (pageIndex !in 0 until document.numberOfPages) return@forEach
            val page = document.getPage(pageIndex)
            val mediaBox = page.mediaBox
            PDPageContentStream(document, page, PDPageContentStream.AppendMode.APPEND, true, true).use { content ->
                pageMarks.forEach { mark ->
                    val rect = PDRectangle(mark.bounds.left * mediaBox.width, mediaBox.height - (mark.bounds.bottom * mediaBox.height), mark.bounds.width * mediaBox.width, mark.bounds.height * mediaBox.height)
                    content.setNonStrokingColor(0f, 0f, 0f)
                    content.addRect(rect.lowerLeftX, rect.lowerLeftY - rect.height, rect.width, rect.height)
                    content.fill()
                }
            }
        }
    }

    private fun createWorkingFile(displayName: String): File {
        val dir = File(context.filesDir, "working-documents").apply { mkdirs() }
        val sanitized = displayName.ifBlank { "document.pdf" }
        return File(dir, "${UUID.randomUUID()}_$sanitized")
    }

    private fun copyToWorkingFile(displayName: String, inputStream: InputStream): File {
        val file = createWorkingFile(displayName)
        inputStream.use { input -> file.outputStream().use { input.copyTo(it) } }
        return file
    }

    private fun stableDraftFile(sessionId: String): File {
        val dir = File(context.filesDir, "drafts").apply { mkdirs() }
        return File(dir, "$sessionId.json")
    }
}







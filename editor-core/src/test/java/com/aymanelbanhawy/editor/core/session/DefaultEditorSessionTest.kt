package com.aymanelbanhawy.editor.core.session

import com.aymanelbanhawy.editor.core.command.AddAnnotationCommand
import com.aymanelbanhawy.editor.core.command.UpdateAnnotationCommand
import com.aymanelbanhawy.editor.core.forms.DigitalSignatureMetadata
import com.aymanelbanhawy.editor.core.forms.FormDocumentModel
import com.aymanelbanhawy.editor.core.forms.FormFieldModel
import com.aymanelbanhawy.editor.core.forms.FormFieldType
import com.aymanelbanhawy.editor.core.forms.FormFieldValue
import com.aymanelbanhawy.editor.core.forms.SignatureVerificationStatus
import com.aymanelbanhawy.editor.core.model.AnnotationCommentThread
import com.aymanelbanhawy.editor.core.model.AnnotationExportMode
import com.aymanelbanhawy.editor.core.model.AnnotationModel
import com.aymanelbanhawy.editor.core.model.AnnotationType
import com.aymanelbanhawy.editor.core.model.DocumentModel
import com.aymanelbanhawy.editor.core.model.DocumentSourceType
import com.aymanelbanhawy.editor.core.model.DraftPayload
import com.aymanelbanhawy.editor.core.model.EditorAction
import com.aymanelbanhawy.editor.core.model.NormalizedRect
import com.aymanelbanhawy.editor.core.model.OpenDocumentRequest
import com.aymanelbanhawy.editor.core.model.PageModel
import com.aymanelbanhawy.editor.core.model.PdfDocumentRef
import com.aymanelbanhawy.editor.core.repository.DocumentRepository
import com.aymanelbanhawy.editor.core.repository.DraftRestoreResult
import com.google.common.truth.Truth.assertThat
import java.io.File
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultEditorSessionTest {

    @Test
    fun executeUpdateUndoRedoAndSave_updatesSessionState() = runTest {
        val repository = FakeDocumentRepository()
        val session = DefaultEditorSession(repository, NoOpAutosaveScheduler(), StandardTestDispatcher(testScheduler))
        session.openDocument(OpenDocumentRequest.FromAsset(assetName = "sample.pdf", displayName = "sample.pdf"))

        val annotation = annotation(id = "ann-1")
        session.execute(AddAnnotationCommand(0, annotation))
        assertThat(session.state.value.document?.pages?.first()?.annotations).contains(annotation)
        testScheduler.advanceUntilIdle()
        assertThat(repository.persistedDrafts).hasSize(1)

        val updated = annotation.copy(text = "Updated", commentThread = annotation.commentThread.copy(modifiedAtEpochMillis = 200L))
        session.execute(UpdateAnnotationCommand(0, annotation, updated))
        assertThat(session.state.value.document?.pages?.first()?.annotations?.single()?.text).isEqualTo("Updated")

        session.undo()
        assertThat(session.state.value.document?.pages?.first()?.annotations?.single()?.text).isEqualTo("Review")

        session.redo()
        assertThat(session.state.value.document?.pages?.first()?.annotations?.single()?.text).isEqualTo("Updated")

        session.manualSave(AnnotationExportMode.Flatten)
        testScheduler.advanceUntilIdle()
        assertThat(repository.savedModes).containsExactly(AnnotationExportMode.Flatten)
    }

    @Test
    fun shareAction_emitsShareEventForOpenDocument() = runTest {
        val repository = FakeDocumentRepository()
        val session = DefaultEditorSession(repository, NoOpAutosaveScheduler(), StandardTestDispatcher(testScheduler))
        session.openDocument(OpenDocumentRequest.FromAsset(assetName = "sample.pdf", displayName = "sample.pdf"))

        val event = async { session.events.first() }
        session.onActionSelected(EditorAction.Share)

        val shareEvent = event.await() as EditorSessionEvent.ShareDocument
        assertThat(shareEvent.document.documentRef.displayName).isEqualTo("sample.pdf")
    }

    @Test
    fun execute_invalidatesExistingSignatureStateAfterDocumentEdit() = runTest {
        val repository = FakeDocumentRepository(withSignedField = true)
        val session = DefaultEditorSession(repository, NoOpAutosaveScheduler(), StandardTestDispatcher(testScheduler))
        session.openDocument(OpenDocumentRequest.FromAsset(assetName = "sample.pdf", displayName = "sample.pdf"))

        session.execute(AddAnnotationCommand(0, annotation(id = "ann-signed")))

        val signatureField = session.state.value.document?.formDocument?.fields?.single()
        val signatureValue = signatureField?.value as FormFieldValue.SignatureValue
        assertThat(signatureValue.status).isEqualTo(SignatureVerificationStatus.Invalid)
        assertThat(signatureField.signatureStatus).isEqualTo(SignatureVerificationStatus.Invalid)
        assertThat(signatureValue.digitalSignature?.verificationMessage).isEqualTo("Document modified after signing.")
    }

    private class FakeDocumentRepository(
        private val restoreResult: DraftRestoreResult? = null,
        private val withSignedField: Boolean = false,
    ) : DocumentRepository {
        val persistedDrafts = mutableListOf<DraftPayload>()
        val savedModes = mutableListOf<AnnotationExportMode>()

        override suspend fun open(request: OpenDocumentRequest): DocumentModel = document("session-1")
        override suspend fun importPages(requests: List<OpenDocumentRequest>): List<PageModel> = emptyList()
        override suspend fun persistDraft(payload: DraftPayload, autosave: Boolean) { persistedDrafts += payload }
        override suspend fun restoreDraft(sourceKey: String): DraftRestoreResult? = restoreResult
        override suspend fun clearDraft(sourceKey: String) = Unit
        override suspend fun save(document: DocumentModel, exportMode: AnnotationExportMode): DocumentModel {
            savedModes += exportMode
            return document.copy(dirtyState = document.dirtyState.copy(isDirty = false, saveMessage = "Saved"))
        }
        override suspend fun saveAs(document: DocumentModel, destination: File, exportMode: AnnotationExportMode): DocumentModel = document
        override suspend fun split(document: DocumentModel, request: com.aymanelbanhawy.editor.core.organize.SplitRequest, outputDirectory: File): List<File> = emptyList()
        override fun createAutosaveTempFile(sessionId: String): File = File.createTempFile(sessionId, ".json")

        private fun document(sessionId: String): DocumentModel {
            return DocumentModel(
                sessionId = sessionId,
                documentRef = PdfDocumentRef(
                    uriString = "file:///sample.pdf",
                    displayName = "sample.pdf",
                    sourceType = DocumentSourceType.Asset,
                    sourceKey = "asset://sample.pdf",
                    workingCopyPath = "C:/sample.pdf",
                ),
                pages = listOf(PageModel(index = 0, label = "1")),
                formDocument = if (withSignedField) {
                    FormDocumentModel(
                        fields = listOf(
                            FormFieldModel(
                                name = "sig-1",
                                label = "Signature",
                                pageIndex = 0,
                                bounds = NormalizedRect(0.1f, 0.1f, 0.4f, 0.2f),
                                type = FormFieldType.Signature,
                                value = FormFieldValue.SignatureValue(
                                    signerName = "Signer",
                                    status = SignatureVerificationStatus.Verified,
                                    digitalSignature = DigitalSignatureMetadata(
                                        fieldName = "sig-1",
                                        verificationStatus = SignatureVerificationStatus.Verified,
                                        verificationMessage = "The digital signature is valid.",
                                    ),
                                ),
                                signatureStatus = SignatureVerificationStatus.Verified,
                            ),
                        ),
                    )
                } else {
                    FormDocumentModel()
                },
            )
        }
    }

    private fun annotation(id: String): AnnotationModel {
        return AnnotationModel(
            id = id,
            pageIndex = 0,
            type = AnnotationType.Highlight,
            bounds = NormalizedRect(0.1f, 0.1f, 0.3f, 0.2f),
            strokeColorHex = "#F9AB00",
            fillColorHex = "#55F9AB00",
            opacity = 0.35f,
            text = "Review",
            commentThread = AnnotationCommentThread(
                author = "Ayman",
                createdAtEpochMillis = 100L,
                modifiedAtEpochMillis = 100L,
                subject = "Review",
            ),
        )
    }
}

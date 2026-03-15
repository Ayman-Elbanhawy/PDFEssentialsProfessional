package com.aymanelbanhawy.editor.core.session

import com.aymanelbanhawy.editor.core.model.AnnotationCommentThread
import com.aymanelbanhawy.editor.core.model.AnnotationExportMode
import com.aymanelbanhawy.editor.core.model.AnnotationModel
import com.aymanelbanhawy.editor.core.model.AnnotationType
import com.aymanelbanhawy.editor.core.model.DocumentModel
import com.aymanelbanhawy.editor.core.model.DocumentSourceType
import com.aymanelbanhawy.editor.core.model.DraftPayload
import com.aymanelbanhawy.editor.core.model.NormalizedRect
import com.aymanelbanhawy.editor.core.model.OpenDocumentRequest
import com.aymanelbanhawy.editor.core.model.PageModel
import com.aymanelbanhawy.editor.core.model.PdfDocumentRef
import com.aymanelbanhawy.editor.core.model.SelectionModel
import com.aymanelbanhawy.editor.core.model.UndoRedoState
import com.aymanelbanhawy.editor.core.repository.DocumentRepository
import com.aymanelbanhawy.editor.core.repository.DraftRestoreResult
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class DraftRestoreBehaviorTest {

    @Test
    fun openDocument_restoresCrashSafeDraftWhenAvailable() = runTest {
        val repository = FakeDocumentRepository(restoreDraft = restoreDraft())
        val session = DefaultEditorSession(repository, NoOpAutosaveScheduler(), StandardTestDispatcher(testScheduler))

        session.openDocument(OpenDocumentRequest.FromAsset(assetName = "sample.pdf", displayName = "sample.pdf"))

        assertThat(session.state.value.document?.restoredFromDraft).isTrue()
        assertThat(session.state.value.document?.pages?.first()?.annotations).hasSize(1)
        assertThat(session.state.value.undoRedoState.undoCount).isEqualTo(1)
    }

    private fun restoreDraft(): DraftRestoreResult {
        val annotation = AnnotationModel(
            id = "annotation-1",
            pageIndex = 0,
            type = AnnotationType.StickyNote,
            bounds = NormalizedRect(0.2f, 0.3f, 0.28f, 0.38f),
            strokeColorHex = "#F9AB00",
            fillColorHex = "#FFFFF59D",
            text = "Recovered draft",
            icon = "comment",
            commentThread = AnnotationCommentThread(
                author = "Ayman",
                createdAtEpochMillis = 100L,
                modifiedAtEpochMillis = 100L,
                subject = "Recovered draft",
            ),
        )
        val document = DocumentModel(
            sessionId = "draft-session",
            documentRef = PdfDocumentRef(
                uriString = "file:///sample.pdf",
                displayName = "sample.pdf",
                sourceType = DocumentSourceType.Asset,
                sourceKey = "asset://sample.pdf",
                workingCopyPath = "C:/sample.pdf",
            ),
            pages = listOf(PageModel(index = 0, label = "1", annotations = listOf(annotation))),
            restoredFromDraft = true,
        )
        return DraftRestoreResult(
            document = document,
            selection = SelectionModel(selectedPageIndex = 0, selectedAnnotationIds = setOf(annotation.id)),
            undoRedoState = UndoRedoState(canUndo = true, undoCount = 1, lastCommandName = "Add annotation"),
        )
    }

    private class FakeDocumentRepository(
        private val restoreDraft: DraftRestoreResult?,
    ) : DocumentRepository {
        override suspend fun open(request: OpenDocumentRequest): DocumentModel {
            return DocumentModel(
                sessionId = "open-session",
                documentRef = PdfDocumentRef(
                    uriString = "file:///sample.pdf",
                    displayName = "sample.pdf",
                    sourceType = DocumentSourceType.Asset,
                    sourceKey = "asset://sample.pdf",
                    workingCopyPath = "C:/sample.pdf",
                ),
                pages = listOf(PageModel(index = 0, label = "1")),
            )
        }

        override suspend fun importPages(requests: List<OpenDocumentRequest>): List<PageModel> = emptyList()
        override suspend fun persistDraft(payload: DraftPayload, autosave: Boolean) = Unit
        override suspend fun restoreDraft(sourceKey: String): DraftRestoreResult? = restoreDraft
        override suspend fun clearDraft(sourceKey: String) = Unit
        override suspend fun save(document: DocumentModel, exportMode: AnnotationExportMode): DocumentModel = document
        override suspend fun saveAs(document: DocumentModel, destination: File, exportMode: AnnotationExportMode): DocumentModel = document
        override suspend fun split(document: DocumentModel, request: com.aymanelbanhawy.editor.core.organize.SplitRequest, outputDirectory: File): List<File> = emptyList()
        override fun createAutosaveTempFile(sessionId: String): File = File.createTempFile(sessionId, ".json")
    }
}

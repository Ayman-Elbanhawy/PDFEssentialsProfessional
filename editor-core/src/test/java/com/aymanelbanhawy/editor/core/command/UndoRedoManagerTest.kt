package com.aymanelbanhawy.editor.core.command

import com.aymanelbanhawy.editor.core.model.AnnotationCommentThread
import com.aymanelbanhawy.editor.core.model.AnnotationModel
import com.aymanelbanhawy.editor.core.model.AnnotationType
import com.aymanelbanhawy.editor.core.model.DocumentModel
import com.aymanelbanhawy.editor.core.model.DocumentSourceType
import com.aymanelbanhawy.editor.core.model.NormalizedRect
import com.aymanelbanhawy.editor.core.model.PageModel
import com.aymanelbanhawy.editor.core.model.PdfDocumentRef
import com.aymanelbanhawy.editor.core.model.SelectionModel
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class UndoRedoManagerTest {

    @Test
    fun executeUndoRedo_roundTripsDocumentChanges() {
        val manager = UndoRedoManager()
        val initial = EditableDocumentState(document(), SelectionModel())
        val annotation = annotation(id = "ann-1")

        val executed = manager.execute(AddAnnotationCommand(0, annotation), initial)
        assertThat(executed.document.pages[0].annotations).contains(annotation)
        assertThat(manager.snapshot().canUndo).isTrue()

        val undone = manager.undo(executed)
        assertThat(undone.document.pages[0].annotations).isEmpty()
        assertThat(manager.snapshot().canRedo).isTrue()

        val redone = manager.redo(undone)
        assertThat(redone.document.pages[0].annotations).contains(annotation)
    }

    @Test
    fun reorderCommand_renumbersPagesAndAnnotations() {
        val manager = UndoRedoManager()
        val initial = EditableDocumentState(
            document(
                pages = listOf(
                    PageModel(index = 0, label = "1", annotations = listOf(annotation(id = "ann-1", pageIndex = 0))),
                    PageModel(index = 1, label = "2"),
                    PageModel(index = 2, label = "3"),
                ),
            ),
            SelectionModel(),
        )

        val updated = manager.execute(ReorderPagesCommand(0, 2), initial)

        assertThat(updated.document.pages.map { it.index }).containsExactly(0, 1, 2).inOrder()
        assertThat(updated.document.pages.last().annotations.single().pageIndex).isEqualTo(2)
        assertThat(updated.selection.selectedPageIndex).isEqualTo(2)
    }

    private fun document(pages: List<PageModel> = listOf(PageModel(index = 0, label = "1"))): DocumentModel {
        return DocumentModel(
            sessionId = "session-1",
            documentRef = PdfDocumentRef(
                uriString = "file:///doc.pdf",
                displayName = "doc.pdf",
                sourceType = DocumentSourceType.File,
                sourceKey = "C:/doc.pdf",
                workingCopyPath = "C:/doc.pdf",
            ),
            pages = pages,
        )
    }

    private fun annotation(id: String, pageIndex: Int = 0): AnnotationModel {
        val now = 100L
        return AnnotationModel(
            id = id,
            pageIndex = pageIndex,
            type = AnnotationType.Highlight,
            bounds = NormalizedRect(0.1f, 0.2f, 0.3f, 0.25f),
            strokeColorHex = "#FFAA00",
            fillColorHex = "#55FFAA00",
            opacity = 0.4f,
            text = "Review",
            commentThread = AnnotationCommentThread(
                author = "Ayman",
                createdAtEpochMillis = now,
                modifiedAtEpochMillis = now,
                subject = "Review",
            ),
        )
    }
}

package com.aymanelbanhawy.editor.core.command

import com.aymanelbanhawy.editor.core.model.DocumentModel
import com.aymanelbanhawy.editor.core.model.DocumentSourceType
import com.aymanelbanhawy.editor.core.model.PageContentType
import com.aymanelbanhawy.editor.core.model.PageModel
import com.aymanelbanhawy.editor.core.model.PdfDocumentRef
import com.aymanelbanhawy.editor.core.model.SelectionModel
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PageOperationCommandTest {

    @Test
    fun deletePages_removesAllSelectedPages() {
        val state = EditableDocumentState(document(pageCount = 4), SelectionModel(selectedPageIndex = 2))

        val updated = DeletePagesCommand(setOf(1, 2)).apply(state)

        assertThat(updated.document.pages).hasSize(2)
        assertThat(updated.document.pages.map { it.label }).containsExactly("1", "2").inOrder()
    }

    @Test
    fun duplicatePages_insertsCopiesAfterSources() {
        val state = EditableDocumentState(document(pageCount = 3), SelectionModel())

        val updated = DuplicatePagesCommand(listOf(0, 2)).apply(state)

        assertThat(updated.document.pages).hasSize(5)
        assertThat(updated.selection.selectedPageIndex).isEqualTo(4)
    }

    @Test
    fun insertBlankAndImagePages_marksContentTypes() {
        val state = EditableDocumentState(document(pageCount = 2), SelectionModel())

        val blank = InsertBlankPageCommand(1).apply(state)
        val image = InsertImagePageCommand(2, "C:/tmp/page.png", 300f, 200f).apply(blank)

        assertThat(blank.document.pages[1].contentType).isEqualTo(PageContentType.Blank)
        assertThat(image.document.pages[2].contentType).isEqualTo(PageContentType.Image)
    }

    @Test
    fun mergePages_insertsImportedPagesAtTargetIndex() {
        val state = EditableDocumentState(document(pageCount = 2), SelectionModel())
        val imported = listOf(
            PageModel(index = 0, label = "1", sourceDocumentPath = "C:/merged.pdf", sourcePageIndex = 0),
            PageModel(index = 1, label = "2", sourceDocumentPath = "C:/merged.pdf", sourcePageIndex = 1),
        )

        val updated = MergePagesCommand(1, imported).apply(state)

        assertThat(updated.document.pages).hasSize(4)
        assertThat(updated.document.pages[1].sourceDocumentPath).isEqualTo("C:/merged.pdf")
        assertThat(updated.document.pages[2].sourceDocumentPath).isEqualTo("C:/merged.pdf")
    }

    private fun document(pageCount: Int): DocumentModel {
        return DocumentModel(
            sessionId = "session-1",
            documentRef = PdfDocumentRef(
                uriString = "file:///doc.pdf",
                displayName = "doc.pdf",
                sourceType = DocumentSourceType.File,
                sourceKey = "C:/doc.pdf",
                workingCopyPath = "C:/doc.pdf",
            ),
            pages = List(pageCount) { index ->
                PageModel(
                    index = index,
                    label = "${index + 1}",
                    sourceDocumentPath = "C:/doc.pdf",
                    sourcePageIndex = index,
                )
            },
        )
    }
}

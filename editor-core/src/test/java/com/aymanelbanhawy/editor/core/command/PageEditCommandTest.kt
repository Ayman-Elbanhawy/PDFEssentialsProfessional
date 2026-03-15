package com.aymanelbanhawy.editor.core.command

import com.aymanelbanhawy.editor.core.model.DocumentModel
import com.aymanelbanhawy.editor.core.model.DocumentSourceType
import com.aymanelbanhawy.editor.core.model.FontFamilyToken
import com.aymanelbanhawy.editor.core.model.ImageEditModel
import com.aymanelbanhawy.editor.core.model.NormalizedRect
import com.aymanelbanhawy.editor.core.model.PageModel
import com.aymanelbanhawy.editor.core.model.PdfDocumentRef
import com.aymanelbanhawy.editor.core.model.SelectionModel
import com.aymanelbanhawy.editor.core.model.TextAlignment
import com.aymanelbanhawy.editor.core.model.TextBoxEditModel
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PageEditCommandTest {

    @Test
    fun addUpdateDeleteEdit_roundTrips() {
        val text = TextBoxEditModel(
            id = "text-1",
            pageIndex = 0,
            bounds = NormalizedRect(0.1f, 0.1f, 0.4f, 0.2f),
            text = "Hello",
        )
        val base = EditableDocumentState(document(), SelectionModel())
        val added = AddPageEditCommand(0, text).apply(base)
        assertThat(added.document.pages.first().editObjects).containsExactly(text)
        assertThat(added.selection.selectedEditId).isEqualTo("text-1")

        val updatedText = text.withTypography(
            fontFamily = FontFamilyToken.Serif,
            fontSizeSp = 20f,
            textColorHex = "#B3261E",
            alignment = TextAlignment.Center,
            lineSpacingMultiplier = 1.5f,
        ).withText("Updated")
        val updated = UpdatePageEditCommand(text, updatedText).apply(added)
        assertThat(updated.document.pages.first().editObjects.single()).isEqualTo(updatedText)

        val deleted = DeletePageEditCommand(0, updatedText).apply(updated)
        assertThat(deleted.document.pages.first().editObjects).isEmpty()

        val restored = DeletePageEditCommand(0, updatedText).revert(deleted)
        assertThat(restored.document.pages.first().editObjects.single()).isEqualTo(updatedText)
    }

    @Test
    fun replaceImageCommand_swapsImageAsset() {
        val image = ImageEditModel(
            id = "image-1",
            pageIndex = 0,
            bounds = NormalizedRect(0.2f, 0.2f, 0.6f, 0.5f),
            imagePath = "C:/before.png",
            label = "before",
        )
        val base = EditableDocumentState(document(pages = listOf(PageModel(index = 0, label = "1", editObjects = listOf(image)))), SelectionModel(selectedEditId = image.id))
        val updated = image.replaced("C:/after.png", "after")
        val result = ReplaceImageAssetCommand(image, updated).apply(base)

        assertThat((result.document.pages.first().editObjects.single() as ImageEditModel).imagePath).isEqualTo("C:/after.png")
        assertThat((result.document.pages.first().editObjects.single() as ImageEditModel).label).isEqualTo("after")
    }

    private fun document(pages: List<PageModel> = listOf(PageModel(index = 0, label = "1"))): DocumentModel {
        return DocumentModel(
            sessionId = "session-1",
            documentRef = PdfDocumentRef(
                uriString = "file:///sample.pdf",
                displayName = "sample.pdf",
                sourceType = DocumentSourceType.File,
                sourceKey = "C:/sample.pdf",
                workingCopyPath = "C:/sample.pdf",
            ),
            pages = pages,
        )
    }
}

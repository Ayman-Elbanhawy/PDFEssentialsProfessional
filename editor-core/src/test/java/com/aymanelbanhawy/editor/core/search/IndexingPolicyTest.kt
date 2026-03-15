package com.aymanelbanhawy.editor.core.search

import com.aymanelbanhawy.editor.core.model.DocumentModel
import com.aymanelbanhawy.editor.core.model.DocumentSourceType
import com.aymanelbanhawy.editor.core.model.PageModel
import com.aymanelbanhawy.editor.core.model.PdfDocumentRef
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class IndexingPolicyTest {
    @Test
    fun largeDocumentsAreScheduledInBackground() {
        val policy = IndexingPolicy(backgroundThreshold = 10)
        assertThat(policy.shouldIndexInBackground(documentWithPages(12))).isTrue()
        assertThat(policy.shouldIndexInBackground(documentWithPages(3))).isFalse()
    }

    private fun documentWithPages(pageCount: Int): DocumentModel {
        return DocumentModel(
            sessionId = "session",
            documentRef = PdfDocumentRef(
                uriString = "file:///tmp/document.pdf",
                displayName = "document.pdf",
                sourceType = DocumentSourceType.File,
                sourceKey = "/tmp/document.pdf",
                workingCopyPath = "/tmp/document.pdf",
            ),
            pages = List(pageCount) { PageModel(index = it, label = "${it + 1}") },
        )
    }
}

package com.aymanelbanhawy.aiassistant.core

import com.aymanelbanhawy.editor.core.model.NormalizedRect
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MultiDocumentGroundingTest {
    @Test
    fun formatter_includesDocumentLabelsForCrossDocumentCitations() {
        val result = AssistantResult(
            task = AssistantTaskType.AskWorkspace,
            headline = "Workspace Answer",
            body = "Two contracts disagree on the renewal term.",
            citations = listOf(
                AssistantCitation(
                    id = "c1",
                    title = "Evidence 1",
                    anchor = CitationAnchor(
                        documentKey = "contract-a",
                        documentTitle = "Master Services Agreement.pdf",
                        pageIndex = 2,
                        bounds = NormalizedRect(0.1f, 0.2f, 0.4f, 0.5f),
                        quote = "Renewal term is 12 months.",
                    ),
                    confidence = 0.92f,
                ),
                AssistantCitation(
                    id = "c2",
                    title = "Evidence 2",
                    anchor = CitationAnchor(
                        documentKey = "contract-b",
                        documentTitle = "Order Form.pdf",
                        pageIndex = 0,
                        bounds = NormalizedRect(0.5f, 0.2f, 0.8f, 0.35f),
                        quote = "Renewal term is 24 months.",
                    ),
                    confidence = 0.87f,
                ),
            ),
            generatedAtEpochMillis = 1L,
        )

        val formatted = AssistantResultFormatter.format(result)

        assertThat(formatted).contains("Master Services Agreement.pdf")
        assertThat(formatted).contains("Order Form.pdf")
        assertThat(formatted).contains("Page 3")
        assertThat(formatted).contains("Page 1")
    }

    @Test
    fun sourceLabel_usesDocumentTitleAndPage() {
        val anchor = CitationAnchor(
            documentKey = "policy",
            documentTitle = "Security Policy.pdf",
            pageIndex = 4,
            bounds = NormalizedRect(0f, 0f, 1f, 1f),
            quote = "Sensitive data must remain encrypted.",
        )

        assertThat(anchor.sourceLabel).isEqualTo("Security Policy.pdf • Page 5")
    }
}

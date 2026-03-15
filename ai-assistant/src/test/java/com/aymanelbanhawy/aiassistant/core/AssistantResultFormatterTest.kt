package com.aymanelbanhawy.aiassistant.core

import com.aymanelbanhawy.editor.core.model.NormalizedRect
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AssistantResultFormatterTest {
    @Test
    fun format_includesCitationAnchors() {
        val result = AssistantResult(
            task = AssistantTaskType.AskPdf,
            headline = "Ask PDF",
            body = "Grounded answer",
            citations = listOf(
                AssistantCitation(
                    id = "c1",
                    title = "Reference",
                    anchor = CitationAnchor(
                        pageIndex = 2,
                        bounds = NormalizedRect(0.1f, 0.2f, 0.4f, 0.5f),
                        quote = "Quoted text",
                    ),
                    confidence = 0.9f,
                ),
            ),
            generatedAtEpochMillis = 1L,
        )

        val formatted = AssistantResultFormatter.format(result)

        assertThat(formatted).contains("Ask PDF")
        assertThat(formatted).contains("Citations:")
        assertThat(formatted).contains("Quoted text")
    }

    @Test
    fun citationAnchor_usesPageAndRegionLabels() {
        val anchor = AssistantResultFormatter.citationAnchor(
            documentKey = "doc-1",
            documentTitle = "Example.pdf",
            pageIndex = 0,
            bounds = NormalizedRect(0.15f, 0.25f, 0.55f, 0.7f),
            quote = "Anchor quote",
        )

        assertThat(anchor.pageLabel).isEqualTo("Page 1")
        assertThat(anchor.regionLabel).contains("15%")
        assertThat(anchor.regionLabel).contains("70%")
    }
}



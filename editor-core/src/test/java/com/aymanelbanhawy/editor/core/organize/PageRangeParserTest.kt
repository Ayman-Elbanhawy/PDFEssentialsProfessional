package com.aymanelbanhawy.editor.core.organize

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PageRangeParserTest {

    @Test
    fun expand_parsesMultipleRangesAndSingles() {
        val expanded = PageRangeParser.expand("1-3,5,7-8", 10)

        assertThat(expanded).containsExactly(0, 1, 2, 4, 6, 7).inOrder()
    }

    @Test
    fun parse_rejectsOutOfBoundsPages() {
        runCatching { PageRangeParser.expand("1,9", 4) }
            .onSuccess { throw AssertionError("Expected parser to reject out-of-bounds page") }
            .onFailure { error -> assertThat(error).hasMessageThat().contains("out of bounds") }
    }
}

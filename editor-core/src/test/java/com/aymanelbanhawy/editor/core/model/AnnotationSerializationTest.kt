package com.aymanelbanhawy.editor.core.model

import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.Json
import org.junit.Test

class AnnotationSerializationTest {

    @Test
    fun annotation_roundTripsThroughSerialization() {
        val annotation = AnnotationModel(
            id = "annotation-1",
            pageIndex = 0,
            type = AnnotationType.FreehandInk,
            bounds = NormalizedRect(0.1f, 0.2f, 0.4f, 0.5f),
            points = listOf(NormalizedPoint(0.1f, 0.2f), NormalizedPoint(0.3f, 0.4f)),
            strokeColorHex = "#1967D2",
            fillColorHex = null,
            text = "Ink note",
            commentThread = AnnotationCommentThread(
                author = "Ayman",
                createdAtEpochMillis = 1L,
                modifiedAtEpochMillis = 2L,
                subject = "Ink note",
                replies = listOf(AnnotationReply(id = "reply-1", author = "Reviewer", message = "Looks good", createdAtEpochMillis = 3L)),
            ),
        )
        val json = Json { encodeDefaults = true }

        val encoded = json.encodeToString(AnnotationModel.serializer(), annotation)
        val decoded = json.decodeFromString(AnnotationModel.serializer(), encoded)

        assertThat(decoded).isEqualTo(annotation)
    }
}

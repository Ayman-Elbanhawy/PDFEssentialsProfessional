package com.aymanelbanhawy.editor.core.write

import com.aymanelbanhawy.editor.core.model.FontFamilyToken
import com.aymanelbanhawy.editor.core.model.ImageEditModel
import com.aymanelbanhawy.editor.core.model.NormalizedRect
import com.aymanelbanhawy.editor.core.model.PageEditModel
import com.aymanelbanhawy.editor.core.model.TextAlignment
import com.aymanelbanhawy.editor.core.model.TextBoxEditModel
import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.junit.Test

class PageEditSerializationTest {
    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
        classDiscriminator = "_type"
    }

    @Test
    fun pageEdits_roundTrip() {
        val edits = listOf<PageEditModel>(
            TextBoxEditModel(
                id = "text-1",
                pageIndex = 0,
                bounds = NormalizedRect(0.1f, 0.1f, 0.4f, 0.2f),
                text = "Hello",
                fontFamily = FontFamilyToken.Monospace,
                fontSizeSp = 18f,
                textColorHex = "#0B57D0",
                alignment = TextAlignment.End,
                lineSpacingMultiplier = 1.4f,
            ),
            ImageEditModel(
                id = "image-1",
                pageIndex = 0,
                bounds = NormalizedRect(0.2f, 0.3f, 0.7f, 0.8f),
                imagePath = "C:/image.png",
                label = "Hero",
                rotationDegrees = 15f,
                opacity = 0.85f,
            ),
        )

        val encoded = json.encodeToString(ListSerializer(PageEditModel.serializer()), edits)
        val decoded = json.decodeFromString(ListSerializer(PageEditModel.serializer()), encoded)

        assertThat(decoded).isEqualTo(edits)
    }
}

package com.aymanelbanhawy.editor.core.forms

import com.aymanelbanhawy.editor.core.model.NormalizedPoint
import com.aymanelbanhawy.editor.core.model.NormalizedRect
import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.Json
import org.junit.Test

class FormSerializationTest {

    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
        classDiscriminator = "_type"
    }

    @Test
    fun formDocument_roundTripsWithSignatureField() {
        val document = FormDocumentModel(
            fields = listOf(
                FormFieldModel(
                    name = "signature_1",
                    label = "Signature",
                    pageIndex = 1,
                    bounds = NormalizedRect(0.2f, 0.4f, 0.6f, 0.5f),
                    type = FormFieldType.Signature,
                    value = FormFieldValue.SignatureValue(
                        savedSignatureId = "sig-1",
                        signerName = "Ayman",
                        signedAtEpochMillis = 1234L,
                        status = SignatureVerificationStatus.Signed,
                        imagePath = "C:/signatures/sig-1.png",
                        kind = SignatureKind.Signature,
                    ),
                    signatureStatus = SignatureVerificationStatus.Signed,
                ),
            ),
        )

        val encoded = json.encodeToString(FormDocumentModel.serializer(), document)
        val decoded = json.decodeFromString(FormDocumentModel.serializer(), encoded)

        assertThat(decoded).isEqualTo(document)
    }

    @Test
    fun signatureCapture_roundTrips() {
        val capture = SignatureCapture(
            strokes = listOf(
                SignatureStroke(listOf(NormalizedPoint(0.1f, 0.2f), NormalizedPoint(0.5f, 0.6f))),
            ),
            width = 320f,
            height = 120f,
        )

        val encoded = json.encodeToString(SignatureCapture.serializer(), capture)
        val decoded = json.decodeFromString(SignatureCapture.serializer(), encoded)

        assertThat(decoded).isEqualTo(capture)
    }
}


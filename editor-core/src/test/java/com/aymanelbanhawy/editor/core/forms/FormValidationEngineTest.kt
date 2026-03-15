package com.aymanelbanhawy.editor.core.forms

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class FormValidationEngineTest {

    @Test
    fun validate_reportsRequiredDateAndLengthIssues() {
        val document = FormDocumentModel(
            fields = listOf(
                FormFieldModel(
                    name = "full_name",
                    label = "Full name",
                    pageIndex = 0,
                    bounds = com.aymanelbanhawy.editor.core.model.NormalizedRect(0.1f, 0.1f, 0.3f, 0.2f),
                    type = FormFieldType.Text,
                    required = true,
                    value = FormFieldValue.Text(""),
                ),
                FormFieldModel(
                    name = "birth_date",
                    label = "Birth date",
                    pageIndex = 0,
                    bounds = com.aymanelbanhawy.editor.core.model.NormalizedRect(0.1f, 0.2f, 0.3f, 0.3f),
                    type = FormFieldType.Date,
                    value = FormFieldValue.Text("2026-02-31"),
                ),
                FormFieldModel(
                    name = "notes",
                    label = "Notes",
                    pageIndex = 0,
                    bounds = com.aymanelbanhawy.editor.core.model.NormalizedRect(0.1f, 0.3f, 0.4f, 0.5f),
                    type = FormFieldType.MultilineText,
                    value = FormFieldValue.Text("Too long"),
                    maxLength = 3,
                ),
            ),
        )

        val summary = FormValidationEngine.validate(document)

        assertThat(summary.isValid).isFalse()
        assertThat(summary.issues).hasSize(3)
        assertThat(summary.issueFor("full_name")?.message).contains("required")
        assertThat(summary.issueFor("birth_date")?.message).contains("yyyy-MM-dd")
        assertThat(summary.issueFor("notes")?.message).contains("Maximum length")
    }
}

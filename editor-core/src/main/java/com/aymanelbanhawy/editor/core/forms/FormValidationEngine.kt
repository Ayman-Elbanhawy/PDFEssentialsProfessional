package com.aymanelbanhawy.editor.core.forms

import java.text.SimpleDateFormat
import java.util.Locale

object FormValidationEngine {
    fun validate(document: FormDocumentModel): FormValidationSummary {
        val issues = document.fields.mapNotNull { field ->
            when {
                field.required && field.value.isBlank() -> FormValidationIssue(field.name, "This field is required.", ValidationSeverity.Error)
                field.type == FormFieldType.Date && !field.value.isValidDate() -> FormValidationIssue(field.name, "Enter a valid date in yyyy-MM-dd format.", ValidationSeverity.Error)
                field.maxLength != null && field.value.length() > field.maxLength -> FormValidationIssue(field.name, "Maximum length is ${field.maxLength} characters.", ValidationSeverity.Error)
                else -> null
            }
        }
        return FormValidationSummary(issues)
    }
}

private fun FormFieldValue.isBlank(): Boolean = when (this) {
    is FormFieldValue.Text -> text.isBlank()
    is FormFieldValue.BooleanValue -> !checked
    is FormFieldValue.Choice -> selected.isBlank()
    is FormFieldValue.SignatureValue -> imagePath.isNullOrBlank() && signerName.isBlank()
}

private fun FormFieldValue.length(): Int = when (this) {
    is FormFieldValue.Text -> text.length
    is FormFieldValue.BooleanValue -> if (checked) 1 else 0
    is FormFieldValue.Choice -> selected.length
    is FormFieldValue.SignatureValue -> signerName.length
}

private fun FormFieldValue.isValidDate(): Boolean {
    if (this !is FormFieldValue.Text || text.isBlank()) return true
    return runCatching {
        SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { isLenient = false }.parse(text)
    }.isSuccess
}

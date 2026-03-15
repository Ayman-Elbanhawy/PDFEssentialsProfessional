package com.aymanelbanhawy.editor.core.command

import com.aymanelbanhawy.editor.core.forms.FormDocumentModel
import com.aymanelbanhawy.editor.core.forms.FormFieldModel

class UpdateFormFieldCommand(
    private val before: FormFieldModel,
    private val after: FormFieldModel,
) : EditorCommand {
    override val name: String = "Update form field"

    override fun apply(state: EditableDocumentState): EditableDocumentState {
        val updatedDocument = state.document.copy(formDocument = state.document.formDocument.updateField(after))
        return state.copy(document = updatedDocument, selection = state.selection.copy(selectedFormFieldName = after.name, selectedPageIndex = after.pageIndex))
    }

    override fun revert(state: EditableDocumentState): EditableDocumentState {
        val updatedDocument = state.document.copy(formDocument = state.document.formDocument.updateField(before))
        return state.copy(document = updatedDocument, selection = state.selection.copy(selectedFormFieldName = before.name, selectedPageIndex = before.pageIndex))
    }
}

class ReplaceFormDocumentCommand(
    private val before: FormDocumentModel,
    private val after: FormDocumentModel,
) : EditorCommand {
    override val name: String = "Apply form profile"

    override fun apply(state: EditableDocumentState): EditableDocumentState = state.copy(document = state.document.copy(formDocument = after))

    override fun revert(state: EditableDocumentState): EditableDocumentState = state.copy(document = state.document.copy(formDocument = before))
}

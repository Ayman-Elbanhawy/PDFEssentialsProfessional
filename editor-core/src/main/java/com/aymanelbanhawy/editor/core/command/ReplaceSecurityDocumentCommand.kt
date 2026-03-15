package com.aymanelbanhawy.editor.core.command

import com.aymanelbanhawy.editor.core.security.SecurityDocumentModel

class ReplaceSecurityDocumentCommand(
    private val before: SecurityDocumentModel,
    private val after: SecurityDocumentModel,
) : EditorCommand {
    override val name: String = "ReplaceSecurityDocument"

    override fun apply(state: EditableDocumentState): EditableDocumentState {
        return state.copy(document = state.document.copy(security = after))
    }

    override fun revert(state: EditableDocumentState): EditableDocumentState {
        return state.copy(document = state.document.copy(security = before))
    }
}

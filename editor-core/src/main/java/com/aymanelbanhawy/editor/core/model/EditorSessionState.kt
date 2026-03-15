package com.aymanelbanhawy.editor.core.model

import com.aymanelbanhawy.editor.core.forms.FormValidationSummary

data class EditorSessionState(
    val document: DocumentModel? = null,
    val selection: SelectionModel = SelectionModel(),
    val availableActions: List<EditorAction> = EditorAction.entries,
    val isLoading: Boolean = false,
    val undoRedoState: UndoRedoState = UndoRedoState(),
    val autosaveInFlight: Boolean = false,
    val formValidationSummary: FormValidationSummary = FormValidationSummary(),
)

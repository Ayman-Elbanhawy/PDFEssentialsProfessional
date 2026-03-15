package com.aymanelbanhawy.editor.core.model

import kotlinx.serialization.Serializable

@Serializable
data class DraftPayload(
    val document: DocumentModel,
    val selection: SelectionModel,
    val undoCount: Int,
    val redoCount: Int,
    val lastCommandName: String?,
)

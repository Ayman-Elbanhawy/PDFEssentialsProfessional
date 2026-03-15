package com.aymanelbanhawy.editor.core.model

import kotlinx.serialization.Serializable

@Serializable
data class UndoRedoState(
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val undoCount: Int = 0,
    val redoCount: Int = 0,
    val lastCommandName: String? = null,
)

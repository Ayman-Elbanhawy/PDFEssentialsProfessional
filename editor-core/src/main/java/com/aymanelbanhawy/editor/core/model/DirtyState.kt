package com.aymanelbanhawy.editor.core.model

import kotlinx.serialization.Serializable

@Serializable
data class DirtyState(
    val isDirty: Boolean = false,
    val lastModifiedAtEpochMillis: Long? = null,
    val saveMessage: String = "All changes saved",
)

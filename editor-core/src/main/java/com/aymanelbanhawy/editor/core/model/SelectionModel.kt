package com.aymanelbanhawy.editor.core.model

import kotlinx.serialization.Serializable

@Serializable
data class SelectionModel(
    val selectedPageIndex: Int = 0,
    val selectedAnnotationIds: Set<String> = emptySet(),
    val selectedFormFieldName: String? = null,
    val selectedEditId: String? = null,
    val selectedRedactionId: String? = null,
)

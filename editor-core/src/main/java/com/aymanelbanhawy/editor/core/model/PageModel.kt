package com.aymanelbanhawy.editor.core.model

import kotlinx.serialization.Serializable

@Serializable
data class PageModel(
    val index: Int,
    val label: String = "",
    val rotationDegrees: Int = 0,
    val annotations: List<AnnotationModel> = emptyList(),
    val editObjects: List<PageEditModel> = emptyList(),
    val contentType: PageContentType = PageContentType.Pdf,
    val sourceDocumentPath: String = "",
    val sourcePageIndex: Int = index,
    val widthPoints: Float = 612f,
    val heightPoints: Float = 792f,
    val insertedImagePath: String? = null,
)

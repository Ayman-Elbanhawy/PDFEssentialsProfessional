package com.aymanelbanhawy.editor.core.model

@Deprecated("Use AnnotationModel directly.")
object PageObjectModel {
    fun annotation(
        id: String,
        pageIndex: Int,
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        author: String,
        colorHex: String,
        body: String,
    ): AnnotationModel {
        val now = System.currentTimeMillis()
        return AnnotationModel(
            id = id,
            pageIndex = pageIndex,
            type = AnnotationType.Highlight,
            bounds = NormalizedRect(x, y, x + width, y + height).normalized(),
            strokeColorHex = colorHex,
            fillColorHex = colorHex,
            opacity = 0.32f,
            text = body,
            commentThread = AnnotationCommentThread(
                author = author,
                createdAtEpochMillis = now,
                modifiedAtEpochMillis = now,
                subject = body,
            ),
        )
    }
}

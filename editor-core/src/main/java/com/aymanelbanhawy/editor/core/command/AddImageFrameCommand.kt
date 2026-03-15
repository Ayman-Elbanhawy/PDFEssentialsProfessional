package com.aymanelbanhawy.editor.core.command

import com.aymanelbanhawy.editor.core.model.AnnotationCommentThread
import com.aymanelbanhawy.editor.core.model.AnnotationModel
import com.aymanelbanhawy.editor.core.model.AnnotationType
import com.aymanelbanhawy.editor.core.model.NormalizedRect

class AddImageFrameCommand(
    private val pageIndex: Int,
    private val frameAnnotation: AnnotationModel,
) : EditorCommand by AddAnnotationCommand(pageIndex, frameAnnotation.copy(type = AnnotationType.Rectangle)) {
    companion object {
        fun create(
            id: String,
            pageIndex: Int,
            bounds: NormalizedRect,
            label: String,
            author: String,
        ): AnnotationModel {
            val now = System.currentTimeMillis()
            return AnnotationModel(
                id = id,
                pageIndex = pageIndex,
                type = AnnotationType.Rectangle,
                bounds = bounds,
                strokeColorHex = "#5F6368",
                fillColorHex = "#20DADCE0",
                text = label,
                commentThread = AnnotationCommentThread(
                    author = author,
                    createdAtEpochMillis = now,
                    modifiedAtEpochMillis = now,
                    subject = label,
                ),
            )
        }
    }
}


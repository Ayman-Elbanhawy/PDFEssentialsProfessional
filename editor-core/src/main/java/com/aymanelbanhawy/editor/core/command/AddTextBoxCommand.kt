package com.aymanelbanhawy.editor.core.command

import com.aymanelbanhawy.editor.core.model.AnnotationCommentThread
import com.aymanelbanhawy.editor.core.model.AnnotationModel
import com.aymanelbanhawy.editor.core.model.AnnotationType
import com.aymanelbanhawy.editor.core.model.NormalizedRect

class AddTextBoxCommand(
    private val pageIndex: Int,
    private val textBox: AnnotationModel,
) : EditorCommand by AddAnnotationCommand(pageIndex, textBox.copy(type = AnnotationType.TextBox)) {
    companion object {
        fun create(
            id: String,
            pageIndex: Int,
            bounds: NormalizedRect,
            text: String,
            author: String,
            fontSizeSp: Float = 16f,
        ): AnnotationModel {
            val now = System.currentTimeMillis()
            return AnnotationModel(
                id = id,
                pageIndex = pageIndex,
                type = AnnotationType.TextBox,
                bounds = bounds,
                strokeColorHex = "#0B57D0",
                fillColorHex = "#FFFFFFFF",
                text = text,
                fontSizeSp = fontSizeSp,
                commentThread = AnnotationCommentThread(
                    author = author,
                    createdAtEpochMillis = now,
                    modifiedAtEpochMillis = now,
                    subject = text,
                ),
            )
        }
    }
}

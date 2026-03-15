package com.aymanelbanhawy.editor.core.model

import kotlinx.serialization.Serializable
import kotlin.math.max
import kotlin.math.min

@Serializable
enum class AnnotationType {
    Highlight,
    Underline,
    Strikeout,
    FreehandInk,
    Rectangle,
    Ellipse,
    Arrow,
    Line,
    StickyNote,
    TextBox,
}

@Serializable
enum class AnnotationStatus {
    Open,
    Accepted,
    Rejected,
    Cancelled,
    Completed,
}

@Serializable
data class NormalizedPoint(
    val x: Float,
    val y: Float,
)

@Serializable
data class NormalizedRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    fun normalized(): NormalizedRect {
        return copy(
            left = min(left, right).coerceIn(0f, 1f),
            top = min(top, bottom).coerceIn(0f, 1f),
            right = max(left, right).coerceIn(0f, 1f),
            bottom = max(top, bottom).coerceIn(0f, 1f),
        )
    }

    fun offset(dx: Float, dy: Float): NormalizedRect {
        val width = width
        val height = height
        val clampedLeft = (left + dx).coerceIn(0f, 1f - width)
        val clampedTop = (top + dy).coerceIn(0f, 1f - height)
        return NormalizedRect(clampedLeft, clampedTop, clampedLeft + width, clampedTop + height)
    }

    fun resize(anchor: ResizeAnchor, x: Float, y: Float): NormalizedRect {
        val target = when (anchor) {
            ResizeAnchor.TopLeft -> copy(left = x, top = y)
            ResizeAnchor.TopRight -> copy(right = x, top = y)
            ResizeAnchor.BottomLeft -> copy(left = x, bottom = y)
            ResizeAnchor.BottomRight -> copy(right = x, bottom = y)
            ResizeAnchor.Start -> copy(left = x)
            ResizeAnchor.End -> copy(right = x)
            ResizeAnchor.Top -> copy(top = y)
            ResizeAnchor.Bottom -> copy(bottom = y)
        }.normalized()
        val minWidth = 0.02f
        val minHeight = 0.02f
        val fixedRight = max(target.right, target.left + minWidth).coerceIn(0f, 1f)
        val fixedBottom = max(target.bottom, target.top + minHeight).coerceIn(0f, 1f)
        return target.copy(right = fixedRight, bottom = fixedBottom)
    }

    val width: Float get() = (right - left).coerceAtLeast(0f)
    val height: Float get() = (bottom - top).coerceAtLeast(0f)
    val center: NormalizedPoint get() = NormalizedPoint((left + right) / 2f, (top + bottom) / 2f)

    companion object {
        fun fromPoints(start: NormalizedPoint, end: NormalizedPoint): NormalizedRect {
            return NormalizedRect(start.x, start.y, end.x, end.y).normalized()
        }
    }
}

@Serializable
enum class ResizeAnchor {
    TopLeft,
    TopRight,
    BottomLeft,
    BottomRight,
    Start,
    End,
    Top,
    Bottom,
}

@Serializable
data class AnnotationReply(
    val id: String,
    val author: String,
    val message: String,
    val createdAtEpochMillis: Long,
)

@Serializable
data class AnnotationCommentThread(
    val author: String,
    val createdAtEpochMillis: Long,
    val modifiedAtEpochMillis: Long,
    val status: AnnotationStatus = AnnotationStatus.Open,
    val subject: String = "",
    val replies: List<AnnotationReply> = emptyList(),
)

@Serializable
data class AnnotationModel(
    val id: String,
    val pageIndex: Int,
    val type: AnnotationType,
    val bounds: NormalizedRect,
    val points: List<NormalizedPoint> = emptyList(),
    val strokeColorHex: String,
    val fillColorHex: String? = null,
    val opacity: Float = 1f,
    val strokeWidth: Float = 0.008f,
    val text: String = "",
    val fontSizeSp: Float = 14f,
    val icon: String = "",
    val commentThread: AnnotationCommentThread,
) {
    fun movedBy(dx: Float, dy: Float): AnnotationModel = copy(bounds = bounds.offset(dx, dy))

    fun resized(anchor: ResizeAnchor, x: Float, y: Float): AnnotationModel = copy(bounds = bounds.resize(anchor, x, y))

    fun recolored(strokeColorHex: String, fillColorHex: String?): AnnotationModel = copy(
        strokeColorHex = strokeColorHex,
        fillColorHex = fillColorHex,
        commentThread = commentThread.copy(modifiedAtEpochMillis = System.currentTimeMillis()),
    )

    fun withPage(pageIndex: Int): AnnotationModel = copy(pageIndex = pageIndex)

    fun duplicated(newId: String, delta: Float = 0.02f): AnnotationModel = copy(
        id = newId,
        bounds = bounds.offset(delta, delta),
        commentThread = commentThread.copy(
            createdAtEpochMillis = System.currentTimeMillis(),
            modifiedAtEpochMillis = System.currentTimeMillis(),
        ),
    )
}

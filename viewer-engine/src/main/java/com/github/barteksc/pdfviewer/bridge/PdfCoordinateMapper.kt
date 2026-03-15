package com.github.barteksc.pdfviewer.bridge

import android.graphics.PointF
import android.graphics.RectF
import com.aymanelbanhawy.editor.core.model.NormalizedPoint
import com.aymanelbanhawy.editor.core.model.NormalizedRect

object PdfCoordinateMapper {
    fun pagePointToView(pageBounds: RectF, point: NormalizedPoint): PointF {
        return PointF(
            pageBounds.left + pageBounds.width() * point.x,
            pageBounds.top + pageBounds.height() * point.y,
        )
    }

    fun viewPointToPage(pageBounds: RectF, x: Float, y: Float): NormalizedPoint {
        if (pageBounds.width() <= 0f || pageBounds.height() <= 0f) {
            return NormalizedPoint(0f, 0f)
        }
        return NormalizedPoint(
            ((x - pageBounds.left) / pageBounds.width()).coerceIn(0f, 1f),
            ((y - pageBounds.top) / pageBounds.height()).coerceIn(0f, 1f),
        )
    }

    fun pageRectToView(pageBounds: RectF, rect: NormalizedRect): RectF {
        return RectF(
            pageBounds.left + pageBounds.width() * rect.left,
            pageBounds.top + pageBounds.height() * rect.top,
            pageBounds.left + pageBounds.width() * rect.right,
            pageBounds.top + pageBounds.height() * rect.bottom,
        )
    }

    fun viewRectToPage(pageBounds: RectF, rect: RectF): NormalizedRect {
        return NormalizedRect(
            left = ((rect.left - pageBounds.left) / pageBounds.width()).coerceIn(0f, 1f),
            top = ((rect.top - pageBounds.top) / pageBounds.height()).coerceIn(0f, 1f),
            right = ((rect.right - pageBounds.left) / pageBounds.width()).coerceIn(0f, 1f),
            bottom = ((rect.bottom - pageBounds.top) / pageBounds.height()).coerceIn(0f, 1f),
        ).normalized()
    }
}

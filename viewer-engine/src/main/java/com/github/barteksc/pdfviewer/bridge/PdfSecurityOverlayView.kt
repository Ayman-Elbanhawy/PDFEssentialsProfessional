package com.github.barteksc.pdfviewer.bridge

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import com.aymanelbanhawy.editor.core.security.SecurityDocumentModel
import com.github.barteksc.pdfviewer.PDFView

internal class PdfSecurityOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    var pdfView: PDFView? = null
    var securityDocument: SecurityDocumentModel = SecurityDocumentModel()
        set(value) {
            field = value
            invalidate()
        }

    private val markedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#55B3261E")
    }
    private val appliedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#CC111111")
    }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
        color = Color.WHITE
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 24f
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val pdf = pdfView ?: return
        if (!securityDocument.redactionWorkflow.previewEnabled && securityDocument.redactionWorkflow.marks.none { it.status.name == "Applied" }) return
        securityDocument.redactionWorkflow.marks.forEach { mark ->
            val pageBounds = pdf.getPageBounds(mark.pageIndex)
            if (pageBounds.width() <= 0f || pageBounds.height() <= 0f) return@forEach
            val rect = PdfCoordinateMapper.pageRectToView(pageBounds, mark.bounds)
            canvas.drawRect(rect, if (mark.status.name == "Applied") appliedPaint else markedPaint)
            canvas.drawRect(rect, borderPaint)
            canvas.drawText(mark.overlayText, rect.left + 12f, rect.centerY(), textPaint)
        }
    }
}

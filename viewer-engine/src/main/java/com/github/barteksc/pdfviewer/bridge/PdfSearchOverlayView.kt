package com.github.barteksc.pdfviewer.bridge

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import com.aymanelbanhawy.editor.core.search.ExtractedTextBlock
import com.aymanelbanhawy.editor.core.search.SearchHit
import com.github.barteksc.pdfviewer.PDFView

internal class PdfSearchOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    var pdfView: PDFView? = null
    var searchHits: List<SearchHit> = emptyList()
        set(value) {
            field = value
            invalidate()
        }
    var selectedBlocks: List<ExtractedTextBlock> = emptyList()
        set(value) {
            field = value
            invalidate()
        }

    private val searchPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#66FFD54F")
    }
    private val selectedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#8834A853")
    }
    private val outlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.parseColor("#1E8E3E")
        strokeWidth = 2f
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val pdf = pdfView ?: return
        searchHits.forEach { hit ->
            val pageBounds = pdf.getPageBounds(hit.pageIndex)
            if (pageBounds.width() <= 0f || pageBounds.height() <= 0f) return@forEach
            val rect = PdfCoordinateMapper.pageRectToView(pageBounds, hit.bounds)
            canvas.drawRect(rect, searchPaint)
        }
        selectedBlocks.forEach { block ->
            val pageBounds = pdf.getPageBounds(block.pageIndex)
            if (pageBounds.width() <= 0f || pageBounds.height() <= 0f) return@forEach
            val rect = PdfCoordinateMapper.pageRectToView(pageBounds, block.bounds)
            canvas.drawRect(rect, selectedPaint)
            canvas.drawRect(RectF(rect), outlinePaint)
        }
    }
}

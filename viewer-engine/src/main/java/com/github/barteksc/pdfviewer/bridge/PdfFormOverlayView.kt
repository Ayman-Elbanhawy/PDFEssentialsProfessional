package com.github.barteksc.pdfviewer.bridge

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.aymanelbanhawy.editor.core.forms.FormDocumentModel
import com.aymanelbanhawy.editor.core.forms.FormFieldType
import com.aymanelbanhawy.editor.core.forms.SignatureVerificationStatus
import com.aymanelbanhawy.editor.core.model.AnnotationTool
import com.aymanelbanhawy.editor.core.model.SelectionModel
import com.github.barteksc.pdfviewer.PDFView

internal class PdfFormOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    var pdfView: PDFView? = null
    var formDocument: FormDocumentModel = FormDocumentModel()
        set(value) {
            field = value
            invalidate()
        }
    var selection: SelectionModel = SelectionModel()
        set(value) {
            field = value
            invalidate()
        }
    var activeTool: AnnotationTool = AnnotationTool.Select
        set(value) {
            field = value
            invalidate()
        }
    var callbacks: PdfViewportCallbacks = PdfViewportCallbacks()

    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
        color = Color.parseColor("#1565C0")
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#221565C0")
    }
    private val selectedStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f
        color = Color.parseColor("#0B57D0")
    }
    private val selectedFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#330B57D0")
    }
    private val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#0B57D0")
        textSize = 28f
    }
    private val badgeBackgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.WHITE
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        formDocument.fields.forEach { field ->
            val pageBounds = pdfView?.getPageBounds(field.pageIndex) ?: return@forEach
            if (pageBounds.width() <= 0f || pageBounds.height() <= 0f) return@forEach
            val rect = PdfCoordinateMapper.pageRectToView(pageBounds, field.bounds)
            val isSelected = field.name == selection.selectedFormFieldName
            canvas.drawRoundRect(rect, 12f, 12f, if (isSelected) selectedFillPaint else fillPaint)
            canvas.drawRoundRect(rect, 12f, 12f, if (isSelected) selectedStrokePaint else strokePaint)
            val badge = buildBadge(field.type, field.signatureStatus)
            if (badge.isNotBlank()) {
                drawBadge(canvas, rect, badge)
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (activeTool != AnnotationTool.Select) return false
        if (event.actionMasked != MotionEvent.ACTION_DOWN) return false
        val hitField = formDocument.fields
            .asReversed()
            .firstOrNull { field ->
                val pageBounds = pdfView?.getPageBounds(field.pageIndex) ?: return@firstOrNull false
                PdfCoordinateMapper.pageRectToView(pageBounds, field.bounds).contains(event.x, event.y)
            } ?: return false
        callbacks.onFormFieldTapped(hitField.name)
        return true
    }

    private fun drawBadge(canvas: Canvas, rect: RectF, badge: String) {
        val width = badgePaint.measureText(badge) + 24f
        val badgeRect = RectF(rect.left + 8f, rect.top + 8f, rect.left + 8f + width, rect.top + 38f)
        canvas.drawRoundRect(badgeRect, 16f, 16f, badgeBackgroundPaint)
        canvas.drawText(badge, badgeRect.left + 12f, badgeRect.bottom - 10f, badgePaint)
    }

    private fun buildBadge(type: FormFieldType, status: SignatureVerificationStatus): String {
        return when (type) {
            FormFieldType.Signature -> when (status) {
                SignatureVerificationStatus.Unsigned -> "Signature"
                SignatureVerificationStatus.Signed -> "Signed"
                SignatureVerificationStatus.Verified -> "Verified"
                SignatureVerificationStatus.Invalid -> "Invalid"
                SignatureVerificationStatus.VerificationFailed -> "Verify"
            }
            FormFieldType.Checkbox -> "Check"
            FormFieldType.RadioGroup -> "Radio"
            FormFieldType.Dropdown -> "List"
            FormFieldType.Date -> "Date"
            FormFieldType.MultilineText -> "Multi"
            FormFieldType.Text -> "Text"
        }
    }
}


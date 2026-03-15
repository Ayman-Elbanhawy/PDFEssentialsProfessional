package com.github.barteksc.pdfviewer.bridge

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.aymanelbanhawy.editor.core.model.AnnotationTool
import com.aymanelbanhawy.editor.core.model.ImageEditModel
import com.aymanelbanhawy.editor.core.model.NormalizedRect
import com.aymanelbanhawy.editor.core.model.PageEditModel
import com.aymanelbanhawy.editor.core.model.PageModel
import com.aymanelbanhawy.editor.core.model.ResizeAnchor
import com.aymanelbanhawy.editor.core.model.SelectionModel
import com.aymanelbanhawy.editor.core.model.TextBoxEditModel
import com.github.barteksc.pdfviewer.PDFView
import kotlin.math.hypot

internal class PdfPageEditOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    var pdfView: PDFView? = null
    var pages: List<PageModel> = emptyList()
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
            gesture = null
            workingEdit = null
            invalidate()
        }
    var callbacks: PdfViewportCallbacks = PdfViewportCallbacks()

    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
        color = Color.parseColor("#1A73E8")
        pathEffect = DashPathEffect(floatArrayOf(10f, 8f), 0f)
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.BLACK
        textSize = 34f
    }
    private val handlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.WHITE
    }
    private val handleStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
        color = Color.parseColor("#1A73E8")
    }

    private var gesture: GestureState? = null
    private var workingEdit: PageEditModel? = null

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        pages.forEach { page ->
            val pageBounds = pdfView?.getPageBounds(page.index) ?: return@forEach
            if (pageBounds.width() <= 0f || pageBounds.height() <= 0f) return@forEach
            page.editObjects.forEach { drawEdit(canvas, pageBounds, it, it.id == selection.selectedEditId) }
        }
        workingEdit?.let { edit ->
            val pageBounds = pdfView?.getPageBounds(edit.pageIndex) ?: return
            drawEdit(canvas, pageBounds, edit, true)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        return handleSelectionTouch(event)
    }

    private fun handleSelectionTouch(event: MotionEvent): Boolean {
        val pdf = pdfView ?: return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val hit = hitTest(event.x, event.y)
                val selected = hit?.editObject
                if (selected == null) {
                    callbacks.onPageEditSelected(selection.selectedPageIndex, null)
                    workingEdit = null
                    gesture = null
                    invalidate()
                    return false
                }
                callbacks.onPageEditSelected(selected.pageIndex, selected.id)
                gesture = if (hit.handle != null) {
                    GestureState.Resize(selected, hit.handle)
                } else {
                    GestureState.Move(selected, PdfCoordinateMapper.viewPointToPage(pdf.getPageBounds(selected.pageIndex), event.x, event.y))
                }
                workingEdit = selected
                parent.requestDisallowInterceptTouchEvent(true)
                invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val currentGesture = gesture ?: return false
                val current = workingEdit ?: return false
                val pageBounds = pdf.getPageBounds(current.pageIndex)
                val point = PdfCoordinateMapper.viewPointToPage(pageBounds, event.x, event.y)
                workingEdit = when (currentGesture) {
                    is GestureState.Move -> currentGesture.original.movedBy(point.x - currentGesture.startPoint.x, point.y - currentGesture.startPoint.y)
                    is GestureState.Resize -> currentGesture.original.resized(currentGesture.anchor, point.x, point.y)
                }
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP -> {
                val original = when (val currentGesture = gesture) {
                    is GestureState.Move -> currentGesture.original
                    is GestureState.Resize -> currentGesture.original
                    null -> null
                }
                val current = workingEdit
                if (current != null && original != null && current != original) {
                    callbacks.onPageEditUpdated(original, current)
                }
                gesture = null
                workingEdit = null
                invalidate()
                return current != null
            }
            MotionEvent.ACTION_CANCEL -> {
                gesture = null
                workingEdit = null
                invalidate()
                return true
            }
        }
        return false
    }

    private fun drawEdit(canvas: Canvas, pageBounds: RectF, editObject: PageEditModel, isSelected: Boolean) {
        val bounds = PdfCoordinateMapper.pageRectToView(pageBounds, editObject.bounds)
        when (editObject) {
            is TextBoxEditModel -> drawTextBox(canvas, bounds, editObject)
            is ImageEditModel -> drawImage(canvas, bounds, editObject)
        }
        if (isSelected) {
            canvas.save()
            canvas.rotate(editObject.rotationDegrees, bounds.centerX(), bounds.centerY())
            canvas.drawRect(bounds, strokePaint)
            handlePoints(bounds).values.forEach { point ->
                canvas.drawCircle(point.x, point.y, 10f, handlePaint)
                canvas.drawCircle(point.x, point.y, 10f, handleStrokePaint)
            }
            canvas.restore()
        }
    }

    private fun drawTextBox(canvas: Canvas, bounds: RectF, edit: TextBoxEditModel) {
        val bgColor = Color.argb((edit.opacity * 45).toInt().coerceIn(10, 90), 255, 255, 255)
        val textColor = runCatching { Color.parseColor(edit.textColorHex) }.getOrDefault(Color.BLACK)
        fillPaint.color = bgColor
        textPaint.color = textColor
        textPaint.textSize = edit.fontSizeSp * resources.displayMetrics.scaledDensity
        canvas.save()
        canvas.rotate(edit.rotationDegrees, bounds.centerX(), bounds.centerY())
        canvas.drawRect(bounds, fillPaint)
        val lines = edit.text.ifBlank { "Text" }.split('\n')
        var y = bounds.top + textPaint.textSize + 12f
        lines.forEach { line ->
            if (y > bounds.bottom) return@forEach
            val textWidth = textPaint.measureText(line)
            val x = when (edit.alignment) {
                com.aymanelbanhawy.editor.core.model.TextAlignment.Start -> bounds.left + 12f
                com.aymanelbanhawy.editor.core.model.TextAlignment.Center -> bounds.centerX() - (textWidth / 2f)
                com.aymanelbanhawy.editor.core.model.TextAlignment.End -> bounds.right - textWidth - 12f
            }
            canvas.drawText(line, x, y, textPaint)
            y += textPaint.textSize * edit.lineSpacingMultiplier
        }
        canvas.restore()
    }

    private fun drawImage(canvas: Canvas, bounds: RectF, edit: ImageEditModel) {
        val bitmap = BitmapFactory.decodeFile(edit.imagePath)
        canvas.save()
        canvas.rotate(edit.rotationDegrees, bounds.centerX(), bounds.centerY())
        if (bitmap != null) {
            val alpha = (edit.opacity * 255).toInt().coerceIn(32, 255)
            fillPaint.alpha = alpha
            canvas.drawBitmap(bitmap, null, bounds, fillPaint)
        } else {
            fillPaint.color = Color.parseColor("#33DADCE0")
            canvas.drawRect(bounds, fillPaint)
            textPaint.color = Color.DKGRAY
            canvas.drawText(edit.label, bounds.left + 12f, bounds.centerY(), textPaint)
        }
        canvas.restore()
    }

    private fun hitTest(x: Float, y: Float): HitResult? {
        val selected = pages.flatMap { it.editObjects }.firstOrNull { it.id == selection.selectedEditId }
        if (selected != null) {
            val pageBounds = pdfView?.getPageBounds(selected.pageIndex) ?: RectF()
            val selectedBounds = PdfCoordinateMapper.pageRectToView(pageBounds, selected.bounds)
            val handle = handlePoints(selectedBounds).entries.firstOrNull { (_, point) -> hypot((x - point.x).toDouble(), (y - point.y).toDouble()) <= 24.0 }?.key
            if (handle != null) return HitResult(selected, handle)
        }
        val candidate = pages.asReversed().flatMap { it.editObjects.asReversed() }.firstOrNull { edit ->
            val pageBounds = pdfView?.getPageBounds(edit.pageIndex) ?: RectF()
            hitBounds(pageBounds, edit, x, y)
        }
        return candidate?.let { HitResult(it, null) }
    }

    private fun hitBounds(pageBounds: RectF, edit: PageEditModel, x: Float, y: Float): Boolean {
        val bounds = PdfCoordinateMapper.pageRectToView(pageBounds, edit.bounds)
        val matrix = Matrix().apply { postRotate(edit.rotationDegrees, bounds.centerX(), bounds.centerY()) }
        val inverse = Matrix()
        matrix.invert(inverse)
        val points = floatArrayOf(x, y)
        inverse.mapPoints(points)
        return RectF(bounds).apply { inset(-16f, -16f) }.contains(points[0], points[1])
    }

    private fun handlePoints(bounds: RectF): Map<ResizeAnchor, android.graphics.PointF> {
        return mapOf(
            ResizeAnchor.TopLeft to android.graphics.PointF(bounds.left, bounds.top),
            ResizeAnchor.TopRight to android.graphics.PointF(bounds.right, bounds.top),
            ResizeAnchor.BottomLeft to android.graphics.PointF(bounds.left, bounds.bottom),
            ResizeAnchor.BottomRight to android.graphics.PointF(bounds.right, bounds.bottom),
        )
    }

    private sealed interface GestureState {
        data class Move(val original: PageEditModel, val startPoint: com.aymanelbanhawy.editor.core.model.NormalizedPoint) : GestureState
        data class Resize(val original: PageEditModel, val anchor: ResizeAnchor) : GestureState
    }

    private data class HitResult(
        val editObject: PageEditModel,
        val handle: ResizeAnchor?,
    )
}

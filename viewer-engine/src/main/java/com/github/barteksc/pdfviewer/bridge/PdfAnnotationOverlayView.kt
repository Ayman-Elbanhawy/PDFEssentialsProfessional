package com.github.barteksc.pdfviewer.bridge

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.aymanelbanhawy.editor.core.model.AnnotationCommentThread
import com.aymanelbanhawy.editor.core.model.AnnotationModel
import com.aymanelbanhawy.editor.core.model.AnnotationTool
import com.aymanelbanhawy.editor.core.model.AnnotationType
import com.aymanelbanhawy.editor.core.model.NormalizedPoint
import com.aymanelbanhawy.editor.core.model.NormalizedRect
import com.aymanelbanhawy.editor.core.model.ResizeAnchor
import com.aymanelbanhawy.editor.core.model.SelectionModel
import com.github.barteksc.pdfviewer.PDFView
import java.util.UUID
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max

internal class PdfAnnotationOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    var pdfView: PDFView? = null
    var pages: List<com.aymanelbanhawy.editor.core.model.PageModel> = emptyList()
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
            workingAnnotation = null
            gesture = null
            invalidate()
        }
    var callbacks: PdfViewportCallbacks = PdfViewportCallbacks()

    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.BLACK
        textSize = 36f
    }
    private val selectionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.parseColor("#0B57D0")
        strokeWidth = 3f
        pathEffect = DashPathEffect(floatArrayOf(12f, 10f), 0f)
    }
    private val handlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.WHITE
    }
    private val handleStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.parseColor("#0B57D0")
        strokeWidth = 2f
    }

    private var gesture: GestureState? = null
    private var workingAnnotation: AnnotationModel? = null

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val currentPages = pages
        currentPages.forEach { page ->
            val pageBounds = pdfView?.getPageBounds(page.index) ?: return@forEach
            if (pageBounds.width() <= 0f || pageBounds.height() <= 0f) return@forEach
            page.annotations.forEach { annotation -> drawAnnotation(canvas, pageBounds, annotation, annotation.id in selection.selectedAnnotationIds) }
        }
        workingAnnotation?.let { annotation ->
            val pageBounds = pdfView?.getPageBounds(annotation.pageIndex) ?: return
            drawAnnotation(canvas, pageBounds, annotation, true)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        return when (activeTool) {
            AnnotationTool.Select -> handleSelectionTouch(event)
            else -> handleCreateTouch(event)
        }
    }

    private fun handleCreateTouch(event: MotionEvent): Boolean {
        val pdf = pdfView ?: return false
        val pageIndex = locatePage(event.x, event.y) ?: return false
        val pageBounds = pdf.getPageBounds(pageIndex)
        val pagePoint = PdfCoordinateMapper.viewPointToPage(pageBounds, event.x, event.y)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                gesture = GestureState.Create(pageIndex, pagePoint)
                workingAnnotation = if (activeTool == AnnotationTool.StickyNote) buildAnnotation(pageIndex, pagePoint, pagePoint) else null
                parent.requestDisallowInterceptTouchEvent(true)
                invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val createGesture = gesture as? GestureState.Create ?: return false
                workingAnnotation = buildAnnotation(createGesture.pageIndex, createGesture.startPoint, pagePoint)
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP -> {
                val createGesture = gesture as? GestureState.Create ?: return false
                val annotation = workingAnnotation ?: buildAnnotation(createGesture.pageIndex, createGesture.startPoint, pagePoint)
                callbacks.onAnnotationCreated(annotation)
                callbacks.onAnnotationSelectionChanged(annotation.pageIndex, setOf(annotation.id))
                gesture = null
                workingAnnotation = null
                invalidate()
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                gesture = null
                workingAnnotation = null
                invalidate()
                return true
            }
        }
        return false
    }

    private fun handleSelectionTouch(event: MotionEvent): Boolean {
        val pdf = pdfView ?: return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val hit = hitTest(event.x, event.y)
                val selected = hit?.annotation
                if (selected == null) {
                    callbacks.onAnnotationSelectionChanged(selection.selectedPageIndex, emptySet())
                    workingAnnotation = null
                    gesture = null
                    invalidate()
                    return false
                }
                callbacks.onAnnotationSelectionChanged(selected.pageIndex, setOf(selected.id))
                gesture = if (hit.handle != null) {
                    GestureState.Resize(selected, hit.handle)
                } else {
                    GestureState.Move(selected, PdfCoordinateMapper.viewPointToPage(pdf.getPageBounds(selected.pageIndex), event.x, event.y))
                }
                workingAnnotation = selected
                parent.requestDisallowInterceptTouchEvent(true)
                invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val currentGesture = gesture ?: return false
                val current = workingAnnotation ?: return false
                val pageBounds = pdf.getPageBounds(current.pageIndex)
                val pagePoint = PdfCoordinateMapper.viewPointToPage(pageBounds, event.x, event.y)
                workingAnnotation = when (currentGesture) {
                    is GestureState.Move -> {
                        val dx = pagePoint.x - currentGesture.startPoint.x
                        val dy = pagePoint.y - currentGesture.startPoint.y
                        currentGesture.original.movedBy(dx, dy).copy(commentThread = current.commentThread.copy(modifiedAtEpochMillis = System.currentTimeMillis()))
                    }
                    is GestureState.Resize -> {
                        currentGesture.original.resized(currentGesture.anchor, pagePoint.x, pagePoint.y)
                            .copy(commentThread = current.commentThread.copy(modifiedAtEpochMillis = System.currentTimeMillis()))
                    }
                    is GestureState.Create -> current
                }
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP -> {
                val current = workingAnnotation
                val original = when (val currentGesture = gesture) {
                    is GestureState.Move -> currentGesture.original
                    is GestureState.Resize -> currentGesture.original
                    else -> null
                }
                if (current != null && original != null && current != original) {
                    callbacks.onAnnotationUpdated(original, current)
                }
                gesture = null
                workingAnnotation = null
                invalidate()
                return current != null
            }
            MotionEvent.ACTION_CANCEL -> {
                gesture = null
                workingAnnotation = null
                invalidate()
                return true
            }
        }
        return false
    }

    private fun drawAnnotation(canvas: Canvas, pageBounds: RectF, annotation: AnnotationModel, isSelected: Boolean) {
        val strokeColor = runCatching { Color.parseColor(annotation.strokeColorHex) }.getOrDefault(Color.RED)
        val fillColor = annotation.fillColorHex?.let { runCatching { Color.parseColor(it) }.getOrDefault(Color.TRANSPARENT) } ?: Color.TRANSPARENT
        strokePaint.color = strokeColor
        strokePaint.strokeWidth = max(2f, annotation.strokeWidth * pageBounds.width())
        strokePaint.alpha = (annotation.opacity * 255).toInt().coerceIn(0, 255)
        fillPaint.color = fillColor
        fillPaint.alpha = (annotation.opacity * 255).toInt().coerceIn(0, 255)
        textPaint.textSize = max(22f, annotation.fontSizeSp * resources.displayMetrics.scaledDensity)

        val bounds = PdfCoordinateMapper.pageRectToView(pageBounds, annotation.bounds)
        when (annotation.type) {
            AnnotationType.Highlight -> {
                canvas.drawRect(bounds, fillPaint)
                canvas.drawRect(bounds, strokePaint)
            }
            AnnotationType.Underline -> {
                canvas.drawLine(bounds.left, bounds.bottom, bounds.right, bounds.bottom, strokePaint)
            }
            AnnotationType.Strikeout -> {
                val midY = bounds.centerY()
                canvas.drawLine(bounds.left, midY, bounds.right, midY, strokePaint)
            }
            AnnotationType.Rectangle,
            AnnotationType.TextBox,
            AnnotationType.StickyNote -> {
                if (fillColor != Color.TRANSPARENT) canvas.drawRect(bounds, fillPaint)
                canvas.drawRect(bounds, strokePaint)
                if (annotation.text.isNotBlank()) {
                    canvas.drawText(annotation.text.take(24), bounds.left + 12f, bounds.top + textPaint.textSize + 10f, textPaint)
                }
            }
            AnnotationType.Ellipse -> {
                if (fillColor != Color.TRANSPARENT) canvas.drawOval(bounds, fillPaint)
                canvas.drawOval(bounds, strokePaint)
            }
            AnnotationType.Line,
            AnnotationType.Arrow -> {
                val start = annotation.points.getOrNull(0)?.let { PdfCoordinateMapper.pagePointToView(pageBounds, it) } ?: PointF(bounds.left, bounds.top)
                val end = annotation.points.getOrNull(1)?.let { PdfCoordinateMapper.pagePointToView(pageBounds, it) } ?: PointF(bounds.right, bounds.bottom)
                canvas.drawLine(start.x, start.y, end.x, end.y, strokePaint)
                if (annotation.type == AnnotationType.Arrow) drawArrowHead(canvas, start, end)
            }
            AnnotationType.FreehandInk -> {
                val path = Path()
                annotation.points.firstOrNull()?.let { first ->
                    val start = PdfCoordinateMapper.pagePointToView(pageBounds, first)
                    path.moveTo(start.x, start.y)
                    annotation.points.drop(1).forEach { point ->
                        val target = PdfCoordinateMapper.pagePointToView(pageBounds, point)
                        path.lineTo(target.x, target.y)
                    }
                    canvas.drawPath(path, strokePaint)
                }
            }
        }
        if (isSelected) {
            canvas.drawRect(bounds, selectionPaint)
            handlePoints(bounds).values.forEach { point ->
                canvas.drawCircle(point.x, point.y, 10f, handlePaint)
                canvas.drawCircle(point.x, point.y, 10f, handleStrokePaint)
            }
        }
    }

    private fun drawArrowHead(canvas: Canvas, start: PointF, end: PointF) {
        val angle = kotlin.math.atan2((end.y - start.y).toDouble(), (end.x - start.x).toDouble())
        val arrowLength = 24f
        val arrowAngle = Math.toRadians(24.0)
        val x1 = (end.x - arrowLength * kotlin.math.cos(angle - arrowAngle)).toFloat()
        val y1 = (end.y - arrowLength * kotlin.math.sin(angle - arrowAngle)).toFloat()
        val x2 = (end.x - arrowLength * kotlin.math.cos(angle + arrowAngle)).toFloat()
        val y2 = (end.y - arrowLength * kotlin.math.sin(angle + arrowAngle)).toFloat()
        canvas.drawLine(end.x, end.y, x1, y1, strokePaint)
        canvas.drawLine(end.x, end.y, x2, y2, strokePaint)
    }

    private fun buildAnnotation(pageIndex: Int, start: NormalizedPoint, end: NormalizedPoint): AnnotationModel {
        val now = System.currentTimeMillis()
        val bounds = NormalizedRect.fromPoints(start, when (activeTool) {
            AnnotationTool.StickyNote -> NormalizedPoint((start.x + 0.08f).coerceAtMost(0.98f), (start.y + 0.08f).coerceAtMost(0.98f))
            AnnotationTool.TextBox -> if (abs(end.x - start.x) < 0.02f && abs(end.y - start.y) < 0.02f) NormalizedPoint((start.x + 0.22f).coerceAtMost(0.98f), (start.y + 0.10f).coerceAtMost(0.98f)) else end
            else -> end
        })
        val type = when (activeTool) {
            AnnotationTool.Highlight -> AnnotationType.Highlight
            AnnotationTool.Underline -> AnnotationType.Underline
            AnnotationTool.Strikeout -> AnnotationType.Strikeout
            AnnotationTool.FreehandInk -> AnnotationType.FreehandInk
            AnnotationTool.Rectangle -> AnnotationType.Rectangle
            AnnotationTool.Ellipse -> AnnotationType.Ellipse
            AnnotationTool.Arrow -> AnnotationType.Arrow
            AnnotationTool.Line -> AnnotationType.Line
            AnnotationTool.StickyNote -> AnnotationType.StickyNote
            AnnotationTool.TextBox -> AnnotationType.TextBox
            AnnotationTool.Select -> AnnotationType.Rectangle
        }
        val points = when (type) {
            AnnotationType.Line, AnnotationType.Arrow -> listOf(start, end)
            AnnotationType.FreehandInk -> listOf(start, end)
            else -> emptyList()
        }
        val defaultText = when (type) {
            AnnotationType.TextBox -> "Text"
            AnnotationType.StickyNote -> "Note"
            else -> ""
        }
        val fill = when (type) {
            AnnotationType.Highlight -> "#55FFEB3B"
            AnnotationType.StickyNote -> "#FFFFF59D"
            AnnotationType.TextBox -> "#FFFFFFFF"
            AnnotationType.Rectangle, AnnotationType.Ellipse -> "#15FF9800"
            else -> null
        }
        return AnnotationModel(
            id = UUID.randomUUID().toString(),
            pageIndex = pageIndex,
            type = type,
            bounds = bounds,
            points = points,
            strokeColorHex = defaultStroke(type),
            fillColorHex = fill,
            opacity = if (type == AnnotationType.Highlight) 0.35f else 1f,
            text = defaultText,
            icon = if (type == AnnotationType.StickyNote) "comment" else "",
            commentThread = AnnotationCommentThread(
                author = "Ayman",
                createdAtEpochMillis = now,
                modifiedAtEpochMillis = now,
                subject = defaultText.ifBlank { type.name },
            ),
        )
    }

    private fun locatePage(x: Float, y: Float): Int? {
        return pages.firstOrNull { page ->
            val bounds = pdfView?.getPageBounds(page.index) ?: RectF()
            bounds.contains(x, y)
        }?.index
    }

    private fun hitTest(x: Float, y: Float): HitResult? {
        val selected = pages.flatMap { it.annotations }.firstOrNull { it.id in selection.selectedAnnotationIds }
        if (selected != null) {
            val pageBounds = pdfView?.getPageBounds(selected.pageIndex) ?: RectF()
            val selectedBounds = PdfCoordinateMapper.pageRectToView(pageBounds, selected.bounds)
            val handle = handlePoints(selectedBounds).entries.firstOrNull { (_, point) -> hypot((x - point.x).toDouble(), (y - point.y).toDouble()) <= 24.0 }?.key
            if (handle != null) return HitResult(selected, handle)
        }
        val candidate = pages.asReversed().flatMap { it.annotations.asReversed() }.firstOrNull { annotation ->
            val pageBounds = pdfView?.getPageBounds(annotation.pageIndex) ?: RectF()
            if (pageBounds.width() <= 0f) false else hitAnnotation(pageBounds, annotation, x, y)
        }
        return candidate?.let { HitResult(it, null) }
    }

    private fun hitAnnotation(pageBounds: RectF, annotation: AnnotationModel, x: Float, y: Float): Boolean {
        val bounds = PdfCoordinateMapper.pageRectToView(pageBounds, annotation.bounds)
        return when (annotation.type) {
            AnnotationType.Line, AnnotationType.Arrow -> {
                val start = annotation.points.getOrNull(0)?.let { PdfCoordinateMapper.pagePointToView(pageBounds, it) } ?: PointF(bounds.left, bounds.top)
                val end = annotation.points.getOrNull(1)?.let { PdfCoordinateMapper.pagePointToView(pageBounds, it) } ?: PointF(bounds.right, bounds.bottom)
                distanceToSegment(x, y, start, end) <= 24f
            }
            else -> RectF(bounds).apply { inset(-18f, -18f) }.contains(x, y)
        }
    }

    private fun handlePoints(bounds: RectF): Map<ResizeAnchor, PointF> {
        return mapOf(
            ResizeAnchor.TopLeft to PointF(bounds.left, bounds.top),
            ResizeAnchor.TopRight to PointF(bounds.right, bounds.top),
            ResizeAnchor.BottomLeft to PointF(bounds.left, bounds.bottom),
            ResizeAnchor.BottomRight to PointF(bounds.right, bounds.bottom),
        )
    }

    private fun distanceToSegment(x: Float, y: Float, start: PointF, end: PointF): Float {
        val dx = end.x - start.x
        val dy = end.y - start.y
        if (dx == 0f && dy == 0f) return hypot((x - start.x).toDouble(), (y - start.y).toDouble()).toFloat()
        val t = (((x - start.x) * dx) + ((y - start.y) * dy)) / (dx * dx + dy * dy)
        val clamped = t.coerceIn(0f, 1f)
        val px = start.x + clamped * dx
        val py = start.y + clamped * dy
        return hypot((x - px).toDouble(), (y - py).toDouble()).toFloat()
    }

    private fun defaultStroke(type: AnnotationType): String = when (type) {
        AnnotationType.Highlight -> "#F9AB00"
        AnnotationType.Underline -> "#0B57D0"
        AnnotationType.Strikeout -> "#B3261E"
        AnnotationType.FreehandInk -> "#1967D2"
        AnnotationType.Rectangle -> "#E37400"
        AnnotationType.Ellipse -> "#137333"
        AnnotationType.Arrow -> "#5E35B1"
        AnnotationType.Line -> "#455A64"
        AnnotationType.StickyNote -> "#F9AB00"
        AnnotationType.TextBox -> "#0B57D0"
    }

    private sealed interface GestureState {
        data class Create(val pageIndex: Int, val startPoint: NormalizedPoint) : GestureState
        data class Move(val original: AnnotationModel, val startPoint: NormalizedPoint) : GestureState
        data class Resize(val original: AnnotationModel, val anchor: ResizeAnchor) : GestureState
    }

    private data class HitResult(
        val annotation: AnnotationModel,
        val handle: ResizeAnchor?,
    )
}



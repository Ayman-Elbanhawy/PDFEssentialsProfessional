package com.aymanelbanhawy.editor.core.organize

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.aymanelbanhawy.editor.core.model.DocumentModel
import com.aymanelbanhawy.editor.core.model.PageContentType
import com.aymanelbanhawy.editor.core.model.PageModel
import com.aymanelbanhawy.editor.core.runtime.RuntimeDiagnosticsRepository
import com.aymanelbanhawy.editor.core.runtime.RuntimeEventCategory
import com.aymanelbanhawy.editor.core.runtime.RuntimeLogLevel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.coroutines.coroutineContext

interface PageThumbnailRepository {
    suspend fun thumbnailsFor(document: DocumentModel, widthPx: Int = 240): List<ThumbnailDescriptor>
}

class DefaultPageThumbnailRepository(
    private val context: Context,
    private val diagnosticsRepository: RuntimeDiagnosticsRepository? = null,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : PageThumbnailRepository {

    override suspend fun thumbnailsFor(document: DocumentModel, widthPx: Int): List<ThumbnailDescriptor> = withContext(ioDispatcher) {
        val startedAt = System.currentTimeMillis()
        val safeWidth = widthPx.coerceIn(120, 240)
        val cacheDir = File(context.cacheDir, "organize-thumbnails/${document.sessionId}").apply { mkdirs() }
        evictIfNeeded(cacheDir.parentFile ?: cacheDir)
        val descriptors = document.pages.mapIndexed { index, page ->
            coroutineContext.ensureActive()
            val target = File(cacheDir, "page_${index}_${page.rotationDegrees}_${page.contentType.name}.jpg")
            if (!target.exists()) {
                renderThumbnail(document, page, safeWidth, target)
            } else {
                target.setLastModified(System.currentTimeMillis())
            }
            ThumbnailDescriptor(pageIndex = index, imagePath = target.absolutePath)
        }
        diagnosticsRepository?.recordBreadcrumb(
            category = RuntimeEventCategory.Cache,
            level = RuntimeLogLevel.Debug,
            eventName = "thumbnail_generation",
            message = "Generated ${descriptors.size} thumbnails in ${System.currentTimeMillis() - startedAt}ms.",
            metadata = mapOf("document" to document.documentRef.displayName, "pageCount" to document.pageCount.toString()),
        )
        descriptors
    }

    private fun renderThumbnail(document: DocumentModel, page: PageModel, widthPx: Int, target: File) {
        when (page.contentType) {
            PageContentType.Pdf -> renderPdfPage(page, widthPx, target)
            PageContentType.Blank -> renderBlankPage(page, widthPx, target)
            PageContentType.Image -> renderImagePage(page, widthPx, target)
        }
    }

    private fun renderPdfPage(page: PageModel, widthPx: Int, target: File) {
        val sourcePath = page.sourceDocumentPath.ifBlank { return renderBlankPage(page, widthPx, target) }
        val file = File(sourcePath)
        if (!file.exists()) return renderBlankPage(page, widthPx, target)
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
            PdfRenderer(pfd).use { renderer ->
                val pageIndex = page.sourcePageIndex.coerceIn(0, renderer.pageCount - 1)
                renderer.openPage(pageIndex).use { rendererPage ->
                    val scale = widthPx / rendererPage.width.toFloat()
                    val height = (rendererPage.height * scale).toInt().coerceAtLeast(1)
                    val bitmap = Bitmap.createBitmap(widthPx, height, Bitmap.Config.RGB_565)
                    bitmap.eraseColor(Color.WHITE)
                    rendererPage.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    saveBitmap(bitmap, target)
                    bitmap.recycle()
                }
            }
        }
    }

    private fun renderBlankPage(page: PageModel, widthPx: Int, target: File) {
        val aspect = (page.heightPoints / page.widthPoints).coerceAtLeast(1f)
        val heightPx = (widthPx * aspect).toInt().coerceAtLeast(widthPx)
        val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.RGB_565)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.LTGRAY
            textSize = 28f
        }
        canvas.drawRect(0f, 0f, widthPx.toFloat(), heightPx.toFloat(), Paint().apply { style = Paint.Style.STROKE; color = Color.LTGRAY; strokeWidth = 3f })
        canvas.drawText("Blank page", 24f, 48f, paint)
        saveBitmap(bitmap, target)
        bitmap.recycle()
    }

    private fun renderImagePage(page: PageModel, widthPx: Int, target: File) {
        val imageFile = page.insertedImagePath?.let(::File)
        if (imageFile == null || !imageFile.exists()) {
            renderBlankPage(page, widthPx, target)
            return
        }
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
            BitmapFactory.decodeFile(imageFile.absolutePath, this)
            inSampleSize = calculateInSampleSize(outWidth, outHeight, widthPx)
            inJustDecodeBounds = false
            inPreferredConfig = Bitmap.Config.RGB_565
        }
        val source = BitmapFactory.decodeFile(imageFile.absolutePath, options) ?: return renderBlankPage(page, widthPx, target)
        val scale = widthPx / source.width.toFloat()
        val height = (source.height * scale).toInt().coerceAtLeast(1)
        val bitmap = Bitmap.createScaledBitmap(source, widthPx, height, true)
        saveBitmap(bitmap, target)
        if (bitmap !== source) bitmap.recycle()
        source.recycle()
    }

    private fun saveBitmap(bitmap: Bitmap, target: File) {
        FileOutputStream(target).use { output ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 82, output)
        }
    }

    private fun evictIfNeeded(root: File) {
        if (!root.exists()) return
        val files = root.walkTopDown().filter { it.isFile }.sortedByDescending { it.lastModified() }.toList()
        val overflowByCount = files.drop(MAX_CACHE_FILES)
        var totalBytes = files.sumOf { it.length() }
        val overflowByBytes = mutableListOf<File>()
        files.asReversed().forEach { file ->
            if (totalBytes <= MAX_CACHE_BYTES) return@forEach
            overflowByBytes += file
            totalBytes -= file.length()
        }
        (overflowByCount + overflowByBytes).distinct().forEach { it.delete() }
    }

    private fun calculateInSampleSize(width: Int, height: Int, requestedWidth: Int): Int {
        var sample = 1
        while ((width / sample) > requestedWidth * 2 || (height / sample) > requestedWidth * 3) {
            sample *= 2
        }
        return sample.coerceAtLeast(1)
    }

    private companion object {
        private const val MAX_CACHE_FILES = 120
        private const val MAX_CACHE_BYTES = 64L * 1024L * 1024L
    }
}

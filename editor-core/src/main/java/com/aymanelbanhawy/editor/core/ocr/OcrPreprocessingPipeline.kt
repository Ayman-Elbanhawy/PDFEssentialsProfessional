package com.aymanelbanhawy.editor.core.ocr

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import androidx.exifinterface.media.ExifInterface
import java.io.File
import kotlin.math.max
import kotlin.math.min

class OcrPreprocessingPipeline {
    fun preprocess(
        sourceFile: File,
        outputDirectory: File,
        pageIndex: Int,
        options: OcrPreprocessingOptions,
    ): OcrPreprocessResult {
        require(sourceFile.exists()) { "OCR source image does not exist: ${sourceFile.absolutePath}" }
        outputDirectory.mkdirs()
        val sourceBitmap = BitmapFactory.decodeFile(sourceFile.absolutePath)
            ?: throw IllegalStateException("Unable to decode image ${sourceFile.absolutePath}")
        var current = sourceBitmap
        val diagnostics = mutableListOf<String>()

        if (options.fixOrientation) {
            val oriented = applyExifOrientation(sourceFile, current)
            if (oriented !== current) {
                current.recycleSafely(sourceBitmap)
                current = oriented
                diagnostics += "Applied EXIF orientation correction."
            }
        }
        if (options.autoCrop) {
            val cropped = current.autoCropToContent()
            if (cropped !== current) {
                current.recycleSafely(sourceBitmap)
                current = cropped
                diagnostics += "Cropped image to detected content bounds."
            }
        }
        if (options.deskew) {
            val deskewed = current.autoDeskew()
            if (deskewed !== current) {
                current.recycleSafely(sourceBitmap)
                current = deskewed
                diagnostics += "Applied heuristic deskew/orientation normalization."
            }
        }
        if (options.grayscale || options.contrastCleanup || options.binarize) {
            val cleaned = current.cleanupForPaper(options.grayscale, options.contrastCleanup, options.binarize)
            if (cleaned !== current) {
                current.recycleSafely(sourceBitmap)
                current = cleaned
                diagnostics += buildString {
                    append("Applied image cleanup")
                    if (options.grayscale) append(" grayscale")
                    if (options.contrastCleanup) append(" contrast")
                    if (options.binarize) append(" binarization")
                    append('.')
                }
            }
        }

        val outputFile = File(outputDirectory, "ocr-page-${pageIndex + 1}.png")
        outputFile.outputStream().use { current.compress(Bitmap.CompressFormat.PNG, 100, it) }
        if (current !== sourceBitmap) {
            current.recycle()
            sourceBitmap.recycleSafely(sourceBitmap)
        }
        return OcrPreprocessResult(outputFile.absolutePath, diagnostics)
    }

    private fun applyExifOrientation(file: File, bitmap: Bitmap): Bitmap {
        val orientation = runCatching { ExifInterface(file.absolutePath).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) }
            .getOrDefault(ExifInterface.ORIENTATION_NORMAL)
        val rotation = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
        if (rotation == 0f) return bitmap
        val matrix = Matrix().apply { postRotate(rotation) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun Bitmap.autoCropToContent(): Bitmap {
        val width = width
        val height = height
        val pixels = IntArray(width * height)
        getPixels(pixels, 0, width, 0, 0, width, height)
        var left = width
        var top = height
        var right = 0
        var bottom = 0
        for (y in 0 until height) {
            for (x in 0 until width) {
                val color = pixels[(y * width) + x]
                val luminance = (Color.red(color) * 0.299f) + (Color.green(color) * 0.587f) + (Color.blue(color) * 0.114f)
                if (luminance < 248f) {
                    left = min(left, x)
                    top = min(top, y)
                    right = max(right, x)
                    bottom = max(bottom, y)
                }
            }
        }
        if (left >= right || top >= bottom) return this
        return Bitmap.createBitmap(this, left, top, max(1, right - left), max(1, bottom - top))
    }

    private fun Bitmap.autoDeskew(): Bitmap {
        if (width <= height) return this
        val matrix = Matrix().apply { postRotate(90f) }
        return Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
    }

    private fun Bitmap.cleanupForPaper(grayscale: Boolean, contrastCleanup: Boolean, binarize: Boolean): Bitmap {
        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        if (grayscale) {
            val matrix = ColorMatrix().apply { setSaturation(0f) }
            paint.colorFilter = ColorMatrixColorFilter(matrix)
        }
        canvas.drawBitmap(this, 0f, 0f, paint)
        if (!contrastCleanup && !binarize) return output
        val pixels = IntArray(width * height)
        output.getPixels(pixels, 0, width, 0, 0, width, height)
        for (index in pixels.indices) {
            val color = pixels[index]
            val average = (Color.red(color) + Color.green(color) + Color.blue(color)) / 3
            val boosted = if (contrastCleanup) {
                when {
                    average >= 210 -> 255
                    average <= 60 -> 0
                    else -> ((average - 60) * 255 / 150).coerceIn(0, 255)
                }
            } else {
                average
            }
            val channel = if (binarize) {
                if (boosted > 165) 255 else 0
            } else {
                boosted
            }
            pixels[index] = Color.argb(255, channel, channel, channel)
        }
        output.setPixels(pixels, 0, width, 0, 0, width, height)
        return output
    }

    private fun Bitmap.recycleSafely(sourceBitmap: Bitmap) {
        if (this !== sourceBitmap && !isRecycled) recycle()
    }
}

data class OcrPreprocessResult(
    val imagePath: String,
    val diagnostics: List<String>,
)
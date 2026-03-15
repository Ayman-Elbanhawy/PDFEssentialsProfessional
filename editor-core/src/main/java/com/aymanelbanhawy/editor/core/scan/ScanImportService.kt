package com.aymanelbanhawy.editor.core.scan

import android.content.Context
import android.graphics.BitmapFactory
import com.aymanelbanhawy.editor.core.model.OpenDocumentRequest
import com.aymanelbanhawy.editor.core.ocr.OcrJobPipeline
import com.aymanelbanhawy.editor.core.ocr.OcrPreprocessingPipeline
import com.aymanelbanhawy.editor.core.ocr.OcrSettingsModel
import com.aymanelbanhawy.editor.core.ocr.QueuedOcrPage
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import java.io.File
import java.util.UUID

interface ScanImportService {
    suspend fun importImages(imageFiles: List<File>, options: ScanImportOptions): OpenDocumentRequest
}

data class ScanImportOptions(
    val displayName: String = "scan-session.pdf",
    val ocrSettings: OcrSettingsModel = OcrSettingsModel(),
)

class DefaultScanImportService(
    private val context: Context,
    private val ocrJobPipeline: OcrJobPipeline,
    private val preprocessingPipeline: OcrPreprocessingPipeline = OcrPreprocessingPipeline(),
) : ScanImportService {

    init {
        PDFBoxResourceLoader.init(context)
    }

    override suspend fun importImages(imageFiles: List<File>, options: ScanImportOptions): OpenDocumentRequest {
        require(imageFiles.isNotEmpty()) { "At least one image is required." }
        val sessionDir = File(context.cacheDir, "scan-import/${UUID.randomUUID()}").apply { mkdirs() }
        val processedImages = imageFiles.mapIndexed { index, file ->
            preprocessingPipeline.preprocess(
                sourceFile = file,
                outputDirectory = sessionDir.resolve("processed").apply { mkdirs() },
                pageIndex = index,
                options = options.ocrSettings.preprocessing,
            ).imagePath
        }.map(::File)
        val pdfFile = File(sessionDir, options.displayName.ifBlank { "scan-session.pdf" })
        PDDocument().use { document ->
            processedImages.forEach { file ->
                val bitmap = BitmapFactory.decodeFile(file.absolutePath) ?: return@forEach
                val page = PDPage(PDRectangle(bitmap.width.toFloat(), bitmap.height.toFloat()))
                document.addPage(page)
                PDPageContentStream(document, page).use { stream ->
                    val image = LosslessFactory.createFromImage(document, bitmap)
                    stream.drawImage(image, 0f, 0f, page.mediaBox.width, page.mediaBox.height)
                }
            }
            document.documentInformation.keywords = "Searchable scan session"
            document.save(pdfFile)
        }
        ocrJobPipeline.enqueue(
            documentKey = pdfFile.absolutePath,
            jobs = processedImages.mapIndexed { index, file -> QueuedOcrPage(pageIndex = index, imagePath = file.absolutePath) },
            settings = options.ocrSettings,
        )
        return OpenDocumentRequest.FromFile(pdfFile.absolutePath, displayNameOverride = pdfFile.name)
    }
}
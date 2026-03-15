package com.aymanelbanhawy.editor.core.workflow

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.aymanelbanhawy.editor.core.model.AnnotationExportMode
import com.aymanelbanhawy.editor.core.model.DocumentModel
import com.aymanelbanhawy.editor.core.model.OpenDocumentRequest
import com.aymanelbanhawy.editor.core.ocr.OcrSessionStore
import com.aymanelbanhawy.editor.core.repository.DocumentRepository
import com.aymanelbanhawy.editor.core.scan.ScanImportOptions
import com.aymanelbanhawy.editor.core.scan.ScanImportService
import com.aymanelbanhawy.editor.core.search.IndexedPageContent
import com.aymanelbanhawy.editor.core.search.PdfBoxTextExtractionService
import com.aymanelbanhawy.editor.core.search.SearchContentSource
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.rendering.ImageType
import com.tom_roush.pdfbox.rendering.PDFRenderer as PdfBoxRenderer
import java.io.File
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

internal class DocumentFileWorkflowService(
    private val context: Context,
    private val extractionService: PdfBoxTextExtractionService,
    private val ocrSessionStore: OcrSessionStore,
    private val documentRepository: DocumentRepository,
    private val scanImportService: ScanImportService,
    private val conversionRuntime: DocumentConversionRuntime,
    private val json: Json,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {

    init {
        PDFBoxResourceLoader.init(context)
    }

    suspend fun exportDocumentAsText(document: DocumentModel, destination: File): ExportBundleResult = withContext(ioDispatcher) {
        val pages = extractIndexedContent(document)
        destination.parentFile?.mkdirs()
        val body = buildString {
            appendLine(document.documentRef.displayName)
            appendLine()
            pages.forEachIndexed { index, page ->
                appendLine("Page ${index + 1}")
                appendLine(page.pageText.ifBlank { "(No text detected)" })
                if (index != pages.lastIndex) appendLine()
            }
        }.trim()
        destination.writeText(body)
        ExportBundleResult(
            title = "Text Export",
            artifacts = listOf(ExportArtifactModel(destination.absolutePath, destination.name, "text/plain")),
        )
    }

    suspend fun exportDocumentAsMarkdown(document: DocumentModel, destination: File): ExportBundleResult = withContext(ioDispatcher) {
        val pages = extractIndexedContent(document)
        destination.parentFile?.mkdirs()
        val body = buildString {
            appendLine("# ${document.documentRef.displayName}")
            appendLine()
            pages.forEachIndexed { index, page ->
                appendLine("## Page ${index + 1}")
                appendLine()
                val blocks = page.blocks.takeIf { it.isNotEmpty() }.orEmpty()
                if (blocks.isEmpty()) {
                    appendLine(page.pageText.ifBlank { "_No text detected._" })
                } else {
                    blocks.forEach { block ->
                        appendLine(block.text.trim())
                        appendLine()
                    }
                }
            }
        }.trim()
        destination.writeText(body)
        ExportBundleResult(
            title = "Markdown Export",
            artifacts = listOf(ExportArtifactModel(destination.absolutePath, destination.name, "text/markdown")),
        )
    }

    suspend fun exportDocumentAsWord(document: DocumentModel, destination: File): ExportBundleResult = withContext(ioDispatcher) {
        val pages = extractIndexedContent(document)
        val provider = conversionRuntime.providerForExport(document)
        val artifact = provider.exportPdfToWord(document, pages, destination)
        ExportBundleResult(title = "Word Export", artifacts = listOf(artifact))
    }

    suspend fun exportCompareReport(report: CompareReportModel, destination: File, format: CompareReportExportFormat): ExportArtifactModel = withContext(ioDispatcher) {
        destination.parentFile?.mkdirs()
        when (format) {
            CompareReportExportFormat.Markdown -> destination.writeText(
                buildString {
                    appendLine("# Compare Report")
                    appendLine()
                    appendLine("${report.baselineDisplayName} vs ${report.comparedDisplayName}")
                    appendLine()
                    appendLine(report.summary.summaryText)
                    appendLine()
                    report.pageChanges.forEach { change ->
                        appendLine("## Page ${change.pageIndex + 1} � ${change.changeType.name}")
                        appendLine(change.summary)
                        if (change.addedLines.isNotEmpty()) {
                            appendLine()
                            appendLine("Added lines:")
                            change.addedLines.forEach { appendLine("- $it") }
                        }
                        if (change.removedLines.isNotEmpty()) {
                            appendLine()
                            appendLine("Removed lines:")
                            change.removedLines.forEach { appendLine("- $it") }
                        }
                        if (change.markers.isNotEmpty()) {
                            appendLine()
                            appendLine("Markers:")
                            change.markers.forEach { marker -> appendLine("- Page ${marker.pageIndex + 1}: ${marker.summary}") }
                        }
                        appendLine()
                    }
                }.trim(),
            )
            CompareReportExportFormat.Json -> destination.writeText(json.encodeToString(CompareReportModel.serializer(), report))
        }
        return@withContext ExportArtifactModel(
            path = destination.absolutePath,
            displayName = destination.name,
            mimeType = if (format == CompareReportExportFormat.Markdown) "text/markdown" else "application/json",
        )
    }

    suspend fun exportDocumentAsImages(
        document: DocumentModel,
        outputDirectory: File,
        format: ExportImageFormat,
    ): ExportBundleResult = withContext(ioDispatcher) {
        val sourceFile = File(document.documentRef.workingCopyPath)
        require(sourceFile.exists()) { "Working copy is missing for image export." }
        outputDirectory.mkdirs()
        val artifacts = mutableListOf<ExportArtifactModel>()
        val androidArtifacts = runCatching {
            exportDocumentAsImagesWithAndroidRenderer(document, sourceFile, outputDirectory, format)
        }.getOrNull().orEmpty()
        artifacts += androidArtifacts
        if (artifacts.isEmpty()) {
            artifacts += exportDocumentAsImagesWithPdfBox(document, sourceFile, outputDirectory, format)
        }
        ExportBundleResult(title = "Image Export", artifacts = artifacts)
    }

    private fun exportDocumentAsImagesWithAndroidRenderer(
        document: DocumentModel,
        sourceFile: File,
        outputDirectory: File,
        format: ExportImageFormat,
    ): List<ExportArtifactModel> {
        val artifacts = mutableListOf<ExportArtifactModel>()
        ParcelFileDescriptor.open(sourceFile, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
            PdfRenderer(descriptor).use { renderer ->
                for (pageIndex in 0 until renderer.pageCount) {
                    renderer.openPage(pageIndex).use { page ->
                        val bitmap = Bitmap.createBitmap(
                            (page.width * 2f).toInt().coerceAtLeast(1),
                            (page.height * 2f).toInt().coerceAtLeast(1),
                            Bitmap.Config.ARGB_8888,
                        )
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        artifacts += writeRenderedPage(document, outputDirectory, format, pageIndex, bitmap)
                    }
                }
            }
        }
        return artifacts
    }

    private fun exportDocumentAsImagesWithPdfBox(
        document: DocumentModel,
        sourceFile: File,
        outputDirectory: File,
        format: ExportImageFormat,
    ): List<ExportArtifactModel> {
        val artifacts = mutableListOf<ExportArtifactModel>()
        PDDocument.load(sourceFile).use { pdfDocument ->
            val renderer = PdfBoxRenderer(pdfDocument)
            for (pageIndex in 0 until pdfDocument.numberOfPages) {
                val bitmap = renderer.renderImageWithDPI(pageIndex, 144f, ImageType.RGB)
                artifacts += writeRenderedPage(document, outputDirectory, format, pageIndex, bitmap)
            }
        }
        return artifacts
    }

    private fun writeRenderedPage(
        document: DocumentModel,
        outputDirectory: File,
        format: ExportImageFormat,
        pageIndex: Int,
        bitmap: Bitmap,
    ): ExportArtifactModel {
        val extension = if (format == ExportImageFormat.Png) "png" else "jpg"
        val file = File(outputDirectory, "${document.documentRef.displayName.removeSuffix(".pdf")}_page_${pageIndex + 1}.$extension")
        try {
            file.outputStream().use { output ->
                bitmap.compress(
                    if (format == ExportImageFormat.Png) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG,
                    if (format == ExportImageFormat.Png) 100 else 88,
                    output,
                )
            }
        } finally {
            bitmap.recycle()
        }
        return ExportArtifactModel(
            path = file.absolutePath,
            displayName = file.name,
            mimeType = if (format == ExportImageFormat.Png) "image/png" else "image/jpeg",
            pageIndex = pageIndex,
        )
    }

    suspend fun createPdfFromImages(imageFiles: List<File>, displayName: String): CreatedPdfResult = withContext(ioDispatcher) {
        val request = scanImportService.importImages(
            imageFiles = imageFiles,
            options = ScanImportOptions(displayName = displayName),
        )
        CreatedPdfResult(request = request, sourceImageCount = imageFiles.size)
    }

    suspend fun importSourceAsPdf(source: File, displayName: String): ImportedPdfResult = withContext(ioDispatcher) {
        val pdfFile = File(context.cacheDir, "imports/${displayName.removeSuffix(".pdf")}-${source.nameWithoutExtension}.pdf")
        val provider = conversionRuntime.providerForImport(source)
        provider.importSourceToPdf(source, pdfFile, displayName)
        ImportedPdfResult(
            request = OpenDocumentRequest.FromFile(pdfFile.absolutePath, displayNameOverride = pdfFile.name),
            sourceFormat = source.toImportFormat(),
        )
    }

    suspend fun mergeSourcesAsPdf(sources: List<File>, displayName: String): ImportedPdfResult = withContext(ioDispatcher) {
        require(sources.isNotEmpty()) { "At least one source is required." }
        val mergedFile = File(context.cacheDir, "exports/${displayName.removeSuffix(".pdf")}.pdf")
        mergedFile.parentFile?.mkdirs()
        val normalizedPdfs = sources.mapIndexed { index, source ->
            if (source.extension.equals("pdf", ignoreCase = true)) {
                source
            } else {
                val tempPdf = File(context.cacheDir, "imports/merge-${index + 1}-${source.nameWithoutExtension}.pdf")
                conversionRuntime.providerForImport(source).importSourceToPdf(source, tempPdf, source.nameWithoutExtension)
                tempPdf
            }
        }
        PDDocument().use { output ->
            normalizedPdfs.forEach { pdf ->
                PDDocument.load(pdf).use { input ->
                    for (pageIndex in 0 until input.numberOfPages) {
                        output.importPage(input.getPage(pageIndex))
                    }
                }
            }
            output.save(mergedFile)
        }
        ImportedPdfResult(
            request = OpenDocumentRequest.FromFile(mergedFile.absolutePath, displayNameOverride = mergedFile.name),
            sourceFormat = if (sources.all { it.extension.equals("pdf", ignoreCase = true) }) DocumentImportFormat.Pdf else DocumentImportFormat.Docx,
        )
    }

    suspend fun optimizeDocument(
        document: DocumentModel,
        destination: File,
        preset: PdfOptimizationPreset,
    ): OptimizationResult = withContext(ioDispatcher) {
        destination.parentFile?.mkdirs()
        val original = File(document.documentRef.workingCopyPath)
        val originalSize = original.takeIf { it.exists() }?.length() ?: 0L
        val mode = when (preset) {
            PdfOptimizationPreset.HighQuality -> AnnotationExportMode.Editable
            PdfOptimizationPreset.Balanced -> AnnotationExportMode.Flatten
            PdfOptimizationPreset.SmallSize -> AnnotationExportMode.Flatten
            PdfOptimizationPreset.ArchivalSafe -> AnnotationExportMode.Editable
        }
        documentRepository.saveAs(document, destination, mode)
        return@withContext OptimizationResult(
            destination = destination,
            preset = preset,
            originalSizeBytes = originalSize,
            optimizedSizeBytes = destination.length(),
        )
    }

    private suspend fun extractIndexedContent(document: DocumentModel): List<IndexedPageContent> {
        val extractedPages = extractionService.extract(document.documentRef).associateBy { it.pageIndex }
        val ocrPages = ocrSessionStore.load(document.documentRef)?.pages.orEmpty().associateBy { it.pageIndex }
        val maxIndex = maxOf(
            extractedPages.keys.maxOrNull() ?: -1,
            ocrPages.keys.maxOrNull() ?: -1,
            document.pages.lastIndex,
        )
        if (maxIndex < 0) {
            return emptyList()
        }
        return (0..maxIndex).map { pageIndex ->
            val extracted = extractedPages[pageIndex]
            val ocr = ocrPages[pageIndex]
            when {
                extracted != null && extracted.pageText.isNotBlank() -> extracted
                ocr != null -> IndexedPageContent(
                    pageIndex = pageIndex,
                    pageText = ocr.text.ifBlank { ocr.blocks.joinToString("\n") { it.text } },
                    blocks = ocr.flattenedSearchBlocks(),
                    source = SearchContentSource.Ocr,
                )
                extracted != null -> extracted
                else -> IndexedPageContent(
                    pageIndex = pageIndex,
                    pageText = "",
                    blocks = emptyList(),
                    source = SearchContentSource.EmbeddedText,
                )
            }
        }
    }

    private fun File.toImportFormat(): DocumentImportFormat = when (extension.lowercase()) {
        "pdf" -> DocumentImportFormat.Pdf
        "docx" -> DocumentImportFormat.Docx
        "txt" -> DocumentImportFormat.Text
        "md", "markdown" -> DocumentImportFormat.Markdown
        else -> DocumentImportFormat.Image
    }
}


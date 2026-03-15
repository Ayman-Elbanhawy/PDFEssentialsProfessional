
package com.aymanelbanhawy.editor.core.write

import android.content.Context
import android.graphics.BitmapFactory
import com.aymanelbanhawy.editor.core.data.SearchIndexDao
import com.aymanelbanhawy.editor.core.model.AnnotationExportMode
import com.aymanelbanhawy.editor.core.model.AnnotationModel
import com.aymanelbanhawy.editor.core.model.AnnotationType
import com.aymanelbanhawy.editor.core.model.DocumentModel
import com.aymanelbanhawy.editor.core.model.DocumentSourceType
import com.aymanelbanhawy.editor.core.model.FontFamilyToken
import com.aymanelbanhawy.editor.core.model.ImageEditModel
import com.aymanelbanhawy.editor.core.model.NormalizedPoint
import com.aymanelbanhawy.editor.core.model.NormalizedRect
import com.aymanelbanhawy.editor.core.model.PageContentType
import com.aymanelbanhawy.editor.core.model.PageEditModel
import com.aymanelbanhawy.editor.core.model.PageModel
import com.aymanelbanhawy.editor.core.model.PdfDocumentRef
import com.aymanelbanhawy.editor.core.model.TextAlignment
import com.aymanelbanhawy.editor.core.model.TextBoxEditModel
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import com.tom_roush.pdfbox.util.Matrix
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

data class PdfMutationEditableState(
    val editObjectsByPage: Map<Int, List<PageEditModel>>,
    val annotationsByPage: Map<Int, List<AnnotationModel>>,
    val integrityStatus: MutationIntegrityStatus?,
)

interface PdfWriteEngine {
    suspend fun load(documentRef: PdfDocumentRef): PdfMutationEditableState
    suspend fun persist(
        document: DocumentModel,
        destinationPdf: File,
        exportMode: AnnotationExportMode,
        strategy: SaveStrategy = SaveStrategy.IncrementalPreferred,
    ): PdfMutationSaveResult
}

enum class SaveStrategy {
    IncrementalPreferred,
    FullRewrite,
    SaveAs,
    ExportCopy,
}

enum class SaveExecutionMode {
    Incremental,
    FullRewrite,
    SaveAs,
    ExportCopy,
}

enum class MutationIntegrityStatus {
    Verified,
    RecoveredFromBackup,
    LegacyMigrated,
}

@Serializable
data class PdfMutationTransactionEntry(
    val id: String,
    val kind: String,
    val summary: String,
    val pageIndices: List<Int> = emptyList(),
    val createdAtEpochMillis: Long,
)

@Serializable
data class PdfMutationTransactionLog(
    val transactionId: String,
    val documentKey: String,
    val destinationPath: String,
    val strategy: SaveStrategy,
    val executionMode: SaveExecutionMode,
    val entries: List<PdfMutationTransactionEntry>,
    val pdfSha256: String,
    val sessionSha256: String,
    val createdAtEpochMillis: Long,
)

@Serializable
data class PdfMutationSessionPayload(
    val schemaVersion: Int,
    val documentKey: String,
    val exportMode: AnnotationExportMode,
    val editObjects: List<PageEditModel>,
    val annotations: List<AnnotationModel> = emptyList(),
    val transactionId: String,
    val updatedAtEpochMillis: Long,
    val integrity: MutationIntegrityStatus,
    val checksumSha256: String,
)

data class PdfMutationSaveResult(
    val destinationPdf: File,
    val strategyRequested: SaveStrategy,
    val executionMode: SaveExecutionMode,
    val transactionLogFile: File,
    val sessionFile: File,
    val pdfSha256: String,
    val sessionSha256: String,
    val integrityStatus: MutationIntegrityStatus,
    val structuralMutationApplied: Boolean,
)

interface MutationInvalidationHooks {
    suspend fun invalidateThumbnails(document: DocumentModel)
    suspend fun invalidateSearchIndex(documentKey: String)
}

class FilesystemMutationInvalidationHooks(
    private val context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : MutationInvalidationHooks {
    override suspend fun invalidateThumbnails(document: DocumentModel) {
        withContext(ioDispatcher) {
            File(context.cacheDir, "organize-thumbnails/${document.sessionId}").deleteRecursively()
            File(context.cacheDir, "organize-thumbnails/${document.documentRef.sourceKey.hashCode()}").deleteRecursively()
        }
    }

    override suspend fun invalidateSearchIndex(documentKey: String) {
        withContext(ioDispatcher) {
            val markerDir = File(context.cacheDir, "search-index-invalidations").apply { mkdirs() }
            File(markerDir, "${documentKey.hashCode()}.marker").writeText(System.currentTimeMillis().toString())
        }
    }
}

class RoomMutationInvalidationHooks(
    context: Context,
    private val searchIndexDao: SearchIndexDao,
    ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : MutationInvalidationHooks {
    private val filesystemHooks = FilesystemMutationInvalidationHooks(context, ioDispatcher)

    override suspend fun invalidateThumbnails(document: DocumentModel) {
        filesystemHooks.invalidateThumbnails(document)
    }

    override suspend fun invalidateSearchIndex(documentKey: String) {
        searchIndexDao.deleteForDocument(documentKey)
        filesystemHooks.invalidateSearchIndex(documentKey)
    }
}

class PdfBoxWriteEngine(
    private val context: Context,
    private val invalidationHooks: MutationInvalidationHooks = FilesystemMutationInvalidationHooks(context),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val json: Json = Json { ignoreUnknownKeys = true; encodeDefaults = true; classDiscriminator = "_type" },
) : PdfWriteEngine {

    init {
        PDFBoxResourceLoader.init(context)
    }

    override suspend fun load(documentRef: PdfDocumentRef): PdfMutationEditableState = withContext(ioDispatcher) {
        val sessionFile = resolveMutationSession(documentRef)
        val backupSessionFile = sessionFile?.backupFile()
        val recovered = readSession(sessionFile)
            ?: readSession(backupSessionFile)?.let { (payload, _) ->
                payload.copy(integrity = MutationIntegrityStatus.RecoveredFromBackup) to MutationIntegrityStatus.RecoveredFromBackup
            }
            ?: return@withContext PdfMutationEditableState(emptyMap(), emptyMap(), null)
        PdfMutationEditableState(
            editObjectsByPage = recovered.first.editObjects.groupBy { it.pageIndex },
            annotationsByPage = recovered.first.annotations.groupBy { it.pageIndex },
            integrityStatus = recovered.second,
        )
    }

    override suspend fun persist(
        document: DocumentModel,
        destinationPdf: File,
        exportMode: AnnotationExportMode,
        strategy: SaveStrategy,
    ): PdfMutationSaveResult = withContext(ioDispatcher) {
        destinationPdf.parentFile?.mkdirs()
        val lockFile = File(destinationPdf.absolutePath + LOCK_SUFFIX)
        FileOutputStream(lockFile, true).channel.use { channel ->
            channel.lock().use {
                val structuralMutationApplied = hasStructuralMutations(document, destinationPdf)
                val executionMode = selectExecutionMode(strategy, structuralMutationApplied, destinationPdf)
                val sourcePdf = when {
                    destinationPdf.exists() -> destinationPdf
                    document.documentRef.workingCopyPath.isNotBlank() -> File(document.documentRef.workingCopyPath)
                    else -> destinationPdf
                }
                val transactionId = UUID.randomUUID().toString()
                val tempPdf = if (executionMode == SaveExecutionMode.Incremental && destinationPdf.exists()) destinationPdf else File(destinationPdf.parentFile, destinationPdf.name + TEMP_SUFFIX)
                val backupPdf = File(destinationPdf.absolutePath + BACKUP_SUFFIX)
                val sessionFile = File(destinationPdf.absolutePath + SESSION_SUFFIX)
                val transactionFile = File(destinationPdf.absolutePath + TRANSACTION_SUFFIX)
                val sessionBackup = sessionFile.backupFile()
                val transactionBackup = transactionFile.backupFile()
                if (destinationPdf.exists() && tempPdf != destinationPdf) {
                    destinationPdf.copyTo(backupPdf, overwrite = true)
                }
                backupFile(sessionFile)
                backupFile(transactionFile)
                try {
                    when (executionMode) {
                        SaveExecutionMode.Incremental -> applyIncremental(document, destinationPdf, exportMode)
                        SaveExecutionMode.FullRewrite, SaveExecutionMode.SaveAs, SaveExecutionMode.ExportCopy -> rebuildAndWrite(document, sourcePdf, tempPdf, exportMode)
                    }
                    if (tempPdf != destinationPdf) {
                        tempPdf.copyTo(destinationPdf, overwrite = true)
                        tempPdf.delete()
                    }
                    val entries = buildTransactionEntries(document, exportMode, structuralMutationApplied)
                    val sessionPayload = buildSessionPayload(document, exportMode, transactionId)
                    val sessionJson = json.encodeToString(PdfMutationSessionPayload.serializer(), sessionPayload)
                    val sessionSha = sha256(sessionJson)
                    sessionFile.writeText(sessionJson)
                    verifySessionFile(sessionFile, sessionPayload)
                    val pdfSha = sha256(destinationPdf)
                    val transactionLog = PdfMutationTransactionLog(
                        transactionId = transactionId,
                        documentKey = document.documentRef.sourceKey,
                        destinationPath = destinationPdf.absolutePath,
                        strategy = strategy,
                        executionMode = executionMode,
                        entries = entries,
                        pdfSha256 = pdfSha,
                        sessionSha256 = sessionSha,
                        createdAtEpochMillis = System.currentTimeMillis(),
                    )
                    transactionFile.writeText(json.encodeToString(PdfMutationTransactionLog.serializer(), transactionLog))
                    cleanupBackup(sessionBackup)
                    cleanupBackup(transactionBackup)
                    cleanupBackup(backupPdf)
                    if (structuralMutationApplied) {
                        invalidationHooks.invalidateThumbnails(document)
                        invalidationHooks.invalidateSearchIndex(document.documentRef.sourceKey)
                    }
                    PdfMutationSaveResult(
                        destinationPdf = destinationPdf,
                        strategyRequested = strategy,
                        executionMode = executionMode,
                        transactionLogFile = transactionFile,
                        sessionFile = sessionFile,
                        pdfSha256 = pdfSha,
                        sessionSha256 = sessionSha,
                        integrityStatus = sessionPayload.integrity,
                        structuralMutationApplied = structuralMutationApplied,
                    )
                } catch (throwable: Throwable) {
                    restoreFileFromBackup(backupPdf, destinationPdf)
                    restoreFileFromBackup(sessionBackup, sessionFile)
                    restoreFileFromBackup(transactionBackup, transactionFile)
                    tempPdf.takeIf { it != destinationPdf && it.exists() }?.delete()
                    throw throwable
                } finally {
                    lockFile.takeIf { it.exists() }?.delete()
                }
            }
        }
    }

    private fun readSession(file: File?): Pair<PdfMutationSessionPayload, MutationIntegrityStatus>? {
        if (file == null || !file.exists()) return null
        return runCatching {
            val payload = json.decodeFromString(PdfMutationSessionPayload.serializer(), file.readText())
            val expectedChecksum = checksumFor(payload.editObjects, payload.annotations)
            if (expectedChecksum != payload.checksumSha256) return null
            payload to payload.integrity
        }.getOrNull()
    }
    private fun selectExecutionMode(strategy: SaveStrategy, structuralMutationApplied: Boolean, destinationPdf: File): SaveExecutionMode {
        return when (strategy) {
            SaveStrategy.SaveAs -> SaveExecutionMode.SaveAs
            SaveStrategy.ExportCopy -> SaveExecutionMode.ExportCopy
            SaveStrategy.FullRewrite -> SaveExecutionMode.FullRewrite
            SaveStrategy.IncrementalPreferred -> if (!structuralMutationApplied && destinationPdf.exists()) SaveExecutionMode.Incremental else SaveExecutionMode.FullRewrite
        }
    }

    private fun hasStructuralMutations(document: DocumentModel, destinationPdf: File): Boolean {
        if (!destinationPdf.exists()) return true
        val currentPageCount = runCatching { PDDocument.load(destinationPdf).use { it.numberOfPages } }.getOrDefault(-1)
        if (currentPageCount != document.pages.size) return true
        return document.pages.anyIndexed { index, page ->
            page.contentType != PageContentType.Pdf ||
                page.sourcePageIndex != index ||
                (page.sourceDocumentPath.isNotBlank() && page.sourceDocumentPath != destinationPdf.absolutePath) ||
                page.rotationDegrees != loadRotation(destinationPdf, index)
        }
    }

    private fun loadRotation(file: File, pageIndex: Int): Int {
        return runCatching { PDDocument.load(file).use { it.getPage(pageIndex).rotation } }.getOrDefault(0)
    }

    private fun applyIncremental(document: DocumentModel, destinationPdf: File, exportMode: AnnotationExportMode) {
        PDDocument.load(destinationPdf).use { pdDocument ->
            applyPageRotations(pdDocument, document.pages)
            applyEditObjects(pdDocument, document.pages)
            if (exportMode == AnnotationExportMode.Flatten) {
                flattenAnnotations(pdDocument, document.pages)
            }
            if (!tryIncrementalSave(pdDocument, destinationPdf)) {
                pdDocument.save(destinationPdf)
            }
        }
    }

    private fun rebuildAndWrite(document: DocumentModel, sourcePdf: File, destinationPdf: File, exportMode: AnnotationExportMode) {
        PDDocument().use { output ->
            val sourceDocuments = mutableMapOf<String, PDDocument>()
            try {
                document.pages.forEach { page ->
                    when (page.contentType) {
                        PageContentType.Pdf -> {
                            val sourcePath = page.sourceDocumentPath.ifBlank { sourcePdf.absolutePath }
                            val sourceDocument = sourceDocuments.getOrPut(sourcePath) { PDDocument.load(File(sourcePath)) }
                            val imported = output.importPage(sourceDocument.getPage(page.sourcePageIndex.coerceAtLeast(0)))
                            imported.rotation = page.rotationDegrees
                        }
                        PageContentType.Blank -> {
                            val blankPage = PDPage(PDRectangle(page.widthPoints, page.heightPoints))
                            blankPage.rotation = page.rotationDegrees
                            output.addPage(blankPage)
                        }
                        PageContentType.Image -> {
                            val mediaBox = PDRectangle(page.widthPoints, page.heightPoints)
                            val imagePage = PDPage(mediaBox)
                            imagePage.rotation = page.rotationDegrees
                            output.addPage(imagePage)
                            page.insertedImagePath?.let { imagePath ->
                                val bitmap = BitmapFactory.decodeFile(imagePath)
                                if (bitmap != null) {
                                    PDPageContentStream(output, imagePage).use { contentStream ->
                                        val image = LosslessFactory.createFromImage(output, bitmap)
                                        contentStream.drawImage(image, 0f, 0f, mediaBox.width, mediaBox.height)
                                    }
                                }
                            }
                        }
                    }
                }
                applyEditObjects(output, document.pages)
                if (exportMode == AnnotationExportMode.Flatten) {
                    flattenAnnotations(output, document.pages)
                }
                output.save(destinationPdf)
            } finally {
                sourceDocuments.values.forEach { it.close() }
            }
        }
    }

    private fun applyPageRotations(document: PDDocument, pages: List<PageModel>) {
        pages.forEachIndexed { index, page ->
            if (index in 0 until document.numberOfPages) {
                document.getPage(index).rotation = page.rotationDegrees
            }
        }
    }

    private fun applyEditObjects(pdDocument: PDDocument, pages: List<PageModel>) {
        pages.forEach { page ->
            if (page.editObjects.isEmpty() || page.index !in 0 until pdDocument.numberOfPages) return@forEach
            val pdfPage = pdDocument.getPage(page.index)
            PDPageContentStream(pdDocument, pdfPage, PDPageContentStream.AppendMode.APPEND, true, true).use { stream ->
                page.editObjects.forEach { editObject ->
                    when (editObject) {
                        is TextBoxEditModel -> drawTextEdit(stream, pdfPage.mediaBox.width, pdfPage.mediaBox.height, editObject)
                        is ImageEditModel -> drawImageEdit(pdDocument, stream, pdfPage.mediaBox.width, pdfPage.mediaBox.height, editObject)
                    }
                }
            }
        }
    }
    private fun flattenAnnotations(pdDocument: PDDocument, pages: List<PageModel>) {
        pages.forEach { page ->
            if (page.annotations.isEmpty() || page.index !in 0 until pdDocument.numberOfPages) return@forEach
            val pdfPage = pdDocument.getPage(page.index)
            PDPageContentStream(pdDocument, pdfPage, PDPageContentStream.AppendMode.APPEND, true, true).use { stream ->
                page.annotations.forEach { annotation -> drawAnnotation(stream, pdfPage.mediaBox, annotation) }
            }
        }
    }

    private fun drawAnnotation(stream: PDPageContentStream, mediaBox: PDRectangle, annotation: AnnotationModel) {
        val bounds = toPdfRect(mediaBox.width, mediaBox.height, annotation.bounds)
        val stroke = parseColor(annotation.strokeColorHex)
        stream.saveGraphicsState()
        stream.setStrokingColor(stroke[0], stroke[1], stroke[2])
        when (annotation.type) {
            AnnotationType.Highlight -> {
                val fill = parseColor(annotation.fillColorHex ?: annotation.strokeColorHex)
                stream.setNonStrokingColor(fill[0], fill[1], fill[2])
                stream.addRect(bounds.left, bounds.bottom, bounds.width, bounds.height)
                stream.fill()
            }
            AnnotationType.Underline -> {
                stream.setLineWidth(annotation.strokeWidth * mediaBox.width)
                stream.moveTo(bounds.left, bounds.bottom)
                stream.lineTo(bounds.right, bounds.bottom)
                stream.stroke()
            }
            AnnotationType.Strikeout -> {
                stream.setLineWidth(annotation.strokeWidth * mediaBox.width)
                val y = bounds.bottom + (bounds.height / 2f)
                stream.moveTo(bounds.left, y)
                stream.lineTo(bounds.right, y)
                stream.stroke()
            }
            AnnotationType.Rectangle -> {
                annotation.fillColorHex?.let {
                    val fill = parseColor(it)
                    stream.setNonStrokingColor(fill[0], fill[1], fill[2])
                    stream.addRect(bounds.left, bounds.bottom, bounds.width, bounds.height)
                    stream.fill()
                }
                stream.addRect(bounds.left, bounds.bottom, bounds.width, bounds.height)
                stream.stroke()
            }
            AnnotationType.Ellipse -> drawEllipse(stream, bounds)
            AnnotationType.Line, AnnotationType.Arrow -> {
                val points = annotation.points.takeIf { it.size >= 2 }
                    ?: listOf(annotation.bounds.center, NormalizedPoint(annotation.bounds.right, annotation.bounds.bottom))
                drawLine(stream, mediaBox, points.first(), points.last(), annotation.type == AnnotationType.Arrow)
            }
            AnnotationType.FreehandInk -> drawInk(stream, mediaBox, annotation.points)
            AnnotationType.StickyNote -> {
                stream.addRect(bounds.left, bounds.bottom, bounds.width, bounds.height)
                stream.stroke()
            }
            AnnotationType.TextBox -> drawAnnotationText(stream, bounds, annotation)
        }
        stream.restoreGraphicsState()
    }

    private fun drawEllipse(stream: PDPageContentStream, bounds: PdfRect) {
        val k = 0.552284749831f
        val ox = (bounds.width / 2f) * k
        val oy = (bounds.height / 2f) * k
        val xe = bounds.left + bounds.width
        val ye = bounds.bottom + bounds.height
        val xm = bounds.left + bounds.width / 2f
        val ym = bounds.bottom + bounds.height / 2f
        stream.moveTo(bounds.left, ym)
        stream.curveTo(bounds.left, ym + oy, xm - ox, ye, xm, ye)
        stream.curveTo(xm + ox, ye, xe, ym + oy, xe, ym)
        stream.curveTo(xe, ym - oy, xm + ox, bounds.bottom, xm, bounds.bottom)
        stream.curveTo(xm - ox, bounds.bottom, bounds.left, ym - oy, bounds.left, ym)
        stream.stroke()
    }

    private fun drawLine(stream: PDPageContentStream, mediaBox: PDRectangle, start: NormalizedPoint, end: NormalizedPoint, arrow: Boolean) {
        val startX = start.x * mediaBox.width
        val startY = mediaBox.height - (start.y * mediaBox.height)
        val endX = end.x * mediaBox.width
        val endY = mediaBox.height - (end.y * mediaBox.height)
        stream.moveTo(startX, startY)
        stream.lineTo(endX, endY)
        stream.stroke()
        if (arrow) {
            val angle = kotlin.math.atan2((endY - startY).toDouble(), (endX - startX).toDouble())
            val arrowLength = 12f
            val leftX = endX - arrowLength * kotlin.math.cos(angle - Math.PI / 6).toFloat()
            val leftY = endY - arrowLength * kotlin.math.sin(angle - Math.PI / 6).toFloat()
            val rightX = endX - arrowLength * kotlin.math.cos(angle + Math.PI / 6).toFloat()
            val rightY = endY - arrowLength * kotlin.math.sin(angle + Math.PI / 6).toFloat()
            stream.moveTo(endX, endY)
            stream.lineTo(leftX, leftY)
            stream.moveTo(endX, endY)
            stream.lineTo(rightX, rightY)
            stream.stroke()
        }
    }

    private fun drawInk(stream: PDPageContentStream, mediaBox: PDRectangle, points: List<NormalizedPoint>) {
        if (points.size < 2) return
        val first = points.first()
        stream.moveTo(first.x * mediaBox.width, mediaBox.height - (first.y * mediaBox.height))
        points.drop(1).forEach { point ->
            stream.lineTo(point.x * mediaBox.width, mediaBox.height - (point.y * mediaBox.height))
        }
        stream.stroke()
    }

    private fun drawAnnotationText(stream: PDPageContentStream, bounds: PdfRect, annotation: AnnotationModel) {
        val font = PDType1Font.HELVETICA
        val lines = annotation.text.ifBlank { annotation.commentThread.subject.ifBlank { "Text" } }.split("\n")
        var y = bounds.top - annotation.fontSizeSp
        lines.forEach { line ->
            stream.beginText()
            stream.setFont(font, annotation.fontSizeSp)
            stream.newLineAtOffset(bounds.left, y)
            stream.showText(line)
            stream.endText()
            y -= annotation.fontSizeSp * 1.2f
        }
    }

    private fun tryIncrementalSave(document: PDDocument, destinationPdf: File): Boolean {
        return runCatching {
            val method = PDDocument::class.java.methods.firstOrNull { it.name == "saveIncremental" && it.parameterTypes.size == 1 }
                ?: return false
            FileOutputStream(destinationPdf, true).use { output ->
                method.invoke(document, output)
            }
            true
        }.getOrDefault(false)
    }

    private fun drawTextEdit(stream: PDPageContentStream, pageWidth: Float, pageHeight: Float, edit: TextBoxEditModel) {
        val bounds = toPdfRect(pageWidth, pageHeight, edit.bounds)
        val font = when (edit.fontFamily) {
            FontFamilyToken.Sans -> PDType1Font.HELVETICA
            FontFamilyToken.Serif -> PDType1Font.TIMES_ROMAN
            FontFamilyToken.Monospace -> PDType1Font.COURIER
        }
        val lines = edit.text.split("\n")
        val lineHeight = edit.fontSizeSp * edit.lineSpacingMultiplier
        val color = parseColor(edit.textColorHex)
        stream.saveGraphicsState()
        stream.setNonStrokingColor(color[0], color[1], color[2])
        var currentY = bounds.top - edit.fontSizeSp
        lines.forEach { line ->
            val lineWidth = font.getStringWidth(line) / 1000f * edit.fontSizeSp
            val x = when (edit.alignment) {
                TextAlignment.Start -> bounds.left
                TextAlignment.Center -> bounds.left + ((bounds.width - lineWidth) / 2f)
                TextAlignment.End -> bounds.right - lineWidth
            }
            stream.beginText()
            stream.setFont(font, edit.fontSizeSp)
            stream.setTextMatrix(Matrix.getRotateInstance(Math.toRadians(edit.rotationDegrees.toDouble()), x, currentY))
            stream.showText(line)
            stream.endText()
            currentY -= lineHeight
        }
        stream.restoreGraphicsState()
    }

    private fun drawImageEdit(document: PDDocument, stream: PDPageContentStream, pageWidth: Float, pageHeight: Float, edit: ImageEditModel) {
        val bitmap = BitmapFactory.decodeFile(edit.imagePath) ?: return
        val image = LosslessFactory.createFromImage(document, bitmap)
        val bounds = toPdfRect(pageWidth, pageHeight, edit.bounds)
        stream.saveGraphicsState()
        val matrix = Matrix()
        matrix.translate(bounds.left + bounds.width / 2f, bounds.bottom + bounds.height / 2f)
        matrix.rotate(Math.toRadians(edit.rotationDegrees.toDouble()))
        matrix.translate(-bounds.width / 2f, -bounds.height / 2f)
        stream.transform(matrix)
        stream.drawImage(image, 0f, 0f, bounds.width, bounds.height)
        stream.restoreGraphicsState()
    }
    private fun toPdfRect(pageWidth: Float, pageHeight: Float, rect: NormalizedRect): PdfRect {
        val left = rect.left * pageWidth
        val width = rect.width * pageWidth
        val height = rect.height * pageHeight
        val bottom = pageHeight - (rect.bottom * pageHeight)
        return PdfRect(left, bottom, width, height)
    }

    private fun parseColor(colorHex: String): FloatArray {
        val color = android.graphics.Color.parseColor(colorHex)
        return floatArrayOf(
            android.graphics.Color.red(color) / 255f,
            android.graphics.Color.green(color) / 255f,
            android.graphics.Color.blue(color) / 255f,
        )
    }

    private fun buildSessionPayload(
        document: DocumentModel,
        exportMode: AnnotationExportMode,
        transactionId: String,
    ): PdfMutationSessionPayload {
        val editObjects = document.pages.flatMap { page -> page.editObjects.map { it.withPage(page.index) } }
        val annotations = document.pages.flatMap { page -> page.annotations.map { it.withPage(page.index) } }
        val checksum = checksumFor(editObjects, annotations)
        val integrity = MutationIntegrityStatus.Verified
        return PdfMutationSessionPayload(
            schemaVersion = SCHEMA_VERSION,
            documentKey = document.documentRef.sourceKey,
            exportMode = exportMode,
            editObjects = editObjects,
            annotations = annotations,
            transactionId = transactionId,
            updatedAtEpochMillis = System.currentTimeMillis(),
            integrity = integrity,
            checksumSha256 = checksum,
        )
    }

    private fun buildTransactionEntries(
        document: DocumentModel,
        exportMode: AnnotationExportMode,
        structuralMutationApplied: Boolean,
    ): List<PdfMutationTransactionEntry> {
        val now = System.currentTimeMillis()
        val entries = mutableListOf<PdfMutationTransactionEntry>()
        if (structuralMutationApplied) {
            entries += PdfMutationTransactionEntry(UUID.randomUUID().toString(), "structural", "Applied structural page mutations", document.pages.map { it.index }, now)
        }

        val textCount = document.pages.sumOf { page -> page.editObjects.count { it is TextBoxEditModel } }
        if (textCount > 0) {
            entries += PdfMutationTransactionEntry(UUID.randomUUID().toString(), "text", "Persisted $textCount text edit objects", document.pages.filter { page -> page.editObjects.any { it is TextBoxEditModel } }.map { it.index }, now)
        }
        val imageCount = document.pages.sumOf { page -> page.editObjects.count { it is ImageEditModel } }
        if (imageCount > 0) {
            entries += PdfMutationTransactionEntry(UUID.randomUUID().toString(), "image", "Persisted $imageCount image edit objects", document.pages.filter { page -> page.editObjects.any { it is ImageEditModel } }.map { it.index }, now)
        }
        if (exportMode == AnnotationExportMode.Flatten) {
            val flattenedPages = document.pages.filter { it.annotations.isNotEmpty() }.map { it.index }
            if (flattenedPages.isNotEmpty()) {
                entries += PdfMutationTransactionEntry(UUID.randomUUID().toString(), "annotation", "Flattened annotations into PDF", flattenedPages, now)
            }
        }
        return entries
    }

    private fun verifySessionFile(sessionFile: File, sessionPayload: PdfMutationSessionPayload) {
        val readBack = readSession(sessionFile)?.first ?: error("Mutation session could not be reloaded after save.")
        require(readBack.checksumSha256 == sessionPayload.checksumSha256) { "Mutation session checksum mismatch." }
    }
    private fun backupFile(file: File) {
        if (file.exists()) {
            file.copyTo(file.backupFile(), overwrite = true)
        }
    }

    private fun restoreFileFromBackup(backup: File, target: File) {
        when {
            backup.exists() -> backup.copyTo(target, overwrite = true)
            target.exists() -> target.delete()
        }
    }

    private fun cleanupBackup(file: File) {
        file.takeIf { it.exists() }?.delete()
    }

    private fun resolveMutationSession(documentRef: PdfDocumentRef): File? {
        val targetFile = resolveTargetPdf(documentRef)
        val mutation = File(targetFile.absolutePath + SESSION_SUFFIX)
        return mutation.takeIf { it.exists() || it.parentFile?.exists() == true }
    }
    private fun resolveTargetPdf(documentRef: PdfDocumentRef): File {
        return when (documentRef.sourceType) {
            DocumentSourceType.File -> File(documentRef.sourceKey)
            DocumentSourceType.Uri, DocumentSourceType.Asset, DocumentSourceType.Memory -> File(documentRef.workingCopyPath)
        }
    }

    private fun sha256(file: File): String = FileInputStream(file).use { input ->
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val read = input.read(buffer)
            if (read <= 0) break
            digest.update(buffer, 0, read)
        }
        digest.digest().joinToString(separator = "") { "%02x".format(it) }
    }

    private fun sha256(value: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(StandardCharsets.UTF_8))
        return bytes.joinToString(separator = "") { "%02x".format(it) }
    }

    private fun checksumFor(editObjects: List<PageEditModel>, annotations: List<AnnotationModel>): String {
        val editJson = json.encodeToString(ListSerializer(PageEditModel.serializer()), editObjects)
        val annotationJson = json.encodeToString(ListSerializer(AnnotationModel.serializer()), annotations)
        return sha256("$editJson|$annotationJson")
    }

    private fun File.backupFile(): File = File(absolutePath + BACKUP_SUFFIX)

    companion object {
        private const val SCHEMA_VERSION = 3
        private const val SESSION_SUFFIX = ".mutations.json"
        private const val TRANSACTION_SUFFIX = ".mutationlog.json"
        private const val LOCK_SUFFIX = ".saving.lock"
        private const val TEMP_SUFFIX = ".tmp"
        private const val BACKUP_SUFFIX = ".bak"
    }
}

private data class PdfRect(
    val left: Float,
    val bottom: Float,
    val width: Float,
    val height: Float,
) {
    val right: Float get() = left + width
    val top: Float get() = bottom + height
}


private inline fun <T> Iterable<T>.anyIndexed(predicate: (Int, T) -> Boolean): Boolean {
    var index = 0
    for (item in this) {
        if (predicate(index, item)) return true
        index += 1
    }
    return false
}
























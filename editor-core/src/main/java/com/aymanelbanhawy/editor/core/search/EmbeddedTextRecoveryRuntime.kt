package com.aymanelbanhawy.editor.core.search

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.aymanelbanhawy.editor.core.model.PdfDocumentRef
import com.aymanelbanhawy.editor.core.ocr.MlKitOcrEngine
import com.aymanelbanhawy.editor.core.ocr.OcrEngine
import com.aymanelbanhawy.editor.core.ocr.OcrPageContent
import com.aymanelbanhawy.editor.core.ocr.OcrPageRequest
import com.aymanelbanhawy.editor.core.ocr.OcrSettingsModel
import java.io.File
import kotlin.math.roundToInt

interface EmbeddedTextRecoveryRuntime {
    suspend fun recover(documentRef: PdfDocumentRef, pages: List<IndexedPageContent>): List<OcrRecoveredPage>
}

data class OcrRecoveredPage(
    val page: OcrPageContent,
    val strategy: String,
)

internal object EmbeddedTextQualityHeuristics {
    fun analyze(text: String, blocks: List<ExtractedTextBlock>): EmbeddedTextQualityReport {
        val normalizedText = text.replace('\u0000', ' ').trim()
        val evidenceText = if (normalizedText.isNotBlank()) normalizedText else blocks.joinToString(separator = " ") { it.text.trim() }.trim()
        val compact = evidenceText.filterNot(Char::isWhitespace)
        if (compact.length < 24) {
            return EmbeddedTextQualityReport(
                looksGarbled = false,
                confidence = 0f,
                reason = "not-enough-text",
            )
        }
        val alphaCount = compact.count(Char::isLetter)
        val digitCount = compact.count(Char::isDigit)
        val punctuationCount = compact.count { !it.isLetterOrDigit() }
        val tokens = evidenceText.split(Regex("\\s+")).filter { it.isNotBlank() }
        val longTokens = tokens.filter { it.length >= 4 }
        val suspiciousTokens = longTokens.count { token ->
            val compactToken = token.filterNot(Char::isWhitespace)
            if (compactToken.isEmpty()) return@count false
            val tokenAlphaRatio = compactToken.count(Char::isLetter).toFloat() / compactToken.length.toFloat()
            val tokenPunctuationRatio = compactToken.count { !it.isLetterOrDigit() }.toFloat() / compactToken.length.toFloat()
            tokenAlphaRatio < 0.45f || tokenPunctuationRatio > 0.35f
        }
        val readableTokens = longTokens.count { token ->
            val compactToken = token.filterNot(Char::isWhitespace)
            if (compactToken.isEmpty()) return@count false
            compactToken.count(Char::isLetter).toFloat() / compactToken.length.toFloat() >= 0.7f &&
                compactToken.any { it.lowercaseChar() in setOf('a', 'e', 'i', 'o', 'u') }
        }
        val repeatedSymbolBursts = Regex("[!\"#$%&'()*+,\\-./:;<=>?@\\[\\\\\\]^_`{|}~]{3,}").findAll(evidenceText).count()
        val alphaRatio = alphaCount.toFloat() / compact.length.toFloat()
        val punctuationRatio = punctuationCount.toFloat() / compact.length.toFloat()
        val digitRatio = digitCount.toFloat() / compact.length.toFloat()
        val suspiciousTokenRatio = if (longTokens.isEmpty()) 0f else suspiciousTokens.toFloat() / longTokens.size.toFloat()
        val readableTokenRatio = if (longTokens.isEmpty()) 0f else readableTokens.toFloat() / longTokens.size.toFloat()
        val confidence = listOf(
            (0.55f - alphaRatio).coerceAtLeast(0f),
            (punctuationRatio - 0.18f).coerceAtLeast(0f),
            (suspiciousTokenRatio - 0.35f).coerceAtLeast(0f),
            (0.4f - readableTokenRatio).coerceAtLeast(0f),
            (digitRatio - 0.2f).coerceAtLeast(0f),
            (repeatedSymbolBursts / 3f).coerceAtMost(1f),
        ).average().toFloat().coerceIn(0f, 1f)
        val looksGarbled = repeatedSymbolBursts >= 2 ||
            (
                alphaRatio < 0.5f &&
                    punctuationRatio > 0.18f &&
                    suspiciousTokenRatio > 0.35f &&
                    readableTokenRatio < 0.4f
                ) ||
            (
                alphaRatio < 0.35f &&
                    punctuationRatio > 0.24f
                )
        return EmbeddedTextQualityReport(
            looksGarbled = looksGarbled,
            confidence = confidence,
            reason = "alpha=$alphaRatio,punct=$punctuationRatio,suspicious=$suspiciousTokenRatio,readable=$readableTokenRatio,bursts=$repeatedSymbolBursts",
        )
    }
}

internal data class EmbeddedTextQualityReport(
    val looksGarbled: Boolean,
    val confidence: Float,
    val reason: String,
)

class AndroidOcrEmbeddedTextRecoveryRuntime(
    context: Context,
    private val ocrEngine: OcrEngine = MlKitOcrEngine(context.applicationContext),
    private val ocrSettings: OcrSettingsModel = OcrSettingsModel(timeoutSeconds = 20, maxRetryCount = 1),
) : EmbeddedTextRecoveryRuntime {
    private val appContext = context.applicationContext

    override suspend fun recover(documentRef: PdfDocumentRef, pages: List<IndexedPageContent>): List<OcrRecoveredPage> {
        val pdfFile = File(documentRef.workingCopyPath)
        if (!pdfFile.exists() || pages.isEmpty()) return emptyList()
        val sessionDirectory = File(appContext.cacheDir, "ai-grounding-ocr/${documentRef.sourceKey.hashCode()}").apply { mkdirs() }
        val renderDirectory = File(sessionDirectory, "rendered").apply { mkdirs() }
        val outputDirectory = File(sessionDirectory, "engine").apply { mkdirs() }
        val recoveredPages = mutableListOf<OcrRecoveredPage>()
        ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
            PdfRenderer(descriptor).use { renderer ->
                pages.sortedBy { it.pageIndex }.forEach { page ->
                    if (page.pageIndex !in 0 until renderer.pageCount) return@forEach
                    val renderedImage = renderPage(renderer, page.pageIndex, renderDirectory) ?: return@forEach
                    try {
                        val result = ocrEngine.recognize(
                            OcrPageRequest(
                                imagePath = renderedImage.absolutePath,
                                pageIndex = page.pageIndex,
                                outputDirectoryPath = outputDirectory.absolutePath,
                                settings = ocrSettings,
                            ),
                        )
                        val ocrQuality = EmbeddedTextQualityHeuristics.analyze(result.page.text, result.blocks)
                        val embeddedQuality = EmbeddedTextQualityHeuristics.analyze(page.pageText, page.blocks)
                        val shouldUseOcr = result.page.text.isNotBlank() &&
                            (
                                !ocrQuality.looksGarbled ||
                                    embeddedQuality.looksGarbled ||
                                    result.page.text.length > page.pageText.length * 1.2f
                                )
                        if (shouldUseOcr) {
                            recoveredPages += OcrRecoveredPage(
                                page = result.page,
                                strategy = "mlkit-rendered-page",
                            )
                        }
                    } finally {
                        renderedImage.delete()
                    }
                }
            }
        }
        renderDirectory.deleteRecursively()
        outputDirectory.deleteRecursively()
        sessionDirectory.deleteRecursively()
        return recoveredPages
    }

    private fun renderPage(renderer: PdfRenderer, pageIndex: Int, outputDirectory: File): File? {
        renderer.openPage(pageIndex).use { page ->
            val width = (page.width * 2f).roundToInt().coerceAtLeast(1)
            val height = (page.height * 2f).roundToInt().coerceAtLeast(1)
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            return try {
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                val target = File(outputDirectory, "page-$pageIndex.png")
                target.outputStream().use { output ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
                }
                target
            } finally {
                bitmap.recycle()
            }
        }
    }
}


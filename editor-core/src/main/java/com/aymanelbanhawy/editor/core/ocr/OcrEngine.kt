package com.aymanelbanhawy.editor.core.ocr

import com.aymanelbanhawy.editor.core.model.NormalizedRect
import com.aymanelbanhawy.editor.core.search.ExtractedTextBlock
import com.aymanelbanhawy.editor.core.search.SearchContentSource
import kotlinx.serialization.Serializable

@Serializable
enum class OcrJobStatus {
    Pending,
    RetryScheduled,
    Running,
    Paused,
    Completed,
    Failed,
}

@Serializable
enum class OcrModelDeliveryMode {
    Bundled,
    PlatformManaged,
}

@Serializable
data class OcrPreprocessingOptions(
    val fixOrientation: Boolean = true,
    val deskew: Boolean = true,
    val autoCrop: Boolean = true,
    val contrastCleanup: Boolean = true,
    val grayscale: Boolean = true,
    val binarize: Boolean = false,
)

@Serializable
data class OcrSettingsModel(
    val deliveryMode: OcrModelDeliveryMode = OcrModelDeliveryMode.Bundled,
    val preprocessing: OcrPreprocessingOptions = OcrPreprocessingOptions(),
    val timeoutSeconds: Int = 20,
    val maxRetryCount: Int = 2,
    val pagesPerWorkerBatch: Int = 8,
    val embedSessionDataOnExport: Boolean = true,
    val languageHints: List<String> = emptyList(),
)

@Serializable
data class OcrTextElementModel(
    val text: String,
    val bounds: NormalizedRect,
    val confidence: Float? = null,
    val languageTag: String? = null,
    val scriptTag: String? = null,
)

@Serializable
data class OcrTextLineModel(
    val text: String,
    val bounds: NormalizedRect,
    val elements: List<OcrTextElementModel>,
    val confidence: Float? = null,
    val languageTag: String? = null,
    val scriptTag: String? = null,
)

@Serializable
data class OcrTextBlockModel(
    val text: String,
    val bounds: NormalizedRect,
    val lines: List<OcrTextLineModel>,
    val confidence: Float? = null,
    val languageTag: String? = null,
    val scriptTag: String? = null,
)

@Serializable
data class OcrPageContent(
    val pageIndex: Int,
    val text: String,
    val blocks: List<OcrTextBlockModel>,
    val imageWidth: Int,
    val imageHeight: Int,
    val languageHints: List<String> = emptyList(),
    val warningMessage: String? = null,
) {
    fun flattenedSearchBlocks(): List<ExtractedTextBlock> {
        val lines = blocks.flatMap { block ->
            block.lines.map { line ->
                ExtractedTextBlock(
                    pageIndex = pageIndex,
                    text = line.text,
                    bounds = line.bounds,
                    source = SearchContentSource.Ocr,
                    confidence = line.confidence ?: block.confidence,
                    languageTag = line.languageTag ?: block.languageTag,
                    scriptTag = line.scriptTag ?: block.scriptTag,
                    lineCount = 1,
                    elementCount = line.elements.size,
                )
            }
        }
        return if (lines.isNotEmpty()) {
            lines
        } else {
            blocks.map { block ->
                ExtractedTextBlock(
                    pageIndex = pageIndex,
                    text = block.text,
                    bounds = block.bounds,
                    source = SearchContentSource.Ocr,
                    confidence = block.confidence,
                    languageTag = block.languageTag,
                    scriptTag = block.scriptTag,
                    lineCount = block.lines.size.coerceAtLeast(1),
                    elementCount = block.lines.sumOf { it.elements.size },
                )
            }
        }
    }
}

@Serializable
data class OcrEngineDiagnostics(
    val code: String,
    val message: String,
    val details: List<String> = emptyList(),
    val retryable: Boolean = false,
)

data class OcrPageRequest(
    val imagePath: String,
    val pageIndex: Int,
    val outputDirectoryPath: String,
    val settings: OcrSettingsModel,
)

data class OcrEngineResult(
    val page: OcrPageContent,
    val preprocessedImagePath: String,
    val diagnostics: OcrEngineDiagnostics? = null,
) {
    val pageText: String
        get() = page.text

    val blocks: List<ExtractedTextBlock>
        get() = page.flattenedSearchBlocks()
}

data class OcrJobSummary(
    val id: String,
    val documentKey: String,
    val pageIndex: Int,
    val status: OcrJobStatus,
    val progressPercent: Int,
    val attemptCount: Int,
    val maxAttempts: Int,
    val imagePath: String,
    val preprocessedImagePath: String? = null,
    val pageText: String? = null,
    val errorMessage: String? = null,
    val diagnostics: List<String> = emptyList(),
    val updatedAtEpochMillis: Long,
) {
    val canRetry: Boolean
        get() = status == OcrJobStatus.Failed || status == OcrJobStatus.Completed

    val canPause: Boolean
        get() = status == OcrJobStatus.Pending || status == OcrJobStatus.RetryScheduled || status == OcrJobStatus.Running

    val canResume: Boolean
        get() = status == OcrJobStatus.Paused || status == OcrJobStatus.Failed
}

interface OcrEngine {
    suspend fun recognize(request: OcrPageRequest): OcrEngineResult
}

class OcrEngineException(
    val diagnostics: OcrEngineDiagnostics,
    cause: Throwable? = null,
) : IllegalStateException(diagnostics.message, cause)

package com.aymanelbanhawy.editor.core.ocr

import android.content.Context
import android.graphics.Rect
import com.aymanelbanhawy.editor.core.model.NormalizedRect
import com.google.mlkit.common.MlKitException
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.File

class MlKitOcrEngine(
    private val context: Context,
    private val preprocessingPipeline: OcrPreprocessingPipeline = OcrPreprocessingPipeline(),
) : OcrEngine {

    override suspend fun recognize(request: OcrPageRequest): OcrEngineResult = withContext(Dispatchers.Default) {
        val outputDirectory = File(request.outputDirectoryPath)
        val preprocessResult = preprocessingPipeline.preprocess(
            sourceFile = File(request.imagePath),
            outputDirectory = outputDirectory,
            pageIndex = request.pageIndex,
            options = request.settings.preprocessing,
        )
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        try {
            val image = InputImage.fromFilePath(context, android.net.Uri.fromFile(File(preprocessResult.imagePath)))
            val visionText = withTimeout(request.settings.timeoutSeconds.coerceAtLeast(5) * 1_000L) {
                recognizer.process(image).await()
            }
            val page = visionText.toPageContent(
                pageIndex = request.pageIndex,
                imageWidth = image.width,
                imageHeight = image.height,
                languageHints = request.settings.languageHints,
                preprocessingDiagnostics = preprocessResult.diagnostics,
                deliveryMode = request.settings.deliveryMode,
            )
            OcrEngineResult(
                page = page,
                preprocessedImagePath = preprocessResult.imagePath,
                diagnostics = page.warningMessage?.let {
                    OcrEngineDiagnostics(
                        code = if (request.settings.deliveryMode == OcrModelDeliveryMode.PlatformManaged) "platform-managed-fallback" else "ocr-warning",
                        message = it,
                        details = preprocessResult.diagnostics,
                        retryable = false,
                    )
                },
            )
        } catch (error: Throwable) {
            throw error.toOcrEngineException(preprocessResult.diagnostics)
        } finally {
            recognizer.close()
        }
    }

    private fun Text.toPageContent(
        pageIndex: Int,
        imageWidth: Int,
        imageHeight: Int,
        languageHints: List<String>,
        preprocessingDiagnostics: List<String>,
        deliveryMode: OcrModelDeliveryMode,
    ): OcrPageContent {
        val blocks = textBlocks.mapNotNull { block ->
            val rect = block.boundingBox?.toNormalizedRect(imageWidth, imageHeight) ?: return@mapNotNull null
            OcrTextBlockModel(
                text = block.text.orEmpty(),
                bounds = rect,
                lines = block.lines.mapNotNull { line ->
                    val lineRect = line.boundingBox?.toNormalizedRect(imageWidth, imageHeight) ?: return@mapNotNull null
                    OcrTextLineModel(
                        text = line.text.orEmpty(),
                        bounds = lineRect,
                        elements = line.elements.mapNotNull { element ->
                            val elementRect = element.boundingBox?.toNormalizedRect(imageWidth, imageHeight) ?: return@mapNotNull null
                            OcrTextElementModel(
                                text = element.text.orEmpty(),
                                bounds = elementRect,
                                confidence = element.optionalFloat("getConfidence"),
                                languageTag = element.optionalString("getRecognizedLanguage"),
                                scriptTag = element.optionalString("getRecognizedLanguage"),
                            )
                        },
                        confidence = line.optionalFloat("getConfidence"),
                        languageTag = line.optionalString("getRecognizedLanguage"),
                        scriptTag = line.optionalString("getRecognizedLanguage"),
                    )
                },
                confidence = block.optionalFloat("getConfidence"),
                languageTag = block.optionalString("getRecognizedLanguage"),
                scriptTag = block.optionalString("getRecognizedLanguage"),
            )
        }
        val warning = buildList {
            addAll(preprocessingDiagnostics)
            if (deliveryMode == OcrModelDeliveryMode.PlatformManaged) {
                add("Platform-managed OCR delivery was requested; this build is currently running the bundled ML Kit text recognizer.")
            }
            if (text.trim().isBlank()) {
                add("No text was recognized on this page.")
            }
        }.takeIf { it.isNotEmpty() }?.joinToString(" ")
        return OcrPageContent(
            pageIndex = pageIndex,
            text = text.trim(),
            blocks = blocks,
            imageWidth = imageWidth,
            imageHeight = imageHeight,
            languageHints = languageHints,
            warningMessage = warning,
        )
    }

    private fun Rect.toNormalizedRect(imageWidth: Int, imageHeight: Int): NormalizedRect {
        val safeWidth = imageWidth.coerceAtLeast(1).toFloat()
        val safeHeight = imageHeight.coerceAtLeast(1).toFloat()
        return NormalizedRect(
            left = (left / safeWidth).coerceIn(0f, 1f),
            top = (top / safeHeight).coerceIn(0f, 1f),
            right = (right / safeWidth).coerceIn(0f, 1f),
            bottom = (bottom / safeHeight).coerceIn(0f, 1f),
        ).normalized()
    }

    private fun Any.optionalString(methodName: String): String? {
        return runCatching { javaClass.getMethod(methodName).invoke(this) as? String }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
    }

    private fun Any.optionalFloat(methodName: String): Float? {
        val value = runCatching { javaClass.getMethod(methodName).invoke(this) }.getOrNull() ?: return null
        return when (value) {
            is Float -> value
            is Double -> value.toFloat()
            is Number -> value.toFloat()
            else -> null
        }
    }

    private fun Throwable.toOcrEngineException(preprocessingDiagnostics: List<String>): OcrEngineException {
        if (this is OcrEngineException) return this
        val diagnostics = when (this) {
            is MlKitException -> {
                val retryable = errorCode == MlKitException.UNAVAILABLE || errorCode == MlKitException.DEADLINE_EXCEEDED || errorCode == MlKitException.CANCELLED
                OcrEngineDiagnostics(
                    code = "mlkit-$errorCode",
                    message = message ?: "ML Kit OCR failed.",
                    details = preprocessingDiagnostics,
                    retryable = retryable,
                )
            }
            else -> OcrEngineDiagnostics(
                code = "ocr-runtime-error",
                message = message ?: "OCR failed.",
                details = preprocessingDiagnostics,
                retryable = false,
            )
        }
        return OcrEngineException(diagnostics, this)
    }
}
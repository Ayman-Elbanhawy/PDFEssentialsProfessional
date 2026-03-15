package com.aymanelbanhawy.editor.core.ocr

import com.aymanelbanhawy.editor.core.data.OcrJobDao
import com.aymanelbanhawy.editor.core.data.OcrJobEntity
import com.aymanelbanhawy.editor.core.model.DocumentSourceType
import com.aymanelbanhawy.editor.core.model.PdfDocumentRef
import com.aymanelbanhawy.editor.core.search.ExtractedTextBlock
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File

class OcrSessionStore(
    private val json: Json,
    private val ocrJobDao: OcrJobDao? = null,
) {
    suspend fun load(documentRef: PdfDocumentRef): OcrDocumentPayload? {
        val databasePayload = loadFromDatabase(documentRef.sourceKey) ?: loadFromDatabase(documentRef.workingCopyPath)
        if (databasePayload != null) {
            return databasePayload
        }
        val compatibilityFile = resolveExistingCompatibilityFile(documentRef) ?: return null
        return decodeCompatibilityPayload(compatibilityFile)
    }

    suspend fun loadForDocumentKey(documentKey: String): OcrDocumentPayload? {
        val databasePayload = loadFromDatabase(documentKey)
        if (databasePayload != null) {
            return databasePayload
        }
        val compatibilityFile = compatibilityFileForPath(documentKey) ?: return null
        return compatibilityFile.takeIf { it.exists() }?.let(::decodeCompatibilityPayload)
    }

    suspend fun mergePage(documentKey: String, settings: OcrSettingsModel, page: OcrPageContent): OcrDocumentPayload {
        val current = loadForDocumentKey(documentKey)
        val pages = (current?.pages.orEmpty().filterNot { it.pageIndex == page.pageIndex } + page).sortedBy { it.pageIndex }
        return OcrDocumentPayload(
            documentKey = documentKey,
            settings = settings,
            pages = pages,
            updatedAtEpochMillis = System.currentTimeMillis(),
        )
    }

    suspend fun persistPayload(payload: OcrDocumentPayload) {
        persistToDatabase(payload)
        persistCompatibilityPayload(payload)
    }

    suspend fun persistPage(documentKey: String, settings: OcrSettingsModel, page: OcrPageContent) {
        persistPayload(mergePage(documentKey, settings, page))
    }

    suspend fun copyCompatibilityPayload(documentRef: PdfDocumentRef, destinationPdf: File, settings: OcrSettingsModel? = null) {
        val payload = load(documentRef) ?: return
        val effective = if (settings != null) {
            payload.copy(settings = settings, updatedAtEpochMillis = System.currentTimeMillis())
        } else {
            payload
        }
        persistPayload(effective.copy(documentKey = destinationPdf.absolutePath))
    }

    fun deleteCompatibilityPayload(destinationPdf: File) {
        File(destinationPdf.absolutePath + COMPATIBILITY_SUFFIX).delete()
    }

    private suspend fun loadFromDatabase(documentKey: String): OcrDocumentPayload? {
        val dao = ocrJobDao ?: return null
        val jobs = dao.jobsForDocument(documentKey)
            .filter { it.resultPageJson != null || it.resultText != null || it.resultBlocksJson != null }
            .sortedBy { it.pageIndex }
        if (jobs.isEmpty()) {
            return null
        }
        val pages = jobs.mapNotNull(::decodePageFromEntity)
        if (pages.isEmpty()) {
            return null
        }
        val settings = jobs.asReversed().firstNotNullOfOrNull { entity ->
            entity.settingsJson?.let { runCatching { json.decodeFromString(OcrSettingsModel.serializer(), it) }.getOrNull() }
        } ?: OcrSettingsModel()
        return OcrDocumentPayload(
            documentKey = documentKey,
            settings = settings,
            pages = pages.sortedBy { it.pageIndex },
            updatedAtEpochMillis = jobs.maxOf { it.updatedAtEpochMillis },
        )
    }

    private suspend fun persistToDatabase(payload: OcrDocumentPayload) {
        val dao = ocrJobDao ?: return
        val now = System.currentTimeMillis()
        val encodedSettings = json.encodeToString(OcrSettingsModel.serializer(), payload.settings)
        val pagesByIndex = payload.pages.associateBy { it.pageIndex }
        val existing = dao.jobsForDocument(payload.documentKey).associateBy { it.pageIndex }
        val updated = pagesByIndex.values.map { page ->
            val current = existing[page.pageIndex]
            OcrJobEntity(
                id = current?.id ?: OcrJobPipeline.buildJobId(payload.documentKey, page.pageIndex),
                documentKey = payload.documentKey,
                pageIndex = page.pageIndex,
                imagePath = current?.imagePath.orEmpty(),
                status = OcrJobStatus.Completed.name,
                progressPercent = 100,
                attemptCount = maxOf(current?.attemptCount ?: 0, 1),
                maxAttempts = maxOf(current?.maxAttempts ?: 0, payload.settings.maxRetryCount.coerceAtLeast(1)),
                resultText = page.text,
                resultBlocksJson = json.encodeToString(ListSerializer(ExtractedTextBlock.serializer()), page.flattenedSearchBlocks()),
                resultPageJson = json.encodeToString(OcrPageContent.serializer(), page),
                diagnosticsJson = current?.diagnosticsJson,
                settingsJson = encodedSettings,
                preprocessedImagePath = current?.preprocessedImagePath,
                errorMessage = null,
                startedAtEpochMillis = current?.startedAtEpochMillis ?: now,
                completedAtEpochMillis = now,
                createdAtEpochMillis = current?.createdAtEpochMillis ?: now,
                updatedAtEpochMillis = now,
            )
        }
        if (updated.isNotEmpty()) {
            dao.upsertAll(updated)
        }
    }

    private fun persistCompatibilityPayload(payload: OcrDocumentPayload) {
        val compatibilityFile = compatibilityFileForPath(payload.documentKey) ?: return
        compatibilityFile.parentFile?.mkdirs()
        compatibilityFile.writeText(json.encodeToString(OcrDocumentPayload.serializer(), payload))
    }

    private fun decodePageFromEntity(entity: OcrJobEntity): OcrPageContent? {
        entity.resultPageJson?.let { encoded ->
            runCatching { json.decodeFromString(OcrPageContent.serializer(), encoded) }
                .getOrNull()
                ?.let { return it }
        }
        val blocks = entity.resultBlocksJson?.let { encoded ->
            runCatching { json.decodeFromString(ListSerializer(ExtractedTextBlock.serializer()), encoded) }.getOrNull()
        }.orEmpty()
        val fallbackText = entity.resultText?.trim().orEmpty()
        if (fallbackText.isBlank() && blocks.isEmpty()) {
            return null
        }
        return OcrPageContent(
            pageIndex = entity.pageIndex,
            text = fallbackText,
            blocks = blocks.groupBy { it.bounds }.map { (bounds, grouped) ->
                OcrTextBlockModel(
                    text = grouped.joinToString("\n") { it.text },
                    bounds = bounds,
                    lines = grouped.map { block ->
                        OcrTextLineModel(
                            text = block.text,
                            bounds = block.bounds,
                            elements = listOf(
                                OcrTextElementModel(
                                    text = block.text,
                                    bounds = block.bounds,
                                    confidence = block.confidence,
                                    languageTag = block.languageTag,
                                    scriptTag = block.scriptTag,
                                ),
                            ),
                            confidence = block.confidence,
                            languageTag = block.languageTag,
                            scriptTag = block.scriptTag,
                        )
                    },
                )
            },
            imageWidth = 0,
            imageHeight = 0,
            languageHints = entity.settingsJson
                ?.let { runCatching { json.decodeFromString(OcrSettingsModel.serializer(), it) }.getOrNull() }
                ?.languageHints
                .orEmpty(),
            warningMessage = entity.errorMessage,
        )
    }

    private fun resolveExistingCompatibilityFile(documentRef: PdfDocumentRef): File? {
        val candidates = buildList {
            when (documentRef.sourceType) {
                DocumentSourceType.File -> add(File(documentRef.sourceKey + COMPATIBILITY_SUFFIX))
                else -> Unit
            }
            add(File(documentRef.workingCopyPath + COMPATIBILITY_SUFFIX))
        }
        return candidates.firstOrNull { it.exists() }
    }

    private fun compatibilityFileForPath(documentKey: String): File? {
        return if (documentKey.contains("://") && !File(documentKey).exists()) {
            null
        } else {
            File(documentKey + COMPATIBILITY_SUFFIX)
        }
    }

    private fun decodeCompatibilityPayload(file: File): OcrDocumentPayload? {
        return runCatching {
            json.decodeFromString(OcrDocumentPayload.serializer(), file.readText())
        }.getOrNull()
    }

    companion object {
        const val COMPATIBILITY_SUFFIX: String = ".ocr.json"
    }
}

@kotlinx.serialization.Serializable
data class OcrDocumentPayload(
    val documentKey: String,
    val settings: OcrSettingsModel,
    val pages: List<OcrPageContent>,
    val updatedAtEpochMillis: Long,
)


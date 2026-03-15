package com.aymanelbanhawy.editor.core.compat

import com.aymanelbanhawy.editor.core.model.AnnotationExportMode
import com.aymanelbanhawy.editor.core.model.AnnotationModel
import com.aymanelbanhawy.editor.core.model.DocumentSourceType
import com.aymanelbanhawy.editor.core.model.PdfDocumentRef
import java.io.File
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

interface LegacyAnnotationCompatibilityStore {
    suspend fun loadAnnotations(documentRef: PdfDocumentRef): Map<Int, List<AnnotationModel>>
}

class FileLegacyAnnotationCompatibilityStore(
    private val json: Json,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : LegacyAnnotationCompatibilityStore {

    override suspend fun loadAnnotations(documentRef: PdfDocumentRef): Map<Int, List<AnnotationModel>> = withContext(ioDispatcher) {
        val file = resolveCompatibilityFile(documentRef) ?: return@withContext emptyMap()
        if (!file.exists()) return@withContext emptyMap()
        val payload = runCatching {
            json.decodeFromString(LegacyAnnotationCompatibilityPayload.serializer(), file.readText())
        }.getOrNull() ?: return@withContext emptyMap()
        payload.annotations.groupBy { it.pageIndex }
    }

    private fun resolveCompatibilityFile(documentRef: PdfDocumentRef): File? {
        val sourceFile = when (documentRef.sourceType) {
            DocumentSourceType.File -> File(documentRef.sourceKey)
            DocumentSourceType.Uri, DocumentSourceType.Asset, DocumentSourceType.Memory -> File(documentRef.workingCopyPath)
        }
        // Allowed legacy annotation reference: compatibility-only fallback for older local artifacts.
        val explicitCompatibilityFile = File(sourceFile.absolutePath + ".annotations.json")
        if (explicitCompatibilityFile.exists()) return explicitCompatibilityFile
        val workingCompatibilityFile = File(documentRef.workingCopyPath + ".annotations.json")
        if (workingCompatibilityFile.exists()) return workingCompatibilityFile
        return explicitCompatibilityFile.takeIf { it.parentFile?.exists() == true }
            ?: workingCompatibilityFile.takeIf { it.parentFile?.exists() == true }
    }
}

@Serializable
private data class LegacyAnnotationCompatibilityPayload(
    val documentKey: String,
    val exportMode: AnnotationExportMode,
    val annotations: List<AnnotationModel>,
    val updatedAtEpochMillis: Long,
)

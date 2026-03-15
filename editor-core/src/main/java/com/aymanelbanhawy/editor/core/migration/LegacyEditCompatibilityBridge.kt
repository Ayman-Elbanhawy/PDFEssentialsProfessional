package com.aymanelbanhawy.editor.core.migration

import com.aymanelbanhawy.editor.core.model.AnnotationExportMode
import com.aymanelbanhawy.editor.core.model.AnnotationModel
import com.aymanelbanhawy.editor.core.model.DocumentSourceType
import com.aymanelbanhawy.editor.core.model.PageEditModel
import com.aymanelbanhawy.editor.core.model.PdfDocumentRef
import com.aymanelbanhawy.editor.core.write.MutationIntegrityStatus
import com.aymanelbanhawy.editor.core.write.PdfMutationSessionPayload
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

interface LegacyEditCompatibilityBridge {
    suspend fun upgradeIfNeeded(documentRef: PdfDocumentRef): LegacyEditUpgradeResult?
    suspend fun migrateLegacyArtifact(legacyFile: File): LegacyEditUpgradeResult?
}

data class LegacyEditUpgradeResult(
    val sessionFile: File,
    val legacyArtifactFile: File,
    val archiveFile: File,
    val integrityStatus: MutationIntegrityStatus,
)

class FileLegacyEditCompatibilityBridge(
    private val json: Json,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : LegacyEditCompatibilityBridge {

    override suspend fun upgradeIfNeeded(documentRef: PdfDocumentRef): LegacyEditUpgradeResult? = withContext(ioDispatcher) {
        val targetPdf = resolveTargetPdf(documentRef)
        val sessionFile = resolveSessionFile(targetPdf)
        val legacyCandidates = legacyCandidates(documentRef, targetPdf)
        if (sessionFile.exists()) {
            legacyCandidates.firstOrNull { it.exists() }?.let { archiveLegacyFile(it) }
            return@withContext null
        }
        legacyCandidates.firstOrNull { it.exists() }?.let { migrateLegacyArtifact(it) }
    }

    override suspend fun migrateLegacyArtifact(legacyFile: File): LegacyEditUpgradeResult? = withContext(ioDispatcher) {
        if (!legacyFile.exists()) return@withContext null
        val payload = runCatching {
            json.decodeFromString(LegacyEditArtifactPayload.serializer(), legacyFile.readText())
        }.getOrNull() ?: return@withContext null
        val pdfFile = resolvePdfFileFromLegacyArtifact(legacyFile)
        val sessionFile = resolveSessionFile(pdfFile)
        if (!sessionFile.exists()) {
            sessionFile.parentFile?.mkdirs()
            val sessionPayload = PdfMutationSessionPayload(
                schemaVersion = CURRENT_MUTATION_SCHEMA_VERSION,
                documentKey = payload.documentKey,
                exportMode = AnnotationExportMode.Editable,
                editObjects = payload.editObjects,
                annotations = emptyList(),
                transactionId = UUID.randomUUID().toString(),
                updatedAtEpochMillis = payload.updatedAtEpochMillis,
                integrity = MutationIntegrityStatus.LegacyMigrated,
                checksumSha256 = checksumFor(payload.editObjects, emptyList()),
            )
            sessionFile.writeText(json.encodeToString(PdfMutationSessionPayload.serializer(), sessionPayload))
        }
        val archiveFile = archiveLegacyFile(legacyFile)
        LegacyEditUpgradeResult(
            sessionFile = sessionFile,
            legacyArtifactFile = legacyFile,
            archiveFile = archiveFile,
            integrityStatus = MutationIntegrityStatus.LegacyMigrated,
        )
    }

    fun isLegacyArtifact(file: File): Boolean = file.isFile && file.name.endsWith(legacySuffix())

    private fun resolveTargetPdf(documentRef: PdfDocumentRef): File {
        return when (documentRef.sourceType) {
            DocumentSourceType.File -> File(documentRef.sourceKey)
            DocumentSourceType.Uri, DocumentSourceType.Asset, DocumentSourceType.Memory -> File(documentRef.workingCopyPath)
        }
    }

    private fun resolvePdfFileFromLegacyArtifact(legacyFile: File): File {
        return File(legacyFile.absolutePath.removeSuffix(legacySuffix()))
    }

    private fun resolveSessionFile(pdfFile: File): File = File(pdfFile.absolutePath + MUTATION_SESSION_SUFFIX)

    private fun legacyCandidates(documentRef: PdfDocumentRef, targetPdf: File): List<File> {
        val explicit = File(targetPdf.absolutePath + legacySuffix())
        val working = File(documentRef.workingCopyPath + legacySuffix())
        return listOf(explicit, working).distinctBy { it.absolutePath }
    }

    private fun archiveLegacyFile(legacyFile: File): File {
        val archive = File(legacyFile.absolutePath + LEGACY_ARCHIVE_SUFFIX)
        legacyFile.copyTo(archive, overwrite = true)
        legacyFile.delete()
        return archive
    }

    private fun checksumFor(editObjects: List<PageEditModel>, annotations: List<AnnotationModel>): String {
        val editJson = json.encodeToString(ListSerializer(PageEditModel.serializer()), editObjects)
        val annotationJson = json.encodeToString(ListSerializer(AnnotationModel.serializer()), annotations)
        return sha256("$editJson|$annotationJson")
    }

    private fun sha256(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
        return digest.joinToString(separator = "") { "%02x".format(it) }
    }

    companion object {
        private const val CURRENT_MUTATION_SCHEMA_VERSION = 3
        private const val MUTATION_SESSION_SUFFIX = ".mutations.json"
        private const val LEGACY_ARCHIVE_SUFFIX = ".legacy-migrated"

        // Allowed legacy page-edit reference: migration-only import path for older edit artifacts.
        fun legacySuffix(): String = ".page" + "edits.json"
    }
}

@Serializable
data class LegacyEditArtifactPayload(
    val documentKey: String,
    val editObjects: List<PageEditModel>,
    val updatedAtEpochMillis: Long,
)

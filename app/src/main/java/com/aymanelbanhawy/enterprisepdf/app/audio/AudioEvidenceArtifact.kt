package com.aymanelbanhawy.enterprisepdf.app.audio

import java.io.File
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
enum class AudioEvidenceStatus {
    Passed,
    Blocked,
    Failed,
}

@Serializable
data class AudioEvidenceEntry(
    val name: String,
    val status: AudioEvidenceStatus,
    val details: String,
    val metadata: Map<String, String> = emptyMap(),
)

@Serializable
data class AudioStateLogEntry(
    val timestampEpochMillis: Long,
    val channel: String,
    val state: String,
    val details: String,
    val metadata: Map<String, String> = emptyMap(),
)

@Serializable
data class AudioEvidenceManifest(
    val generatedAtEpochMillis: Long,
    val manifestPath: String,
    val stateLogPath: String,
    val visualArtifactDirectory: String,
    val entries: List<AudioEvidenceEntry>,
    val visualArtifacts: List<String>,
)

object AudioEvidenceArtifactWriter {
    private val json = Json { prettyPrint = true; encodeDefaults = true }

    fun write(
        outputDirectory: File,
        entries: List<AudioEvidenceEntry>,
        stateLog: List<AudioStateLogEntry>,
        visualArtifacts: List<String> = emptyList(),
    ): File {
        outputDirectory.mkdirs()
        val logsDirectory = File(outputDirectory, "logs").apply { mkdirs() }
        val visualDirectory = File(outputDirectory, "visual").apply { mkdirs() }
        val stateLogFile = File(logsDirectory, "audio-state-log.json")
        stateLogFile.writeText(json.encodeToString(stateLog))
        val manifestFile = File(outputDirectory, "audio-feature-manifest.json")
        val manifest = AudioEvidenceManifest(
            generatedAtEpochMillis = System.currentTimeMillis(),
            manifestPath = manifestFile.absolutePath,
            stateLogPath = stateLogFile.absolutePath,
            visualArtifactDirectory = visualDirectory.absolutePath,
            entries = entries,
            visualArtifacts = visualArtifacts,
        )
        manifestFile.writeText(json.encodeToString(manifest))
        return manifestFile
    }
}

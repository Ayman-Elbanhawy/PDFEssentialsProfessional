package com.aymanelbanhawy.enterprisepdf.app.audio

import android.content.Context
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import android.util.Base64
import com.aymanelbanhawy.editor.core.collaboration.VoiceCommentAttachmentModel
import java.io.File
import java.util.UUID

class AndroidVoiceCommentRuntime(
    private val context: Context,
) {
    private var recorder: MediaRecorder? = null
    private var recordingFile: File? = null
    private var recordingStartedAtEpochMillis: Long = 0L
    private var mediaPlayer: MediaPlayer? = null

    fun startRecording(scopeKey: String): VoiceCommentAttachmentModel? {
        stopPlayback()
        cancelRecording()
        val outputDir = File(context.filesDir, "voice-comments").apply { mkdirs() }
        val outputFile = File(outputDir, "${scopeKey}_${UUID.randomUUID()}.m4a")
        val created = MediaRecorder().apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioSamplingRate(44_100)
            setAudioEncodingBitRate(96_000)
            setOutputFile(outputFile.absolutePath)
            prepare()
            start()
        }
        recorder = created
        recordingFile = outputFile
        recordingStartedAtEpochMillis = System.currentTimeMillis()
        return null
    }

    fun stopRecording(transcript: String = ""): VoiceCommentAttachmentModel? {
        val activeRecorder = recorder ?: return null
        val outputFile = recordingFile ?: return null
        recorder = null
        recordingFile = null
        runCatching { activeRecorder.stop() }
        activeRecorder.reset()
        activeRecorder.release()
        val durationMillis = readDurationMillis(outputFile).coerceAtLeast(System.currentTimeMillis() - recordingStartedAtEpochMillis)
        val payload = Base64.encodeToString(outputFile.readBytes(), Base64.NO_WRAP)
        return VoiceCommentAttachmentModel(
            id = UUID.randomUUID().toString(),
            localFilePath = outputFile.absolutePath,
            mimeType = "audio/mp4",
            durationMillis = durationMillis,
            createdAtEpochMillis = System.currentTimeMillis(),
            transcript = transcript,
            payloadBase64 = payload,
        )
    }

    fun cancelRecording() {
        val activeRecorder = recorder ?: return
        val outputFile = recordingFile
        recorder = null
        recordingFile = null
        runCatching { activeRecorder.stop() }
        activeRecorder.reset()
        activeRecorder.release()
        outputFile?.delete()
    }

    fun play(attachment: VoiceCommentAttachmentModel, onCompleted: () -> Unit = {}) {
        stopPlayback()
        val sourceFile = ensureMaterializedFile(attachment)
        mediaPlayer = MediaPlayer().apply {
            setDataSource(sourceFile.absolutePath)
            setOnCompletionListener {
                stopPlayback()
                onCompleted()
            }
            prepare()
            start()
        }
    }

    fun stopPlayback() {
        val player = mediaPlayer ?: return
        mediaPlayer = null
        runCatching { player.stop() }
        player.release()
    }

    private fun ensureMaterializedFile(attachment: VoiceCommentAttachmentModel): File {
        val existing = File(attachment.localFilePath)
        if (existing.exists()) return existing
        val outputDir = File(context.filesDir, "voice-comments").apply { mkdirs() }
        val restored = File(outputDir, "restored_${attachment.id}.m4a")
        if (!restored.exists() && attachment.payloadBase64.isNotBlank()) {
            restored.writeBytes(Base64.decode(attachment.payloadBase64, Base64.DEFAULT))
        }
        return restored
    }

    private fun readDurationMillis(file: File): Long {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        } finally {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                retriever.close()
            } else {
                retriever.release()
            }
        }
    }
}

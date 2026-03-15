package com.aymanelbanhawy.enterprisepdf.app.audio

import android.content.Context
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.util.Base64
import com.aymanelbanhawy.editor.core.collaboration.VoiceCommentAttachmentModel
import java.io.File
import java.util.UUID

class VoiceCommentRuntime(
    private val context: Context,
) {
    private var recorder: MediaRecorder? = null
    private var activeRecordingFile: File? = null
    private var player: MediaPlayer? = null
    private var playingCommentId: String? = null

    fun startRecording(): File {
        stopPlayback()
        stopRecording(discard = true)
        val outputDir = File(context.filesDir, "voice-comments").apply { mkdirs() }
        val outputFile = File(outputDir, "voice-${UUID.randomUUID()}.m4a")
        val mediaRecorder = MediaRecorder().apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioEncodingBitRate(128_000)
            setAudioSamplingRate(44_100)
            setOutputFile(outputFile.absolutePath)
            prepare()
            start()
        }
        recorder = mediaRecorder
        activeRecordingFile = outputFile
        return outputFile
    }

    fun stopRecording(discard: Boolean = false): VoiceCommentAttachmentModel? {
        val mediaRecorder = recorder ?: return null
        val outputFile = activeRecordingFile
        recorder = null
        activeRecordingFile = null
        runCatching { mediaRecorder.stop() }
        runCatching { mediaRecorder.reset() }
        runCatching { mediaRecorder.release() }
        if (discard || outputFile == null || !outputFile.exists()) {
            outputFile?.delete()
            return null
        }
        val duration = runCatching {
            val tempPlayer = MediaPlayer()
            try {
                tempPlayer.setDataSource(outputFile.absolutePath)
                tempPlayer.prepare()
                tempPlayer.duration.toLong()
            } finally {
                runCatching { tempPlayer.release() }
            }
        }.getOrDefault(0L)
        return VoiceCommentAttachmentModel(
            id = UUID.randomUUID().toString(),
            localFilePath = outputFile.absolutePath,
            mimeType = "audio/mp4",
            durationMillis = duration,
            createdAtEpochMillis = System.currentTimeMillis(),
            payloadBase64 = Base64.encodeToString(outputFile.readBytes(), Base64.NO_WRAP),
        )
    }

    fun cancelRecording() {
        stopRecording(discard = true)
    }

    fun startPlayback(comment: VoiceCommentAttachmentModel) {
        stopPlayback()
        val sourcePath = ensureLocalFile(comment)
        val mediaPlayer = MediaPlayer().apply {
            setDataSource(sourcePath)
            setOnCompletionListener { stopPlayback() }
            prepare()
            start()
        }
        player = mediaPlayer
        playingCommentId = comment.id
    }

    fun stopPlayback() {
        val mediaPlayer = player ?: return
        player = null
        playingCommentId = null
        runCatching { mediaPlayer.stop() }
        runCatching { mediaPlayer.reset() }
        runCatching { mediaPlayer.release() }
    }

    fun currentPlaybackCommentId(): String? = playingCommentId

    private fun ensureLocalFile(comment: VoiceCommentAttachmentModel): String {
        val existing = comment.localFilePath?.takeIf { File(it).exists() }
        if (existing != null) return existing
        val payload = comment.payloadBase64 ?: error("Voice comment audio is unavailable.")
        val restoredDir = File(context.cacheDir, "voice-comment-playback").apply { mkdirs() }
        val restoredFile = File(restoredDir, "${comment.id}.m4a")
        restoredFile.writeBytes(Base64.decode(payload, Base64.DEFAULT))
        return restoredFile.absolutePath
    }
}

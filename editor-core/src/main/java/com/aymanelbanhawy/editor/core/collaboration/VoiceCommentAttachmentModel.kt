package com.aymanelbanhawy.editor.core.collaboration

import kotlinx.serialization.Serializable

@Serializable
data class VoiceCommentAttachmentModel(
    val id: String,
    val localFilePath: String,
    val mimeType: String,
    val durationMillis: Long,
    val createdAtEpochMillis: Long,
    val transcript: String = "",
    val payloadBase64: String = "",
)

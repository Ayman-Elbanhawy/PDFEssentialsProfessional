package com.aymanelbanhawy.editor.core.model

import kotlinx.serialization.Serializable

@Serializable
sealed interface OpenDocumentRequest {
    @Serializable
    data class FromFile(
        val absolutePath: String,
        val displayNameOverride: String? = null,
        val password: String? = null,
    ) : OpenDocumentRequest

    @Serializable
    data class FromUri(
        val uriString: String,
        val displayName: String,
        val password: String? = null,
    ) : OpenDocumentRequest

    @Serializable
    data class FromAsset(
        val assetName: String,
        val displayName: String,
        val password: String? = null,
    ) : OpenDocumentRequest

    @Serializable
    data class FromBytes(
        val bytes: ByteArray,
        val displayName: String,
        val password: String? = null,
    ) : OpenDocumentRequest
}

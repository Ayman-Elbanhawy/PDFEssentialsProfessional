package com.aymanelbanhawy.editor.core.model

import android.net.Uri
import kotlinx.serialization.Serializable

@Serializable
enum class DocumentSourceType {
    File,
    Uri,
    Asset,
    Memory,
}

@Serializable
data class PdfDocumentRef(
    val uriString: String,
    val displayName: String,
    val password: String? = null,
    val sourceType: DocumentSourceType,
    val sourceKey: String,
    val workingCopyPath: String,
) {
    val uri: Uri
        get() = Uri.parse(uriString)
}

package com.aymanelbanhawy.enterprisepdf.app.open

import com.aymanelbanhawy.editor.core.model.OpenDocumentRequest

enum class PdfOpenSource {
    SampleSeed,
    SafPicker,
    ExternalViewIntent,
    ExternalSendIntent,
    RecentDocument,
    Connector,
}

data class PendingPdfOpenRequest(
    val request: OpenDocumentRequest,
    val source: PdfOpenSource,
    val activeUri: String,
    val displayName: String,
)

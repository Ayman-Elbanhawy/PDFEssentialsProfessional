package com.aymanelbanhawy.editor.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class PageContentType {
    Pdf,
    Blank,
    Image,
}

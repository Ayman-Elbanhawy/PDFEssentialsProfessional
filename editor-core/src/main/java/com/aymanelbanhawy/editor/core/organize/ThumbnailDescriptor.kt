package com.aymanelbanhawy.editor.core.organize

import kotlinx.serialization.Serializable

@Serializable
data class ThumbnailDescriptor(
    val pageIndex: Int,
    val imagePath: String,
)

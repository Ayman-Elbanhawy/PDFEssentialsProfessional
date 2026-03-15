package com.aymanelbanhawy.editor.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class EditorAction {
    Annotate,
    Organize,
    Forms,
    Sign,
    Search,
    Assistant,
    Review,
    Activity,
    Protect,
    Settings,
    Diagnostics,
    Share,
}

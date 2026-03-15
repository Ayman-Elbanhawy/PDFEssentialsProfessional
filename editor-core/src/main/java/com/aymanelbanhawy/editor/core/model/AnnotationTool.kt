package com.aymanelbanhawy.editor.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class AnnotationTool {
    Select,
    Highlight,
    Underline,
    Strikeout,
    FreehandInk,
    Rectangle,
    Ellipse,
    Arrow,
    Line,
    StickyNote,
    TextBox,
}

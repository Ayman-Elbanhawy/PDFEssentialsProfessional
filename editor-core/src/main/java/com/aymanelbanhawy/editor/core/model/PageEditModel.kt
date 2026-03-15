package com.aymanelbanhawy.editor.core.model

import kotlinx.serialization.Serializable
import kotlin.math.max

@Serializable
enum class EditObjectType {
    TextBox,
    Image,
}

@Serializable
enum class TextAlignment {
    Start,
    Center,
    End,
}

@Serializable
enum class FontFamilyToken {
    Sans,
    Serif,
    Monospace,
}

@Serializable
sealed interface PageEditModel {
    val id: String
    val pageIndex: Int
    val bounds: NormalizedRect
    val rotationDegrees: Float
    val opacity: Float
    val type: EditObjectType

    fun withPage(pageIndex: Int): PageEditModel
    fun movedBy(dx: Float, dy: Float): PageEditModel
    fun resized(anchor: ResizeAnchor, x: Float, y: Float): PageEditModel
    fun rotatedTo(rotationDegrees: Float): PageEditModel
    fun withOpacity(opacity: Float): PageEditModel
}

@Serializable
data class TextBoxEditModel(
    override val id: String,
    override val pageIndex: Int,
    override val bounds: NormalizedRect,
    override val rotationDegrees: Float = 0f,
    override val opacity: Float = 1f,
    val text: String = "Text",
    val fontFamily: FontFamilyToken = FontFamilyToken.Sans,
    val fontSizeSp: Float = 16f,
    val textColorHex: String = "#202124",
    val alignment: TextAlignment = TextAlignment.Start,
    val lineSpacingMultiplier: Float = 1.2f,
) : PageEditModel {
    override val type: EditObjectType = EditObjectType.TextBox

    override fun withPage(pageIndex: Int): PageEditModel = copy(pageIndex = pageIndex)
    override fun movedBy(dx: Float, dy: Float): PageEditModel = copy(bounds = bounds.offset(dx, dy))
    override fun resized(anchor: ResizeAnchor, x: Float, y: Float): PageEditModel = copy(bounds = bounds.resize(anchor, x, y))
    override fun rotatedTo(rotationDegrees: Float): PageEditModel = copy(rotationDegrees = rotationDegrees)
    override fun withOpacity(opacity: Float): PageEditModel = copy(opacity = opacity.coerceIn(0.05f, 1f))

    fun withText(text: String): TextBoxEditModel = copy(text = text)
    fun withTypography(
        fontFamily: FontFamilyToken = this.fontFamily,
        fontSizeSp: Float = this.fontSizeSp,
        textColorHex: String = this.textColorHex,
        alignment: TextAlignment = this.alignment,
        lineSpacingMultiplier: Float = this.lineSpacingMultiplier,
    ): TextBoxEditModel {
        return copy(
            fontFamily = fontFamily,
            fontSizeSp = fontSizeSp.coerceAtLeast(8f),
            textColorHex = textColorHex,
            alignment = alignment,
            lineSpacingMultiplier = lineSpacingMultiplier.coerceAtLeast(0.8f),
        )
    }
}

@Serializable
data class ImageEditModel(
    override val id: String,
    override val pageIndex: Int,
    override val bounds: NormalizedRect,
    override val rotationDegrees: Float = 0f,
    override val opacity: Float = 1f,
    val imagePath: String,
    val label: String = "Image",
) : PageEditModel {
    override val type: EditObjectType = EditObjectType.Image

    override fun withPage(pageIndex: Int): PageEditModel = copy(pageIndex = pageIndex)
    override fun movedBy(dx: Float, dy: Float): PageEditModel = copy(bounds = bounds.offset(dx, dy))
    override fun resized(anchor: ResizeAnchor, x: Float, y: Float): PageEditModel = copy(bounds = bounds.resize(anchor, x, y))
    override fun rotatedTo(rotationDegrees: Float): PageEditModel = copy(rotationDegrees = rotationDegrees)
    override fun withOpacity(opacity: Float): PageEditModel = copy(opacity = opacity.coerceIn(0.05f, 1f))

    fun replaced(imagePath: String, label: String = this.label): ImageEditModel = copy(imagePath = imagePath, label = label)
}

fun List<PageEditModel>.replaceEdit(updated: PageEditModel): List<PageEditModel> {
    return map { existing -> if (existing.id == updated.id) updated else existing }
}

fun List<PageEditModel>.withoutEdit(editId: String): List<PageEditModel> = filterNot { it.id == editId }

fun PageEditModel.duplicated(newId: String, delta: Float = 0.02f): PageEditModel = when (this) {
    is TextBoxEditModel -> copy(id = newId, bounds = bounds.offset(delta, delta))
    is ImageEditModel -> copy(id = newId, bounds = bounds.offset(delta, delta))
}

fun PageEditModel.displayLabel(): String = when (this) {
    is TextBoxEditModel -> text.ifBlank { "Text box" }.take(32)
    is ImageEditModel -> label.ifBlank { "Image" }
}

fun PageEditModel.normalizedRotation(): Float {
    var rotation = rotationDegrees % 360f
    if (rotation < 0f) rotation += 360f
    return rotation
}

fun PageEditModel.minimumSized(): PageEditModel = when (this) {
    is TextBoxEditModel -> copy(bounds = ensureMinSize(bounds))
    is ImageEditModel -> copy(bounds = ensureMinSize(bounds))
}

private fun ensureMinSize(bounds: NormalizedRect): NormalizedRect {
    val minWidth = 0.04f
    val minHeight = 0.04f
    val normalized = bounds.normalized()
    val right = max(normalized.right, normalized.left + minWidth).coerceIn(0f, 1f)
    val bottom = max(normalized.bottom, normalized.top + minHeight).coerceIn(0f, 1f)
    return normalized.copy(right = right, bottom = bottom)
}


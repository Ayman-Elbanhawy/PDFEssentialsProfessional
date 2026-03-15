package com.aymanelbanhawy.enterprisepdf.app.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Draw
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.aymanelbanhawy.editor.core.model.FontFamilyToken
import com.aymanelbanhawy.editor.core.model.ImageEditModel
import com.aymanelbanhawy.editor.core.model.PageEditModel
import com.aymanelbanhawy.editor.core.model.TextAlignment
import com.aymanelbanhawy.editor.core.model.TextBoxEditModel
import com.aymanelbanhawy.editor.core.model.displayLabel
import com.aymanelbanhawy.enterprisepdf.app.ui.IconTooltipButton
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EditInspectorSidebar(
    modifier: Modifier,
    editObjects: List<PageEditModel>,
    selectedEditObject: PageEditModel?,
    onSelectEdit: (String) -> Unit,
    onAddTextBox: () -> Unit,
    onAddImage: () -> Unit,
    onDeleteSelected: () -> Unit,
    onDuplicateSelected: () -> Unit,
    onReplaceSelectedImage: () -> Unit,
    onTextChanged: (String) -> Unit,
    onFontFamilyChanged: (FontFamilyToken) -> Unit,
    onFontSizeChanged: (Float) -> Unit,
    onTextColorChanged: (String) -> Unit,
    onTextAlignmentChanged: (TextAlignment) -> Unit,
    onLineSpacingChanged: (Float) -> Unit,
    onOpacityChanged: (Float) -> Unit,
    onRotationChanged: (Float) -> Unit,
) {
    Surface(
        modifier = modifier.testTag("lightweight-edit-tools"),
        tonalElevation = 4.dp,
        shadowElevation = 12.dp,
        shape = RoundedCornerShape(26.dp),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.62f),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("Lightweight object tools", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "Borrowing the quick overlay idea, reimplemented natively for fast text and image edits while signature capture stays in the dedicated sign flow.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.66f),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text("Quick create", style = MaterialTheme.typography.titleMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        IconTooltipButton(icon = Icons.Outlined.TextFields, tooltip = "Add Text", onClick = onAddTextBox)
                        IconTooltipButton(icon = Icons.Outlined.AddPhotoAlternate, tooltip = "Add Image", onClick = onAddImage)
                        IconTooltipButton(icon = Icons.Outlined.ContentCopy, tooltip = "Duplicate", onClick = onDuplicateSelected, enabled = selectedEditObject != null)
                        IconTooltipButton(icon = Icons.Outlined.Delete, tooltip = "Delete", onClick = onDeleteSelected, enabled = selectedEditObject != null)
                        if (selectedEditObject is ImageEditModel) {
                            IconTooltipButton(icon = Icons.Outlined.AutoAwesome, tooltip = "Replace Image", onClick = onReplaceSelectedImage)
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Surface(shape = RoundedCornerShape(999.dp), color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f)) {
                            Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                androidx.compose.material3.Icon(Icons.Outlined.Draw, contentDescription = null)
                                Text("Quick signature tools stay under Sign", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
            }

            Text("Current objects", style = MaterialTheme.typography.titleMedium)
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(editObjects, key = { it.id }) { edit ->
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        tonalElevation = if (edit.id == selectedEditObject?.id) 5.dp else 0.dp,
                        shape = RoundedCornerShape(18.dp),
                        color = if (edit.id == selectedEditObject?.id) {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.34f)
                        } else {
                            MaterialTheme.colorScheme.surface
                        },
                        onClick = { onSelectEdit(edit.id) },
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(edit.type.name, style = MaterialTheme.typography.labelLarge)
                            Text(edit.displayLabel(), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }

            selectedEditObject?.let { selected ->
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.16f))
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text("Inspector", style = MaterialTheme.typography.titleMedium)
                        when (selected) {
                            is TextBoxEditModel -> {
                                OutlinedTextField(
                                    value = selected.text,
                                    onValueChange = onTextChanged,
                                    label = { Text("Text") },
                                    modifier = Modifier.fillMaxWidth(),
                                    minLines = 3,
                                )
                                Text("Font family")
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    FontFamilyToken.entries.forEach { family ->
                                        FilterChip(
                                            selected = selected.fontFamily == family,
                                            onClick = { onFontFamilyChanged(family) },
                                            label = { Text(family.name) },
                                        )
                                    }
                                }
                                Text("Alignment")
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    TextAlignment.entries.forEach { alignment ->
                                        FilterChip(
                                            selected = selected.alignment == alignment,
                                            onClick = { onTextAlignmentChanged(alignment) },
                                            label = { Text(alignment.name) },
                                        )
                                    }
                                }
                                OutlinedTextField(
                                    value = selected.textColorHex,
                                    onValueChange = onTextColorChanged,
                                    label = { Text("Text color") },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                )
                                InspectorSlider(label = "Font size", valueText = "${selected.fontSizeSp.toInt()} sp", value = selected.fontSizeSp, valueRange = 8f..48f, onValueChange = onFontSizeChanged)
                                InspectorSlider(
                                    label = "Line spacing",
                                    valueText = String.format(Locale.US, "%.2fx", selected.lineSpacingMultiplier),
                                    value = selected.lineSpacingMultiplier,
                                    valueRange = 0.9f..2f,
                                    onValueChange = onLineSpacingChanged,
                                )
                            }
                            is ImageEditModel -> {
                                Text(selected.label, style = MaterialTheme.typography.bodyMedium)
                                Text(selected.imagePath, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        InspectorSlider(label = "Opacity", valueText = String.format(Locale.US, "%.2f", selected.opacity), value = selected.opacity, valueRange = 0.1f..1f, onValueChange = onOpacityChanged)
                        InspectorSlider(label = "Rotation", valueText = "${selected.rotationDegrees.toInt()}°", value = selected.rotationDegrees, valueRange = -180f..180f, onValueChange = onRotationChanged)
                    }
                }
            }
        }
    }
}

@Composable
private fun InspectorSlider(
    label: String,
    valueText: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.labelLarge)
            Text(valueText, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Slider(value = value, onValueChange = onValueChange, valueRange = valueRange)
    }
}

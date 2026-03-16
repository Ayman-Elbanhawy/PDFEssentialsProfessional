package com.aymanelbanhawy.enterprisepdf.app.forms

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Draw
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import com.aymanelbanhawy.editor.core.forms.FormFieldModel
import com.aymanelbanhawy.editor.core.forms.FormFieldType
import com.aymanelbanhawy.editor.core.forms.FormFieldValue
import com.aymanelbanhawy.editor.core.forms.FormProfileModel
import com.aymanelbanhawy.editor.core.forms.SavedSignatureModel
import com.aymanelbanhawy.editor.core.forms.SignatureKind
import com.aymanelbanhawy.editor.core.forms.SignatureVerificationStatus
import com.aymanelbanhawy.enterprisepdf.app.ui.IconTooltipButton

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FormsSidebar(
    modifier: Modifier = Modifier,
    activeSignMode: Boolean,
    fields: List<FormFieldModel>,
    selectedField: FormFieldModel?,
    validationMessage: String?,
    profiles: List<FormProfileModel>,
    signatures: List<SavedSignatureModel>,
    onSelectField: (String) -> Unit,
    onTextChanged: (String, String) -> Unit,
    onBooleanChanged: (String, Boolean) -> Unit,
    onChoiceChanged: (String, String) -> Unit,
    onSaveProfile: (String) -> Unit,
    onApplyProfile: (String) -> Unit,
    onExportFormData: () -> Unit,
    onOpenSignatureCapture: (String) -> Unit,
    onApplySignature: (String, String) -> Unit,
) {
    var profileName by remember { mutableStateOf("Field Profile") }
    val visibleFields = if (activeSignMode) {
        fields.filter { it.type == FormFieldType.Signature }
    } else {
        fields
    }

    Surface(modifier = modifier, tonalElevation = 2.dp, shape = RoundedCornerShape(20.dp)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(if (activeSignMode) "Signatures" else "Forms", style = MaterialTheme.typography.titleMedium)
            if (validationMessage != null && selectedField != null) {
                Text(
                    text = validationMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.errorContainer, RoundedCornerShape(12.dp))
                        .padding(10.dp),
                )
            }

            if (activeSignMode) {
                Text(
                    text = "Select a signature field to capture a new signature or apply one of your saved signatures.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                if (signatures.isEmpty()) {
                    Text(
                        text = "No saved signatures yet.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                OutlinedTextField(
                    value = profileName,
                    onValueChange = { profileName = it },
                    label = { Text("Profile name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconTooltipButton(
                        icon = Icons.Outlined.ContentCopy,
                        tooltip = "Save Profile",
                        enabled = profileName.isNotBlank() && visibleFields.isNotEmpty(),
                        onClick = { onSaveProfile(profileName) },
                    )
                    IconTooltipButton(
                        icon = Icons.Outlined.Download,
                        tooltip = "Export Form JSON",
                        enabled = visibleFields.isNotEmpty(),
                        onClick = onExportFormData,
                    )
                    profiles.take(4).forEach { profile ->
                        IconTooltipButton(
                            icon = Icons.Outlined.Person,
                            tooltip = "Apply profile: ${profile.name}",
                            enabled = visibleFields.isNotEmpty(),
                            onClick = { onApplyProfile(profile.id) },
                        )
                    }
                }
            }
            if (visibleFields.isEmpty()) {
                Text(
                    text = if (activeSignMode) {
                        "No signature fields are available on this page."
                    } else {
                        "No form fields are available on this page."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(visibleFields, key = { it.name }) { field ->
                        FieldCard(
                            field = field,
                            selected = field.name == selectedField?.name,
                            signatures = signatures,
                            onSelectField = onSelectField,
                            onTextChanged = onTextChanged,
                            onBooleanChanged = onBooleanChanged,
                            onChoiceChanged = onChoiceChanged,
                            onOpenSignatureCapture = onOpenSignatureCapture,
                            onApplySignature = onApplySignature,
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FieldCard(
    field: FormFieldModel,
    selected: Boolean,
    signatures: List<SavedSignatureModel>,
    onSelectField: (String) -> Unit,
    onTextChanged: (String, String) -> Unit,
    onBooleanChanged: (String, Boolean) -> Unit,
    onChoiceChanged: (String, String) -> Unit,
    onOpenSignatureCapture: (String) -> Unit,
    onApplySignature: (String, String) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        tonalElevation = if (selected) 4.dp else 0.dp,
        shape = RoundedCornerShape(18.dp),
        onClick = { onSelectField(field.name) },
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(field.label.ifBlank { field.name }, style = MaterialTheme.typography.titleSmall)
                StatusBadge(field.type, field.signatureStatus)
            }
            when (field.type) {
                FormFieldType.Text, FormFieldType.MultilineText, FormFieldType.Date -> {
                    val current = (field.value as? FormFieldValue.Text)?.text.orEmpty()
                    OutlinedTextField(
                        value = current,
                        onValueChange = { onTextChanged(field.name, it) },
                        label = { Text(field.placeholder.ifBlank { field.type.name }) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = field.type != FormFieldType.MultilineText,
                        minLines = if (field.type == FormFieldType.MultilineText) 3 else 1,
                    )
                }
                FormFieldType.Checkbox -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = (field.value as? FormFieldValue.BooleanValue)?.checked == true,
                            onCheckedChange = { onBooleanChanged(field.name, it) },
                        )
                        Text(field.helperText.ifBlank { "Toggle value" })
                    }
                }
                FormFieldType.RadioGroup, FormFieldType.Dropdown -> {
                    val selectedValue = (field.value as? FormFieldValue.Choice)?.selected.orEmpty()
                    Text("Selected: ${selectedValue.ifBlank { "None" }}", style = MaterialTheme.typography.bodyMedium)
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        field.options.forEach { option ->
                            FilterChip(
                                selected = selectedValue == option.value,
                                onClick = { onChoiceChanged(field.name, option.value) },
                                label = { Text(option.label) },
                            )
                        }
                    }
                }
                FormFieldType.Signature -> {
                    val signatureValue = field.value as? FormFieldValue.SignatureValue
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconTooltipButton(icon = Icons.Outlined.Draw, tooltip = "Capture Signature", onClick = { onOpenSignatureCapture(field.name) })
                        signatures.take(4).forEach { signature ->
                            IconTooltipButton(
                                icon = Icons.Outlined.Draw,
                                tooltip = "Apply saved ${signature.kind.name.lowercase()}: ${signature.name}",
                                onClick = { onApplySignature(field.name, signature.id) },
                            )
                        }
                    }
                    if (signatureValue?.imagePath != null) {
                        BitmapFactory.decodeFile(signatureValue.imagePath)?.let { bitmap ->
                            Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = field.label,
                                modifier = Modifier.size(width = 140.dp, height = 88.dp),
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(signatureValue?.signerName.orEmpty(), style = MaterialTheme.typography.bodyMedium)
                        Text(signatureValue?.kind?.name ?: SignatureKind.Signature.name, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusBadge(type: FormFieldType, verificationStatus: SignatureVerificationStatus) {
    val label = when (type) {
        FormFieldType.Signature -> verificationStatus.name
        else -> type.name
    }
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(999.dp)) {
        Text(label, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall)
    }
}

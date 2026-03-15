package com.aymanelbanhawy.enterprisepdf.app.scan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aymanelbanhawy.editor.core.ocr.OcrModelDeliveryMode
import com.aymanelbanhawy.editor.core.scan.ScanImportOptions

@Composable
fun ScanImportDialog(
    options: ScanImportOptions,
    onOptionsChanged: (ScanImportOptions) -> Unit,
    onDismiss: () -> Unit,
    onPickImages: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Import Scan") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = options.displayName,
                    onValueChange = { onOptionsChanged(options.copy(displayName = it)) },
                    label = { Text("Output PDF name") },
                )
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = options.ocrSettings.languageHints.joinToString(", "),
                    onValueChange = { raw ->
                        onOptionsChanged(
                            options.copy(
                                ocrSettings = options.ocrSettings.copy(
                                    languageHints = raw.split(',').map { it.trim() }.filter { it.isNotBlank() },
                                ),
                            ),
                        )
                    },
                    label = { Text("Language hints") },
                    supportingText = { Text("Optional BCP-47 hints such as en, ar, fr") },
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        modifier = Modifier.weight(1f),
                        value = options.ocrSettings.timeoutSeconds.toString(),
                        onValueChange = { value -> value.toIntOrNull()?.let { onOptionsChanged(options.copy(ocrSettings = options.ocrSettings.copy(timeoutSeconds = it.coerceIn(5, 120)))) } },
                        label = { Text("Timeout") },
                        singleLine = true,
                    )
                    OutlinedTextField(
                        modifier = Modifier.weight(1f),
                        value = options.ocrSettings.maxRetryCount.toString(),
                        onValueChange = { value -> value.toIntOrNull()?.let { onOptionsChanged(options.copy(ocrSettings = options.ocrSettings.copy(maxRetryCount = it.coerceIn(0, 6)))) } },
                        label = { Text("Retries") },
                        singleLine = true,
                    )
                    OutlinedTextField(
                        modifier = Modifier.weight(1f),
                        value = options.ocrSettings.pagesPerWorkerBatch.toString(),
                        onValueChange = { value -> value.toIntOrNull()?.let { onOptionsChanged(options.copy(ocrSettings = options.ocrSettings.copy(pagesPerWorkerBatch = it.coerceIn(1, 24)))) } },
                        label = { Text("Batch") },
                        singleLine = true,
                    )
                }
                ScanOptionRow("Fix orientation", options.ocrSettings.preprocessing.fixOrientation) {
                    onOptionsChanged(options.copy(ocrSettings = options.ocrSettings.copy(preprocessing = options.ocrSettings.preprocessing.copy(fixOrientation = it))))
                }
                ScanOptionRow("Deskew", options.ocrSettings.preprocessing.deskew) {
                    onOptionsChanged(options.copy(ocrSettings = options.ocrSettings.copy(preprocessing = options.ocrSettings.preprocessing.copy(deskew = it))))
                }
                ScanOptionRow("Auto-crop", options.ocrSettings.preprocessing.autoCrop) {
                    onOptionsChanged(options.copy(ocrSettings = options.ocrSettings.copy(preprocessing = options.ocrSettings.preprocessing.copy(autoCrop = it))))
                }
                ScanOptionRow("Contrast cleanup", options.ocrSettings.preprocessing.contrastCleanup) {
                    onOptionsChanged(options.copy(ocrSettings = options.ocrSettings.copy(preprocessing = options.ocrSettings.preprocessing.copy(contrastCleanup = it))))
                }
                ScanOptionRow("Grayscale", options.ocrSettings.preprocessing.grayscale) {
                    onOptionsChanged(options.copy(ocrSettings = options.ocrSettings.copy(preprocessing = options.ocrSettings.preprocessing.copy(grayscale = it))))
                }
                ScanOptionRow("Binarize", options.ocrSettings.preprocessing.binarize) {
                    onOptionsChanged(options.copy(ocrSettings = options.ocrSettings.copy(preprocessing = options.ocrSettings.preprocessing.copy(binarize = it))))
                }
                ScanOptionRow("Embed OCR session data on export", options.ocrSettings.embedSessionDataOnExport) {
                    onOptionsChanged(options.copy(ocrSettings = options.ocrSettings.copy(embedSessionDataOnExport = it)))
                }
                ScanOptionRow("Platform-managed delivery hint", options.ocrSettings.deliveryMode == OcrModelDeliveryMode.PlatformManaged) {
                    onOptionsChanged(options.copy(ocrSettings = options.ocrSettings.copy(deliveryMode = if (it) OcrModelDeliveryMode.PlatformManaged else OcrModelDeliveryMode.Bundled)))
                }
                Text(
                    "Imported pages are cleaned locally, queued for ML Kit OCR, and rewritten into a searchable PDF with persisted text blocks and page regions.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        confirmButton = { TextButton(onClick = onPickImages) { Text("Choose Images") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun ScanOptionRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label)
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
    }
}

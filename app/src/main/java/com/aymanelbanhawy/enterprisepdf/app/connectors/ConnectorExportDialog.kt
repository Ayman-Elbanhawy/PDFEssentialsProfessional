package com.aymanelbanhawy.enterprisepdf.app.connectors

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aymanelbanhawy.editor.core.connectors.ConnectorAccountModel
import com.aymanelbanhawy.editor.core.connectors.SaveDestinationMode
import com.aymanelbanhawy.enterprisepdf.app.editor.ConnectorExportUiState

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ConnectorExportDialog(
    state: ConnectorExportUiState,
    accounts: List<ConnectorAccountModel>,
    onDismiss: () -> Unit,
    onAccountChanged: (String) -> Unit,
    onRemotePathChanged: (String) -> Unit,
    onDisplayNameChanged: (String) -> Unit,
    onSubmit: () -> Unit,
) {
    var accountId by remember(state.selectedAccountId) { mutableStateOf(state.selectedAccountId) }
    var remotePath by remember(state.remotePath) { mutableStateOf(state.remotePath) }
    var displayName by remember(state.displayName) { mutableStateOf(state.displayName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (state.destinationMode == SaveDestinationMode.ShareCopy) "Share To Connector" else "Save To Connector") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    accounts.forEach { account ->
                        FilterChip(
                            selected = accountId == account.id,
                            onClick = {
                                accountId = account.id
                                onAccountChanged(account.id)
                            },
                            label = { Text(account.displayName) },
                        )
                    }
                }
                OutlinedTextField(
                    value = displayName,
                    onValueChange = {
                        displayName = it
                        onDisplayNameChanged(it)
                    },
                    label = { Text("File name") },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = remotePath,
                    onValueChange = {
                        remotePath = it
                        onRemotePathChanged(it)
                    },
                    label = { Text("Destination path") },
                    supportingText = { Text("Path format: /exports/document.pdf or content://...", style = MaterialTheme.typography.bodySmall) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onSubmit, enabled = accountId.isNotBlank() && remotePath.isNotBlank()) {
                Text(if (state.destinationMode == SaveDestinationMode.ShareCopy) "Queue Share" else "Queue Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
        modifier = Modifier.padding(12.dp),
    )
}
